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
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilterProcessor
import codes.pepper.whimsicalart.feature.editor.domain.matting.BackgroundReplacer
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import java.util.UUID

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
 * If a single chosen layer needs several pixel applications (e.g. face
 * re-shape which combines slim+enlarge+nose+jaw), those are kept as parameters
 * of that ONE effect and all applied inside its single [render] implementation.
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
     * Whether applying this effect **changes the image geometry** — the pixel
     * footprint / face space the image lives in — rather than only its colour.
     * True for: crop / transform / rotate-flip, the face-reshape beauty
     * dimensions (slim / eye enlarge / nose / jaw), occluding overlays
     * (stickers / text / frames), and hard-stroke brushes (pen / mosaic /
     * object removal). False for colour / adjustment / filter effects,
     * face-makeup brushes (they map onto existing facial geometry) and the
     * blur brush (does not hard-cover the subject). Shared by the beauty-geometry
     * segment tracking ([renderStackWithBeautyGeometry]), stack-reorder staleness
     * and the merge-time geometry-base regeneration — the single source of truth
     * replacing the previous pile of hardcoded `when` lists. (Some tools'
     * behaviour in this set is expected to change in the future; this is the
     * current definition.)
     */
    val changesGeometry: Boolean get() = false
}

/** The immutable source image the whole stack is folded over. */
data class StackRoot(
    val image: Bitmap? = null
) : StackEffect {
    override val id: String get() = ROOT_ID
    override val tool: EditTool get() = EditTool.CROP
    override fun render(current: Bitmap, context: Context): Bitmap = image ?: current

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

    override fun render(current: Bitmap, context: Context): Bitmap {
        return when (tool) {
            EditTool.SHARPEN -> BitmapRenderer.applySharpenCopy(current, value)
            EditTool.VIGNETTE -> BitmapRenderer.applyVignetteCopy(current, value)
            else -> {
                val matrix = EditorColorMatrix.singleAdjustmentMatrix(tool, value)
                BitmapRenderer.applyMatrixPass(current, matrix)
            }
        }
    }
}

/** The filter-strip look (image-wide color matrix). */
data class FilterEffect(
    override val id: String = UUID.randomUUID().toString(),
    val filterMatrix: FloatArray?
) : StackEffect {
    override val tool: EditTool get() = EditTool.FILTERS
    override val layerKey: String get() = "layer:FILTER"

    override fun render(current: Bitmap, context: Context): Bitmap =
        BitmapRenderer.applyMatrixPass(current, filterMatrix)
}

