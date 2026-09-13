package codes.pepper.whimsicalart.feature.editor.domain

import codes.pepper.whimsicalart.core.domain.GeometryAnchor
import codes.pepper.whimsicalart.core.domain.UniformFit
import codes.pepper.whimsicalart.core.domain.reAnchor
import codes.pepper.whimsicalart.feature.stickers.domain.StickerItem
import kotlin.math.abs

/**
 * The anchored positional-geometry fix (docs/effect_stack.md → *Positional
 * layers — anchored reference base, centre-origin, uniform fit*, rules 1+4):
 * the capture/commit side of a positional layer must FROZEN the geometry and
 * reference it was committed with, and only RE-ANCHOR them — never silently
 * re-normalise against whatever dimensions happen to be current — when the
 * layer's own base actually changes.
 *
 * The reconcillation rule applied to every positional family (stickers / text /
 * brush strokes) at bundle-build time:
 *
 * - **First commit** (no stored layer yet): adopt the live capture as-is. Its
 *   reference is already the layer's own-position base (the family frame it is
 *   captured against, fitted to the layer's own prefix footprint).
 * - **Unchanged re-commit**: when the live round-trip reproduces the stored
 *   geometry, the live state is a restore of the committed layer (or a no-op
 *   re-entry) — so the stored geometry AND reference are carried verbatim. An
 *   unrelated commit, a re-entry or a "no-move re-commit" therefore NEVER
 *   refreshes the reference, and never re-bakes stale screen positions.
 * - **Own-base change** (an effect before the layer was re-edited / the layer
 *   reordered): re-express the STORED geometry through the centre-origin
 *   uniform fit onto the new base ([reAnchor]) and update the reference to the
 *   new base — the object keeps its exact on-image position/size/aspect.
 * - **Actively edited**: the live capture wins (its geometry differs from the
 *   stored round-trip); its reference already equals the layer's own base.
 *
 * The final committed list always carries a reference identical to the layer's
 * actual own-position base — the invariant the renderers rely on.
 */
internal fun <T> reconcilePositionalLayer(
    stored: List<T>?,
    storedRef: GeometryAnchor,
    derived: List<T>,
    newBase: GeometryAnchor,
    sameGeometry: (T, T) -> Boolean,
    reanchorTo: (List<T>, GeometryAnchor) -> List<T>
): List<T> {
    if (stored == null || stored.isEmpty()) return derived
    if (!storedRef.isSet) {
        // Legacy never-anchored layer: the fresh capture adopts the current
        // base as its reference (identity fractions preserved by the capture).
        return derived
    }
    if (stored.size != derived.size) return derived
    val unchanged = stored.indices.all { i ->
        sameGeometry(stored[i], derived[i])
    }
    if (!unchanged) return derived
    return if (storedRef == newBase) stored else reanchorTo(stored, newBase)
}

/**
 * Whether two committed items carry the same geometry (and content), ignoring
 * their reference fields. Fractions round-trip through dp↔px conversions, so
 * positional/size measures compare within a small absolute epsilon; every other
 * field is exact.
 */
private const val FRACTION_EPSILON = 1e-3f

private fun close(a: Float, b: Float): Boolean = abs(a - b) <= FRACTION_EPSILON

private fun sameStickerGeometry(a: StickerItem, b: StickerItem): Boolean =
    a.stickerId == b.stickerId &&
        a.drawableRes == b.drawableRes &&
        a.emojiText == b.emojiText &&
        a.imageUri == b.imageUri &&
        close(a.x, b.x) &&
        close(a.y, b.y) &&
        close(a.widthFrac, b.widthFrac) &&
        close(a.heightFrac, b.heightFrac) &&
        close(a.rotationDegrees, b.rotationDegrees) &&
        close(a.opacity, b.opacity) &&
        close(a.lineThickness, b.lineThickness) &&
        a.tintColor == b.tintColor &&
        a.isFlipped == b.isFlipped &&
        a.aspectRatio == b.aspectRatio &&
        a.lineStyle == b.lineStyle &&
        a.lineColor == b.lineColor

private fun reanchorStickers(layers: List<StickerItem>, anchor: GeometryAnchor): List<StickerItem> {
    if (!anchor.isSet) return layers
    val stored = layers.firstOrNull()?.let { GeometryAnchor(it.referenceWidth, it.referenceHeight) }
        ?: return layers
    if (stored == anchor) return layers
    return layers.map { item ->
        val re = reAnchor(
            item.x, item.y, item.widthFrac, item.heightFrac,
            stored, anchor.width, anchor.height
        )
        item.copy(
            x = re.x,
            y = re.y,
            widthFrac = re.widthFraction,
            heightFrac = re.heightFraction,
            referenceWidth = anchor.width,
            referenceHeight = anchor.height
        )
    }
}

private fun sameTextGeometry(a: TextItem, b: TextItem): Boolean =
    a.id == b.id &&
        a.text == b.text &&
        a.color == b.color &&
        a.fontFamily == b.fontFamily &&
        a.style == b.style &&
        a.alignment == b.alignment &&
        a.background == b.background &&
        a.backgroundColor == b.backgroundColor &&
        close(a.backgroundBlur, b.backgroundBlur) &&
        close(a.fontSize, b.fontSize) &&
        close(a.x, b.x) &&
        close(a.y, b.y) &&
        close(a.scale, b.scale) &&
        close(a.rotationDegrees, b.rotationDegrees) &&
        close(a.shadow.distance, b.shadow.distance) &&
        a.shadow.enabled == b.shadow.enabled &&
        close(a.shadow.angleDegrees, b.shadow.angleDegrees) &&
        a.shadow.color == b.shadow.color &&
        a.outline.enabled == b.outline.enabled &&
        close(a.outline.size, b.outline.size) &&
        a.outline.color == b.outline.color &&
        close(a.outline.blur, b.outline.blur)

