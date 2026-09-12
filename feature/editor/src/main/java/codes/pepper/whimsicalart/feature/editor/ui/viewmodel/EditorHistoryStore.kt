package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Rect
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyParams
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode
import codes.pepper.whimsicalart.feature.editor.domain.FrameStyle
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehShape
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter

/**
 * A full snapshot of the editor's reversible state at a single commit point.
 * Used for stack-aware undo/redo: every meaningful user action pushes a
 * [StackDocument]; undo/redo restores the snapshot (effectStack + all live
 * controls), which the screen's LaunchedEffect then syncs back into the stack.
 *
 * Large bitmaps (originalBitmap, subjectMask, backgroundImage) are captured
 * by reference since they don't change between undo steps in normal usage.
 */
data class StackDocument(
    val effectStack: List<StackEffect>,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val sharpness: Float = 0f,
    val exposure: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vignette: Float = 0f,
    val skinDenoise: Float = 0f,
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropRect: Rect? = null,
    val innerFrameStyle: FrameStyle? = null,
    val innerFrameThickness: Float = 0.06f,
    val innerFrameColor: Int = Color.BLACK,
    val innerFrameEdgeSize: Float = 0.2f,
    val innerFrameCustomAngle: Float = 0f,
    val innerFrameCustomCurveDistance: Float = -0.5f,
    val innerFrameCustomStrength: Float = 0f,
    val innerFrameCustomCount: Float = 2f,
    val innerFrameLines: Int = 0,
    val innerFrameLinesEnabled: Boolean = false,
    val innerFrameLineDistance: Float = 0.02f,
    val innerFrameLineThickness: Float = 0.02f,
    val outerFrameStyle: FrameStyle? = null,
    val outerFrameThickness: Float = 0.06f,
    val outerFrameColor: Int = Color.BLACK,
    val outerFrameEdgeSize: Float = 0.2f,
    val outerFrameCustomAngle: Float = 0f,
    val outerFrameCustomCurveDistance: Float = -0.5f,
    val outerFrameCustomStrength: Float = 0f,
    val outerFrameCustomCount: Float = 2f,
    val outerFrameLines: Int = 0,
    val outerFrameLinesEnabled: Boolean = false,
    val outerFrameLineDistance: Float = 0.02f,
    val outerFrameLineThickness: Float = 0.02f,
    val enhanceEnabled: Boolean = false,
    val styleFilter: StyleFilter? = null,
    val backgroundMode: BackgroundMode = BackgroundMode.BLUR,
    val backgroundBlurRadius: Float = 50f,
    val backgroundShape: BokehShape = BokehShape.CIRCLE,
    val subjectMask: Bitmap? = null,
    val backgroundImage: Bitmap? = null,
    val selectedBackgroundRes: Int? = null,
    val backgroundPlacementScale: Float = 1f,
    val backgroundPlacementPanX: Float = 0f,
    val backgroundPlacementPanY: Float = 0f,
    val backgroundPlacementRotationDegrees: Float = 0f,
    val backgroundFreeScaleUp: Boolean = false,
    val selectedFilterId: String? = null,
    val enabledLensFilterIds: Set<String> = emptySet(),
    // Per-sub-tool beauty parameters (editor-owned), captured so undo/redo restores
    // both the effect stack AND the params that derive it.
    val beauty: BeautyParams = BeautyParams(),
    /**
     * What kind of user operation this checkpoint records (docs/effect_stack.md
     * → Undo / redo). Fine-grained control commits and brush strokes are
     * [HistoryKind.FINE_GRAINED]; document-structure edits (tool-switch baseline,
     * layer commit on lost focus, removal, reorder, merge) are
     * [HistoryKind.COARSE_GRAINED].
     * The per-kind caps in [EditorHistoryStore.push] evict only the oldest entries
     * of a kind, so coarse-grained layer commits/removals/moves survive far
     * longer than fine-grained tweaks while the relative event order is preserved.
     */
    val kind: HistoryKind = HistoryKind.FINE_GRAINED
)

/** The kind of user operation a [StackDocument] checkpoint records. */
enum class HistoryKind {
    FINE_GRAINED,
    COARSE_GRAINED
}

/** Bounds on the [StackDocument] list per [HistoryKind] (docs/effect_stack.md → Undo / redo). */
private const val FINE_GRAINED_LIMIT = 30
private const val COARSE_GRAINED_LIMIT = 100

