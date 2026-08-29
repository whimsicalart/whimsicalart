package com.whimsicalart.feature.editor.ui.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

data class TextEditorUiState(
    val textOverlays: List<TextOverlay> = emptyList(),
    val selectedOverlayId: String? = null,
    val isEditing: Boolean = false,
    val currentText: String = "",
    val currentFontSize: Float = 24f,
    val currentColor: Color = Color.White,
    val currentFont: TextFont = TextFont.DEFAULT,
    val hasShadow: Boolean = false,
    val hasStroke: Boolean = false,
    val currentBackgroundShape: TextBackgroundShape = TextBackgroundShape.NONE,
    val currentBackgroundColor: Color = Color.Black
)

@HiltViewModel
class TextEditorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(TextEditorUiState())
    val uiState: StateFlow<TextEditorUiState> = _uiState.asStateFlow()

    fun startAddingText() {
        _uiState.value = _uiState.value.copy(
            isEditing = true,
            currentText = "",
            selectedOverlayId = null
        )
    }

    fun updateCurrentText(text: String) {
        _uiState.value = _uiState.value.copy(currentText = text)
    }

    fun updateFontSize(size: Float) {
        _uiState.value = _uiState.value.copy(currentFontSize = size)
    }

    fun updateColor(color: Color) {
        _uiState.value = _uiState.value.copy(currentColor = color)
    }

    fun updateFont(font: TextFont) {
        _uiState.value = _uiState.value.copy(currentFont = font)
    }

    fun toggleShadow() {
        _uiState.value = _uiState.value.copy(hasShadow = !_uiState.value.hasShadow)
    }

    fun toggleStroke() {
        _uiState.value = _uiState.value.copy(hasStroke = !_uiState.value.hasStroke)
    }

    fun updateBackgroundShape(shape: TextBackgroundShape) {
        _uiState.value = _uiState.value.copy(currentBackgroundShape = shape)
    }

    fun updateBackgroundColor(color: Color) {
        _uiState.value = _uiState.value.copy(currentBackgroundColor = color)
    }

    fun addText(position: Offset) {
        val state = _uiState.value
        if (state.currentText.isBlank()) return

        val overlay = TextOverlay(
            id = UUID.randomUUID().toString(),
            text = state.currentText,
            position = position,
            fontSize = state.currentFontSize,
            color = state.currentColor,
            hasShadow = state.hasShadow,
            hasStroke = state.hasStroke,
            backgroundShape = state.currentBackgroundShape,
            backgroundColor = state.currentBackgroundColor
        )

        _uiState.value = state.copy(
            textOverlays = state.textOverlays + overlay,
            isEditing = false,
            currentText = ""
        )
    }

    fun moveOverlay(overlayId: String, newPosition: Offset) {
        _uiState.value = _uiState.value.copy(
            textOverlays = _uiState.value.textOverlays.map { overlay ->
                if (overlay.id == overlayId) {
                    overlay.copy(position = newPosition)
                } else {
                    overlay
                }
            }
        )
    }

    fun scaleOverlay(overlayId: String, scale: Float) {
        _uiState.value = _uiState.value.copy(
            textOverlays = _uiState.value.textOverlays.map { overlay ->
                if (overlay.id == overlayId) {
                    overlay.copy(
                        scaleX = scale,
                        scaleY = scale
                    )
                } else {
                    overlay
                }
            }
        )
    }

    fun rotateOverlay(overlayId: String, rotation: Float) {
        _uiState.value = _uiState.value.copy(
            textOverlays = _uiState.value.textOverlays.map { overlay ->
                if (overlay.id == overlayId) {
                    overlay.copy(rotation = rotation)
                } else {
                    overlay
                }
            }
        )
    }

    fun removeOverlay(overlayId: String) {
        _uiState.value = _uiState.value.copy(
            textOverlays = _uiState.value.textOverlays.filter { it.id != overlayId }
        )
    }

    fun selectOverlay(overlayId: String?) {
        _uiState.value = _uiState.value.copy(selectedOverlayId = overlayId)
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(
            isEditing = false,
            currentText = ""
        )
    }
}
