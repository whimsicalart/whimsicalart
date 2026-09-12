package codes.pepper.whimsicalart.feature.editor.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import codes.pepper.whimsicalart.feature.beauty.domain.SkinDenoiseProcessor
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehProcessor
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehShape
import codes.pepper.whimsicalart.feature.editor.domain.enhance.EnhanceProcessor
import codes.pepper.whimsicalart.feature.editor.domain.enhance.EnhanceSettings
import codes.pepper.whimsicalart.feature.editor.domain.filter.LensFilterProcessor
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilterProcessor
import codes.pepper.whimsicalart.feature.editor.domain.matting.BackgroundReplacer
import codes.pepper.whimsicalart.feature.editor.domain.matting.SoftEdgeBand
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.stickers.domain.StickerItem
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A single entry in the editor's ordered, non-destructive effect stack.
 *
 * Each effect fully captures the parameters it needs to re-create its output on
 * top of whichever image precedes it in the stack, exposed through the single
 * [render] method: given the CURRENT running image (the output of the previous
 * layer, or the base when first) and its own parameters, it returns the next
 * image. Because each effect is self-contained, the stack folds left-to-right
 * in [StackEffect] order and order therefore matters — effect _N_ input is the
 * output of effects _0..N-1_.
 *
 * If a single chosen layer needs several pixel applications (e.g. the photo-look
 * filter layer which combines color + style + lens matrices), those are kept as
 * parameters of that ONE effect and all applied inside its single [render]
 * implementation.
 *
 * Brush tools store a *painted diff layer* ([BitmapDiffEffect]) rather than
 * their individual strokes — the renderer rasterizes the strokes once and
 * applies them as a whole, so a stack entry never grows with stroke count.
 */
sealed interface StackEffect {

    /** Stable identity used by the Layers UI, removal and undo/redo. */
    val id: String

    /** The tool that produced this effect (drives label + thumbnail + re-edit). */
    val tool: EditTool

    /**
     * A stable key identifying this effect's *layer* across recompositions, for
     * thumbnail/fingerprint caching. Unlike [id] (regenerated each time the screen
     * re-snapshots the stack), the layer key only changes when the effect's
     * parameters change, so unchanged layers keep their cached thumbnail while
     * stale layers (and everything after them) are recomputed.
     */
    val layerKey: String
        get() = "layer:${tool}"

    /**
     * Applies this effect to the [current] running image and returns the next
     * image for the fold. The default is a no-op (returns [current]);
     * transforming effects return a NEW bitmap (leaving [current] owned by the
     * fold), while overlay/draw-in-place effects mutate [current] and return it.
     */
    fun render(current: Bitmap, context: Context): Bitmap = current

    /**
     * Estimates the output dimensions of applying this effect to an input of
     * [inputWidth] × [inputHeight] pixels, WITHOUT touching a pixel — it never
     * runs a fold or allocates a bitmap (docs/effect_stack.md → *Choosing
     * preview resolution*). The default returns the input unchanged, which holds
     * for every colour/adjustment/overlay effect that draws in place (filters,
     * enhancers, styles, background, brushes, stickers, text, frames, beauty).
     * Dimension-affecting effects mirror the integer geometry of their [render]
     * implementation exactly:
     * - [TransformEffect]: swapped dimensions for an exact 90° turn, otherwise
     *   the grown bounding-canvas dimensions of the rotated photo.
     * - [CropEffect]: its selected region scaled against the input dimensions.
     *
     * The per-layer preview resolution is the prefix fold of these estimators
     * over the stack from the pristine source — a crop is normalized against the
     * already rotated/transformed composite, exactly as in the fold renderer.
     */
    fun estimateOutputSize(inputWidth: Int, inputHeight: Int): Pair<Int, Int> =
        inputWidth to inputHeight

