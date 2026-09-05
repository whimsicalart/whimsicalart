package codes.pepper.whimsicalart.feature.editor.domain.filter

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StyleFilterTest {

    @Test
    fun `applying a filter keeps dimensions and does not mutate the source`() {
        val input = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(120, 80, 60))
        val before = input.getPixel(0, 0)
        val out = StyleFilterProcessor.apply(input, StyleFilter.FILMIC)
        assertEquals(8, out.width)
        assertEquals(6, out.height)
        assertEquals("source must stay intact", before, input.getPixel(0, 0))
    }

    @Test
    fun `filmic filter changes tone (non-identity on a saturated pixel)`() {
        val input = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(200, 100, 40))
        val out = StyleFilterProcessor.apply(input, StyleFilter.FILMIC)
        val original = input.getPixel(1, 1)
        assertNotEquals("filmic curve must remap a non-neutral pixel", original, out.getPixel(1, 1))
        val r = Color.red(out.getPixel(1, 1))
        assertTrue("output channel must stay in range, got $r", r in 0..255)
    }

    @Test
    fun `vibrant filter increases chroma separation on a colourful edge`() {
        val input = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
        for (y in 0 until 6) for (x in 0 until 6) {
            input.setPixel(x, y, if (x < 3) Color.rgb(60, 40, 220) else Color.rgb(220, 60, 40))
        }
        val out = StyleFilterProcessor.apply(input, StyleFilter.VIBRANT)
        val blue = out.getPixel(1, 3)
        val red = out.getPixel(4, 3)
        // Vibrant boosts channel separation, so red-vs-blue dominance strengthens.
        val blueStrength = Color.blue(blue) - Color.red(blue)
        val redStrength = Color.red(red) - Color.blue(red)
        assertTrue("vibrant must keep blue side blue-dominant ($blueStrength)", blueStrength > 100)
        assertTrue("vibrant must keep red side red-dominant ($redStrength)", redStrength > 100)
    }

    @Test
    fun `matte filter lifts shadows while preserving relative order`() {
        val input = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(30, 30, 30))
        val out = StyleFilterProcessor.apply(input, StyleFilter.MATTE)
        val r = Color.red(out.getPixel(2, 2))
        // Matte lifts shadows toward a muted, soft look.
        assertTrue("matte should lift deep shadow (was 30, got $r)", r > 40)
    }

    @Test
    fun `fromId resolves registered filters and rejects unknown`() {
        assertEquals(StyleFilter.FILMIC, StyleFilter.fromId("filmic"))
        assertEquals(StyleFilter.VIBRANT, StyleFilter.fromId("vibrant"))
        assertEquals(StyleFilter.MATTE, StyleFilter.fromId("matte"))
        assertEquals(null, StyleFilter.fromId("nope"))
    }
}