package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import android.content.Context
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehProcessor
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehShape
import codes.pepper.whimsicalart.feature.editor.domain.enhance.EnhanceProcessor
import codes.pepper.whimsicalart.feature.editor.domain.enhance.EnhanceSettings
import codes.pepper.whimsicalart.feature.editor.domain.filter.LensFilterProcessor
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilterProcessor
import codes.pepper.whimsicalart.feature.editor.domain.matting.BackgroundReplacer
import codes.pepper.whimsicalart.feature.editor.domain.matting.SoftEdgeBand
import codes.pepper.whimsicalart.feature.stickers.domain.StickerItem
import kotlin.math.roundToInt

/**
 * Pen brush shaping (docs/effect_stack.md → Pen stroke-shaping). Mirrors the
 * live preview's branch behaviour so the committed render never diverges from
 * what the user sees while drawing:
 * - [SOLID] / [RAINBOW]: a radial hardness-gradient stamp (RAINBOW cycles hue).
 * - [GLOW]: wide faint halo + solid core + white-hot centre, no hardness.
 * - [NEON]: faint outer halo + solid core, no hardness.
 */
enum class PenBrushKind { SOLID, GLOW, NEON, RAINBOW }

data class StrokeLayer(
    val type: StrokeType,
    val points: List<Pair<Float, Float>>,
    val brushSize: Float,
    val color: Int? = null,
    val opacity: Float = 1f,
    /**
     * 0–1 pen edge hardness relative to size (docs/effects_reference.md → Pen
     * controls): 1 = solid across the whole size, 0 = only the centre solid,
     * fading out to the size. Only consulted for [PenBrushKind.SOLID] and
     * [PenBrushKind.RAINBOW].
     */
    val hardness: Float = 1f,
    /** The pen brush shaping used to commit this stroke (defaults to SOLID). */
    val brush: PenBrushKind = PenBrushKind.SOLID
)

/**
 * One tier of the Frames tool (docs/effects_reference.md → *Frames*): the
 * **inner** tier draws its band **inward** onto the photo, the **outer** tier
 * draws **outward** from the photo's edge and, when enabled, enlarges the
 * rendered output. A tier with [style] == `null` ("None") contributes nothing.
 *
 * [thickness] is the band's width as a fraction of the **short image edge**
 * (applied at half strength — `0.5` = a quarter, `1.0` = half the short edge),
 * [edgeSize] is the ⁎ boundary-treatment reach along each edge, the `custom*`
 * fields feed the `Custom (Curved)` style's four sliders. The three line fields
 * draw [lines] concentric decorative strips in the tier's colour: [lineDistance]
 * (fraction of the short edge) spaces them and [lineThickness] is each strip's
 * width.
 */
data class FrameTier(
    val style: FrameStyle? = null,
    val thickness: Float = 0f,
    val color: Int = Color.BLACK,
    /** 0–1 how far the corner / edge treatment runs along each edge (0 = squared
     * corner, 1 = the treatment reaches the middle of the short edge). Only
     * consulted for the ⁎ styles ([FrameStyle.hasEdgeSize]). */
    val edgeSize: Float = 0.2f,
    /** CUSTOM-style start phase along each edge (−180…+180). */
    val customAngle: Float = 0f,
    /** CUSTOM-style curve reach; the SIGN picks the direction (negative = inward
     * scoop, positive = outward bulge, 0 = straight) and the absolute value the
     * magnitude (−100%…+100%, default −50%). */
    val customCurveDistance: Float = -0.5f,
    /** CUSTOM-style shaping (negative = teardrop, 0 = arc, 1 = triangle point). */
    val customStrength: Float = 0f,
    /** CUSTOM-style number of half-curves per side (1–10, default 2). */
    val customCount: Float = 2f,
    /** How many concentric decorative line strips to draw (0 = just the band). */
    val lines: Int = 0,
    /** Line spacing as a fraction of the short image edge (default 4%). */
    val lineDistance: Float = 0.04f,
    /** Each line strip's width as a fraction of the short image edge (default 2%). */
    val lineThickness: Float = 0.02f
) {
    /** True when the tier has a style picked (anything but "None"). */
    val enabled: Boolean get() = style != null
}

