package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import codes.pepper.whimsicalart.feature.editor.domain.removal.DiffusionInpainter
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A single pen stroke rasterised with max-alpha accumulation: [bitmap] covers
 * the stroke's bounding region on a `width`×`height` frame and belongs at
 * ([offsetX], [offsetY]) in that frame. Both the committed fold and the live
 * overlay draw this same raster, so soft strokes can never darken differently
 * where they cross themselves.
 */
internal data class PenRaster(
    val bitmap: Bitmap,
    val offsetX: Int,
    val offsetY: Int
)

/**
 * Committed-pass renderer for the brush overlays — the Mosaic, Blur, Pen and
 * Removal strokes plus the auto-suggested privacy (censor) regions — extracted
 * from [BitmapRenderer] so each overlay drawing family owns its raster logic.
 * Every pass mirrors the live Compose preview's branch behaviour byte-for-byte
 * (see the per-brush docs in [drawPenStroke] and [applyBrushBlur]); the
 * incremental removal resolves each stroke against the already-baked canvas.
 * Draw math is unchanged from `BitmapRenderer.drawStrokes`/`drawPrivacyRegions`.
 */
internal object StrokeOverlayRenderer {

    /**
     * Extra padding around a removal stroke's footprint the incremental fill
     * region adds, so the antialiased mask edge keeps its outer ring and the
     * onion-peel has known pixels to grow from.
     */
    private const val HALO_MARGIN_PX = 2

    internal fun drawStrokes(canvas: Bitmap, strokes: List<StrokeLayer>) {
        if (strokes.isEmpty()) return
        val regular = ArrayList<StrokeLayer>()
        val removals = ArrayList<StrokeLayer>()
        for (stroke in strokes) {
            if (stroke.type == StrokeType.REMOVAL) removals.add(stroke) else regular.add(stroke)
        }
        // One source snapshot shared by every mosaic stroke: drawing a pixel
        // mosaic needs the pre-mosaic pixels, and `applyPixelMosaic` used to
        // `canvas.copy()` a content-identical snapshot on every call — the same
        // copy repeated per stroke, whole-canvas, per fold. The shared copy is
        // taken lazily at the FIRST mosaic stroke (so earlier pen/blur strokes
        // stay baked in, exactly as before) and re-used by the rest; it is
        // recycled once the loop is done.
        var mosaicSrc: Bitmap? = null
        try {
            for (stroke in regular) {
                when (stroke.type) {
                    StrokeType.PEN -> drawPenStroke(canvas, stroke)
                    StrokeType.MOSAIC -> {
                        val src = mosaicSrc ?: canvas.copy(Bitmap.Config.ARGB_8888, false).also { mosaicSrc = it }
                        applyPixelMosaic(canvas, stroke, src)
                    }
                    StrokeType.BLUR -> applyBrushBlur(canvas, stroke)
                    StrokeType.REMOVAL -> Unit
                }
            }
        } finally {
            mosaicSrc?.recycle()
        }
        if (removals.isNotEmpty()) {
            applyRemoval(canvas, removals)
        }
    }

    /**
     * Content-aware object removal. Each removal stroke is resolved incrementally
     * against the canvas that already has the EARLIER strokes baked in: a stroke's
     * mask is drawn on its own, then [DiffusionInpainter.fillRegion] overwrites
     * only that stroke's bounding region (plus the surrounding known-pixel halo the
     * fill needs to grow from). Previously resolved areas are never revisited, so
     * the per-commit cost scales with the newly brushed area — not the whole
     * canvas, and not the whole union mask. For mutually isolated objects this is
     * byte-identical to a one-pass union inpaint (each hole propagates from its own
     * untouched boundary); a learned model can later replace [DiffusionInpainter]
     * behind the
     * [codes.pepper.whimsicalart.feature.editor.domain.removal.InpaintSegmenter]
     * interface without touching this path.
     */
    private fun applyRemoval(canvas: Bitmap, strokes: List<StrokeLayer>) {
        if (strokes.isEmpty()) return
        val w = canvas.width
        val h = canvas.height
        val pixels = IntArray(w * h)
        canvas.getPixels(pixels, 0, w, 0, 0, w, h)
        val mask = IntArray(w * h)
        // Mask circles are stamped pixel-exactly (no Canvas) so the mask is
        // deterministic and unit-testable: a pixel is masked when its centre
        // lies within the brush radius — the same footprint drawCircle's
        // antialiased ring (alpha >= MASK_THRESHOLD) would include.
        for (stroke in strokes) {
            val radiusPx = (stroke.brushSize * w).toFloat()
            java.util.Arrays.fill(mask, 0)
            val bounds = IntArray(4).also { it[0] = w; it[1] = h; it[2] = -1; it[3] = -1 }
            sampleAlong(stroke.points, radiusPx, w, h) { px, py ->
                stampMaskCircle(mask, px, py, radiusPx, bounds, w, h)
            }
            if (bounds[2] < 0) continue
            // The fill is bounded by the stroke's footprint (+ a halo so the
            // peel has known pixels to grow from).
            val x0 = (bounds[0] - HALO_MARGIN_PX).coerceAtLeast(0)
            val y0 = (bounds[1] - HALO_MARGIN_PX).coerceAtLeast(0)
            val x1 = (bounds[2] + 1 + HALO_MARGIN_PX).coerceAtMost(w)
            val y1 = (bounds[3] + 1 + HALO_MARGIN_PX).coerceAtMost(h)
            DiffusionInpainter.fillRegion(pixels, mask, w, h, x0, y0, x1, y1)
        }
        // Copy the resolved pixels back (createBitmap + drawBitmap rather than
        // setPixels, which is a no-op under Robolectric).
        val resolved = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(canvas).drawBitmap(resolved, 0f, 0f, null)
        resolved.recycle()
    }

