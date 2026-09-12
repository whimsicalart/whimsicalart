package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Xfermode
import android.os.Build
import java.util.LinkedHashMap
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
     * Dimension-agnostic feathered-edge reach: 18% of the mask's short edge,
     * capped at a fraction of the mask (a huge full-size mask cannot
     * over-feather). `internal` for tests.
     */
    internal fun featheredEdge(size: Float): Int =
        (size * 0.18f).roundToInt()
            .coerceIn(2, (size * 0.4f).roundToInt().coerceAtLeast(2))

    /**
     * Feathered-mask variant that keeps the caller's [Paint] (colour, alpha and
     * blend mode) so soft-light-style tools (skin smoothing, foundation) get the
     * same blurred-boundary treatment as the colour/Xfermode tools.
     *
     * The mask construction (off-screen bitmap + drawPath + box blur) depends
     * ONLY on the region [Path]'s geometry and the effect's RGB colour — never
     * on the intensity, which merely modulates the composite opacity. A slider
     * drag re-runs the effect with the same face geometry and colour on every
     * fold, so the finished mask is cached keyed by a content signature of the
     * path (see [maskKey]) and each tick just recomposites the stored bitmap —
     * skipping the allocation + blur entirely, with the paint's alpha (the
     * intensity) applied at draw time instead of being baked into the blur.
     */
    fun drawFeatheredMask(canvas: Canvas, path: Path, paint: Paint) {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return

        val size = min(bounds.width(), bounds.height())
        // One effect, two scales: the feathered edge is 18% of the mask bounds
        // (the old fixed 24px cap made previews feather ~12% of a face while the
        // full-size fold feathered ~1.5% of the same face); the ceiling is a
        // fraction of the mask so a huge full-size mask cannot over-feather.
        val feather = featheredEdge(size)
        val pad = ceil(feather * 2f + (feather * 0.12f)).toInt().coerceAtLeast(4)
        val width = (bounds.width() + pad * 2).toInt().coerceAtLeast(2)
        val height = (bounds.height() + pad * 2).toInt().coerceAtLeast(2)

        val key = maskKey(path, feather, pad, width, height, paint)
        if (key != NO_CACHE) {
            maskCache.remove(key)?.let { hit ->
                maskCache[key] = hit
                canvas.drawBitmap(hit.bitmap, hit.left, hit.top, compositePaint(paint))
                return
            }
        }

        maskRenderCount++
        // Fill at FULL alpha so the cached artefact is intensity-independent: the
        // caller's alpha is reapplied on the composite paint (see
        // [compositePaint]). Premultiplied blur + alpha-scaled composite is
        // mathematically identical to baking the alpha into the fill.
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, Color.red(paint.color), Color.green(paint.color), Color.blue(paint.color))
        }
        maskCanvas.translate(-bounds.left + pad, -bounds.top + pad)
        maskCanvas.drawPath(path, fill)

        val blurred = boxBlurPremultiplied(mask, feather)
        mask.recycle()

        if (key != NO_CACHE) {
            putCache(key, CachedMask(blurred, bounds.left - pad, bounds.top - pad, blurred.byteCount))
        }
        canvas.drawBitmap(blurred, bounds.left - pad, bounds.top - pad, compositePaint(paint))
        if (key == NO_CACHE) blurred.recycle()
    }

    private fun compositePaint(source: Paint): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = source.xfermode
        alpha = Color.alpha(source.color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = source.blendMode
        }
    }

    /*
     * Bounded LRU of finished (blurred) feathered masks, keyed by a 63-bit
     * content signature. A geometry-stable effect over any number of intensity
     * ticks hits the same entry and only recomposites.
     */
    private class CachedMask(val bitmap: Bitmap, val left: Float, val top: Float, val bytes: Int)

    private const val NO_CACHE = 0L

    private const val MAX_CACHE_BYTES = 4 * 1024 * 1024

    private val maskCache = LinkedHashMap<Long, CachedMask>(16, 0.75f, true)

    private var maskCacheBytes = 0L

    /** Feathered-mask renders since the last [clearMaskCache]; internal for tests. */
    internal var maskRenderCount: Int = 0
        private set

    private fun putCache(key: Long, entry: CachedMask) {
        maskCache[key] = entry
        maskCacheBytes += entry.bytes
        while (maskCacheBytes > MAX_CACHE_BYTES && maskCache.isNotEmpty()) {
            val eldest = maskCache.entries.iterator().next()
            maskCacheBytes -= eldest.value.bytes
            eldest.value.bitmap.recycle()
            maskCache.remove(eldest.key)
        }
    }

    /** Drops every cached mask (frees the bitmaps); internal for tests. */
    internal fun clearMaskCache() {
        for (entry in maskCache.values) entry.bitmap.recycle()
        maskCache.clear()
        maskCacheBytes = 0L
        maskRenderCount = 0
    }

    /**
     * Content signature of a feathered-mask render: the region's sampled path
     * geometry plus every other input that affects the finished artefact (the
     * feather/pad/size derivations are already functions of the bounds, then the
     * effect RGB colour and blend). The ALPHA is deliberately excluded — the
     * mask is rendered at full alpha and opacity is applied per-draw, so a
     * geometry-stable effect reuses the mask across all intensities.
     *
     * Sampling the path — rather than keying on Path identity — is what lets two
     * independently-built but geometrically identical paths (the norm across
     * drag ticks) share one cached mask. Falls back to [NO_CACHE] when path
     * sampling is unavailable so the renderer stays correct everywhere and
     * simply never reuses.
     */
    private fun maskKey(path: Path, feather: Int, pad: Int, width: Int, height: Int, paint: Paint): Long {
        val samples = runCatching { path.approximate(1f) }.getOrNull()
        if (samples == null || samples.isEmpty()) return NO_CACHE
        // FNV-1a 64 offset basis expressed as a signed Long
        // (14695981039346656037 wraps to -3750763034362895579).
        var hash = -3750763034362895579L
        for (value in samples) {
            hash = (hash xor value.toRawBits().toLong()) * 1099511628211L
        }
        hash = (hash xor feather.toLong()) * 1099511628211L
        hash = (hash xor pad.toLong()) * 1099511628211L
        hash = (hash xor width.toLong()) * 1099511628211L
        hash = (hash xor height.toLong()) * 1099511628211L
        hash = (hash xor (paint.color and 0x00FFFFFF).toLong()) * 1099511628211L
        hash = (hash xor (paint.xfermode?.hashCode()?.toLong() ?: 0L)) * 1099511628211L
        // Paint.getBlendMode() only exists from API 29; keep the key stable on
        // older runtimes (the composite path guards the same access).
        val blend = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode?.hashCode()?.toLong() ?: 0L
        } else {
            0L
        }
        hash = (hash xor blend) * 1099511628211L
        return if (hash == NO_CACHE) 1L else hash
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