    /**
     * Whether applying this effect **changes the image geometry** — the pixel
     * footprint / face space the image lives in — rather than only its colour.
     * True for: crop / transform / rotate-flip, the face-reshape beauty
     * dimensions (slim / eye enlarge / nose / jaw), occluding overlays
     * (stickers / text / frames), hard-stroke brushes (pen / mosaic /
     * object removal), and a background Replace WITH a placement image (the
     * placement moves/scales/rotates the cut-out subject over the backdrop).
     * False for colour / adjustment / filter effects, face-makeup brushes
     * (they map onto existing facial geometry), the blur brush (does not
     * hard-cover the subject) and a background Blur / Replace without a
     * placement image. Shared by the beauty-geometry segment tracking
     * ([renderStackWithBeautyGeometry]), stack-reorder staleness and the
     * merge-time geometry-base regeneration — the single source of truth
     * replacing the previous pile of hardcoded `when` lists. (Some tools'
     * behaviour in this set is expected to change in the future; this is the
     * current definition.)
     */
    val changesGeometry: Boolean get() = false

    /**
     * The committed preview bitmap for this layer, **carried by the layer itself**
     * (docs/effect_stack.md → Performance / preview cache → *Per-layer preview
     * cache*). A preview image assigned when the layer is committed and the layer
     * previews are (re)built: the layer's fold result at preview resolution, and
     * the base the NEXT layer folds from (the first layer folds from the pristine
     * source shrunk to preview). It is a session cache, not a parameter:
     *
     * - never serialized (undo/redo restores the same effect object, so its
     *   committed preview survives for the session);
     * - recycled/evicted under memory pressure ([LAYER_PREVIEW_CACHE_MAX]) or on
     *   staleness — reorder / removal / re-edit / preview-resolution change — and
     *   set back to null there so a stale preview is never resumed from;
     * - null until the preview builder has run over this layer.
     */
    var committedPreview: Bitmap?
}

/** The immutable source image the whole stack is folded over. */
data class StackRoot(
    val image: Bitmap? = null
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val id: String get() = ROOT_ID
    override val tool: EditTool get() = EditTool.CROP
    override fun render(current: Bitmap, context: Context): Bitmap = image ?: current

    override fun estimateOutputSize(inputWidth: Int, inputHeight: Int): Pair<Int, Int> =
        image?.let { it.width to it.height } ?: (inputWidth to inputHeight)

    companion object {
        const val ROOT_ID = "__root__"
    }
}

/**
 * A single color-adjustment effect. Non-matrix tools (sharpen / vignette) run as
 * their own pixel pass; the rest build a single-parameter color matrix. Each
 * slider is its own stack entry, so effects apply in the order they were added.
 */
data class SingleAdjustmentEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val tool: EditTool,
    val value: Float
) : StackEffect {
    override var committedPreview: Bitmap? = null

    override fun render(current: Bitmap, context: Context): Bitmap {
        return when (tool) {
            EditTool.SHARPEN -> BitmapRenderer.applySharpenCopy(current, value)
            EditTool.VIGNETTE -> VignetteRenderer.applyVignetteCopy(current, value)
            else -> {
                val matrix = EditorColorMatrix.singleAdjustmentMatrix(tool, value)
                BitmapRenderer.applyMatrixPass(current, matrix)
            }
        }
    }
}

/** The filter-strip look (image-wide color matrix + real lens passes). */
data class FilterEffect(
    override val id: String = UUID.randomUUID().toString(),
    val filterMatrix: FloatArray?,
    val lensPassIds: Set<String> = emptySet()
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.FILTERS
    override val layerKey: String get() = "layer:FILTER"

    override fun render(current: Bitmap, context: Context): Bitmap {
        if (filterMatrix == null && lensPassIds.isEmpty()) return current
        val pre = BitmapRenderer.applyMatrixPass(current, filterMatrix)
        if (lensPassIds.isEmpty()) return pre
        val out = LensFilterProcessor.applyLensPasses(pre, lensPassIds)
        if (out !== pre && !pre.isRecycled) pre.recycle()
        return out
    }
}

