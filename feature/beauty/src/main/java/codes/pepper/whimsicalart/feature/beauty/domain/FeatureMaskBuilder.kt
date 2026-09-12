package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Builds landmark-anchored closed-path MASKS for the beauty features, replacing
 * the free geometric primitives (a circle drawn at a landmark point, a rect
 * between mouth corners, face oval) with shapes that follow the actual feature
 * outline.
 *
 * Reference material kept generic (adopted "mask" concept from the analysed
 * beauty pipeline: per-feature region anchored to named face points, feathered
 * boundary). All shapes here are computed from the detected landmark/contour
 * geometry of the CURRENT face - no reference assets or code are used.
 *
 * Every function falls back to the previous primitive shape when the contour
 * is missing (ML Kit may not return contours on very small/dark faces), so a
 * missing contour degrades gracefully instead of failing.
 */
object FeatureMaskBuilder {

    /** Closed [Path] for a contour point list; null when fewer than 3 points. */
    fun closedPath(points: List<PointF>): Path? {
        if (points.size < 3) return null
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        path.close()
        return path
    }

    fun ovalPath(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float): Path {
        val path = Path()
        path.addOval(
            RectF(
                centerX - radiusX,
                centerY - radiusY,
                centerX + radiusX,
                centerY + radiusY
            ),
            Path.Direction.CW
        )
        return path
    }

    /**
     * Almond-shaped eye path from a centre and radial width/height: the two
     * corners meet on the eye's major axis, the upper lid is rounder and the
     * lower lid tighter. Used as the eye-mask fallback so a missing contour
     * still yields an eye-shaped PATH, not a bare ellipse (eyeshadow and the
     * eyeliner stroke both rely on it).
     */
    fun almondPath(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float): Path {
        val left = centerX - radiusX
        val right = centerX + radiusX
        val top = centerY - radiusY
        val bottom = centerY + radiusY
        val dx = radiusX * 0.45f
        val path = Path()
        path.moveTo(left, centerY)
        path.cubicTo(left + dx, top, right - dx, top, right, centerY)
        path.cubicTo(right - dx, bottom, left + dx, bottom, left, centerY)
        path.close()
        return path
    }

    /**
     * Eye mask: the eye contour polygon when available, otherwise an almond
     * path centred on the eye landmark.
     */
    fun eyeMask(contour: List<PointF>, center: PointF?, fallbackRadius: Float): Path? {
        closedPath(contour)?.let { return it }
        if (center == null) return null
        val radius = fallbackRadius.coerceAtLeast(2f)
        return almondPath(center.x, center.y, radius, radius * 0.6f)
    }

    /**
     * Lip mask (outer mouth outline) for lipstick: the lips contour when
     * available, otherwise a mouth-width path with a subtle cupid's-bow top
     * edge and a fuller bottom lip built from the mouth corners.
     *
     * When the mouth is OPEN ([isMouthOpen]) the mouth aperture (the inner-lip
     * ring reused from the teeth-whitening work) is punched OUT of the fill as
     * a counter-winding-agnostic even-odd hole, so lipstick covers the lips but
     * never the teeth/mouth interior. A closed or pressed-together mouth has a
     * near-degenerate aperture and keeps the full fill. [isMouthOpen] ratio
     * [MOUTH_OPEN_RATIO].
     */
    fun lipMask(
        lipsContour: List<PointF>,
        mouthLeft: PointF?,
        mouthRight: PointF?,
        mouthApertureContour: List<PointF> = emptyList()
    ): Path? {
        val outer = closedPath(lipsContour) ?: bowLipPath(mouthLeft, mouthRight) ?: return null
        if (isMouthOpen(mouthApertureContour)) {
            closedPath(mouthApertureContour)?.let { aperture ->
                outer.fillType = Path.FillType.EVEN_ODD
                outer.addPath(aperture)
            }
        }
        return outer
    }

    private fun bowLipPath(mouthLeft: PointF?, mouthRight: PointF?): Path? {
        if (mouthLeft == null || mouthRight == null) return null
        val cx = (mouthLeft.x + mouthRight.x) / 2f
        val cy = (mouthLeft.y + mouthRight.y) / 2f
        val width = abs(mouthRight.x - mouthLeft.x)
        val rise = width * 0.16f
        val drop = width * 0.26f
        val path = Path()
        path.moveTo(mouthLeft.x, cy)
        path.quadTo(cx - width * 0.24f, cy - rise, cx, cy)
        path.quadTo(cx + width * 0.24f, cy - rise, mouthRight.x, cy)
        path.quadTo(cx, cy + drop, mouthLeft.x, cy)
        path.close()
        return path
    }