    /**
     * Stamps one hard-edged circle of [radius] px around ([cx],[cy]) into [mask]
     * (row-major ARGB, alpha ≥ [MASK_THRESHOLD] marks the hole), extending
     * [bounds] (minX, minY, maxX, maxY) to the actual pixel footprint so the
     * incremental fill can be limited to it.
     */
    private fun stampMaskCircle(
        mask: IntArray,
        cx: Float,
        cy: Float,
        radius: Float,
        bounds: IntArray,
        w: Int,
        h: Int
    ) {
        val r2 = radius * radius
        val x0 = (cx - radius).toInt().coerceAtLeast(0)
        val x1 = (cx + radius).toInt().coerceAtMost(w - 1)
        val y0 = (cy - radius).toInt().coerceAtLeast(0)
        val y1 = (cy + radius).toInt().coerceAtMost(h - 1)
        for (y in y0..y1) {
            var idx = y * w + x0
            for (x in x0..x1) {
                val dx = x + 0.5f - cx
                val dy = y + 0.5f - cy
                if (dx * dx + dy * dy <= r2) {
                    mask[idx] = Color.WHITE
                    if (x < bounds[0]) bounds[0] = x
                    if (y < bounds[1]) bounds[1] = y
                    if (x > bounds[2]) bounds[2] = x
                    if (y > bounds[3]) bounds[3] = y
                }
                idx++
            }
        }
    }

    /**
     * Renders a pen stroke as a radial per-brush alpha profile over the brushed
     * ribbon, mirroring the live preview's `drawPenSegment` branches
     * (docs/effect_stack.md → Pen stroke-shaping):
     * - SOLID / RAINBOW: a hardness-gradient stamp — the colour's alpha carries
     *   the pen opacity and hardness 1 = solid across the whole size, 0 = only a
     *   solid centre fading out to the size. RAINBOW cycles hue along the stroke
     *   and ignores the picked colour, exactly like the preview's `rainbowColor`.
     * - GLOW: a wide faint halo + solid core + white-hot centre — no hardness.
     * - NEON: a faint outer halo + solid core — no hardness.
     * Per-pixel CPU pass like the mosaic/blur stamps, so it stays unit-testable
     * without shader support. The stroke is first rasterised on its own with
     * **max-alpha accumulation** (overlapping stamps never compound — a soft
     * stroke does not darken where it crosses itself, and the live overlay draws
     * the SAME raster via [penStrokeRaster], so live preview == committed fold),
     * then source-over composited onto the canvas.
     */
    private fun drawPenStroke(canvas: Bitmap, stroke: StrokeLayer) {
        val raster = penStrokeRaster(canvas.width, canvas.height, stroke) ?: return
        compositePenRaster(canvas, raster)
    }

    private fun compositePenRaster(canvas: Bitmap, raster: PenRaster) {
        val w = canvas.width
        val h = canvas.height
        val bmp = raster.bitmap
        val x0 = raster.offsetX
        val y0 = raster.offsetY
        if (x0 >= w || y0 >= h) return
        val bw = bmp.width
        val bh = bmp.height
        val cw = minOf(bw, w - x0)
        val ch = minOf(bh, h - y0)
        if (cw <= 0 || ch <= 0) return

        // One batched read of each source (the raster region + the canvas region
        // it covers) and one write-back: the float source-over chain is unchanged
        // from the per-pixel loop (same skip gates, same float order), only the
        // 3 JNI calls per pixel become 3 calls in total.
        val srcPixels = IntArray(bw * bh)
        bmp.getPixels(srcPixels, 0, bw, 0, 0, bw, bh)
        val dstPixels = IntArray(cw * ch)
        canvas.getPixels(dstPixels, 0, cw, x0, y0, cw, ch)

        for (i in dstPixels.indices) {
            val src = srcPixels[i]
            val sa = Color.alpha(src) / 255f
            if (sa <= 0.001f) continue
            val dst = dstPixels[i]
            val da = Color.alpha(dst) / 255f
            val outA = sa + da * (1f - sa)
            if (outA <= 0f) continue
            dstPixels[i] = Color.argb(
                (outA * 255f).roundToInt().coerceIn(0, 255),
                ((Color.red(src) * sa + Color.red(dst) * da * (1f - sa)) / outA).roundToInt().coerceIn(0, 255),
                ((Color.green(src) * sa + Color.green(dst) * da * (1f - sa)) / outA).roundToInt().coerceIn(0, 255),
                ((Color.blue(src) * sa + Color.blue(dst) * da * (1f - sa)) / outA).roundToInt().coerceIn(0, 255)
            )
        }

        // One region setPixels write-back — the same primitive the pen raster
        // write-out and BokehProcessor rely on ("getPixel/setPixels ... do
        // rasterize reliably on this Robolectric build"), so the composited
        // region stays byte-verifiable under Robolectric.
        canvas.setPixels(dstPixels, 0, cw, x0, y0, cw, ch)
    }

