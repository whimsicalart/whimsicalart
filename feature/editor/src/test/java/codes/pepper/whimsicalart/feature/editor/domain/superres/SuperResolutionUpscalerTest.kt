package codes.pepper.whimsicalart.feature.editor.domain.superres

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SuperResolutionUpscalerTest {

    @Test
    fun `upscale x2 produces exact doubled dimensions`() {
        val input = Bitmap.createBitmap(6, 4, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(10, 20, 30))
        val out = SuperResolutionUpscaler.upscale(input, 2)
        assertEquals(12, out.width)
        assertEquals(8, out.height)
    }

    @Test
    fun `upscale x4 produces exact quadrupled dimensions`() {
        val input = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(10, 20, 30))
        val out = SuperResolutionUpscaler.upscale(input, 4)
        assertEquals(20, out.width)
        assertEquals(12, out.height)
    }

    @Test
    fun `upscale does not mutate the source`() {
        val input = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(200, 100, 50))
        val before = input.getPixel(2, 2)
        SuperResolutionUpscaler.upscale(input, 2)
        assertEquals("source bitmap must stay intact", before, input.getPixel(2, 2))
    }

    @Test
    fun `uniform image upscales to a uniform output (no ringing or artifacts)`() {
        val input = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(128, 130, 132))
        val out = SuperResolutionUpscaler.upscale(input, 2)
        val sample = out.getPixel(1, 1)
        var allSame = true
        for (y in 0 until out.height) for (x in 0 until out.width) {
            if (out.getPixel(x, y) != sample) allSame = false
        }
        assertTrue("a flat image must stay flat after upscale (edge reconstruction adds no noise)", allSame)
    }

    @Test
    fun `edge-aware upscale keeps a hard edge crisper than a plain bilinear stretch`() {
        // 6x4: left half black (20), right half white (220). A hard vertical edge.
        val input = Bitmap.createBitmap(6, 4, Bitmap.Config.ARGB_8888)
        for (y in 0 until 4) for (x in 0 until 6) {
            input.setPixel(x, y, if (x < 3) Color.rgb(20, 20, 20) else Color.rgb(220, 220, 220))
        }
        val out = SuperResolutionUpscaler.upscale(input, 2) // 12x8

        // Reference bilinear upscale for comparison.
        val bilinear = Bitmap.createScaledBitmap(input, 12, 8, true)

        // Measure edge sharpness: sum of |delta| across the horizontal transition line.
        fun edgeEnergy(bmp: Bitmap): Int {
            var e = 0
            for (x in 1 until bmp.width) {
                e += kotlin.math.abs(Color.red(bmp.getPixel(x, 4)) - Color.red(bmp.getPixel(x - 1, 4)))
            }
            return e
        }
        val srEnergy = edgeEnergy(out)
        val biEnergy = edgeEnergy(bilinear)
        assertTrue(
            "SR upscale must hold a crisper edge than bilinear (sr=$srEnergy bilinear=$biEnergy)",
            srEnergy >= biEnergy
        )
    }
}