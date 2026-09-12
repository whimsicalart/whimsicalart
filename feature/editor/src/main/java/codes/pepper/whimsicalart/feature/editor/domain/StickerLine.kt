package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import codes.pepper.whimsicalart.feature.stickers.domain.StickerLineStyle
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Upper bound (px per side) on the bitmap scanned for a sticker silhouette
 * contour — larger art is downsampled first so the outline trace stays cheap
 * regardless of sticker size.
 */
const val STICKER_CONTOUR_MAX_DIM = 256

/**
 * Returns a closed [android.graphics.Path] tracing the sticker's **silhouette**
 * — the outline of the non-transparent region of [alpha] — at [outW]×[outH] px.
 * The art is scanned at most [STICKER_CONTOUR_MAX_DIM] per side (a larger
 * [alpha] is downsampled first, then the traced geometry is scaled back up), so
 * the fold and the live preview both use the same contour at bounded cost.
 *
 * The trace walks the boundary between opaque and transparent pixels: every
 * lattice edge separating an opaque pixel from a transparent one (or the bitmap
 * rim) becomes a directed segment, and the segments are chained into closed
 * loops (one per outline, interior holes included). With no diagonal or
 * ambiguous cases it hugs the art's exact pixel outline, so the stroked line
 * reads as sitting right on the sticker's edge (spec: *Stickers* → *Line*, and
 * "Rendering-in-identically" for preview/fold parity).
 */
fun stickerSilhouettePath(alpha: Bitmap, outW: Int, outH: Int): Path {
    require(alpha.width > 0 && alpha.height > 0)
    val scale = min(1f, STICKER_CONTOUR_MAX_DIM.toFloat() / maxOf(alpha.width, alpha.height))
    val scan: Bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            alpha,
            (alpha.width * scale).roundToInt().coerceAtLeast(1),
            (alpha.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    } else {
        alpha
    }
    val xScale = if (scan.width > 0) outW.toFloat() / scan.width else 1f
    val yScale = if (scan.height > 0) outH.toFloat() / scan.height else 1f
    return traceSilhouette(scan, xScale, yScale)
}

/**
 * Builds the paint used to stroke a sticker's silhouette line. A [NONE] style
 * (or a non-positive width) means no line and yields null. [DashPathEffect]
 * breaks SOLID into on/off runs of `2 × width`, and DOTTED into short rounded
 * blobs of `width` separated by `3 × width`.
 */
fun stickerLinePaint(
    style: StickerLineStyle,
    widthPx: Float,
    color: Int,
    alpha: Int
): Paint? {
    if (style == StickerLineStyle.NONE || widthPx <= 0f) return null
    return Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.alpha = alpha.coerceIn(0, 255)
        this.style = Paint.Style.STROKE
        strokeWidth = widthPx
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        when (style) {
            StickerLineStyle.DASHED ->
                pathEffect = DashPathEffect(floatArrayOf(widthPx * 2f, widthPx * 2f), 0f)
            StickerLineStyle.DOTTED ->
                pathEffect = DashPathEffect(floatArrayOf(widthPx, widthPx * 3f), 0f)
            StickerLineStyle.SOLID, StickerLineStyle.NONE -> Unit
        }
    }
}

private fun traceSilhouette(scan: Bitmap, xScale: Float, yScale: Float): Path {
    val w = scan.width
    val h = scan.height
    val path = Path()
    if (w == 0 || h == 0) return path

    val alpha = IntArray(w * h)
    scan.getPixels(alpha, 0, w, 0, 0, w, h)
    fun filled(px: Int, py: Int): Boolean =
        px in 0 until w && py in 0 until h && (alpha[py * w + px] ushr 24) != 0

    // Walk the boundary between opaque and transparent pixels: every lattice edge
    // that separates an opaque pixel from a transparent one (or the bitmap rim)
    // becomes one directed segment. Because each such edge is adjacent to exactly
    // one opaque pixel, segments are unique and form closed loops (one per outline,
    // including interior holes) with no diagonal/ambiguous cases — the silhouette
    // hugs the art's exact pixel outline.
    data class Vertex(val x: Int, val y: Int)
    data class Edge(val from: Vertex, val to: Vertex)
    val edgeList = ArrayList<Edge>()
    fun add(from: Vertex, to: Vertex) {
        edgeList.add(Edge(from, to))
    }
    for (px in 0 until w) {
        for (py in 0 until h) {
            if (!filled(px, py)) continue
            // Top side: opaque below, transparent above (or the rim).
            if (py == 0 || !filled(px, py - 1)) {
                add(Vertex(px, py), Vertex(px + 1, py))
            }
            // Bottom side.
            if (py == h - 1 || !filled(px, py + 1)) {
                add(Vertex(px + 1, py + 1), Vertex(px, py + 1))
            }
            // Left side.
            if (px == 0 || !filled(px - 1, py)) {
                add(Vertex(px, py + 1), Vertex(px, py))
            }
            // Right side.
            if (px == w - 1 || !filled(px + 1, py)) {
                add(Vertex(px + 1, py), Vertex(px + 1, py + 1))
            }
        }
    }

    val next = HashMap<Long, Vertex>(edgeList.size)
    for (e in edgeList) {
        next[(e.from.x.toLong() shl 32) or (e.from.y.toLong() and 0xFFFFFFFFL)] = e.to
    }
    if (next.isEmpty()) return path

    fun key(v: Vertex): Long = (v.x.toLong() shl 32) or (v.y.toLong() and 0xFFFFFFFFL)
    fun px(v: Vertex): Pair<Float, Float> = (v.x * xScale) to (v.y * yScale)

    val visited = HashSet<Long>()
    for (start in next.keys.sorted()) {
        if (start in visited) continue
        val loop = ArrayList<Vertex>()
        var cur = next[start]
        var guard = 0
        while (cur != null && guard <= edgeList.size + 2) {
            loop.add(cur)
            val k = key(cur)
            if (k == start) break
            visited.add(k)
            cur = next[k]
            guard++
        }
        if (loop.size < 2) continue
        var first = true
        for (v in loop) {
            val (x, y) = px(v)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        visited.addAll(loop.map { key(it) })
    }
    return path
}