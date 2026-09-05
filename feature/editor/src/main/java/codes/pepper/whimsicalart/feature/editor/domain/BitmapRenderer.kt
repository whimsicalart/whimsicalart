package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import android.content.Context
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehProcessor
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehShape
import codes.pepper.whimsicalart.feature.editor.domain.enhance.EnhanceProcessor
import codes.pepper.whimsicalart.feature.editor.domain.enhance.EnhanceSettings
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilterProcessor
import codes.pepper.whimsicalart.feature.editor.domain.matting.BackgroundReplacer
import codes.pepper.whimsicalart.feature.editor.domain.removal.DiffusionInpainter
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
    val color: Int,
    val shadowColor: Int? = null,
    val shadowRadius: Float = 0f
)

/**
 * Which background effect to apply to the region outside the subject mask.
 */
enum class BackgroundMode {
    /** Defocus (blur) the background, keeping the subject sharp. */
    BLUR,

    /** Replace the background with a chosen image, keeping the subject intact. */
    REPLACE
}

/**
 * Subject-aware background layer. [subjectMask] is the person/foreground alpha
 * mask (source-sized, alpha = foreground confidence) produced by on-device
 * segmentation; [mode] decides whether the outside of that mask is defocused
 * ([BackgroundMode.BLUR], intensity = [blurRadius] as a fraction of canvas
 * width, shaped by [shape]) or swapped for [backgroundImage]
 * ([BackgroundMode.REPLACE]).
 */
data class BackgroundLayer(
    val mode: BackgroundMode = BackgroundMode.BLUR,
    val subjectMask: Bitmap? = null,
    val blurRadius: Float = 10f,
    val shape: BokehShape = BokehShape.CIRCLE,
    val backgroundImage: Bitmap? = null
)

enum class StrokeType { MOSAIC, BLUR, PEN, REMOVAL }

/**
 * The full render payload for a save/share: the selected filter's color matrix
 * (combined with the adjustments inside the renderer) plus every interactive
 * overlay, expressed in NORMALIZED [0..1] coordinates.
 */
