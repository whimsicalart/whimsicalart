package codes.pepper.whimsicalart.feature.editor.domain.bokeh

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    @Test
    fun should_return_the_source_untouched_when_the_blur_radius_is_zero() {
        val w = 16
        val h = 8
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) px[y * w + x] = if (x < w / 2) Color.BLACK else Color.WHITE
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        val mask = foregroundLeftMask(w, h, foregroundWidth = w / 2)

        val out = BokehProcessor().applyBackgroundBlur(src, mask, blurRadius = 0f)

        assertEquals(src.width, out.width)
        assertEquals(src.height, out.height)
        for (y in 0 until h) {
            for (x in 0 until w) {
                assertEquals("pixel ($x,$y) must stay identical at radius 0", src.getPixel(x, y), out.getPixel(x, y))
            }
        }
    }

    @Test
    fun should_blur_more_for_a_bigger_radius_and_keep_the_subject_sharp_at_every_radius() {
        val w = 16
        val h = 8
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) px[y * w + x] = if (x < w / 2) Color.BLACK else Color.WHITE
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        val mask = foregroundLeftMask(w, h, foregroundWidth = w / 2)

        val r1 = BokehProcessor().applyBackgroundBlur(src, mask, blurRadius = 1f)
        val r40 = BokehProcessor().applyBackgroundBlur(src, mask, blurRadius = 40f)
        val w40 = BokehProcessor().applyBackgroundBlur(src, mask, blurRadius = 40f, bokehShape = BokehShape.HEART)

        for (radius in listOf(r1, r40, w40)) {
            for (x in 0 until w / 2) {
                for (y in 0 until h) {
                    assertEquals("subject must stay sharp at ($x,$y)", Color.BLACK, radius.getPixel(x, y))
                }
            }
        }

        assertTrue("radius 1 still feathers the edge", red(r1.getPixel(w / 2, h / 2)) < 255)
        assertTrue(
            "radius 40 pulls the far background closer to the mean than radius 1: " +
                "r1=${red(r1.getPixel(w - 1, h / 2))} r40=${red(r40.getPixel(w - 1, h / 2))}",
            red(r40.getPixel(w - 1, h / 2)) < red(r1.getPixel(w - 1, h / 2))
        )
        assertTrue(
            "radius 40 blends the whole width toward the mean (darker than the white far edge)",
            red(r40.getPixel(w - 1, h / 2)) < 255
        )
    }

    @Test
    fun should_fade_the_blur_a_few_pixels_inward_from_the_subject_boundary() {
        val w = 16
        val h = 8
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) px[y * w + x] = if (x < w / 2) Color.BLACK else Color.WHITE
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        val mask = foregroundLeftMask(w, h, foregroundWidth = w / 2)

        val out = BokehProcessor().applyBackgroundBlur(src, mask, blurRadius = 2f, softEdgeWidth = 4)

        // Deep subject core (>= bandWidth from the path) stays exactly sharp.
        for (x in 0 until w / 2 - 4) {
            for (y in 0 until h) {
                assertEquals("subject core must stay sharp at ($x,$y)", Color.BLACK, out.getPixel(x, y))
            }
        }
        // The few-px band just inside the path mixes the blurred background
        // into the subject edge with a monotonic ramp: luma decreases (toward
        // the pure source black) as the distance from the boundary grows.
        val row = h / 2
        val b7 = red(out.getPixel(7, row))
        val b6 = red(out.getPixel(6, row))
        val b5 = red(out.getPixel(5, row))
        assertTrue(
            "band must be a monotonic source<-background ramp (b7=$b7 b6=$b6 b5=$b5)",
            b7 > b6 && b6 >= b5 && b6 > 0
        )
        // Fully-background pixels just past the path stay blurred (not the
        // sharp white source).
        for (x in w / 2 until w / 2 + 2) {
            assertTrue("background pixel x=$x must stay blurred", red(out.getPixel(x, row)) < 255)
        }
    }

    @Test
    fun `shaped bokeh disc radius is a fraction of the short edge, not a fixed 2px`() {
        // One effect, two scales: a 2px disc disappears at full size (0.05% of a
        // 4000px image vs 0.4% of a 500px preview). The radius scales with the
        // live image, keeping the shaped highlights equally visible.
        val processor = BokehProcessor()
        assertEquals(1.25f, processor.bokehDiscRadius(500), 1e-3f)
        assertEquals(5f, processor.bokehDiscRadius(2000), 1e-3f)
        assertTrue(
            "full-size disc keeps a visible reach",
            processor.bokehDiscRadius(4000) > processor.bokehDiscRadius(500) * 4
        )
    }

    @Test
    fun `portrait subject mask renders the soft band without reading the depth map out of bounds`() {
        // The mask is taller than wide (maskH > maskW) — the exact shape that
        // previously indexed the depth map as edge[my * maskH + mx] (height
        // stride) and crashed with ArrayIndexOutOfBoundsException once the row
        // index pushed the flat index past maskW * maskH.
        val w = 24
        val h = 48
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) px[y * w + x] = if (x < w / 2) Color.BLACK else Color.WHITE
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        val mask = foregroundLeftMask(w, h, foregroundWidth = w / 2)

        val out = BokehProcessor().applyBackgroundBlur(src, mask, blurRadius = 2f, softEdgeWidth = 4)

        // Subject core (>= bandWidth from the path) stays exactly sharp.
        for (x in 0 until w / 2 - 4) {
            for (y in 0 until h) {
                assertEquals("subject core must stay sharp at ($x,$y)", Color.BLACK, out.getPixel(x, y))
            }
        }
        // The band ramp inward from the path column (w/2 - 1): the blurred
        // background mixes in monotonically toward the pure source black — at
        // the path the blend factor is 0 (full blur), growing to 1 (full
        // source) by bandWidth columns in.
        val row = h / 2
        val bPath = red(out.getPixel(w / 2 - 1, row))
        val bIn1 = red(out.getPixel(w / 2 - 2, row))
        val bIn2 = red(out.getPixel(w / 2 - 3, row))
        val bIn3 = red(out.getPixel(w / 2 - 4, row))
        assertTrue(
            "band must be monotonic inward (path=$bPath in1=$bIn1 in2=$bIn2 in3=$bIn3)",
            bPath > bIn1 && bIn1 > bIn2 && bIn2 > bIn3 && bIn3 > 0
        )
        // Fully-background pixels just past the path stay blurred (not the
        // sharp white source).
        for (x in w / 2 until w / 2 + 2) {
            assertTrue("background pixel x=$x must stay blurred", red(out.getPixel(x, row)) < 255)
        }
    }

    @Test
    fun `portrait and landscape subject masks produce an identical soft-edge ramp`() {
        // Locks the depth-map stride in BOTH directions: the edge ramp results
        // must be identical whether the mask is 24x48 (portrait) or 48x24
        // (landscape) with the same boundary pattern — a stride bug in either
        // orientation would shift one of the ramps.
        val pW = 24
        val pH = 48
        val srcP = Bitmap.createBitmap(pW, pH, Bitmap.Config.ARGB_8888)
        val pxP = IntArray(pW * pH)
        for (y in 0 until pH) {
            for (x in 0 until pW) pxP[y * pW + x] = if (x < pW / 2) Color.BLACK else Color.WHITE
        }
        srcP.setPixels(pxP, 0, pW, 0, 0, pW, pH)
        val maskP = foregroundLeftMask(pW, pH, foregroundWidth = pW / 2)

        val lW = 48
        val lH = 24
        val srcL = Bitmap.createBitmap(lW, lH, Bitmap.Config.ARGB_8888)
        val pxL = IntArray(lW * lH)
        for (y in 0 until lH) {
            for (x in 0 until lW) pxL[y * lW + x] = if (x < lW / 2) Color.BLACK else Color.WHITE
        }
        srcL.setPixels(pxL, 0, lW, 0, 0, lW, lH)
        val maskL = foregroundLeftMask(lW, lH, foregroundWidth = lW / 2)

        val outP = BokehProcessor().applyBackgroundBlur(srcP, maskP, blurRadius = 2f, softEdgeWidth = 4)
        val outL = BokehProcessor().applyBackgroundBlur(srcL, maskL, blurRadius = 2f, softEdgeWidth = 4)

        // Profile columns relative to the boundary path (the last subject
        // column): the ramp reads the same pixels in both orientations.
        val pRow = pH / 2
        val lRow = lH / 2
        for (i in 0 until 4) {
            val pCol = pW / 2 - 1 - i
            val lCol = lW / 2 - 1 - i
            val expected = outL.getPixel(lCol, lRow)
            assertEquals(
                "ramp pixel i=$i (p=$pCol, l=$lCol) must match across orientations",
                expected,
                outP.getPixel(pCol, pRow)
            )
        }
    }

    @Test
    fun `radius zero returns the original reference, not a copy`() {
        // Mirrors 0b61c12: applyBackgroundBlur never mutates `original` on the
        // blurRadius<=0 path, and every caller now gates on `!==` before drawing
        // or recycling the returned bitmap, so the shared reference skips a full
        // alloc+copy per zero-intensity preview/commit publish.
        val w = 16
        val h = 8
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) px[y * w + x] = if (x < w / 2) Color.BLACK else Color.WHITE
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        val mask = foregroundLeftMask(w, h, foregroundWidth = w / 2)

        val out = BokehProcessor().applyBackgroundBlur(src, mask, blurRadius = 0f)

        assertSame(src, out)
        assertEquals("the shared reference is not mutated downstream", Color.BLACK, out.getPixel(0, h / 2))
    }

    @Test
    fun `blurBitmap caches per input bitmap identity and radius`() {
        // The 4-pass separable blur is the dominant bokeh background cost and is
        // recomputed on every radius-stable preview drag tick; memoizing on
        // (bitmap identity, radius) like SoftEdgeBand.depthCache turns the hot
        // path into pure lookups. Retains a bounded number of results per input.
        val src = stepRow(8, 4)
        val other = stepRow(8, 4)
        val processor = BokehProcessor()

        val a = processor.blurBitmap(src, 2f)
        val b = processor.blurBitmap(src, 2f)
        assertSame("same input + radius must hit the cache", a, b)

        val c = processor.blurBitmap(src, 4f)
        val d = processor.blurBitmap(src, 4f)
        assertSame("a new radius computes once then caches", c, d)
        assertTrue("different radius must not alias the first entry", c !== a)

        val e = processor.blurBitmap(other, 2f)
        assertTrue("different bitmap identity must recompute", e !== a)
    }

    @Test
    fun `shaped highlight pass reads batched composited pixels, byte-identical to the per-pixel scan`() {
        // The fix reads the freshly composited pixel array (one getPixels batch)
        // instead of one result.getPixel JNI call per pixel and hoists the disc
        // radius out of the loop. With well-separated bright low-contrast
        // highlights in the sharp subject no drawn disc re-influences a later
        // read, so the fast path must be byte-identical to the old per-pixel
        // canvas scan (control below: live getPixel reads + per-highlight radius).
        val w = 48
        val h = 32
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h) { Color.rgb(50, 40, 36) }
        px[8 * w + 8] = Color.rgb(240, 236, 232)
        px[16 * w + 24] = Color.rgb(244, 238, 232)
        src.setPixels(px, 0, w, 0, 0, w, h)
        val mask = foregroundLeftMask(w, h, foregroundWidth = w * 3 / 4)

        val processor = BokehProcessor()
        val fast = processor.applyBackgroundBlur(src, mask, blurRadius = 2f, bokehShape = BokehShape.HEXAGON)
        // CIRCLE skips the highlight pass, exposing the freshly composited base.
        val base = processor.applyBackgroundBlur(src, mask, blurRadius = 2f, bokehShape = BokehShape.CIRCLE)
        val control = perPixelShapeHighlights(base, BokehShape.HEXAGON)

        val fastPx = IntArray(w * h)
        val controlPx = IntArray(w * h)
        fast.getPixels(fastPx, 0, w, 0, 0, w, h)
        control.getPixels(controlPx, 0, w, 0, 0, w, h)
        for (i in 0 until w * h) {
            assertEquals("pixel ${i % w},${i / w}", controlPx[i], fastPx[i])
        }
    }

    /**
     * Reference clone of the PREVIOUS applyShapeHighlights: per-pixel live canvas
     * reads, per-highlight radius computation. Used to prove the batched-array
     * rewrite is byte-identical on inputs where discs do not overlap later reads.
     */
    private fun perPixelShapeHighlights(base: Bitmap, shape: BokehShape): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val w = result.width
        val h = result.height
        val canvas = Canvas(result)
        val processor = BokehProcessor()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = result.getPixel(x, y)
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val luma = (r * 0.3f + g * 0.59f + b * 0.11f)
                if (luma > 200f && (max - min) < 70) {
                    val radius = processor.bokehDiscRadius(minOf(w, h))
                    drawDiscClone(canvas, x.toFloat(), y.toFloat(), radius, shape, Color.argb(120, r, g, b))
                }
            }
        }
        return result
    }

    /** Clone of BokehProcessor.drawBokehDisc (hexagon path used by the parity test). */
    private fun drawDiscClone(canvas: Canvas, cx: Float, cy: Float, radius: Float, shape: BokehShape, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        when (shape) {
            BokehShape.HEXAGON -> {
                val path = Path()
                val n = 6
                for (i in 0 until n) {
                    val angle = Math.toRadians((60 * i - 30).toDouble())
                    val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
                    val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            BokehShape.HEART, BokehShape.CIRCLE -> Unit
        }
    }
}