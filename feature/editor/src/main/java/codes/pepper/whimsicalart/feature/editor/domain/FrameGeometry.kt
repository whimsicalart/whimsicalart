package codes.pepper.whimsicalart.feature.editor.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-style frame-band geometry (docs/effects_reference.md → *Frames*): the
 * interior shape — the window left for the photo — for a [width]×[height] canvas
 * at a given band [thickness] (px). Both the committed renderer
 * (`BitmapRenderer.drawFrames`) and the Compose overlay preview (`FramePreview`)
 * consume this geometry so the band matches pixel-for-pixel, and every style
 * degenerates to "no interior" at thickness 0 (the frame fills nothing) or a
 * sliver at thickness 100% (the frame covers the canvas).
 *
 * Every ⁎ style ([FrameStyle.hasEdgeSize]) additionally takes an [edgeSize]:
 * how far the corner / edge treatment runs along each edge (as a fraction of
 * half the short dimension — `0` = squared corner, `1` = the treatment reaches
 * the middle of the short edge). The special [FrameStyle.CUSTOM] style instead
 * takes its own four parameters ([customAngle], [customCurveDistance],
 * [customStrength], [customCount]): [customCurveDistance]'s SIGN picks the
 * direction (− = inward scoop, + = outward bulge, 0 = straight) and its absolute
 * value the reach (default −0.5), while [customAngle] sets only the phase.
 *
 * Two construction modes back the two frame tiers:
 * - **inner** ([outward] = false, default): the band runs INWARD from the photo
 *   edges; [width]/[height] are the *photo* size and [offsetX]/[offsetY] park
 *   the window inside a larger canvas (the enlarged outer-frame fold).
 * - **outer** ([outward] = true): the band runs OUTWARD from a photo rect
 *   anchored at ([offsetX], [offsetY]) sized `width - 2×offsetX` ×
 *   `height - 2×offsetY`; everything beyond the band (and the photo itself) is
 *   interior, so the margin between the band and the canvas edge is untouched.
 *
 * The band is rasterised pixel-exactly via [isInterior]: everything that is not
 * the interior is the frame band, so any style — a straight band, a modulated
 * ornamental edge, a corner zone profile, or a moulding profile — is covered by
 * one deterministic pass that blends the frame colour over the previous image
 * behind it. The decorative line strips ([FrameTier.lines]) are evaluated via
 * [interiorMargin] (inner tier, measured from the interior window boundary) or
 * [outwardMargin] (outer tier, measured from the photo rect edge).
 */