    /**
     * Rasterises a single pen stroke into a transparent bitmap, accumulating the
     * **maximum** per-brush alpha coverage per pixel (the strongest stamp wins,
     * carrying its own colour — GLOW's white-hot centre and RAINBOW's hue shift
     * change colour across the band). The result is exactly what [drawPenStroke]
     * composites onto the fold, and exactly what the live overlay draws through
     * [codes.pepper.whimsicalart.feature.editor.ui.pen.PenStrokeOverlay], so a
     * self-crossing soft stroke cannot darken differently in preview than in the
     * committed result. The bitmap is the stroke's bounding region, placed at
     * ([PenRaster.offsetX], [PenRaster.offsetY]) on `width`×`height`; null when
     * the brush paints nothing.
     */
    internal fun penStrokeRaster(width: Int, height: Int, stroke: StrokeLayer): PenRaster? {
        val w = width
        val h = height
        val radiusPx = (stroke.brushSize * w / 2f).coerceAtLeast(1f)
        val argb = stroke.color ?: Color.BLACK
        val baseAlpha = Color.alpha(argb) / 255f
        val hardness = stroke.hardness.coerceIn(0f, 1f)
        if (baseAlpha <= 0f) return null

        val centres = ArrayList<Pair<Float, Float>>()
        sampleAlong(stroke.points, radiusPx * 0.5f, w, h) { px, py ->
            centres.add(px to py)
        }
        if (centres.isEmpty()) return null

        // The halo bands of GLOW / NEON reach well beyond the core size, so both
        // the bounding box and the pixel loop must scale with the widest band.
        val maxFactor = when (stroke.brush) {
            PenBrushKind.GLOW -> 7f
            PenBrushKind.NEON -> 3.5f
            else -> 1f
        }
        val spreadPx = radiusPx * maxFactor
        val ri = ceil(spreadPx).toInt()
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for ((px, py) in centres) {
            val cx = px.roundToInt()
            val cy = py.roundToInt()
            minX = minOf(minX, cx - ri)
            maxX = maxOf(maxX, cx + ri)
            minY = minOf(minY, cy - ri)
            maxY = maxOf(maxY, cy + ri)
        }
        val x0 = minX.coerceAtLeast(0)
        val y0 = minY.coerceAtLeast(0)
        val x1 = maxX.coerceAtMost(w - 1)
        val y1 = maxY.coerceAtMost(h - 1)
        if (x1 < x0 || y1 < y0) return null
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1

        // max-alpha accumulation with per-pixel winning colour: each pixel keeps
        // the strongest stroke coverage (and its source colour, since GLOW's
        // white-hot centre and RAINBOW's hue shift change colour across the band).
        val alpha = FloatArray(bw * bh)
        val sourceR = IntArray(bw * bh)
        val sourceG = IntArray(bw * bh)
        val sourceB = IntArray(bw * bh)
        val spread2 = spreadPx * spreadPx
        var hueIndex = 0
        for ((px, py) in centres) {
            val thisHueIndex = hueIndex++
            for (dy in -ri..ri) {
                val yy = py.toInt() + dy
                if (yy < y0 || yy > y1) continue
                val dyy = yy - py
                for (dx in -ri..ri) {
                    val xx = px.toInt() + dx
                    if (xx < x0 || xx > x1) continue
                    val d2 = dyy * dyy + (xx - px) * (xx - px)
                    if (d2 > spread2) continue
                    val t = kotlin.math.sqrt(d2.toFloat()) / radiusPx
                    val a: Float
                    val col: Int
                    when (stroke.brush) {
                        PenBrushKind.SOLID -> {
                            a = baseAlpha * (1f - (1f - hardness) * t.coerceAtMost(1f))
                            if (a <= 0f) continue
                            col = argb
                        }
                        PenBrushKind.RAINBOW -> {
                            a = baseAlpha * (1f - (1f - hardness) * t.coerceAtMost(1f))
                            if (a <= 0f) continue
                            col = rainbowHueColor(thisHueIndex)
                        }
                        PenBrushKind.GLOW -> {
                            when {
                                t <= 0.6f -> {
                                    a = 0.5f * baseAlpha
                                    col = Color.WHITE
                                }
                                t <= 4f -> {
                                    a = baseAlpha
                                    col = argb
                                }
                                t <= 7f -> {
                                    a = 0.25f * baseAlpha
                                    col = argb
                                }
                                else -> continue
                            }
                        }
                        PenBrushKind.NEON -> {
                            when {
                                t <= 1f -> {
                                    a = baseAlpha
                                    col = argb
                                }
                                t <= 3.5f -> {
                                    a = 0.35f * baseAlpha
                                    col = argb
                                }
                                else -> continue
                            }
                        }
                    }
                    val idx = (yy - y0) * bw + (xx - x0)
                    if (a > alpha[idx]) {
                        alpha[idx] = a
                        sourceR[idx] = Color.red(col)
                        sourceG[idx] = Color.green(col)
                        sourceB[idx] = Color.blue(col)
                    }
                }
            }
        }

        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        // One batched write-out instead of a setPixel per raster pixel
        // (~100k+ JNI calls per full-width GLOW stroke); skipped (alpha<=0.001)
        // pixels stay 0 == TRANSPARENT, exactly like a fresh bitmap.
        val outPixels = IntArray(bw * bh)
        for (i in outPixels.indices) {
            val sa = alpha[i]
            if (sa <= 0.001f) continue
            outPixels[i] = Color.argb(
                (sa * 255f).roundToInt().coerceIn(0, 255),
                sourceR[i],
                sourceG[i],
                sourceB[i]
            )
        }
        out.setPixels(outPixels, 0, bw, 0, 0, bw, bh)
        return PenRaster(out, x0, y0)
    }