/**
 * The Frames layer (docs/effects_reference.md → *Frames*): a SINGLE stack entry
 * carries BOTH tiers — [inner] (drawn inward over the photo) and [outer]
 * (drawn outward, enlarging the output) — so the two tiers commit, reorder,
 * merge and revert together.
 */
data class FrameLayer(
    val inner: FrameTier = FrameTier(),
    val outer: FrameTier = FrameTier()
) {
    /** True when either tier has a style picked. */
    val enabled: Boolean get() = inner.enabled || outer.enabled

    /**
     * The outer tier's total outward extent in pixels for a [photoW]×[photoH]
     * image: the outer band plus the decorative line extent
     * (`lines × (distance + line thickness)`) plus any outward-carrying edge
     * treatment (docs/effects_reference.md → *Outer-frame dimensions*). Zero
     * when the outer tier is at "None". The fold canvas grows by 2× this amount
     * and the photo is inset by it.
     */
    fun outerExtentPx(photoW: Int, photoH: Int): Int {
        val style = outer.style ?: return 0
        val short = minOf(photoW, photoH).toFloat()
        if (short < 1f) return 0
        var extent = (outer.thickness * short / 2f).coerceIn(0f, short / 2f)
        if (outer.lines > 0) {
            // Same third-scale the renderer applies to the strip px (see
            // FrameOverlayRenderer.buildStripMask); lines×(distance+thickness)
            // is then the exact reach INCLUDING the leading gap before strip one.
            val d = (outer.lineDistance * short / 3f).coerceAtLeast(0f)
            val l = (outer.lineThickness * short / 3f).coerceAtLeast(0f)
            extent += outer.lines * (d + l)
        }
        extent += FrameGeometry.outwardReachPx(outer, short)
        return extent.toInt().coerceAtLeast(0)
    }
}

/**
 * Which background effect to apply to the region outside the subject mask.
 */
enum class BackgroundMode {
    /** Defocus (blur) the background, keeping the subject sharp. */
    BLUR,

    /** Replace the background with a chosen image, keeping the subject intact. */
    REPLACE
}

/**
 * Subject-aware background layer. [subjectMask] is the person/foreground alpha
 * mask (source-sized, alpha = foreground confidence) produced by on-device
 * segmentation; [mode] decides whether the outside of that mask is defocused
 * ([BackgroundMode.BLUR], intensity = [blurRadius] in percent of canvas
 * width, shaped by [shape]) or swapped for [backgroundImage]
 * ([BackgroundMode.REPLACE]).
 */
data class BackgroundLayer(
    val mode: BackgroundMode = BackgroundMode.BLUR,
    val subjectMask: Bitmap? = null,
    val blurRadius: Float = 50f,
    val shape: BokehShape = BokehShape.CIRCLE,
    val backgroundImage: Bitmap? = null,
    val placementScale: Float = 1f,
    val placementPanX: Float = 0f,
    val placementPanY: Float = 0f,
    val placementRotationDegrees: Float = 0f,
    val freeScaleUp: Boolean = false
)

enum class StrokeType { MOSAIC, BLUR, PEN, REMOVAL }

/**
 * The full render payload for a save/share: the selected filter's color matrix
 * (combined with the adjustments inside the renderer) plus every interactive
 * overlay, expressed in NORMALIZED [0..1] coordinates.
 */
data class EditorRenderBundle(
    val filterMatrix: FloatArray? = null,
    val styleFilter: StyleFilter? = null,
    val lensPassIds: Set<String> = emptySet(),
    val stickers: List<StickerItem> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val strokes: List<StrokeLayer> = emptyList(),
    val frames: List<FrameLayer> = emptyList(),
    val background: BackgroundLayer? = null,
    val suggestedRegions: List<RectF> = emptyList()
)