class FrameGeometry(
    val style: FrameStyle,
    val width: Int,
    val height: Int,
    val thickness: Float,
    val edgeSize: Float = 0.2f,
    val customAngle: Float = 0f,
    val customCurveDistance: Float = -0.5f,
    val customStrength: Float = 0f,
    val customCount: Float = 2f,
    /** Outer-tier mode: the band grows outward from the anchored photo rect. */
    val outward: Boolean = false,
    /** Where the photo window starts inside this canvas (photo-relative dims follow). */
    val offsetX: Int = 0,
    /** Where the photo window starts inside this canvas (photo-relative dims follow). */
    val offsetY: Int = 0
) {
    /** Top-edge inset per column (px) — inner mode interior boundary. */
    private val top = FloatArray(width)

    /** Bottom-edge inset per column (px) — inner mode interior boundary. */
    private val bottom = FloatArray(width)

    /** Left-edge inset per row (px) — inner mode interior boundary. */
    private val left = FloatArray(height)

    /** Right-edge inset per row (px) — inner mode interior boundary. */
    private val right = FloatArray(height)

    /** Outer mode: band reach above the photo top edge per column (px). */
    private val outTop = FloatArray(width)

    /** Outer mode: band reach below the photo bottom edge per column (px). */
    private val outBottom = FloatArray(width)

    /** Outer mode: band reach left of the photo edge per row (px). */
    private val outLeft = FloatArray(height)

    /** Outer mode: band reach right of the photo edge per row (px). */
    private val outRight = FloatArray(height)

    /**
     * Lazy exact Euclidean distance transform over the interior window: [interiorMargin]
     * returns dist[y·width+x] (px to the nearest boundary pixel, 0 on the outermost
     * interior ring), built on first use — the inner tier's decorative lines never
     * ask unless [FrameTier.lines] > 0. Only meaningful for the inner (non-outer) mode.
     */
    private var marginTransform: FloatArray? = null

    /**
     * Lazy exact Euclidean distance transform over the outer tier's contour: the
     * [outwardContourMargin] of every pixel beyond the band's outer border ring
     * — built on first use by the outer tier's decorative lines. Only meaningful
     * for the [outward] mode.
     */
    private var outwardEdgeTransform: FloatArray? = null

    /**
     * Precomputed per-row interior x-spans, used by closed shapes (oval / heart)
     * whose interior is not separable per axis; empty arrays mean an empty span,
     * so null → the inset-function test is used.
     */
    private val spans: Array<FloatArray>?

    /**
     * The interior boundary polygon (the photo window), scaled to this canvas
     * (inner mode), or the outer band's boundary contour around the photo rect
     * (outer mode). Used by the Compose overlay preview to draw the band as the
     * even-odd gap between canvas/photo rect and this boundary.
     */
    val polygon: FloatArray

    /** The photo rect's width inside this canvas (outer mode). */
    private val photoW: Int = (width - 2 * offsetX).coerceAtLeast(1)

    /** The photo rect's height inside this canvas (outer mode). */
    private val photoH: Int = (height - 2 * offsetY).coerceAtLeast(1)

    init {
        val w = width
        val h = height
        val t = thickness.coerceIn(0f, min(w, h) / 2f)
        if (outward) {
            spans = null
            buildOuter(t)
            polygon = outerBoundaryPolygon()
        } else {
            when (style) {
            FrameStyle.OVAL -> {
                val ellipse = ellipseGeometry(w, h, t)
                spans = ellipse.first
                polygon = ellipse.second
            }
            FrameStyle.HEART -> {
                val heart = heartGeometry(w, h, t)
                spans = heart.first
                polygon = heart.second
            }
            // Rounded and corner-eased both take the edge size as their corner
            // radius (0% → 0 = squared corner, 100% → radius = half the short
            // edge).
            FrameStyle.ROUNDED, FrameStyle.CORNER_EASED -> {
                val radius = cornerRadius()
                fillRounded(t, radius)
                spans = null
                polygon = edgePolygon()
            }
            FrameStyle.BEVELED -> {
                fillChamfer(t, zoneReach())
                spans = null
                polygon = edgePolygon()
            }
            // The remaining edge & bevel profiles: a corner treatment of reach
            // zoneReach() applies the profile's shape, squared (plain t) beyond.
            FrameStyle.INWARD_CURVED, FrameStyle.GEAR_EDGE,
            FrameStyle.OGEE_EDGE, FrameStyle.DOUBLE_OGEE_EDGE,
            FrameStyle.DUPONT_EDGE, FrameStyle.COVE,
            FrameStyle.FRENCH_COVE, FrameStyle.DUPONT_BULLNOSE,
            FrameStyle.OGEE_COVE_EDGE -> {
                fillZone(t, zoneReach(), zoneProfile(style), zoneAmplitude(style))
                spans = null
                polygon = edgePolygon()
            }
            else -> {
                fillInsets(t, insetValue(style))
                spans = null
                polygon = edgePolygon()
            }
        }
        }
    }

    /**
     * True when [x],[y] lies inside the interior window (the photo region); the
     * frame band is every pixel that is not interior. In [outward] mode the
     * interior additionally covers the margin between the outer band and the
     * canvas edge, so only the band itself is ever painted.
     */
    fun isInterior(x: Int, y: Int): Boolean {
        if (outward) return !isOuterBand(x, y)
        val sx = x - offsetX
        val sy = y - offsetY
        val sp = spans
        if (sp != null) {
            if (sy < 0 || sy >= height) return false
            val row = sp[sy]
            var k = 0
            while (k + 1 < row.size) {
                if (sx >= row[k] && sx < row[k + 1]) return true
                k += 2
            }
            return false
        }
        if (sx < 0 || sx >= width || sy < 0 || sy >= height) return false
        return sy >= top[sx] && sy < height - bottom[sx] &&
            sx >= left[sy] && sx < width - right[sy]
    }

    /**
     * True when [x],[y] lies inside the OUTER tier's band — the pixels in the
     * ring at reach [outTop]/[outBottom]/[outLeft]/[outRight] outward from the
     * anchored photo rect. Corner pixels are painted when any adjacent side's
     * reach covers them, so the ring is a continuous band around the photo.
     */
    fun isOuterBand(x: Int, y: Int): Boolean {
        if (x >= offsetX && x < offsetX + photoW &&
            y >= offsetY && y < offsetY + photoH
        ) {
            return false
        }
        // The reach arrays are indexed by ABSOLUTE canvas coordinate (buildOuter
        // fills every absolute column/row), so no photo-relative re-map here.
        if (y < offsetY && (offsetY - y).toFloat() < outTop[x]) return true
        if (y >= offsetY + photoH && (y - (offsetY + photoH)).toFloat() < outBottom[x]) return true
        if (x < offsetX && (offsetX - x).toFloat() < outLeft[y]) return true
        if (x >= offsetX + photoW && (x - (offsetX + photoW)).toFloat() < outRight[y]) return true
        return false
    }

    /**
     * The interior window's perpendicular margin (px) at canvas point [x],[y] —
     * how far the pixel sits from the window boundary, used to place the inner
     * tier's decorative line strips. This is the EXACT Euclidean distance to
     * the nearest boundary pixel (0 on the outermost interior ring), so the
     * strips follow the shape's true normal offset at constant thickness and
     * spacing — the axis-scalar min the code used to compute is the normal
     * distance only on straight edges and deformed curves (oval / heart /
     * rounded corners). `-1` when the pixel is outside the window.
     */
    fun interiorMargin(x: Int, y: Int): Float {
        if (outward) return -1f
        val sx = x - offsetX
        val sy = y - offsetY
        if (sx < 0 || sx >= width || sy < 0 || sy >= height) return -1f
        if (!isInterior(x, y)) return -1f
        val cache = marginTransform ?: computeMarginTransform().also { marginTransform = it }
        return cache[sy * width + sx]
    }

    /**
     * The outward margin (px) of [x],[y] measured from the band's OUTER BORDER
     * contour (not the photo rect) — used to place the outer tier's decorative
     * line strips at the same concentric offsets the inner tier's strips use off
     * its window boundary. This is the EXACT Euclidean distance to the nearest
     * outer-contour-ring pixel (0 on the outermost band ring), so the strips
     * follow every step of the modulated contour (cogged / sawtooth …) rather
     * than drifting out to the photo-edge-parallel position. `-1` for pixels
     * inside the photo or on the band itself.
     */
    fun outwardContourMargin(x: Int, y: Int): Float {
        if (!outward) return -1f
        if (x < 0 || x >= width || y < 0 || y >= height) return -1f
        if (isOuterBand(x, y)) return -1f
        if (x >= offsetX && x < offsetX + photoW && y >= offsetY && y < offsetY + photoH) return -1f
        val cache = outwardEdgeTransform ?: computeOutwardEdgeTransform().also { outwardEdgeTransform = it }
        return cache[y * width + x]
    }

    /**
     * Builds the exact L2 distance map from every pixel to the band's border:
     * the border is the outermost (and the photo-facing) band ring, then the
     * same separable two-pass exact Euclidean distance transform used by the
     * inner tier yields the per-pixel nearest-border distance.
     */
    private fun computeOutwardEdgeTransform(): FloatArray {
        val w = width
        val h = height
        val n = w * h
        val inside = BooleanArray(n)
        for (y in 0 until h) {
            for (x in 0 until w) {
                inside[y * w + x] = isOuterBand(x, y)
            }
        }
        return buildBoundaryDistance(inside)
    }

    /**
     * Builds the exact L2 distance map from every interior pixel to the window
     * boundary: the boundary is the outermost interior ring (interior pixels with
     * any 8-neighbour outside), then a separable two-pass exact Euclidean
     * distance transform (Felzenszwalb & Huttenlocher) yields the per-pixel
     * nearest-boundary distance. On a straight edge this reproduces the historic
     * axis margin (0 on the ring, +1 per row deeper) and on curved shapes it
     * equals the true normal offset away from the boundary.
     */
    private fun computeMarginTransform(): FloatArray {
        val w = width
        val h = height
        val n = w * h
        val inside = BooleanArray(n)
        for (sy in 0 until h) {
            val y = sy + offsetY
            for (sx in 0 until w) {
                inside[sy * w + sx] = isInterior(sx + offsetX, y)
            }
        }
        return buildBoundaryDistance(inside)
    }

    /** The separable two-pass exact Euclidean distance to the region's border. */
    private fun buildBoundaryDistance(inside: BooleanArray): FloatArray {
        val w = width
        val h = height
        val n = w * h
        val boundary = BooleanArray(n)
        var found = false
        for (sy in 0 until h) {
            for (sx in 0 until w) {
                if (!inside[sy * w + sx]) continue
                var b = false
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dy == 0 && dx == 0) continue
                        val nx = sx + dx
                        val ny = sy + dy
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h || !inside[ny * w + nx]) {
                            b = true
                        }
                    }
                }
                if (b) {
                    boundary[sy * w + sx] = true
                    found = true
                }
            }
        }
        if (!found) return FloatArray(n) { -1f }
        val dist = FloatArray(n)
        val col = FloatArray(h)
        val colOut = FloatArray(h)
        for (x in 0 until w) {
            for (y in 0 until h) col[y] = if (boundary[y * w + x]) 0f else SDF_INF
            distanceTransform1D(col, colOut)
            for (y in 0 until h) dist[y * w + x] = colOut[y]
        }
        val rowF = FloatArray(w)
        val rowOut = FloatArray(w)
        for (y in 0 until h) {
            for (x in 0 until w) rowF[x] = dist[y * w + x]
            distanceTransform1D(rowF, rowOut)
            for (x in 0 until w) dist[y * w + x] = rowOut[x]
        }
        val maxSide = maxOf(w, h).toFloat() + 1f
        for (i in 0 until n) {
            val s = sqrt(dist[i])
            dist[i] = if (s <= maxSide) s else -1f
        }
        return dist
    }

    /**
     * Exact 1-D squared Euclidean distance transform (Felzenszwalb & Huttenlocher,
     * "Distance Transforms of Sampled Functions"): `out[q] = min_j ((q−j)² + f[j])`
     * with sites where `f[j]` is finite ([SDF_INF] marks an absent site). `f` and
     * `out` must be the same size.
     */
    private fun distanceTransform1D(f: FloatArray, out: FloatArray) {
        val m = f.size
        if (m == 0) return
        val v = IntArray(m)
        val z = FloatArray(m + 1)
        var k = 0
        v[0] = 0
        z[0] = -1e10f
        z[1] = 1e10f
        for (q in 1 until m) {
            var s = intersection(f, v[k], q)
            while (s <= z[k]) {
                k--
                s = intersection(f, v[k], q)
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = 1e10f
        }
        k = 0
        for (q in 0 until m) {
            while (z[k + 1] < q) k++
            val d = (q - v[k]).toFloat()
            out[q] = d * d + f[v[k]]
        }
    }

    /** Where the parabola of position [q] meets that of the last kept site [p]. */
    private fun intersection(f: FloatArray, p: Int, q: Int): Float {
        return ((f[q] + q * q) - (f[p] + p * p)) / (2.0f * (q - p))
    }

    /**
     * The outward margin (px) of [x],[y] measured from the anchored photo rect
     * edge — used to place the outer tier's decorative line strips beyond its
     * band. `-1` for pixels inside the photo rect.
     */
    fun outwardMargin(x: Int, y: Int): Float {
        if (x >= offsetX && x < offsetX + photoW &&
            y >= offsetY && y < offsetY + photoH
        ) {
            return -1f
        }
        var m = Float.MAX_VALUE
        if (y < offsetY) m = minOf(m, (offsetY - y).toFloat())
        if (y >= offsetY + photoH) m = minOf(m, (y - (offsetY + photoH)).toFloat())
        if (x < offsetX) m = minOf(m, (offsetX - x).toFloat())
        if (x >= offsetX + photoW) m = minOf(m, (x - (offsetX + photoW)).toFloat())
        return if (m == Float.MAX_VALUE) -1f else m
    }

    /** Interior top inset (px) at column [x] — exposed for geometry tests. */
    internal fun topInset(x: Int): Float = top[x - offsetX]

    /** Interior bottom inset (px) at column [x] — exposed for geometry tests. */
    internal fun bottomInset(x: Int): Float = bottom[x - offsetX]

    /** Interior left inset (px) at row [y] — exposed for geometry tests. */
    internal fun leftInset(y: Int): Float = left[y - offsetY]

    /** Interior right inset (px) at row [y] — exposed for geometry tests. */
    internal fun rightInset(y: Int): Float = right[y - offsetY]

    /** Outer band reach above the photo edge at absolute column [x] — exposed for geometry tests. */
    internal fun outTopAt(x: Int): Float = outTop[x]

    /** Outer band reach below the photo edge at absolute column [x] — exposed for geometry tests. */
    internal fun outBottomAt(x: Int): Float = outBottom[x]

    /** Outer band reach left of the photo edge at absolute row [y] — exposed for geometry tests. */
    internal fun outLeftAt(y: Int): Float = outLeft[y]

    /** Outer band reach right of the photo edge at absolute row [y] — exposed for geometry tests. */
    internal fun outRightAt(y: Int): Float = outRight[y]

    /** The edge-size reach along each edge: 0..half the short dimension. */
    private fun zoneReach(): Float {
        val short = min(width, height).toFloat()
        return edgeSize.coerceIn(0f, 1f) * short / 2f
    }

    /** Rounded / corner-eased radius from the edge size (half the short edge at 100%). */
    private fun cornerRadius(): Float = zoneReach().coerceIn(0f, min(width, height) * 0.5f)

    /**
     * Fills the per-axis insets with an edge-size-bounded corner treatment: on
     * each edge the profile shape (of [amplitude]) runs for [reach] px from each
     * corner; beyond the reach the band is straight (inset = [t]). `reach` 0 →
     * every edge is straight (a squared corner, exactly like Solid).
     *
     * The profile swing is **thickness-independent** (docs/effects_reference.md
     * → *Edge-size bug fix*): it is derived from the edge-size [reach] rather
     * than the band thickness, so dragging the thickness slider preserves the
     * edge shape — only clamped by `maxI = min(width, height)/2` when a thick
     * band would otherwise let opposite edges meet/touch.
     */
    private fun fillZone(t: Float, reach: Float, shape: (Float) -> Float, amplitude: Float) {
        val maxI = min(width, height) * 0.5f
        val swing = (amplitude * reach).coerceIn(0f, maxI)
        fun zone(d: Float): Float {
            if (reach <= 0f || d >= reach) return t
            return (t + swing * shape(d / reach)).coerceIn(0f, maxI)
        }
        for (x in 0 until width) {
            val d = min(x.toFloat(), (width - 1 - x).toFloat())
            val i = zone(d)
            top[x] = i
            bottom[x] = i
        }
        for (y in 0 until height) {
            val d = min(y.toFloat(), (height - 1 - y).toFloat())
            val i = zone(d)
            left[y] = i
            right[y] = i
        }
    }

    /** Chamfered (mitred) corner of depth = [reach], straight beyond. */
    private fun fillChamfer(t: Float, reach: Float) {
        val maxI = min(width, height) * 0.5f
        fun chamfer(d: Float): Float {
            if (reach <= 0f || d >= reach) return t
            return (t + reach - d).coerceIn(0f, maxI)
        }
        for (x in 0 until width) {
            val d = min(x.toFloat(), (width - 1 - x).toFloat())
            val i = chamfer(d)
            top[x] = i
            bottom[x] = i
        }
        for (y in 0 until height) {
            val d = min(y.toFloat(), (height - 1 - y).toFloat())
            val i = chamfer(d)
            left[y] = i
            right[y] = i
        }
    }

    private fun fillInsets(t: Float, inset: (Float) -> Float) {
        val wm = (Math.max(1, width - 1)).toFloat()
        val hm = (Math.max(1, height - 1)).toFloat()
        val maxI = min(width, height) * 0.5f
        for (x in 0 until width) {
            val i = (t * inset(x / wm)).coerceIn(0f, maxI)
            top[x] = i
            bottom[x] = i
        }
        for (y in 0 until height) {
            val i = (t * inset(y / hm)).coerceIn(0f, maxI)
            left[y] = i
            right[y] = i
        }
    }

    private fun fillRounded(t: Float, radius: Float) {
        val r = radius.coerceIn(0f, min(width, height) * 0.5f)
        for (x in 0 until width) {
            val corner = cornerInset(min(x, width - 1 - x).toFloat(), t, r)
            top[x] = corner
            bottom[x] = corner
        }
        for (y in 0 until height) {
            val corner = cornerInset(min(y, height - 1 - y).toFloat(), t, r)
            left[y] = corner
            right[y] = corner
        }
    }

    /** Interior top-edge inset for a rounded corner at axis distance [d] from it. */
    private fun cornerInset(d: Float, t: Float, r: Float): Float {
        if (d < t) return t
        if (d >= t + r) return t
        return t + r - sqrt(r * r - (d - (t + r)) * (d - (t + r)))
    }

    /** Samples the four inset edges as a closed perimeter polygon for previews. */
    private fun edgePolygon(): FloatArray {
        val pts = ArrayList<Float>((width + height) * 2 + 8)
        for (x in 0 until width) {
            pts.add((x + offsetX).toFloat()); pts.add(offsetY + top[x])
        }
        for (y in 0 until height) {
            pts.add(offsetX + width - right[y]); pts.add((y + offsetY).toFloat())
        }
        for (x in width - 1 downTo 0) {
            pts.add((x + offsetX).toFloat()); pts.add(offsetY + height - bottom[x])
        }
        for (y in height - 1 downTo 0) {
            pts.add(offsetX + left[y]); pts.add((y + offsetY).toFloat())
        }
        return pts.toFloatArray()
    }

    // ---------------------------------------------------------------- Outer tier

    /**
     * Fills the outward reach arrays for the [outward] mode: the reach of the
     * band beyond each photo-rect edge, mirrored from the inner style math so
     * the edge treatments sit on the ring's OUTER border.
     */
    private fun buildOuter(t: Float) {
        val maxI = min(width, height) * 0.5f
        val pmx = (Math.max(1, photoW - 1)).toFloat()
        val pmy = (Math.max(1, photoH - 1)).toFloat()
        when (style) {
            // Rounded / corner-eased: the corner bulges into a quarter-arc of
            // radius = the edge size; reach t + arc, tapering back to t.
            FrameStyle.ROUNDED, FrameStyle.CORNER_EASED -> {
                val r = cornerRadius()
                for (x in 0 until width) {
                    val c = (x - offsetX).coerceIn(0, photoW - 1)
                    val d = min(c.toFloat(), (photoW - 1 - c).toFloat())
                    val i = outerRoundedReach(t, r, d).coerceIn(0f, maxI)
                    outTop[x] = i; outBottom[x] = i
                }
                for (y in 0 until height) {
                    val rw = (y - offsetY).coerceIn(0, photoH - 1)
                    val d = min(rw.toFloat(), (photoH - 1 - rw).toFloat())
                    val i = outerRoundedReach(t, r, d).coerceIn(0f, maxI)
                    outLeft[y] = i; outRight[y] = i
                }
            }
            FrameStyle.BEVELED -> {
                val reach = zoneReach()
                for (x in 0 until width) {
                    val c = (x - offsetX).coerceIn(0, photoW - 1)
                    val d = min(c.toFloat(), (photoW - 1 - c).toFloat())
                    val i = outerChamfer(t, reach, d).coerceIn(0f, maxI)
                    outTop[x] = i; outBottom[x] = i
                }
                for (y in 0 until height) {
                    val rw = (y - offsetY).coerceIn(0, photoH - 1)
                    val d = min(rw.toFloat(), (photoH - 1 - rw).toFloat())
                    val i = outerChamfer(t, reach, d).coerceIn(0f, maxI)
                    outLeft[y] = i; outRight[y] = i
                }
            }
            // The edge & bevel profiles: the treatment carries OUTWARD on the
            // ring's outer border, shaped by the same zone profile and driven by
            // the edge-size reach alone (thickness-independent swing).
            FrameStyle.INWARD_CURVED, FrameStyle.GEAR_EDGE,
            FrameStyle.OGEE_EDGE, FrameStyle.DOUBLE_OGEE_EDGE,
            FrameStyle.DUPONT_EDGE, FrameStyle.COVE,
            FrameStyle.FRENCH_COVE, FrameStyle.DUPONT_BULLNOSE,
            FrameStyle.OGEE_COVE_EDGE -> {
                val reach = zoneReach()
                val swing = (zoneAmplitude(style) * reach).coerceIn(0f, maxI)
                fun zone(d: Float): Float {
                    if (reach <= 0f || d >= reach) return t
                    return (t + swing * zoneProfile(style)(d / reach)).coerceIn(0f, maxI)
                }
                for (x in 0 until width) {
                    val c = (x - offsetX).coerceIn(0, photoW - 1)
                    val d = min(c.toFloat(), (photoW - 1 - c).toFloat())
                    val i = zone(d)
                    outTop[x] = i; outBottom[x] = i
                }
                for (y in 0 until height) {
                    val rw = (y - offsetY).coerceIn(0, photoH - 1)
                    val d = min(rw.toFloat(), (photoH - 1 - rw).toFloat())
                    val i = zone(d)
                    outLeft[y] = i; outRight[y] = i
                }
            }
            // Solid, oval/heart, ornamental / moulding and Custom: the reach is
            // the band scaled by the style's edge modulation, constant for Solid.
            else -> {
                val inset = insetValue(style)
                for (x in 0 until width) {
                    val c = (x - offsetX).coerceIn(0, photoW - 1)
                    val u = c / pmx
                    val i = (t * inset(u)).coerceIn(0f, maxI)
                    outTop[x] = i; outBottom[x] = i
                }
                for (y in 0 until height) {
                    val rw = (y - offsetY).coerceIn(0, photoH - 1)
                    val u = rw / pmy
                    val i = (t * inset(u)).coerceIn(0f, maxI)
                    outLeft[y] = i; outRight[y] = i
                }
            }
        }
    }

    /** Outward reach of a rounded corner at axis distance [d] (quarter-arc bulge). */
    private fun outerRoundedReach(t: Float, r: Float, d: Float): Float {
        if (r <= 0f || d >= r) return t
        return t + sqrt(r * r - d * d)
    }

    /** Outward reach of a beveled (chamfered) corner at axis distance [d]. */
    private fun outerChamfer(t: Float, reach: Float, d: Float): Float {
        if (reach <= 0f || d >= reach) return t
        return t + (reach - d)
    }

    /** Samples the outer band's boundary contour (preview polygon). */
    private fun outerBoundaryPolygon(): FloatArray {
        val pts = ArrayList<Float>((width + height) * 2 + 8)
        for (x in 0 until width) {
            pts.add(x.toFloat()); pts.add(offsetY - outTop[x])
        }
        for (y in 0 until height) {
            pts.add(offsetX + photoW + outRight[y]); pts.add(y.toFloat())
        }
        for (x in width - 1 downTo 0) {
            pts.add(x.toFloat()); pts.add(offsetY + photoH + outBottom[x])
        }
        for (y in height - 1 downTo 0) {
            pts.add(offsetX - outLeft[y]); pts.add(y.toFloat())
        }
        return pts.toFloatArray()
    }

    private fun ellipseGeometry(w: Int, h: Int, t: Float): Pair<Array<FloatArray>, FloatArray> {
        val short = min(w, h)
        val ratio = (short - 2 * t) / short
        if (ratio <= 0f) return spansNone()
        val rx = w * ratio / 2f
        val ry = h * ratio / 2f
        val cx = w / 2f
        val cy = h / 2f
        val rows = Array(h) { EMPTY }
        for (y in 0 until h) {
            val dy = (y + 0.5f - cy) / ry
            if (abs(dy) > 1f) continue
            val dx = rx * sqrt(1f - dy * dy)
            rows[y] = floatArrayOf(cx - dx, cx + dx)
        }
        val poly = FloatArray(256)
        for (i in 0 until 128) {
            val a = (i.toFloat() / 128f) * 2f * PI_F
            poly[2 * i] = cx + rx * cos(a)
            poly[2 * i + 1] = cy + ry * sin(a)
        }
        return rows to poly
    }

    private fun heartGeometry(w: Int, h: Int, t: Float): Pair<Array<FloatArray>, FloatArray> {
        val short = min(w, h)
        val ratio = (short - 2 * t) / short
        if (ratio <= 0f) return spansNone()
        // Heart parameter space spans x∈[-16,16] and y∈[-18,12] (width 32, height 30).
        val s = min(w * ratio / 32f, h * ratio / 32f)
        val pts = FloatArray(360)
        for (i in 0 until 180) {
            val a = (i.toFloat() / 180f) * 2f * PI_F
            val sinA = sin(a)
            val cx = 16f * sinA * sinA * sinA
            val cy = 13f * cos(a) - 5f * cos(2f * a) - 2f * cos(3f * a) - cos(4f * a)
            pts[2 * i] = w / 2f + cx * s
            // Heart parameter-space y spans cy ∈ [-18, 12] (midpoint -3); the
            // (cy + 3f) offset centres the bounding box (h/2 − (cy − max)‥(cy − min)
            // would drop it by ~6*s below centre).
            pts[2 * i + 1] = h / 2f - (cy + 3f) * s
        }
        val rows = rayCast(pts, h)
        return rows to pts
    }

    private fun spansNone(): Pair<Array<FloatArray>, FloatArray> {
        return Array(height) { EMPTY } to EMPTY
    }

    private fun rayCast(poly: FloatArray, h: Int): Array<FloatArray> {
        val rows = Array(h) { EMPTY }
        val n = poly.size / 2
        for (y in 0 until h) {
            val yc = y + 0.5f
            val xs = ArrayList<Float>(4)
            var i = 0
            while (i < n) {
                val j = (i + 1) % n
                val ax = poly[2 * i]; val ay = poly[2 * i + 1]
                val bx = poly[2 * j]; val by = poly[2 * j + 1]
                if ((ay <= yc && by > yc) || (by <= yc && ay > yc)) {
                    val tt = (yc - ay) / (by - ay)
                    xs.add(ax + tt * (bx - ax))
                }
                i++
            }
            xs.sort()
            if (xs.size >= 2) {
                val clean = ArrayList<Float>(xs.size)
                var k = 0
                while (k + 1 < xs.size) {
                    val a = xs[k]
                    val b = xs[k + 1]
                    if (b - a >= 1f) { clean.add(a); clean.add(b) }
                    k += 2
                }
                rows[y] = clean.toFloatArray()
            }
        }
        return rows
    }

    companion object {
        private const val PI_F = 3.14159265f
        private val EMPTY = FloatArray(0)

        /** SDF "absent site" sentinel for the 1-D distance-transform passes. */
        private const val SDF_INF = 1e9f

        /**
         * How far a style's edge treatment can carry OUTWARD beyond the band's
         * nominal edge (docs/effects_reference.md → *Outer-frame dimensions*):
         * the ⁎ reach for the edge profiles / rounded / beveled, an allowance
         * for the ornamental modulations and Custom curve, and zero for the
         * plain shapes. Consumed when computing the outer tier's output extent
         * so the enlarged canvas never clips a treatment.
         */
        internal fun outwardReachPx(outer: FrameTier, short: Float): Float {
            val style = outer.style ?: return 0f
            return when {
                style.hasEdgeSize || style == FrameStyle.ROUNDED ||
                    style == FrameStyle.BEVELED || style == FrameStyle.CORNER_EASED ->
                    outer.edgeSize.coerceIn(0f, 1f) * short / 2f
                style == FrameStyle.CUSTOM -> outer.thickness * short / 2f
                style == FrameStyle.WAVY || style == FrameStyle.SAWTOOTH ||
                    style == FrameStyle.FLORAL || style == FrameStyle.COGGED ||
                    style == FrameStyle.WATERFALL || style == FrameStyle.SINGLE_WAVE ||
                    style == FrameStyle.TWIN_WAVE || style == FrameStyle.RECESSED ||
                    style == FrameStyle.OFFSET_WAVE ||
                    style == FrameStyle.WATER_WAVES_INWARD ||
                    style == FrameStyle.WATER_WAVES_OUTWARD ->
                    0.4f * (outer.thickness * short / 2f).coerceIn(0f, short / 2f)
                else -> 0f
            }
        }
    }

    /** The per-axis inset (px) for the edge-modulated / ornamental styles. */
    private fun insetValue(style: FrameStyle): (Float) -> Float {
            return when (style) {
                FrameStyle.SOLID -> { u -> 1f }
                FrameStyle.WAVY -> { u -> 1f + 0.3f * sin(2f * PI_F * 3f * u) }
                FrameStyle.SAWTOOTH -> { u ->
                    val v = u * 3f
                    1f + 0.35f * (v - floor(v) - 0.5f)
                }
                // The sequential "ornamental / moulding" styles keep their
                // geometric footprint through the renames (Cogged was Gear,
                // Single Wave was Ogee, Twin Wave was Double Ogee, Recessed was
                // Dupont, Offset Wave was Ogee Cove).
                FrameStyle.COGGED -> { u -> if (sin(2f * PI_F * 3f * u) >= 0f) 1.35f else 0.8f }
                FrameStyle.FLORAL -> { u -> 1f + 0.4f * cos(2f * PI_F * 6f * u) }
                FrameStyle.WATERFALL -> { u -> if (u < 0.5f) 1f else 0.7f }
                FrameStyle.SINGLE_WAVE -> { u -> 1f + 0.3f * cos(2f * PI_F * u) }
                FrameStyle.TWIN_WAVE -> { u -> 1f + 0.3f * cos(4f * PI_F * u) }
                FrameStyle.RECESSED -> { u ->
                    if (u < 0.4f || u > 0.6f) 1f else 0.6f
                }
                FrameStyle.OFFSET_WAVE -> { u ->
                    1f + 0.25f * cos(2f * PI_F * (u - 0.25f))
                }
                FrameStyle.WATER_WAVES_INWARD -> { u ->
                    1f - 0.3f * abs(sin(PI_F * 4f * u))
                }
                FrameStyle.WATER_WAVES_OUTWARD -> { u ->
                    1f + 0.3f * abs(sin(PI_F * 4f * u))
                }
                FrameStyle.CUSTOM -> { u -> customInset(u) }
                else -> { u -> 1f }
            }
        }

        /**
         * The custom free-form inset: a train of [customCount] half-curves per
         * side (default 2 = one complete wave), each expanded by the absolute
         * [customCurveDistance] from the band in the SIGN-chosen direction — the
         * curve-distance sign is the direction (negative = inward scoop, positive
         * = outward bulge, 0 = straight) — shaped by [customStrength] (0 = arc,
         * →1 = triangle point, negative = teardrop); [customAngle] controls the
         * PHASE ONLY (where the pattern starts along each edge).
         */
        private fun customInset(u: Float): Float {
            val count = customCount.coerceIn(1f, 10f)
            val amp = abs(customCurveDistance).coerceIn(0f, 1f)
            val strength = customStrength.coerceIn(-1f, 1f)
            val outward = customCurveDistance > 0f
            val phase = (customAngle.coerceIn(-180f, 180f)) / 180f
            val vt = u * count + phase * 0.5f
            val x = vt - floor(vt)
            var y = when {
                strength <= 0f -> {
                    val arc = sin(PI_F * x)
                    val teardrop = sqrt(abs(sin(PI_F * x)))
                    arc + strength * (arc - teardrop)
                }
                else -> {
                    val arc = sin(PI_F * x)
                    val triangle = 1f - abs(2f * x - 1f)
                    arc + strength * (triangle - arc)
                }
            }
            y = y.coerceIn(0f, 1f)
            return if (outward) 1f + amp * y else 1f - amp * y
        }

        /** The corner-zone shape (0 at the reach edge, amplitude-multiplied) per profile. */
        private fun zoneProfile(style: FrameStyle): (Float) -> Float {
            return when (style) {
                FrameStyle.INWARD_CURVED -> { s -> (1f - s) * (1f - s) }
                FrameStyle.GEAR_EDGE -> { s ->
                    0.5f - 0.5f * cos(2f * PI_F * 2f * s)
                }
                FrameStyle.OGEE_EDGE -> { s -> 0.5f - 0.5f * cos(2f * PI_F * s) }
                FrameStyle.DOUBLE_OGEE_EDGE -> { s -> 0.5f - 0.5f * cos(4f * PI_F * s) }
                FrameStyle.DUPONT_EDGE -> { s ->
                    when {
                        s < 0.5f -> 0f
                        s < 0.75f -> (s - 0.5f) / 0.25f
                        else -> (1f - s) / 0.25f
                    }
                }
                FrameStyle.COVE -> { s -> sin(PI_F * s) }
                FrameStyle.FRENCH_COVE -> { s -> sin(PI_F * s) }
                FrameStyle.DUPONT_BULLNOSE -> { s ->
                    if (s < 0.5f) 0f else sin(PI_F * (s - 0.5f))
                }
                FrameStyle.OGEE_COVE_EDGE -> { s -> sin(PI_F * s) + 0.4f * sin(2f * PI_F * s) }
                else -> { s -> s }
            }
        }

        /** Corner-zone amplitude (fraction of thickness) per profile. */
        private fun zoneAmplitude(style: FrameStyle): Float {
            return when (style) {
                FrameStyle.INWARD_CURVED -> 0.5f
                FrameStyle.GEAR_EDGE -> 0.6f
                FrameStyle.OGEE_EDGE -> 0.4f
                FrameStyle.DOUBLE_OGEE_EDGE -> 0.35f
                FrameStyle.DUPONT_EDGE -> 0.5f
                FrameStyle.COVE -> 0.6f
                FrameStyle.FRENCH_COVE -> 0.8f
                FrameStyle.DUPONT_BULLNOSE -> 0.6f
                FrameStyle.OGEE_COVE_EDGE -> 0.4f
                else -> 0f
            }
        }
}