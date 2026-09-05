package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Skin-denoising processor based on multi-scale A-Trous wavelet shrinkage (the
 * classic technique popularised by dcraw / UFRaw).
 *
 * Independent, simplified implementation:
 *  - Always runs over all three RGB channels (no per-channel selection).
 *  - The threshold is derived automatically from the image dimensions (scales
 *    with the diagonal, roughly the caller's "~1% of the diagonal" idea,
 *    mapped into the wavelet-coefficient working range). Not a user control.
 *  - A single [softness] slider (0..1) controls how much in-threshold detail
 *    is preserved: higher softness keeps more natural texture (and noise),
 *    lower softness denoises more aggressively.
 *  - Work is bounded to [WORK_MAX_DIMENSION] so a 2048px preview does not
 *    allocate ~88MB of working arrays on every slider tick (the wavelet step
 *    needs the full pixel buffer plus six float arrays); larger inputs are
 *    downscaled for the wavelet pass and the result scaled back up.
 *  - The alpha channel is preserved unchanged: transparency (a cut-out or
 *    transparent background) survives the wavelet pass instead of being
 *    flattened to opaque black.
 *
 * Returns a new [Bitmap]; the input is left untouched.
 */
object SkinDenoiseProcessor {

    private const val LEVELS = 5
    private const val WORK_MAX_DIMENSION = 1024

    /**
     * Derives a denoise threshold from the image size, roughly 0.1% of the
     * diagonal: threshold = 0.001 * diagonal. Larger images denoise more
     * aggressively. Floored so tiny images still get a meaningful denoise.
     */
    fun estimateThreshold(width: Int, height: Int): Float {
        val diagonal = sqrt(width.toDouble() * width + height.toDouble() * height)
        return (0.001 * diagonal).toFloat().coerceAtLeast(0.1f)
    }

    fun denoise(bitmap: Bitmap, softness: Float): Bitmap {
        val low = softness.coerceIn(0f, 1f)
        val w = bitmap.width
        val h = bitmap.height

        if (max(w, h) > WORK_MAX_DIMENSION) {
            // Bound the working arrays (see class doc): scale to a smaller
            // canvas for the wavelet pass, then scale the result back up.
            val scale = WORK_MAX_DIMENSION.toFloat() / max(w, h)
            val workWidth = max(1, (w * scale).roundToInt())
            val workHeight = max(1, (h * scale).roundToInt())
            val small = Bitmap.createScaledBitmap(bitmap, workWidth, workHeight, true)
            val smallResult = denoiseBounded(small, low)
            val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            Canvas(out).drawBitmap(smallResult, null, Rect(0, 0, w, h), paint)
            small.recycle()
            smallResult.recycle()
            return out
        }
        return denoiseBounded(bitmap, low)
    }

