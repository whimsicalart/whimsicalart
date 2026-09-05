package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrTextExtractorTest {

    private val IMAGE_W = 1000
    private val IMAGE_H = 800

    private fun line(text: String, left: Float, top: Float, right: Float, bottom: Float): OcrLine =
        OcrLine(text, RectF(left, top, right, bottom))

    @Test
    fun `junk and short fragments are dropped`() {
        val lines = listOf(
            line("---", 0f, 0f, 50f, 20f),
            line("a", 0f, 40f, 20f, 60f),
            line("???", 0f, 80f, 40f, 100f)
        )
        assertTrue(OcrTextExtractor.overlayFragments(lines, IMAGE_W, IMAGE_H).isEmpty())
    }

    @Test
    fun `legible line is kept and normalized`() {
        val lines = listOf(line("  Hello   World  ", 100f, 100f, 300f, 140f))
        val fragments = OcrTextExtractor.overlayFragments(lines, IMAGE_W, IMAGE_H)
        assertEquals(1, fragments.size)
        assertEquals("Hello World", fragments[0].text)
    }

    @Test
    fun `anchor reflects bounding box center`() {
        val lines = listOf(line("Hi", 100f, 100f, 300f, 200f)) // center (200,150)
        val fragments = OcrTextExtractor.overlayFragments(lines, IMAGE_W, IMAGE_H)
        assertEquals(0.2f, fragments[0].normalizedX, 0.001f)
        assertEquals(150f / 800f, fragments[0].normalizedY, 0.001f)
        assertEquals(0.2f, fragments[0].fontSizeFraction, 0.001f)
    }

    @Test
    fun `anchor is clamped to the source image`() {
        val lines = listOf(line("Hi", -50f, -50f, 10f, 10f)) // center negative/zero
        val fragments = OcrTextExtractor.overlayFragments(lines, IMAGE_W, IMAGE_H)
        assertEquals(1, fragments.size)
        assertEquals(0f, fragments[0].normalizedX, 0.001f)
        assertEquals(0f, fragments[0].normalizedY, 0.001f)
    }

    @Test
    fun `fragments are sorted by reading order`() {
        val lines = listOf(
            line("second", 100f, 200f, 300f, 240f),
            line("first", 100f, 40f, 300f, 80f)
        )
        val fragments = OcrTextExtractor.overlayFragments(lines, IMAGE_W, IMAGE_H)
        assertEquals(2, fragments.size)
        assertEquals("first", fragments[0].text)
        assertEquals("second", fragments[1].text)
    }

    @Test
    fun `line length is capped`() {
        val long = "x".repeat(500)
        val fragments = OcrTextExtractor.overlayFragments(
            listOf(line(long, 0f, 0f, 200f, 30f)), IMAGE_W, IMAGE_H
        )
        assertEquals(1, fragments.size)
        assertEquals(OcrTextExtractor.MAX_FRAGMENT_LENGTH, fragments[0].text.length)
    }

    @Test
    fun `zero or negative source size yields nothing`() {
        assertTrue(OcrTextExtractor.overlayFragments(listOf(line("Hi", 0f, 0f, 10f, 10f)), 0, 0).isEmpty())
    }
}