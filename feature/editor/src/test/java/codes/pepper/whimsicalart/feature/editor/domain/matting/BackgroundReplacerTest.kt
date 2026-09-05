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
class BackgroundReplacerTest {

    private fun solid(w: Int, h: Int, color: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun should_fill_background_region_with_background_image() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(1, 1, Color.WHITE)
        }

        val result = BackgroundReplacer.composite(source, mask, background)

        assertEquals("fully-background pixel shows the replacement", Color.BLUE, result.getPixel(0, 0))
    }

    @Test
    fun should_keep_subject_pixels_untouched() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(1, 1, Color.WHITE)
        }

        val result = BackgroundReplacer.composite(source, mask, background)

        assertEquals("subject pixel keeps the original", Color.RED, result.getPixel(1, 1))
    }

    @Test
    fun should_cover_scale_to_exact_target_size() {
        val background = solid(100, 50, Color.BLUE)

        val result = BackgroundReplacer.coverScale(background, 50, 50)

        assertEquals(50, result.width)
        assertEquals(50, result.height)
    }

    @Test
    fun should_support_smaller_background_than_source() {
        val source = solid(100, 100, Color.RED)
        val background = solid(20, 10, Color.BLUE)
        val mask = solid(100, 100, Color.TRANSPARENT)

        val result = BackgroundReplacer.composite(source, mask, background)

        assertEquals(100, result.width)
        assertEquals(100, result.height)
        assertEquals("up-scaled cover background fills even corners", Color.BLUE, result.getPixel(3, 3))
    }
}
