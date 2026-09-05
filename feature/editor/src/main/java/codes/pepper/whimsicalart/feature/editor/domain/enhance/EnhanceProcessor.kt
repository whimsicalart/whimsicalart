package codes.pepper.whimsicalart.feature.editor.domain.enhance

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/**
 * One-tap "Auto-Enhance / HDR" tone map.
 *
 * Combines three learned-look stages into a single per-pixel pass, mirroring the
 * classic auto-enhance recipes (Meitu, AutoSharpen, iOS auto, …):
 *
 *  1. **Auto-levels** — stretches each colour channel to fill the histogram
 *     with a small clip at both tails, lifting flat, murky shadows and opening
 *     the highlights without a manual slider.
 *  2. **HDR tone map** — a monotonic midtone-contrast curve that lifts shadows
 *     and rolls off highlights (an HDR look) instead of clipping.
 *  3. **Edge-aware denoise** — a light box average weighted by local variance so
 *     smooth, noisy regions are flattened while real edges are preserved.
 *
 * Everything is an analytic pixel pass so it runs identically under Robolectric;
 * a quantized enhancement model (e.g. a TFLite tone-map / denoiser) can later
 * replace each stage behind the same interface without touching the render path.
 */
data class EnhanceSettings(
    /** Histogram clip percentile [0, 5] % at each tail for auto-levels. 0 = off. */
    val levelsClip: Float = 0.5f,
    /** Strength of the HDR midtone-contrast tonemap [0, 1]. */
    val hdrAmount: Float = 0.6f,
    /** Reserved for a local-tonemap radius; kept for signature stability. */
    val hdrRadius: Float = 0.02f,
    /** Denoise strength [0, 1]; 0 = off (keeps sharpness). */
    val denoiseAmount: Float = 0.3f
)

object EnhanceProcessor {

