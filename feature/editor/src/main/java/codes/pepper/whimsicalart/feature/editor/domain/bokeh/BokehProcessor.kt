package codes.pepper.whimsicalart.feature.editor.domain.bokeh

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import codes.pepper.whimsicalart.feature.editor.domain.matting.SoftEdgeBand
import java.util.WeakHashMap
import kotlin.math.roundToInt

class BokehProcessor {

    companion object {

        /** How many freshly computed [blurBitmap] results each input bitmap may retain. */
        private const val BLUR_CACHE_CAPACITY = 4

        /** Memoization key for [blurBitmap]: the blur radius (w/h belong to the bitmap identity). */
        private data class BlurParam(val radiusPx: Int)

        /**
         * Memoized [blurBitmap] results, keyed by the input bitmap's identity and
         * the blur radius. The 4-pass 2×2 separable blur is the dominant bokeh
         * background cost, and it is recomputed on every radius-stable preview
         * drag tick even though the input reference and radius do NOT change.
         * Retaining the result per (bitmap, radius) makes the hot path pure
         * lookups.
         *
         * The input is the WEAK key (Bitmap does not override equals/hashCode, so
         * the map keys by identity) so a cleared bitmap's entries are collected
         * with it instead of pinning full-res bitmaps for the life of the
         * process. The cached result is only ever read (getPixels) after
         * publication — the callers never mutate it — so concurrent readers share
         * it safely. Access is synchronized (the shared cache is reachable from
         * the preview Default threads at once). Evicted entries are dropped
         * (never recycled: an in-flight caller may still hold the value).
         */
        private val blurCache = WeakHashMap<Bitmap, MutableMap<BlurParam, Bitmap>>()
    }

    /** Dimension-agnostic highlight-disc reach: ≈ 2px at a 500px short edge. `internal` for tests. */
    internal fun bokehDiscRadius(shortEdge: Int): Float = (shortEdge * 0.0025f).coerceAtLeast(1f)