/**
 * The single entry point of the committed image pipeline. Rasterizes a full
 * [render] call (or the incremental [renderStack] fold) onto a fresh bitmap,
 * applying every effect family in the same order [StackEffect] folds them in.
 *
 * Each drawing family owns its raster logic in its own renderer —
 * [CropMountRenderer] (rotate/flip/crop), [VignetteRenderer] (radial vignette),
 * prior to the overlay families [StickerOverlayRenderer], [TextOverlayRenderer],
 * [StrokeOverlayRenderer] (brush work + privacy regions) and the final
 * [FrameOverlayRenderer] borders. The per-effect fold helpers
 * ([applyMatrixPass], [applySharpenCopy]) are shared with [CascadeRenderer].
 */
object BitmapRenderer {

    fun render(
        context: Context,
        input: Bitmap,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        cropRect: Rect?,
        colorMatrix: FloatArray?,
        vignette: Float = 0f,
        sharpen: Float = 0f,
        enhance: EnhanceSettings? = null,
        styleFilter: StyleFilter? = null,
        lensPassIds: Set<String> = emptySet(),
        stickers: List<StickerItem> = emptyList(),
        texts: List<TextItem> = emptyList(),
        strokes: List<StrokeLayer> = emptyList(),
        frames: List<FrameLayer> = emptyList(),
        background: BackgroundLayer? = null,
        suggestedRegions: List<RectF> = emptyList()
    ): Bitmap {
        // 1. Rotation + flip.
        var canvas = CropMountRenderer.transforms(input, rotationDegrees, flipHorizontal, flipVertical)

        // 2. Crop (normalized) applied in the transformed orientation.
        if (cropRect != null) {
            canvas = CropMountRenderer.crop(canvas, cropRect)
        }

        // 3. Adjustments + filter color matrix.
        var result = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (colorMatrix != null) {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
        }
        Canvas(result).drawBitmap(canvas, 0f, 0f, paint)

        // 4. Sharpen (unsharp mask) and vignette (radial darkening) — these are
        //    not expressible as a color matrix, so run as per-pixel passes.
        if (sharpen != 0f) {
            applySharpen(canvas = result, amount = sharpen)
        }
        if (vignette != 0f) {
            VignetteRenderer.applyVignette(canvas = result, strength = vignette)
        }

        // 4.5. One-tap Auto-Enhance / HDR tone map. Runs after the colour matrix
        // and sharpening so it grades the already-adjusted pixels, and before the
        // subject-aware background/overlays so they paint consistently on top.
        if (enhance != null) {
            val enhanced = EnhanceProcessor.enhance(result, enhance)
            if (enhanced !== result) {
                val draw = Canvas(result)
                draw.drawBitmap(enhanced, 0f, 0f, null)
                enhanced.recycle()
            }
        }

        // 4.6. Learned-look style filter layer, applied on top of the enhance map.
        if (styleFilter != null) {
            val styled = StyleFilterProcessor.apply(result, styleFilter)
            if (styled !== result) {
                val draw = Canvas(result)
                draw.drawBitmap(styled, 0f, 0f, null)
                styled.recycle()
            }
        }

        // 4.7. Real lens-filter pixel passes (red-eye, star, anamorphic, astro).
        // Not expressible as a color matrix, so run as per-pixel passes, applied
        // after the color/style passes and before subject-aware background.
        if (lensPassIds.isNotEmpty()) {
            val passed = LensFilterProcessor.applyLensPasses(result, lensPassIds)
            if (passed !== result) {
                val draw = Canvas(result)
                draw.drawBitmap(passed, 0f, 0f, null)
                passed.recycle()
            }
        }

        // 5. Subject-aware background: defocus (bokeh) or replace the region
        //    outside the segmentation mask. Runs after the global color/sharpen/
        //    vignette passes so the result matches the colour-graded subject,
        //    but before interactive overlays paint on top.
        val bg = background
        val subjectMask = bg?.subjectMask
        if (bg != null && subjectMask != null) {
            when (bg.mode) {
                BackgroundMode.BLUR -> {
                    val radiusPx = backgroundBlurRadiusPx(bg.blurRadius, result.width)
                    // applyBackgroundBlur copies `result` and blurs the copy;
                    // the old code discarded the return value (same discard bug
                    // as StackEffect.BackgroundEffect.render) so the defocus
                    // never landed.
                    val blurred = BokehProcessor().applyBackgroundBlur(
                        original = result,
                        foregroundMask = subjectMask,
                        blurRadius = radiusPx,
                        bokehShape = bg.shape,
                        softEdgeWidth = SoftEdgeBand.DEFAULT_WIDTH
                    )
                    if (blurred !== result) {
                        val draw = Canvas(result)
                        draw.drawBitmap(blurred, 0f, 0f, null)
                        blurred.recycle()
                    }
                }
                BackgroundMode.REPLACE -> {
                    val image = bg.backgroundImage
                    if (image != null) {
                        result = recycleAndApply(
                            result,
                            BackgroundReplacer.place(
                                result,
                                subjectMask,
                                image,
                                BackgroundReplacer.PlacementTransform(
                                    scale = bg.placementScale,
                                    panX = bg.placementPanX,
                                    panY = bg.placementPanY,
                                    rotationDegrees = bg.placementRotationDegrees,
                                    freeScaleUp = bg.freeScaleUp
                                ),
                                softEdgeWidth = SoftEdgeBand.DEFAULT_WIDTH
                            )
                        )
                    }
                }
            }
        }

        // 6. Overlays.
        StickerOverlayRenderer.drawStickers(context, result, stickers)
        TextOverlayRenderer.drawTexts(context, result, texts)
        StrokeOverlayRenderer.drawStrokes(result, strokes)
        StrokeOverlayRenderer.drawPrivacyRegions(result, suggestedRegions)

        // 7. Frame borders sit on top of every overlay so they never get
        //    painted over by stickers, text or brush work. When a frame's OUTER
        //    tier is enabled the fold canvas grows by 2× the outer extent and
        //    the image moves to the centre; then both tiers rasterise onto the
        //    enlarged canvas so save/share export the larger image.
        val outerFrame = frames.firstOrNull { it.outer.enabled }
        if (outerFrame != null) {
            val extent = outerFrame.outerExtentPx(result.width, result.height)
            if (extent > 0) {
                val enlarged = Bitmap.createBitmap(
                    result.width + 2 * extent,
                    result.height + 2 * extent,
                    Bitmap.Config.ARGB_8888
                )
                android.graphics.Canvas(enlarged)
                    .drawBitmap(result, extent.toFloat(), extent.toFloat(), null)
                if (!result.isRecycled) result.recycle()
                result = enlarged
            }
            FrameOverlayRenderer.drawFrames(result, frames, insetX = extent, insetY = extent)
        } else {
            FrameOverlayRenderer.drawFrames(result, frames)
        }

        return result
    }

