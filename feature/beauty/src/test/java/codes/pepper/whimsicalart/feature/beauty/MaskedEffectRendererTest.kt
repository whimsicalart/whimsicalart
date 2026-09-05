package codes.pepper.whimsicalart.feature.beauty

import android.graphics.Color
import codes.pepper.whimsicalart.feature.beauty.domain.MaskedEffectRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaskedEffectRendererTest {

    private fun argbPixel(alpha: Int, color: Int): Int =
        Color.argb(alpha, (color ushr 16) and 0xff, (color ushr 8) and 0xff, color and 0xff)

    @Test
    fun `box blur keeps an opaque interior fully saturated`() {
        val w = 40
        val r = 3
        val src = IntArray(w * w)
        for (i in src.indices) {
            val x = i % w
            val y = i / w
            val inside = x in 15..24 && y in 15..24
            src[i] = if (inside) argbPixel(255, Color.RED) else 0
        }

        val out = MaskedEffectRenderer.boxBlurPixels(src, w, w, r)
        val centerA = (out[20 * w + 20] ushr 24) and 0xff
        val centerR = (out[20 * w + 20] ushr 16) and 0xff
        assertEquals("interior stays opaque", 255, centerA)
        assertTrue("interior stays saturated red", centerR > 240)
    }

    @Test
    fun `box blur feathers the hard edge into a soft falloff`() {
        val w = 40
        val r = 3
        val src = IntArray(w * w)
        for (i in src.indices) {
            val x = i % w
            val y = i / w
            val inside = x in 15..24 && y in 15..24
            src[i] = if (inside) argbPixel(255, Color.RED) else 0
        }

        val out = MaskedEffectRenderer.boxBlurPixels(src, w, w, r)
        fun findFirstAlphaBeyond(start: Int): Int {
            for (x in start until w - 1) {
                val a = (out[20 * w + x] ushr 24) and 0xff
                if (a > 0) return x
            }
            return -1
        }
        // Gap between the 10px-wide interior and the first blurred pixel.
        val leftGap = findFirstAlphaBeyond(0)
        assertTrue("blur spreads a few px beyond the interior", leftGap >= 12 && leftGap <= 18)
        assertTrue("outside the spread there is still transparency", out[20 * w + 39] == 0)
    }

    @Test
    fun `output is always valid premultiplied - channels never exceed alpha`() {
        val w = 30
        val r = 3
        val src = IntArray(w * w)
        for (i in src.indices) {
            val x = i % w
            val y = i / w
            val inside = x in 8..21 && y in 8..21
            src[i] = if (inside) argbPixel(255, Color.RED) else 0
        }

        val out = MaskedEffectRenderer.boxBlurPixels(src, w, w, r)
        out.forEachIndexed { index, px ->
            val a = (px ushr 24) and 0xff
            val rC = (px ushr 16) and 0xff
            val gC = (px ushr 8) and 0xff
            val bC = px and 0xff
            assertTrue("alpha $a nums r $rC at $index", rC <= a || a == 0)
            assertTrue("alpha $a nums g $gC at $index", gC <= a || a == 0)
            assertTrue("alpha $a nums b $bC at $index", bC <= a || a == 0)
        }
    }

    @Test
    fun `blur fades a lone bright pixel without exceeding its alpha`() {
        val w = 30
        val r = 2
        val src = IntArray(w * w)
        for (i in src.indices) {
            val x = i % w
            val y = i / w
            src[i] = if ((x == 15) and (y == 15)) argbPixel(255, Color.GREEN) else 0
        }

        val out = MaskedEffectRenderer.boxBlurPixels(src, w, w, r)
        val px = out[15 * w + 15]
        val a = (px ushr 24) and 0xff
        val green = (px ushr 8) and 0xff
        assertTrue("single bright pixel spreads", a in 1..255)
        assertTrue("premultiplied colour is dimmer than the source", green < 255)
        assertTrue("premultiplied colour never exceeds alpha", green <= a)
    }

    @Test
    fun `zero radius is a passthrough for opaque pixels`() {
        val w = 10
        val src = IntArray(w * w) { argbPixel(255, Color.BLUE) }
        val out = MaskedEffectRenderer.boxBlurPixels(src, w, w, 0)
        assertEquals(src.toList(), out.toList())
    }
}