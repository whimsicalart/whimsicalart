package com.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import android.content.Context
import kotlin.math.roundToInt

/**
 * Represents an overlay to draw onto the final bitmap. Coordinates are NORMALIZED
 * fractions of the canvas [0..1], so they map deterministically to any output size.
 */
data class StickerLayer(
    @DrawableRes val drawableRes: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f
)

data class TextLayer(
    val text: String,
    val color: Int,
    val fontSizeSp: Float,
    val x: Float,
    val y: Float,
    val rotationDegrees: Float = 0f
)

data class StrokeLayer(
    val type: StrokeType,
    val points: List<Pair<Float, Float>>,
    val brushSize: Float,
    val color: Int? = null,
    val opacity: Float = 1f
)

data class FrameLayer(
    val borderWidth: Float,
    val cornerRadius: Float,
    val color: Int
)

enum class StrokeType { MOSAIC, BLUR, PEN }

/**
 * The full render payload for a save/share: the selected filter's color matrix
 * (combined with the adjustments inside the renderer) plus every interactive
 * overlay, expressed in NORMALIZED [0..1] coordinates.
 */
data class EditorRenderBundle(
    val filterMatrix: FloatArray? = null,
    val stickers: List<StickerLayer> = emptyList(),
    val texts: List<TextLayer> = emptyList(),
    val strokes: List<StrokeLayer> = emptyList(),
    val frames: List<FrameLayer> = emptyList()
)

/**
 * Renders the full set of editor transformations onto an input bitmap:
 * rotation, flip, normalized crop, the combined adjustments+filter color
 * matrix, then composite sticker / text / mosaic / blur / pen overlays.
 */
object BitmapRenderer {

    fun render(
        context: Context,
        input: Bitmap,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        cropRect: Rect?,
        colorMatrix: FloatArray?,
        vignette: Float = 0f,
        sharpen: Float = 0f,
        stickers: List<StickerLayer> = emptyList(),
        texts: List<TextLayer> = emptyList(),
        strokes: List<StrokeLayer> = emptyList(),
        frames: List<FrameLayer> = emptyList()
    ): Bitmap {
        // 1. Rotation + flip.
        var canvas = transforms(input, rotationDegrees, flipHorizontal, flipVertical)

        // 2. Crop (normalized) applied in the transformed orientation.
        if (cropRect != null) {
            canvas = crop(canvas, cropRect, rotationDegrees)
        }

        // 3. Adjustments + filter color matrix.
        val result = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (colorMatrix != null) {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
        }
        Canvas(result).drawBitmap(canvas, 0f, 0f, paint)

        // 4. Sharpen (unsharp mask) and vignette (radial darkening) — these are
        //    not expressible as a color matrix, so run as per-pixel passes.
        if (sharpen != 0f) {
            applySharpen(canvas = result, amount = sharpen)
        }
        if (vignette != 0f) {
            applyVignette(canvas = result, strength = vignette)
        }

        // 5. Overlays.
        drawStickers(context, result, stickers)
        drawTexts(context, result, texts)
        drawStrokes(result, strokes)

        // 6. Frame borders sit on top of every overlay so they never get
        //    painted over by stickers, text or brush work.
        drawFrames(result, frames)

        return result
    }

