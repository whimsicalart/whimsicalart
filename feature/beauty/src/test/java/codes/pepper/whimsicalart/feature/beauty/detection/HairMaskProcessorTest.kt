package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HairMaskProcessorTest {

    private fun emptyMask(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    @Test
    fun `toPath returns null when there is no hair`() {
        val mask = emptyMask(32, 32)
        assertNull(HairMaskProcessor.toPath(mask))
    }

    @Test
    fun `toPath returns null when the mask is too sparse`() {
        val mask = emptyMask(16, 16)
        mask.setPixel(2, 2, Color.argb(255, 255, 255, 255))
        assertNull(HairMaskProcessor.toPath(mask))
    }

    @Test
    fun `toPath outlines a rectangular hair region`() {
        val mask = emptyMask(40, 40)
        // Solid hair column from y=8..31, x=12..27.
        for (y in 8..31) {
            for (x in 12..27) {
                mask.setPixel(x, y, Color.argb(255, 255, 255, 255))
            }
        }
        val path = HairMaskProcessor.toPath(mask)
        assertNotNull(path)
        val bounds = RectF()
        path?.computeBounds(bounds, true)
        assertTrue("left edge hugs the blob's left (12)", bounds.left in 10f..14f)
        assertTrue("right edge hugs the blob's right (27)", bounds.right in 25f..29f)
        assertTrue("top hugs the blob's top (8)", bounds.top in 6f..10f)
        assertTrue("bottom hugs the blob's bottom (31)", bounds.bottom in 29f..33f)
    }

    @Test
    fun `toPath ignores low-alpha pixels below the threshold`() {
        val mask = emptyMask(20, 20)
        var rowsFilled = 0
        for (y in 2..11) {
            for (x in 5..14) {
                mask.setPixel(x, y, Color.argb(200, 255, 255, 255))
            }
            rowsFilled++
        }
        // A single dark pixel on its own row must not affect the outline much.
        mask.setPixel(10, 18, Color.argb(60, 255, 255, 255))
        val path = HairMaskProcessor.toPath(mask, threshold = 128)
        assertNotNull(path)
        val bounds = RectF()
        path?.computeBounds(bounds, true)
        assertTrue("teen rows of hair give a solid outline", bounds.height() >= 8f)
    }
}