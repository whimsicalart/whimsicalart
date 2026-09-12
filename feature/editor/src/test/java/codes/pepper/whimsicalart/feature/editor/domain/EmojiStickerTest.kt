package codes.pepper.whimsicalart.feature.editor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Spec: effects_reference.md → *Stickers* → *Emoji rendering* — an emoji sticker
 * renders at its full sticker size, never cropped on any side, identically in
 * the live preview and the committed image. The fit rule the tracker asks for:
 * measure the glyph's real bounds and scale the text size so ascent–descent
 * (and width) fit the sticker square with a small margin.
 */
@RunWith(RobolectricTestRunner::class)
class EmojiStickerTest {

    private val margin = 0.04f

    @Test
    fun `ratio fills exactly when block and width equal the square`() {
        val scale = emojiFitScale(blockHeightPx = 100f, glyphWidthPx = 100f, sidePx = 100f, margin = margin)
        assertEquals((1f - margin), scale, 1e-4f)
    }

    @Test
    fun `wide glyph is constrained by width`() {
        val scale = emojiFitScale(blockHeightPx = 100f, glyphWidthPx = 120f, sidePx = 100f, margin = margin)
        val expected = (100f * (1f - margin)) / 120f
        assertEquals(expected, scale, 1e-4f)
    }

    @Test
    fun `tall block is constrained by its height`() {
        val scale = emojiFitScale(blockHeightPx = 140f, glyphWidthPx = 100f, sidePx = 100f, margin = margin)
        val expected = (100f * (1f - margin)) / 140f
        assertEquals(expected, scale, 1e-4f)
    }

    @Test
    fun `never returns a scale above one (glyph never overflows)`() {
        for (block in listOf(60f, 100f, 200f)) {
            for (width in listOf(60f, 100f, 200f)) {
                val scale = emojiFitScale(block, width, sidePx = 100f, margin = margin)
                assertTrue("scale=$scale for block=$block width=$width", scale <= 1f)
                assertTrue("scale=$scale for block=$block width=$width", scale > 0f)
            }
        }
    }

    @Test
    fun `degenerate inputs fall back to full size`() {
        assertEquals(1f, emojiFitScale(0f, 0f, sidePx = 100f, margin = margin), 1e-6f)
        assertEquals(1f, emojiFitScale(100f, 100f, sidePx = 0f, margin = margin), 1e-6f)
    }

    @Test
    fun `margin is clamped to a sane range`() {
        // 90% clamps to 50%: usable = side * 0.5, block == width == side.
        val clamped = emojiFitScale(blockHeightPx = 100f, glyphWidthPx = 100f, sidePx = 100f, margin = 0.9f)
        assertEquals(0.5f, clamped, 1e-4f)
        // Negative margin clamps to 0: full-size usable area.
        val noMargin = emojiFitScale(blockHeightPx = 100f, glyphWidthPx = 100f, sidePx = 100f, margin = -1f)
        assertEquals(1f, noMargin, 1e-4f)
    }

    @Test
    fun `real fonts fit both a default and a tall emoji into the square with the margin`() {
        val side = 300f
        for (emoji in listOf("😀", "🦙")) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = side
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val fm = paint.fontMetrics
            val blockHeight = fm.descent - fm.ascent
            val glyphWidth = paint.measureText(emoji)
            val scale = emojiFitScale(blockHeight, glyphWidth, side, margin = margin)
            val fittedBlock = blockHeight * scale
            val fittedWidth = glyphWidth * scale
            val usable = side * (1f - margin)
            assertTrue("😀 block overflows: $fittedBlock > $usable", fittedBlock <= usable + 0.5f)
            assertTrue("😀 width overflows: $fittedWidth > $usable", fittedWidth <= usable + 0.5f)
        }
    }

    @Test
    fun `glyph render produces a correctly sized bitmap without crashing`() {
        // Robolectric's bundled fonts do not rasterize color-emoji (draws a
        // blank run), so only the dimensions / pipeline are asserted here; the
        // visual fit guarantee is enforced by the fontMetrics-based tests above.
        val bitmap = renderEmojiGlyph("😀", 160)
        assertEquals(160, bitmap.width)
        assertEquals(160, bitmap.height)
    }
}