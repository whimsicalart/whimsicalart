package codes.pepper.whimsicalart.feature.editor.domain.removal

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiffusionInpainterTest {

    private fun solidBitmap(color: Int): Bitmap {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return b
    }

    private fun mask(x: Int, y: Int, size: Int = 6): Bitmap {
        val m = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        m.eraseColor(Color.TRANSPARENT)
        for (yy in y until y + size) {
            for (xx in x until x + size) {
                m.setPixel(xx, yy, Color.argb(255, 255, 255, 255))
            }
        }
        return m
    }

    @Test
    fun `removed region is replaced with surrounding colour`() {
        val background = Color.rgb(200, 100, 50)
        // A uniform background with a black "object" in the centre to remove.
        val scarred = solidBitmap(background)
        for (yy in 13..18) {
            for (xx in 13..18) {
                scarred.setPixel(xx, yy, Color.rgb(0, 0, 0))
            }
        }
        val out = DiffusionInpainter.inpaint(scarred, mask(13, 13, 6))

        val centre = out!!.getPixel(16, 16)
        assertNotEquals("centre is no longer the black object", 0, centre)
        // On a uniform field every known neighbour is (200, 100, 50), so the
        // fill converges exactly on the background colour.
        assertEquals(background, centre)
    }

    @Test
    fun `empty mask leaves the image effectively unchanged`() {
        val source = solidBitmap(Color.rgb(10, 20, 30))
        val emptyMask = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        emptyMask.eraseColor(Color.TRANSPARENT)
        val out = DiffusionInpainter.inpaint(source, emptyMask)
        // No mask pixels -> no fill, so the bitmap is a copy of the source.
        assertEquals(Color.rgb(10, 20, 30), out!!.getPixel(5, 5))
        assertNotEquals("inpaint returns a fresh bitmap", source, out)
    }

    @Test
    fun `non 8888 source is coerced to ARGB 8888`() {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.RGB_565)
        source.eraseColor(Color.rgb(120, 120, 120))
        val out = DiffusionInpainter.inpaint(source, mask(4, 4))
        assertEquals(Bitmap.Config.ARGB_8888, out!!.config)
        assertNotEquals("content remains after fill", 0, out!!.getPixel(8, 8))
        source.recycle()
    }
}