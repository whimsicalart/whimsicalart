package codes.pepper.whimsicalart.feature.stickers.domain

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.domain.UniformFit

/** Default on-canvas size (dp) of a freshly picked emoji sticker. */
const val EMOJI_STICKER_BASE_SIZE_DP = 150f

/**
 * Normalized sticker parameters used as the STACK source of truth and by the
 * renderer. Coordinates are fractions of the canvas [0..1] and sizes are
 * fractions of the canvas dimensions, so a stack entry maps deterministically
 * to any output size without needing a Density (mirrors [TextItem]). The
 * [drawableRes] is resolved at commit time; emoji stickers carry [emojiText]
 * instead of a drawable and are drawn with the emoji font; Gallery stickers
 * carry the picked image's [imageUri] (content URI), decoded at render time.
 */
data class StickerItem(
    val stickerId: String,
    @DrawableRes val drawableRes: Int? = null,
    val emojiText: String? = null,
    val x: Float,
    val y: Float,
    val widthFrac: Float,
    val heightFrac: Float,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
    val tintColor: Int? = null,
    val isFlipped: Boolean = false,
    /** The picked image's content URI (Gallery sticker); null for preset/emoji. */
    val imageUri: String? = null,
    /**
     * Intrinsic width:height ratio of the picked image (EXIF-oriented), carried
     * from the placement so the box (and the fold) keep the photo's proportion;
     * 1 for presets/emoji whose base is already their own geometry.
     */
    val aspectRatio: Float = 1f,
    /** The sticker's outline style (colour-enabled stickers), default none. */
    val lineStyle: StickerLineStyle = StickerLineStyle.NONE,
    /** Outline thickness as a fraction of the sticker's shorter side. */
    val lineThickness: Float = 0.05f,
    /**
     * Outline colour; null resolves to black at draw time
     * (the default line colour).
     */
    val lineColor: Int? = null,
    /**
     * The anchored reference base of this sticker (docs/effect_stack.md →
     * *Positional layers*): the full-size pixel dims of the image its
     * [x]/[y]/[widthFrac]/[heightFrac] were captured against. The renderer maps
     * the placement through the uniform fit of these dims into the running
     * bitmap, so the sticker keeps its on-image spot and size when an earlier
     * effect changes the base. `0×0` (default) keeps the un-anchored identity
     * mapping.
     */
    val referenceWidth: Int = 0,
    val referenceHeight: Int = 0
)

/**
 * Converts a dp-space placement into a normalized [StickerItem] stack entry,
 * against the FITTED image rect within the box ([frameWidthPx]×[frameHeightPx],
 * letterbox top-left at [frameOffsetX]/[frameOffsetY] in box px) so the baked
 * normalized position/size match the fraction of the image the user sees — with
 * an unletterboxed box (offset 0, box == image) this equals normalizing by the
 * box.
 */
fun StickerPlacement.toStickerItem(
    sticker: Sticker?,
    density: Density,
    frameWidthPx: Float,
    frameHeightPx: Float,
    frameOffsetX: Float = 0f,
    frameOffsetY: Float = 0f,
    referenceWidth: Int = 0,
    referenceHeight: Int = 0
): StickerItem? {
    if (frameWidthPx <= 0f || frameHeightPx <= 0f || density.density <= 0f) return null
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    // A Gallery photo keeps its own aspect ratio (spec "Gallery stickers keep the
    // original image's aspect ratio"): the base anchors the long side at the same
    // default on-canvas size as an emoji, with the other side derived from the
    // image's intrinsic ratio — landscape is wide and short, portrait is narrow
    // and tall, square stays square. Emoji/presets use their own square base.
    val base = sticker
        ?.let { Size(it.width.toFloat(), it.height.toFloat()) }
        ?: emojiText?.let { Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP) }
        ?: if (imageUri != null) {
            Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP / ratio)
        } else null
        ?: return null
    val w = frameWidthPx
    val h = frameHeightPx
    return StickerItem(
        stickerId = stickerId,
        drawableRes = sticker?.drawableRes,
        emojiText = emojiText,
        imageUri = imageUri,
        x = (with(density) { x.dp.toPx() } - frameOffsetX) / w,
        y = (with(density) { y.dp.toPx() } - frameOffsetY) / h,
        widthFrac = with(density) { base.width.dp.toPx() * scaleX } / w,
        heightFrac = with(density) { base.height.dp.toPx() * scaleY } / h,
        rotationDegrees = rotation,
        opacity = opacity,
        tintColor = tintColor,
        isFlipped = isFlipped,
        aspectRatio = ratio,
        lineStyle = lineStyle,
        lineThickness = lineThickness,
        lineColor = lineColor,
        referenceWidth = referenceWidth,
        referenceHeight = referenceHeight
    )
}