    /** The aperture is a real opening (the mouth is open) at a vertical extent
     * of at least [MOUTH_OPEN_RATIO] of its width; a pressed-together mouth
     * leaves a flat, degenerate lens that must not punch a hole in the fill. */
    fun isMouthOpen(mouthApertureContour: List<PointF>): Boolean {
        if (mouthApertureContour.size < 3) return false
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in mouthApertureContour) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val width = maxX - minX
        val height = maxY - minY
        if (width <= 0f || height <= 0f) return false
        // Epsilon absorbs float rounding of `width * MOUTH_OPEN_RATIO` (40f *
        // 0.1f is 4.00000006, which would deny an exactly-10% opening).
        return height + 1e-4f >= width * MOUTH_OPEN_RATIO
    }

    private const val MOUTH_OPEN_RATIO = 0.1f

    /**
     * Mouth aperture mask for teeth whitening: the lens-shaped opening between
     * the lips, built as quadratic arcs whose corners land exactly on the mouth
     * corners and whose vertical extent stays inside the lips bands. Mirrors the
     * reference's "teeth LUT applied inside the mouth region" instead of a free
     * ellipse drawn at the mouth centre.
     */
    fun mouthAperturePath(
        mouthLeft: PointF,
        mouthRight: PointF,
        bandTop: Float,
        bandBottom: Float
    ): Path {
        val cx = (mouthLeft.x + mouthRight.x) / 2f
        val cy = (mouthLeft.y + mouthRight.y) / 2f
        val path = Path()
        path.moveTo(mouthLeft.x, cy)
        path.quadTo(cx, bandTop, mouthRight.x, cy)
        path.quadTo(cx, bandBottom, mouthLeft.x, cy)
        path.close()
        return path
    }

    /**
     * Teeth-whitening mask polygon: the mouth aperture where the teeth show.
     *
     * Prefers the MediaPipe Face Mesh **inner-lip ring** (`mouthApertureContour`
     * — the actual mouth opening), resampled via evenly-spaced subdivision along
     * the ring's perimeter to a node count proportional to the aperture's pixel
     * footprint (`clamp(apertureWidthPx / 8, 24, 96)` — dense for wide smiles,
     * cheap for small mouths), so whitening hugs the teeth instead of the outer
     * lips. Falls back to the convex aperture polygon [mouthLeft, top,
     * mouthRight, bottom] (the analytic lens between the mouth corners and the
     * lip bands) when no inner-lip contour is available.
     */
    fun teethPolygon(
        lipsContour: List<PointF>,
        mouthLeft: PointF?,
        mouthRight: PointF?,
        mouthApertureContour: List<PointF> = emptyList()
    ): List<PointF>? {
        if (mouthApertureContour.size >= 3) {
            val margin = 1f
            val width = mouthApertureContour.maxOf { it.x } -
                mouthApertureContour.minOf { it.x } + margin
            val target = (width / 8f).roundToInt().coerceIn(24, 96)
            return resampleRing(mouthApertureContour, target)
        }
        return lensPolygon(lipsContour, mouthLeft, mouthRight)
    }

    /**
     * Legacy analytic lens: convex aperture polygon [mouthLeft, top, mouthRight,
     * bottom] between the mouth corners and the lips bands. [teethPolygon] uses
     * this only as the no-inner-contour fallback.
     */
    private fun lensPolygon(
        lipsContour: List<PointF>,
        mouthLeft: PointF?,
        mouthRight: PointF?
    ): List<PointF>? {
        var left = mouthLeft?.x
        var right = mouthRight?.x
        var top: Float?
        var bottom: Float?
        if (lipsContour.isNotEmpty()) {
            val bounds = RectF()
            closedPath(lipsContour)?.computeBounds(bounds, false)
            if (bounds.width() > 0f && bounds.height() > 0f) {
                if (left == null || right == null) {
                    left = bounds.left
                    right = bounds.right
                }
                top = bounds.top + bounds.height() * 0.3f
                bottom = bounds.bottom - bounds.height() * 0.2f
            } else {
                top = null
                bottom = null
            }
        } else {
            top = null
            bottom = null
        }

        if (mouthLeft == null || mouthRight == null || left == null || right == null) return null
        val midY = (mouthLeft.y + mouthRight.y) / 2f
        val bandTop = top ?: (midY - 0.08f * (right - left))
        val bandBottom = bottom ?: (midY + 0.12f * (right - left))
        val cx = (mouthLeft.x + mouthRight.x) / 2f
        return listOf(
            PointF(mouthLeft.x, midY),
            PointF(cx, bandTop),
            PointF(mouthRight.x, midY),
            PointF(cx, bandBottom)
        )
    }

    /**
     * Resamples a closed point ring to exactly [target] points, evenly spaced
     * along the ring's perimeter (midpoint subdivision / decimation by arc
     * length). This is the teeth-aperture density rule: the ring is denser
     * when it bounds a wider mouth. Returns the input unchanged when it has
     * fewer than 3 points, [target] is not positive, or the ring has zero
     * perimeter length.
     */
    internal fun resampleRing(points: List<PointF>, target: Int): List<PointF> {
        if (target <= 0 || points.size < 3) return points
        val n = points.size
        // Prefix arc length: arc[i] is the perimeter distance at vertex i.
        val arc = FloatArray(n + 1)
        for (i in 0 until n) {
            val next = (i + 1) % n
            val len = hypot(points[next].x - points[i].x, points[next].y - points[i].y)
            arc[i + 1] = arc[i] + len
        }
        val total = arc[n]
        if (total <= 0f) return points
        val out = ArrayList<PointF>(target)
        var seg = 0
        for (k in 0 until target) {
            val targetArc = total * k / target
            while (seg < n && arc[seg + 1] < targetArc) seg++
            val a0 = arc[seg]
            val a1 = arc[seg + 1]
            val f = if (a1 > a0) (targetArc - a0) / (a1 - a0) else 0f
            val p0 = points[seg]
            val p1 = points[(seg + 1) % n]
            out.add(PointF(p0.x + (p1.x - p0.x) * f, p0.y + (p1.y - p0.y) * f))
        }
        return out
    }

    /**
     * Teeth-whitening mask path (the mouth aperture between the lips) used by the
     * legacy feathered renderer. New callers should prefer [teethPolygon] +
     * `TeethMaskProcessor.toothMask` so the per-pixel teeth-segmentation alpha
     * mask can test containment analytically; this path keeps the convex lens
     * shape even when an inner-lip ring is present.
     */
    fun teethMask(
        lipsContour: List<PointF>,
        mouthLeft: PointF?,
        mouthRight: PointF?
    ): Path? {
        val polygon = lensPolygon(lipsContour, mouthLeft, mouthRight) ?: return null
        return mouthAperturePath(
            polygon[0], polygon[2],
            polygon[1].y, polygon[3].y
        )
    }

    /**
     * Under-eye "silkworm" region (dark-circle removal): a soft lens hugging
     * below the eye, its near-flat top edge grazing under the lash line and a
     * rounded lower arc dipping toward the cheek. Replaces the raw circle drawn
     * at the eye landmark with a region that follows the eye's under-socket
     * shape. [height] is the downward reach below the eye anchor.
     */
    fun silkwormPath(cx: Float, cy: Float, width: Float, height: Float): Path {
        val halfW = (width / 2f).coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        val left = cx - halfW
        val right = cx + halfW
        val top = cy + h * 0.18f
        val bottom = cy + h
        val side = top + (bottom - top) * 0.2f
        val path = Path()
        path.moveTo(left, side)
        path.cubicTo(left, top, right, top, right, side)
        path.quadTo(cx, bottom, left, side)
        path.close()
        return path
    }

    /**
     * Horizontal face band for the wrinkle/removal tools: a trapezoid whose
     * top and bottom edges sit on the face contour at the given [top]/[bottom]
     * heights, so the region follows the face silhouette instead of being a
     * hard rectangle (which would spill past the cheeks). Falls back to a
     * rounded rect of [fallback] when the contour is missing.
     */
    fun verticalBand(
        faceContour: List<PointF>,
        top: Float,
        bottom: Float,
        fallback: RectF
    ): Path {
        val path = Path()
        // One effect, two scales: the fallback band's corner roundness is a
        // fraction of its short edge, so a preview and the full-size fold round
        // the same relative geometry.
        val corner = (minOf(fallback.width(), fallback.height()) * 0.04f).coerceAtLeast(1f)
        if (faceContour.isEmpty()) {
            path.addRoundRect(fallback, corner, corner, Path.Direction.CW)
            return path
        }
        val cx = fallback.centerX()
        val leftSide = faceContour.filter { it.x < cx && it.y in top..bottom }
        val rightSide = faceContour.filter { it.x >= cx && it.y in top..bottom }
        val leftTop = leftSide.minByOrNull { it.y }
        val leftBottom = leftSide.maxByOrNull { it.y }
        val rightTop = rightSide.minByOrNull { it.y }
        val rightBottom = rightSide.maxByOrNull { it.y }
        if (leftTop == null || leftBottom == null || rightTop == null || rightBottom == null) {
            path.addRoundRect(fallback, corner, corner, Path.Direction.CW)
            return path
        }
        path.moveTo(leftTop.x, leftTop.y)
        path.lineTo(rightTop.x, rightTop.y)
        path.lineTo(rightBottom.x, rightBottom.y)
        path.lineTo(leftBottom.x, leftBottom.y)
        path.close()
        return path
    }

    /**
     * Cheek (blush) mask: a leaf hugging the side of the face. The outer edge
     * is the face contour sampled around the cheek's height; the inner edge is
     * a concave quadratic back through a point inside the cheek, so the blush
     * tapers to points on the face edge (matching the reference's cheek-anchored
     * template mask) instead of a symmetric ellipse.
     */
    fun cheekLeafPath(
        faceContour: List<PointF>,
        cheek: PointF,
        isLeft: Boolean,
        verticalSpan: Float,
        fallbackRadius: Float
    ): Path? {
        val side = faceContour
            .asSequence()
            .filter { (it.x < cheek.x) == isLeft && abs(it.y - cheek.y) <= verticalSpan }
            .toList()
        if (side.size < 4) return null
        val sorted = side.sortedBy { it.y }
        val top = sorted.first()
        val bottom = sorted.last()
        val innerX = if (isLeft) cheek.x - fallbackRadius * 0.5f else cheek.x + fallbackRadius * 0.5f
        val path = Path()
        path.moveTo(top.x, top.y)
        sorted.forEach { path.lineTo(it.x, it.y) }
        path.quadTo(innerX, cheek.y, top.x, top.y)
        path.close()
        return path
    }

    /**
     * Distance from a cheek anchor to the face edge at the cheek's height,
     * sampled from the face contour on the correct side. Falls back to the
     * previous bounds-based radius when the face contour is missing.
     */
    fun cheekEdgeRadius(
        faceContour: List<PointF>,
        cheek: PointF,
        isLeft: Boolean,
        boundsHeight: Float,
        fallbackRadius: Float
    ): Float {
        if (faceContour.isEmpty()) return fallbackRadius
        val tolerance = (boundsHeight * 0.08f).coerceAtLeast(6f)
        val best = faceContour
            .asSequence()
            .filter { (it.x < cheek.x) == isLeft && abs(it.y - cheek.y) <= tolerance }
            .maxOfOrNull { hypot(it.x - cheek.x, it.y - cheek.y) }
        return max((best ?: 0f), fallbackRadius * 0.6f)
    }

    /** Face region mask (skin smoothing / foundation): face contour or oval fallback. */
    fun faceMask(faceContour: List<PointF>, fallbackOval: RectF): Path? {
        closedPath(faceContour)?.let { return it }
        val path = Path()
        path.addOval(fallbackOval, Path.Direction.CW)
        return path
    }

    /**
     * Hair-colour region: a dome anchored to the face bounds (width, centre and
     * top edge from the detected face, crown ~45% of the face height above the
     * brow line, bottom edge inside the face top). There is no hair landmark
     * set, so the face rect - which already tracks the face contour - anchors
     * the region instead of a free-floating oval at a guessed offset.
     */
    fun hairMask(bounds: Rect): Path {
        val height = bounds.height().toFloat()
        val left = bounds.left.toFloat()
        val right = bounds.right.toFloat()
        val top = bounds.top.toFloat() - height * 0.45f
        val bottom = bounds.top.toFloat() + height * 0.08f
        val midX = (left + right) / 2f
        val path = Path()
        path.moveTo(right, bottom)
        path.quadTo(right, top, midX, top)
        path.quadTo(left, top, left, bottom)
        path.close()
        return path
    }

    /**
     * Cheek (blush) mask: a face-contour leaf anchored on the cheek landmark
     * whose outer edge follows the side of the face (so the blush hugs the face
     * silhouette instead of being a fixed-radius ellipse). Falls back to the old
     * bounds/contour-sized ellipse when the contour is missing.
     */
    fun cheekMask(
        faceContour: List<PointF>,
        cheek: PointF?,
        isLeft: Boolean,
        boundsHeight: Float,
        fallbackRadius: Float
    ): Path? {
        if (cheek == null) return null
        val radiusX = cheekEdgeRadius(faceContour, cheek, isLeft, boundsHeight, fallbackRadius)
        val verticalSpan = (boundsHeight * 0.16f).coerceAtLeast(4f)
        cheekLeafPath(faceContour, cheek, isLeft, verticalSpan, radiusX)?.let { return it }
        val radiusY = radiusX * 0.72f
        return ovalPath(cheek.x, cheek.y, radiusX, radiusY)
    }
}