/** One-tap Auto-Enhance / HDR. */
data class EnhanceEffect(
    override val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean,
    val settings: EnhanceSettings = EnhanceSettings()
) : StackEffect {
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

/** Learned-look style layer. */
data class StyleEffect(
    override val id: String = UUID.randomUUID().toString(),
    val filter: StyleFilter?
) : StackEffect {
    override val tool: EditTool get() = EditTool.FILTERS

    override fun render(current: Bitmap, context: Context): Bitmap {
        val filter = filter ?: return current
        val styled = StyleFilterProcessor.apply(current, filter)
        if (styled === current) return current
        val out = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(styled, 0f, 0f, null)
        styled.recycle()
        return out
    }
}

/** Subject-aware background blur / replacement. */
data class BackgroundEffect(
    override val id: String = UUID.randomUUID().toString(),
    val mode: BackgroundMode = BackgroundMode.BLUR,
    val subjectMask: Bitmap? = null,
    val blurRadius: Float = 10f,
    val shape: BokehShape = BokehShape.CIRCLE,
    val backgroundImage: Bitmap? = null
) : StackEffect {
    override val tool: EditTool get() = EditTool.BACKGROUND

    override fun render(current: Bitmap, context: Context): Bitmap {
        val mask = subjectMask ?: return current
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        when (mode) {
            BackgroundMode.BLUR -> {
                val radiusPx = (blurRadius * out.width).coerceAtLeast(1f)
                BokehProcessor().applyBackgroundBlur(
                    original = out,
                    foregroundMask = mask,
                    blurRadius = radiusPx,
                    bokehShape = shape
                )
            }
            BackgroundMode.REPLACE -> backgroundImage?.let {
                BackgroundReplacer.composite(out, mask, it)
            }
        }
        return out
    }
}

/** Frames / decorative border applied over the photo. */
data class FrameEffect(
    override val id: String = UUID.randomUUID().toString(),
    val layer: FrameLayer? = null
) : StackEffect {
    override val tool: EditTool get() = EditTool.FRAMES
    override val changesGeometry: Boolean get() = true

    override fun render(current: Bitmap, context: Context): Bitmap {
        val frame = layer ?: return current
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        BitmapRenderer.drawFrames(out, listOf(frame))
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

    // Hard-stroke brushes (pen / mosaic / object removal) hard-cover the subject
    // and occlude face space → they change geometry; the blur brush does not.
    override val changesGeometry: Boolean
        get() = tool == EditTool.PEN ||
            tool == EditTool.MOSAIC ||
            tool == EditTool.OBJECT_REMOVAL

    override fun render(current: Bitmap, context: Context): Bitmap {
        if (strokes.isEmpty()) return current
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        BitmapRenderer.drawStrokes(out, strokes)
        return out
    }
}

/** Stick-on decorations. */
data class StickerEffect(
    override val id: String = UUID.randomUUID().toString(),
    val layers: List<StickerLayer> = emptyList()
) : StackEffect {
    override val tool: EditTool get() = EditTool.STICKERS
    override val changesGeometry: Boolean get() = true

    override fun render(current: Bitmap, context: Context): Bitmap {
        if (layers.isEmpty()) return current
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        BitmapRenderer.drawStickers(context, out, layers)
        return out
    }
}

/** Text overlays. */
data class TextEffect(
    override val id: String = UUID.randomUUID().toString(),
    val layers: List<TextLayer> = emptyList()
) : StackEffect {
    override val tool: EditTool get() = EditTool.TEXT
    override val changesGeometry: Boolean get() = true

    override fun render(current: Bitmap, context: Context): Bitmap {
        if (layers.isEmpty()) return current
        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        BitmapRenderer.drawTexts(context, out, layers)
        return out
    }
}

/** A merged (flattened) layer produced by "Merge Layers". */
data class MergedEffect(
    override val id: String = UUID.randomUUID().toString(),
    val image: Bitmap? = null
) : StackEffect {
    override val tool: EditTool get() = EditTool.CROP
    override val layerKey: String get() = "layer:MERGED"

    override fun render(current: Bitmap, context: Context): Bitmap = image ?: current
}

/** Rotation / flip of the photo. */
data class TransformEffect(
    override val id: String = UUID.randomUUID().toString(),
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) : StackEffect {
    override val tool: EditTool get() = EditTool.TRANSFORM
    override val layerKey: String get() = "layer:TRANSFORM"
    override val changesGeometry: Boolean get() = true

    override fun render(current: Bitmap, context: Context): Bitmap =
        BitmapRenderer.transforms(current, rotation, flipHorizontal, flipVertical)
}

/** Crop of the photo (region is normalized [0..1]). */
data class CropEffect(
    override val id: String = UUID.randomUUID().toString(),
    val rect: Rect? = null
) : StackEffect {
    override val tool: EditTool get() = EditTool.CROP
    override val changesGeometry: Boolean get() = true

    override fun render(current: Bitmap, context: Context): Bitmap {
        val rect = rect ?: return current
        return BitmapRenderer.crop(current, rect)
    }
}

/** Skin denoise (wavelet), a main-toolbar edit adjacent to filters. */
data class SkinDenoiseEffect(
    override val id: String = UUID.randomUUID().toString(),
    val intensity: Float = 0f
) : StackEffect {
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
