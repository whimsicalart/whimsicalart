package codes.pepper.whimsicalart.feature.collage.domain

import android.graphics.PointF
import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * Computes the source crop window for a cover-style fill that is "face aware":
 * instead of always centre-cropping, the window is shifted so a detected face
 * (given as a fractional centre in the source image, 0..1) sits near the centre
 * of the visible area. When no face is provided (or none is detected) it falls
 * back to a plain centre crop, preserving the existing look.
 *
 * Pure function over [Rect]/[PointF], so it is directly unit-testable under
 * Robolectric without any native runtime.
 */
object FaceAwareCropper {

    /**
     * @param sourceW source bitmap width in px
     * @param sourceH source bitmap height in px
     * @param targetAspect target cell width / height
     * @param faceCenter fractional face centre (x, y in 0..1) or null to centre-crop
     * @return the source crop rectangle to draw into the target
     */
    fun cropWindow(
        sourceW: Int,
        sourceH: Int,
        targetAspect: Float,
        faceCenter: PointF?
    ): Rect {
        if (sourceW <= 0 || sourceH <= 0) return Rect(0, 0, sourceW, sourceH)
        val aspect = targetAspect.coerceAtLeast(0.001f)

        if (sourceW.toFloat() / sourceH.toFloat() > aspect) {
            // source is wider than the target -> crop left/right.
            val newWidth = (sourceH * aspect).toInt().coerceAtLeast(1).coerceAtMost(sourceW)
            val left = driftCentre(sourceW, newWidth, faceCenter?.x)
            return Rect(left, 0, left + newWidth, sourceH)
        } else {
            // source is taller than the target -> crop top/bottom.
            val newHeight = (sourceW / aspect).toInt().coerceAtLeast(1).coerceAtMost(sourceH)
            val top = driftCentre(sourceH, newHeight, faceCenter?.y)
            return Rect(0, top, sourceW, top + newHeight)
        }
    }

    /**
     * Places a `window` of size [window] inside a [total] dimension so its centre is
     * aligned with `face`, then clamps it to stay fully inside [0, total]. When
     * `face` is null, centres the window.
     */
    private fun driftCentre(total: Int, window: Int, face: Float?): Int {
        val fallback = (total - window) / 2
        if (face == null) return fallback
        // Map the fractional face coordinate to a pixel centre, then back into a
        // window origin bounded by the window size.
        val facePx = face.coerceIn(0f, 1f) * total
        var origin = (facePx - window / 2f).roundToInt()
        val maxOrigin = (total - window).coerceAtLeast(0)
        origin = origin.coerceIn(0, maxOrigin)
        return origin
    }
}