/**
 * The sticker's intrinsic dp size for SIZING when reading a stack entry back
 * into dp space (restore, hit targets, preview sprite): the preset's width/height
 * when the id resolves, else the emoji base size. A Gallery photo instead
 * returns its RATIO-CORRECTED base (anchored on the emoji default width, other
 * side derived from the carried aspect ratio) so restore / re-open keeps the
 * same proportional box the user placed. Null only for an unknown id that
 * carries neither a preset nor emoji/gallery content.
 */
fun StickerItem.baseSizeDp(): Size? {
    StickerPresets.getStickerById(stickerId)?.let {
        return Size(it.width.toFloat(), it.height.toFloat())
    }
    if (emojiText != null) {
        return Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP)
    }
    if (imageUri != null) {
        val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        return Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP / ratio)
    }
    return null
}

/** Converts a normalized [StickerItem] back to a dp-space placement (restore). */
fun StickerItem.toStickerPlacement(
    density: Density,
    frameWidthPx: Float,
    frameHeightPx: Float,
    baseSize: Size,
    frameOffsetX: Float = 0f,
    frameOffsetY: Float = 0f,
    referenceWidth: Int = 0,
    referenceHeight: Int = 0
): StickerPlacement {
    val w = if (frameWidthPx > 0f) frameWidthPx else 1f
    val h = if (frameHeightPx > 0f) frameHeightPx else 1f
    // With an anchored reference, the stored fractions are fractions of that
    // reference, NOT of the current frame: they re-express through the uniform
    // fit of the reference into the CURRENT frame (spec rule 5), so a restore
    // lands the placement exactly where the committed render shows it even when
    // the frame drifts from the reference. An unset reference keeps the legacy
    // direct mapping (identity).
    val anchored = referenceWidth > 0 && referenceHeight > 0
    val wPx: Float
    val hPx: Float
    val xPx: Float
    val yPx: Float
    if (anchored) {
        val fit = UniformFit.of(w.toInt(), h.toInt(), referenceWidth, referenceHeight)
        wPx = fit.measureX(widthFrac, referenceWidth)
        hPx = fit.measureY(heightFrac, referenceHeight)
        xPx = fit.frameX(x, referenceWidth)
        yPx = fit.frameY(y, referenceHeight)
    } else {
        wPx = widthFrac * w
        hPx = heightFrac * h
        xPx = x * w
        yPx = y * h
    }
    return StickerPlacement(
        stickerId = stickerId,
        x = with(density) { (xPx + frameOffsetX).toDp().value },
        y = with(density) { (yPx + frameOffsetY).toDp().value },
        scaleX = if (baseSize.width > 0f) with(density) { wPx.toDp().value } / baseSize.width else 1f,
        scaleY = if (baseSize.height > 0f) with(density) { hPx.toDp().value } / baseSize.height else 1f,
        rotation = rotationDegrees,
        opacity = opacity,
        isFlipped = isFlipped,
        tintColor = tintColor,
        emojiText = emojiText,
        imageUri = imageUri,
        aspectRatio = aspectRatio,
        lineStyle = lineStyle,
        lineThickness = lineThickness,
        lineColor = lineColor
    )
}

/**
 * The sticker's intrinsic dp base size for a [StickerPlacement] — the same
 * geometry the committed [StickerItem] uses ([StickerItem.baseSizeDp]). A
 * preset uses its own width/height; an emoji its square base; a Gallery photo
 * keeps its own proportional box (long side anchored at the emoji default width,
 * the other side derived from the carried EXIF-oriented [StickerPlacement.aspectRatio],
 * so portrait is taller than wide and landscape wider than tall). Null only when
 * the placement carries neither a preset, emoji nor gallery URI. Used by the
 * live sprite and the overlay hit-target sizing so the on-canvas box, gestures,
 * re-open restore and the committed fold all agree.
 */
fun StickerPlacement.baseSizeDp(): Size? {
    StickerPresets.getStickerById(stickerId)?.let {
        return Size(it.width.toFloat(), it.height.toFloat())
    }
    if (emojiText != null) {
        return Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP)
    }
    if (imageUri != null) {
        val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        return Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP / ratio)
    }
    return null
}