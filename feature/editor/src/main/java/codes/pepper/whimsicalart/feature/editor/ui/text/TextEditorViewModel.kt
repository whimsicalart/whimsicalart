package codes.pepper.whimsicalart.feature.editor.ui.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import codes.pepper.whimsicalart.core.domain.wrapDegrees
import codes.pepper.whimsicalart.feature.editor.domain.TextAlignment
import codes.pepper.whimsicalart.feature.editor.domain.TextBackgroundShape
import codes.pepper.whimsicalart.feature.editor.domain.TextFont
import codes.pepper.whimsicalart.feature.editor.domain.TextStyleFlag
import java.util.UUID
import javax.inject.Inject

data class TextEditorUiState(
    val textOverlays: List<TextOverlay> = emptyList(),
    val selectedOverlayId: String? = null,
    val isEditing: Boolean = false,
    val draftOverlayId: String? = null,
    val canvasCenter: Offset? = null,
    val currentText: String = "",
    val currentFontSize: Float = 24f,
    val currentColor: Color = Color.White,
    val textOpacity: Float = 1f,
    val currentFont: TextFont = TextFont.DEFAULT,
    val currentStyle: Set<TextStyleFlag> = emptySet(),
    val currentAlignment: TextAlignment = TextAlignment.CENTER,
    val currentRotation: Float = 0f,
    val hasShadow: Boolean = false,
    val shadowAngle: Float = 45f,
    val shadowDistance: Float = 6f,
    val shadowColor: Color = Color.Black,
    val hasOutline: Boolean = false,
    val outlineSize: Float = 2f,
    val outlineColor: Color = Color.Black,
    val outlineBlur: Float = 0f,
    val currentBackgroundShape: TextBackgroundShape = TextBackgroundShape.NONE,
    val currentBackgroundColor: Color = Color.Black,
    val backgroundOpacity: Float = 1f,
    val currentBackgroundBlur: Float = 0f
) {
    /** The commit-ready colour: swatch colour with the opacity slider applied. */
    val effectiveTextColor: Color
        get() = currentColor.copy(alpha = textOpacity)

    val effectiveBackgroundColor: Color
        get() = currentBackgroundColor.copy(alpha = backgroundOpacity)
}

/**
 * Live editing state for the Text tool (dp-space [TextOverlay]s). The committed
 * normalized parameters live on the stack's `TextEffect` (a `List<TextItem>`);
 * the editor imports them back here on re-entry ([restoreOverlays]) and this
 * ViewModel never owns a committed layer.
 *
 * Auto-placement: text is added automatically as it is typed — the first item
 * lands at the canvas CENTER and each subsequent one just below the previous
 * (the placement cursor walks down). Tapping an existing item selects it and
 * loads its content + parameters into the field and controls for in-place
 * editing; the **Add** button commits the field as a **new** item.
 */