/** One-tap Auto-Enhance / HDR. */
data class EnhanceEffect(
    override val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean,
    val settings: EnhanceSettings = EnhanceSettings()
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.ENHANCE

    override fun render(current: Bitmap, context: Context): Bitmap {
        if (!enabled) return current
        val enhanced = EnhanceProcessor.enhance(current, settings)
        if (enhanced === current) return current
        val out = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(enhanced, 0f, 0f, null)
        if (enhanced != null) enhanced.recycle()
        return out
    }
}

/**
 * Learned-look style layer. **Filters is a SINGLE effect** (docs/effects_reference.md
 * → *Filters*): the color, style and lens options are controls of one `layer:FILTER`
 * layer, so a selected style carries the lens/color matrix along with it — the
 * matrix pass runs first, then the style tone-map pass, under the same key as a
 * plain color `FilterEffect`.
 */
data class StyleEffect(
    override val id: String = UUID.randomUUID().toString(),
    val filter: StyleFilter?,
    val filterMatrix: FloatArray? = null,
    val lensPassIds: Set<String> = emptySet()
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.FILTERS
    override val layerKey: String get() = "layer:FILTER"

    override fun render(current: Bitmap, context: Context): Bitmap {
        val matrix = filterMatrix
        val filter = filter
        if (matrix == null && filter == null && lensPassIds.isEmpty()) return current
        var pre = if (matrix != null) {
            BitmapRenderer.applyMatrixPass(current, matrix)
        } else {
            current
        }
        if (lensPassIds.isNotEmpty()) {
            val passOut = LensFilterProcessor.applyLensPasses(pre, lensPassIds)
            if (passOut !== pre && pre !== current && !pre.isRecycled) pre.recycle()
            pre = passOut
        }
        if (filter == null) return pre
        // StyleFilterProcessor.apply returns a fresh bitmap owned by this effect.
        val out = StyleFilterProcessor.apply(pre, filter)
        if (pre !== current && !pre.isRecycled) pre.recycle()
        return out
    }
}

/**
 * Maps the `0..100` background-blur intensity percentage to a blur radius in
 * pixels: `100` ≡ the former value `20`, i.e. `0.20 × width` (so `0` = no
 * blur, `50` = `0.10 × width`, `100` = `0.20 × width`). Every blur radius must
 * be computed through this single mapping so the preview (`EditorViewModel`)
 * and the committed fold (`BitmapRenderer`) stay identical
 * (docs/effects_reference.md → *Background* → *Blur intensity*).
 */
internal fun backgroundBlurRadiusPx(blurRadius: Float, width: Int): Float =
    blurRadius / 100f * 0.20f * width

