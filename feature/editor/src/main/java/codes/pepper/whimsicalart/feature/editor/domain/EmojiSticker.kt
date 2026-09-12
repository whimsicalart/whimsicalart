package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Fraction of the sticker square left empty around a fitted emoji glyph. */
const val EMOJI_STICKER_FIT_MARGIN = 0.04f

/**
 * Scale factor to apply to a reference text size so the emoji's line box
 * (ascent–descent) and glyph width both fit inside a [sidePx]-sized square with
 * `margin * sidePx` of breathing room. [blockHeightPx] and [glyphWidthPx] must
 * be measured at the same reference text size the box is being fit into (the
 * fit is proportional). Returns a value in `(0, 1]` — the emoji never overflows
 * its sticker square on any side (spec: effects_reference.md → *Stickers* →
 * *Emoji rendering*).
 */
fun emojiFitScale(
    blockHeightPx: Float,
    glyphWidthPx: Float,
    sidePx: Float,
    margin: Float = EMOJI_STICKER_FIT_MARGIN
): Float {
    if (sidePx <= 0f || blockHeightPx <= 0f || glyphWidthPx <= 0f) return 1f
    val safeMargin = if (margin.isNaN()) EMOJI_STICKER_FIT_MARGIN else margin.coerceIn(0f, 0.5f)
    val usable = sidePx * (1f - safeMargin)
    return (usable / maxOf(blockHeightPx, glyphWidthPx)).coerceIn(1e-3f, 1f)
}

/**
 * Renders [emoji] into a `sidePx`×`sidePx` bitmap with the glyph scaled so its
 * line box (ascent–descent) and width fit the square with
 * [EMOJI_STICKER_FIT_MARGIN] of margin. This single Paint path is used by BOTH
 * the live preview (`StickerSprite`) and the committed bitmap
 * ([BitmapRenderer]), so an emoji sticker fills its sticker square and is never
 * cropped on any side, identically in preview and commit.
 */
fun renderEmojiGlyph(emoji: String, sidePx: Int): Bitmap {
    val side = sidePx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = side.toFloat()
    }
    val fm = paint.fontMetrics
    val blockHeight = fm.descent - fm.ascent
    val glyphWidth = paint.measureText(emoji)
    val scale = emojiFitScale(blockHeight, glyphWidth, side.toFloat())
    paint.textSize = side.toFloat() * scale
    // Centre the line box (ascent..descent — proportional to textSize) in the
    // square: baseline such that (ascent+descent)/2 sits at the square's middle.
    val baseline = side / 2f - (fm.ascent + fm.descent) * scale / 2f
    canvas.drawText(emoji, side / 2f, baseline, paint)
    return bitmap
}