private fun reanchorTexts(layers: List<TextItem>, anchor: GeometryAnchor): List<TextItem> {
    if (!anchor.isSet) return layers
    val stored = layers.firstOrNull()?.let { GeometryAnchor(it.referenceWidth, it.referenceHeight) }
        ?: return layers
    if (stored == anchor) return layers
    val fit = UniformFit.of(anchor.width, anchor.height, stored.width, stored.height)
    fun remeasure(v: Float): Float = fit.measureX(v, stored.width) / anchor.width
    return layers.map { item ->
        val re = reAnchor(
            item.x, item.y, item.scale, 1f,
            stored, anchor.width, anchor.height
        )
        item.copy(
            x = re.x,
            y = re.y,
            // scale is a unitless multiplier (position and fontSize carry the
            // dimensionful fractions); re-measuring fontSize re-anchors the
            // object's on-image size, so scale stays verbatim.
            scale = item.scale,
            fontSize = remeasure(item.fontSize),
            shadow = item.shadow.copy(distance = remeasure(item.shadow.distance)),
            outline = item.outline.copy(size = remeasure(item.outline.size)),
            referenceWidth = anchor.width,
            referenceHeight = anchor.height
        )
    }
}

private fun sameStrokeGeometry(a: StrokeLayer, b: StrokeLayer): Boolean =
    a.type == b.type &&
        a.color == b.color &&
        a.brush == b.brush &&
        a.points.size == b.points.size &&
        a.points.indices.all { i ->
            close(a.points[i].first, b.points[i].first) &&
                close(a.points[i].second, b.points[i].second)
        } &&
        close(a.brushSize, b.brushSize) &&
        close(a.opacity, b.opacity) &&
        close(a.hardness, b.hardness)

private fun reanchorStrokes(layers: List<StrokeLayer>, anchor: GeometryAnchor): List<StrokeLayer> {
    if (!anchor.isSet) return layers
    val stored = layers.firstOrNull()?.let { GeometryAnchor(it.referenceWidth, it.referenceHeight) }
        ?: return layers
    if (stored == anchor) return layers
    val fit = UniformFit.of(anchor.width, anchor.height, stored.width, stored.height)
    return layers.map { stroke ->
        stroke.copy(
            points = stroke.points.map { p ->
                fit.frameX(p.first, stored.width) / anchor.width to
                    fit.frameY(p.second, stored.height) / anchor.height
            },
            brushSize = fit.measureX(stroke.brushSize, stored.width) / anchor.width,
            referenceWidth = anchor.width,
            referenceHeight = anchor.height
        )
    }
}

/** Internal reconcilers for the editor's three positional families. */
internal object PositionalReconcilers {

    fun stickers(
        stored: List<StickerItem>?,
        storedRef: GeometryAnchor,
        derived: List<StickerItem>,
        newBase: GeometryAnchor
    ): List<StickerItem> = reconcilePositionalLayer(
        stored, storedRef, derived, newBase,
        ::sameStickerGeometry, ::reanchorStickers
    )

    fun texts(
        stored: List<TextItem>?,
        storedRef: GeometryAnchor,
        derived: List<TextItem>,
        newBase: GeometryAnchor
    ): List<TextItem> = reconcilePositionalLayer(
        stored, storedRef, derived, newBase,
        ::sameTextGeometry, ::reanchorTexts
    )

    fun strokes(
        stored: List<StrokeLayer>?,
        storedRef: GeometryAnchor,
        derived: List<StrokeLayer>,
        newBase: GeometryAnchor
    ): List<StrokeLayer> = reconcilePositionalLayer(
        stored, storedRef, derived, newBase,
        ::sameStrokeGeometry, ::reanchorStrokes
    )
}

/**
 * Re-expresses a committed [StrokeLayer]'s anchored fractions into the
 * box-pixel coordinate system of the CURRENT fitted family frame — the inverse
 * of the capture-side frame normalization (docs/effect_stack.md → *Positional
 * layers*): reference-frame point fractions map to frame pixels through the
 * uniform fit, exactly as the renderers map them into any destination. Used to
 * re-present committed strokes to the live sub-state on tool re-entry, so a
 * brush never rides stale screen positions after the layer's base changed.
 * An unset reference keeps the legacy identity mapping (fractions of the frame).
 * Returns the box-px points and the box-px brush width.
 */
internal fun StrokeLayer.toFramePixels(
    frameOffsetX: Float,
    frameOffsetY: Float,
    frameWidthPx: Float,
    frameHeightPx: Float
): Pair<List<Pair<Float, Float>>, Float> {
    val w = if (frameWidthPx > 0f) frameWidthPx else 1f
    val h = if (frameHeightPx > 0f) frameHeightPx else 1f
    return if (referenceWidth > 0 && referenceHeight > 0) {
        val fit = UniformFit.of(w.toInt(), h.toInt(), referenceWidth, referenceHeight)
        points.map { p ->
            (frameOffsetX + fit.frameX(p.first, referenceWidth)) to
                (frameOffsetY + fit.frameY(p.second, referenceHeight))
        } to fit.measureX(brushSize, referenceWidth)
    } else {
        points.map { p -> (frameOffsetX + p.first * w) to (frameOffsetY + p.second * h) } to
            (brushSize * w)
    }
}