    /** Replaces [old] with [next] when they differ, recycling the old bitmap. */
    private fun recycleAndApply(old: Bitmap, next: Bitmap): Bitmap {
        if (next !== old && !next.isRecycled) {
            if (!old.isRecycled) old.recycle()
            return next
        }
        return old
    }

    /**
     * Folds an ordered [effects] stack left-to-right over [input] (the pristine
     * base photo), producing the final image. This is the incremental render
     * path used by the save/merge/share pipeline and the Layers thumbnails.
     *
     * Each effect is a self-contained [StackEffect.render] step applied in stack
     * order to the running image, so order matters and every added effect folds
     * on top of the already-cropped, already-transformed image (the behaviour
     * that "crop not applying" previously broke, when the stack snapshot carried
     * crop LAST, out of sync with the renderer).
     */
    fun renderStack(
        context: Context,
        input: Bitmap,
        effects: List<StackEffect>
    ): Bitmap {
        return CascadeRenderer.fold(context, input, effects)
    }

    /**
     * Applies only the unsharp-mask sharpen/soften to a copy of [input] so the
     * editor can preview the sharpness slider live (everything else in the
     * preview is handled by the color matrix / overlay composables).
     */
    fun sharpenPreview(input: Bitmap, amount: Float): Bitmap {
        val out = input.copy(Bitmap.Config.ARGB_8888, true)
        applySharpen(out, amount)
        return out
    }