    /** The rainbow hue cycle — 18°/per sample stamp (docs → Pen controls). */
    private fun rainbowHueColor(index: Int): Int =
        android.graphics.Color.HSVToColor(floatArrayOf((index * 18) % 360f, 0.9f, 1f))

    /**
     * Real pixel-mosaic: reads the shared [src] snapshot and repaints
     * rectangular cells with the block-average colour along every point of the
     * stroke, so the covered region genuinely pixelates instead of being
     * scribbled over. [src] is the pre-mosaic copy hoisted in [drawStrokes] and
     * shared by every mosaic stroke — the caller owns its lifetime.
     */
    private fun applyPixelMosaic(canvas: Bitmap, stroke: StrokeLayer, src: Bitmap) {
        val w = canvas.width
        val h = canvas.height
        // One batched read of the pre-mosaic snapshot: every cell then averages
        // straight from this array instead of cell² per-pixel getPixel JNI calls.
        val srcPixels = IntArray(src.width * src.height)
        src.getPixels(srcPixels, 0, src.width, 0, 0, src.width, src.height)
        val cell = (stroke.brushSize * w).roundToInt().coerceAtLeast((minOf(w, h) / 140f).coerceAtLeast(1f).roundToInt())
        val alpha = (stroke.opacity * 255f).roundToInt().coerceIn(0, 255)
        val draw = android.graphics.Canvas(canvas)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }

        // Long coverage = the per-cell O(cell²) window scan dominates, so replace
        // it with a per-stroke integral image: every cell average becomes O(1) and
        // byte-identical (integer sums are grouping-exact, same floor division).
        // The O(w·h) prepass pays for itself only when the stroke's total
        // window-scan work (~2·pathLen·cell, see [useMosaicIntegral]) exceeds it.
        val integral = if (useMosaicIntegral(w, h, cell, stroke.points)) {
            MosaicIntegral(srcPixels, src.width, src.height)
        } else {
            null
        }
        sampleAlong(stroke.points, cell / 2f, w, h) { px, py ->
            val x0 = (px - cell / 2f).roundToInt().coerceIn(0, (w - cell).coerceAtLeast(0))
            val y0 = (py - cell / 2f).roundToInt().coerceIn(0, (h - cell).coerceAtLeast(0))
            paint.color = if (integral != null) {
                integralAverage(integral, src.width, src.height, x0, y0, cell, cell)
            } else {
                blockAverage(srcPixels, src.width, src.height, x0, y0, cell, cell)
            }
            draw.drawRect(x0.toFloat(), y0.toFloat(), (x0 + cell).toFloat(), (y0 + cell).toFloat(), paint)
        }
    }

    /**
     * Whether the per-stroke integral-image prepass pays for this mosaic stroke.
     * The naive path scans a cell² window per stamp (~2·pathLen/cell stamps) —
     * total work ≈ 2·pathLen·cell — while the integral swaps that for one O(w·h)
     * prepass plus O(1) per cell. Only a long trail (or a fat brush) makes the
     * swap worthwhile, and only when the prepass footprint fits in memory
     * ([MAX_INTEGRAL_PIXELS], ~24 MB transient at the cap); a tiny stamp would
     * pay the whole prepass for a fraction of the frame. Internal for tests.
     */
    internal fun useMosaicIntegral(w: Int, h: Int, cell: Int, points: List<Pair<Float, Float>>): Boolean {
        val framePixels = w.toLong() * h
        if (framePixels > MAX_INTEGRAL_PIXELS) return false
        if (points.isEmpty()) return false
        // Stamped region estimate: contiguous window-scan work ≈ 2·pathLen·cell
        // (adjacent stamps overlap, so the union is smaller — the SCAN count is
        // what matters and that is what the integral replaces).
        var pathLenPx = 0.0
        var ax = points.first().first * w
        var ay = points.first().second * h
        for (i in 1 until points.size) {
            val bx = points[i].first * w
            val by = points[i].second * h
            pathLenPx += kotlin.math.hypot(bx - ax, by - ay)
            ax = bx; ay = by
        }
        val stamps = if (points.size == 1) 1L else ((pathLenPx / (cell / 2f)).roundToLong().coerceAtLeast(1))
        // Each stamp's window is clipped to the frame (cells sweeping off the
        // edge shrink), so the scan work bound uses the clipped cell area.
        val stampW = minOf(cell.toLong(), w.toLong())
        val stampH = minOf(cell.toLong(), h.toLong())
        val scanWork = stamps * stampW * stampH
        // Break even: continuous stamping must re-scan the whole frame before the
        // prepass amortizes the same cost (a single full-frame cell is a wash).
        return scanWork > framePixels
    }

    /** Integral prepass is capped at this many source pixels (~24 MB transient
     * of R/G/B Long planes at the cap); larger frames keep the per-window scan. */
    private const val MAX_INTEGRAL_PIXELS = 1_000_000L

    /**
     * Pixel-blocks every auto-suggested privacy region (normalized [0..1] rects,
     * e.g. faces detected in the Mosaic tool). Unlike the freeform brush it fills
     * the whole rect on a cell grid so private regions are uniformly masked.
     */
    internal fun drawPrivacyRegions(canvas: Bitmap, regions: List<RectF>) {
        if (regions.isEmpty()) return
        val w = canvas.width
        val h = canvas.height
        // Snapshot straight into an IntArray limited to the union bbox of the
        // pixel rects — the old path `canvas.copy()`ed a whole bitmap (used only
        // as a pixel source) and read every pixel of it.
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        val rects = ArrayList<IntArray>(regions.size)
        for (region in regions) {
            val left = (region.left * w).roundToInt().coerceIn(0, w - 1)
            val top = (region.top * h).roundToInt().coerceIn(0, h - 1)
            val right = (region.right * w).roundToInt().coerceIn(left + 1, w)
            val bottom = (region.bottom * h).roundToInt().coerceIn(top + 1, h)
            rects.add(intArrayOf(left, top, right, bottom))
            minX = minOf(minX, left); minY = minOf(minY, top)
            maxX = maxOf(maxX, right); maxY = maxOf(maxY, bottom)
        }
        val bboxW = (maxX - minX).coerceAtLeast(1)
        val bboxH = (maxY - minY).coerceAtLeast(1)
        val srcPixels = IntArray(bboxW * bboxH)
        canvas.getPixels(srcPixels, 0, bboxW, minX, minY, bboxW, bboxH)

        val draw = android.graphics.Canvas(canvas)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for ((left, top, right, bottom) in rects) {
            val cell = ((right - left) / 3f).roundToInt().coerceAtLeast((minOf(w, h) / 140f).coerceAtLeast(1f).roundToInt())
            var y = top
            while (y < bottom) {
                var x = left
                while (x < right) {
                    val cw = cell.coerceAtMost(right - x)
                    val ch = cell.coerceAtMost(bottom - y)
                    paint.color = blockAverage(srcPixels, bboxW, bboxH, x - minX, y - minY, cw, ch)
                    draw.drawRect(x.toFloat(), y.toFloat(), (x + cw).toFloat(), (y + ch).toFloat(), paint)
                    x += cw
                }
                y += cell
            }
        }
    }

    /**
     * Real brush blur: for every pixel inside each disc of the stroke it replaces
     * the value with a box-blur of the source, blended by the stroke opacity.
     * Operates on the pixel array and writes back once for reliability/performance.
     */
    private fun applyBrushBlur(canvas: Bitmap, stroke: StrokeLayer) {
        val w = canvas.width
        val h = canvas.height
        val src = IntArray(w * h)
        canvas.getPixels(src, 0, w, 0, 0, w, h)
        val radiusPx = (stroke.brushSize * w).roundToInt().coerceAtLeast(2)
        // One effect, two scales: the blur reach is a fraction of the live image
        // (the fixed 20px cap skewed long-armed brushes — 4% of a small preview
        // vs 0.5% of full — so it now caps at a fraction of the short edge
        // instead, ~(radiusPx/3) until it would exceed the user's brush reach).
        val blurRadius = brushBlurRadius(minOf(w, h), radiusPx)
        val opacity = stroke.opacity.coerceIn(0f, 1f)

        // The per-pixel box average would re-scan a (2r+1)^2 window for every
        // pixel it overwrites (~302k boxes across a long stroke) — compute it
        // once per stroke as a separable two-pass box blur instead: identical
        // clipped-window sums, O(w*h) pre-pass + O(1) per pixel.
        val avg = boxBlurImage(src, w, h, blurRadius)

        sampleAlong(stroke.points, radiusPx.toFloat(), w, h) { px, py ->
            val cx = px.roundToInt()
            val cy = py.roundToInt()
            val x0 = (cx - radiusPx).coerceAtLeast(0)
            val y0 = (cy - radiusPx).coerceAtLeast(0)
            val x1 = (cx + radiusPx).coerceAtMost(w - 1)
            val y1 = (cy + radiusPx).coerceAtMost(h - 1)
            val r2 = radiusPx * radiusPx
            for (y in y0..y1) {
                for (x in x0..x1) {
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx + dy * dy > r2) continue
                    val idx = y * w + x
                    src[idx] = blend(src[idx], avg[idx], opacity)
                }
            }
        }
        canvas.setPixels(src, 0, w, 0, 0, w, h)
    }

    /**
     * Separable two-pass box blur used by [applyBrushBlur]: pass A builds per-row
     * sliding-window channel sums (clipped at the image edges), pass B slides a
     * vertical window over those row sums to get each pixel's exact (2r+1)^2 box
     * average with the same clipped bounds and floor division as the previous
     * per-pixel box average, so the painted output is byte-identical.
     */
    internal fun boxBlurImage(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return src.copyOf()
        val len = w * h
        val rowR = IntArray(len)
        val rowG = IntArray(len)
        val rowB = IntArray(len)
        for (y in 0 until h) {
            val base = y * w
            var sr = 0L; var sg = 0L; var sb = 0L
            val hi = minOf(w - 1, radius)
            for (x0 in 0..hi) {
                val c = src[base + x0]
                sr += (c shr 16) and 0xff; sg += (c shr 8) and 0xff; sb += c and 0xff
            }
            rowR[base] = sr.toInt(); rowG[base] = sg.toInt(); rowB[base] = sb.toInt()
            for (x in 1 until w) {
                val xl = x - radius - 1
                if (xl >= 0) {
                    val c = src[base + xl]
                    sr -= (c shr 16) and 0xff; sg -= (c shr 8) and 0xff; sb -= c and 0xff
                }
                val xr = x + radius
                if (xr < w) {
                    val c = src[base + xr]
                    sr += (c shr 16) and 0xff; sg += (c shr 8) and 0xff; sb += c and 0xff
                }
                rowR[base + x] = sr.toInt(); rowG[base + x] = sg.toInt(); rowB[base + x] = sb.toInt()
            }
        }
        val out = IntArray(len)
        for (x in 0 until w) {
            var sr = 0L; var sg = 0L; var sb = 0L
            val hi = minOf(h - 1, radius)
            for (y0 in 0..hi) {
                val idx = y0 * w + x
                sr += rowR[idx]; sg += rowG[idx]; sb += rowB[idx]
            }
            val cols = (minOf(w - 1, x + radius) - maxOf(0, x - radius) + 1)
            var rows = hi + 1
            for (y in 0 until h) {
                val count = rows * cols
                out[y * w + x] = Color.rgb((sr / count).toInt(), (sg / count).toInt(), (sb / count).toInt())
                val yt = y - radius
                if (yt >= 0) {
                    val idx = yt * w + x
                    sr -= rowR[idx]; sg -= rowG[idx]; sb -= rowB[idx]
                    rows--
                }
                val yb = y + radius + 1
                if (yb < h) {
                    val idx = yb * w + x
                    sr += rowR[idx]; sg += rowG[idx]; sb += rowB[idx]
                    rows++
                }
            }
        }
        return out
    }

    /**
     * Alpha-aware separable box average — identical to calling
     * [VignetteRenderer.boxAverageAt] for every pixel, but computed in O(w·h)
     * via the same two-pass sliding-window pattern as [boxBlurImage].
     *
     * Fully transparent pixels are excluded from the channel sums and from the
     * divisor; if every pixel in the clipped window is transparent the source
     * centre value is returned (matching `boxAverageAt`'s all-transparent
     * fallback). Floor (integer) division reproduces the naive per-pixel scan
     * exactly.
     */
    internal fun boxAverageImage(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return src.copyOf()
        val len = w * h

        // Pass A — horizontal sliding sums of R/G/B/A and visible-pixel count.
        val hR = IntArray(len)
        val hG = IntArray(len)
        val hB = IntArray(len)
        val hA = IntArray(len)
        val hCount = IntArray(len)

        for (y in 0 until h) {
            val base = y * w
            var sr = 0L; var sg = 0L; var sb = 0L; var sa = 0L; var sc = 0L
            val hi = minOf(w - 1, radius)
            for (x0 in 0..hi) {
                val c = src[base + x0]
                val alpha = Color.alpha(c)
                if (alpha != 0) {
                    sr += (c shr 16) and 0xff; sg += (c shr 8) and 0xff; sb += c and 0xff
                    sa += alpha; sc++
                }
            }
            hR[base] = sr.toInt(); hG[base] = sg.toInt(); hB[base] = sb.toInt()
            hA[base] = sa.toInt(); hCount[base] = sc.toInt()

            for (x in 1 until w) {
                val xl = x - radius - 1
                if (xl >= 0) {
                    val c = src[base + xl]
                    val alpha = Color.alpha(c)
                    if (alpha != 0) {
                        sr -= (c shr 16) and 0xff; sg -= (c shr 8) and 0xff; sb -= c and 0xff
                        sa -= alpha; sc--
                    }
                }
                val xr = x + radius
                if (xr < w) {
                    val c = src[base + xr]
                    val alpha = Color.alpha(c)
                    if (alpha != 0) {
                        sr += (c shr 16) and 0xff; sg += (c shr 8) and 0xff; sb += c and 0xff
                        sa += alpha; sc++
                    }
                }
                hR[base + x] = sr.toInt(); hG[base + x] = sg.toInt(); hB[base + x] = sb.toInt()
                hA[base + x] = sa.toInt(); hCount[base + x] = sc.toInt()
            }
        }

        // Pass B — vertical accumulation → per-pixel clipped box average.
        val out = IntArray(len)
        for (x in 0 until w) {
            var sr = 0L; var sg = 0L; var sb = 0L; var sa = 0L; var sc = 0L
            val hi = minOf(h - 1, radius)
            for (y0 in 0..hi) {
                val idx = y0 * w + x
                sr += hR[idx]; sg += hG[idx]; sb += hB[idx]
                sa += hA[idx]; sc += hCount[idx]
            }
            for (y in 0 until h) {
                val idx = y * w + x
                out[idx] = if (sc == 0L) {
                    src[idx]
                } else {
                    Color.argb(
                        (sa / sc).toInt(), (sr / sc).toInt(),
                        (sg / sc).toInt(), (sb / sc).toInt()
                    )
                }
                val yt = y - radius
                if (yt >= 0) {
                    val i = yt * w + x
                    sr -= hR[i]; sg -= hG[i]; sb -= hB[i]; sa -= hA[i]; sc -= hCount[i]
                }
                val yb = y + radius + 1
                if (yb < h) {
                    val i = yb * w + x
                    sr += hR[i]; sg += hG[i]; sb += hB[i]; sa += hA[i]; sc += hCount[i]
                }
            }
        }
        return out
    }

    /**
     * Dimension-agnostic blur-brush reach for [applyBrushBlur]: one third of
     * the brush disc ([radiusPx]) until it would exceed a fraction of the live
     * image's short edge (the historical fixed 20px cap skewed — 4% of a small
     * preview vs 0.5% of full). `internal` for tests.
     */
    internal fun brushBlurRadius(minDim: Int, radiusPx: Int): Int =
        (radiusPx / 3f).roundToInt()
            .coerceIn(1, (minDim / 25f).roundToInt().coerceAtLeast(1))

    private fun sampleAlong(
        points: List<Pair<Float, Float>>,
        spacingPx: Float,
        w: Int,
        h: Int,
        block: (px: Float, py: Float) -> Unit
    ) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            block(points[0].first * w, points[0].second * h)
            return
        }
        val spacing = spacingPx.coerceAtLeast(1f)
        for (i in 0 until points.size - 1) {
            val ax = points[i].first * w
            val ay = points[i].second * h
            val bx = points[i + 1].first * w
            val by = points[i + 1].second * h
            val dist = kotlin.math.hypot(bx - ax, by - ay)
            val steps = (dist / spacing).roundToInt().coerceAtLeast(1)
            for (s in 0..steps) {
                block(ax + (bx - ax) * s / steps, ay + (by - ay) * s / steps)
            }
        }
    }

    /**
     * The per-cell average used by the Mosaic brush (and the privacy-region
     * fill): clipped at the frame edges, transparent pixels included, floor
     * division. `internal` so the integral-image parity test can compare it
     * against [integralAverage] per cell.
     */
    internal fun blockAverage(src: IntArray, srcW: Int, srcH: Int, x0: Int, y0: Int, w: Int, h: Int): Int {
        val width = (x0 + w).coerceAtMost(srcW)
        val height = (y0 + h).coerceAtMost(srcH)
        var r = 0L; var g = 0L; var b = 0L
        var count = 0L
        for (y in y0 until height) {
            var i = y * srcW + x0
            val end = i + (width - x0)
            while (i < end) {
                val c = src[i]
                r += Color.red(c); g += Color.green(c); b += Color.blue(c)
                i++
                count++
            }
        }
        if (count == 0L) return src[x0.coerceAtMost(srcW - 1) + y0.coerceAtMost(srcH - 1) * srcW]
        return Color.rgb(
            (r / count).toInt(), (g / count).toInt(), (b / count).toInt()
        )
    }

    /**
     * O(1) equivalent of [blockAverage] over a rectangle, using the per-stroke
     * [MosaicIntegral]. The sum is exact (integer, no float errors) and the
     * floor division is the same Long division, so the colour is byte-identical
     * to the per-window scan for the same clipped [x0],[y0],[w],[h] box.
     */
    internal fun integralAverage(
        integral: MosaicIntegral,
        srcW: Int,
        srcH: Int,
        x0: Int,
        y0: Int,
        w: Int,
        h: Int
    ): Int {
        val x1 = (x0 + w).coerceAtMost(srcW)
        val y1 = (y0 + h).coerceAtMost(srcH)
        val count = (x1 - x0).toLong() * (y1 - y0)
        if (count == 0L) return integral.at(x0.coerceAtMost(srcW - 1), y0.coerceAtMost(srcH - 1))
        val r = integral.sum(0, x0, y0, x1, y1)
        val g = integral.sum(1, x0, y0, x1, y1)
        val b = integral.sum(2, x0, y0, x1, y1)
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun blend(from: Int, into: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(from),
            (Color.red(from) * (1f - a) + Color.red(into) * a).roundToInt(),
            (Color.green(from) * (1f - a) + Color.green(into) * a).roundToInt(),
            (Color.blue(from) * (1f - a) + Color.blue(into) * a).roundToInt()
        )
    }
}

