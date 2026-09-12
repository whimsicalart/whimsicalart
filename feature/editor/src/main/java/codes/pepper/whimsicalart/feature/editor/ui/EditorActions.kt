package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.ui.geometry.Rect
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool

/**
 * The shared screen-level callback surface for the decomposed editor chrome
 * ([EditorTopBar]) and canvas ([EditorCanvasArea]) — the pieces of the former
 * god-composable that are NOT tool protocol. Tool protocol stays with each
 * tool's own ViewModel (each panel is a single-writer leaf used as-is, per the
 * "do not duplicate/relayer" rule in the BP-4/BP-5 notes), so only the
 * cross-cutting navigation, history, eye/layers and crop-frame actions live
 * here.
 */
internal data class EditorActions(
    val onBack: () -> Unit,
    val onToggleLayers: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onRequestSave: () -> Unit,
    val onRetryLoadImage: () -> Unit,
    val onSelectTool: (EditTool?) -> Unit,
    val onEyeToggle: () -> Unit,
    val onSetCrop: (Rect) -> Unit,
    val onApplyCrop: () -> Unit
)