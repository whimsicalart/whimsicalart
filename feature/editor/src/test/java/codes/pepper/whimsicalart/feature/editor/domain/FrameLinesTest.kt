package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Frame decorative-line geometry tests (docs/effects_reference.md → *Frames* →
 * *Line*): the strips must follow the shape's TRUE normal offset off the window
 * boundary at constant thickness and spacing (not the axis-scalar margins that
 * deformed oval / heart / rounded corners), start after a leading gap of one
 * [FrameTier.lineDistance], and apply their [FrameTier.lineDistance] /
 * [FrameTier.lineThickness] fractions at a third of the short edge.
 */
@RunWith(RobolectricTestRunner::class)
class FrameLinesTest {

    private val stripedOval = FrameTier(
        style = FrameStyle.OVAL,
        thickness = 0.3f,
        color = Color.BLACK,
        lines = 3,
        lineDistance = 0.15f,
        lineThickness = 0.1f
    )

    private val stripedHeart = FrameTier(
        style = FrameStyle.HEART,
        thickness = 0.3f,
        color = Color.BLACK,
        lines = 3,
        lineDistance = 0.15f,
        lineThickness = 0.1f
    )

    /** Renders a frame tier over a white canvas at the given size. */
    private fun renderTier(size: Int, tier: FrameTier): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }.also {
            FrameOverlayRenderer.drawFrames(it, listOf(FrameLayer(inner = tier)))
        }

    /** Colours along a walked ray, run-length encoded (1 = frame BLACK, 0 = white). */
    private fun colorRuns(
        bmp: Bitmap,
        startX: Float,
        startY: Float,
        dirX: Float,
        dirY: Float,
        maxU: Int
    ): List<Int> {
        val runs = ArrayList<Int>()
        var prev = -1
        var run = 0
        var lastX = Int.MIN_VALUE
        var lastY = Int.MIN_VALUE
        for (u in 1..maxU) {
            val px = (startX + u * dirX).roundToInt()
            val py = (startY + u * dirY).roundToInt()
            if (px == lastX && py == lastY) continue
            lastX = px
            lastY = py
            if (px < 0 || px >= bmp.width || py < 0 || py >= bmp.height) break
            val c = if (bmp.getPixel(px, py) == Color.BLACK) 1 else 0
            if (c != prev) {
                if (prev != -1) runs.add(run)
                run = 1
                prev = c
            } else {
                run++
            }
        }
        if (prev != -1) runs.add(run)
        return runs
    }

    /** Inward surface normal of an ellipse at parameter [theta] (a = half-width, b = half-height). */
    private fun ellipseInwardNormal(a: Float, b: Float, theta: Double): Pair<Float, Float> {
        val nx = -b * cos(theta)
        val ny = -a * sin(theta)
        val len = sqrt(nx * nx + ny * ny)
        return (nx / len).toFloat() to (ny / len).toFloat()
    }

    /** Sampled inward normals around a closed boundary polygon, oriented toward its centroid. */
    private fun polygonNormals(polygon: FloatArray, step: Int): List<Triple<Float, Float, Pair<Float, Float>>> {
        val n = polygon.size / 2
        var cx = 0f
        var cy = 0f
        for (i in 0 until n) {
            cx += polygon[2 * i]
            cy += polygon[2 * i + 1]
        }
        cx /= n
        cy /= n
        val out = ArrayList<Triple<Float, Float, Pair<Float, Float>>>()
        var i = 0
        while (i < n) {
            val j = (i + 1) % n
            val mx = (polygon[2 * i] + polygon[2 * j]) / 2f
            val my = (polygon[2 * i + 1] + polygon[2 * j + 1]) / 2f
            var dx = -(polygon[2 * j + 1] - polygon[2 * i + 1])
            var dy = polygon[2 * j] - polygon[2 * i]
            val len = sqrt(dx * dx + dy * dy)
            if (len > 1e-6f) {
                dx /= len
                dy /= len
                if (dx * (cx - mx) + dy * (cy - my) < 0f) {
                    dx = -dx
                    dy = -dy
                }
                out.add(Triple(mx, my, dx to dy))
            }
            i += step
        }
        return out
    }

    private fun assertSameStripPattern(profiles: List<List<Int>>, label: String, tolerance: Int = 1) {
        assertTrue("$label needs a reference profile", profiles.isNotEmpty())
        val ref = profiles.maxByOrNull { run ->
            run.count { it > 1 }
        } ?: error("empty profile")
        assertTrue("$label: reference must resolve to the expected 3 strips, got $ref", ref.count { it > 1 } >= 3)
        for (p in profiles) {
            val short = min(p.size, ref.size)
            assertTrue(
                "$label: run count $p vs reference $ref",
                abs(p.size - ref.size) <= 2
            )
            for (k in 0 until short) {
                assertTrue(
                    "$label: run $k width ${p[k]} vs reference ${ref[k]}",
                    abs(p[k] - ref[k]) <= tolerance
                )
            }
        }
    }

