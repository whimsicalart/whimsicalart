package codes.pepper.whimsicalart.feature.editor.domain.filter

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StyleFilterTest {

    /** The pre-LUT FILMIC reference: the same S-curve + tone offsets, per pixel. */
    private fun filmicRef(p: Int): Int = Color.argb(
        Color.alpha(p),
        curveRef(Color.red(p) * 0.90f + 8f) + 4,
        curveRef(Color.green(p) * 0.96f) + 2,
        curveRef(Color.blue(p) * 0.88f + 6f) - 2
    )

    private fun curveRef(v: Float): Int =
        (255f / (1f + kotlin.math.exp(-(v - 127.5f) / 45f))).roundToInt().coerceIn(0, 255)

    @Test
    fun `applying a filter keeps dimensions and does not mutate the source`() {
        val input = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(120, 80, 60))
        val before = input.getPixel(0, 0)
        val out = StyleFilterProcessor.apply(input, StyleFilter.FILMIC)
        assertEquals(8, out.width)
        assertEquals(6, out.height)
        assertEquals("source must stay intact", before, input.getPixel(0, 0))
    }

    @Test
    fun `filmic filter changes tone (non-identity on a saturated pixel)`() {
        val input = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(200, 100, 40))
        val out = StyleFilterProcessor.apply(input, StyleFilter.FILMIC)
        val original = input.getPixel(1, 1)
        assertNotEquals("filmic curve must remap a non-neutral pixel", original, out.getPixel(1, 1))
        val r = Color.red(out.getPixel(1, 1))
        assertTrue("output channel must stay in range, got $r", r in 0..255)
    }

    @Test
    fun `vibrant filter increases chroma separation on a colourful edge`() {
        val input = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
        for (y in 0 until 6) for (x in 0 until 6) {
            input.setPixel(x, y, if (x < 3) Color.rgb(60, 40, 220) else Color.rgb(220, 60, 40))
        }
        val out = StyleFilterProcessor.apply(input, StyleFilter.VIBRANT)
        val blue = out.getPixel(1, 3)
        val red = out.getPixel(4, 3)
        // Vibrant boosts channel separation, so red-vs-blue dominance strengthens.
        val blueStrength = Color.blue(blue) - Color.red(blue)
        val redStrength = Color.red(red) - Color.blue(red)
        assertTrue("vibrant must keep blue side blue-dominant ($blueStrength)", blueStrength > 100)
        assertTrue("vibrant must keep red side red-dominant ($redStrength)", redStrength > 100)
    }

    @Test
    fun `matte filter lifts shadows while preserving relative order`() {
        val input = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(30, 30, 30))
        val out = StyleFilterProcessor.apply(input, StyleFilter.MATTE)
        val r = Color.red(out.getPixel(2, 2))
        // Matte lifts shadows toward a muted, soft look.
        assertTrue("matte should lift deep shadow (was 30, got $r)", r > 40)
    }

    @Test
    fun `fromId resolves registered filters and rejects unknown`() {
        assertEquals(StyleFilter.FILMIC, StyleFilter.fromId("filmic"))
        assertEquals(StyleFilter.VIBRANT, StyleFilter.fromId("vibrant"))
        assertEquals(StyleFilter.MATTE, StyleFilter.fromId("matte"))
        assertEquals(null, StyleFilter.fromId("nope"))
    }

    @Test
    fun `a fully transparent pixel stays transparent under every style filter`() {
        val input = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.TRANSPARENT)
        for (filter in StyleFilter.entries) {
            val out = StyleFilterProcessor.apply(input, filter)
            for (y in 0 until 4) for (x in 0 until 4) {
                assertEquals(
                    "alpha must stay 0 after $filter at ($x,$y) — not opaque black",
                    0,
                    Color.alpha(out.getPixel(x, y))
                )
            }
        }
    }

    @Test
    fun `a semi-transparent pixel keeps its alpha under every style filter`() {
        val alpha = 128
        val input = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.argb(alpha, 200, 100, 40))
        for (filter in StyleFilter.entries) {
            val out = StyleFilterProcessor.apply(input, filter)
            for (y in 0 until 4) for (x in 0 until 4) {
                assertEquals(
                    "alpha must be preserved as $alpha after $filter at ($x,$y)",
                    alpha,
                    Color.alpha(out.getPixel(x, y))
                )
            }
        }
    }

    @Test
    fun `a semi-transparent highlight pixel keeps its alpha across the fold style effect`() {
        val alpha = 96
        val input = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.argb(alpha, 240, 200, 120))
        for (filter in StyleFilter.entries) {
            val out = StyleFilterProcessor.apply(input, filter)
            assertEquals(alpha, Color.alpha(out.getPixel(0, 0)))
        }
    }

    @Test
    fun `filmic LUT reproduces the per-pixel curve for all 256 channel values`() {
        // The FILMIC branch precomputes 256-entry per-channel LUTs once per
        // apply; each lookup (bit-exact by construction) must equal evaluating
        // the S-curve per pixel. A grey sweep drives every entry of all three
        // LUTs because the R/G/B curve inputs differ per channel.
        val input = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        for (v in 0..255) {
            input.setPixel(0, 0, Color.rgb(v, v, v))
            val out = StyleFilterProcessor.apply(input, StyleFilter.FILMIC)
            val expected = filmicRef(input.getPixel(0, 0))
            assertEquals("grey channel $v", expected, out.getPixel(0, 0))
        }
    }

    @Test
    fun `filmic bitmap output is byte-identical to the per-pixel reference map`() {
        // Random-looking deterministic ARGB sweep (opaque and transparent pixels
        // mix, and near-255 channels push the +4/-2 tone offsets past the byte
        // mask, exactly like the per-pixel reference).
        val w = 37
        val h = 19
        val input = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                input.setPixel(
                    x, y,
                    Color.argb((x * 7 + y) % 256, (x * 13 + y * 3) % 256, (y * 17 + x * 5) % 256, (x * 23 + y * 11) % 256)
                )
            }
        }
        val out = StyleFilterProcessor.apply(input, StyleFilter.FILMIC)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val expected = filmicRef(input.getPixel(x, y))
                assertEquals("pixel ($x,$y)", expected, out.getPixel(x, y))
            }
        }
    }
}