    private fun transforms(
        input: Bitmap,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): Bitmap {
        val normalized = ((rotationDegrees % 360f) + 360f) % 360f
        val quarterTurns = (normalized / 90f).roundToInt() % 4
        val degrees = (quarterTurns * 90).toFloat()

        val transform = Matrix().apply {
            postRotate(degrees)
            postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f)
        }
        return Bitmap.createBitmap(
            input, 0, 0, input.width, input.height, transform, true
        )
    }

    private fun crop(input: Bitmap, cropRect: Rect, rotationDegrees: Float): Bitmap {
        val left = (cropRect.left * input.width).coerceIn(0f, input.width.toFloat())
        val top = (cropRect.top * input.height).coerceIn(0f, input.height.toFloat())
        val right = (cropRect.right * input.width).coerceIn(left, input.width.toFloat())
        val bottom = (cropRect.bottom * input.height).coerceIn(top, input.height.toFloat())
        var width = (right - left).roundToInt()
        var height = (bottom - top).roundToInt()
        if (width <= 0) width = 1
        if (height <= 0) height = 1
        return Bitmap.createBitmap(
            input,
            left.roundToInt(), top.roundToInt(),
            width, height
        )
    }

    /**
     * Unsharp-mask sharpen/soften. Shifts each channel toward the local average
     * (soften) or away from it (sharpen) by [amount] in [-1, 1]. Blur radius is
     * fixed small (2px) so edges respond without large halos.
     */
    private fun applySharpen(canvas: Bitmap, amount: Float) {
        val w = canvas.width
        val h = canvas.height
        if (w < 3 || h < 3) return
        val pixels = IntArray(w * h)
        canvas.getPixels(pixels, 0, w, 0, 0, w, h)
        val src = pixels.copyOf()
        val strength = (amount / 100f).coerceIn(-1f, 1f)
        val radius = 2

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val base = src[idx]
                val avg = boxAverageAt(src, w, h, x, y, radius)
                val r = (Color.red(base) + strength * (Color.red(base) - Color.red(avg)))
                    .roundToInt().coerceIn(0, 255)
                val g = (Color.green(base) + strength * (Color.green(base) - Color.green(avg)))
                    .roundToInt().coerceIn(0, 255)
                val b = (Color.blue(base) + strength * (Color.blue(base) - Color.blue(avg)))
                    .roundToInt().coerceIn(0, 255)
                pixels[idx] = Color.rgb(r, g, b)
            }
        }
        canvas.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * Radial edge darkening. [strength] in [-100, 100]; positive darkens the
     * corners toward the edges, negative lightens them. The centre is untouched.
     */
    private fun applyVignette(canvas: Bitmap, strength: Float) {
        val w = canvas.width
        val h = canvas.height
        if (w < 1 || h < 1) return
        val pixels = IntArray(w * h)
        canvas.getPixels(pixels, 0, w, 0, 0, w, h)
        val s = (strength / 100f).coerceIn(-1f, 1f)
        val maxAmount = 0.6f * s
        val cx = w / 2f
        val cy = h / 2f
        // Distance from centre to nearest edge (shortest axis) -> strongest falloff.
        val maxR = minOf(cx, cy).coerceAtLeast(1f)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = (x - cx) / maxR
                val dy = (y - cy) / maxR
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d < 1f) continue
                val t = (d - 1f).coerceIn(0f, 1f)
                val amount = maxAmount * t
                val idx = y * w + x
                val c = pixels[idx]
                val r = (Color.red(c) - amount * Color.red(c)).roundToInt().coerceIn(0, 255)
                val g = (Color.green(c) - amount * Color.green(c)).roundToInt().coerceIn(0, 255)
                val b = (Color.blue(c) - amount * Color.blue(c)).roundToInt().coerceIn(0, 255)
                pixels[idx] = Color.rgb(r, g, b)
            }
        }
        canvas.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun boxAverageAt(src: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int): Int {
        val x0 = (cx - radius).coerceAtLeast(0)
        val y0 = (cy - radius).coerceAtLeast(0)
        val x1 = (cx + radius).coerceAtMost(w - 1)
        val y1 = (cy + radius).coerceAtMost(h - 1)
        var r = 0L; var g = 0L; var b = 0L; var count = 0L
        for (y in y0..y1) {
            var offset = y * w + x0
            for (x in x0..x1) {
                val c = src[offset]
                r += Color.red(c); g += Color.green(c); b += Color.blue(c)
                count++
                offset++
            }
        }
        if (count == 0L) return src[cy * w + cx]
        return Color.rgb(
            (r / count).toInt(), (g / count).toInt(), (b / count).toInt()
        )
    }

    private fun drawFrames(canvas: Bitmap, frames: List<FrameLayer>) {
        for (f in frames) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val thickness = (f.borderWidth * canvas.width).coerceAtLeast(2f)
            val radius = (f.cornerRadius * canvas.width).coerceAtLeast(0f)
            val draw = android.graphics.Canvas(canvas)
            if (radius > 0f) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = thickness
                    color = f.color
                }
                val inset = thickness / 2f
                draw.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, paint)
            } else {
                // Four fill rects are identical visually to an outlined stroke
                // but rasterize deterministically on every backend (including
                // the Robolectric software canvas used by unit tests).
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = f.color }
                draw.drawRect(0f, 0f, w, thickness, paint)
                draw.drawRect(0f, h - thickness, w, h, paint)
                draw.drawRect(0f, 0f, thickness, h, paint)
                draw.drawRect(w - thickness, 0f, w, h, paint)
            }
        }
    }

    private fun drawStickers(context: Context, canvas: Bitmap, stickers: List<StickerLayer>) {
        if (stickers.isEmpty()) return
        val bitmap = canvas
        val draw = android.graphics.Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (s in stickers) {
            val stickerBitmap = ContextCompat.getDrawable(context, s.drawableRes)?.let {
                asBitmap(it, (s.width * bitmap.width).roundToInt().coerceAtLeast(1),
                    (s.height * bitmap.height).roundToInt().coerceAtLeast(1))
            } ?: continue
            val cx = s.x * bitmap.width
            val cy = s.y * bitmap.height
            val left = cx - stickerBitmap.width / 2f
            val top = cy - stickerBitmap.height / 2f
            paint.alpha = (s.opacity * 255f).roundToInt().coerceIn(0, 255)
            val matrix = Matrix().apply {
                postTranslate(left, top)
                postRotate(s.rotationDegrees, cx, cy)
            }
            draw.drawBitmap(stickerBitmap, matrix, paint)
        }
    }

    private fun asBitmap(drawable: android.graphics.drawable.Drawable, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    private fun drawTexts(context: Context, bitmap: Bitmap, texts: List<TextLayer>) {
        if (texts.isEmpty()) return
        val draw = android.graphics.Canvas(bitmap)
        for (t in texts) {
            val x = t.x * bitmap.width
            val y = t.y * bitmap.height
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = t.color
                // fontSizeSp is a NORMALIZED fraction of the canvas width, so it
                // stays proportionate to how the text is shown in the preview box.
                textSize = (t.fontSizeSp * bitmap.width).coerceAtLeast(4f)
            }
            val metrics = paint.fontMetrics
            val baseline = y - (metrics.ascent + metrics.descent) / 2f
            draw.save()
            draw.rotate(t.rotationDegrees, x, y)
            draw.drawText(t.text, x, baseline, paint)
            draw.restore()
        }
    }

    private fun drawStrokes(canvas: Bitmap, strokes: List<StrokeLayer>) {
        if (strokes.isEmpty()) return
        for (stroke in strokes) {
            when (stroke.type) {
                StrokeType.PEN -> drawPenStroke(canvas, stroke)
                StrokeType.MOSAIC -> applyPixelMosaic(canvas, stroke)
                StrokeType.BLUR -> applyBrushBlur(canvas, stroke)
            }
        }
    }

    private fun drawPenStroke(canvas: Bitmap, stroke: StrokeLayer) {
        val draw = android.graphics.Canvas(canvas)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke.brushSize * canvas.width
            strokeCap = Paint.Cap.ROUND
            color = stroke.color ?: Color.BLACK
        }
        drawPath(draw, stroke.points, paint, canvas.width, canvas.height)
    }

    /**
     * Real pixel-mosaic: reads the source pixels and repaints rectangular cells
     * with the block-average colour along every point of the stroke, so the
     * covered region genuinely pixelates instead of being scribbled over.
     */
    private fun applyPixelMosaic(canvas: Bitmap, stroke: StrokeLayer) {
        val w = canvas.width
        val h = canvas.height
        val src = canvas.copy(Bitmap.Config.ARGB_8888, false)
        val cell = (stroke.brushSize * w).roundToInt().coerceAtLeast(4)
        val alpha = (stroke.opacity * 255f).roundToInt().coerceIn(0, 255)
        val draw = android.graphics.Canvas(canvas)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        sampleAlong(stroke.points, cell / 2f, w, h) { px, py ->
            val x0 = (px - cell / 2f).roundToInt().coerceIn(0, (w - cell).coerceAtLeast(0))
            val y0 = (py - cell / 2f).roundToInt().coerceIn(0, (h - cell).coerceAtLeast(0))
            paint.color = blockAverage(src, x0, y0, cell, cell)
            draw.drawRect(x0.toFloat(), y0.toFloat(), (x0 + cell).toFloat(), (y0 + cell).toFloat(), paint)
        }
        src.recycle()
    }

    /**
     * Real brush blur: for every pixel inside each disc of the stroke it replaces
     * the value with a box-blur of the source, blended by the stroke opacity.
     * Operates on the pixel array and writes back once for reliability/performance.
     */
    private fun applyBrushBlur(canvas: Bitmap, stroke: StrokeLayer) {
        val w = canvas.width
        val h = canvas.height
        val src = IntArray(w * h)
        canvas.getPixels(src, 0, w, 0, 0, w, h)
        val radiusPx = (stroke.brushSize * w).roundToInt().coerceAtLeast(2)
        val blurRadius = (radiusPx / 3f).roundToInt().coerceIn(1, 20)
        val opacity = stroke.opacity.coerceIn(0f, 1f)

        sampleAlong(stroke.points, radiusPx.toFloat(), w, h) { px, py ->
            val cx = px.roundToInt()
            val cy = py.roundToInt()
            val x0 = (cx - radiusPx).coerceAtLeast(0)
            val y0 = (cy - radiusPx).coerceAtLeast(0)
            val x1 = (cx + radiusPx).coerceAtMost(w - 1)
            val y1 = (cy + radiusPx).coerceAtMost(h - 1)
            val r2 = radiusPx * radiusPx
            for (y in y0..y1) {
                for (x in x0..x1) {
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx + dy * dy > r2) continue
                    val idx = y * w + x
                    val blurred = boxAverageArray(src, w, h, x, y, blurRadius)
                    src[idx] = blend(src[idx], blurred, opacity)
                }
            }
        }
        canvas.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun boxAverageArray(src: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int): Int {
        val x0 = (cx - radius).coerceAtLeast(0)
        val y0 = (cy - radius).coerceAtLeast(0)
        val x1 = (cx + radius).coerceAtMost(w - 1)
        val y1 = (cy + radius).coerceAtMost(h - 1)
        var r = 0L; var g = 0L; var b = 0L; var count = 0L
        for (y in y0..y1) {
            var offset = y * w + x0
            for (x in x0..x1) {
                val c = src[offset]
                r += Color.red(c); g += Color.green(c); b += Color.blue(c)
                count++
                offset++
            }
        }
        return Color.rgb(
            (r / count).toInt(), (g / count).toInt(), (b / count).toInt()
        )
    }

    private fun sampleAlong(
        points: List<Pair<Float, Float>>,
        spacingPx: Float,
        w: Int,
        h: Int,
        block: (px: Float, py: Float) -> Unit
    ) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            block(points[0].first * w, points[0].second * h)
            return
        }
        val spacing = spacingPx.coerceAtLeast(1f)
        for (i in 0 until points.size - 1) {
            val ax = points[i].first * w
            val ay = points[i].second * h
            val bx = points[i + 1].first * w
            val by = points[i + 1].second * h
            val dist = kotlin.math.hypot(bx - ax, by - ay)
            val steps = (dist / spacing).roundToInt().coerceAtLeast(1)
            for (s in 0..steps) {
                block(ax + (bx - ax) * s / steps, ay + (by - ay) * s / steps)
            }
        }
    }

    private fun blockAverage(src: Bitmap, x0: Int, y0: Int, w: Int, h: Int): Int {
        val width = (x0 + w).coerceAtMost(src.width)
        val height = (y0 + h).coerceAtMost(src.height)
        var r = 0L; var g = 0L; var b = 0L
        var count = 0L
        for (y in y0 until height) {
            for (x in x0 until width) {
                val c = src.getPixel(x, y)
                r += Color.red(c); g += Color.green(c); b += Color.blue(c)
                count++
            }
        }
        if (count == 0L) return src.getPixel(x0.coerceAtMost(src.width - 1), y0.coerceAtMost(src.height - 1))
        return Color.rgb(
            (r / count).toInt(), (g / count).toInt(), (b / count).toInt()
        )
    }

    private fun blend(from: Int, into: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(from),
            (Color.red(from) * (1f - a) + Color.red(into) * a).roundToInt(),
            (Color.green(from) * (1f - a) + Color.green(into) * a).roundToInt(),
            (Color.blue(from) * (1f - a) + Color.blue(into) * a).roundToInt()
        )
    }

    private fun drawPath(
        draw: Canvas,
        points: List<Pair<Float, Float>>,
        paint: Paint,
        w: Int,
        h: Int
    ) {
        if (points.isEmpty()) return
        val path = android.graphics.Path()
        val first = points.first()
        path.moveTo(first.first * w, first.second * h)
        for (i in 1 until points.size) {
            val p = points[i]
            path.lineTo(p.first * w, p.second * h)
        }
        draw.drawPath(path, paint)
    }
}