/**
 * R/G/B integral image built once per mosaic stroke from the pre-mosaic
 * snapshot (see [StrokeOverlayRenderer.applyPixelMosaic]); lets every cell
 * average be answered in O(1) instead of a cell² window scan. Each channel is
 * stored as a `LongArray` (`(srcW+1)*(srcH+1)` with a 1-based origin) so
 * full-frame region sums cannot overflow the 32-bit Int the per-pixel scan is
 * bounded by. Flat 1-based indexing: index(y, x) = y * pw + x for pw = srcW + 1.
 */
internal class MosaicIntegral(srcPixels: IntArray, private val srcW: Int, private val srcH: Int) {
    private val pw = srcW + 1
    private val r = LongArray(pw * (srcH + 1))
    private val g = LongArray(pw * (srcH + 1))
    private val b = LongArray(pw * (srcH + 1))

    init {
        for (y in 1..srcH) {
            val srcRow = (y - 1) * srcW
            var rowR = 0L; var rowG = 0L; var rowB = 0L
            for (x in 1..srcW) {
                val c = srcPixels[srcRow + x - 1]
                rowR += Color.red(c); rowG += Color.green(c); rowB += Color.blue(c)
                val idx = y * pw + x
                val up = (y - 1) * pw + x
                r[idx] = rowR + r[up]
                g[idx] = rowG + g[up]
                b[idx] = rowB + b[up]
            }
        }
    }

