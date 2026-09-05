package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MaskFactoryTest {

    @Test
    fun should_encode_confidence_into_alpha_channel() {
        val confidence = floatArrayOf(0f, 0.5f, 1f, 1f)

        val mask = MaskFactory.fromConfidence(confidence, width = 2, height = 2)

        assertEquals(0, Color.alpha(mask.getPixel(0, 0)))
        assertEquals(127, Color.alpha(mask.getPixel(1, 0)))
        assertEquals(255, Color.alpha(mask.getPixel(0, 1)))
        assertEquals(255, Color.alpha(mask.getPixel(1, 1)))
    }

    @Test
    fun should_return_transparent_mask_on_wrong_sized_confidence() {
        val mask = MaskFactory.fromConfidence(floatArrayOf(0f, 1f), width = 4, height = 4)

        assertEquals(0, Color.alpha(mask.getPixel(2, 2)))
    }

    @Test
    fun should_upscale_to_target_dimensions() {
        val small = MaskFactory.fromConfidence(floatArrayOf(1f), width = 1, height = 1)

        val big = MaskFactory.upscale(small, targetWidth = 3, targetHeight = 3)

        assertEquals(3, big.width)
        assertEquals(3, big.height)
        assertEquals(255, Color.alpha(big.getPixel(0, 0)))
    }

    @Test
    fun should_threshold_to_hard_mask() {
        val confidence = floatArrayOf(0.9f, 0.2f)
        val mask = MaskFactory.fromConfidence(confidence, 1, 2)

        val hard = MaskFactory.threshold(mask, level = 128)

        assertEquals(255, Color.alpha(hard.getPixel(0, 0)))
        assertEquals(0, Color.alpha(hard.getPixel(0, 1)))
    }
}
