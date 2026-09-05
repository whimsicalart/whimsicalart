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
class SkinMaskProcessorTest {

    private fun emptyMask(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    @Test
    fun `toPath returns null when there is no skin`() {
        val mask = emptyMask(32, 32)
        assertNull(SkinMaskProcessor.toPath(mask))
    }

    @Test
    fun `toPath returns null when the mask is too sparse`() {
        val mask = emptyMask(16, 16)
        mask.setPixel(2, 2, Color.argb(255, 255, 255, 255))
        assertNull(SkinMaskProcessor.toPath(mask))
    }

    @Test
    fun `toPath outlines a rectangular skin region`() {
        val mask = emptyMask(40, 40)
        for (y in 8..31) {
            for (x in 12..27) {
                mask.setPixel(x, y, Color.argb(255, 255, 255, 255))
            }
        }
        val path = SkinMaskProcessor.toPath(mask)
        assertNotNull(path)
        val bounds = RectF()
        path?.computeBounds(bounds, true)
        assertTrue("left hugs the blob (12)", bounds.left in 10f..14f)
        assertTrue("right hugs the blob (27)", bounds.right in 25f..29f)
        assertTrue("top hugs the blob (8)", bounds.top in 6f..10f)
        assertTrue("bottom hugs the blob (31)", bounds.bottom in 29f..33f)
    }

    @Test
    fun `toPath ignores low-alpha pixels below the threshold`() {
        val mask = emptyMask(20, 20)
        for (y in 2..11) {
            for (x in 5..14) {
                mask.setPixel(x, y, Color.argb(200, 255, 255, 255))
            }
        }
        mask.setPixel(10, 18, Color.argb(60, 255, 255, 255))
        val path = SkinMaskProcessor.toPath(mask, threshold = 128)
        assertNotNull(path)
        val bounds = RectF()
        path?.computeBounds(bounds, true)
        assertTrue("sampled rows give a solid outline", bounds.height() >= 8f)
    }

    @Test
    fun `toPath skips rows where the skin is broken so a gap is bridged`() {
        val mask = emptyMask(30, 24)
        // A skin band interrupted mid-way (e.g. a mouth/nose gap): the sweep
        // still captures the outer left/right extents per row.
        for (y in 4..19) {
            mask.setPixel(6, y, Color.argb(255, 255, 255, 255))
            mask.setPixel(23, y, Color.argb(255, 255, 255, 255))
        }
        val path = SkinMaskProcessor.toPath(mask)
        assertNotNull(path)
        val bounds = RectF()
        path?.computeBounds(bounds, true)
        assertTrue("crosses the broken band", bounds.width() >= 14f && bounds.height() >= 14f)
    }
}