data class EditorRenderBundle(
    val filterMatrix: FloatArray? = null,
    val styleFilter: StyleFilter? = null,
    val stickers: List<StickerLayer> = emptyList(),
    val texts: List<TextLayer> = emptyList(),
    val strokes: List<StrokeLayer> = emptyList(),
    val frames: List<FrameLayer> = emptyList(),
    val background: BackgroundLayer? = null,
    val suggestedRegions: List<RectF> = emptyList()
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
        enhance: EnhanceSettings? = null,
        styleFilter: StyleFilter? = null,
        stickers: List<StickerLayer> = emptyList(),
        texts: List<TextLayer> = emptyList(),
        strokes: List<StrokeLayer> = emptyList(),
        frames: List<FrameLayer> = emptyList(),
        background: BackgroundLayer? = null,
        suggestedRegions: List<RectF> = emptyList()
    ): Bitmap {
        // 1. Rotation + flip.
        var canvas = transforms(input, rotationDegrees, flipHorizontal, flipVertical)

        // 2. Crop (normalized) applied in the transformed orientation.
        if (cropRect != null) {
            canvas = crop(canvas, cropRect)
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

        // 4.5. One-tap Auto-Enhance / HDR tone map. Runs after the colour matrix
        // and sharpening so it grades the already-adjusted pixels, and before the
        // subject-aware background/overlays so they paint consistently on top.
        if (enhance != null) {
            val enhanced = EnhanceProcessor.enhance(result, enhance)
            if (enhanced !== result) {
                val draw = Canvas(result)
                draw.drawBitmap(enhanced, 0f, 0f, null)
                enhanced.recycle()
            }
        }

        // 4.6. Learned-look style filter layer, applied on top of the enhance map.
        if (styleFilter != null) {
            val styled = StyleFilterProcessor.apply(result, styleFilter)
            if (styled !== result) {
                val draw = Canvas(result)
                draw.drawBitmap(styled, 0f, 0f, null)
                styled.recycle()
            }
        }

        // 5. Subject-aware background: defocus (bokeh) or replace the region
        //    outside the segmentation mask. Runs after the global color/sharpen/
        //    vignette passes so the result matches the colour-graded subject,
        //    but before interactive overlays paint on top.
        val bg = background
        val subjectMask = bg?.subjectMask
        if (bg != null && subjectMask != null) {
            when (bg.mode) {
                BackgroundMode.BLUR -> {
                    val radiusPx = (bg.blurRadius * result.width).coerceAtLeast(1f)
                    BokehProcessor().applyBackgroundBlur(
                        original = result,
                        foregroundMask = subjectMask,
                        blurRadius = radiusPx,
                        bokehShape = bg.shape
                    )
                }
                BackgroundMode.REPLACE -> {
                    val image = bg.backgroundImage
                    if (image != null) {
                        BackgroundReplacer.composite(result, subjectMask, image)
                    }
                }
            }
        }

        // 6. Overlays.
        drawStickers(context, result, stickers)
        drawTexts(context, result, texts)
        drawStrokes(result, strokes)
        drawPrivacyRegions(result, suggestedRegions)

        // 7. Frame borders sit on top of every overlay so they never get
        //    painted over by stickers, text or brush work.
        drawFrames(result, frames)

        return result
    }

    /**
     * Folds an ordered [effects] stack left-to-right over [input] (the pristine
     * base photo), producing the final image. This is the incremental render
     * path used by the save/merge/share pipeline and the Layers thumbnails.
     *
     * Each effect is a self-contained [StackEffect.render] step applied in stack
     * order to the running image, so order matters and every added effect folds
     * on top of the already-cropped, already-transformed image (the behaviour
     * that "crop not applying" previously broke, when the stack snapshot carried
     * crop LAST, out of sync with the renderer).
     */
    fun renderStack(
        context: Context,
        input: Bitmap,
        effects: List<StackEffect>
    ): Bitmap {
        return CascadeRenderer.fold(context, input, effects)
    }

    /**
     * Applies only the unsharp-mask sharpen/soften to a copy of [input] so the
     * editor can preview the sharpness slider live (everything else in the
     * preview is handled by the color matrix / overlay composables).
     */
    fun sharpenPreview(input: Bitmap, amount: Float): Bitmap {
        val out = input.copy(Bitmap.Config.ARGB_8888, true)
        applySharpen(out, amount)
        return out
    }

    /**
     * Functional single matrix-pass helper for the per-effect fold: draws a copy
     * of [current] through the [matrix] (or unchanged when [matrix] is null) and
     * returns a NEW bitmap, leaving [current] untouched (owned by the fold).
     */
    internal fun applyMatrixPass(current: Bitmap, matrix: FloatArray?): Bitmap {
        val result = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (matrix != null) colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(result).drawBitmap(current, 0f, 0f, paint)
        return result
    }

    /**
     * Functional unsharp-mask sharpen/soften (or vignette) pass: returns a copy
     * of [current] with [amount] applied in place, leaving [current] untouched.
     */
    internal fun applySharpenCopy(current: Bitmap, amount: Float): Bitmap {
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        applySharpen(out, amount)
        return out
    }

    /** Functional vignette pass: returns an altered copy of [current]. */
    internal fun applyVignetteCopy(current: Bitmap, strength: Float): Bitmap {
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        applyVignette(out, strength)
        return out
    }

    /**
     * Produces the actual cropped region (after applying rotation + flips),
     * used by the Crop tool's "eye" preview-now toggle so the user sees the
     * final crop composition instead of the full photo with a grid. Mirrors the
     * rotation/flip/crop steps of [render]. Returns null when nothing is cropped.
     */
    fun cropPreview(
        input: Bitmap,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        cropRect: Rect?
    ): Bitmap? {
        if (cropRect == null) return null
        val oriented = transforms(input, rotationDegrees, flipHorizontal, flipVertical)
        return crop(oriented, cropRect)
    }

    internal fun transforms(
        input: Bitmap,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): Bitmap {
        val normalized = ((rotationDegrees % 360f) + 360f) % 360f
        val quarterTurns = (normalized / 90f).roundToInt() % 4
        val degrees = (quarterTurns * 90).toFloat()
        // True when [normalized] is a (near-)multiple of 90°, in which case the
        // existing cheap path applies — the canvas simply swaps dimensions and the
        // source fills it exactly, with no transparent gutter. For any other
        // (custom/free) angle the canvas must grow to fit the rotated image with a
        // TRANSPARENT background (white is applied only at JPEG export time).
        val remainder = normalized % 90f
        val isExact90 = remainder < 0.5f || remainder > 89.5f

        val transform = Matrix().apply {
            postRotate(normalized)
            postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f)
        }

        if (isExact90) {
            return Bitmap.createBitmap(
                input, 0, 0, input.width, input.height, transform, true
            )
        }

        // Custom angle: map the source rect through the matrix to find the rotated
        // bounding box, grow the canvas to exactly fit it (centred), and draw with
        // a filtered paint so the rotation stays smooth. Background is transparent.
        val srcRect = RectF(0f, 0f, input.width.toFloat(), input.height.toFloat())
        val rotatedRect = RectF()
        transform.mapRect(rotatedRect, srcRect)
        val outW = rotatedRect.width().roundToInt().coerceAtLeast(1)
        val outH = rotatedRect.height().roundToInt().coerceAtLeast(1)
        val centered = Matrix(transform)
        centered.postTranslate(-rotatedRect.left, -rotatedRect.top)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { isFilterBitmap = true }
        Canvas(out).drawBitmap(input, centered, paint)
        return out
    }

    internal fun crop(input: Bitmap, cropRect: Rect): Bitmap {
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
     * 4px so the effect is clearly visible on phone screens without large halos.
     */
    internal fun applySharpen(canvas: Bitmap, amount: Float) {
        val w = canvas.width
        val h = canvas.height
        if (w < 9 || h < 9) return
        val pixels = IntArray(w * h)
        canvas.getPixels(pixels, 0, w, 0, 0, w, h)
        val src = pixels.copyOf()
        val strength = (amount / 100f).coerceIn(-1f, 1f)
        val radius = 4

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
                pixels[idx] = Color.argb(Color.alpha(base), r, g, b)
            }
        }
        canvas.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * Radial edge darkening. [strength] in [-100, 100]; positive darkens the
     * corners toward the edges, negative lightens them. The centre is untouched.
     */
    internal fun applyVignette(canvas: Bitmap, strength: Float) {
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
                pixels[idx] = Color.argb(Color.alpha(c), r, g, b)
            }
        }
        canvas.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun boxAverageAt(src: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int): Int {
        val x0 = (cx - radius).coerceAtLeast(0)
        val y0 = (cy - radius).coerceAtLeast(0)
        val x1 = (cx + radius).coerceAtMost(w - 1)
        val y1 = (cy + radius).coerceAtMost(h - 1)
        var r = 0L; var g = 0L; var b = 0L; var a = 0L; var count = 0L
        for (y in y0..y1) {
            var offset = y * w + x0
            for (x in x0..x1) {
                val c = src[offset]
                val alpha = Color.alpha(c)
                if (alpha == 0) { offset++; continue }
                r += Color.red(c); g += Color.green(c); b += Color.blue(c); a += alpha
                count++
                offset++
            }
        }
        if (count == 0L) return src[cy * w + cx]
        return Color.argb(
            (a / count).toInt(), (r / count).toInt(), (g / count).toInt(), (b / count).toInt()
        )
    }

    internal fun drawFrames(canvas: Bitmap, frames: List<FrameLayer>) {
        for (f in frames) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val thickness = (f.borderWidth * canvas.width).coerceAtLeast(2f)
            val radius = (f.cornerRadius * canvas.width).coerceAtLeast(0f)

            if (f.shadowColor != null && f.shadowRadius > 0f) {
                drawFrameShadow(canvas, f, thickness, radius)
            }

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

    /**
     * Renders the frame's drop shadow as a soft, box-blurred band just inside
     * the photo edges, behind the crisp border ring drawn by [drawFrames]. Kept
     * as a deterministic pixel pass (box blur) so it rasterizes identically on
     * every backend, including the Robolectric software canvas.
     */
    private fun drawFrameShadow(
        canvas: Bitmap,
        f: FrameLayer,
        thickness: Float,
        radius: Float
    ) {
        val w = canvas.width
        val h = canvas.height
        if (w < 1 || h < 1) return

        val blurPx = (f.shadowRadius * w).roundToInt().coerceIn(1, 40)
        val band = (thickness * 0.5f).coerceAtLeast(2f).roundToInt()

        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.eraseColor(Color.TRANSPARENT)
        val dm = android.graphics.Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        if (radius > 0f) {
            dm.drawRoundRect(
                band.toFloat(), band.toFloat(),
                (w - band).toFloat(), (h - band).toFloat(),
                radius, radius, paint
            )
        } else {
            dm.drawRect(band.toFloat(), band.toFloat(), (w - band).toFloat(), (h - band).toFloat(), paint)
        }
        boxBlurColor(mask, blurPx)

        val src = IntArray(w * h)
        canvas.getPixels(src, 0, w, 0, 0, w, h)
        val msk = IntArray(w * h)
        mask.getPixels(msk, 0, w, 0, 0, w, h)
        val shadow = f.shadowColor ?: Color.BLACK
        val sr = Color.red(shadow); val sg = Color.green(shadow); val sb = Color.blue(shadow)
        for (i in src.indices) {
            val a = Color.alpha(msk[i])
            if (a == 0) continue
            val t = a / 255f * 0.8f
            val base = src[i]
            src[i] = Color.rgb(
                (Color.red(base) * (1f - t) + sr * t).roundToInt().coerceIn(0, 255),
                (Color.green(base) * (1f - t) + sg * t).roundToInt().coerceIn(0, 255),
                (Color.blue(base) * (1f - t) + sb * t).roundToInt().coerceIn(0, 255)
            )
        }
        canvas.setPixels(src, 0, w, 0, 0, w, h)
        mask.recycle()
    }

    /** In-place intensity-only box blur of a greyscale mask (alpha channel). */
    private fun boxBlurColor(mask: Bitmap, radius: Int) {
        val w = mask.width
        val h = mask.height
        if (w < 1 || h < 1 || radius <= 0) return
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        mask.getPixels(src, 0, w, 0, 0, w, h)
        var cur = src
        var next = dst
        repeat(2) {
            // Horizontal pass
            for (y in 0 until h) {
                var sum = 0
                for (x in -radius..radius) {
                    sum += Color.alpha(cur[y * w + x.coerceIn(0, w - 1)])
                }
                for (x in 0 until w) {
                    next[y * w + x] = Color.argb(sum / (2 * radius + 1), 255, 255, 255)
                    val leaving = Color.alpha(cur[y * w + (x - radius).coerceIn(0, w - 1)])
                    val entering = Color.alpha(cur[y * w + (x + radius + 1).coerceIn(0, w - 1)])
                    sum += entering - leaving
                }
            }
            // Vertical pass
            val tmp = cur
            cur = next
            next = tmp
            for (x in 0 until w) {
                var sum = 0
                for (y in -radius..radius) {
                    sum += Color.alpha(cur[y.coerceIn(0, h - 1) * w + x])
                }
                for (y in 0 until h) {
                    next[y * w + x] = Color.argb(sum / (2 * radius + 1), 255, 255, 255)
                    val leaving = Color.alpha(cur[(y - radius).coerceIn(0, h - 1) * w + x])
                    val entering = Color.alpha(cur[(y + radius + 1).coerceIn(0, h - 1) * w + x])
                    sum += entering - leaving
                }
            }
            val tmp2 = cur
            cur = next
            next = tmp2
        }
        // cur holds the final blurred mask.
        mask.setPixels(cur, 0, w, 0, 0, w, h)
    }

    internal fun drawStickers(context: Context, canvas: Bitmap, stickers: List<StickerLayer>) {
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

    internal fun drawTexts(context: Context, bitmap: Bitmap, texts: List<TextLayer>) {
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

    internal fun drawStrokes(canvas: Bitmap, strokes: List<StrokeLayer>) {
        if (strokes.isEmpty()) return
        val regular = ArrayList<StrokeLayer>()
        val removals = ArrayList<StrokeLayer>()
        for (stroke in strokes) {
            if (stroke.type == StrokeType.REMOVAL) removals.add(stroke) else regular.add(stroke)
        }
        for (stroke in regular) {
            when (stroke.type) {
                StrokeType.PEN -> drawPenStroke(canvas, stroke)
                StrokeType.MOSAIC -> applyPixelMosaic(canvas, stroke)
                StrokeType.BLUR -> applyBrushBlur(canvas, stroke)
                StrokeType.REMOVAL -> Unit
            }
        }
        if (removals.isNotEmpty()) {
            applyRemoval(canvas, removals)
        }
    }

    /**
     * Content-aware object removal. All painted removal strokes are merged into a
     * single mask, then [DiffusionInpainter] propagates the surrounding background
     * colours inward so the brushed object disappears (a classical inpaint; a
     * learned model can later replace [DiffusionInpainter] behind the
     * [codes.pepper.whimsicalart.feature.editor.domain.removal.InpaintSegmenter]
     * interface without touching this path).
     */
    private fun applyRemoval(canvas: Bitmap, strokes: List<StrokeLayer>) {
        val w = canvas.width
        val h = canvas.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val maskDraw = android.graphics.Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(255, 255, 255, 255)
        }
        for (stroke in strokes) {
            val radiusPx = (stroke.brushSize * w).toFloat()
            sampleAlong(stroke.points, radiusPx, w, h) { px, py ->
                maskDraw.drawCircle(px, py, radiusPx, paint)
            }
        }
        val inpainted = DiffusionInpainter.inpaint(canvas, mask)
        if (inpainted != null && inpainted !== canvas) {
            val draw = android.graphics.Canvas(canvas)
            draw.drawBitmap(inpainted, 0f, 0f, null)
            inpainted.recycle()
        }
        mask.recycle()
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
     * Pixel-blocks every auto-suggested privacy region (normalized [0..1] rects,
     * e.g. faces detected in the Mosaic tool). Unlike the freeform brush it fills
     * the whole rect on a cell grid so private regions are uniformly masked.
     */
    internal fun drawPrivacyRegions(canvas: Bitmap, regions: List<RectF>) {
        if (regions.isEmpty()) return
        val w = canvas.width
        val h = canvas.height
        val src = canvas.copy(Bitmap.Config.ARGB_8888, false)
        val draw = android.graphics.Canvas(canvas)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (region in regions) {
            val left = (region.left * w).roundToInt().coerceIn(0, w - 1)
            val top = (region.top * h).roundToInt().coerceIn(0, h - 1)
            val right = (region.right * w).roundToInt().coerceIn(left + 1, w)
            val bottom = (region.bottom * h).roundToInt().coerceIn(top + 1, h)
            val cell = ((right - left) / 3f).roundToInt().coerceAtLeast(8)
            var y = top
            while (y < bottom) {
                var x = left
                while (x < right) {
                    val cw = cell.coerceAtMost(right - x)
                    val ch = cell.coerceAtMost(bottom - y)
                    paint.color = blockAverage(src, x, y, cw, ch)
                    draw.drawRect(x.toFloat(), y.toFloat(), (x + cw).toFloat(), (y + ch).toFloat(), paint)
                    x += cw
                }
                y += cell
            }
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
