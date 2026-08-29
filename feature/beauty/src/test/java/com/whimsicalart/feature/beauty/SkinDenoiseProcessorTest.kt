package com.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkinDenoiseProcessorTest {

    @Test
    fun `estimateThreshold is positive and finite`() {
        val t = SkinDenoiseProcessor.estimateThreshold(4000, 3000)
        assertTrue(t > 0f)
        assertTrue(t.isFinite())
    }

    @Test
    fun `estimateThreshold scales with image size`() {
        val small = SkinDenoiseProcessor.estimateThreshold(200, 200)
        val large = SkinDenoiseProcessor.estimateThreshold(4000, 3000)
        assertTrue("larger images should estimate a larger threshold", large > small)
    }

    @Test
    fun `estimateThreshold matches empirical calibration`() {
        // VGA (640x480, diagonal 800) -> ~0.8
        assertEquals(0.8f, SkinDenoiseProcessor.estimateThreshold(640, 480), 0.01f)
        // 2MP (1600x1200, diagonal 2000) -> ~2.0
        assertEquals(2.0f, SkinDenoiseProcessor.estimateThreshold(1600, 1200), 0.01f)
    }

    @Test
    fun `estimateThreshold is floored for tiny images`() {
        assertTrue(SkinDenoiseProcessor.estimateThreshold(10, 10) >= 0.1f)
    }

    @Test
    fun `denoise returns same dimensions and is mutable`() {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(128, 96, 64))

        val out = SkinDenoiseProcessor.denoise(bitmap, 0.5f)

        assertEquals(128, out.width)
        assertEquals(128, out.height)
        assertEquals(Bitmap.Config.ARGB_8888, out.config)
        // A flat image has no detail, so it should stay near its original value.
        val outColor = out.getPixel(50, 50)
        assertEquals(Color.rgb(128, 96, 64), outColor)
    }

    @Test
    fun `denoise with zero softness differs from permissive softness`() {
        fun noisy(): Bitmap {
            val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            for (y in 0 until 128) {
                for (x in 0 until 128) {
                    val n = ((x * 73856093) xor (y * 19349663)) ushr 16 and 0xFF
                    bmp.setPixel(x, y, Color.rgb(n, n, n))
                }
            }
            return bmp
        }

        val aggressive = SkinDenoiseProcessor.denoise(noisy(), 0f)
        val permissive = SkinDenoiseProcessor.denoise(noisy(), 1f)
        // Both output a valid image with the original dimensions.
        assertEquals(128, aggressive.width)
        assertEquals(128, permissive.width)
        // The permissive (softness = 1) pass keeps all detail, so the two
        // results should not be pixel-identical on noisy content.
        assertTrue(bitmapsDiffer(aggressive, permissive))
    }

    @Test
    fun `denoise preserves dimensions for images larger than the work bound`() {
        // Larger than WORK_MAX_DIMENSION (1024): the wavelet pass runs on a
        // downscaled canvas and the result is scaled back up, so the output
        // must keep the full input size and stay mutable.
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(130, 100, 70))

        val out = SkinDenoiseProcessor.denoise(bitmap, 0.5f)

        assertEquals(1600, out.width)
        assertEquals(1200, out.height)
        // A flat image stays near its original value after the round-trip.
        val outColor = out.getPixel(800, 600)
        assertEquals(Color.rgb(130, 100, 70), outColor)
    }

    @Test
    fun `denoise does not crash on tiny images where mirror indices need clamping`() {
        // Short side (24) is well below 2*2^4 = 32, which used to drive the
        // mirrored index out of bounds at the largest wavelet level.
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        for (y in 0 until 24) {
            for (x in 0 until 24) {
                val n = ((x * 73856093) xor (y * 19349663)) ushr 16 and 0xFF
                bitmap.setPixel(x, y, Color.rgb(n, n, n))
            }
        }

        val out = SkinDenoiseProcessor.denoise(bitmap, 0f)
        val permissive = SkinDenoiseProcessor.denoise(bitmap, 1f)

        assertEquals(24, out.width)
        assertEquals(24, out.height)
        assertEquals(Bitmap.Config.ARGB_8888, out.config)
        assertTrue(bitmapsDiffer(out, permissive))
    }

    private fun bitmapsDiffer(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return true
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) return true
            }
        }
        return false
    }
}