    fun enhance(
        source: Bitmap,
        settings: EnhanceSettings = EnhanceSettings()
    ): Bitmap {
        val w = source.width
        val h = source.height
        val inPixels = IntArray(w * h)
        source.getPixels(inPixels, 0, w, 0, 0, w, h)

        var rgb = inPixels
        if (settings.levelsClip > 0f) {
            rgb = autoLevels(rgb, w, h, settings.levelsClip)
        }
        if (settings.hdrAmount > 0f) {
            rgb = hdrTonemap(rgb, w, h, settings.hdrRadius, settings.hdrAmount)
        }
        if (settings.denoiseAmount > 0f) {
            rgb = edgeAwareDenoise(rgb, w, h, settings.denoiseAmount)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(rgb, 0, w, 0, 0, w, h)
        return out
    }

    /** Auto-levels: per-channel stretch with a clipped percentile at each tail. */
    fun autoLevels(pixels: IntArray, w: Int, h: Int, clip: Float): IntArray {
        val n = pixels.size
        if (n == 0 || clip <= 0f) return pixels

        val min = IntArray(3) { 255 }
        val max = IntArray(3) { 0 }
        val hist = Array(3) { IntArray(256) }
        for (p in pixels) {
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            hist[0][r]++; hist[1][g]++; hist[2][b]++
            if (r < min[0]) min[0] = r
            if (g < min[1]) min[1] = g
            if (b < min[2]) min[2] = b
            if (r > max[0]) max[0] = r
            if (g > max[1]) max[1] = g
            if (b > max[2]) max[2] = b
        }

        // Clip target: clip% of pixels at each tail (fractional, so a small clip
        // still trims on tiny images).
        val cut = n * clip / 100f
        val lo = IntArray(3)
        val hi = IntArray(3)
        for (c in 0 until 3) {
            // Lower bound: smallest value at or below which at least `cut` pixels lie.
            var cumLo = 0
            lo[c] = 0
            while (lo[c] < 256 && cumLo < cut) { cumLo += hist[c][lo[c]]; lo[c]++ }
            lo[c] = (lo[c] - 1).coerceIn(min[c], max[c])
            // Upper bound: largest value at or above which at least `cut` pixels lie.
            var cumHi = 0
            hi[c] = 255
            while (hi[c] >= 0 && cumHi < cut) { cumHi += hist[c][hi[c]]; hi[c]-- }
            hi[c] = (hi[c] + 1).coerceIn(min[c], max[c])
            // A truly compressed image (narrow spread) gets a full-range stretch
            // from its observed min/max instead of a no-op.
            if (hi[c] - lo[c] < 64) { lo[c] = min[c]; hi[c] = max[c] }
        }

        val out = IntArray(n)
        for (i in 0 until n) {
            val p = pixels[i]
            out[i] = Color.rgb(
                stretch(Color.red(p), lo[0], hi[0]),
                stretch(Color.green(p), lo[1], hi[1]),
                stretch(Color.blue(p), lo[2], hi[2])
            )
        }
        return out
    }

    /**
     * HDR tone map: a monotonic midtone-contrast curve applied to luma. Tones
     * below mid-grey are lifted (shadow detail is recovered) and tones above it
     * are compressed (highlights roll off instead of clipping), which is the
     * signature of an HDR look. Blended by [amount] so 0 = identity.
     */
    fun hdrTonemap(pixels: IntArray, w: Int, h: Int, radius: Float, amount: Float): IntArray {
        val n = pixels.size
        if (n == 0 || amount <= 0f) return pixels
        val a = amount.coerceIn(0f, 1f)
        val out = IntArray(n)
        for (i in 0 until n) {
            val p = pixels[i]
            val r = Color.red(p) / 255f
            val g = Color.green(p) / 255f
            val b = Color.blue(p) / 255f
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            // Reflect about mid-grey scaled by amount: shadows up, highlights down.
            val delta = (0.5f - luma) * a
            val r2 = (r + delta).coerceIn(0f, 1f)
            val g2 = (g + delta).coerceIn(0f, 1f)
            val b2 = (b + delta).coerceIn(0f, 1f)
            out[i] = Color.rgb(
                (r2 * 255f).roundToInt(),
                (g2 * 255f).roundToInt(),
                (b2 * 255f).roundToInt()
            )
        }
        return out
    }

    /** Edge-aware denoise: box average blended by local high-frequency energy. */
    fun edgeAwareDenoise(pixels: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val n = pixels.size
        if (n == 0 || amount <= 0f) return pixels
        val radius = 1
        val mean = IntArray(n)
        val variance = IntArray(n)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val avg = boxAverage(pixels, w, h, x, y, radius)
                mean[idx] = avg
                val base = pixels[idx]
                val dr = Color.red(base) - Color.red(avg)
                val dg = Color.green(base) - Color.green(avg)
                val db = Color.blue(base) - Color.blue(avg)
                variance[idx] = (dr * dr + dg * dg + db * db) / 3
            }
        }
        // Variance threshold in "12-bit squared error" units: smooth areas < 900 get blurred.
        val threshold = 900f
        val a = amount.coerceIn(0f, 1f)
        val out = IntArray(n)
        for (i in 0 until n) {
            val edge = variance[i] / threshold
            val blend = (a * (1f - edge)).coerceIn(0f, a)
            val m = mean[i]
            val p = pixels[i]
            out[i] = Color.rgb(
                lerp(Color.red(p), Color.red(m), blend),
                lerp(Color.green(p), Color.green(m), blend),
                lerp(Color.blue(p), Color.blue(m), blend)
            )
        }
        return out
    }

    private fun stretch(v: Int, lo: Int, hi: Int): Int {
        if (hi <= lo) return v
        val scaled = (v - lo) * 255f / (hi - lo)
        return scaled.roundToInt().coerceIn(0, 255)
    }

    private fun lerp(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).roundToInt().coerceIn(0, 255)

    private fun boxAverage(pixels: IntArray, w: Int, h: Int, cx: Int, cy: Int, r: Int): Int {
        val x0 = (cx - r).coerceAtLeast(0)
        val y0 = (cy - r).coerceAtLeast(0)
        val x1 = (cx + r).coerceAtMost(w - 1)
        val y1 = (cy + r).coerceAtMost(h - 1)
        var rr = 0L; var gg = 0L; var bb = 0L; var count = 0L
        for (y in y0..y1) {
            var off = y * w + x0
            for (x in x0..x1) {
                val c = pixels[off]
                rr += Color.red(c); gg += Color.green(c); bb += Color.blue(c)
                count++
                off++
            }
        }
        if (count == 0L) return pixels[cy * w + cx]
        return Color.rgb((rr / count).toInt(), (gg / count).toInt(), (bb / count).toInt())
    }
}