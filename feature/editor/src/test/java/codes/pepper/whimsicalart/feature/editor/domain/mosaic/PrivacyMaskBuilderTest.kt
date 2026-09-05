package codes.pepper.whimsicalart.feature.editor.domain.mosaic

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivacyMaskBuilderTest {

    private fun norm(value: Float) = ((value * 1000).toInt()) / 1000f

    @Test
    fun `a centred face yields a normalized centred region`() {
        // 1000x1000 image, face occupies x 400-600, y 300-700.
        val rects = PrivacyMaskBuilder.suggestedRegions(
            faceRects = listOf(Rect(400, 300, 600, 700)),
            imageWidth = 1000,
            imageHeight = 1000,
            marginRatio = 0f
        )
        assertEquals(1, rects.size)
        val r = rects[0]
        assertEquals(0.4f, norm(r.left), 0.001f)
        assertEquals(0.3f, norm(r.top), 0.001f)
        assertEquals(0.6f, norm(r.right), 0.001f)
        assertEquals(0.7f, norm(r.bottom), 0.001f)
    }

    @Test
    fun `a face near the left edge is expanded but clamped to 0`() {
        val rects = PrivacyMaskBuilder.suggestedRegions(
            faceRects = listOf(Rect(10, 100, 110, 300)),
            imageWidth = 1000,
            imageHeight = 1000
        )
        val r = rects[0]
        // marginX = 100*0.25 = 25; left = (10-25)/1000 = -0.015 -> clamped to 0
        assertEquals(0f, r.left, 0.001f)
        assertTrue(r.left >= 0f)
        assertTrue(r.right > r.left)
    }

    @Test
    fun `face near the bottom right clamps to 1`() {
        val rects = PrivacyMaskBuilder.suggestedRegions(
            faceRects = listOf(Rect(900, 900, 999, 999)),
            imageWidth = 1000,
            imageHeight = 1000
        )
        val r = rects[0]
        // marginY = 99*0.25 ~ 24.75; bottom = (999+24.75)/1000 ~ 1.024 -> clamp 1
        assertEquals(1f, r.bottom, 0.001f)
        assertEquals(1f, r.right, 0.001f)
    }

    @Test
    fun `no faces produces no regions`() {
        val rects = PrivacyMaskBuilder.suggestedRegions(
            faceRects = emptyList(),
            imageWidth = 1000,
            imageHeight = 1000
        )
        assertTrue(rects.isEmpty())
    }

    @Test
    fun `degenerate image dimensions produce no regions`() {
        val rects = PrivacyMaskBuilder.suggestedRegions(
            faceRects = listOf(Rect(0, 0, 100, 100)),
            imageWidth = 0,
            imageHeight = 0
        )
        assertTrue(rects.isEmpty())
    }

    @Test
    fun `multiple faces yield multiple regions`() {
        val rects = PrivacyMaskBuilder.suggestedRegions(
            faceRects = listOf(Rect(100, 100, 200, 250), Rect(700, 100, 800, 250)),
            imageWidth = 1000,
            imageHeight = 1000,
            marginRatio = 0f
        )
        assertEquals(2, rects.size)
    }
}