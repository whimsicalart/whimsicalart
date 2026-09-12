package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

import android.graphics.Bitmap
import codes.pepper.whimsicalart.feature.beauty.domain.ML_MAX_PIXELS
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyTool
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyPenEffect
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.domain.estimateStackOutputSize
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Owns the editor's GEOMETRY-TRACK planning policy (docs/effect_stack.md →
 * Original geometry image and original geometry): the pure deciders behind the
 * beauty/background ML pipeline — where the currently-selected tool lands in the
 * stack ([currentToolIndex]), which geometry-changing effects precede the
 * background layer ([backgroundGeometryEffects]), the preview-resolution segment-0
 * base ([geometryTrackSegment0Base]), the full-size ML pixel budget and its
 * adaptive fold scale ([geometryPixelCountFor]/[geometryTrackScaleFor]/
 * [resolverPixelHintFor]), and the footprint-adaptive background artifact
 * rescaling ([adaptBackgroundTo]/[scaledArtifact]).
 *
 * The class is PASSIVE and single-purpose: it keeps no coroutines, no ML
 * pipeline and no fold cache. Its state dependencies — the original geometry
 * image (pristine decode / merged re-ground), the currently selected tool and
 * the live effect stack — arrive as [originalGeometryImage]/[selectedTool]/
 * [effectStack] suppliers so the ViewModel remains the source of truth. The
 * geometry-track FOLD runner and its bounded intermediate cache stay in the
 * ViewModel (that is orchestration, not policy).
 */