    /**
     * Portrait-style background defocus that keeps the subject sharp.
     *
     * [foregroundMask] is white where the subject should stay in focus (any pixel
     * whose alpha >= [maskThreshold] is kept sharp). Everything outside the mask
     * is replaced with a blurred copy of [original]; where [bokehShape] is applied
     * the blurred background's bright highlights are re-stamped as shaped discs to
     * give the bokeh its characteristic defocused-light look.
     *
     * Purely pixel-level compositing so it rasterizes deterministically on every
     * backend (including the Robolectric software canvas used by unit tests).
     */
    fun applyBackgroundBlur(
        original: Bitmap,
        foregroundMask: Bitmap,
        blurRadius: Float,
        bokehShape: BokehShape = BokehShape.CIRCLE,
        maskThreshold: Int = 128,
        softEdgeWidth: Int = 0
    ): Bitmap {
        // Intensity 0 means no blur at all. `original` is never mutated on this
        // path, and every caller (BitmapRenderer.render, EditorViewModel's
        // recycleAndApply and StackEffect.render) now gates on reference identity
        // before drawing/recycling the returned bitmap, so returning the SAME
        // reference skips a full alloc+copy per zero-intensity publication.
        if (blurRadius <= 0f) {
            return original
        }
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val blurred = blurBitmap(original, blurRadius)
        val edge = if (softEdgeWidth > 0) {
            SoftEdgeBand.depthMap(foregroundMask, maskThreshold, softEdgeWidth)
        } else {
            null
        }

        val w = original.width
        val h = original.height
        val maskW = foregroundMask.width.coerceAtLeast(1)
        val maskH = foregroundMask.height.coerceAtLeast(1)

        val pixels = IntArray(w * h)
        val blurredPixels = IntArray(w * h)
        val maskPixels = IntArray(maskW * maskH)
        original.getPixels(pixels, 0, w, 0, 0, w, h)
        blurred.getPixels(blurredPixels, 0, w, 0, 0, w, h)
        foregroundMask.getPixels(maskPixels, 0, maskW, 0, 0, maskW, maskH)
        val resultPixels = pixels.copyOf()

        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) {
                val idx = base + x
                val mx = (x * maskW / w).coerceIn(0, maskW - 1)
                val my = (y * maskH / h).coerceIn(0, maskH - 1)
                val mIdx = my * maskW + mx
                val maskAlpha = Color.alpha(maskPixels[mIdx])
                if (maskAlpha < maskThreshold) {
                    resultPixels[idx] = blurredPixels[idx]
                } else if (edge != null && edge[mIdx] < softEdgeWidth) {
                    // Soft edge: the blurred background fades into the subject
                    // over the few-px band inward from the mask path.
                    val t = edge[mIdx] / softEdgeWidth.toFloat()
                    resultPixels[idx] = SoftEdgeBand.blend(pixels[idx], blurredPixels[idx], t)
                }
            }
        }
        result.setPixels(resultPixels, 0, w, 0, 0, w, h)

        if (bokehShape != BokehShape.CIRCLE) {
            applyShapeHighlights(result, resultPixels, bokehShape)
        }

        return result
    }

    /**
     * Re-stamps the bright, already-blurred highlights of the background
     * (pixels with high luma and strong alpha contribution) as the selected
     * bokeh disc shape. This is the subtle "shaped bokeh" look; intensity is
     * driven by pixel luma so it only affects actual highlights.
     *
     * Reads the freshly composited pixels from [resultPixels] (the same array
     * [applyBackgroundBlur] just pushed into [result]) instead of one per-pixel
     * canvas `getPixel` JNI call, and hoists the disc radius (it only depends on
     * the image short edge). Discs drawn over a previously-scanned pixel do not
     * re-influence a later read (pixels, not the live canvas, are the source of
     * truth for the highlight decision), which is both faster and deterministic.
     */
    private fun applyShapeHighlights(
        result: Bitmap,
        resultPixels: IntArray,
        shape: BokehShape
    ) {
        val w = result.width
        val h = result.height
        val canvas = Canvas(result)
        // One effect, two scales: the highlight disc is a fraction of the live
        // image, so shaped bokeh stays equally visible on a preview and at full
        // size. Hoisted out of the per-pixel loop (short-edge only).
        val radius = bokehDiscRadius(minOf(w, h))
        for (idx in resultPixels.indices) {
            val c = resultPixels[idx]
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            // A highlight: bright and relatively desaturated core.
            val luma = (r * 0.3f + g * 0.59f + b * 0.11f)
            if (luma > 200f && (max - min) < 70) {
                drawBokehDisc(canvas, (idx % w).toFloat(), (idx / w).toFloat(), radius, shape, Color.argb(120, r, g, b))
            }
        }
    }

    private fun drawBokehDisc(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        shape: BokehShape,
        color: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        when (shape) {
            BokehShape.HEXAGON -> {
                val path = Path()
                val n = 6
                for (i in 0 until n) {
                    val angle = Math.toRadians((60 * i - 30).toDouble())
                    val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
                    val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            BokehShape.HEART -> {
                val path = Path()
                path.moveTo(cx, cy + radius * 0.35f)
                path.cubicTo(
                    cx - radius, cy - radius * 0.4f,
                    cx - radius * 0.9f, cy - radius,
                    cx - radius * 0.2f, cy - radius * 0.6f
                )
                path.lineTo(cx, cy - radius * 0.35f)
                path.cubicTo(
                    cx + radius * 0.2f, cy - radius * 0.6f,
                    cx + radius * 0.9f, cy - radius,
                    cx + radius, cy - radius * 0.4f
                )
                path.moveTo(cx, cy + radius * 0.35f)
                path.cubicTo(
                    cx + radius, cy - radius * 0.4f,
                    cx + radius * 0.9f, cy - radius,
                    cx + radius * 0.2f, cy - radius * 0.6f
                )
                path.close()
                canvas.drawPath(path, paint)
            }
            BokehShape.CIRCLE -> {
                canvas.drawCircle(cx, cy, radius, paint)
            }
        }
    }

    fun createForegroundMask(
        bitmap: Bitmap,
        foregroundRect: RectF,
        featherRadius: Float = 20f
    ): Bitmap {
        val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)

        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }

        val path = Path().apply {
            addRoundRect(
                foregroundRect,
                featherRadius,
                featherRadius,
                Path.Direction.CW
            )
        }

        canvas.drawPath(path, paint)
        return mask
    }

    fun applyBokehEffect(
        bitmap: Bitmap,
        mask: Bitmap,
        intensity: Float,
        bokehShape: BokehShape
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val paint = Paint().apply {
            alpha = (intensity * 255).toInt()
        }

        when (bokehShape) {
            BokehShape.CIRCLE -> {
                drawCircleBokeh(canvas, mask, paint)
            }
            BokehShape.HEXAGON -> {
                drawHexagonBokeh(canvas, mask, paint)
            }
            BokehShape.HEART -> {
                drawHeartBokeh(canvas, mask, paint)
            }
        }

        return result
    }

    /**
     * Software separable blur of [bitmap] at [radius], memoized per input
     * bitmap identity + radius (see the shared [companion]'s [BlurParam]
     * cache) so radius-stable bokeh drags skip the dominant background cost
     * entirely. The cached result is never mutated after publication. `internal`
     * for the cache test.
     */
    internal fun blurBitmap(bitmap: Bitmap, radius: Float): Bitmap {
        val radiusPx = radius.roundToInt().coerceAtLeast(1)
        val params = BlurParam(radiusPx)
        synchronized(blurCache) {
            blurCache[bitmap]?.get(params)?.let { return it }
        }
        val computed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            .apply { applySoftwareBlur(this, radiusPx) }
        synchronized(blurCache) {
            var byBitmap = blurCache[bitmap]
            if (byBitmap == null || byBitmap.size >= BLUR_CACHE_CAPACITY) {
                byBitmap = mutableMapOf()
                blurCache[bitmap] = byBitmap
            }
            byBitmap[params] = computed
        }
        return computed
    }

    /**
     * Software separable box blur. Two passes (horizontal then
     * vertical) repeated twice approximate a gaussian kernel closely enough for
     * a defocus background while staying cheap on CPU.
     */
    internal fun applySoftwareBlur(bitmap: Bitmap, radiusPx: Int) {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 1 || h < 1) return
        val source = IntArray(w * h)
        val target = IntArray(w * h)
        bitmap.getPixels(source, 0, w, 0, 0, w, h)
        repeat(2) {
            boxBlurPass(source, target, w, h, radiusPx, horizontal = true)
            boxBlurPass(target, source, w, h, radiusPx, horizontal = false)
        }
        bitmap.setPixels(source, 0, w, 0, 0, w, h)
    }

    internal fun boxBlurPass(
        input: IntArray,
        output: IntArray,
        w: Int,
        h: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        val length = if (horizontal) w else h
        val stride = if (horizontal) 1 else w
        val count = 2 * radius + 1
        for (outer in 0 until (if (horizontal) h else w)) {
            val base = if (horizontal) outer * w else outer

            fun index(pos: Int): Int = base + pos.coerceIn(0, length - 1) * stride

            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (k in -radius..radius) {
                val c = input[index(k)]
                sumR += Color.red(c)
                sumG += Color.green(c)
                sumB += Color.blue(c)
            }
            for (pos in 0 until length) {
                output[index(pos)] = Color.rgb(
                    (sumR / count).toInt(),
                    (sumG / count).toInt(),
                    (sumB / count).toInt()
                )
                val leaving = input[index(pos - radius)]
                val entering = input[index(pos + radius + 1)]
                sumR += Color.red(entering) - Color.red(leaving)
                sumG += Color.green(entering) - Color.green(leaving)
                sumB += Color.blue(entering) - Color.blue(leaving)
            }
        }
    }

    private fun drawCircleBokeh(canvas: Canvas, mask: Bitmap, paint: Paint) {
        val path = Path()
        val centerX = mask.width / 2f
        val centerY = mask.height / 2f
        val radius = minOf(mask.width, mask.height) / 2f

        path.addCircle(centerX, centerY, radius, Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawBitmap(mask, 0f, 0f, paint)
    }

    private fun drawHexagonBokeh(canvas: Canvas, mask: Bitmap, paint: Paint) {
        val path = Path()
        val centerX = mask.width / 2f
        val centerY = mask.height / 2f
        val radius = minOf(mask.width, mask.height) / 2f

        for (i in 0..5) {
            val angle = Math.toRadians((60 * i - 30).toDouble())
            val x = centerX + (radius * kotlin.math.cos(angle)).toFloat()
            val y = centerY + (radius * kotlin.math.sin(angle)).toFloat()

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        canvas.clipPath(path)
        canvas.drawBitmap(mask, 0f, 0f, paint)
    }

    private fun drawHeartBokeh(canvas: Canvas, mask: Bitmap, paint: Paint) {
        val path = Path()
        val centerX = mask.width / 2f
        val centerY = mask.height / 2f
        val size = minOf(mask.width, mask.height) / 2f

        path.moveTo(centerX, centerY + size / 4)
        path.cubicTo(
            centerX + size / 2, centerY - size / 2,
            centerX + size, centerY + size / 4,
            centerX, centerY + size
        )
        path.moveTo(centerX, centerY + size / 4)
        path.cubicTo(
            centerX - size / 2, centerY - size / 2,
            centerX - size, centerY + size / 4,
            centerX, centerY + size
        )
        path.close()

        canvas.clipPath(path)
        canvas.drawBitmap(mask, 0f, 0f, paint)
    }
}

enum class BokehShape {
    CIRCLE,
    HEXAGON,
    HEART
}

data class BokehConfig(
    val intensity: Float = 0.5f,
    val blurRadius: Float = 10f,
    val shape: BokehShape = BokehShape.CIRCLE,
    val foregroundRect: RectF? = null
)
