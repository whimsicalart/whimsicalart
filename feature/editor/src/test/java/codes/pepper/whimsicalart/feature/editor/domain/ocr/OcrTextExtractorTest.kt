package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.atan2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(100f / 800f, fragments[0].fontSizeFraction, 0.001f)
    }

    @Test
    fun `font size is proportional to detected line height, not width`() {
        // Wide-but-short line: width fraction would clamp at 0.2, but the spec
        // sizes glyphs from the line's HEIGHT so the text is 40px tall on an
        // 800px image → 0.05.
        val lines = listOf(line("wide", 100f, 100f, 900f, 140f))
        val fragments = OcrTextExtractor.overlayFragments(lines, IMAGE_W, IMAGE_H)
        assertEquals(40f / 800f, fragments[0].fontSizeFraction, 0.001f)
    }

    @Test
    fun `rotated quad yields baseline angle, height-based size and centroid anchor`() {
        val cx = 200f
        val cy = 136.5f
        val quad = listOf(
            PointF(100f, 100f), PointF(300f, 100f), PointF(300f, 173f), PointF(100f, 173f)
        ).map { rotate(it, cx, cy, 20f) }
        val detected = OcrLine("Hello", RectF(100f, 100f, 300f, 173f), rotationDegrees = 20f, quad = quad)
        val fragments = OcrTextExtractor.overlayFragments(listOf(detected), IMAGE_W, IMAGE_H)
        assertEquals(1, fragments.size)

        val expectedAngle = Math.toDegrees(
            atan2((quad[1].y - quad[0].y).toDouble(), (quad[1].x - quad[0].x).toDouble())
        ).toFloat()
        assertEquals(expectedAngle, fragments[0].rotationDegrees, 0.5f)
        assertEquals(73f / 800f, fragments[0].fontSizeFraction, 0.002f)
        assertEquals(0.2f, fragments[0].normalizedX, 0.001f)
        assertEquals(136.5f / 800f, fragments[0].normalizedY, 0.001f)
    }

    @Test
    fun `axis-aligned line carries zero rotation`() {
        val fragments = OcrTextExtractor.overlayFragments(listOf(line("plain", 100f, 100f, 300f, 140f)), IMAGE_W, IMAGE_H)
        assertEquals(0f, fragments[0].rotationDegrees, 0.001f)
        assertNull(fragments[0].colorArgb)
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

    // -----------------------------------------------------------------
    // Ink-colour sampling (spec: colour = detected ink, fallback to default).
    // -----------------------------------------------------------------

    @Test
    fun `ink colour is sampled from the provided source bitmap`() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        Canvas(bitmap).drawRect(50f, 20f, 150f, 80f, Paint().apply { color = Color.RED })
        val fragments = OcrTextExtractor.overlayFragments(
            listOf(line("Hello", 0f, 0f, 200f, 100f)), 200, 100, bitmap
        )
        assertEquals(Color.rgb(255, 0, 0), fragments.single().colorArgb)
    }

    @Test
    fun `colour sampling is skipped without a source bitmap`() {
        val fragments = OcrTextExtractor.overlayFragments(
            listOf(line("Hello", 0f, 0f, 200f, 100f)), 200, 100
        )
        assertNull(fragments.single().colorArgb)
    }

    @Test
    fun `ambiguous region falls back to a null colour`() {
        // Uniform mid-grey: no bimodal ink/background distribution → null.
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        val fragments = OcrTextExtractor.overlayFragments(
            listOf(line("Hi", 0f, 0f, 200f, 100f)), 200, 100, bitmap
        )
        assertNull(fragments.single().colorArgb)
    }

    @Test
    fun `too few ink pixels is ambiguous`() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(100, 50, Color.BLACK) // a single speck, not text
        val fragments = OcrTextExtractor.overlayFragments(
            listOf(line("Hi", 0f, 0f, 200f, 100f)), 200, 100, bitmap
        )
        assertNull(fragments.single().colorArgb)
    }

    private fun rotate(p: PointF, cx: Float, cy: Float, deg: Float): PointF {
        val t = Math.toRadians(deg.toDouble())
        val cos = Math.cos(t)
        val sin = Math.sin(t)
        val dx = p.x - cx
        val dy = p.y - cy
        return PointF(
            cx + (dx * cos - dy * sin).toFloat(),
            cy + (dx * sin + dy * cos).toFloat()
        )
    }
}