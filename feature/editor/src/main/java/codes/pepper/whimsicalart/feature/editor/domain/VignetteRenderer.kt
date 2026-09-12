package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/**
 * Committed-pass renderer for the radial-vignette tone pass (and the shared
 * box-average sampling it has in common with the unsharp mask), extracted from
 * [BitmapRenderer] so each tone/drawing family owns its raster logic. Draw math
 * is unchanged from `BitmapRenderer.applyVignette`/`applyVignetteCopy`.
 */
internal object VignetteRenderer {

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

    /** Functional vignette pass: returns an altered copy of [current]. */
    internal fun applyVignetteCopy(current: Bitmap, strength: Float): Bitmap {
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        applyVignette(out, strength)
        return out
    }

    /**
     * Box-averages the neighbourhood around (cx, cy) skipping fully transparent
     * pixels, shared with the unsharp-mask pass in [BitmapRenderer.applySharpen].
     */
    internal fun boxAverageAt(src: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int): Int {
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
}