/**
 * Owns the undo/redo history of the editor: [StackDocument] checkpoints, the
 * per-kind caps that bound the list, and the pure state transforms that push a
 * checkpoint or restore a document onto [EditorUiState]. Keeping these as pure
 * functions over the state makes the two caps and the undo/redo restore
 * independently unit-testable without instantiating the whole ViewModel.
 */
internal class EditorHistoryStore {

    /**
     * Records a [StackDocument] snapshot of [state] and returns the state with
     * the bounded history advanced. Pure: never mutates; the caller publishes
     * the returned state (drops the redo tail, applies per-kind caps).
     */
    fun push(state: EditorUiState, kind: HistoryKind): EditorUiState {
        val doc = StackDocument(
            effectStack = state.effectStack.toList(),
            brightness = state.brightness,
            contrast = state.contrast,
            saturation = state.saturation,
            sharpness = state.sharpness,
            exposure = state.exposure,
            shadows = state.shadows,
            highlights = state.highlights,
            temperature = state.temperature,
            tint = state.tint,
            vignette = state.vignette,
            skinDenoise = state.skinDenoise,
            rotation = state.rotation,
            flipHorizontal = state.flipHorizontal,
            flipVertical = state.flipVertical,
            cropRect = state.cropRect,
            innerFrameStyle = state.innerFrameStyle,
            innerFrameThickness = state.innerFrameThickness,
            innerFrameColor = state.innerFrameColor,
            innerFrameEdgeSize = state.innerFrameEdgeSize,
            innerFrameCustomAngle = state.innerFrameCustomAngle,
            innerFrameCustomCurveDistance = state.innerFrameCustomCurveDistance,
            innerFrameCustomStrength = state.innerFrameCustomStrength,
            innerFrameCustomCount = state.innerFrameCustomCount,
            innerFrameLines = state.innerFrameLines,
            innerFrameLinesEnabled = state.innerFrameLinesEnabled,
            innerFrameLineDistance = state.innerFrameLineDistance,
            innerFrameLineThickness = state.innerFrameLineThickness,
            outerFrameStyle = state.outerFrameStyle,
            outerFrameThickness = state.outerFrameThickness,
            outerFrameColor = state.outerFrameColor,
            outerFrameEdgeSize = state.outerFrameEdgeSize,
            outerFrameCustomAngle = state.outerFrameCustomAngle,
            outerFrameCustomCurveDistance = state.outerFrameCustomCurveDistance,
            outerFrameCustomStrength = state.outerFrameCustomStrength,
            outerFrameCustomCount = state.outerFrameCustomCount,
            outerFrameLines = state.outerFrameLines,
            outerFrameLinesEnabled = state.outerFrameLinesEnabled,
            outerFrameLineDistance = state.outerFrameLineDistance,
            outerFrameLineThickness = state.outerFrameLineThickness,
            enhanceEnabled = state.enhanceEnabled,
            styleFilter = state.styleFilter,
            backgroundMode = state.backgroundMode,
            backgroundBlurRadius = state.backgroundBlurRadius,
            backgroundShape = state.backgroundShape,
            subjectMask = state.subjectMask,
            backgroundImage = state.backgroundImage,
            selectedBackgroundRes = state.selectedBackgroundRes,
            backgroundPlacementScale = state.backgroundPlacementScale,
            backgroundPlacementPanX = state.backgroundPlacementPanX,
            backgroundPlacementPanY = state.backgroundPlacementPanY,
            backgroundPlacementRotationDegrees = state.backgroundPlacementRotationDegrees,
            backgroundFreeScaleUp = state.backgroundFreeScaleUp,
            selectedFilterId = state.selectedFilterId,
            enabledLensFilterIds = state.enabledLensFilterIds,
            beauty = state.beauty,
            kind = kind
        )

        val newHistory = enforceLimits(state.history.take(state.historyIndex + 1) + doc)
        return state.copy(
            history = newHistory,
            historyIndex = newHistory.size - 1
        )
    }

