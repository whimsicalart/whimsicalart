package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Xfermode
import android.os.Build
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a feathered (soft-edged) feature mask onto a canvas, matching the
 * analysed reference behaviour: the region is built as a mask and its boundary
 * is blurred so edits blend into the skin instead of leaving a hard cut. The
 * caller supplies the region [Path] plus the effect [color] and an [Xfermode]
 * (ADD, LIGHTEN, SRC_OVER, ...) so each beauty tool keeps its existing blend.
 *
 * The mask is drawn into a small off-screen bitmap (bounded to the feature),
 * box-blurred (premultiplied alpha) and composited through the given blend.
 * The feather is derived from the mask size so large regions (lips, cheeks)
 * feather more than tiny ones (eyes, teeth).
 */
object MaskedEffectRenderer {

    fun drawFeatheredMask(
        canvas: Canvas,
        path: Path,
        color: Int,
        xfermode: Xfermode?
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.xfermode = xfermode
        }
        drawFeatheredMask(canvas, path, paint)
    }

    /**
     * Feathered-mask variant that keeps the caller's [Paint] (colour, alpha and
     * blend mode) so soft-light-style tools (skin smoothing, foundation) get the
     * same blurred-boundary treatment as the colour/Xfermode tools.
     */
    fun drawFeatheredMask(canvas: Canvas, path: Path, paint: Paint) {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return

        val size = min(bounds.width(), bounds.height())
        val feather = (size * 0.18f).roundToInt().coerceIn(2, 24)
        val pad = ceil(feather * 2f + (feather * 0.12f)).toInt().coerceAtLeast(4)
        val width = (bounds.width() + pad * 2).toInt().coerceAtLeast(2)
        val height = (bounds.height() + pad * 2).toInt().coerceAtLeast(2)

        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = paint.color }
        maskCanvas.translate(-bounds.left + pad, -bounds.top + pad)
        maskCanvas.drawPath(path, fill)

        val blurred = boxBlurPremultiplied(mask, feather)
        mask.recycle()

        val composite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = paint.xfermode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = paint.blendMode
            }
        }
        canvas.drawBitmap(blurred, bounds.left - pad, bounds.top - pad, composite)
        blurred.recycle()
    }

    /**
     * Separable box blur that preserves premultiplied alpha: the colour
     * channels are accumulated as alpha-weighted (premultiplied) values so
     * feathered edges fade out to fully transparent without halos. The
     * internal pixel work is a pure function over the ARGB array so it can be
     * tested without a rasterising canvas.
     */
    private fun boxBlurPremultiplied(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return source
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)

        val result = boxBlurPixels(src, w, h, radius)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    /** Box-blurs a premultiplied ARGB pixel array (separable, two passes). */
    internal fun boxBlurPixels(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val horizontal = IntArray(w * h)
        blurAxis(src, horizontal, w, h, radius, horizontal = true)
        val result = IntArray(w * h)
        blurAxis(horizontal, result, w, h, radius, horizontal = false)
        return result
    }

    private fun blurAxis(input: IntArray, output: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val size = if (horizontal) w else h
        val row = IntArray(size)
        val window = 2 * radius + 1

        for (line in 0 until (if (horizontal) h else w)) {
            if (horizontal) {
                for (i in 0 until size) row[i] = input[line * w + i]
            } else {
                for (i in 0 until size) row[i] = input[i * w + line]
            }

            var a = 0
            var pr = 0L
            var pg = 0L
            var pb = 0L
            for (j in 0..minOf(radius, size - 1)) {
                val p = row[j]
                val alpha = p ushr 24
                a += alpha
                pr += (p ushr 16 and 0xff) * alpha
                pg += (p ushr 8 and 0xff) * alpha
                pb += (p and 0xff) * alpha
            }

            for (i in 0 until size) {
                val removeIdx = i - radius - 1
                val addIdx = i + radius
                if (removeIdx >= 0) {
                    val p = row[removeIdx]
                    val alpha = p ushr 24
                    a -= alpha
                    pr -= (p ushr 16 and 0xff) * alpha
                    pg -= (p ushr 8 and 0xff) * alpha
                    pb -= (p and 0xff) * alpha
                }
                if (addIdx < size) {
                    val p = row[addIdx]
                    val alpha = p ushr 24
                    a += alpha
                    pr += (p ushr 16 and 0xff) * alpha
                    pg += (p ushr 8 and 0xff) * alpha
                    pb += (p and 0xff) * alpha
                }

                if (a > 0) {
                    val avgA = (a / window).toInt().coerceIn(0, 255)
                    // Store properly premultiplied: channels must never exceed alpha.
                    val r = ((pr / a).toInt() * avgA / 255).coerceIn(0, 255)
                    val g = ((pg / a).toInt() * avgA / 255).coerceIn(0, 255)
                    val b = ((pb / a).toInt() * avgA / 255).coerceIn(0, 255)
                    if (horizontal) {
                        output[line * w + i] = Color.argb(avgA, r, g, b)
                    } else {
                        output[i * w + line] = Color.argb(avgA, r, g, b)
                    }
                } else {
                    if (horizontal) output[line * w + i] = 0 else output[i * w + line] = 0
                }
            }
        }
    }
}