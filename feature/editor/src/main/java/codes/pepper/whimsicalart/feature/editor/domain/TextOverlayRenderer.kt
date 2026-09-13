package codes.pepper.whimsicalart.feature.editor.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.res.ResourcesCompat
import codes.pepper.whimsicalart.core.domain.UniformFit
import codes.pepper.whimsicalart.feature.editor.R
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehProcessor
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The ROUNDED background shape's corner radius as a fraction of the TEXT SIZE —
 * the single rule shared by the live preview ([TextOverlayLayer]'s
 * `backgroundModifier` clip) and this committed rasteriser's `drawRoundRect`, so
 * both always render identical corner curvature (docs/effects_reference.md →
 * *Text*). `rect`/`oval`/`pill` are likewise size-relative; this is the ROUNDED
 * shape's proportion.
 */
internal const val ROUNDED_BACKGROUND_CORNER_FACTOR = 0.25f

/**
 * Corner radius for a ROUNDED text background in px: 25% of the text size — the
 * expression the committed rasteriser hands to `drawRoundRect`. The live preview
 * clips with the same rule in dp, so its px radius equals this for every text
 * size and density (see [ROUNDED_BACKGROUND_CORNER_FACTOR]).
 */
internal fun roundedBackgroundRadiusPx(textSizePx: Float): Float =
    textSizePx * ROUNDED_BACKGROUND_CORNER_FACTOR

/**
 * Committed-pass renderer for the Text tool (docs/effects_reference.md → *Text*),
 * extracted from [BitmapRenderer] so each overlay drawing family owns its raster
 * logic. Mirrors the live Compose preview's [.drawTexts] block layout exactly
 * (same wrap math, background shape, outline feathering and shadow), so the
 * committed render never diverges from what the user sees while editing.
 * Draw math is unchanged from `BitmapRenderer.drawTexts`.
 */
internal object TextOverlayRenderer {

