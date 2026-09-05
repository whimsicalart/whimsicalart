package codes.pepper.whimsicalart.feature.editor.domain.bokeh

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the API < 31 software fallback used by [BokehProcessor]: a separable
 * box blur (two horizontal/vertical passes, repeated twice). Pixel-level
 * invariants are checked with getPixel/setPixels, which - unlike Canvas
 * drawing - do rasterize reliably on this Robolectric build.
 *
 * Note: pixels are full ARGB ints (alpha = 0xFF), so every assertion compares
 * the extracted red channel (the grayscale pixels used here have R=G=B).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BokehProcessorTest {

    private fun solid(w: Int, h: Int, color: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }

    /** 1xN row: first [edge] pixels black, the rest white. */
    private fun stepRow(n: Int, edge: Int): Bitmap {
        val bmp = Bitmap.createBitmap(n, 1, Bitmap.Config.ARGB_8888)
        val px = IntArray(n)
        px.fill(Color.BLACK, 0, edge)
        px.fill(Color.WHITE, edge, n)
        bmp.setPixels(px, 0, n, 0, 0, n, 1)
        return bmp
    }

    /** A mask bitmap: opaque white on the left [foregroundStart] pixels, transparent on the rest. */
    private fun foregroundLeftMask(w: Int, h: Int, foregroundWidth: Int): Bitmap {
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                px[idx] = if (x < foregroundWidth) Color.argb(255, 255, 255, 255) else Color.TRANSPARENT
            }
        }
        mask.setPixels(px, 0, w, 0, 0, w, h)
        return mask
    }

    private fun readRow(bmp: Bitmap): IntArray {
        val px = IntArray(bmp.width)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, 1)
        return px
    }

    private fun red(c: Int): Int = (c shr 16) and 0xFF

    @Test
    fun should_leave_a_solid_color_unchanged() {
        val bmp = solid(16, 16, Color.rgb(200, 30, 90))
        BokehProcessor().applySoftwareBlur(bmp, 3)
        for (x in 0 until bmp.width) {
            for (y in 0 until bmp.height) {
                assertEquals(Color.rgb(200, 30, 90), bmp.getPixel(x, y))
            }
        }
    }

    @Test
    fun should_soften_a_horizontal_step_edge_and_preserve_the_mean() {
        val original = stepRow(8, 4)
        val sumIn = readRow(original).sumOf { red(it) }
        BokehProcessor().applySoftwareBlur(original, 1)
        val px = readRow(original)
        assertTrue("first pixel stays dark", red(px[0]) == 0)
        assertTrue("last pixel stays white", red(px[7]) == 255)
        assertTrue(
            "edge is feathered, not abrupt: px[3]=${red(px[3])} < px[4]=${red(px[4])}",
            red(px[3]) < red(px[4])
        )
        assertTrue(
            "edge pixels take intermediate values",
            red(px[3]) > 0 && red(px[4]) < 255
        )
        assertEquals(
            "total luma is conserved",
            sumIn.toDouble(),
            px.sumOf { red(it) }.toDouble(),
            8.0
        )
    }

    @Test
    fun should_spread_the_transition_wider_for_a_bigger_radius() {
        val r1 = stepRow(8, 4)
        val r2 = stepRow(8, 4)
        BokehProcessor().applySoftwareBlur(r1, 1)
        BokehProcessor().applySoftwareBlur(r2, 2)
        val a = readRow(r1)
        val b = readRow(r2)
        assertTrue(
            "bigger radius should soften further from the step (a[2]=${red(a[2])}, b[2]=${red(b[2])})",
            red(a[2]) < red(b[2])
        )
        assertTrue(
            "bigger radius should pull the step core closer to the mean (a[4]=${red(a[4])}, b[4]=${red(b[4])})",
            abs(red(b[4]).toFloat() - 127.5f) < abs(red(a[4]).toFloat() - 127.5f)
        )
    }

    @Test
    fun should_soften_a_vertical_step_edge_across_rows() {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val px = IntArray(8 * 8)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                px[y * 8 + x] = if (y < 4) Color.BLACK else Color.WHITE
            }
        }
        bmp.setPixels(px, 0, 8, 0, 0, 8, 8)
        val col0In = px.filterIndexed { i, _ -> i % 8 == 0 }.sumOf { red(it) }
        BokehProcessor().applySoftwareBlur(bmp, 1)
        val out = IntArray(64)
        bmp.getPixels(out, 0, 8, 0, 0, 8, 8)
        assertTrue("very top row stays dark", red(out[0]) == 0)
        assertTrue("very bottom row stays white", red(out[63]) == 255)
        assertTrue(
            "vertical edge feathered: row3=${red(out[3 * 8])} < row4=${red(out[4 * 8])}",
            red(out[3 * 8]) < red(out[4 * 8])
        )
        assertTrue(
            "edge rows take intermediate values",
            red(out[3 * 8]) > 0 && red(out[4 * 8]) < 255
        )
        val colLuma = (0 until 8).sumOf { red(out[it * 8]) }
        assertEquals(
            "column 0 luma is conserved",
            col0In.toDouble(),
            colLuma.toDouble(),
            8.0
        )
    }

    @Test
    fun should_blur_only_the_background_behind_a_subject_mask() {
        // 16x8 image: left half black, right half white (a strong edge).
        val w = 16
        val h = 8
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) px[y * w + x] = if (x < w / 2) Color.BLACK else Color.WHITE
        }
        src.setPixels(px, 0, w, 0, 0, w, h)

        // Foreground = left 8 columns (opaque); background = right 8 (transparent).
        val mask = foregroundLeftMask(w, h, foregroundWidth = w / 2)

        val out = BokehProcessor().applyBackgroundBlur(src, mask, blurRadius = 2f)

        // Pixels just right of the black/white boundary (x=8) are background and
        // must be feathered toward the dark side by the blur.
        for (x in w / 2 until w / 2 + 2) {
            assertTrue(
                "background pixel x=$x must soften toward dark: red=${red(out.getPixel(x, h / 2))}",
                red(out.getPixel(x, h / 2)) < 255
            )
        }

        // Left (foreground) half must remain exactly the original black.
        for (x in 0 until w / 2) {
            for (y in 0 until h) {
                assertEquals("foreground must stay sharp at ($x,$y)", Color.BLACK, out.getPixel(x, y))
            }
        }
    }
}