    private fun denoiseBounded(bitmap: Bitmap, low: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val size = w * h
        val threshold = estimateThreshold(w, h)
        val work = WaveletWork(size)

        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val r = FloatArray(size)
        val g = FloatArray(size)
        val b = FloatArray(size)
        for (i in 0 until size) {
            val p = pixels[i]
            r[i] = (p shr 16 and 0xFF) / 255f
            g[i] = (p shr 8 and 0xFF) / 255f
            b[i] = (p and 0xFF) / 255f
        }

        waveletDenoise(r, w, h, threshold, low, work)
        waveletDenoise(g, w, h, threshold, low, work)
        waveletDenoise(b, w, h, threshold, low, work)

        for (i in 0 until size) {
            val pr = (r[i] * 255f).roundClamp()
            val pg = (g[i] * 255f).roundClamp()
            val pb = (b[i] * 255f).roundClamp()
            // Preserve the source alpha: only the RGB channels are wavelet-shrunk,
            // so a transparent background stays transparent instead of turning black.
            val a = pixels[i] ushr 24 and 0xFF
            pixels[i] = (a shl 24) or (pr shl 16) or (pg shl 8) or pb
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    /**
     * Reusable working arrays for the wavelet pass (allocated once, shared
     * across the three channels) instead of per-channel allocations.
     */
    private class WaveletWork(size: Int) {
        val temp = FloatArray(max(size, 1))
        val lowA = FloatArray(size)
        val lowB = FloatArray(size)
        val out = FloatArray(size)
    }

    /**
     * Multi-scale A-Trous wavelet shrinkage on a single channel.
     *
     * [channel] starts as the original image and becomes the output. At each
     * level a separable low-pass ("hat") filter isolates the smooth band; the
     * difference is the detail band, whose noise level is estimated per
     * intensity. The detail is soft-shrunk and accumulated back into [channel].
     */
    private fun waveletDenoise(
        channel: FloatArray,
        width: Int,
        height: Int,
        threshold: Float,
        low: Float,
        work: WaveletWork
    ) {
        val size = width * height
        val temp = work.temp
        val out = work.out
        out.fill(0f)

        var currentLow = work.lowA
        var nextLow = work.lowB
        var readsFromChannel = true
        var prevLow: FloatArray? = null

        for (lev in 0 until LEVELS) {
            val src = if (readsFromChannel) channel else currentLow
            val lp = nextLow
            readsFromChannel = false

            // Separable low-pass: horizontal then vertical.
            for (row in 0 until height) {
                hatTransform(temp, src, row * width, 1, width, 1 shl lev)
                for (col in 0 until width) {
                    lp[row * width + col] = temp[col] * 0.25f
                }
            }
            for (col in 0 until width) {
                hatTransform(temp, lp, col, width, height, 1 shl lev)
                for (row in 0 until height) {
                    lp[row * width + col] = temp[row] * 0.25f
                }
            }

            // Level-dependent window for deciding which detail is "noise".
            val levelGain = (5.0 / 64.0) * exp(-2.6 * sqrt((lev + 1).toDouble())) *
                (0.8002 / exp(-2.6))

            // Estimate noise stdev per intensity bin from small detail.
            val stdev = DoubleArray(5)
            val samples = IntArray(5)
            for (i in 0 until size) {
                val detail = src[i] - lp[i]
                if (detail > -levelGain && detail < levelGain) {
                    val bin = intensityBin(lp[i])
                    stdev[bin] += detail * detail
                    samples[bin]++
                }
            }
            for (bin in 0..4) {
                stdev[bin] = sqrt(stdev[bin] / (samples[bin] + 1))
            }

            // Soft-shrink the detail and accumulate into channel. The final
            // image is the sum of all shrunk detail bands plus the final
            // low-pass; with softness = 1 the unshrunk detail telescopes back
            // to the original exactly.
            for (i in 0 until size) {
                val d = src[i] - lp[i]
                out[i] += shrink(d, threshold * stdev[intensityBin(lp[i])].toFloat(), low)
            }

            prevLow = lp
            val swap = currentLow
            currentLow = nextLow
            nextLow = swap
        }

        // Add the final low-pass back to reconstruct the full image.
        val finalLow = prevLow ?: return
        for (i in 0 until size) {
            out[i] += finalLow[i]
        }
        out.copyInto(channel)
    }

    private fun shrink(detail: Float, thold: Float, low: Float): Float = when {
        detail < -thold -> detail + thold - thold * low
        detail > thold -> detail - thold + thold * low
        else -> detail * low
    }

    private fun intensityBin(lowPass: Float): Int = when {
        lowPass > 0.8f -> 4
        lowPass > 0.6f -> 3
        lowPass > 0.4f -> 2
        lowPass > 0.2f -> 1
        else -> 0
    }

    /**
     * 1D low-pass "hat" transform with symmetric (mirrored) edge handling from
     * the classic 3-tap smoothed filter: out[i] = 2*in[i] + in[i-sc] + in[i+sc].
     * Uses a single mirrored index so very small images (short side < 2*sc) can
     * never index out of bounds.
     */
    private fun hatTransform(
        temp: FloatArray,
        base: FloatArray,
        offset: Int,
        stride: Int,
        size: Int,
        sc: Int
    ) {
        var i = 0
        while (i < size) {
            temp[i] = 2f * base[stride * i + offset] +
                base[stride * mirrorIndex(i - sc, size) + offset] +
                base[stride * mirrorIndex(i + sc, size) + offset]
            i++
        }
    }

    private fun mirrorIndex(i: Int, size: Int): Int {
        val mirrored = when {
            i < 0 -> -i
            i >= size -> 2 * size - 2 - i
            else -> i
        }
        // Guard against pathological small sizes where the mirrored index can
        // still leave the row; clamp is safe and only affects sub-2*sc dims.
        return mirrored.coerceIn(0, size - 1)
    }

    private fun Float.roundClamp(): Int = (this + 0.5f).toInt().coerceIn(0, 255)
}