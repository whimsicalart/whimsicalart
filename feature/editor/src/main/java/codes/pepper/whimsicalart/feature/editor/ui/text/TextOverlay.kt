package codes.pepper.whimsicalart.feature.editor.ui.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.domain.UniformFit
import codes.pepper.whimsicalart.feature.editor.domain.TextAlignment
import codes.pepper.whimsicalart.feature.editor.domain.TextBackgroundShape
import codes.pepper.whimsicalart.feature.editor.domain.TextFont
import codes.pepper.whimsicalart.feature.editor.domain.TextItem
import codes.pepper.whimsicalart.feature.editor.domain.TextOutline
import codes.pepper.whimsicalart.feature.editor.domain.TextShadow
import codes.pepper.whimsicalart.feature.editor.domain.TextStyleFlag

/**
 * The Text tool's LIVE, dp-space editing model: [position] is the CENTER of the
 * text (in dp), [fontSize], shadow [distance] and outline [size] are dp widths,
 * and the colour fields carry their opacity in the alpha channel. Converted to a
 * normalized [TextItem] when it is committed to the stack (and back again when
 * restoring an item on re-entry).
 */
data class TextOverlay(
    val id: String,
    val text: String,
    val position: Offset,
    val fontSize: Float = 24f,
    val color: Color = Color.White,
    val fontFamily: TextFont = TextFont.DEFAULT,
    val style: Set<TextStyleFlag> = emptySet(),
    val alignment: TextAlignment = TextAlignment.CENTER,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val hasShadow: Boolean = false,
    val shadowAngle: Float = 45f,
    val shadowDistance: Float = 6f,
    val shadowColor: Color = Color.Black,
    val hasOutline: Boolean = false,
    val outlineSize: Float = 2f,
    val outlineColor: Color = Color.Black,
    val outlineBlur: Float = 0f,
    val backgroundShape: TextBackgroundShape = TextBackgroundShape.NONE,
    val backgroundColor: Color = Color.Black,
    val backgroundBlur: Float = 0f
) {
    /**
     * True when this item's shadow must be HOLLOWED out of the glyph rather than
     * drawn through it — i.e. the font colour's opacity is below 100%
     * (spec: effects_reference.md → *Text* → "The shadow is hollowed out of the
     * glyph like the outline": a translucent fill reveals the image through the
     * glyph, never the shadow; an opaque fill is unchanged).
     */
    val usesHollowShadow: Boolean
        get() = hasShadow && color.alpha < 1f
}

/**
 * Normalizes this dp-space editing item against the FITTED image rect within the
 * box ([frameWidthPx]×[frameHeightPx], letterbox top-left at
 * [frameOffsetX]/[frameOffsetY] in box px), so the baked normalized position and
 * sizes match the fraction of the image the user sees — with an unletterboxed
 * box (offset 0, box == image) this is identical to normalizing by the box.
 */
fun TextOverlay.toTextItem(
    density: Density,
    frameWidthPx: Float,
    frameHeightPx: Float,
    frameOffsetX: Float = 0f,
    frameOffsetY: Float = 0f,
    referenceWidth: Int = 0,
    referenceHeight: Int = 0
): TextItem {
    val px = { dp: Float -> with(density) { dp.dp.toPx() } }
    val w = if (frameWidthPx > 0f) frameWidthPx else 1f
    val h = if (frameHeightPx > 0f) frameHeightPx else 1f
    return TextItem(
        id = id,
        text = text,
        color = color.toArgb(),
        fontFamily = fontFamily,
        fontSize = px(fontSize) / w,
        x = (px(position.x) - frameOffsetX) / w,
        y = (px(position.y) - frameOffsetY) / h,
        scale = scale,
        rotationDegrees = rotation,
        style = style,
        alignment = alignment,
        shadow = TextShadow(
            enabled = hasShadow,
            angleDegrees = shadowAngle,
            distance = px(shadowDistance) / w,
            color = shadowColor.toArgb()
        ),
        outline = TextOutline(
            enabled = hasOutline,
            size = px(outlineSize) / w,
            color = outlineColor.toArgb(),
            blur = outlineBlur
        ),
        background = backgroundShape,
        backgroundColor = backgroundColor.toArgb(),
        backgroundBlur = backgroundBlur,
        referenceWidth = referenceWidth,
        referenceHeight = referenceHeight
    )
}

/** De-normalizes a committed [TextItem] back into dp-space for re-editing. */
fun TextItem.toTextOverlay(
    density: Density,
    frameWidthPx: Float,
    frameHeightPx: Float,
    frameOffsetX: Float = 0f,
    frameOffsetY: Float = 0f
): TextOverlay {
    val w = if (frameWidthPx > 0f) frameWidthPx else 1f
    val h = if (frameHeightPx > 0f) frameHeightPx else 1f
    // With an anchored reference the stored fractions belong to that reference,
    // not the current frame: re-express them through the uniform fit of the
    // reference into the CURRENT frame (spec rule 5), so the restored overlay
    // lands where the committed render shows it even when the frame drifts.
    val xPx: Float
    val yPx: Float
    val fontPx: Float
    val shadowPx: Float
    val outlinePx: Float
    if (referenceWidth > 0 && referenceHeight > 0) {
        val fit = UniformFit.of(w.toInt(), h.toInt(), referenceWidth, referenceHeight)
        xPx = fit.frameX(x, referenceWidth)
        yPx = fit.frameY(y, referenceHeight)
        fontPx = fit.measureX(fontSize, referenceWidth)
        shadowPx = fit.measureX(shadow.distance, referenceWidth)
        outlinePx = fit.measureX(outline.size, referenceWidth)
    } else {
        xPx = x * w
        yPx = y * h
        fontPx = fontSize * w
        shadowPx = shadow.distance * w
        outlinePx = outline.size * w
    }
    return TextOverlay(
        id = id,
        text = text,
        position = Offset(
            with(density) { (xPx + frameOffsetX).toDp().value },
            with(density) { (yPx + frameOffsetY).toDp().value }
        ),
        fontSize = with(density) { fontPx.toDp().value },
        color = Color(color),
        fontFamily = fontFamily,
        style = style,
        alignment = alignment,
        scale = scale,
        rotation = rotationDegrees,
        hasShadow = shadow.enabled,
        shadowAngle = shadow.angleDegrees,
        shadowDistance = with(density) { shadowPx.toDp().value },
        shadowColor = Color(shadow.color),
        hasOutline = outline.enabled,
        outlineSize = with(density) { outlinePx.toDp().value },
        outlineColor = Color(outline.color),
        outlineBlur = outline.blur,
        backgroundShape = background,
        backgroundColor = Color(backgroundColor),
        backgroundBlur = backgroundBlur
    )
}