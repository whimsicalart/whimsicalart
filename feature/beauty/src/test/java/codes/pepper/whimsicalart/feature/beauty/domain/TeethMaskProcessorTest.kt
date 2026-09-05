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
}