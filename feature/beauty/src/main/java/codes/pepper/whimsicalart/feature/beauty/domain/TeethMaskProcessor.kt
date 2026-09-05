package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.roundToInt

/**
 * Builds a per-pixel "teeth segmentation" alpha mask. Instead of blanching the
 * entire geometric mouth aperture (which also lightens gums/gloss), only pixels
 * that look tooth-like (bright) produce alpha, so whitening is confined to the
 * teeth. The gate is a luminance model with no native ML runtime, so it is fully
 * unit-testable under Robolectric; a learned teeth segmenter could later replace
 * it behind the same computing surface.
 */
object TeethMaskProcessor {

    const val LUMINANCE_THRESHOLD = 110f
    const val LUMINANCE_FLOOR = 200f

    /**
     * Computes a whitening mask for [source], restricted to the convex mouth
     * aperture [polygon] (the lens bounded by mouth corners and lip bands). Every
     * pixel inside the polygon gets alpha = clip((lum - threshold) * gain) so
     * bright tooth pixels are covered and dark gum/gloss are excluded; outside
     * the polygon alpha is 0. The mask RGB is white, so drawing it through a
     * LIGHTEN blend whitens by the alpha strength.
     *
     * @return a source-sized ARGB mask, or null when the polygon has no area.
     */
    fun toothMask(
        source: Bitmap,
        polygon: List<PointF>?,
        threshold: Float = LUMINANCE_THRESHOLD,
        floor: Float = LUMINANCE_FLOOR
    ): Bitmap? {
        if (polygon == null) return null
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return null

        val left = polygon.minOf { it.x }.toInt().coerceIn(0, w - 1)
        val top = polygon.minOf { it.y }.toInt().coerceIn(0, h - 1)
        val right = (polygon.maxOf { it.x }.toInt() + 1).coerceIn(left + 1, w)
        val bottom = (polygon.maxOf { it.y }.toInt() + 1).coerceIn(top + 1, h)

        val src = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        val mask = IntArray(w * h)
        val gain = 255f / (floor - threshold).coerceAtLeast(1f)

        for (y in top until bottom) {
            for (x in left until right) {
                if (!contains(polygon, x + 0.5f, y + 0.5f)) continue
                val srcIdx = y * w + x
                val lum = luminance(src[srcIdx])
                val t = (lum - threshold).coerceAtLeast(0f)
                val alpha = (t * gain).roundToInt().coerceIn(0, 255)
                if (alpha > 0) {
                    mask[srcIdx] = Color.argb(alpha, 255, 255, 255)
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(mask, 0, w, 0, 0, w, h)
        return out
    }

    /** Ray-casting point-in-polygon test for a convex (or simple) ring. */
    fun contains(polygon: List<PointF>, px: Float, py: Float): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val intersects = ((yi > py) != (yj > py)) &&
                (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    fun luminance(pixel: Int): Float {
        val r = (pixel ushr 16) and 0xff
        val g = (pixel ushr 8) and 0xff
        val b = pixel and 0xff
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}