/** Subject-aware background blur / replacement. */
data class BackgroundEffect(
    override val id: String = UUID.randomUUID().toString(),
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
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.BACKGROUND

    // REPLACE-with-a-placement transforms the cut-out SUBJECT (move / scale /
    // rotate over the static backdrop, via BackgroundReplacer.place) instead of
    // the backdrop, so it re-locates the face space the image lives in; a BLUR
    // mode or a REPLACE that never got a placement image keeps the original
    // geometry. Mirrors the BitmapDiffEffect computed-getter pattern (Sticker /
    // Text / Frame are always deforming; this one is conditional).
    override val changesGeometry: Boolean
        get() = mode == BackgroundMode.REPLACE && backgroundImage != null

    override fun render(current: Bitmap, context: Context): Bitmap {
        val mask = subjectMask ?: return current
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        when (mode) {
            BackgroundMode.BLUR -> {
                val radiusPx = backgroundBlurRadiusPx(blurRadius, out.width)
                // applyBackgroundBlur copies `out`, blurs the copy and returns it;
                // the old code discarded the return value and returned the
                // unmodified `out`, so the fold never showed the defocus at all —
                // matching the device report "blur ... visibly do nothing" (same
                // discard bug the REPLACE branch below had). Recycle the working
                // copy and surface the blurred result.
                val blurred = BokehProcessor().applyBackgroundBlur(
                    original = out,
                    foregroundMask = mask,
                    blurRadius = radiusPx,
                    bokehShape = shape,
                    softEdgeWidth = SoftEdgeBand.DEFAULT_WIDTH
                )
                if (blurred !== out) {
                    out.recycle()
                }
                return blurred
            }
            BackgroundMode.REPLACE -> backgroundImage?.let {
                val placed = BackgroundReplacer.place(
                    out,
                    mask,
                    it,
                    BackgroundReplacer.PlacementTransform(
                        scale = placementScale,
                        panX = placementPanX,
                        panY = placementPanY,
                        rotationDegrees = placementRotationDegrees,
                        freeScaleUp = freeScaleUp
                    ),
                    softEdgeWidth = SoftEdgeBand.DEFAULT_WIDTH
                )
                // place() returns a NEW bitmap (same size as out for the
                // default fit, or GROWN when the placement is scaled up or
                // rotated so nothing is cropped). The old code discarded the
                // return value and returned the unchanged `out`, so the fold
                // never showed the replacement at all — matching the device
                // report "blur and replace/remove visibly do nothing."
                out.recycle()
                return placed
            }
        }
        return out
    }

    /**
     * A scaled-up or rotated placement grows the committed bitmap to fit
     * (nothing is ever cropped), so dimension-affecting Replace follows the
     * exact canvas-growth geometry of [BackgroundReplacer.place]; Blur and a
     * within-fit Replace stay in place.
     */
    override fun estimateOutputSize(inputWidth: Int, inputHeight: Int): Pair<Int, Int> =
        if (mode == BackgroundMode.REPLACE && backgroundImage != null &&
            (placementScale > 1f || kotlin.math.abs(placementRotationDegrees) > 1e-3f)
        ) {
            // Keep the estimator and the renderer in lockstep by clamping the
            // scale the same way [BackgroundReplacer.place] does (freeScaleUp
            // gates any enlargement above the fit size).
            BackgroundReplacer.placedCanvasSize(
                inputWidth,
                inputHeight,
                BackgroundReplacer.clampPlacementScale(placementScale, freeScaleUp),
                placementRotationDegrees
            )
        } else {
            inputWidth to inputHeight
        }
}

/** Frames / decorative border applied over the photo. */
data class FrameEffect(
    override val id: String = UUID.randomUUID().toString(),
    val layer: FrameLayer? = null
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.FRAMES
    override val changesGeometry: Boolean get() = true

    override fun estimateOutputSize(inputWidth: Int, inputHeight: Int): Pair<Int, Int> {
        val fr = layer ?: return inputWidth to inputHeight
        val extent = fr.outerExtentPx(inputWidth, inputHeight)
        if (extent > 0) return (inputWidth + 2 * extent) to (inputHeight + 2 * extent)
        return inputWidth to inputHeight
    }

    override fun render(current: Bitmap, context: Context): Bitmap {
        val frame = layer ?: return current
        // A recycled `current` (a racing cache owner recycled the fold base) must
        // never be read here: `current.width` on a recycled bitmap throws and
        // kills the app — the exact crash seen when a frame is the very FIRST
        // effect and the fold base is recycled before it renders. Leave the
        // composite unchanged rather than crash.
        if (current.isRecycled) return current
        // Draw into a fresh ARGB canvas instead of `current.copy(...)`: the
        // committed pixel pass (BitmapRenderer.drawFrames) reads + writes every
        // pixel, and a bitmap produced by copy() of a rotation-baked source can
        // be pixel-less on some software backends (Robolectric legacy shadow).
        // When the OUTER tier is enabled the canvas grows by 2× the outer extent
        // and the current image is inset to the centre (the inner tier then
        // draws inward over the photo, the outer band fills the ring beyond).
        val extent = frame.outerExtentPx(current.width, current.height)
        val out = Bitmap.createBitmap(
            current.width + 2 * extent,
            current.height + 2 * extent,
            Bitmap.Config.ARGB_8888
        )
        android.graphics.Canvas(out).drawBitmap(current, extent.toFloat(), extent.toFloat(), null)
        FrameOverlayRenderer.drawFrames(out, listOf(frame), insetX = extent, insetY = extent)
        return out
    }
}

