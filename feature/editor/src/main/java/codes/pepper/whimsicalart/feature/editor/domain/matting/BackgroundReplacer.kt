package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Replaces the background of a photo with another image while keeping the
 * subject (foreground) intact, driven by a subject alpha [mask].
 *
 * Pure pixel compositing so it rasterizes deterministically on every backend
 * (including the Robolectric software canvas used by unit tests).
 */
object BackgroundReplacer {

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
        maskThreshold: Int = 110
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val bg = coverScale(background, source.width, source.height)
        val w = source.width
        val h = source.height

        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = Color.alpha(mask.getPixel(x, y))
                if (a < maskThreshold) {
                    result.setPixel(x, y, bg.getPixel(x, y))
                } else if (a < 255) {
                    val t = (a - maskThreshold) / (255f - maskThreshold)
                    result.setPixel(x, y, blend(source.getPixel(x, y), bg.getPixel(x, y), t))
                }
            }
        }
        if (bg !== background) bg.recycle()
        return result
    }

    /**
     * Scales [input] so it fully covers [w] x [h] ("cover"), cropping any
     * overflow equally from the sides so the subject is not distorted. Returns
     * a bitmap exactly [w] x [h]. The scaled bitmap is drawn with negative
     * top-left offset for a centred crop (the plain float `drawBitmap`
     * overload renders on every canvas, including Robolectric's software one).
     */
    internal fun coverScale(input: Bitmap, w: Int, h: Int): Bitmap {
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
        val r = (Color.red(fg) * t + Color.red(bg) * (1 - t)).roundToInt()
        val g = (Color.green(fg) * t + Color.green(bg) * (1 - t)).roundToInt()
        val b = (Color.blue(fg) * t + Color.blue(bg) * (1 - t)).roundToInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
