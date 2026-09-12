package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Committed-pass renderer for the Frames tool (docs/effects_reference.md →
 * *Frames*), extracted from [BitmapRenderer] so each overlay drawing family owns
 * its raster logic. Every band / decorative-line pixel outside the interior
 * window (see [FrameGeometry]) is blended toward the frame colour, run as a
 * plain getPixels/setPixels pass so every style — even the modulated / moulding
 * edges — rasterizes deterministically on every backend, including the
 * Robolectric software canvas used by the parity tests. Draw math is unchanged
 * from `BitmapRenderer.drawFrames`.
 */
internal object FrameOverlayRenderer {

    internal fun drawFrames(
        canvas: Bitmap,
        frames: List<FrameLayer>,
        insetX: Int = 0,
        insetY: Int = 0
    ) {
        for (f in frames) {
            drawFrame(canvas, f, insetX, insetY)
        }
    }

    /**
     * Commits one frame layer — its **inner** tier draws inward over the photo,
     * its **outer** tier draws outward from the photo rect (the photo [insetX]×
     * [insetY] into the canvas, which the caller enlarged by the outer extent).
     */
    private fun drawFrame(canvas: Bitmap, f: FrameLayer, insetX: Int, insetY: Int) {
        val w = canvas.width
        val h = canvas.height
        if (w < 2 * insetX || h < 2 * insetY) return
        val photoW = w - 2 * insetX
        val photoH = h - 2 * insetY
        val short = minOf(photoW, photoH).coerceAtLeast(1).toFloat()
        if (f.inner.enabled) drawTierInner(canvas, f.inner, insetX, insetY, photoW, photoH, short)
        if (f.outer.enabled) drawTierOuter(canvas, f.outer, insetX, insetY, photoW, photoH, short)
    }

    private fun drawTierInner(
        canvas: Bitmap,
        tier: FrameTier,
        insetX: Int,
        insetY: Int,
        photoW: Int,
        photoH: Int,
        short: Float
    ) {
        val style = tier.style ?: return
        val w = canvas.width
        val h = canvas.height
        val t = (tier.thickness * short / 2f).coerceIn(0f, short / 2f)
        if (t < 1f && tier.lines <= 0) return
        val geometry = FrameGeometry(
            style, photoW, photoH, t,
            edgeSize = tier.edgeSize,
            customAngle = tier.customAngle,
            customCurveDistance = tier.customCurveDistance,
            customStrength = tier.customStrength,
            customCount = tier.customCount,
            offsetX = insetX,
            offsetY = insetY
        )
        val a = Color.alpha(tier.color)
        if (a == 0) return
        val fr = Color.red(tier.color)
        val fg = Color.green(tier.color)
        val fb = Color.blue(tier.color)
        val stripMask = buildStripMask(w, h, geometry, tier, short, outward = false)
        val keep = { x: Int, y: Int ->
            !geometry.isInterior(x, y) || stripMask[y * w + x]
        }
        blendTier(canvas, w, h, a, fr, fg, fb, keep)
    }

    private fun drawTierOuter(
        canvas: Bitmap,
        tier: FrameTier,
        insetX: Int,
        insetY: Int,
        photoW: Int,
        photoH: Int,
        short: Float
    ) {
        val style = tier.style ?: return
        val w = canvas.width
        val h = canvas.height
        val t = (tier.thickness * short / 2f).coerceIn(0f, short / 2f)
        if (t < 1f && tier.lines <= 0) return
        val geometry = FrameGeometry(
            style, w, h, t,
            edgeSize = tier.edgeSize,
            customAngle = tier.customAngle,
            customCurveDistance = tier.customCurveDistance,
            customStrength = tier.customStrength,
            customCount = tier.customCount,
            outward = true,
            offsetX = insetX,
            offsetY = insetY
        )
        val a = Color.alpha(tier.color)
        if (a == 0) return
        val fr = Color.red(tier.color)
        val fg = Color.green(tier.color)
        val fb = Color.blue(tier.color)
        val stripMask = buildStripMask(w, h, geometry, tier, short, outward = true)
        val keep = { x: Int, y: Int ->
            !geometry.isInterior(x, y) || stripMask[y * w + x]
        }
        blendTier(canvas, w, h, a, fr, fg, fb, keep)
    }

