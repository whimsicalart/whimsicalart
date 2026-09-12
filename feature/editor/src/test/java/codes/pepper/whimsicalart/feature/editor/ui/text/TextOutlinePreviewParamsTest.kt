package codes.pepper.whimsicalart.feature.editor.ui.text

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.feature.editor.domain.OutlinePass
import codes.pepper.whimsicalart.feature.editor.domain.TextOutline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec: the outline is feathered OUTWARD — `blur=0` is a hard stroke of the
 * chosen size, `blur→100%` keeps the full thickness at the glyph edge (opaque
 * next to the fill) but fades to transparent outward, never a shrunken band
 * (docs/effects_reference.md → *Text* → *Outline blur semantics*). The preview
 * mirror (`textOutlinePreviewParams`) must produce the SAME pass stack the
 * committed rasteriser uses (`TextOutline.featherPasses`), only with the stroke
 * width converted from the panel's dp value.
 */
class TextOutlinePreviewParamsTest {

    // Arbitrary 3× density (phone-class) to make the math non-trivial.
    private val density = Density(density = 3f, fontScale = 1f)
    private val boxWidthPx = 1000f  // the "w" the committed path normalises against

    private fun preview(outlineSizeDp: Float, blur: Float) =
        textOutlinePreviewParams(
            outlineSizeDp, blur, boxWidthPx, density
        )

    private fun committedPasses(outlineSizeDp: Float, blur: Float): List<OutlinePass> {
        // Normalised: size = px(density)/boxWidth. Committed stroke = size × boxWidth = px(density).
        val strokePx = with(density) { outlineSizeDp.dp.toPx() }
        return TextOutline(blur = blur).featherPasses(strokePx)
    }

    // ------------------------------------------------------------------
    // Sharp (blur = 0): a single opaque band at the chosen size
    // ------------------------------------------------------------------

    @Test
    fun `blur zero produces a single opaque pass at the chosen size`() {
        val p = preview(outlineSizeDp = 3f, blur = 0f)
        assertEquals(1, p.size)
        assertEquals(with(density) { 3.dp.toPx() }, p[0].strokeWidthPx, 0f)
        assertEquals(1f, p[0].alpha, 0f)
    }

    @Test
    fun `blur zero preview matches committed pass stack`() {
        val p = preview(outlineSizeDp = 3f, blur = 0f)
        val committed = committedPasses(3f, 0f)
        assertEquals(committed.size, p.size)
        assertEquals(committed[0].strokeWidthPx, p[0].strokeWidthPx, 0f)
        assertEquals(committed[0].alpha, p[0].alpha, 0f)
    }

    @Test
    fun `stroke coerces to minimum half pixel`() {
        val p = preview(outlineSizeDp = 0f, blur = 0f)
        assertEquals(0.5f, p[0].strokeWidthPx, 0f)
    }

    // ------------------------------------------------------------------
    // Feathered (blur > 0): outward ramp, never inward
    // ------------------------------------------------------------------

    @Test
    fun `blur zero when disabled - single pass only`() {
        val p = preview(outlineSizeDp = 4f, blur = 0f)
        assertEquals(1, p.size)
        assertFalse(p[0].alpha < 1f)
    }

    @Test
    fun `feather passes widen outward with a decreasing alpha`() {
        val p = preview(outlineSizeDp = 4f, blur = 1f)
        assertEquals(TextOutline.OUTLINE_FEATHER_PASSES, p.size)
        // Widest first, narrowing to exactly the chosen size (never shrinks inward).
        val widths = p.map { it.strokeWidthPx }
        assertEquals(widths.sortedDescending(), widths)
        assertEquals(with(density) { 4.dp.toPx() }, p.last().strokeWidthPx, 0f)
        // Outermost is faintest; the glyph-edge pass is opaque.
        val alphas = p.map { it.alpha }
        assertEquals(alphas.sorted(), alphas)
        assertTrue("outermost must be fainter than 1", p.first().alpha < 1f)
        assertEquals("innermost must be fully opaque", 1f, p.last().alpha, 0f)
    }

    @Test
    fun `feather distance scales linearly with the blur fraction`() {
        val half = preview(outlineSizeDp = 4f, blur = 0.5f)
        val full = preview(outlineSizeDp = 4f, blur = 1f)
        val halfFeather = half.first().strokeWidthPx - half.last().strokeWidthPx
        val fullFeather = full.first().strokeWidthPx - full.last().strokeWidthPx
        assertEquals(2f, fullFeather / halfFeather, 0.001f)
    }

    @Test
    fun `feather keeps the glyph edge at the chosen size for every blur`() {
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { blur ->
            val p = preview(outlineSizeDp = 5f, blur = blur)
            assertEquals(
                "innermost band must stay at the sharp size for blur=$blur",
                with(density) { 5.dp.toPx() },
                p.last().strokeWidthPx,
                0f
            )
        }
    }

    @Test
    fun `blur never produces a band narrower than the sharp size`() {
        val p = preview(outlineSizeDp = 2f, blur = 1f)
        p.forEach { pass ->
            assertTrue(
                "no pass may be narrower than the sharp size (got ${pass.strokeWidthPx})",
                pass.strokeWidthPx >= with(density) { 2.dp.toPx() }
            )
        }
    }

    // ------------------------------------------------------------------
    // Preview↔commit consistency for a feathered outline
    // ------------------------------------------------------------------

    @Test
    fun `feathered preview matches the committed pass stack`() {
        val p = preview(outlineSizeDp = 4f, blur = 0.6f)
        val committed = committedPasses(4f, 0.6f)
        assertEquals(committed.size, p.size)
        committed.zip(p).forEach { (committedPass, previewPass) ->
            assertEquals(committedPass.strokeWidthPx, previewPass.strokeWidthPx, 0f)
            assertEquals(committedPass.alpha, previewPass.alpha, 0f)
        }
    }

    @Test
    fun `one-off mapping committed normalised size roundtrips at zero blur`() {
        val outlineSizeDp = 5f
        val normalisedSize = with(density) { outlineSizeDp.dp.toPx() } / boxWidthPx
        val committedStroke = normalisedSize * boxWidthPx
        val previewStroke = preview(outlineSizeDp, 0f)[0].strokeWidthPx
        assertEquals(committedStroke, previewStroke, 0f)
    }
}