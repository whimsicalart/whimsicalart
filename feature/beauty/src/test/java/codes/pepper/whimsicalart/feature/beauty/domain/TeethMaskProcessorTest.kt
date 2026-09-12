package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class TeethMaskProcessorTest {

    private fun solidLuminance(lum: Int): Int {
        val c = lum.coerceIn(0, 255)
        return Color.rgb(c, c, c)
    }

    private fun imageWithTeethPixel(): Bitmap {
        val b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.rgb(60, 40, 40)) // dark mouth interior (gum/gloss)
        b.setPixel(8, 8, solidLuminance(240)) // a bright "tooth" pixel
        b.setPixel(10, 10, Color.rgb(60, 50, 45)) // dark gum below
        return b
    }

    private fun centeredAperture(): List<PointF> {
        // Lens aperture spanning (3,3)-(13,13).
        return listOf(
            PointF(3f, 8f),
            PointF(8f, 3f),
            PointF(13f, 8f),
            PointF(8f, 13f)
        )
    }

    @Test
    fun `bright tooth pixel yields high alpha`() {
        val mask = TeethMaskProcessor.toothMask(imageWithTeethPixel(), centeredAperture())
        assertNotNull(mask)
        val alpha = Color.alpha(mask!!.getPixel(8, 8))
        assertTrue("tooth alpha high, was $alpha", alpha > 120)
        mask.recycle()
    }

    @Test
    fun `dark gum pixel inside the aperture stays unmasked`() {
        val mask = TeethMaskProcessor.toothMask(imageWithTeethPixel(), centeredAperture())
        assertNotNull(mask)
        // (10,10) is in the aperture but dark -> luminance gate keeps it clear.
        val alpha = Color.alpha(mask!!.getPixel(10, 10))
        assertEquals(0, alpha)
        mask.recycle()
    }

    @Test
    fun `pixel outside the aperture is unmasked even if bright`() {
        val bright = imageWithTeethPixel()
        bright.setPixel(1, 1, solidLuminance(250))
        val mask = TeethMaskProcessor.toothMask(bright, centeredAperture())
        assertNotNull(mask)
        assertEquals(0, Color.alpha(mask!!.getPixel(1, 1)))
        mask.recycle()
        bright.recycle()
    }

    @Test
    fun `null polygon returns null mask`() {
        assertNull(TeethMaskProcessor.toothMask(imageWithTeethPixel(), null))
    }

    @Test
    fun `luminance of white is high and of black is zero`() {
        assertEquals(255f, TeethMaskProcessor.luminance(Color.WHITE.toInt()), 1f)
        assertEquals(0f, TeethMaskProcessor.luminance(Color.BLACK.toInt()), 1f)
    }

    @Test
    fun `mask is source sized ARGB 8888`() {
        val mask = TeethMaskProcessor.toothMask(imageWithTeethPixel(), centeredAperture())
        assertEquals(Bitmap.Config.ARGB_8888, mask!!.config)
        assertEquals(16, mask.width)
        assertEquals(16, mask.height)
        mask.recycle()
    }

    @Test
    fun `contains is true inside and false outside the lens`() {
        val polygon = centeredAperture()
        assertTrue(TeethMaskProcessor.contains(polygon, 8f, 8f))
        assertEquals(false, TeethMaskProcessor.contains(polygon, 0f, 0f))
        assertEquals(false, TeethMaskProcessor.contains(emptyList(), 8f, 8f))
    }

    /**
     * Regression: the teeth aperture can now be the MediaPipe inner-lip ring, an
     * asymmetric CONCAVE shape (dips at the corners). The ray-cast containment
     * test must keep a point inside the notch inside and a point in the outer-lip
     * rim outside — the whitening region hugs the teeth.
     */
    @Test
    fun `large sparse frame masks only the aperture bbox - region-scoped read lands at the right offset`() {
        // PERF tracker: the source read is Rect-scoped to the aperture bbox. A
        // large frame with a small aperture in the bottom-right corner exercises
        // the region offset math — a wrong offset would either mask the wrong
        // pixel or leave the actual tooth pixel unmasked.
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.rgb(60, 40, 40))
        b.setPixel(200, 220, solidLuminance(240)) // bright "tooth", bottom-right
        b.setPixel(10, 10, solidLuminance(250))   // bright pixel far outside the aperture
        val aperture = listOf(
            PointF(190f, 230f),
            PointF(200f, 210f),
            PointF(210f, 230f),
            PointF(200f, 238f)
        )
        val mask = TeethMaskProcessor.toothMask(b, aperture)
        assertNotNull(mask)
        assertEquals(256, mask!!.width)
        assertEquals(256, mask.height)
        // Mask stays source-sized: everything outside the aperture bbox is zero.
        assertEquals(0, Color.alpha(mask.getPixel(0, 0)))
        assertEquals(0, Color.alpha(mask.getPixel(10, 10)))
        assertEquals(0, Color.alpha(mask.getPixel(255, 0)))
        assertEquals(0, Color.alpha(mask.getPixel(0, 255)))
        // The bright tooth pixel INSIDE the aperture is masked...
        assertTrue("tooth alpha high, was ${Color.alpha(mask.getPixel(200, 220))}",
            Color.alpha(mask.getPixel(200, 220)) > 120)
        // ...while the dark gum around it in the same bbox stays clear.
        assertEquals(0, Color.alpha(mask.getPixel(195, 220)))
        mask.recycle()
        b.recycle()
    }

    @Test
    fun `region-scoped read is byte-identical to the full-frame reference`() {
        // PARITY (PERF tracker): the Rect-scoped getPixels must produce the
        // exact same source-sized mask as the old full-frame read — the same
        // pixels go through the same floor division, so the swap is
        // output-identical.
        val b = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.rgb(70, 45, 45))
        for (i in 0 until 30) {
            b.setPixel(5 + i, 8, solidLuminance((200 + i % 4 * 14).coerceAtMost(255)))
        }
        b.setPixel(32, 24, solidLuminance(235))
        val aperture = listOf(
            PointF(4f, 4f),
            PointF(30f, 2f),
            PointF(58f, 26f),
            PointF(10f, 44f)
        )
        val mask = TeethMaskProcessor.toothMask(b, aperture)
        assertNotNull(mask)
        val lhs = IntArray(64 * 48)
        mask!!.getPixels(lhs, 0, 64, 0, 0, 64, 48)
        val rhs = IntArray(64 * 48)
        fullFrameReference(b, aperture).getPixels(rhs, 0, 64, 0, 0, 64, 48)
        assertTrue("region-scoped mask must equal the full-frame reference pixel-for-pixel",
            lhs.contentEquals(rhs))
        mask.recycle()
        b.recycle()
    }

    /** In-test replication of the OLD full-frame-read reference, as a control. */
    private fun fullFrameReference(source: Bitmap, polygon: List<PointF>): Bitmap {
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val threshold = TeethMaskProcessor.LUMINANCE_THRESHOLD
        val floor = TeethMaskProcessor.LUMINANCE_FLOOR
        val gain = 255f / (floor - threshold).coerceAtLeast(1f)
        val left = polygon.minOf { it.x }.toInt().coerceIn(0, w - 1)
        val top = polygon.minOf { it.y }.toInt().coerceIn(0, h - 1)
        val right = (polygon.maxOf { it.x }.toInt() + 1).coerceIn(left + 1, w)
        val bottom = (polygon.maxOf { it.y }.toInt() + 1).coerceIn(top + 1, h)
        for (y in top until bottom) {
            for (x in left until right) {
                if (!TeethMaskProcessor.contains(polygon, x + 0.5f, y + 0.5f)) continue
                val srcIdx = y * w + x
                val lum = TeethMaskProcessor.luminance(src[srcIdx])
                val t = (lum - threshold).coerceAtLeast(0f)
                val alpha = (t * gain).roundToInt().coerceIn(0, 255)
                if (alpha > 0) {
                    out[srcIdx] = Color.argb(alpha, 255, 255, 255)
                }
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    /** Regression: the teeth aperture can now be the MediaPipe inner-lip ring, an
     * asymmetric CONCAVE shape (dips at the corners). The ray-cast containment
     * test must keep a point inside the notch inside and a point in the outer-lip
     * rim outside — the whitening region hugs the teeth. */
    @Test
    fun `contains handles a concave inner-lip ring`() {
        // Concave ring: mouth corners dip to y=9 while the upper lip apexes at
        // y=3 and the lower at y=13.
        val ring = listOf(
            PointF(3f, 9f),   // left corner (the concave notch)
            PointF(5f, 5f),
            PointF(8f, 3f),   // upper apex
            PointF(11f, 5f),
            PointF(13f, 9f),  // right corner (concave)
            PointF(11f, 13f),
            PointF(8f, 13f),  // lower apex
            PointF(5f, 13f)
        )
        assertTrue("inside aperture", TeethMaskProcessor.contains(ring, 8f, 8f))
        assertTrue("inside upper region under the rim", TeethMaskProcessor.contains(ring, 5f, 6f))
        assertTrue("inside lower region over the rim", TeethMaskProcessor.contains(ring, 11f, 12f))
        assertEquals(false, TeethMaskProcessor.contains(ring, 1.5f, 9f))   // left of the notch
        assertEquals(false, TeethMaskProcessor.contains(ring, 14.5f, 9f))  // right of the notch
        assertEquals(false, TeethMaskProcessor.contains(ring, 8f, 2f))     // above the lip
        assertEquals(false, TeethMaskProcessor.contains(ring, 8f, 14f))    // below the lip
    }
}