/**
 * A brush / pixel-editing layer whose effect is carried as a normalized stroke
 * list ([strokes]) — the renderer rasterizes it once over the running stack
 * result. The doc's *painted diff bitmap* ([diff]) is an optional pre-baked
 * equivalent used at merge time; until then the strokes are the source of truth
 * so successive layers stay reversible and re-applied in order.
 */
data class BitmapDiffEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val tool: EditTool,
    val diff: Bitmap? = null,
    val strokes: List<StrokeLayer> = emptyList(),
    val suggestedRegions: List<RectF> = emptyList()
) : StackEffect {
    override var committedPreview: Bitmap? = null

    // Hard-stroke brushes (pen / mosaic / object removal) hard-cover the subject
    // and occlude face space → they change geometry; the blur brush does not.
    override val changesGeometry: Boolean
        get() = tool == EditTool.PEN ||
            tool == EditTool.MOSAIC ||
            tool == EditTool.OBJECT_REMOVAL

    override fun render(current: Bitmap, context: Context): Bitmap {
        if (strokes.isEmpty()) return current
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        StrokeOverlayRenderer.drawStrokes(out, strokes)
        return out
    }
}

/** Stick-on decorations. Parameters are normalized [StickerItem] placements. */
data class StickerEffect(
    override val id: String = UUID.randomUUID().toString(),
    val layers: List<StickerItem> = emptyList()
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.STICKERS
    override val changesGeometry: Boolean get() = true

    override fun render(current: Bitmap, context: Context): Bitmap {
        if (layers.isEmpty()) return current
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        StickerOverlayRenderer.drawStickers(context, out, layers)
        return out
    }
}

/**
 * Text overlays. [layers] is the parameterised `TextItem` list — the source of
 * truth on this stack entry — so returning to the tool restores every item for
 * re-editing until Merge Layers bakes the layer into pixels
 * (docs/effects_reference.md → *Text*).
 */
data class TextEffect(
    override val id: String = UUID.randomUUID().toString(),
    val layers: List<TextItem> = emptyList()
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.TEXT
    override val changesGeometry: Boolean get() = true

    override fun render(current: Bitmap, context: Context): Bitmap {
        if (layers.isEmpty()) return current
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        TextOverlayRenderer.drawTexts(context, out, layers)
        return out
    }
}

/** A merged (flattened) layer produced by "Merge Layers". */
data class MergedEffect(
    override val id: String = UUID.randomUUID().toString(),
    val image: Bitmap? = null
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.CROP
    override val layerKey: String get() = "layer:MERGED"

    override fun render(current: Bitmap, context: Context): Bitmap = image ?: current

    override fun estimateOutputSize(inputWidth: Int, inputHeight: Int): Pair<Int, Int> =
        image?.let { it.width to it.height } ?: (inputWidth to inputHeight)
}