internal class GeometryTracker(
    private val originalGeometryImage: () -> Bitmap?,
    private val selectedTool: () -> EditTool?,
    private val selectedBeautyTool: () -> BeautyTool?,
    private val effectStack: () -> List<StackEffect>
) {

    /**
     * Small memoized scale-artifact cache (cleared on photo change via
     * [clearScaledArtifactCache]): full-size artifacts rescaled to a matching
     * footprint (background subject masks + replacement images). [source] is
     * always a full-size artifact owned elsewhere, so the result is a NEW bitmap
     * held by the cache and never recycled while the photo lives.
     */
    private val scaledArtifactCache = HashMap<String, Bitmap>()

    /**
     * Index into [effects] of the currently-selected tool's FIRST effect; if the
     * tool has no effect in the stack yet (e.g. it was just opened), returns
     * [effects.size] so it is treated as a new effect appended at the end.
     *
     * The beauty pen is ONE sub-layer of the multi-layer BEAUTY group (all
     * beauty sub-layers share `tool == EditTool.BEAUTY`), so its own layer is
     * the first `beauty:pen` — the index lands ON the pen layer, keeping the
     * other beauty layers below it in the folded base.
     */
    fun currentToolIndex(effects: List<StackEffect>): Int {
        val tool = selectedTool() ?: return effects.size
        val idx = if (tool == EditTool.BEAUTY &&
            selectedBeautyTool() == BeautyTool.BRIGHTNESS_PEN
        ) {
            effects.indexOfFirst { it is BeautyPenEffect }
        } else {
            effects.indexOfFirst { it.tool == tool }
        }
        return if (idx == -1) effects.size else idx
    }

    /**
     * The geometry-changing effects of the stack UP TO the background layer's
     * position — the shared geometry context's base source for the subject mask
     * (docs/effect_stack.md → Shared geometry context (beauty + background)):
     * same geometry-track fold the beauty tools use, so a mask is never taken
     * over the raw original/resident bitmap. When a committed background layer
     * exists the position is that layer's index (its mask re-resolves against
     * the same base it renders over); otherwise the background is treated as
     * landing at the END of the current stack, so the mask covers whatever
     * deformation the run will already contain.
     */
    fun backgroundGeometryEffects(): List<StackEffect> {
        val stack = effectStack()
        val backgroundIndex = stack.indexOfFirst { it is BackgroundEffect }
        val upTo = if (backgroundIndex >= 0) backgroundIndex else stack.size
        return stack.take(upTo).filter { it.changesGeometry }
    }

    /**
     * The geometry-track segment-0 base at [scale]: the original geometry image
     * itself (scale ≥ 1) or a preview-resolution downscale of it (memoized via
     * [scaledArtifact]). Its dims align with the colour fold base at the same
     * scale, so the resolved geometry's coordinates match the pixels the beauty
     * layer renders over. Never stored in the ViewModel's geometry-track cache
     * (it is owned by the decode / the tracker's artifact cache); folded segments
     * descend from it.
     */
    fun geometryTrackSegment0Base(scale: Float): Bitmap? {
        val original = originalGeometryImage() ?: return null
        if (scale >= 1f) return original
        val w = (original.width * scale).roundToInt().coerceAtLeast(1)
        val h = (original.height * scale).roundToInt().coerceAtLeast(1)
        return scaledArtifact(original, w, h)
    }

    /**
     * The FULL-SIZE pixel count (`width × height`) of the geometry-track
     * segment base for [geomEffects] — the original geometry image's dimensions
     * folded through the effects' dimension estimators ([estimateStackOutputSize];
     * the same pure, allocation-free approach the crop tool uses to know the final
     * dimensions) — never the running base's own resolution (a preview fold
     * passes a downscaled base). The beauty resolver evaluates its adaptive
     * ML-input pixel budget against this, so the desired ML size tracks the
     * FINAL content footprint (the pipeline's compute/memory scale with area,
     * not any single linear dimension) and is recomputed whenever a
     * size-changing effect (crop / transform with rotate / background) changes
     * it or a merge re-grounds the original geometry image.
     */
    fun geometryPixelCountFor(geomEffects: List<StackEffect>): Int {
        val original = originalGeometryImage() ?: return 0
        val (w, h) = estimateStackOutputSize(geomEffects, original.width, original.height)
        return w * h
    }

    /**
     * The GEOMETRY-TRACK fold scale for [geomEffects] on the FULL-SIZE path
     * (docs/effect_stack.md → Original geometry image and original geometry →
     * Lazy beauty geometry → the adaptive 1–4 MP budget): `1` for a
     * footprint at or under the [ML_MAX_PIXELS] cap, otherwise the uniform
     * `sqrt(cap / footprint)` scale that brings the folded segment base inside
     * the ML budget. The resolver runs its detection at that base's own
     * resolution (see [resolverPixelHintFor]) and the editor rescales the
     * resolved geometry up to the full-size colour fold the beauty layer renders
     * over — so a multi-MP source NEVER folds a deforming effect at full size
     * and NEVER allocates a full-size deformation intermediate on the
     * committed / zoom>100% / save path. Preview-scale binds (scale < 1) and
     * the merge re-ground keep today's behavior (the merge deliberately
     * re-folds at full size; the two-stage preview-then-full flow still binds
     * against the preview-resolution base).
     */
    fun geometryTrackScaleFor(geomEffects: List<StackEffect>): Float {
        val footprint = geometryPixelCountFor(geomEffects)
        if (footprint <= 0 || footprint <= ML_MAX_PIXELS) return 1f
        return sqrt(ML_MAX_PIXELS.toDouble() / footprint).toFloat()
    }

    /**
     * The reference pixel count the beauty resolver's adaptive ML-input budget
     * is evaluated against for a geometry-track [base] (see the ViewModel's
     * geometry-track fold and its shared [GeometryTracker]): the base's OWN
     * pixel count whenever it is already at or under the [ML_MAX_PIXELS] cap —
     * which, after the geometry-track cap, is every preview and every capped
     * committed base — so ML runs the FULL base (no second downscale) and the
     * preview and the capped full-quality pass detect at the same input
     * resolution (coordinates stay consistent within rounding). Only a base the
     * caller folds at FULL size above the cap (the merge re-ground, or a
     * legacy/pre-commit call) falls back to the full-size footprint of
     * [geomEffects] exactly as the resolver behaved before this cap existed.
     */
    fun resolverPixelHintFor(base: Bitmap, geomEffects: List<StackEffect>): Int {
        val basePixels = base.width * base.height
        return if (basePixels <= ML_MAX_PIXELS) basePixels else geometryPixelCountFor(geomEffects)
    }

    /**
     * Rescales a background layer's subject mask / replacement image to the
     * current render footprint so the artifacts align with the pixels they are
     * composited over. The subject mask is a hard-edged binary (alphas 0/255),
     * so a plain bilinear downscale is exact for the hard edge; the replacement
     * image is a photograph, so a bilinear downscale is the same quality the
     * base decode got. When the artifacts already match the footprint, [effect]
     * is returned unchanged.
     */
    fun adaptBackgroundTo(current: Bitmap, effect: BackgroundEffect): BackgroundEffect {
        val mask = effect.subjectMask
        val bg = effect.backgroundImage
        val needMask = mask?.takeIf {
            it.width != current.width || it.height != current.height
        }
        val needBg = bg?.takeIf {
            it.width != current.width || it.height != current.height
        }
        if (needMask == null && needBg == null) return effect
        return effect.copy(
            subjectMask = if (needMask != null) scaledArtifact(mask, current.width, current.height) else mask,
            backgroundImage = if (needBg != null) scaledArtifact(bg, current.width, current.height) else bg
        )
    }

    /**
     * Returns [source] scaled to the target footprint, memoized in a small cache
     * (cleared on photo change). [source] is always a full-size artifact owned
     * elsewhere, so the result is a NEW bitmap held by the cache and never
     * recycled while the photo lives. [source] itself is returned unchanged when
     * it already matches.
     */
    fun scaledArtifact(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (source.isRecycled) return null
        if (source.width == targetWidth && source.height == targetHeight) return source
        val key = "${source.hashCode()}:${targetWidth}x$targetHeight"
        scaledArtifactCache[key]?.let { if (!it.isRecycled) return it }
        if (scaledArtifactCache.size > 12) {
            scaledArtifactCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
            scaledArtifactCache.clear()
        }
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        scaledArtifactCache[key] = scaled
        return scaled
    }

    /** Clears the scale-artifact cache without recycling (called on photo change). */
    fun clearScaledArtifactCache() {
        scaledArtifactCache.clear()
    }
}