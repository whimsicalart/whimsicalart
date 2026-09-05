package codes.pepper.whimsicalart.feature.editor.domain.enhance

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnhanceProcessorTest {

    private fun solid(color: Int, w: Int = 16, h: Int = 16): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    @Test
    fun `enhance keeps dimensions and keeps a uniform image uniform`() {
        val input = solid(Color.rgb(100, 110, 120), 32, 24)
        val out = EnhanceProcessor.enhance(input)
        assertEquals(32, out.width)
        assertEquals(24, out.height)
        // A uniform image has no per-pixel variation, so after auto-levels (no
        // spread to stretch) and HDR (same delta everywhere) it stays uniform.
        assertEquals(out.getPixel(5, 5), out.getPixel(31, 23))
        assertTrue("output must stay in a sane range", Color.red(out.getPixel(5, 5)) in 0..255)
    }

    @Test
    fun `autoLevels stretches a compressed range toward full scale`() {
        val pixels = IntArray(6 * 6)
        // Narrow band 100..110; 0.5% tail clip is trivially crossed so the visible
        // spread widens well beyond the original 10.
        var i = 0
        for (y in 0 until 6) for (x in 0 until 6) {
            pixels[i++] = Color.rgb(100 + (x % 20) / 2, 100 + (x % 20) / 2, 100 + (y % 20) / 2)
        }
        val out = EnhanceProcessor.autoLevels(pixels, 6, 6, 0.5f)
        var min = 255; var max = 0
        for (c in out) { val r = Color.red(c); if (r < min) min = r; if (r > max) max = r }
        assertTrue("autoLevels must widen the channel: got min=$min max=$max", max - min >= 100)
    }

    @Test
    fun `hdrTonemap preserves mid-grey and lifts shadows`() {
        // Mid-grey is the pivot: near-unchanged. Deep shadow is lifted.
        val mid = IntArray(listOf(Color.rgb(127, 127, 127), Color.rgb(60, 60, 60), Color.rgb(30, 30, 30)).size * 4)
        // Build: 1x? array of the three samples repeated in a 3x4 grid.
        val samples = intArrayOf(Color.rgb(127, 127, 127), Color.rgb(60, 60, 60), Color.rgb(30, 30, 30))
        val pixels = IntArray(3 * 4)
        var i = 0
        for (col in 0 until 4) for (s in samples) pixels[i++] = s
        assertEquals(12, pixels.size)

        val out = EnhanceProcessor.hdrTonemap(pixels, 4, 3, 0.1f, 0.8f)
        assertEquals(12, out.size)
        // Shadow sample lifted well above its original.
        assertTrue("shadow must be lifted (>60), got ${Color.red(out[1])}", Color.red(out[1]) > 100)
        // Deep shadow lifted most.
        assertTrue("deep shadow lifted (>30), got ${Color.red(out[2])}", Color.red(out[2]) > 60)
    }

    @Test
    fun `hdrTonemap lifts a dark interior that sits below the surrounding mean`() {
        // 12x12: a bright border around a darker interior. The interior's local
        // mean is dragged up by the bright surround, so HDR lifts the dark tones.
        val pixels = IntArray(12 * 12)
        for (y in 0 until 12) for (x in 0 until 12) {
            val inside = x in 2..9 && y in 2..9
            pixels[y * 12 + x] = if (inside) Color.rgb(60, 60, 60) else Color.rgb(220, 220, 220)
        }
        val out = EnhanceProcessor.hdrTonemap(pixels, 12, 12, 0.1f, 1f)
        var sum = 0L; var count = 0L
        for (y in 4..7) for (x in 4..7) { sum += Color.red(out[y * 12 + x]); count++ }
        val avg = sum.toFloat() / count
        assertTrue("HDR should lift the dark interior (was ~60, got $avg)", avg > 70)
    }

    @Test
    fun `edgeAwareDenoise flattens noise in a smooth block but preserves a hard edge`() {
        val pixels = IntArray(16 * 16)
        // Left half: 60 with strong noise; right half: hard edge to 200.
        for (y in 0 until 16) for (x in 0 until 16) {
            val n = ((x * 31 + y * 17) % 9) - 4
            pixels[y * 16 + x] =
                if (x < 8) Color.rgb(60 + n * 6, 60 + n * 6, 60 + n * 6) else Color.rgb(200, 200, 200)
        }
        val out = EnhanceProcessor.edgeAwareDenoise(pixels, 16, 16, 1f)

        // Smooth area: adjacent pixels should become less noisy (spread shrinks).
        fun spread(y0: Int, y1: Int, x0: Int, x1: Int, src: IntArray): Int {
            var s = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                s += kotlin.math.abs(Color.red(src[y * 16 + x]) - Color.red(src[y * 16 + maxOf(x - 1, x0)]))
            }
            return s
        }
        val beforeSpread = spread(0, 16, 2, 8, pixels)
        val afterSpread = spread(0, 16, 2, 8, out)
        assertTrue(
            "denoise must reduce spatial spread in the flat area (before=$beforeSpread after=$afterSpread)",
            afterSpread < beforeSpread
        )
        // Hard edge: the dark-side pixel just left of the boundary stays dark.
        val edgeDark = Color.red(out[8 * 16 + 7])
        assertTrue("hard edge must be preserved (got $edgeDark)", edgeDark < 160)
    }
}