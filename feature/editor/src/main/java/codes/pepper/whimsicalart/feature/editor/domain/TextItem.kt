package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Color

/**
 * Alignment of a text item's lines within their wrap width. [JUSTIFIED] spreads
 * every wrapped line — except the last — across the full block width by widening
 * the word gaps.
 */
enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFIED
}

/** Font families offered by the Text tool, mapped to Android [Typeface]s at raster time. */
enum class TextFont(val displayName: String) {
    DEFAULT("Default"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
    SANS_SERIF("Sans Serif"),
    HANDWRITING("Handwriting")
}

/** Combinable text style flags — any subset is valid. */
enum class TextStyleFlag {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH
}

/** Background shape drawn behind a text item. */
enum class TextBackgroundShape {
    /** No background. */
    NONE,

    /** Plain rectangle (right angles). */
    RECT,

    /** Rounded rectangle. */
    ROUNDED,

    /** Ellipse. */
    OVAL,

    /** Full pill (ends fully rounded). */
    PILL,

    /** Soft-edged oval of blurred photo content behind the text (docs/effects_reference.md → *Text* → *Background blur semantics*). */
    BLUR
}

/**
 * Drop-shadow parameters: [distance] is a fraction of the canvas width, the
 * [angleDegrees] maps to a `dx`/`dy` offset exactly as the spec's
 * "angle, distance, colour" control.
 */
data class TextShadow(
    val enabled: Boolean = false,
    val angleDegrees: Float = 45f,
    val distance: Float = 0.008f,
    val color: Int = Color.BLACK
)

/**
 * Outline effect: [size] is a fraction of the canvas width, [blur] is 0..1
 * (`0` = solid stroke). Blur is an OUTWARD feather only (docs/effects_reference.md
 * → *Text* → *Outline blur semantics*): the stroke keeps its full thickness at
 * the glyph edge (opaque next to the fill) and fades to transparent outward —
 * it never shrinks inward.
 */
data class TextOutline(
    val enabled: Boolean = false,
    val size: Float = 0.003f,
    val color: Int = Color.BLACK,
    val blur: Float = 0f
) {
    /**
     * Splits this outline into alpha-scaled stroke passes (widest/faintest first,
     * narrowing to the exact solid [strokeWidthPx] band last). At [blur] = 0 a
     * single opaque pass keeps the sharp stroke; as blur → 1 the band widens to
     * `strokeWidthPx × (1 + OUTLINE_FEATHER_SCALE)` while the alpha of the outer
     * passes ramps down, so coverage fades to transparent outward from an opaque
     * glyph edge. Shared by the committed rasteriser and the overlay preview so
     * commit always matches preview.
     */
    fun featherPasses(strokeWidthPx: Float): List<OutlinePass> {
        val width = strokeWidthPx.coerceAtLeast(0.5f)
        if (blur <= 0f) return listOf(OutlinePass(width, 1f))
        val featherPx = blur.coerceIn(0f, 1f) * width * OUTLINE_FEATHER_SCALE
        return (1..OUTLINE_FEATHER_PASSES).map { i ->
            val fraction = i.toFloat() / OUTLINE_FEATHER_PASSES
            OutlinePass(
                // Widest band first (drawn earliest, behind): fraction < 1 widens.
                strokeWidthPx = width + featherPx * (1f - fraction),
                // Outermost passes are fainter; the glyph-edge pass is opaque.
                alpha = 0.25f + 0.75f * fraction
            )
        }
    }

    companion object {
        /** Feather band count — enough passes to look smooth on a wide range of sizes. */
        const val OUTLINE_FEATHER_PASSES = 6

        /** Blur distance (× stroke width) reached at blur = 1 before fading to nothing. */
        const val OUTLINE_FEATHER_SCALE = 4f
    }
}

/** One outward-feather pass: an increasing stroke width painted at a reduced alpha. */
data class OutlinePass(
    val strokeWidthPx: Float,
    val alpha: Float
)

/**
 * A single editable text overlay, parameterised in NORMALIZED [0..1] canvas
 * space the same way sticker placements are:
 * - [x]/[y] anchor the CENTER of the text block;
 * - [fontSize] is a fraction of the canvas width (so text stays proportionate
 *   when the canvas dimension changes);
 * - shadow [distance] and outline [size] are likewise fractions of the width;
 * - [color] and [backgroundColor] are ARGB ints whose alpha channel carries the
 *   control's opacity (0–100%);
 * - [scale] is a uniform multiplier and [rotationDegrees] turns the whole block.
 *
 * The `TextItem` list is the **source of truth on the `TextEffect` stack
 * entry** (docs/effects_reference.md → *Text*): the render rasterises exactly
 * this list, and returning to the tool restores every item for re-editing.
 */
data class TextItem(
    val id: String,
    val text: String,
    val color: Int,
    val fontFamily: TextFont = TextFont.DEFAULT,
    val fontSize: Float,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val style: Set<TextStyleFlag> = emptySet(),
    val alignment: TextAlignment = TextAlignment.CENTER,
    val shadow: TextShadow = TextShadow(),
    val outline: TextOutline = TextOutline(),
    val background: TextBackgroundShape = TextBackgroundShape.NONE,
    val backgroundColor: Int = Color.BLACK,
    /** Blur-background intensity 0..1 (`0` = no blur, `1` = maximum); other shapes ignore it. */
    val backgroundBlur: Float = 0f,
    /**
     * The anchored reference base of this text item (docs/effect_stack.md →
     * *Positional layers*): the full-size pixel dims of the image its
     * [x]/[y]/[fontSize] (and shadow/outline width fractions) were captured
     * against. The renderer lays the block out through the uniform fit of these
     * dims into the running bitmap, so the text keeps its on-image position and
     * size when an earlier effect changes the base. `0×0` (default) keeps the
     * un-anchored identity mapping.
     */
    val referenceWidth: Int = 0,
    val referenceHeight: Int = 0
)