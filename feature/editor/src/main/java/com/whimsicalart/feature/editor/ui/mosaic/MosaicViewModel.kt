package com.whimsicalart.feature.editor.ui.mosaic

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class MosaicViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MosaicEditorState())
    val uiState = _uiState.asStateFlow()

    fun selectBrushType(type: MosaicBrushType) {
        _uiState.update { it.copy(selectedBrushType = type) }
    }

    fun updateBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size) }
    }

    fun updateOpacity(opacity: Float) {
        _uiState.update { it.copy(opacity = opacity) }
    }

    fun toggleErasing() {
        _uiState.update { it.copy(isErasing = !it.isErasing) }
    }

    fun clear() {
        _uiState.update { it.copy(strokes = emptyList(), currentStroke = null) }
    }

    fun onDragStart(position: Offset) {
        _uiState.update { state ->
            if (state.isErasing) {
                state.copy(
                    strokes = filterErased(state.strokes, listOf(position), state.brushSize)
                )
            } else {
                state.copy(
                    currentStroke = MosaicStroke(
                        id = UUID.randomUUID().toString(),
                        points = listOf(position),
                        brushType = state.selectedBrushType,
                        brushSize = state.brushSize,
                        opacity = state.opacity
                    )
                )
            }
        }
    }

    fun onDragMove(position: Offset) {
        _uiState.update { state ->
            if (state.isErasing) {
                state.copy(
                    strokes = filterErased(state.strokes, listOf(position), state.brushSize)
                )
            } else {
                val current = state.currentStroke ?: return@update state
                val last = current.points.lastOrNull()
                if (last != null && (position - last).getDistance() < 3f) {
                    state
                } else {
                    state.copy(
                        currentStroke = current.copy(points = current.points + position)
                    )
                }
            }
        }
    }

    fun onDragEnd() {
        _uiState.update { state ->
            val current = state.currentStroke
            if (current != null && current.points.isNotEmpty()) {
                state.copy(
                    strokes = state.strokes + current,
                    currentStroke = null
                )
            } else {
                state.copy(currentStroke = null)
            }
        }
    }

    private fun filterErased(
        strokes: List<MosaicStroke>,
        points: List<Offset>,
        radius: Float
    ): List<MosaicStroke> {
        return strokes.filterNot { stroke ->
            stroke.points.any { p -> points.any { q -> (p - q).getDistance() < radius } }
        }
    }
}