    internal fun drawTexts(context: Context, bitmap: Bitmap, texts: List<TextItem>) {
        if (texts.isEmpty()) return
        val draw = android.graphics.Canvas(bitmap)
        for (t in texts) {
            val refW = t.referenceWidth.takeIf { it > 0 } ?: bitmap.width
            val refH = t.referenceHeight.takeIf { it > 0 } ?: bitmap.height
            val fit = UniformFit.of(bitmap.width, bitmap.height, refW, refH)
            // The text is laid out inside the FITTED reference frame (the same
            // frame the item was captured against), then centred in the
            // destination with the fit offset — so an anchored item keeps its
            // on-image wrap box and position when an earlier effect changes the
            // base (identity when the reference is unset/absent) and all of the
            // block math below stays a pure function of (w,h,cx,cy).
            val w = fit.measureX(1f, refW)
            val h = fit.measureY(1f, refH)
            val lox = fit.offsetX
            val loy = fit.offsetY

            // The rasterised text BLOCK is cached per item, keyed by every
            // parameter that changes its pixels — content, colour, font, size,
            // style, alignment, background, shadow, outline — plus the target
            // size (textSize and the block geometry scale with w). A fold reuses
            // the whole canvas for the same text, so every unchanged item
            // recomposites its once-rasterised block bitmap instead of
            // re-measuring + re-wrapping + re-rasterising the glyphs on record
            // (the fold runs the overlay, then the stamp). Position and rotation
            // are NOT in the key and the block is placed from the CURRENT centre
            // (see the offset in [CachedTextBlock]) — transforms applied at
            // composite time, so a moved/rotated but otherwise identical item
            // still hits AND lands on its live position.
            val textKey = textBlockKey(t, w, h)
            val entry = textBlockCache.remove(textKey)
            if (entry != null) {
                textBlockCache[textKey] = entry
                val cx = lox + t.x * w
                val cy = loy + t.y * h
                // The BLUR background is baked into the PHOTO, not the cached
                // glyph block, so it must be repainted on every fresh canvas —
                // right before THIS block composites so z-order across items
                // stays identical to the rasterisation path. The measurement is
                // the cached one (never re-measured per fold).
                drawBlurBackground(draw, bitmap, context, t, w, h, cx, cy, lox, loy, entry.measure)
                // The cached block composites from the CURRENT centre, never a
                // stored absolute left/top (which would pin a MOVED item to its
                // ORIGINAL raster position while the blur oval above follows the
                // move). [offsetLeft]/[offsetTop] are position-independent (the
                // bitmap top-left relative to the item centre at raster time), so
                // the centre-about rotate lands the block on the live spot. Scale
                // is NOT a composite transform: the block is rasterised at its
                // effective on-image size for this scale (the key holds it), so
                // the cached bitmap is already the full-resolution glyphs — a
                // canvas scale here would both double-apply the factor and
                // bitmap-upscale the vector glyphs (Text is vector, docs/effect_stack.md).
                draw.save()
                draw.rotate(t.rotationDegrees, cx, cy)
                draw.drawBitmap(entry.bitmap, cx + entry.offsetLeft, cy + entry.offsetTop, null)
                draw.restore()
                continue
            }

            val measure = measureTextBlock(context, t, w, h, t.scale)
            val paint = measure.paint
            val lines = measure.lines
            val lineHeight = measure.lineHeight
            val blockWidth = measure.blockWidth
            val blockHeight = measure.blockHeight
            val textSize = measure.textSize
            val last = measure.last ?: continue

            val cx = lox + t.x * w
            val cy = loy + t.y * h

            // Rasterise the block into its own bitmap (with a margin so the
            // background pad, outline overhang and shadow reach never clip), then
            // composite it in the item's ROTATE frame — bit-identical to
            // drawing the block directly because the canvas only ever paints
            // SRC_OVER onto this image.
            val padX = textSize * 0.2f
            val padY = textSize * 0.18f
            val left = cx - blockWidth / 2f - padX
            val top = cy - blockHeight / 2f - padY
            val right = cx + blockWidth / 2f + padX
            val bottom = cy + blockHeight / 2f + padY
            val strokeWidthPx = if (t.outline.enabled) (t.outline.size * w * t.scale).coerceAtLeast(0.5f) else 0f
            val shadowReach = if (t.shadow.enabled) {
                val dist = t.shadow.distance * w * t.scale
                dist + (dist * 0.25f).coerceAtLeast(0.1f)
            } else {
                0f
            }
            val margin = ceil(maxOf(strokeWidthPx, shadowReach)).toInt().coerceAtLeast(0) + 2
            val leftB = left - margin
            val topB = top - margin
            val bitmapW = (ceil(right - left).toInt().coerceAtLeast(1) + 2 * margin).coerceAtLeast(2)
            val bitmapH = (ceil(bottom - top).toInt().coerceAtLeast(1) + 2 * margin).coerceAtLeast(2)
            val block = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
            val bdraw = android.graphics.Canvas(block)
            val bdrawCx = cx - leftB
            val bdrawCy = cy - topB
            val bx = left - leftB
            val by = top - topB
            val brx = right - leftB
            val bry = bottom - topB

            // Background shape sits behind the text block, with a small padding.
            when (t.background) {
                TextBackgroundShape.NONE -> {}
                TextBackgroundShape.RECT -> {
                    bdraw.drawRect(bx, by, brx, bry, Paint().apply { color = t.backgroundColor })
                }
                TextBackgroundShape.ROUNDED -> {
                    val radius = roundedBackgroundRadiusPx(textSize)
                    bdraw.drawRoundRect(bx, by, brx, bry, radius, radius, Paint().apply { color = t.backgroundColor })
                }
                TextBackgroundShape.OVAL -> {
                    bdraw.drawOval(RectF(bx, by, brx, bry), Paint().apply { color = t.backgroundColor })
                }
                TextBackgroundShape.PILL -> {
                    val radius = (bry - by) / 2f
                    bdraw.drawRoundRect(bx, by, brx, bry, radius, radius, Paint().apply { color = t.backgroundColor })
                }
                // Blur is NOT a block-layer fill: it reads the real photo pixels
                // from the main bitmap behind the text and paints them back as a
                // feathered oval on `draw` itself, just before THIS block
                // composites (see drawBlurBackground) — never inside the cached
                // raster, which keeps the block cache reuse valid across frames.
                TextBackgroundShape.BLUR -> {}
            }

            val shadow = t.shadow
            // A shadow is baked into the FILL paint only when the fill is OPAQUE:
            // an opaque glyph hides the shadow directly beneath it, so only the
            // external halo shows (spec: effects_reference.md → *Text* → "an
            // opaque fill is unchanged"). Under a TRANSLUCENT fill the shadow
            // must NOT bleed through the glyph — it is hollowed out of the glyph
            // shape with the same negative-mask pass the outline uses (see the
            // hollow-shadow block after the outline).
            val hollowShadow = shadow.enabled && Color.alpha(t.color) < 255
            if (shadow.enabled && !hollowShadow) {
                val rad = Math.toRadians(shadow.angleDegrees.toDouble())
                val dist = shadow.distance * w * t.scale
                paint.setShadowLayer(
                    (dist * 0.25f).coerceAtLeast(0.1f),
                    kotlin.math.cos(rad).toFloat() * dist,
                    kotlin.math.sin(rad).toFloat() * dist,
                    shadow.color
                )
            }

            // Outline renders BEFORE the fill pass. The stroke is feathered OUTWARD
            // (spec: effects_reference.md → Text → Outline blur semantics) and is
            // clipped OUT of the glyph fill shape so it only shows around the
            // OUTSIDE of the glyph edge — never under a translucent fill (see
            // "Transparency is carried by the fill"): a sub-100% fill reveals the
            // background image through the glyph instead of blending toward the
            // outline colour, and no outline sneaks inside the glyph shape.
            val outline = t.outline
            if (outline.enabled) {
                val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.textSize = textSize
                    typeface = paint.typeface
                    isUnderlineText = t.style.contains(TextStyleFlag.UNDERLINE)
                    isStrikeThruText = t.style.contains(TextStyleFlag.STRIKETHROUGH)
                    style = Paint.Style.STROKE
                }
                val blockRect = RectF(
                    bdrawCx - blockWidth / 2f, bdrawCy - blockHeight / 2f,
                    bdrawCx + blockWidth / 2f, bdrawCy + blockHeight / 2f
                )
                bdraw.saveLayer(blockRect, null, android.graphics.Canvas.ALL_SAVE_FLAG)
                try {
                    // Outward-only outline (spec: effects_reference.md → Text →
                    // "outline must hug the glyph edge outward only, never grow
                    // inward"). Every pass is clipped OUT of the glyph fill shape,
                    // so the stroke only ever renders on the NON-covered outside
                    // band — nothing of the outline can appear inside the glyph
                    // even under a translucent fill (Transparency is carried by
                    // the fill). The stroke is drawn at double width so that after
                    // the inward half is clipped away the surviving outer half
                    // retains the exact outline thickness the user set.
                    val fillPaths = textFillPaths(paint, t, lines, blockWidth, blockHeight, bdrawCx, bdrawCy, lineHeight)
                    for (pass in outline.featherPasses(strokeWidthPx)) {
                        val stroke = Paint(outlinePaint).apply {
                            strokeWidth = pass.strokeWidthPx * 2f
                            color = strokeColor(outline.color, pass.alpha)
                        }
                        bdraw.save()
                        fillPaths.forEach { path -> bdraw.clipOutPath(path) }
                        drawTextBlock(bdraw, stroke, lines, blockWidth, blockHeight, bdrawCx, bdrawCy, lineHeight, t.alignment)
                        bdraw.restore()
                    }
                } finally {
                    bdraw.restore()
                }
            }

            // A translucent fill must reveal the image THROUGH the glyph, never
            // the shadow beneath it (spec: effects_reference.md → *Text* → "The
            // shadow is hollowed out of the glyph like the outline"). The shadow
            // is drawn into an isolated layer with the glyph fill shape clipped
            // OUT of it (the same negative-mask pass the outline above uses), so
            // only the shadow's external band — the offset silhouette outside the
            // glyph — survives; then the translucent fill renders on top with no
            // shadow of its own.
            if (hollowShadow) {
                val rad = Math.toRadians(shadow.angleDegrees.toDouble())
                val dist = shadow.distance * w * t.scale
                val shadowPaint = Paint(paint).apply {
                    color = shadow.color
                    setShadowLayer(
                        (dist * 0.25f).coerceAtLeast(0.1f),
                        kotlin.math.cos(rad).toFloat() * dist,
                        kotlin.math.sin(rad).toFloat() * dist,
                        shadow.color
                    )
                }
                val blockRect = RectF(
                    bdrawCx - blockWidth / 2f, bdrawCy - blockHeight / 2f,
                    bdrawCx + blockWidth / 2f, bdrawCy + blockHeight / 2f
                )
                bdraw.saveLayer(blockRect, null, android.graphics.Canvas.ALL_SAVE_FLAG)
                try {
                    val fillPaths = textFillPaths(paint, t, lines, blockWidth, blockHeight, bdrawCx, bdrawCy, lineHeight)
                    bdraw.save()
                    fillPaths.forEach { path -> bdraw.clipOutPath(path) }
                    drawTextBlock(bdraw, shadowPaint, lines, blockWidth, blockHeight, bdrawCx, bdrawCy, lineHeight, t.alignment)
                    bdraw.restore()
                } finally {
                    bdraw.restore()
                }
            }

            paint.color = t.color
            drawTextBlock(bdraw, paint, lines, blockWidth, blockHeight, bdrawCx, bdrawCy, lineHeight, t.alignment)

            textRasterCount++
            putTextBlock(textKey, CachedTextBlock(block, leftB - cx, topB - cy, measure))

            // BLUR background paints the photo under the block BEFORE the glyph
            // block composites, so it sits exactly behind THIS item's text and
            // any later (higher z-order) item stays on top — identical to the
            // cache-hit path above. Reuses the same measurement as the raster.
            drawBlurBackground(draw, bitmap, context, t, w, h, cx, cy, lox, loy, measure)

            draw.save()
            draw.rotate(t.rotationDegrees, cx, cy)
            draw.drawBitmap(block, leftB, topB, null)
            draw.restore()
        }
    }

    /**
     * Refreshes the BLUR background on a fresh canvas: only BLUR items contribute
     * here (their fill is NOT rasterised into the cached glyph block), and the
     * oval is painted right before THIS item's block composites so the photo
     * under the text blurs while later items still stack above it. A
     * [TextItem.backgroundBlur] of `0f` is a no-op.
     */
    private fun drawBlurBackground(
        draw: Canvas,
        bitmap: Bitmap,
        context: Context,
        t: TextItem,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
        lox: Float,
        loy: Float,
        measure: TextBlockMeasure
    ) {
        if (t.background != TextBackgroundShape.BLUR || t.backgroundBlur <= 0f) return
        if (measure.last == null) return
        // The BLUR oval is painted at the SCALED cover: the [measure] handed in
        // is the block's raster measure, already measured at the item's effective
        // on-image size (fontSize × scale, see [measureTextBlock]), so its cover
        // rect IS the on-canvas oval — scale is baked into the geometry, never a
        // canvas scale, and never rescaled a second time (that is exactly what the
        // public [scaledBlurCoverRect] rule derives from the UNSCALED measure).
        val scaled = blurCoverRect(measure, t, w, h, lox, loy)
        blurBackgroundArea(
            draw,
            bitmap,
            t,
            scaled.left,
            scaled.top,
            scaled.right,
            scaled.bottom,
            scaled.height(),
            cx,
            cy
        )
    }

    /**
     * The exact padded inner rect a BLUR background paints for [t] at target
     * size (w,h) at SCALE 1 — the single source of truth for the oval's base
     * rule, shared by the committed rasteriser ([drawBlurBackground]) and
     * scale-parity tests. It is deliberately measured at the item's UNSCALED
     * size: the SCALED oval comes from [scaledBlurCoverRect] (the item's own
     * scale frame), which is exactly the cover the raster's effective-size
     * measure produces — so the two never diverge (see that function for the
     * scaled form both sides actually paint).
     */
    internal fun blurCoverRect(context: Context, t: TextItem, w: Float, h: Float): RectF =
        blurCoverRect(measureTextBlock(context, t, w, h), t, w, h)

    private fun blurCoverRect(
        measure: TextBlockMeasure,
        t: TextItem,
        w: Float,
        h: Float,
        lox: Float = 0f,
        loy: Float = 0f
    ): RectF {
        val padX = measure.textSize * 0.2f
        val padY = measure.textSize * 0.18f
        val cx = lox + t.x * w
        val cy = loy + t.y * h
        return RectF(
            cx - measure.blockWidth / 2f - padX,
            cy - measure.blockHeight / 2f - padY,
            cx + measure.blockWidth / 2f + padX,
            cy + measure.blockHeight / 2f + padY
        )
    }

    /**
     * The exact on-canvas rect a BLUR background oval covers for [t] at target
     * size (w,h) AFTER the item's [TextItem.scale]: [blurCoverRect] transformed
     * by `t.scale` about the block centre. THIS is the oval the committed
     * rasteriser actually paints ([blurBackgroundArea] composites straight from
     * this rect — scale is baked into the geometry, never applied as a canvas
     * scale, so a scaled item's oval can never be left at the unscaled size nor
     * scaled a second time) and the oval the live preview paints
     * (TextOverlayLayer.buildTextBlurPiece derives its source window from it).
     * Scale 1 is identity: the scaled cover is exactly the unscaled cover.
     */
    internal fun scaledBlurCoverRect(context: Context, t: TextItem, w: Float, h: Float): RectF =
        scaledBlurCoverRect(measureTextBlock(context, t, w, h), t, w, h)

    private fun scaledBlurCoverRect(
        measure: TextBlockMeasure,
        t: TextItem,
        w: Float,
        h: Float,
        lox: Float = 0f,
        loy: Float = 0f
    ): RectF {
        val cover = blurCoverRect(measure, t, w, h, lox, loy)
        val cx = cover.centerX()
        val cy = cover.centerY()
        val halfW = cover.width() / 2f * t.scale
        val halfH = cover.height() / 2f * t.scale
        return RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }

    /**
     * Blur-background rasterisation (spec: docs/effects_reference.md → *Text* →
     * *Background blur semantics*). Copies the photo content under the text's
     * padded block rect (inflated by a few px so the feather band has source to
     * blend), blurs it, and composites it back onto the main canvas under an
     * oval mask that is fully opaque in the centre and fades to transparent
     * outward across the band past the ellipse edge. The [left]/[top]/[right]/
     * [bottom] rect is the SCALED cover (see [scaledBlurCoverRect]) — the item's
     * scale is baked into the geometry, so the oval is painted at the exact
     * on-canvas position/size the scaled glyph block occupies. The item's
     * rotation is applied two-sidedly: the patch is composited rotated about the
     * block centre AND the photo content it carries is re-sampled under that
     * rotated footprint ([blurRegionOval] reads the source at the rotated
     * coordinates), so the oval always blurs the pixels actually behind the
     * rotated text — never a spun slice of the pre-rotation region.
     */
    private fun blurBackgroundArea(
        draw: Canvas,
        bitmap: Bitmap,
        t: TextItem,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        blockHeight: Float,
        cx: Float,
        cy: Float
    ) {
        val intensity = t.backgroundBlur
        if (intensity <= 0f || blockHeight <= 0f) return
        val radius = (intensity * blockHeight).coerceAtLeast(0.5f)
        val feather = (blockHeight * 0.08f).coerceIn(3f, 32f)
        val w = bitmap.width
        val h = bitmap.height
        val sx = floor(left - feather).toInt().coerceIn(0, w - 1)
        val sy = floor(top - feather).toInt().coerceIn(0, h - 1)
        val ex = ceil(right + feather).toInt().coerceIn(sx + 1, w)
        val ey = ceil(bottom + feather).toInt().coerceIn(sy + 1, h)
        val rw = ex - sx
        val rh = ey - sy
        if (rw < 1 || rh < 1) return

        val blended = blurRegionOval(
            bitmap, sx, sy, rw, rh,
            RectF(left, top, right, bottom),
            radius, feather,
            t.rotationDegrees
        )

        draw.save()
        draw.rotate(t.rotationDegrees, cx, cy)
        draw.drawBitmap(blended, sx.toFloat(), sy.toFloat(), null)
        draw.restore()
        blended.recycle()
    }

    /**
     * Shared blur-oval core for the committed renderer AND the live preview
     * (`TextOverlayLayer`): copies the [source] window starting at (sx, sy) of
     * size rw×rh, box-blurs it, then blends the blurred copy over the original
     * under an oval mask centered on [innerRect] — fully blurred inside the
     * ellipse, fading back to the sharp original across the [feather] band past
     * its edge. Returns a new bitmap the caller composites and recycles.
     *
     * [rotationDegrees] rotates the sampled CONTENT under the oval's own
     * rotation (spec: effects_reference.md → *Text* → *Background blur
     * semantics*): the caller composites the returned patch rotated about the
     * oval centre, so each patch pixel is seen at `C + Rθ(p − C)` on the canvas.
     * Writing the photo value AT that rotated position back into the patch makes
     * every canvas pixel under the oval show the photo content actually behind
     * it — never a rotated slice of the pre-rotation axis-aligned region, which
     * is what a naive rotate-the-window draw produces. `0f` (the default) is the
     * exact axis-aligned fast path, byte-identical to the un-rotated behaviour.
     */
    internal fun blurRegionOval(
        source: Bitmap,
        sx: Int,
        sy: Int,
        rw: Int,
        rh: Int,
        innerRect: RectF,
        radius: Float,
        feather: Float,
        rotationDegrees: Float = 0f
    ): Bitmap {
        val original =
            if (rotationDegrees == 0f) {
                Bitmap.createBitmap(source, sx, sy, rw, rh)
            } else {
                Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888).apply {
                    setPixels(
                        rotatedContentPixels(source, sx, sy, rw, rh, innerRect, rotationDegrees),
                        0, rw, 0, 0, rw, rh
                    )
                }
            }
        val blended = original.copy(Bitmap.Config.ARGB_8888, true)
        BokehProcessor().applySoftwareBlur(blended, radius.roundToInt().coerceAtLeast(1))

        val ocx = innerRect.centerX() - sx
        val ocy = innerRect.centerY() - sy
        val innerRx = innerRect.width() / 2f
        val innerRy = innerRect.height() / 2f
        val bandN = feather / ((innerRx + innerRy) / 2f).coerceAtLeast(0.1f)
        val extS = IntArray(rw * rh)
        val blrS = IntArray(rw * rh)
        original.getPixels(extS, 0, rw, 0, 0, rw, rh)
        blended.getPixels(blrS, 0, rw, 0, 0, rw, rh)
        // The output starts from the SHARP pixels so the untouched (weight ≤ 0)
        // band outside the ellipse keeps the original photo; only inside-and-
        // beyond pixels are overwritten by the blend below (blrS keeps the
        // blurred copy so the mix can reference it).
        val bluS = extS.copyOf()
        for (y in 0 until rh) {
            val dy = (y - ocy) / innerRy
            val dy2 = dy * dy
            for (x in 0 until rw) {
                val dx = (x - ocx) / innerRx
                val nd = sqrt(dx * dx + dy2)
                val weight = if (nd <= 1f) {
                    1f
                } else {
                    ((1f + bandN - nd) / bandN).coerceIn(0f, 1f)
                }
                if (weight <= 0f) continue
                val idx = y * rw + x
                val orig = extS[idx]
                val blr = blrS[idx]
                bluS[idx] = Color.argb(
                    Color.alpha(orig),
                    (Color.red(blr) * weight + Color.red(orig) * (1f - weight)).roundToInt(),
                    (Color.green(blr) * weight + Color.green(orig) * (1f - weight)).roundToInt(),
                    (Color.blue(blr) * weight + Color.blue(orig) * (1f - weight)).roundToInt()
                )
            }
        }
        blended.setPixels(bluS, 0, rw, 0, 0, rw, rh)
        original.recycle()
        return blended
    }

    /**
     * Samples the photo content under the oval's ROTATED footprint for
     * [blurRegionOval]: the caller composites the patch rotated by
     * [rotationDegrees] about the oval centre ([innerRect]'s), so the patch
     * pixel at world `(sx+x, sy+y)` is seen on the canvas at its rotated
     * position. Writing the photo value at that rotated position back into the
     * window means every canvas pixel under the oval shows the photo content
     * actually behind it, blurred (spec: effects_reference.md → *Text* →
     * *Background blur semantics* — the region is re-sampled live against the
     * item's new footprint, never a stale/rotated slice of the pre-rotation
     * region). Bilinear reads keep a rotated oval smooth; the source read is
     * bounded to the smallest sub-window the mapped corners can touch.
     */
    private fun rotatedContentPixels(
        source: Bitmap,
        sx: Int,
        sy: Int,
        rw: Int,
        rh: Int,
        innerRect: RectF,
        rotationDegrees: Float
    ): IntArray {
        val centerX = innerRect.centerX()
        val centerY = innerRect.centerY()
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        val sw = source.width
        val sh = source.height

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        val corners = arrayOf(
            sx to sy,
            sx + rw - 1 to sy,
            sx to sy + rh - 1,
            sx + rw - 1 to sy + rh - 1
        )
        for ((ox, oy) in corners) {
            val relX = ox - centerX
            val relY = oy - centerY
            val mx = centerX + relX * cos - relY * sin
            val my = centerY + relX * sin + relY * cos
            minX = min(minX, floor(mx).toInt())
            maxX = max(maxX, ceil(mx).toInt())
            minY = min(minY, floor(my).toInt())
            maxY = max(maxY, ceil(my).toInt())
        }
        val srcX0 = minX.coerceIn(0, sw - 1)
        val srcY0 = minY.coerceIn(0, sh - 1)
        val srcX1 = maxX.coerceIn(0, sw - 1)
        val srcY1 = maxY.coerceIn(0, sh - 1)
        val srcW = srcX1 - srcX0 + 1
        val srcH = srcY1 - srcY0 + 1
        val peek = IntArray(srcW * srcH)
        source.getPixels(peek, 0, srcW, srcX0, srcY0, srcW, srcH)

        val out = IntArray(rw * rh)
        for (y in 0 until rh) {
            val relY = sy + y - centerY
            for (x in 0 until rw) {
                val relX = sx + x - centerX
                val fx = (centerX + relX * cos - relY * sin).coerceIn(0f, (sw - 1).toFloat())
                val fy = (centerY + relX * sin + relY * cos).coerceIn(0f, (sh - 1).toFloat())
                out[y * rw + x] = bilinearSample(peek, srcW, srcH, fx - srcX0, fy - srcY0)
            }
        }
        return out
    }

    /** Bilinear fetch from [px] (`stride×rows`), clamped inside the window. */
    private fun bilinearSample(px: IntArray, stride: Int, rows: Int, fx: Float, fy: Float): Int {
        val x0 = floor(fx).toInt().coerceAtLeast(0)
        val y0 = floor(fy).toInt().coerceAtLeast(0)
        val x1 = (x0 + 1).coerceAtMost(stride - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = blendColor(px[y0 * stride + x0], px[y0 * stride + x1], tx)
        val bottom = blendColor(px[y1 * stride + x0], px[y1 * stride + x1], tx)
        return blendColor(top, bottom, ty)
    }

    /** Per-channel ARGB linear mix, `t` in 0..1. */
    private fun blendColor(a: Int, b: Int, t: Float): Int {
        val inv = 1f - t
        return Color.argb(
            (Color.alpha(a) * inv + Color.alpha(b) * t).roundToInt(),
            (Color.red(a) * inv + Color.red(b) * t).roundToInt(),
            (Color.green(a) * inv + Color.green(b) * t).roundToInt(),
            (Color.blue(a) * inv + Color.blue(b) * t).roundToInt()
        )
    }

    /**
     * Single measurement for a text block — shared by the rasterisation path AND
     * [drawBlurBackground], so the BLUR oval always spans exactly the same rect
     * the glyph block fills (padding included) no matter which path runs.
     */
    private class TextBlockMeasure(
        val paint: Paint,
        val lines: List<String>,
        val blockWidth: Float,
        val blockHeight: Float,
        val lineHeight: Float,
        val textSize: Float
    ) {
        val last: String? get() = lines.lastOrNull()
    }

    private fun measureTextBlock(context: Context, t: TextItem, w: Float, h: Float, scale: Float = 1f): TextBlockMeasure {
        textMeasureCount++
        // The glyphs are rasterised at their EFFECTIVE on-image size — the font
        // size combined with the item's own [t.scale] (docs/effect_stack.md →
        // *Text and sticker overlays — parameterised lists* → *Text is vector*):
        // an item enlarged through the selection grid re-renders the vector
        // glyphs at the new size, so grid-scaling stays sharp in the committed /
        // full-size result. Scale is therefore baked into the RASTER ONLY: the
        // wrap box (maxLineWidth), the block geometry and every element
        // (background pad, outline stroke, shadow, margin) scale together by the
        // same factor, and the composite draws the block at 1:1 — NEVER a canvas
        // scale, which would bitmap-upscale the base-size raster (pixelation) and
        // double-apply the factor. The blur geometry keeps measuring at scale 1
        // ([blurCoverRect]/[scaledBlurCoverRect]) and rescales geometrically, so
        // nothing ever squares the factor.
        val textSize = (t.fontSize * w * scale).coerceAtLeast(2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = typefaceFor(context, t)
            isUnderlineText = t.style.contains(TextStyleFlag.UNDERLINE)
            isStrikeThruText = t.style.contains(TextStyleFlag.STRIKETHROUGH)
        }
        val maxLineWidth = w * 0.95f * scale
        val lines = wrapTexlines(paint, t.text, maxLineWidth)
        val metrics = paint.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        // For justified text every line except the last spans the full block
        // width; for the other alignments the block is the widest line.
        val blockWidth = if (t.alignment == TextAlignment.JUSTIFIED && lines.size > 1) {
            maxLineWidth
        } else {
            lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        }
        return TextBlockMeasure(
            paint,
            lines,
            blockWidth,
            lines.size * lineHeight,
            lineHeight,
            textSize
        )
    }

    private fun textBlockKey(t: TextItem, w: Float, h: Float): String = listOf(
        t.text, t.color, t.fontFamily, t.fontSize,
        t.style.sorted().joinToString(","),
        t.alignment,
        t.shadow.enabled, t.shadow.angleDegrees, t.shadow.distance, t.shadow.color,
        t.outline.enabled, t.outline.size, t.outline.color, t.outline.blur,
        t.background, t.backgroundColor, w, h,
        // The block's pixels ARE a function of scale: glyphs are rasterised at
        // the item's effective on-image size (fontSize × scale), so a grid-scale
        // change re-rasterises the vector block instead of reusing a base-size
        // raster and bitmap-scaling it (the sharpness fix; Text is vector).
        t.scale
    ).joinToString("|")

    /**
     * A rasterised text block. [offsetLeft]/[offsetTop] express the bitmap's
     * top-left RELATIVE to the item centre (cx, cy) at raster time — position-
     * independent, so a cache hit (an item whose pixels are unchanged but whose
     * centre has moved / rotated) reuses the raster and composites it at the
     * CURRENT centre, exactly where the live overlay shows it. The block's
     * [TextBlockMeasure] rides along so the BLUR background repaint reuses the
     * exact measurement instead of re-measuring on every fold. A RESCALED item
     * is NOT a hit: scale is part of the block's pixels (glyphs re-raster at
     * their effective on-image size), so it produces a fresh key.
     */
    private class CachedTextBlock(
        val bitmap: Bitmap,
        val offsetLeft: Float,
        val offsetTop: Float,
        val measure: TextBlockMeasure
    ) {
        val bytes: Int = bitmap.byteCount
    }

    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024

    private val textBlockCache = LinkedHashMap<String, CachedTextBlock>(32, 0.75f, true)

    private var textCacheBytes = 0L

    /** Text-block re-rasterisations since the last [clearTextCache]; internal for tests. */
    internal var textRasterCount: Int = 0
        private set

    /** [measureTextBlock] calls since the last [clearTextCache]; internal for tests. */
    internal var textMeasureCount: Int = 0
        private set

    private fun putTextBlock(key: String, entry: CachedTextBlock) {
        textBlockCache[key] = entry
        textCacheBytes += entry.bytes
        while (textCacheBytes > MAX_CACHE_BYTES && textBlockCache.isNotEmpty()) {
            val eldest = textBlockCache.entries.iterator().next()
            textCacheBytes -= eldest.value.bytes
            eldest.value.bitmap.recycle()
            textBlockCache.remove(eldest.key)
        }
    }

    /** Drops every cached text block (frees the bitmaps); internal for tests. */
    internal fun clearTextCache() {
        for (entry in textBlockCache.values) entry.bitmap.recycle()
        textBlockCache.clear()
        textCacheBytes = 0L
        textRasterCount = 0
        textMeasureCount = 0
    }

    /** Outlines the stroke's source colour scaled by the feather pass alpha. */
    private fun strokeColor(color: Int, alpha: Float): Int = Color.argb(
        (Color.alpha(color).toFloat() * alpha).roundToInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    /**
     * Returns the vector fill [Path]s of every glyph in the wrapped [lines], in
     * canvas coordinates, using [paint]'s type metrics and the block layout
     * computed by [drawTextBlock]. Used to clip the outline OUT of the glyph
     * region so the outline only renders on the non-covered outside band (see
     * the outward-only outline in [drawTexts]).
     */
    private fun textFillPaths(
        paint: Paint,
        t: TextItem,
        lines: List<String>,
        blockWidth: Float,
        blockHeight: Float,
        cx: Float,
        cy: Float,
        lineHeight: Float
    ): List<Path> {
        val paths = ArrayList<Path>()
        val metrics = paint.fontMetrics
        val top = cy - blockHeight / 2f
        lines.forEachIndexed { index, line ->
            val baseline = top + index * lineHeight - metrics.ascent
            val fw = paint.measureText(line)
            val startX = when (t.alignment) {
                TextAlignment.LEFT -> cx - blockWidth / 2f
                TextAlignment.RIGHT -> cx + blockWidth / 2f - fw
                TextAlignment.JUSTIFIED, TextAlignment.CENTER -> cx - fw / 2f
            }
            val path = Path()
            paint.getTextPath(line, 0, line.length, startX, baseline, path)
            paths.add(path)
        }
        return paths
    }

    private fun typefaceFor(context: Context, t: TextItem): android.graphics.Typeface {
        val base = when (t.fontFamily) {
            // The fallback/sans families stay on the system defaults: SANS_SERIF
            // is by definition the platform fallback, so it must equal DEFAULT.
            TextFont.DEFAULT -> android.graphics.Typeface.DEFAULT
            TextFont.SANS_SERIF -> android.graphics.Typeface.SANS_SERIF
            // Bundled OFL/Apache-licensed fonts. The device's OWN generic families
            // are NOT deterministic across OEMs: with a custom font pack active
            // (e.g. Motorola) `serif`/`monospace`/`cursive` all resolve to the
            // same installed family, collapsing every font into one shape.
            TextFont.SERIF -> ResourcesCompat.getFont(context, R.font.serif)
                ?: android.graphics.Typeface.SERIF
            TextFont.MONOSPACE -> ResourcesCompat.getFont(context, R.font.monospace)
                ?: android.graphics.Typeface.MONOSPACE
            TextFont.HANDWRITING -> ResourcesCompat.getFont(context, R.font.handwriting)
                ?: android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
        }
        val bold = t.style.contains(TextStyleFlag.BOLD)
        val italic = t.style.contains(TextStyleFlag.ITALIC)
        return when {
            bold && italic -> android.graphics.Typeface.create(base, android.graphics.Typeface.BOLD_ITALIC)
            bold -> android.graphics.Typeface.create(base, android.graphics.Typeface.BOLD)
            italic -> android.graphics.Typeface.create(base, android.graphics.Typeface.ITALIC)
            else -> base
        }
    }

    /**
     * Splits a (possibly multi-line) text into wrapped single lines fitting
     * [maxWidth], preserving explicit newlines. Deterministic on every backend
     * (pure [Paint.measureText] math — no native StaticLayout) so it rasterizes
     * identically under Robolectric and on device. The wrap mirrors the live
     * Compose `Text` layout's default ICU line breaking on the common cases
     * (docs/effects_reference.md → Text tools): words break at whitespace,
     * **whitespace runs are kept verbatim** (consecutive spaces survive), a
     * no-space **CJK / ideographic run breaks between characters**, and a single
     * unit too wide for the block is **hard-broken by character** instead of
     * clipping past the block edge. Line-leading and line-trailing whitespace is
     * collapsed exactly like the live layout collapses it at a wrap.
     */
    private fun wrapTexlines(paint: Paint, text: String, maxWidth: Float): List<String> =
        wrapLines(text, maxWidth) { paint.measureText(it) }

    /**
     * [wrapTexlines] with an injectable width function, so the wrap decisions
     * (whitespace preservation, CJK breaks, overflow hard-breaks and the greedy
     * single-space behaviour) are unit-testable independent of glyph metrics.
     */
    internal fun wrapLines(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        for (raw in text.split('\n')) {
            if (raw.isBlank()) {
                out += ""
                continue
            }
            val units = wrapUnits(raw)
            var line = StringBuilder()
            var lineWidth = 0f
            var pendingSpace: String? = null
            val commit = { out += line.toString(); line = StringBuilder(); lineWidth = 0f }
            val hardBreak = fun(unit: String) {
                var rest = unit
                while (rest.isNotEmpty()) {
                    val prefix = longestFitPrefix(rest, maxWidth, measure)
                    if (prefix.isEmpty()) {
                        val one = firstCodePoint(rest)
                        out += one
                        rest = rest.removePrefix(one)
                    } else {
                        out += prefix
                        rest = rest.removePrefix(prefix)
                    }
                }
            }
            for (unit in units) {
                if (unit.isBlank()) {
                    pendingSpace = if (pendingSpace == null) unit else pendingSpace + unit
                    continue
                }
                if (lineWidth == 0f) {
                    // Fresh line: a pending whitespace run collapses (line-leading
                    // space is not drawn), and an over-wide unit hard-breaks.
                    pendingSpace = null
                    if (measure(unit) > maxWidth) {
                        hardBreak(unit)
                        continue
                    }
                    line.append(unit)
                    lineWidth = measure(unit)
                    continue
                }
                val candidate = line.toString() + (pendingSpace ?: "") + unit
                if (measure(candidate) <= maxWidth) {
                    if (pendingSpace != null) {
                        line.append(pendingSpace)
                        lineWidth += measure(pendingSpace)
                        pendingSpace = null
                    }
                    line.append(unit)
                    lineWidth += measure(unit)
                } else {
                    commit()
                    pendingSpace = null
                    if (measure(unit) > maxWidth) {
                        hardBreak(unit)
                    } else {
                        line.append(unit)
                        lineWidth = measure(unit)
                    }
                }
            }
            if (lineWidth > 0f) out += line.toString()
        }
        return out
    }

    /**
     * Atomises a physical line into units for [wrapLines]: whitespace runs (kept
     * verbatim) separated from content runs, and content runs further split at
     * every Unicode line-break opportunity the live Compose layout exploits —
     * between two CJK-class characters and at any CJK ↔ non-CJK boundary, so a
     * long no-space CJK string wraps like the preview instead of clipping.
     */
    private fun wrapUnits(text: String): List<String> {
        val units = mutableListOf<String>()
        var start = 0
        for (i in 1 until text.length) {
            val prev = text[i - 1]
            val cur = text[i]
            val prevWs = prev.isWhitespace()
            val curWs = cur.isWhitespace()
            // Split at every whitespace ↔ content boundary, and inside a content
            // run at each CJK line-break opportunity — but NOT between two
            // consecutive spaces, so a whitespace run stays verbatim.
            if (prevWs != curWs || (!prevWs && !curWs && (isCjkClass(prev) || isCjkClass(cur)))) {
                units += text.substring(start, i)
                start = i
            }
        }
        units += text.substring(start)
        return units
    }

    /**
     * True when [c] is part of a CJK script or a CJK punctuation block — the
     * character classes between which the live Compose layout puts line-break
     * opportunities. Determined per-codepoint (not per grapheme) via Unicode
     * script blocks, so it is backend-independent like the rest of [wrapLines].
     */
    internal fun isCjkClass(c: Char): Boolean {
        val cp = c.code
        return when (Character.UnicodeScript.of(cp)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL -> true
            else -> cp in 0x3000..0x303F || cp in 0x3300..0x33FF || cp in 0xFF00..0xFFEF
        }
    }

    /**
     * The longest prefix of [s] whose [measure] fits in [budget], by candidate
     * count (never splitting a surrogate pair). Empty when even one character
     * does not fit — the caller then hard-breaks by a single character.
     */
    private fun longestFitPrefix(s: String, budget: Float, measure: (String) -> Float): String {
        if (budget <= 0f || s.isEmpty()) return ""
        var count = s.codePointCount(0, s.length)
        var low = 1
        var high = count
        while (low < high) {
            val mid = (low + high + 1) / 2
            val end = s.offsetByCodePoints(0, mid)
            if (measure(s.substring(0, end)) <= budget) low = mid else high = mid - 1
        }
        val end = s.offsetByCodePoints(0, low)
        return if (measure(s.substring(0, end)) <= budget) s.substring(0, end) else ""
    }

    private fun firstCodePoint(s: String): String = s.substring(0, s.offsetByCodePoints(0, 1).coerceAtLeast(1))

    /**
     * Draws a wrapped [lines] block centered on (cx · cy), applying the item's
     * alignment. [blockWidth]/[blockHeight] are the block's measured extents;
     * [lineHeight] the per-line advance.
     */
    private fun drawTextBlock(
        draw: android.graphics.Canvas,
        paint: Paint,
        lines: List<String>,
        blockWidth: Float,
        blockHeight: Float,
        cx: Float,
        cy: Float,
        lineHeight: Float,
        alignment: TextAlignment
    ) {
        val metrics = paint.fontMetrics
        val top = cy - blockHeight / 2f
        lines.forEachIndexed { index, line ->
            val baseline = top + index * lineHeight - metrics.ascent
            val fw = paint.measureText(line)
            when (alignment) {
                TextAlignment.LEFT -> draw.drawText(line, cx - blockWidth / 2f, baseline, paint)
                TextAlignment.RIGHT -> draw.drawText(line, cx + blockWidth / 2f - fw, baseline, paint)
                TextAlignment.JUSTIFIED -> {
                    val isLast = index == lines.size - 1
                    val gaps = line.count { it == ' ' }
                    if (isLast || gaps == 0) {
                        draw.drawText(line, cx - blockWidth / 2f, baseline, paint)
                    } else {
                        val extraPerGap = (blockWidth - fw) / gaps
                        var x = cx - blockWidth / 2f
                        var wordStart = 0
                        var gapWidth = 0f
                        for (ci in line.indices) {
                            if (line[ci] == ' ') {
                                gapWidth = paint.measureText(line, wordStart, ci + 1)
                                draw.drawText(line, wordStart, ci + 1, x, baseline, paint)
                                x += gapWidth + extraPerGap
                                wordStart = ci + 1
                            }
                        }
                        if (wordStart < line.length) {
                            draw.drawText(line, wordStart, line.length, x, baseline, paint)
                        }
                    }
                }
                TextAlignment.CENTER -> draw.drawText(line, cx - fw / 2f, baseline, paint)
            }
        }
    }
}