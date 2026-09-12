package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Replaces the background of a photo with another image while keeping the
 * subject (foreground) intact, driven by a subject alpha [mask].
 *
 * Pure pixel compositing so it rasterizes deterministically on every backend
 * (including the Robolectric software canvas used by unit tests).
 */
object BackgroundReplacer {

    /**
     * How many freshly computed [coverScale] covers to retain per background.
     * The cover-cover draw is re-done on every placement drag tick at an
     * unchanged (w, h) — rescaling the full background then cropping it each
     * time. Caching per (background, w, h) turns the hot path into pure
     * lookups, at the cost of holding a cover bitmap alive until the background
     * bitmap is collected ([coverCache] keys its covers weakly by the input).
     */
    private const val COVER_CACHE_CAPACITY = 4

    /** Cover size key for [coverCache]. */
    private data class CoverKey(val w: Int, val h: Int)

    /**
     * Memoized [coverScale] results, keyed by the source background's identity
     * (Bitmap does not override equals/hashCode) and the target size. The input
     * reference is held WEAKLY so a replaced background's covers are collectable.
     */
    private val coverCache = WeakHashMap<Bitmap, MutableMap<CoverKey, Bitmap>>()

    /**
     * Placement parameters of a replacement background are normalized against
     * the canvas it covers: [scale] 1.0 is the "fit size" (the placement covers
     * the canvas exactly), [panX]/[panY] are fractions of the canvas size added
     * to its centre, and [rotationDegrees] rotates the placement about its
     * centre. Enlarging the placement (with [freeScaleUp]) or rotating it grows
     * the committed canvas so nothing is ever cropped.
     */
    data class PlacementTransform(
        val scale: Float = 1f,
        val panX: Float = 0f,
        val panY: Float = 0f,
        val rotationDegrees: Float = 0f,
        val freeScaleUp: Boolean = false
    )

    /**
     * The largest scale a placement may reach: 1.0× (its fit size, the default)
     * unless the user unlocks enlargement. Scale-down is always allowed down to
     * [MIN_PLACEMENT_SCALE]; enlarge is capped at [MAX_PLACEMENT_SCALE].
     */
    private const val MAX_FIT_SCALE = 1f
    private const val MIN_PLACEMENT_SCALE = 0.2f
    private const val MAX_PLACEMENT_SCALE = 2f

    /** Coerces a user-supplied scale into the legal placement range. */
    fun clampPlacementScale(scale: Float, freeScaleUp: Boolean): Float =
        scale.coerceIn(
            MIN_PLACEMENT_SCALE,
            if (freeScaleUp) MAX_PLACEMENT_SCALE else MAX_FIT_SCALE
        )

    /**
     * Fills [background] to fully cover the source area (unified scale + centre
     * crop), then draws it only in the background region of [source] — pixels
     * where [mask] alpha is below [maskThreshold]. Boundary pixels (between
     * [maskThreshold] and 255) are alpha-blended for a soft, feathered edge.
     * Returns a new bitmap the same size as [source].
     */
    fun composite(
        source: Bitmap,
        mask: Bitmap,
        background: Bitmap,
        maskThreshold: Int = 110,
        softEdgeWidth: Int = 0
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val bg = coverScale(background, source.width, source.height)
        val w = source.width
        val h = source.height
        val maskW = mask.width.coerceAtLeast(1)
        val maskH = mask.height.coerceAtLeast(1)
        val edge = if (softEdgeWidth > 0) {
            SoftEdgeBand.depthMap(mask, maskThreshold, softEdgeWidth)
        } else {
            null
        }

        val sourcePx = IntArray(w * h)
        source.getPixels(sourcePx, 0, w, 0, 0, w, h)
        val bgPixels = IntArray(w * h)
        bg.getPixels(bgPixels, 0, w, 0, 0, w, h)
        val maskPx = IntArray(maskW * maskH)
        mask.getPixels(maskPx, 0, maskW, 0, 0, maskW, maskH)
        val resultPx = sourcePx.copyOf()

        for (y in 0 until h) {
            val rowBase = y * w
            val my = (y.toLong() * maskH / h).toInt().coerceIn(0, maskH - 1)
            for (x in 0 until w) {
                val idx = rowBase + x
                val mx = (x.toLong() * maskW / w).toInt().coerceIn(0, maskW - 1)
                val a = Color.alpha(maskPx[my * maskW + mx])
                if (a < maskThreshold) {
                    resultPx[idx] = bgPixels[idx]
                } else if (edge != null && x < maskW && y < maskH && edge[y * maskW + x] < softEdgeWidth) {
                    // Soft edge: the backdrop fades into the subject over the
                    // few-px band inward from the mask path.
                    val t = edge[y * maskW + x] / softEdgeWidth.toFloat()
                    resultPx[idx] = SoftEdgeBand.blend(sourcePx[idx], bgPixels[idx], t)
                } else if (a < 255) {
                    val t = (a - maskThreshold) / (255f - maskThreshold)
                    resultPx[idx] = blend(sourcePx[idx], bgPixels[idx], t)
                }
            }
        }
        result.setPixels(resultPx, 0, w, 0, 0, w, h)
        // bg is a shared coverScale cache entry — NOT recycled here (only the
        // freshly drawn source copy is ours to own).
        return result
    }