    /**
     * Functional single matrix-pass helper for the per-effect fold: draws a copy
     * of [current] through the [matrix] (or unchanged when [matrix] is null) and
     * returns a NEW bitmap, leaving [current] untouched (owned by the fold).
     */
    internal fun applyMatrixPass(current: Bitmap, matrix: FloatArray?): Bitmap {
        val result = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (matrix != null) colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(result).drawBitmap(current, 0f, 0f, paint)
        return result
    }

    /**
     * Functional unsharp-mask sharpen/soften pass: returns a copy of [current]
     * with [amount] applied in place, leaving [current] untouched.
     */
    internal fun applySharpenCopy(current: Bitmap, amount: Float): Bitmap {
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        applySharpen(out, amount)
        return out
    }

    /**
     * Produces the actual cropped region (after applying rotation + flips),
     * used by the Crop tool's "eye" preview-now toggle so the user sees the
     * final crop composition instead of the full photo with a grid. Mirrors the
     * rotation/flip/crop steps of [render]. Returns null when nothing is
     * cropped. Delegates to [CropMountRenderer] (which owns the crop math).
     */
    fun cropPreview(
        input: Bitmap,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        cropRect: Rect?
    ): Bitmap? = CropMountRenderer.cropPreview(
        input, rotationDegrees, flipHorizontal, flipVertical, cropRect
    )

    /**
     * Applies rotation + flips to [input], returning a new bitmap. Delegates to
     * [CropMountRenderer] (which owns the transform math), kept on
     * [BitmapRenderer] for the parity tests.
     */
    internal fun transforms(
        input: Bitmap,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): Bitmap = CropMountRenderer.transforms(input, rotationDegrees, flipHorizontal, flipVertical)

    /**
     * Unsharp-mask sharpen/soften. Shifts each channel toward the local average
     * (soften) or away from it (sharpen) by [amount] in [-1, 1]. One effect, two
     * scales: the blur radius is a fraction of the live image (4px at a 500px
     * short edge — clearly visible on phone screens without large halos),
     * clamped only to keep the full-size pass cheap.
     */
    internal fun applySharpen(canvas: Bitmap, amount: Float) {
        val w = canvas.width
        val h = canvas.height
        if (w < 9 || h < 9) return
        val pixels = IntArray(w * h)
        canvas.getPixels(pixels, 0, w, 0, 0, w, h)
        val src = pixels.copyOf()
        val strength = (amount / 100f).coerceIn(-1f, 1f)
        val radius = sharpenRadius(minOf(w, h))
        val avg = StrokeOverlayRenderer.boxAverageImage(src, w, h, radius)

        for (idx in pixels.indices) {
            val base = src[idx]
            val a = avg[idx]
            val r = (Color.red(base) + strength * (Color.red(base) - Color.red(a)))
                .roundToInt().coerceIn(0, 255)
            val g = (Color.green(base) + strength * (Color.green(base) - Color.green(a)))
                .roundToInt().coerceIn(0, 255)
            val b = (Color.blue(base) + strength * (Color.blue(base) - Color.blue(a)))
                .roundToInt().coerceIn(0, 255)
            pixels[idx] = Color.argb(Color.alpha(base), r, g, b)
        }
        canvas.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * Dimension-agnostic unsharp-mask reach for [applySharpen]: a fraction of
     * the live image's short edge (4px at a 500px short edge), clamped only to
     * keep the full-size pass cheap. `internal` for tests.
     */
    internal fun sharpenRadius(shortEdge: Int): Int =
        (shortEdge * 4f / 500f).roundToInt().coerceIn(1, 16)

    /**
     * Dimension-agnostic blur-brush reach for the brush-blur overlay. Delegates
     * to [StrokeOverlayRenderer] (which owns the stroke math), kept on
     * [BitmapRenderer] for the parity tests.
     */
    internal fun brushBlurRadius(minDim: Int, radiusPx: Int): Int =
        StrokeOverlayRenderer.brushBlurRadius(minDim, radiusPx)
}