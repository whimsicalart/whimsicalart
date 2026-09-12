package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF

/**
 * Pure, JVM-testable helpers for turning the hair alpha mask produced by
 * [HairSegmenter] into a closed [Path] the hair-colour tool can draw. Keeping
 * this independent of any ML runtime means the mask-to-path logic can be
 * unit-tested on the JVM (Robolectric), which the native hair inference cannot.
 */
object HairMaskProcessor {

    /**
     * Builds a closed polygon [Path] around the mask's hair region - the set of
     * pixels whose alpha is `>= [threshold]`. Each source row contributes its
     * left-most and right-most hair pixel; the path runs down those left edges
     * and back up the right edges, giving a solid silhouette that hugs the hair
     * outline (including the crown) instead of a fixed dome.
     *
     * Returns `null` when fewer than [minRows] valid rows are found (e.g. no
     * hair detected, or a garbage mask), so the caller falls back to the dome.
     */
    fun toPath(
        mask: Bitmap,
        threshold: Int = HAIR_THRESHOLD,
        minRows: Int = 4,
        stride: Int = 1
    ): Path? {
        val width = mask.width
        val height = mask.height
        // One batched read of the mask: the scan below indexes `y*w+x` instead
        // of hitting `getPixel` once per pixel (up to 4M JNI calls at the ML
        // cap, once per geometry resolve — same rationale as SkinMaskProcessor).
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        val leftEdge = ArrayList<PointF>(height / stride)
        val rightEdge = ArrayList<PointF>(height / stride)

        for (y in 0 until height step stride) {
            var left = -1
            var right = -1
            val row = y * width
            for (x in 0 until width) {
                if (Color.alpha(pixels[row + x]) >= threshold) {
                    if (left < 0) left = x
                    right = x
                }
            }
            if (left >= 0 && right >= left) {
                leftEdge.add(PointF(left.toFloat(), y.toFloat()))
                rightEdge.add(PointF(right.toFloat(), y.toFloat()))
            }
        }

        if (leftEdge.size < minRows) return null

        val path = Path()
        path.moveTo(leftEdge[0].x, leftEdge[0].y)
        for (i in 1 until leftEdge.size) {
            path.lineTo(leftEdge[i].x, leftEdge[i].y)
        }
        for (i in rightEdge.indices.reversed()) {
            path.lineTo(rightEdge[i].x, rightEdge[i].y)
        }
        path.close()
        return path
    }

    private const val HAIR_THRESHOLD = 128
}