    /**
     * The output canvas needed to composite [PlacementTransform] over a
     * [w]×[h] canvas **without cropping**: the axis-aligned bounding box of the
     * (scaled, rotated) placement unioned with the source canvas. Pure integer
     * geometry — the same math [place] applies pixel-by-pixel, so the fold's
     * preview-resolution estimator and the renderer agree.
     */
    internal fun placedCanvasSize(
        w: Int,
        h: Int,
        scale: Float,
        rotationDegrees: Float
    ): Pair<Int, Int> {
        val scaleF = scale.coerceAtLeast(MIN_PLACEMENT_SCALE)
        if (scaleF == MAX_FIT_SCALE && abs(rotationDegrees) <= 1e-3f) return w to h
        val rad = rotationDegrees * PI / 180f
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val halfW = w * scaleF / 2f
        val halfH = h * scaleF / 2f
        var minX = 0f
        var maxX = w.toFloat()
        var minY = 0f
        var maxY = h.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val corners = arrayOf(
            -halfW to -halfH, halfW to -halfH, halfW to halfH, -halfW to halfH
        )
        for ((px, py) in corners) {
            val x = cx + px * cosA - py * sinA
            val y = cy + px * sinA + py * cosA
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
        // EPS absorbs float noise from cos/sin of exact axis-aligned rotations
        // (e.g. 180°/360°) that would otherwise add a phantom sub-pixel row of
        // growth; it is far below any real scale/rotation expansion.
        val eps = 1e-3f
        val outW = (ceil(maxX.toDouble() - eps) - floor(minX.toDouble() + eps)).toInt().coerceAtLeast(1)
        val outH = (ceil(maxY.toDouble() - eps) - floor(minY.toDouble() + eps)).toInt().coerceAtLeast(1)
        return outW to outH
    }

    /**
     * Composites the placement [transform] (pan / scale / rotate) over [source]:
     * the placement transform moves the **subject** — the cut-out region where
     * [mask] alpha is at or above [maskThreshold] — while the replacement
     * [background] stays a static cover-fill behind it. Scale-up / rotation grow
     * the committed canvas so the transformed subject is never cropped
     * ("Scaling never crops"); overflow beyond the original source area is
     * transparent where neither the subject nor the static cover reaches.
     */
    fun place(
        source: Bitmap,
        mask: Bitmap,
        background: Bitmap,
        transform: PlacementTransform,
        maskThreshold: Int = 110,
        softEdgeWidth: Int = 0
    ): Bitmap {
        val w = source.width
        val h = source.height
        val maskW = mask.width.coerceAtLeast(1)
        val maskH = mask.height.coerceAtLeast(1)
        val scaleF = clampPlacementScale(transform.scale, transform.freeScaleUp)
        val cover = coverScale(background, w, h)
        val edge = if (softEdgeWidth > 0) {
            SoftEdgeBand.depthMap(mask, maskThreshold, softEdgeWidth)
        } else {
            null
        }

        // The per-pixel loops below only sample these buffers: one batched
        // getPixels/setPixels pair per bitmap instead of a JNI-bound
        // getPixel/setPixel per pixel (measured ~4.7 s -> ~70 ms for a
        // 1056x1408 fold).
        val sourcePx = IntArray(w * h)
        source.getPixels(sourcePx, 0, w, 0, 0, w, h)
        val coverPx = IntArray(w * h)
        cover.getPixels(coverPx, 0, w, 0, 0, w, h)
        val maskPx = IntArray(maskW * maskH)
        mask.getPixels(maskPx, 0, maskW, 0, 0, maskW, maskH)

        // Fast path: the default placement (fit size, no pan / rotation) is
        // exactly the original cover behaviour — the subject keeps its pixels
        // and the background cover fills the rest — no projection needed.
        if (scaleF == MAX_FIT_SCALE &&
            abs(transform.rotationDegrees) <= 1e-3f &&
            abs(transform.panX) <= 1e-4f &&
            abs(transform.panY) <= 1e-4f
        ) {
            val result = source.copy(Bitmap.Config.ARGB_8888, true)
            val resultPx = sourcePx.copyOf()
            for (y in 0 until h) {
                val rowBase = y * w
                val my = (y.toLong() * maskH / h).toInt().coerceIn(0, maskH - 1)
                for (x in 0 until w) {
                    val idx = rowBase + x
                    val mx = (x.toLong() * maskW / w).toInt().coerceIn(0, maskW - 1)
                    val a = Color.alpha(maskPx[my * maskW + mx])
                    if (a < maskThreshold) {
                        resultPx[idx] = coverPx[idx]
                    } else if (edge != null && x < maskW && y < maskH && edge[y * maskW + x] < softEdgeWidth) {
                        val t = edge[y * maskW + x] / softEdgeWidth.toFloat()
                        resultPx[idx] = SoftEdgeBand.blend(sourcePx[idx], coverPx[idx], t)
                    } else if (a < 255) {
                        val t = (a - maskThreshold) / (255f - maskThreshold)
                        resultPx[idx] = blend(sourcePx[idx], coverPx[idx], t)
                    }
                }
            }
            result.setPixels(resultPx, 0, w, 0, 0, w, h)
            // cover is a shared coverScale cache entry — NOT recycled here.
            return result
        }

        val rad = transform.rotationDegrees * PI / 180f
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val invScale = 1f / scaleF
        val sCentreX = w / 2f
        val sCentreY = h / 2f
        // Destination centre of the transformed subject in source-local coords.
        val cx = w / 2f + transform.panX * w
        val cy = h / 2f + transform.panY * h

        val (outW, outH) = placedCanvasSize(w, h, scaleF, transform.rotationDegrees)
        val ox = (outW - w) / 2f
        val oy = (outH - h) / 2f

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val outPx = IntArray(outW * outH)
        for (y in 0 until outH) {
            val outBase = y * outW
            for (x in 0 until outW) {
                val dxo = x - ox - cx
                val dyo = y - oy - cy
                // Inverse of d = centre + R(theta)·(scale·(s - sCentre)):
                // s = sCentre + scale⁻¹·R(-theta)·(d - centre).
                val su = ((dxo * cosA + dyo * sinA) * invScale) + sCentreX
                val sv = ((-dxo * sinA + dyo * cosA) * invScale) + sCentreY
                val si = su.toInt()
                val sj = sv.toInt()
                // The transformed subject: where the projected source point lands
                // inside the source and the mask marks it as the subject.
                val subjectAlpha =
                    if (si in 0 until w && sj in 0 until h) {
                        val mmx = (si.toLong() * maskW / w).toInt().coerceIn(0, maskW - 1)
                        val mmy = (sj.toLong() * maskH / h).toInt().coerceIn(0, maskH - 1)
                        Color.alpha(maskPx[mmy * maskW + mmx])
                    } else {
                        0
                    }
                val outIdx = outBase + x
                if (subjectAlpha >= maskThreshold) {
                    val bgPx = backgroundPixel(coverPx, x - ox, y - oy, w, h)
                    val srcPx = sampleSubject(sourcePx, w, h, su, sv)
                    val alpha = subjectAlpha
                    val depth = if (edge != null) edge[sj * maskW + si] else softEdgeWidth
                    outPx[outIdx] =
                        if (depth < softEdgeWidth) {
                            SoftEdgeBand.blend(srcPx, bgPx, depth / softEdgeWidth.toFloat())
                        } else {
                            blendSubject(srcPx, bgPx, alpha, maskThreshold)
                        }
                    continue
                }
                // Static, untransformed background cover fills the source region.
                outPx[outIdx] = backgroundPixel(coverPx, x - ox, y - oy, w, h)
            }
        }
        out.setPixels(outPx, 0, outW, 0, 0, outW, outH)
        // cover is a shared coverScale cache entry — NOT recycled here.
        return out
    }

    /** The static background cover pixel at source-local [sx]/[sy], or transparent. */
    private fun backgroundPixel(cover: IntArray, sx: Float, sy: Float, w: Int, h: Int): Int {
        if (sx < 0f || sy < 0f || sx >= w || sy >= h) return Color.TRANSPARENT
        return cover[sy.toInt() * w + sx.toInt()]
    }

    /**
     * Bilinear sample of [sourcePx] for the transformed subject at [u]/[v].
     */
    private fun sampleSubject(sourcePx: IntArray, srcW: Int, srcH: Int, u: Float, v: Float): Int {
        val xi = floor(u)
        val yi = floor(v)
        val fx = (u - xi).toFloat()
        val fy = (v - yi).toFloat()
        val x0 = xi.toInt()
        val y0 = yi.toInt()
        val x1 = (x0 + 1).coerceAtMost(srcW - 1)
        val y1 = (y0 + 1).coerceAtMost(srcH - 1)
        val tl = sourcePx[y0 * srcW + x0]
        val tr = sourcePx[y0 * srcW + x1]
        val bl = sourcePx[y1 * srcW + x0]
        val br = sourcePx[y1 * srcW + x1]
        val a0 = Color.alpha(tl) * (1 - fx) + Color.alpha(tr) * fx
        val r0 = Color.red(tl) * (1 - fx) + Color.red(tr) * fx
        val g0 = Color.green(tl) * (1 - fx) + Color.green(tr) * fx
        val b0 = Color.blue(tl) * (1 - fx) + Color.blue(tr) * fx
        val a1 = Color.alpha(bl) * (1 - fx) + Color.alpha(br) * fx
        val r1 = Color.red(bl) * (1 - fx) + Color.red(br) * fx
        val g1 = Color.green(bl) * (1 - fx) + Color.green(br) * fx
        val b1 = Color.blue(bl) * (1 - fx) + Color.blue(br) * fx
        return Color.argb(
            (a0 * (1 - fy) + a1 * fy).roundToInt().coerceIn(0, 255),
            (r0 * (1 - fy) + r1 * fy).roundToInt().coerceIn(0, 255),
            (g0 * (1 - fy) + g1 * fy).roundToInt().coerceIn(0, 255),
            (b0 * (1 - fy) + b1 * fy).roundToInt().coerceIn(0, 255)
        )
    }

    /**
     * Subject-alpha blend with a possibly-transparent [bg]: fully-subject pixels
     * keep [src], fully-background pixels take [bg] (which may be
     * transparent when the placement doesn't reach), and boundary pixels are
     * alpha-blended (including the alpha channel, so transparent edges stay
     * transparent).
     */
    private fun blendSubject(src: Int, bg: Int, alpha: Int, maskThreshold: Int): Int =
        when {
            alpha >= 255 -> src
            alpha < maskThreshold -> bg
            else -> {
                val t = (alpha - maskThreshold) / (255f - maskThreshold)
                blend(src, bg, t)
            }
        }

    /**
     * Scales [input] so it fully covers [w] x [h] ("cover"), cropping any
     * overflow equally from the sides so the subject is not distorted. Returns
     * a bitmap exactly [w] x [h]. The scaled bitmap is drawn with negative
     * top-left offset for a centred crop (the plain float `drawBitmap`
     * overload renders on every canvas, including Robolectric's software one).
     *
     * The result is MEMOIZED per (input, w, h) and shared: it is never mutated
     * after publication, so callers must NOT recycle it. The cache is cleared
     * automatically when the input bitmap is collected ([coverCache] keys by the
     * input's identity weakly).
     */
    internal fun coverScale(input: Bitmap, w: Int, h: Int): Bitmap {
        val key = CoverKey(w, h)
        synchronized(coverCache) {
            coverCache[input]?.get(key)?.let { return it }
        }
        val computed = computeCover(input, w, h)
        synchronized(coverCache) {
            var byInput = coverCache[input]
            if (byInput == null || byInput.size >= COVER_CACHE_CAPACITY) {
                byInput = mutableMapOf()
                coverCache[input] = byInput
            }
            byInput[key] = computed
        }
        return computed
    }

    private fun computeCover(input: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / input.width, h.toFloat() / input.height)
        val sw = (input.width * scale).roundToInt().coerceAtLeast(1)
        val sh = (input.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(input, sw, sh, true)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dx = (w - sw) / 2f
        val dy = (h - sh) / 2f
        Canvas(out).drawBitmap(scaled, dx, dy, null)
        if (scaled !== input) scaled.recycle()
        return out
    }

    private fun blend(fg: Int, bg: Int, t: Float): Int {
        val a = (Color.alpha(fg) * t + Color.alpha(bg) * (1 - t)).roundToInt()
        val r = (Color.red(fg) * t + Color.red(bg) * (1 - t)).roundToInt()
        val g = (Color.green(fg) * t + Color.green(bg) * (1 - t)).roundToInt()
        val b = (Color.blue(fg) * t + Color.blue(bg) * (1 - t)).roundToInt()
        return Color.argb(
            a.coerceIn(0, 255), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)
        )
    }
}
