package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PointF

/**
 * Pure-JVM adapter that turns a [SkinSegmenter] alpha [Bitmap] into a closed
 * [Path] silhouette consumable by [MaskedEffectRenderer][codes.pepper.whimsicalart.feature.beauty.domain.MaskedEffectRenderer].
 *
 * The sweep walks each sampled row, records the leftmost and rightmost pixel at
 * or above [threshold] (alpha encodes skin confidence), then returns a closed
 * polygon path: down the left edges, back up the reversed right edges. Face-skin
 * is a mostly contiguous region (the ML model already excises eyes, brows, lips
 * and mouth), so a single drop-down/drop-up boundary is a faithful outline.
 *
 * Returns `null` when fewer than [minRows] rows carry skin, so callers fall back
 * to the geometric face contour.
 */
object SkinMaskProcessor {

    fun toPath(
        mask: Bitmap,
        threshold: Int = SKIN_THRESHOLD,
        minRows: Int = 4,
        stride: Int = 1
    ): Path? {
        val width = mask.width
        val height = mask.height
        val leftEdge = ArrayList<PointF>(height / stride)
        val rightEdge = ArrayList<PointF>(height / stride)

        for (y in 0 until height step stride) {
            var left = -1
            var right = -1
            for (x in 0 until width) {
                if (android.graphics.Color.alpha(mask.getPixel(x, y)) >= threshold) {
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

    private const val SKIN_THRESHOLD = 128
}