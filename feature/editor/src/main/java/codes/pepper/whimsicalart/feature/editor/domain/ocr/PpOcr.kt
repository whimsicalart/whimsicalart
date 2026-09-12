package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure, unit-testable PP-OCR pipeline pieces. Nothing here touches the MNN
 * runtime: image resizing rules, the NCHW float packing for a bitmap crop, the
 * DB (detect, don't segment boxes) post-processing geometry, CTC decoding and
 * the textline-orientation classifier argmax all follow PaddleOCR's conventions
 * so they can be pinned by Robolectric tests without a native engine.
 */
object PpOcr {

    /** PP-OCRv5 mobile detector emits its probability map at this fraction. */
    const val DET_MAP_STRIDE = 4
    const val DET_MAX_SIDE = 960
    const val REC_TARGET_HEIGHT = 48
    const val REC_MAX_WIDTH = 320
    const val CLS_HEIGHT = 48
    const val CLS_WIDTH = 192

    // ------------------------------------------------------------------ resize

    /** PP-OCR DetResizeForTest: longest side ~960, both sides rounded to [stride]. */
    fun detResize(srcWidth: Int, srcHeight: Int, maxSide: Int = DET_MAX_SIDE, stride: Int = 32): ResizeSpec {
        if (srcWidth <= 0 || srcHeight <= 0) return ResizeSpec(1, 1)
        val longest = max(srcWidth, srcHeight)
        val ratio = if (longest <= maxSide) 1f else maxSide.toFloat() / longest
        val w = ((srcWidth * ratio).roundToInt().coerceAtLeast(stride) / stride) * stride
        val h = ((srcHeight * ratio).roundToInt().coerceAtLeast(stride) / stride) * stride
        return ResizeSpec(w, h)
    }

    /** PP-OCR RecResizeImg: fixed height, width keeps aspect ratio, padded to 4. */
    fun recResize(
        srcWidth: Int,
        srcHeight: Int,
        targetHeight: Int = REC_TARGET_HEIGHT,
        maxWidth: Int = REC_MAX_WIDTH,
        padTo: Int = 4
    ): ResizeSpec {
        if (srcWidth <= 0 || srcHeight <= 0) return ResizeSpec(maxWidth.coerceAtLeast(padTo), targetHeight)
        val ratio = targetHeight.toFloat() / srcHeight
        var w = (srcWidth * ratio).roundToInt()
        w = (w / padTo).coerceAtLeast(1) * padTo
        w = w.coerceAtMost(maxWidth)
        return ResizeSpec(w, targetHeight)
    }

    /** Target of an image resize. */
    data class ResizeSpec(val width: Int, val height: Int)

    /**
     * Pack an ARGB crop into a NCHW float tensor. [fromW]x[fromH] bilinear to
     * [toW]x[toH], values / 255 in PaddleOCR's RGB order (index 0 = the red
     * channel plane, 1 = green, 2 = blue). Pure over raw pixel ints.
     */
    fun toNchwRgb(
        argb: IntArray,
        fromW: Int,
        fromH: Int,
        toW: Int,
        toH: Int
    ): FloatArray {
        require(argb.size >= fromW * fromH) { "src buffer too small" }
        val out = FloatArray(toW * toH * 3)
        var o = 0
        for (y in 0 until toH) {
            val sy = (y + 0.5f) * fromH / toH - 0.5f
            val sy0 = sy.coerceAtLeast(0f).toInt().coerceAtMost(fromH - 1)
            val sy1 = (sy0 + 1).coerceAtMost(fromH - 1)
            val fy = sy - sy0
            for (x in 0 until toW) {
                val sx = (x + 0.5f) * fromW / toW - 0.5f
                val sx0 = sx.coerceAtLeast(0f).toInt().coerceAtMost(fromW - 1)
                val sx1 = (sx0 + 1).coerceAtMost(fromW - 1)
                val fx = sx - sx0
                val i00 = argb[sy0 * fromW + sx0]
                val i10 = argb[sy0 * fromW + sx1]
                val i01 = argb[sy1 * fromW + sx0]
                val i11 = argb[sy1 * fromW + sx1]
                val r00 = (i00 shr 16) and 0xff
                val g00 = (i00 shr 8) and 0xff
                val b00 = i00 and 0xff
                val r10 = (i10 shr 16) and 0xff
                val g10 = (i10 shr 8) and 0xff
                val b10 = i10 and 0xff
                val r01 = (i01 shr 16) and 0xff
                val g01 = (i01 shr 8) and 0xff
                val b01 = i01 and 0xff
                val r11 = (i11 shr 16) and 0xff
                val g11 = (i11 shr 8) and 0xff
                val b11 = i11 and 0xff
                out[o] = ((r00 * (1 - fx) + r10 * fx) * (1 - fy) + (r01 * (1 - fx) + r11 * fx) * fy) / 255f
                out[o + toW * toH] =
                    ((g00 * (1 - fx) + g10 * fx) * (1 - fy) + (g01 * (1 - fx) + g11 * fx) * fy) / 255f
                out[o + 2 * toW * toH] =
                    ((b00 * (1 - fx) + b10 * fx) * (1 - fy) + (b01 * (1 - fx) + b11 * fx) * fy) / 255f
                o++
            }
        }
        return out
    }

    // ------------------------------------------------------------ DB postprocess

    /** Axis-aligned oriented rectangle from the rotating-calipers loop. */
    data class OrientedBox(
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        /** Radians of the box's long-ish side against +x. */
        val radians: Float
    )

    /**
     * DBNet mask → text boxes. Thresholds the probability map, labels 4-connected
     * components, fits each with a minimum-area rectangle and inflates it by the
     * DBNet unclip rule (offset = area * ratio / perimeter). Returns quad corners
     * in MAP space (multiply by DET_MAP_STRIDE for the detector input image).
     */
    fun findTextBoxes(
        probs: FloatArray,
        mapWidth: Int,
        mapHeight: Int,
        threshold: Float = 0.3f,
        unclipRatio: Float = 1.6f,
        minAreaPx: Float = 9f
    ): List<Quad> {
        if (mapWidth <= 0 || mapHeight <= 0 || probs.size < mapWidth * mapHeight) return emptyList()

        val mask = ByteArray(mapWidth * mapHeight)
        for (i in mask.indices) if (probs[i] > threshold) mask[i] = 1

        // 4-connected component labelling (breadth-first over the byte mask).
        val label = IntArray(mask.size) { -1 }
        var next = 0
        val members = HashMap<Int, MutableList<PointF>>()
        for (start in mask.indices) {
            if (mask[start] == 1.toByte() || label[start] >= 0) continue
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            label[start] = next
            while (stack.isNotEmpty()) {
                val p = stack.removeLast()
                val x = p % mapWidth
                val y = p / mapWidth
                fun push(qx: Int, qy: Int) {
                    if (qx < 0 || qy < 0 || qx >= mapWidth || qy >= mapHeight) return
                    val q = qy * mapWidth + qx
                    if (label[q] >= 0 || mask[q] != 1.toByte()) return
                    label[q] = next
                    stack.addLast(q)
                }
                push(x - 1, y); push(x + 1, y); push(x, y - 1); push(x, y + 1)
            }
            next++
        }
        for (i in label.indices) {
            if (label[i] >= 0) members.getOrPut(label[i]) { ArrayList() }.add(PointF((i % mapWidth).toFloat(), (i / mapWidth).toFloat()))
        }

        val boxes = ArrayList<Quad>()
        for (pts in members.values) {
            if (pts.size < 3) continue
            val hull = convexHull(pts)
            if (hull.size < 3) continue
            val rect = minAreaRect(hull)
            val area = rect.width * rect.height
            if (area < minAreaPx) continue
            val perimeter = 2 * (rect.width + rect.height)
            if (perimeter <= 0f) continue
            val offset = area * unclipRatio / perimeter
            boxes.add(boxCorners(rect, inflate = offset))
        }
        return boxes
    }

    /** One detected candidate as four float corners (map space, cyclic order). */
    class Quad internal constructor(val corners: List<PointF>) {
        fun boundingBox(): RectF {
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (p in corners) {
                l = minOf(l, p.x); t = minOf(t, p.y)
                r = maxOf(r, p.x); b = maxOf(b, p.y)
            }
            return RectF(l, t, r, b)
        }

        fun centroid(): PointF {
            var x = 0f
            var y = 0f
            for (p in corners) {
                x += p.x
                y += p.y
            }
            return PointF(x / corners.size, y / corners.size)
        }
    }

    // ------------------------------------------------------------------ geometry

    /** Convex hull (Graham scan) of the given points; inclusive of collinear ends is fine. */
    fun convexHull(points: List<PointF>): List<PointF> {
        val p = points.sortedWith(compareBy({ it.x }, { it.y }))
        if (p.size <= 3) return p.toList()
        fun cross(o: PointF, a: PointF, b: PointF): Float =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<PointF>()
        for (pt in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], pt) <= 0) lower.removeAt(lower.size - 1)
            lower.add(pt)
        }
        val upper = ArrayList<PointF>()
        for (i in p.indices.reversed()) {
            val pt = p[i]
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], pt) <= 0) upper.removeAt(upper.size - 1)
            upper.add(pt)
        }
        val hull = lower.dropLast(1) + upper.dropLast(1)
        // Keep the hull at least a triangle; the caller filters degenerate boxes.
        return hull.ifEmpty { p }
    }

    /** Minimum-area rectangle over a convex polygon via rotating calipers. */
    fun minAreaRect(hull: List<PointF>): OrientedBox {
        var bestArea = Float.MAX_VALUE
        var best = OrientedBox(0f, 0f, 0f, 0f, 0f)
        val n = hull.size
        for (i in 0 until n) {
            val a = hull[i]
            val b = hull[(i + 1) % n]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = sqrt(dx * dx + dy * dy)
            if (len <= 0f) continue
            val ex = dx / len
            val ey = dy / len
            val nx = -ey
            val ny = ex
            var minE = Float.MAX_VALUE
            var maxE = -Float.MAX_VALUE
            var minN = Float.MAX_VALUE
            var maxN = -Float.MAX_VALUE
            for (v in hull) {
                val e = (v.x - a.x) * ex + (v.y - a.y) * ey
                val nn = (v.x - a.x) * nx + (v.y - a.y) * ny
                minE = minOf(minE, e); maxE = maxOf(maxE, e)
                minN = minOf(minN, nn); maxN = maxOf(maxN, nn)
            }
            val area = (maxE - minE) * (maxN - minN)
            if (area < bestArea) {
                bestArea = area
                val cx0 = (minE + maxE) / 2
                val cy0 = (minN + maxN) / 2
                val cx = a.x + cx0 * ex + cy0 * nx
                val cy = a.y + cx0 * ey + cy0 * ny
                best = OrientedBox(cx, cy, maxE - minE, maxN - minN, atan2(ey, ex))
            }
        }
        return best
    }

    /** The four corners of an oriented box, inflated by [inflate] map px on all sides. */
    fun boxCorners(rect: OrientedBox, inflate: Float = 0f): Quad {
        val w = rect.width + 2 * inflate
        val h = rect.height + 2 * inflate
        val ex = cos(rect.radians)
        val ey = sin(rect.radians)
        val nx = -ey
        val ny = ex
        val hw = w / 2
        val hh = h / 2
        val corners = arrayListOf(
            PointF(rect.centerX + hw * ex + hh * nx, rect.centerY + hw * ey + hh * ny),
            PointF(rect.centerX + hw * ex - hh * nx, rect.centerY + hw * ey - hh * ny),
            PointF(rect.centerX - hw * ex - hh * nx, rect.centerY - hw * ey - hh * ny),
            PointF(rect.centerX - hw * ex + hh * nx, rect.centerY - hw * ey + hh * ny)
        )
        return Quad(corners)
    }

    // ---------------------------------------------------------------------- CTC

    data class DecodedText(val text: String, val confidence: Float)

    /**
     * Greedy CTC decode: per timestep argmax, drop blanks (index 0), collapse
     * repeats. [vocab] maps model class indices to characters with index 0 = blank.
     */
    fun ctcDecode(
        vocab: List<String>,
        probs: FloatArray,
        timesteps: Int,
        classes: Int
    ): DecodedText {
        if (timesteps <= 0 || classes <= 0) return DecodedText("", 0f)
        val sb = StringBuilder()
        var prev = -1
        var sum = 0f
        for (t in 0 until timesteps) {
            var best = 0
            var bestV = Float.NEGATIVE_INFINITY
            for (c in 0 until classes) {
                val v = probs[t * classes + c]
                if (v > bestV) {
                    bestV = v
                    best = c
                }
            }
            sum += bestV
            if (best != 0 && best != prev) {
                sb.append(vocab.getOrElse(best) { "" })
            }
            prev = best
        }
        return DecodedText(sb.toString(), if (timesteps > 0) sum / timesteps else 0f)
    }

    /** Picks the decoded rec candidate with the higher mean confidence. */
    fun betterRec(a: DecodedText, b: DecodedText): DecodedText =
        if (b.confidence > a.confidence) b else a

    /** Textline orientation head: class 1 = 180° (else upright). */
    fun rotationDegreesFromCls(probs: FloatArray, classes: Int): Int {
        if (classes < 2 || probs.size < classes) return 0
        if (probs[1] > probs[0]) return 180
        return 0
    }

    /** Loaded vocabulary: prepends the CTC blank at index 0. */
    fun vocabWithBlank(dictLines: List<String>): List<String> = listOf("") + dictLines
}