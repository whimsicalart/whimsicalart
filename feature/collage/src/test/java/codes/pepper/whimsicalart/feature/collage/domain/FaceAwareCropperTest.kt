package codes.pepper.whimsicalart.feature.collage.domain

import android.graphics.PointF
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaceAwareCropperTest {

    @Test
    fun `wider-than-target image with no face centre crops to the middle`() {
        // 200x100 source into a square cell (aspect 1) -> horizontal band cropped.
        val rect = FaceAwareCropper.cropWindow(200, 100, 1f, null)
        assertEquals(Rect(50, 0, 150, 100), rect)
    }

    @Test
    fun `taller-than-target image with no face centre crops top and bottom evenly`() {
        // 100x200 source into a square cell -> vertical band cropped.
        val rect = FaceAwareCropper.cropWindow(100, 200, 1f, null)
        assertEquals(Rect(0, 50, 100, 150), rect)
    }

    @Test
    fun `face at horizontal edge shifts the crop window toward it and stays in bounds`() {
        // 200x100 into square cell; face near the left edge (fx = 0.1).
        val rect = FaceAwareCropper.cropWindow(200, 100, 1f, PointF(0.1f, 0.5f))
        // The 100-wide window should sit at the left edge (cannot go negative).
        assertEquals(0, rect.left)
        assertEquals(100, rect.right)
        assertTrue("face screen x within window", rect.left <= 20 && 20 <= rect.right)
    }

    @Test
    fun `face at horizontal middle keeps the crop centred`() {
        val rect = FaceAwareCropper.cropWindow(200, 100, 1f, PointF(0.5f, 0.5f))
        assertEquals(Rect(50, 0, 150, 100), rect)
    }

    @Test
    fun `face near bottom shifts the vertical crop toward it while staying in bounds`() {
        // 100x200 into square; face low (fy = 0.85).
        val rect = FaceAwareCropper.cropWindow(100, 200, 1f, PointF(0.5f, 0.85f))
        assertEquals(100, rect.height())
        assertTrue("crop shifted downward", rect.top > 50)
        assertTrue("crop stays in bounds", rect.top <= 100)
    }

    @Test
    fun `degenerate empty source returns an empty rect without crashing`() {
        val rect = FaceAwareCropper.cropWindow(0, 0, 1f, PointF(0.5f, 0.5f))
        assertEquals(Rect(0, 0, 0, 0), rect)
    }
}