/** Rotation / flip of the photo. */
data class TransformEffect(
    override val id: String = UUID.randomUUID().toString(),
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.TRANSFORM
    override val layerKey: String get() = "layer:TRANSFORM"
    override val changesGeometry: Boolean get() = true

    override fun render(current: Bitmap, context: Context): Bitmap =
        CropMountRenderer.transforms(current, rotation, flipHorizontal, flipVertical)

    /**
     * Mirrors the integer geometry of [CropMountRenderer.transforms]. An exact 90°
     * turn (or 270°) swaps the dimensions; 0° / 180° and any flips keep them.
     * Any other (free/custom) angle grows the canvas to the rotated bounding box
     * — x-extent = W·|cosθ| + H·|sinθ|, y-extent = W·|sinθ| + H·|cosθ| — the
     * same integer result as the `Matrix.mapRect` over the source rect, computed
     * here without allocating a Matrix so the estimate stays allocation-free.
     */
    override fun estimateOutputSize(inputWidth: Int, inputHeight: Int): Pair<Int, Int> {
        val normalized = ((rotation % 360f) + 360f) % 360f
        val quarterTurns = (normalized / 90f).roundToInt() % 4
        val isExact90 = (normalized % 90f).let { it < 0.5f || it > 89.5f }
        if (isExact90) {
            return if (quarterTurns % 2 == 1) inputHeight to inputWidth else inputWidth to inputHeight
        }
        val rad = Math.toRadians(normalized.toDouble())
        val cosA = abs(kotlin.math.cos(rad))
        val sinA = abs(kotlin.math.sin(rad))
        val w = (inputWidth * cosA + inputHeight * sinA).roundToInt().coerceAtLeast(1)
        val h = (inputWidth * sinA + inputHeight * cosA).roundToInt().coerceAtLeast(1)
        return w to h
    }
}

/** Crop of the photo (region is normalized [0..1]). */
data class CropEffect(
    override val id: String = UUID.randomUUID().toString(),
    val rect: Rect? = null
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.CROP
    override val changesGeometry: Boolean get() = true

    override fun render(current: Bitmap, context: Context): Bitmap {
        val rect = rect ?: return current
        return CropMountRenderer.crop(current, rect)
    }

    /**
     * Mirrors the integer geometry of [CropMountRenderer.crop]: the selected region
     * (normalized [0..1]) scaled against the input dimensions, floor-clamped to
     * at least 1px. Null rect is an identity.
     */
    override fun estimateOutputSize(inputWidth: Int, inputHeight: Int): Pair<Int, Int> {
        val rect = rect ?: return inputWidth to inputHeight
        val left = (rect.left * inputWidth).coerceIn(0f, inputWidth.toFloat())
        val top = (rect.top * inputHeight).coerceIn(0f, inputHeight.toFloat())
        val right = (rect.right * inputWidth).coerceIn(left, inputWidth.toFloat())
        val bottom = (rect.bottom * inputHeight).coerceIn(top, inputHeight.toFloat())
        val w = (right - left).roundToInt().coerceAtLeast(1)
        val h = (bottom - top).roundToInt().coerceAtLeast(1)
        return w to h
    }
}

/** Skin denoise (wavelet), a main-toolbar edit adjacent to filters. */
data class SkinDenoiseEffect(
    override val id: String = UUID.randomUUID().toString(),
    val intensity: Float = 0f
) : StackEffect {
    override var committedPreview: Bitmap? = null
    override val tool: EditTool get() = EditTool.SKIN_DENOISE

    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        // The slider exposes 0..1 while the wavelet denoiser is calibrated so a
        // higher softness PRESERVES more detail: invert so a larger slider value
        // produces a stronger effect.
        val softness = (1f - intensity).coerceIn(0f, 1f)
        return SkinDenoiseProcessor.denoise(current, softness)
    }
}

/**
 * Prefix-folds the size estimators of [effects] over a [startWidth] ×
 * [startHeight] base, projecting the output footprint of the whole stack (and,
 * layer by layer, of every prefix) WITHOUT touching pixels
 * (docs/effect_stack.md → *Choosing preview resolution*). One integer pass per
 * effect: a crop is normalized against the already transformed composite (its
 * input is whatever the estimators folded so far), exactly as in the fold
 * renderer. Never allocates a bitmap or runs a fold.
 */
internal fun estimateStackOutputSize(
    effects: List<StackEffect>,
    startWidth: Int,
    startHeight: Int
): Pair<Int, Int> {
    var w = startWidth
    var h = startHeight
    for (effect in effects) {
        val out = effect.estimateOutputSize(w, h)
        w = out.first
        h = out.second
    }
    return w to h
}