    /** Sum of channel [ch] over the half-open rectangle [x0,x1) × [y0,y1). */
    fun sum(ch: Int, x0: Int, y0: Int, x1: Int, y1: Int): Long {
        val a = y0 * pw + x0
        val b0 = y0 * pw + x1
        val c0 = y1 * pw + x0
        val d = y1 * pw + x1
        return when (ch) {
            0 -> r[d] - r[c0] - r[b0] + r[a]
            1 -> g[d] - g[c0] - g[b0] + g[a]
            else -> b[d] - b[c0] - b[b0] + b[a]
        }
    }

    /** The source pixel at ([x],[y]) — the empty-rectangle fallback of [blockAverage]. */
    fun at(x: Int, y: Int): Int {
        // A 1×1 box difference against the inclusive cumulative sums
        // (index(cy, cx) holds the sum over rows 1..cy, cols 1..cx).
        val d = (y + 1) * pw + (x + 1)
        val c = y * pw + (x + 1)
        val bb = (y + 1) * pw + x
        val a = y * pw + x
        val rr = r[d] - r[c] - r[bb] + r[a]
        val gg = g[d] - g[c] - g[bb] + g[a]
        val bb0 = b[d] - b[c] - b[bb] + b[a]
        return Color.rgb(rr.toInt(), gg.toInt(), bb0.toInt())
    }
}