    /**
     * Bounds the history list per kind while preserving event order
     * (docs/effect_stack.md → Undo / redo → "Two caps on the one list"): starting
     * from the newest checkpoint, keep at most [FINE_GRAINED_LIMIT] `fine_grained`
     * entries and at most [COARSE_GRAINED_LIMIT] `coarse_grained` entries, evicting
     * only the OLDEST entries of each kind. The newest checkpoint (the one just
     * recorded — always the current index after a push) is never evicted, and the
     * surviving entries keep their original relative order, so an old
     * coarse_grained commit/remove/move is never dropped by a long run of
     * fine_grained tweaks (it is already beyond the `fine_grained` cap's scope).
     */
    fun enforceLimits(history: List<StackDocument>): List<StackDocument> {
        if (history.size <= 1) return history
        var fineGrainedCount = 0
        var coarseGrainedCount = 0
        val kept = ArrayDeque<StackDocument>()
        for (i in history.indices.reversed()) {
            val doc = history[i]
            if (doc.kind == HistoryKind.COARSE_GRAINED) {
                if (coarseGrainedCount >= COARSE_GRAINED_LIMIT) continue
                coarseGrainedCount++
            } else {
                if (fineGrainedCount >= FINE_GRAINED_LIMIT) continue
                fineGrainedCount++
            }
            kept.addFirst(doc)
        }
        return kept.toList()
    }

    /**
     * Returns [state] with every document field replaced by [doc]'s snapshot and
     * the history cursor moved to [newIndex] — the shared undo/redo restore. Also
     * copies the beauty sub-tool parameters back onto the state, so undo/redo
     * restores both the effect stack and its deriving params.
     */
    fun restore(state: EditorUiState, doc: StackDocument, newIndex: Int): EditorUiState = state.copy(
        effectStack = doc.effectStack,
        brightness = doc.brightness,
        contrast = doc.contrast,
        saturation = doc.saturation,
        sharpness = doc.sharpness,
        exposure = doc.exposure,
        shadows = doc.shadows,
        highlights = doc.highlights,
        temperature = doc.temperature,
        tint = doc.tint,
        vignette = doc.vignette,
        skinDenoise = doc.skinDenoise,
        rotation = doc.rotation,
        flipHorizontal = doc.flipHorizontal,
        flipVertical = doc.flipVertical,
        cropRect = doc.cropRect,
        innerFrameStyle = doc.innerFrameStyle,
        innerFrameThickness = doc.innerFrameThickness,
        innerFrameColor = doc.innerFrameColor,
        innerFrameEdgeSize = doc.innerFrameEdgeSize,
        innerFrameCustomAngle = doc.innerFrameCustomAngle,
        innerFrameCustomCurveDistance = doc.innerFrameCustomCurveDistance,
        innerFrameCustomStrength = doc.innerFrameCustomStrength,
        innerFrameCustomCount = doc.innerFrameCustomCount,
        innerFrameLines = doc.innerFrameLines,
        innerFrameLinesEnabled = doc.innerFrameLinesEnabled,
        innerFrameLineDistance = doc.innerFrameLineDistance,
        innerFrameLineThickness = doc.innerFrameLineThickness,
        outerFrameStyle = doc.outerFrameStyle,
        outerFrameThickness = doc.outerFrameThickness,
        outerFrameColor = doc.outerFrameColor,
        outerFrameEdgeSize = doc.outerFrameEdgeSize,
        outerFrameCustomAngle = doc.outerFrameCustomAngle,
        outerFrameCustomCurveDistance = doc.outerFrameCustomCurveDistance,
        outerFrameCustomStrength = doc.outerFrameCustomStrength,
        outerFrameCustomCount = doc.outerFrameCustomCount,
        outerFrameLines = doc.outerFrameLines,
        outerFrameLinesEnabled = doc.outerFrameLinesEnabled,
        outerFrameLineDistance = doc.outerFrameLineDistance,
        outerFrameLineThickness = doc.outerFrameLineThickness,
        enhanceEnabled = doc.enhanceEnabled,
        styleFilter = doc.styleFilter,
        backgroundMode = doc.backgroundMode,
        backgroundBlurRadius = doc.backgroundBlurRadius,
        backgroundShape = doc.backgroundShape,
        subjectMask = doc.subjectMask,
        backgroundImage = doc.backgroundImage,
        selectedBackgroundRes = doc.selectedBackgroundRes,
        backgroundPlacementScale = doc.backgroundPlacementScale,
        backgroundPlacementPanX = doc.backgroundPlacementPanX,
        backgroundPlacementPanY = doc.backgroundPlacementPanY,
        backgroundPlacementRotationDegrees = doc.backgroundPlacementRotationDegrees,
        backgroundFreeScaleUp = doc.backgroundFreeScaleUp,
        selectedFilterId = doc.selectedFilterId,
        enabledLensFilterIds = doc.enabledLensFilterIds,
        historyIndex = newIndex
    ).copy(beauty = doc.beauty)
}