@HiltViewModel
class TextEditorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(TextEditorUiState())
    val uiState: StateFlow<TextEditorUiState> = _uiState.asStateFlow()

    /** Vertical step (dp) between consecutive auto-placed items. */
    private companion object {
        const val PLACEMENT_STEP_DP = 56f
    }

    /** Starts a new draft (clears the field, deselects any in-place item). */
    fun startAddingText() {
        _uiState.value = _uiState.value.copy(
            isEditing = true,
            currentText = "",
            draftOverlayId = null,
            selectedOverlayId = null
        )
    }

    /** Remembers the edit-box centre (dp) so the first auto-placed item lands centred. */
    fun updateCanvasCenter(center: Offset) {
        if (_uiState.value.canvasCenter != center) {
            _uiState.value = _uiState.value.copy(canvasCenter = center)
        }
    }

    fun updateCurrentText(text: String) {
        val state = _uiState.value
        _uiState.value = state.copy(currentText = text)
        val selectedId = state.selectedOverlayId
        if (selectedId != null) {
            updateItem(selectedId) { it.copy(text = text) }
        } else if (text.isNotBlank()) {
            upsertDraft { it.copy(
                text = text,
                // A fresh draft inherits the current style parameters.
                fontSize = state.currentFontSize,
                color = state.effectiveTextColor,
                fontFamily = state.currentFont,
                style = state.currentStyle,
                alignment = state.currentAlignment,
                hasShadow = state.hasShadow,
                shadowAngle = state.shadowAngle,
                shadowDistance = state.shadowDistance,
                shadowColor = state.shadowColor,
                hasOutline = state.hasOutline,
                outlineSize = state.outlineSize,
                outlineColor = state.outlineColor,
                outlineBlur = state.outlineBlur,
                backgroundShape = state.currentBackgroundShape,
                backgroundColor = state.effectiveBackgroundColor,
                backgroundBlur = state.currentBackgroundBlur
            ) }
        }
    }

    fun updateFontSize(size: Float) {
        updateCurrentAndItem(state.currentFontSize, size) { it.copy(fontSize = size) }.also {
            _uiState.value = _uiState.value.copy(currentFontSize = size)
        }
    }

    fun updateColor(color: Color) {
        _uiState.value = _uiState.value.copy(currentColor = color)
        applyEffectiveToSelection { it.copy(color = color.copy(alpha = _uiState.value.textOpacity)) }
    }

    fun updateTextOpacity(opacity: Float) {
        _uiState.value = _uiState.value.copy(textOpacity = opacity)
        applyEffectiveToSelection { it.copy(color = it.color.copy(alpha = opacity)) }
    }

    fun updateFont(font: TextFont) {
        updateCurrentAndItem(state.currentFont, font) { it.copy(fontFamily = font) }.also {
            _uiState.value = _uiState.value.copy(currentFont = font)
        }
    }

    fun toggleStyle(flag: TextStyleFlag) {
        val state = _uiState.value
        val next = if (flag in state.currentStyle) state.currentStyle - flag else state.currentStyle + flag
        updateCurrentAndItem(state.currentStyle, next) { it.copy(style = next) }.also {
            _uiState.value = _uiState.value.copy(currentStyle = next)
        }
    }

    fun updateAlignment(alignment: TextAlignment) {
        updateCurrentAndItem(state.currentAlignment, alignment) { it.copy(alignment = alignment) }.also {
            _uiState.value = _uiState.value.copy(currentAlignment = alignment)
        }
    }

    fun toggleShadow() {
        val state = _uiState.value
        updateCurrentAndItem(state.hasShadow, !state.hasShadow) { it.copy(hasShadow = !it.hasShadow) }.also {
            _uiState.value = _uiState.value.copy(hasShadow = !state.hasShadow)
        }
    }

    fun updateShadowAngle(angle: Float) {
        updateCurrentAndItem(state.shadowAngle, angle) { it.copy(shadowAngle = angle) }.also {
            _uiState.value = _uiState.value.copy(shadowAngle = angle)
        }
    }

    fun updateShadowDistance(distance: Float) {
        updateCurrentAndItem(state.shadowDistance, distance) { it.copy(shadowDistance = distance) }.also {
            _uiState.value = _uiState.value.copy(shadowDistance = distance)
        }
    }

    fun updateShadowColor(color: Color) {
        updateCurrentAndItem(state.shadowColor, color) { it.copy(shadowColor = color) }.also {
            _uiState.value = _uiState.value.copy(shadowColor = color)
        }
    }

    fun toggleOutline() {
        val state = _uiState.value
        updateCurrentAndItem(state.hasOutline, !state.hasOutline) { it.copy(hasOutline = !it.hasOutline) }.also {
            _uiState.value = _uiState.value.copy(hasOutline = !state.hasOutline)
        }
    }

    fun updateOutlineSize(size: Float) {
        updateCurrentAndItem(state.outlineSize, size) { it.copy(outlineSize = size) }.also {
            _uiState.value = _uiState.value.copy(outlineSize = size)
        }
    }

    fun updateOutlineColor(color: Color) {
        updateCurrentAndItem(state.outlineColor, color) { it.copy(outlineColor = color) }.also {
            _uiState.value = _uiState.value.copy(outlineColor = color)
        }
    }

    fun updateOutlineBlur(blur: Float) {
        updateCurrentAndItem(state.outlineBlur, blur) { it.copy(outlineBlur = blur) }.also {
            _uiState.value = _uiState.value.copy(outlineBlur = blur)
        }
    }

    fun updateBackgroundShape(shape: TextBackgroundShape) {
        updateCurrentAndItem(state.currentBackgroundShape, shape) { it.copy(backgroundShape = shape) }.also {
            _uiState.value = _uiState.value.copy(currentBackgroundShape = shape)
        }
    }

    fun updateBackgroundColor(color: Color) {
        _uiState.value = _uiState.value.copy(currentBackgroundColor = color)
        applyEffectiveToSelection { it.copy(backgroundColor = color.copy(alpha = _uiState.value.backgroundOpacity)) }
    }

    fun updateBackgroundOpacity(opacity: Float) {
        _uiState.value = _uiState.value.copy(backgroundOpacity = opacity)
        applyEffectiveToSelection { it.copy(backgroundColor = it.backgroundColor.copy(alpha = opacity)) }
    }

    fun updateBackgroundBlur(blur: Float) {
        updateCurrentAndItem(state.currentBackgroundBlur, blur) { it.copy(backgroundBlur = blur) }.also {
            _uiState.value = _uiState.value.copy(currentBackgroundBlur = blur)
        }
    }

    /**
     * Commits the current field as a NEW item at the auto-placement position
     * ("Add" button) and starts a fresh draft below it.
     */
    fun addText() {
        val state = _uiState.value
        if (state.currentText.isBlank()) return
        val placement = placementFor(state, Offset.Zero)
        val overlay = TextOverlay(
            id = UUID.randomUUID().toString(),
            text = state.currentText,
            position = placement,
            fontSize = state.currentFontSize,
            color = state.effectiveTextColor,
            fontFamily = state.currentFont,
            style = state.currentStyle,
            alignment = state.currentAlignment,
            hasShadow = state.hasShadow,
            shadowAngle = state.shadowAngle,
            shadowDistance = state.shadowDistance,
            shadowColor = state.shadowColor,
            hasOutline = state.hasOutline,
            outlineSize = state.outlineSize,
            outlineColor = state.outlineColor,
            outlineBlur = state.outlineBlur,
            backgroundShape = state.currentBackgroundShape,
            backgroundColor = state.effectiveBackgroundColor,
            backgroundBlur = state.currentBackgroundBlur
        )
        _uiState.value = state.copy(
            textOverlays = state.textOverlays + overlay,
            draftOverlayId = null,
            currentText = "",
            selectedOverlayId = null
        )
    }

    fun moveOverlay(overlayId: String, newPosition: Offset) {
        updateItem(overlayId) { it.copy(position = newPosition) }
    }

    fun scaleOverlay(overlayId: String, scale: Float) {
        updateItem(overlayId) { it.copy(scale = scale.coerceIn(0.3f, 5f)) }
    }

    /**
     * Corner/edge resize: a single atomic update sets the new uniform scale AND
     * re-anchors the centre so the opposite corner stays fixed under the finger
     * (one call, no intermediate flash between scale and move).
     */
    fun resizeOverlay(overlayId: String, scale: Float, position: Offset) {
        updateItem(overlayId) { it.copy(scale = scale.coerceIn(0.3f, 5f), position = position) }
    }

    /**
     * Applies an on-canvas pinch-twist rotation (gesture path). Updates the
     * item's rotation AND mirrors the wrapped value into the current rotation
     * control so the panel slider tracks the live gesture — 1:1 with the sticker
     * slider, which binds the placement's rotation directly (spec: the rotation
     * control "reflects the live pinch-twist value, wrapped [0°,360°), and
     * driving the slider pokes the same wrapped value back into the item").
     */
    fun rotateOverlay(overlayId: String, rotation: Float) {
        updateItem(overlayId) { it.copy(rotation = wrapDegrees(rotation)) }
        _uiState.value = _uiState.value.copy(currentRotation = wrapDegrees(rotation))
    }

    /** Updates the in-place edited item's rotation (panel slider), mirrored in
     *  the current controls. */
    fun updateCurrentRotation(rotation: Float) {
        updateCurrentAndItem(state.currentRotation, rotation) { it.copy(rotation = wrapDegrees(rotation)) }.also {
            _uiState.value = _uiState.value.copy(currentRotation = wrapDegrees(rotation))
        }
    }

    /**
     * Removes the selected overlay (if any) and RESETS the panel to its
     * first-open state (spec: *Text editor* → *Remove resets the panel; there
     * is no Cancel link*): every control snaps back to its [TextEditorUiState]
     * default (font size 24, colour white, opacity 1, font DEFAULT, style
     * empty, alignment CENTER, rotation 0, shadow / outline off, background
     * shape NONE and the background defaults), the input is cleared and any
     * draft is dropped — the committed [textOverlays] and the edit box's
     * [canvasCenter] are the only state kept, so the tool is ready just as when
     * it was opened.
     */
    fun removeSelectionAndReset() {
        val state = _uiState.value
        val targetId = state.selectedOverlayId
        _uiState.value = TextEditorUiState(
            textOverlays = if (targetId == null) state.textOverlays
            else state.textOverlays.filterNot { it.id == targetId },
            canvasCenter = state.canvasCenter
        )
    }

    /**
     * Selects [overlayId] and loads its content + parameters into the field and
     * controls for in-place editing. Finalizes any live draft first.
     */
    fun selectOverlay(overlayId: String) {
        val state = _uiState.value
        val overlay = state.textOverlays.firstOrNull { it.id == overlayId } ?: return
        if (state.selectedOverlayId == overlayId) return
        _uiState.value = state.copy(
            selectedOverlayId = overlayId,
            draftOverlayId = null,
            isEditing = true,
            currentText = overlay.text,
            currentFontSize = overlay.fontSize,
            textOpacity = overlay.color.alpha,
            currentColor = overlay.color.copy(alpha = 1f),
            currentFont = overlay.fontFamily,
            currentStyle = overlay.style,
            currentAlignment = overlay.alignment,
            currentRotation = wrapDegrees(overlay.rotation),
            hasShadow = overlay.hasShadow,
            shadowAngle = overlay.shadowAngle,
            shadowDistance = overlay.shadowDistance,
            shadowColor = overlay.shadowColor,
            hasOutline = overlay.hasOutline,
            outlineSize = overlay.outlineSize,
            outlineColor = overlay.outlineColor,
            outlineBlur = overlay.outlineBlur,
            currentBackgroundShape = overlay.backgroundShape,
            backgroundOpacity = overlay.backgroundColor.alpha,
            currentBackgroundColor = overlay.backgroundColor.copy(alpha = 1f),
            currentBackgroundBlur = overlay.backgroundBlur
        )
    }

    fun clearAllText() {
        _uiState.value = TextEditorUiState()
    }

    /** Replaces the editing list with imported (committed) [overlays]. */
    fun restoreOverlays(overlays: List<TextOverlay>) {
        if (overlays.isEmpty()) return
        _uiState.value = _uiState.value.copy(textOverlays = overlays)
        // Entering the tool always starts with the LAST inserted text selected
        // and its parameters loaded, so the panel is immediately in edit mode
        // (no first-state screen); the last commit matches what the user just
        // added or edited on the photo.
        selectOverlay(overlays.last().id)
    }

    /** Appends overlays produced from on-device OCR (each is user-editable). */
    fun addRecognizedOverlays(overlays: List<TextOverlay>) {
        if (overlays.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            textOverlays = _uiState.value.textOverlays + overlays
        )
        // Stay in the editing panel (no first-state fallback) and load the last
        // recognized item so the user immediately sees the OCR result in the field.
        selectOverlay(overlays.last().id)
    }

    // --- internals ---

    private val state: TextEditorUiState
        get() = _uiState.value

    private fun updateItem(overlayId: String, transform: (TextOverlay) -> TextOverlay) {
        val s = _uiState.value
        var changed = false
        val updated = s.textOverlays.map { it ->
            if (it.id == overlayId) {
                changed = true
                transform(it)
            } else {
                it
            }
        }
        if (changed) _uiState.value = s.copy(textOverlays = updated)
    }

    /**
     * Applies [transform] to the item being edited — the in-place selected item,
     * or the live draft while typing — so control edits show up immediately.
     */
    private fun updateCurrentAndItem(
        current: Any,
        next: Any,
        transform: (TextOverlay) -> TextOverlay
    ) {
        if (current == next) return
        val s = _uiState.value
        val targetId = s.selectedOverlayId ?: s.draftOverlayId ?: return
        updateItem(targetId, transform)
    }

    private fun applyEffectiveToSelection(transform: (TextOverlay) -> TextOverlay) {
        val s = _uiState.value
        val targetId = s.selectedOverlayId ?: s.draftOverlayId ?: return
        updateItem(targetId, transform)
    }

    /** Creates or updates the live draft overlay (typed while in add mode). */
    private fun upsertDraft(transform: (TextOverlay) -> TextOverlay) {
        val s = _uiState.value
        val draftId = s.draftOverlayId
        if (draftId != null) {
            updateItem(draftId, transform)
        } else {
            val id = UUID.randomUUID().toString()
            val draft = transform(TextOverlay(id = id, text = s.currentText, position = Offset.Zero))
                .copy(position = placementFor(s, Offset.Zero))
            _uiState.value = s.copy(textOverlays = s.textOverlays + draft, draftOverlayId = id)
        }
    }

    /**
     * Auto-placement: first item at the canvas CENTER, each subsequent one
     * slightly below the previous added item (the placement cursor walks down).
     * [fallbackCenter] is used when the remembered canvas centre is not known
     * yet (before the first layout pass).
     */
    private fun placementFor(state: TextEditorUiState, fallbackCenter: Offset): Offset {
        val base = state.canvasCenter ?: fallbackCenter
        val last = state.textOverlays.lastOrNull() ?: return base
        // Ignore the live draft when finding the previous item — it would
        // otherwise stack on itself.
        val previous = if (last.id == state.draftOverlayId) {
            state.textOverlays.dropLast(1).lastOrNull()
        } else {
            last
        }
        val anchor = previous?.position ?: base
        return anchor.copy(y = anchor.y + PLACEMENT_STEP_DP)
    }
}