/**
     * Walk offsets (u indices) at which each black strip starts — the leading
     * edge of every strip along the surface normal at the sampled point.
     */
    private fun stripStarts(
        bmp: Bitmap,
        startX: Float,
        startY: Float,
        dirX: Float,
        dirY: Float,
        maxU: Int
    ): List<Int> {
        val starts = ArrayList<Int>()
        var black = false
        var lastX = Int.MIN_VALUE
        var lastY = Int.MIN_VALUE
        for (u in 1..maxU) {
            val px = (startX + u * dirX).roundToInt()
            val py = (startY + u * dirY).roundToInt()
            if (px == lastX && py == lastY) continue
            lastX = px
            lastY = py
            if (px < 0 || px >= bmp.width || py < 0 || py >= bmp.height) break
            val nowBlack = bmp.getPixel(px, py) == Color.BLACK
            if (nowBlack && !black) starts.add(u)
            black = nowBlack
        }
        return starts
    }

    /**
     * The walked margins where the three strips should begin: strip `k` covers
     * `[d + k·(d+l), +l)` out from the boundary, so its leading edge lands at
     * walk offset `d + k·(d+l)` (rounded to the rasterized pixel).
     */
    private fun expectedStripStarts(size: Int): List<Int> {
        val d = 0.15f * size / 3f
        val l = 0.1f * size / 3f
        return listOf(d, d + (d + l), d + 2 * (d + l)).map { it.roundToInt() }
    }

    private fun assertStripStarts(
        samples: List<List<Int>>,
        expected: List<Int>,
        label: String,
        innerTolerance: Int = 3
    ) {
        val valid = samples.filter { it.size == expected.size }.map { it to expected }
        assertTrue(
            "$label: need at least 6 samples resolving all $expected strips, got ${valid.size}",
            valid.size >= 6
        )
        for ((starts, exp) in valid) {
            for (k in starts.indices) {
                val tight = k < starts.lastIndex
                val bound = if (tight) 3 else maxOf(innerTolerance, 3)
                assertTrue(
                    "$label: strip ${k + 1} at $starts vs expected $exp",
                    abs(starts[k] - exp[k]) <= bound
                )
            }
        }
    }

    @Test
    fun `oval line strips keep a constant width and gap along the surface normal at preview and full size`() {
        for (size in listOf(48, 96)) {
            val bmp = renderTier(size, stripedOval)
            val short = size
            val t = 0.3f * short / 2f
            val ratio = (short - 2 * t) / short
            val a = size * ratio / 2f
            val b = size * ratio / 2f
            val cx = size / 2f
            val cy = size / 2f
            val maxU = size * 3 / 10
            val starts = (0 until 8).map { k ->
                val theta = k * Math.PI * 2.0 / 8.0
                val bx = (cx + a * cos(theta)).toFloat()
                val by = (cy + b * sin(theta)).toFloat()
                val (nx, ny) = ellipseInwardNormal(a, b, theta)
                stripStarts(bmp, bx, by, nx, ny, maxU)
            }
            assertStripStarts(starts, expectedStripStarts(size), "oval @ $size")
            val widths = (0 until 8).map { k ->
                val theta = k * Math.PI * 2.0 / 8.0
                val bx = (cx + a * cos(theta)).toFloat()
                val by = (cy + b * sin(theta)).toFloat()
                val (nx, ny) = ellipseInwardNormal(a, b, theta)
                colorRuns(bmp, bx, by, nx, ny, maxU)
            }
            assertSameStripPattern(widths, "oval @ $size", tolerance = 3)
        }
    }

    @Test
    fun `ellipse line strips are uniform on a non-circular oval where axis margins would deform them`() {
        val w = 96
        val h = 64
        val short = min(w, h)
        val t = 0.3f * short / 2f
        val ratio = (short - 2 * t) / short
        val a = w * ratio / 2f
        val b = h * ratio / 2f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }.also {
            FrameOverlayRenderer.drawFrames(it, listOf(FrameLayer(inner = stripedOval)))
        }
        val cx = w / 2f
        val cy = h / 2f
        val maxU = short * 3 / 10
        val starts = (0 until 8).map { k ->
            val theta = k * Math.PI * 2.0 / 8.0
            val bx = (cx + a * cos(theta)).toFloat()
            val by = (cy + b * sin(theta)).toFloat()
            val (nx, ny) = ellipseInwardNormal(a, b, theta)
            stripStarts(bmp, bx, by, nx, ny, maxU)
        }
        assertStripStarts(starts, expectedStripStarts(short), "non-circular oval ${w}x$h")
    }

    @Test
    fun `heart line strips keep a constant width and gap along the surface normal at preview and full size`() {
        for (size in listOf(80, 128)) {
            val bmp = renderTier(size, stripedHeart)
            val g = FrameGeometry(FrameStyle.HEART, size, size, 0.3f * size / 2f)
            val expected = expectedStripStarts(size)
            val starts = polygonNormals(g.polygon, 15).mapNotNull { (mx, my, dir) ->
                val s = stripStarts(bmp, mx, my, dir.first, dir.second, size * 3 / 10)
                if (s.size == expected.size) s else null
            }
            assertStripStarts(starts, expected, "heart @ $size", innerTolerance = 10)
        }
    }

