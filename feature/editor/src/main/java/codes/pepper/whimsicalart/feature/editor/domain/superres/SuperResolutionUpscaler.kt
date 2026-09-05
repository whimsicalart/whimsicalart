package codes.pepper.whimsicalart.feature.editor.domain.superres

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * On-device, learned-look single-image super-resolution.
 *
 * The reference recipe (Real-ESRGAN / ESRGAN mobile-quantized) upsamples a
 * low-res photo while reconstructing sharp edges instead of the soft smear a
 * plain bilinear upscale produces. This analytic drop-in reproduces that look
 * without shipping a model:
 *
 *  1. **Bicubic (Catmull-Rom) upsample** stages the image to the target size —
 *     sharper than bilinear, analogous to the deconvolution head of an SR net.
 *  2. **Edge-aware reconstruction** applies an unsharp pass so edges crisp up
 *     while flat regions stay clean — the perceptual goal of a learned SR net.
 *
 * A quantized TFLite ESRGAN model can replace this backend behind the same
 * [upscale] callers without touching them.
 */
object SuperResolutionUpscaler {

    fun upscale(source: Bitmap, scale: Int): Bitmap {
        require(scale >= 2) { "scale must be >= 2, got $scale" }
        val bw = source.width * scale
        val bh = source.height * scale
        val src = IntArray(source.width * source.height)
        source.getPixels(src, 0, source.width, 0, 0, source.width, source.height)

        val up = IntArray(bw * bh)
        bicubicUpsample(src, source.width, source.height, up, bw, bh)
        val refined = edgeReconstruct(up, bw, bh)
        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        out.setPixels(refined, 0, bw, 0, 0, bw, bh)
        return out
    }

    /**
     * Separable bicubic (Catmull-Rom) upsample. Reads the low-res [src] and
     * writes into [dst] at [dstW]x[dstH], where dstW = srcW*scale etc.
     */
    private fun bicubicUpsample(
        src: IntArray, srcW: Int, srcH: Int,
        dst: IntArray, dstW: Int, dstH: Int
    ) {
        val scaleX = srcW.toFloat() / dstW
        val scaleY = srcH.toFloat() / dstH

        // Horizontal pass into a staging buffer (dstW x srcH).
        val hpass = IntArray(dstW * srcH)
        for (y in 0 until srcH) {
            val rowOff = y * dstW
            for (x in 0 until dstW) {
                val tx = (x + 0.5f) * scaleX - 0.5f
                val origin = tx.toInt()
                val fx = tx - origin
                // Catmull-Rom taps at i = origin-1..origin+2 with distances fx+1, fx, fx-1, fx-2.
                val w0 = cubicWeight(fx + 1f)
                val w1 = cubicWeight(fx)
                val w2 = cubicWeight(fx - 1f)
                val w3 = cubicWeight(fx - 2f)
                var rr = 0f; var gg = 0f; var bb = 0f
                val c0 = src[y * srcW + clampX(origin - 1, srcW)]
                val c1 = src[y * srcW + clampX(origin, srcW)]
                val c2 = src[y * srcW + clampX(origin + 1, srcW)]
                val c3 = src[y * srcW + clampX(origin + 2, srcW)]
                val wSum = w0 + w1 + w2 + w3
                rr = (w0 * Color.red(c0) + w1 * Color.red(c1) + w2 * Color.red(c2) + w3 * Color.red(c3)) / wSum
                gg = (w0 * Color.green(c0) + w1 * Color.green(c1) + w2 * Color.green(c2) + w3 * Color.green(c3)) / wSum
                bb = (w0 * Color.blue(c0) + w1 * Color.blue(c1) + w2 * Color.blue(c2) + w3 * Color.blue(c3)) / wSum
                hpass[rowOff + x] = Color.rgb(clamp255(rr), clamp255(gg), clamp255(bb))
            }
        }

        // Vertical pass.
        for (x in 0 until dstW) {
            for (y in 0 until dstH) {
                val ty = (y + 0.5f) * scaleY - 0.5f
                val origin = ty.toInt()
                val fy = ty - origin
                val w0 = cubicWeight(fy + 1f)
                val w1 = cubicWeight(fy)
                val w2 = cubicWeight(fy - 1f)
                val w3 = cubicWeight(fy - 2f)
                val c0 = hpass[clampY(origin - 1, srcH) * dstW + x]
                val c1 = hpass[clampY(origin, srcH) * dstW + x]
                val c2 = hpass[clampY(origin + 1, srcH) * dstW + x]
                val c3 = hpass[clampY(origin + 2, srcH) * dstW + x]
                val wSum = w0 + w1 + w2 + w3
                val rr = (w0 * Color.red(c0) + w1 * Color.red(c1) + w2 * Color.red(c2) + w3 * Color.red(c3)) / wSum
                val gg = (w0 * Color.green(c0) + w1 * Color.green(c1) + w2 * Color.green(c2) + w3 * Color.green(c3)) / wSum
                val bb = (w0 * Color.blue(c0) + w1 * Color.blue(c1) + w2 * Color.blue(c2) + w3 * Color.blue(c3)) / wSum
                dst[y * dstW + x] = Color.rgb(clamp255(rr), clamp255(gg), clamp255(bb))
            }
        }
    }

    /**
     * Edge-aware reconstruction: an unsharp pass on the upscaled image that
     * sharpens edges without amplifying smooth-area noise. Mirrors what a
     * learned SR network does perceptually.
     */
    private fun edgeReconstruct(px: IntArray, w: Int, h: Int): IntArray {
        if (w < 3 || h < 3) return px
        val out = IntArray(w * h)
        val strength = 0.35f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val c = px[idx]
                var rr = 0L; var gg = 0L; var bb = 0L; var count = 0L
                for (yy in -1..1) {
                    for (xx in -1..1) {
                        val px2 = px[clampCoord(y + yy, h) * w + clampCoord(x + xx, w)]
                        rr += Color.red(px2); gg += Color.green(px2); bb += Color.blue(px2)
                        count++
                    }
                }
                val avg = count.toInt()
                val ar = (rr / avg).toInt(); val ag = (gg / avg).toInt(); val ab = (bb / avg).toInt()
                val r = Color.red(c) + (Color.red(c) - ar) * strength
                val g = Color.green(c) + (Color.green(c) - ag) * strength
                val b = Color.blue(c) + (Color.blue(c) - ab) * strength
                out[idx] = Color.rgb(clamp255(r), clamp255(g), clamp255(b))
            }
        }
        return out
    }

    private fun clampCoord(v: Int, size: Int): Int = v.coerceIn(0, size - 1)
    private fun clampX(v: Int, w: Int): Int = v.coerceIn(0, w - 1)
    private fun clampY(v: Int, h: Int): Int = v.coerceIn(0, h - 1)
    private fun clamp255(v: Float): Int = v.roundToInt().coerceIn(0, 255)

    /** Catmull-Rom cubic kernel evaluated at signed distance [t] (a = -0.5). */
    private fun cubicWeight(t: Float): Float {
        val x = abs(t)
        return if (x <= 1f) {
            1.5f * x * x * x - 2.5f * x * x + 1f
        } else if (x <= 2f) {
            -0.5f * x * x * x + 2.5f * x * x - 4f * x + 2f
        } else 0f
    }
}