    /**
     * Blends every pixel where [keep] returns true toward the tier colour in a
     * single whole-canvas getPixels/setPixels pair (instead of one pair per row,
     * which issued 2×h JNI calls). The blend of a pixel depends only on that
     * pixel's own current value, so the row-wise and whole-canvas passes are
     * byte-identical.
     */
    private fun blendTier(
        canvas: Bitmap,
        w: Int,
        h: Int,
        a: Int,
        fr: Int,
        fg: Int,
        fb: Int,
        keep: (Int, Int) -> Boolean
    ) {
        val pixels = IntArray(w * h)
        canvas.getPixels(pixels, 0, w, 0, 0, w, h)
        // Translate the row-major keep predicate into a flat scan without boxing:
        // keep = blend, skip = untouched.
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (keep(x, y)) pixels[i] = blendFrame(pixels[i], a, fr, fg, fb)
                i++
            }
        }
        canvas.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * Rasterises the tier's decorative line strips into a canvas-sized mask —
     * the polyline the tier is anchored to — the interior window boundary for
     * the [outward] = false (inner) tier, the band's outer border contour for
     * the outer tier — with the *lining rule* from docs/effects_reference.md →
     * *Frame lines*: at every vertex whose interior angle lies more than 60°
     * away from 180° (below 120° — a sharp convex peak — or above 240° — a
     * concave notch) the strip reproduces that corner as a hard (mitered) angle;
     * at vertices within 60° of straight (a smooth 120°–240° angle, e.g. a
     * sampled oval / heart / rounded corner) the strip shares the boundary's
     * curve.
     *
     * The inner tier paints its strips as the exact distance-annulus of the
     * window boundary (the same margin bands the pre-mitre renderer used, so the
     * smooth shapes — oval / heart / rounded corners — stay byte-identical)
     * PLUS the miter wedge that the annulus arcs away around each hard vertex.
     * This keeps the curved / narrow geometry robust (a constant-offset
     * centre-line of a curved heart detaches and self-crosses) while still
     * drawing the hard corners the annulus alone cannot. The outer tier mirrors
     * the same construction off its own contour (the exact distance to the
     * band's outer border ring + the miter wedges), so the modulated outer
     * contours — cogged / sawtooth / hammer-bevel — carry their hard corners in
     * the strips too instead of smoothing them away.
     */
    private fun buildStripMask(
        w: Int,
        h: Int,
        geometry: FrameGeometry,
        tier: FrameTier,
        short: Float,
        outward: Boolean
    ): BooleanArray {
        val n = tier.lines
        val mask = BooleanArray(w * h)
        if (n <= 0) return mask
        val d = (tier.lineDistance * short / 3f).coerceAtLeast(0f)
        val l = (tier.lineThickness * short / 3f).coerceAtLeast(0f)
        if (l <= 0f) return mask
        val poly = stripSourcePolygon(geometry)
        if (poly.size < 6) return mask
        // The innermost strip centre-line can only reach half the boundary's
        // bbox; past that the boundary's opposite edges would cross and the
        // strips vanish (mirrors the old distance-field, whose concentric bands
        // collapse center-ward).
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var i = 0
        while (i < poly.size) {
            val x = poly[i]
            val y = poly[i + 1]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            i += 2
        }
        val reach = minOf(maxX - minX, maxY - minY) / 2f
        val half = l / 2f
        val marginAt: (Int, Int) -> Float = if (outward) { x, y -> geometry.outwardContourMargin(x, y) }
        else { x, y -> geometry.interiorMargin(x, y) }
        paintBandBaseline(mask, w, h, marginAt, d, l, n, reach, half)
        val hard = hardVertices(poly)
        if (hard == null || !hard.contains(true)) return mask
        for (k in 0 until n) {
            val c = d + k * (d + l) + half
            if (c >= reach) break
            // The offset heads deeper into the interior (inner tier) or further
            // beyond the outer contour (outer tier) — the side the margin grows.
            val centre = offsetPolyline(poly, if (outward) -c else c)
            paintHardEdges(mask, w, h, centre, half, hard, marginAt, c - half - 0.5f, c + half - 0.5f)
        }
        return mask
    }

    /** The baseline strips: the exact distance bands of the tier's boundary —
     * pixel `p` lies on strip `k` when its margin (built from the same Euclidean
     * transform of the boundary — the interior window or the outer contour)
     * falls in `[C_k − l/2 − 0.5, C_k + l/2 − 0.5)`. */
    private fun paintBandBaseline(
        mask: BooleanArray,
        w: Int,
        h: Int,
        marginAt: (Int, Int) -> Float,
        d: Float,
        l: Float,
        n: Int,
        reach: Float,
        half: Float
    ) {
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val margin = marginAt(x, y)
                if (margin < 0f) continue
                for (k in 0 until n) {
                    val c = d + k * (d + l) + half
                    if (c >= reach) break
                    val low = c - half - 0.5f
                    if (margin >= low && margin < c + half - 0.5f) {
                        mask[row + x] = true
                        break
                    }
                }
            }
        }
    }

    /**
     * The polyline the strips follow. The geometry's [FrameGeometry.polygon] is
     * a per-column / per-row sample walk, so the FOUR plain frame corners never
     * become vertices — each corner is cut by the diagonal between the last
     * top/left sample and the first right column (a chamfer the boundary itself
     * does not have). This returns the polygon with the four CORNER vertices
     * re-inserted where the adjacent inset curves intersect, so a right-angle
     * corner reads as a genuine hard vertex (interior ≈ 90°) and the strips
     * miter there; the smooth styles (oval / heart — a dense parametric sample)
     * and the rounded corners keep their own vertices and are returned verbatim.
     * The corner point is found by fixed-pointing `x = xCurve(y), y = yCurve(x)`
     * on the two curves that meet there, so it lands on the boundary even where
     * the curves meet tangentially (rounded / chamfered corners).
     */
    private fun stripSourcePolygon(geometry: FrameGeometry): FloatArray {
        val base = geometry.polygon
        val gw = geometry.width
        val gh = geometry.height
        val n = base.size / 2
        // Only the per-column/row walks carry the four inset corners: their
        // vertex count is exactly 2×(width+height). Parametric samples (oval /
        // heart) and degenerate shapes have different counts and stay as-is.
        if (n != 2 * (gw + gh)) return base
        val outer = geometry.outward
        val ox = geometry.offsetX.toFloat()
        val oy = geometry.offsetY.toFloat()
        val photoW = (gw - 2 * geometry.offsetX).coerceAtLeast(1)
        val photoH = (gh - 2 * geometry.offsetY).coerceAtLeast(1)
        fun railY(iy: Int, ix: Int, second: Boolean): Float {
            if (outer) {
                return if (second) (oy + photoH + geometry.outBottomAt(ix)).toFloat()
                else (oy - geometry.outTopAt(ix)).toFloat()
            }
            return if (second) (oy + (gh - geometry.bottomInset(ix))).toFloat()
            else (oy + geometry.topInset(ix)).toFloat()
        }
        fun railX(iy: Int, ix: Int, vertical: Boolean, second: Boolean): Float {
            if (outer) {
                return if (vertical) (ox + photoW + geometry.outRightAt(iy)).toFloat()
                else (ox - geometry.outLeftAt(iy)).toFloat()
            }
            return if (vertical) (ox + (gw - geometry.rightInset(iy))).toFloat()
            else (ox + geometry.leftInset(iy)).toFloat()
        }
        // Each corner = where the two adjacent inset / reach curves intersect,
        // fixed-pointed by bouncing between the extrapolated samples.
        fun cornerTR(): Pair<Float, Float> {
            var y: Float
            var x: Float
            if (outer) { y = 0f; x = ox + photoW + geometry.outRightAt(0) }
            else { y = 0f; x = ox + (gw - geometry.rightInset(0)) }
            repeat(8) {
                val iy = y.toInt().coerceIn(0, gh - 1)
                x = railX(iy, 0, vertical = true, second = false)
                val ix = x.toInt().coerceIn(0, gw - 1)
                y = railY(0, ix, second = false)
            }
            return x to y
        }
        fun cornerRB(): Pair<Float, Float> {
            var y = 0f
            var x = (gw - 1).toFloat()
            repeat(8) {
                val ix = x.toInt().coerceIn(0, gw - 1)
                y = railY(0, ix, second = true)
                val iy = y.toInt().coerceIn(0, gh - 1)
                x = railX(iy, 0, vertical = true, second = false)
            }
            return x to y
        }
        fun cornerBL(): Pair<Float, Float> {
            var y = (gh - 1).toFloat()
            var x = 0f
            repeat(8) {
                val iy = y.toInt().coerceIn(0, gh - 1)
                x = railX(iy, 0, vertical = false, second = false)
                val ix = x.toInt().coerceIn(0, gw - 1)
                y = railY(0, ix, second = true)
            }
            return x to y
        }
        fun cornerLT(): Pair<Float, Float> {
            var y = 0f
            var x = 0f
            repeat(8) {
                val ix = x.toInt().coerceIn(0, gw - 1)
                y = railY(0, ix, second = false)
                val iy = y.toInt().coerceIn(0, gh - 1)
                x = railX(iy, 0, vertical = false, second = false)
            }
            return x to y
        }
        val (trx, try_) = cornerTR()
        val (rbx, rby) = cornerRB()
        val (blx, bly) = cornerBL()
        val (ltx, lty) = cornerLT()
        val out = FloatArray((n + 4) * 2)
        var oi = 0
        for (i in 0 until n) {
            out[oi * 2] = base[2 * i]
            out[oi * 2 + 1] = base[2 * i + 1]
            oi++
            when (i) {
                gw - 1 -> {
                    out[oi * 2] = trx; out[oi * 2 + 1] = try_; oi++
                }
                gw + gh - 1 -> {
                    out[oi * 2] = rbx; out[oi * 2 + 1] = rby; oi++
                }
                2 * gw + gh - 1 -> {
                    out[oi * 2] = blx; out[oi * 2 + 1] = bly; oi++
                }
            }
        }
        out[oi * 2] = ltx
        out[oi * 2 + 1] = lty
        return out
    }

    /** Marks the indices of the followed [poly] whose INTERIOR angle lies more
     * than 60° away from a straight path (below 120° or above 240°) — the
     * vertices where the strips must reproduce a hard (mitered) corner. `null`
     * when the polygon has no such corner. */
    private fun hardVertices(poly: FloatArray): BooleanArray? {
        val n = poly.size / 2
        if (n < 4) return null
        val hard = BooleanArray(n)
        var any = false
        for (i in 0 until n) {
            val prev = (i - 1 + n) % n
            val next = (i + 1) % n
            val ax = poly[2 * i] - poly[2 * prev]
            val ay = poly[2 * i + 1] - poly[2 * prev + 1]
            val bx = poly[2 * next] - poly[2 * i]
            val by = poly[2 * next + 1] - poly[2 * i + 1]
            val alen = sqrt(ax * ax + ay * ay)
            val blen = sqrt(bx * bx + by * by)
            if (alen < 1e-4f || blen < 1e-4f) continue
            val ux = ax / alen
            val uy = ay / alen
            val vx = bx / blen
            val vy = by / blen
            val cross = ux * vy - uy * vx
            val dot = (ux * vx + uy * vy).coerceIn(-1f, 1f)
            val angleDeg = Math.toDegrees(acos(dot).toDouble()).toFloat()
            val interiorDeg = if (cross < 0f) 360f - angleDeg else angleDeg
            if (interiorDeg < HARD_ANGLE_MIN || interiorDeg > HARD_ANGLE_MAX) {
                hard[i] = true
                any = true
            }
        }
        return if (any) hard else null
    }

    /** Offsets a closed boundary [poly] by [dist] along the interior normal of
     * every edge ([dist] positive = deeper into the polygon's interior, negative
     * = outward), producing the strip centre-line. Hard corners (interior angle
     * below 120° or above 240°) stay hard — the two offset edges are intersected
     * (the miter); smooth corners (120°–240°) offset along the averaged vertex
     * normal so the centre-line follows the boundary's curve. */
    private fun offsetPolyline(poly: FloatArray, dist: Float): FloatArray {
        val n = poly.size / 2
        if (n < 3) return FloatArray(0)
        if (abs(dist) < 1e-4f) return poly.copyOf()
        val px = FloatArray(n)
        val py = FloatArray(n)
        for (i in 0 until n) {
            px[i] = poly[2 * i]
            py[i] = poly[2 * i + 1]
        }
        val outX = FloatArray(n)
        val outY = FloatArray(n)
        for (i in 0 until n) {
            val prev = (i - 1 + n) % n
            val next = (i + 1) % n
            val ax = px[i] - px[prev]
            val ay = py[i] - py[prev]
            val bx = px[next] - px[i]
            val by = py[next] - py[i]
            val alen = sqrt(ax * ax + ay * ay)
            val blen = sqrt(bx * bx + by * by)
            if (alen < 1e-4f || blen < 1e-4f) {
                continue
            }
            val ux = ax / alen
            val uy = ay / alen
            val vx = bx / blen
            val vy = by / blen
            // Left normals of the two edges: for the clockwise polygon walk
            // (interior window / outer contour) these point into the interior.
            val nax = -uy
            val nay = ux
            val nbx = -vy
            val nby = vx
            val cross = ux * vy - uy * vx
            val dot = (ux * vx + uy * vy).coerceIn(-1f, 1f)
            val angleDeg = Math.toDegrees(acos(dot).toDouble()).toFloat()
            // Interior angle at the vertex: reflex (concave notch, cross < 0 on
            // the clockwise walk) takes the complement of the headed angle.
            val interiorDeg = if (cross < 0f) 360f - angleDeg else angleDeg
            if (interiorDeg in HARD_ANGLE_MIN..HARD_ANGLE_MAX) {
                // Smooth corner: offset along the averaged vertex normal.
                var nx = nax + nbx
                var ny = nay + nby
                val nl = sqrt(nx * nx + ny * ny)
                if (nl < 1e-4f) {
                    nx = nax
                    ny = nay
                } else {
                    nx /= nl
                    ny /= nl
                }
                outX[i] = px[i] + dist * nx
                outY[i] = py[i] + dist * ny
            } else {
                // Hard corner: intersect the two offset edges (the miter).
                val lx = px[prev] + dist * nax
                val ly = py[prev] + dist * nay
                val rx = px[i] + dist * nbx
                val ry = py[i] + dist * nby
                val denom = ux * vy - uy * vx
                if (abs(denom) < 1e-6f) {
                    var nx = nax + nbx
                    var ny = nay + nby
                    val nl = sqrt(nx * nx + ny * ny)
                    if (nl < 1e-4f) {
                        outX[i] = px[i] + dist * nax
                        outY[i] = py[i] + dist * nay
                    } else {
                        outX[i] = px[i] + dist * (nx / nl)
                        outY[i] = py[i] + dist * (ny / nl)
                    }
                } else {
                    val s = ((lx - rx) * vy - (ly - ry) * vx) / denom
                    outX[i] = rx + s * vx
                    outY[i] = ry + s * vy
                }
            }
        }
        val out = FloatArray(2 * n)
        for (i in 0 until n) {
            out[2 * i] = outX[i]
            out[2 * i + 1] = outY[i]
        }
        return out
    }

    /** Paints the segments that meet at each hard vertex of the centre-line
     * [poly] (mirroring [hard] on the source polyline — the same index order is
     * kept), so only the miter wedge is superimposed on the baseline annulus.
     * The wedge pixels must lie on the strip's far side of the boundary (their
     * [marginAt] ≥ [minDepth]); this also prunes the centre-line pieces that
     * detach and self-cross around curved tips. */
    private fun paintHardEdges(
        mask: BooleanArray,
        w: Int,
        h: Int,
        poly: FloatArray,
        half: Float,
        hard: BooleanArray,
        marginAt: (Int, Int) -> Float,
        minDepth: Float,
        maxDepth: Float
    ) {
        val n = poly.size / 2
        if (n < 3 || half <= 0f) return
        val pad = (ceil(half.toDouble()) + 1.0).toInt()
        for (i in 0 until n) {
            if (!hard[i]) continue
            val prev = (i - 1 + n) % n
            val next = (i + 1) % n
            paintSegment(mask, w, h, poly, prev, i, half, pad, marginAt, minDepth, maxDepth)
            paintSegment(mask, w, h, poly, i, next, half, pad, marginAt, minDepth, maxDepth)
        }
    }

    private fun paintSegment(
        mask: BooleanArray,
        w: Int,
        h: Int,
        poly: FloatArray,
        i: Int,
        j: Int,
        half: Float,
        pad: Int,
        marginAt: ((Int, Int) -> Float)?,
        minDepth: Float,
        maxDepth: Float
    ) {
        val x1 = poly[2 * i]
        val y1 = poly[2 * i + 1]
        val x2 = poly[2 * j]
        val y2 = poly[2 * j + 1]
        if (x1 == x2 && y1 == y2) return
        val x0 = max(0, min(x1, x2).toInt() - pad)
        val x1i = min(w - 1, max(x1, x2).toInt() + pad)
        val y0 = max(0, min(y1, y2).toInt() - pad)
        val y1i = min(h - 1, max(y1, y2).toInt() + pad)
        for (py in y0..y1i) {
            val row = py * w
            for (px in x0..x1i) {
                if (mask[row + px]) continue
                if (marginAt != null) {
                    val m = marginAt(px, py)
                    if (m < minDepth || m > maxDepth) continue
                }
                if (pointSegmentDist(px + 0.5f, py + 0.5f, x1, y1, x2, y2) <= half) {
                    mask[row + px] = true
                }
            }
        }
    }

    /** Offsets a closed boundary [poly] by [dist] along the interior normal of
     * every edge ([dist] positive = deeper into the polygon's interior, negative
     * = outward), producing the strip centre-line. Hard corners (interior angle
     * below 120° or above 240°) stay hard — the two offset edges are intersected
     * (the miter); smooth corners (120°–240°) offset along the averaged vertex
     * normal so the centre-line follows the boundary's curve. */
    /** Euclidean distance from ([px],[py]) to the segment ([x1],[y1])–([x2],[y2]). */
    private fun pointSegmentDist(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-9f) {
            val ex = px - x1
            val ey = py - y1
            return sqrt(ex * ex + ey * ey)
        }
        var tt = ((px - x1) * dx + (py - y1) * dy) / lenSq
        tt = tt.coerceIn(0f, 1f)
        val cx = x1 + tt * dx
        val cy = y1 + tt * dy
        val ex = px - cx
        val ey = py - cy
        return sqrt(ex * ex + ey * ey)
    }

    private const val HARD_ANGLE_MIN = 120f

    /** Interior angles above 240° (concave notches) also miter. */
    private const val HARD_ANGLE_MAX = 240f

    /**
     * Alpha-blends the frame colour over [original]; the band is always opaque
     * afterwards (alpha 255) regardless of the frame colour's own alpha.
     */
    private fun blendFrame(original: Int, a: Int, fr: Int, fg: Int, fb: Int): Int {
        if (a == 255) return Color.argb(255, fr, fg, fb)
        val oa = 255 - a
        return Color.argb(
            255,
            (a * fr + oa * Color.red(original)) / 255,
            (a * fg + oa * Color.green(original)) / 255,
            (a * fb + oa * Color.blue(original)) / 255
        )
    }
}