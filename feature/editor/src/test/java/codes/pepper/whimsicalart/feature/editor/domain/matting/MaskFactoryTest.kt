package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
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

    private fun pixels(bmp: Bitmap): IntArray {
        val p = IntArray(bmp.width * bmp.height)
        bmp.getPixels(p, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return p
    }

    @Test
    fun from_confidence_is_byte_identical_to_the_scalar_per_pixel_write() {
        // The batched IntArray + setPixels write-out must reproduce the
        // per-pixel setPixel path exactly, including clamped end values.
        val w = 9
        val h = 7
        val confidence = FloatArray(w * h) { (it % 31) / 37f }
        val mask = MaskFactory.fromConfidence(confidence, w, h)

        val scalar = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = (confidence[y * w + x] * 255f).toInt().coerceIn(0, 255)
                scalar.setPixel(x, y, Color.argb(a, 255, 255, 255))
            }
        }
        assertArrayEquals(pixels(scalar), pixels(mask))
    }

    @Test
    fun threshold_is_byte_identical_to_the_scalar_per_pixel_reference() {
        // An alpha ramp through 0..255 exercises every boundary side; the
        // batched read+threshold+write must equal the per-pixel getPixel/setPixel
        // shape, with RGB pinned to white on both sides of the cut.
        val w = 32
        val h = 8
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                src.setPixel(x, y, Color.argb(y * 32 + x, 40, 80, 120))
            }
        }
        val hard = MaskFactory.threshold(src, level = 128)

        val scalar = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = if (Color.alpha(src.getPixel(x, y)) >= 128) 255 else 0
                scalar.setPixel(x, y, Color.argb(a, 255, 255, 255))
            }
        }
        assertArrayEquals(pixels(scalar), pixels(hard))
    }
}