@Test
    fun `oval interior margins equal the true normal offset at the axis-aligned cardinal points`() {
        // At the ellipse's top and right the inward normal is axis-aligned, so
        // the pixel distance transform must reproduce it exactly (an axis-scalar
        // horizontal span margin collapses near the top of a tall oval).
        for ((w, h) in listOf(48 to 48, 96 to 64)) {
            val short = min(w, h)
            val t = 0.3f * short / 2f
            val ratio = (short - 2 * t) / short
            val a = w * ratio / 2f
            val b = h * ratio / 2f
            val g = FrameGeometry(FrameStyle.OVAL, w, h, t)
            val cx = w / 2f
            val cy = h / 2f
            for (theta in listOf(0.0, Math.PI / 2.0)) {
                val bx = (cx + a * cos(theta)).toFloat()
                val by = (cy + b * sin(theta)).toFloat()
                val (nx, ny) = ellipseInwardNormal(a, b, theta)
                for (u in 1..6) {
                    val px = (bx + u * nx).roundToInt()
                    val py = (by + u * ny).roundToInt()
                    val m = g.interiorMargin(px, py)
                    assertTrue("$w x $h: θ=$theta u=$u margin $m must be inside the window", m >= 0f)
                    assertTrue("$w x $h: θ=$theta u=$u margin $m must equal the step $u (±1)", abs(m - u) <= 1f)
                }
            }
        }
    }

    @Test
    fun `the first inner line strip starts after a leading gap of one line distance`() {
        val tier = FrameTier(
            style = FrameStyle.SOLID,
            thickness = 0.3f,
            color = Color.BLACK,
            lines = 3,
            lineDistance = 0.15f,
            lineThickness = 0.1f
        )
        val size = 60
        val bmp = renderTier(size, tier)
        val y = 30
        val t = 9
        val d = 3f
        val l = 2f
        for (margin in listOf(0, 1, 2)) {
            assertEquals("margin $margin before the first strip stays white", Color.WHITE, bmp.getPixel(t + margin, y))
        }
        assertEquals("first strip starts at margin = lineDistance", Color.BLACK, bmp.getPixel(t + d.toInt() + 0, y))
        assertEquals("gap after the first strip starts at margin d+l", Color.WHITE, bmp.getPixel(t + (d + l).toInt(), y))
        assertEquals("second strip at margin d+(d+l)", Color.BLACK, bmp.getPixel(t + (d + (d + l)).toInt(), y))
        assertEquals("centre stays white", Color.WHITE, bmp.getPixel(size / 2, y))
    }

    @Test
    fun `line thickness scales at a third of the short edge so the sliders afford finer control`() {
        val size = 60
        val maxU = size * 3 / 10
        val bmp = renderTier(size, stripedOval)
        val short = size
        val t = 0.3f * short / 2f
        val ratio = (short - 2 * t) / short
        val a = size * ratio / 2f
        val b = size * ratio / 2f
        val cx = size / 2f
        val cy = size / 2f
        val (nx, ny) = ellipseInwardNormal(a, b, 0.0)
        val runs = colorRuns(bmp, cx + a, cy, nx, ny, maxU)
        assertTrue("no strips painted", runs.size >= 3)
        // lineThickness 10% of the short edge must materialise as 10%/3 ≈ 2 px,
        // not the 0.1×60 = 6 px the un-scaled fraction would give.
        assertEquals("strip width is the lineThickness/3 of the short edge", 2, runs[1], 1)
    }

    @Test
    fun `inner line strips reproduce the plain frame corners as hard mitred angles`() {
        // A solid frame window is a sharp rectangle; its decorative strips are
        // concentric offsets of that rectangle and so must carry the FOUR square
        // corners (the exact-distance annulus of an axis-aligned corner is the
        // 45° miter band, not an arc).
        for (size in listOf(60, 120)) {
            val tier = FrameTier(
                style = FrameStyle.SOLID,
                thickness = 0.3f,
                color = Color.BLACK,
                lines = 3,
                lineDistance = 0.15f,
                lineThickness = 0.1f
            )
            val bmp = renderTier(size, tier)
            val t = (0.3f * size / 2f).toInt()
            // Peel the first strip's offset centre from the window corner.
            val d = 0.15f * size / 3f
            val l = 0.1f * size / 3f
            val c0 = d + l / 2f
            // Along the corner diagonal the first strip runs at |(px,py) corner L| ~ C0.
            val diag = ArrayList<Int>()
            for (u in 0 until size) if (bmp.getPixel(u, u) == Color.BLACK) diag.add(u)
            val clusters = ArrayList<List<Int>>()
            var cur = ArrayList<Int>()
            for (i in diag) {
                if (cur.isNotEmpty() && i - cur.last() > 1) {
                    clusters.add(cur); cur = ArrayList()
                }
                cur.add(i)
            }
            if (cur.isNotEmpty()) clusters.add(cur)
            // The band diagonal, then three strip runs at increasing corner distance.
            assertTrue(
                "$size: diagonal must carry band + 3 strip clusters, got $clusters",
                clusters.size >= 4
            )
            val stripClusters = clusters.drop(1)
            for (k in 0 until min(3, stripClusters.size)) {
                val c = d + k * (d + l) + l / 2f
                val expect = (t + c).roundToInt()
                assertTrue(
                    "$size: strip $k centre ≈ delegate $expect, got ${stripClusters[k]}",
                    abs(stripClusters[k].getOrNull(stripClusters[k].size / 2)!! - expect) <= 2
                )
            }
            // The miter corner: the corner centre pixel sits on the strip (the
            // 45° miter band fills the square corner), while the diagonal-ish
            // pixel one half-line before the corner's inner edge stays in the gap.
            val cx = t + c0.roundToInt()
            val half = l / 2f
            assertTrue(
                "$size: ($cx,$cx) miter-corner pixel must be on the strip",
                bmp.getPixel(cx, cx) == Color.BLACK
            )
            assertTrue(
                "$size: (${cx - 2 * half.roundToInt() - 1},${cx + 2 * half.roundToInt() + 1}) leading-gap pixel must stay white",
                bmp.getPixel(cx - 2 * half.roundToInt() - 1, cx + 2 * half.roundToInt() + 1) == Color.WHITE
            )
        }
    }

    @Test
    fun `outer line strips follow the band contour so cogged teeth carry into the strips`() {
        // The outer tier's strips are concentric offsets of the band's OUTER
        // border contour. A cogged contour alternates between teeth (reach 1.35×
        // thickness) and valleys (0.8×), so the innermost strip must sit further
        // out at a tooth column than at a valley column — a rect-parallel strip
        // (anchored to the photo edge) would sit at the same distance at both.
        for (size in listOf(96, 192)) {
            val m = size / 2
            val tier = FrameTier(
                style = FrameStyle.COGGED,
                thickness = 0.3f,
                color = Color.BLACK,
                lines = 2,
                lineDistance = 0.1f,
                lineThickness = 0.1f
            )
            val w = size + 2 * m
            val h = size + 2 * m
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }.also {
                FrameOverlayRenderer.drawFrames(it, listOf(FrameLayer(outer = tier)), insetX = m, insetY = m)
            }
            val t = 0.3f * size / 2f
            val period = size / 3
            fun stripRunStarts(cPrime: Int): List<Int> {
                val px = m + cPrime
                val out = ArrayList<Int>()
                var black = false
                for (rel in -m until 0) {
                    val py = m + rel
                    val now = bmp.getPixel(px, py) == Color.BLACK
                    if (now && !black) out.add(rel)
                    black = now
                }
                return out
            }
            // Tooth columns: sin(2π·3·c'/95) ≥ 0 for c' ≲ size/6; valley columns
            // at the midpoint of the negative half-wave c' ≈ size/4.
            val tooth = stripRunStarts(size / 6)
            val valley = stripRunStarts(size / 4)
            assertTrue("$size: tooth column must resolve band + strip runs, got $tooth", tooth.size >= 2)
            assertTrue("$size: valley column must resolve band + strip runs, got $valley", valley.size >= 2)
            // The strip just above the band (the innermost decorative strip) is
            // the second-to-last black run when scanning downward.
            val toothStrip = tooth[tooth.size - 2]
            val valleyStrip = valley[valley.size - 2]
            val delta = abs(toothStrip - valleyStrip)
            val expect = ((1.35f - 0.8f) * t).roundToInt()
            assertTrue(
                "$size: contour-carrying Δ $delta must be ≈ $expect (±2), tooth strip@$toothStrip vs valley strip@$valleyStrip",
                abs(delta - expect) <= 2
            )
        }
    }

    private fun assertEquals(message: String, expected: Int, actual: Int, tolerance: Int) {
        assertTrue("$message: expected $expected ±$tolerance, was $actual", abs(expected - actual) <= tolerance)
    }
}