package com.whimsicalart.feature.editor.ui.pen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PenBrushType {
    SOLID,
    GLOW,
    NEON,
    RAINBOW
}

data class PenStroke(
    val id: String,
    val points: List<Offset>,
    val brushType: PenBrushType,
    val color: Color,
    val size: Float = 12f
)

data class PenLayer(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val strokes: List<PenStroke> = emptyList()
)

data class PenEditorState(
    val layers: List<PenLayer> = listOf(PenLayer(id = "layer0", name = "Layer 1")),
    val activeLayerIndex: Int = 0,
    val currentStroke: PenStroke? = null,
    val selectedBrushType: PenBrushType = PenBrushType.SOLID,
    val selectedColor: Color = Color.Red,
    val brushSize: Float = 12f,
    val undoStack: List<PenStroke> = emptyList()
) {
    val activeLayer: PenLayer? get() = layers.getOrNull(activeLayerIndex)
    val strokes: List<PenStroke>
        get() = layers.filter { it.visible }.flatMap { it.strokes }
}

@HiltViewModel
class PenViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PenEditorState())
    val uiState = _uiState.asStateFlow()

    fun selectBrushType(type: PenBrushType) {
        _uiState.update { it.copy(selectedBrushType = type) }
    }

    fun selectColor(color: Color) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun updateBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size) }
    }

    fun onDragStart(position: Offset) {
        if (_uiState.value.activeLayer == null) return
        _uiState.update { state ->
            state.copy(
                currentStroke = PenStroke(
                    id = UUID.randomUUID().toString(),
                    points = listOf(position),
                    brushType = state.selectedBrushType,
                    color = state.selectedColor,
                    size = state.brushSize
                )
            )
        }
    }

    fun onDragMove(position: Offset) {
        _uiState.update { state ->
            val current = state.currentStroke ?: return@update state
            val last = current.points.lastOrNull()
            if (last != null && (position - last).getDistance() < 2f) {
                state
            } else {
                state.copy(
                    currentStroke = current.copy(points = current.points + position)
                )
            }
        }
    }

    fun onDragEnd() {
        _uiState.update { state ->
            val current = state.currentStroke
            val activeIndex = state.activeLayerIndex
            val layer = state.layers.getOrNull(activeIndex)
            if (current != null && current.points.isNotEmpty() && layer != null) {
                val layers = state.layers.toMutableList()
                layers[activeIndex] = layer.copy(strokes = layer.strokes + current)
                state.copy(
                    layers = layers,
                    currentStroke = null,
                    undoStack = emptyList()
                )
            } else {
                state.copy(currentStroke = null)
            }
        }
    }

    fun undo() {
        _uiState.update { state ->
            val activeIndex = state.activeLayerIndex
            val layer = state.layers.getOrNull(activeIndex) ?: return@update state
            if (layer.strokes.isEmpty()) return@update state
            val removed = layer.strokes.last()
            val layers = state.layers.toMutableList()
            layers[activeIndex] = layer.copy(strokes = layer.strokes.dropLast(1))
            state.copy(
                layers = layers,
                undoStack = listOf(removed) + state.undoStack
            )
        }
    }

    fun redo() {
        _uiState.update { state ->
            val activeIndex = state.activeLayerIndex
            val layer = state.layers.getOrNull(activeIndex) ?: return@update state
            if (state.undoStack.isEmpty()) return@update state
            val restored = state.undoStack.first()
            val layers = state.layers.toMutableList()
            layers[activeIndex] = layer.copy(strokes = layer.strokes + restored)
            state.copy(
                layers = layers,
                undoStack = state.undoStack.drop(1)
            )
        }
    }

    fun clear() {
        _uiState.update { state ->
            val activeIndex = state.activeLayerIndex
            val layer = state.layers.getOrNull(activeIndex) ?: return@update state
            val layers = state.layers.toMutableList()
            layers[activeIndex] = layer.copy(strokes = emptyList())
            state.copy(layers = layers, undoStack = emptyList())
        }
    }

    fun addLayer() {
        _uiState.update { state ->
            val newLayer = PenLayer(
                id = "layer${state.layers.size}",
                name = "Layer ${state.layers.size + 1}"
            )
            state.copy(
                layers = state.layers + newLayer,
                activeLayerIndex = state.layers.size
            )
        }
    }

    fun removeLayer() {
        _uiState.update { state ->
            if (state.layers.size <= 1) return@update state
            val layers = state.layers.toMutableList().also { it.removeAt(state.activeLayerIndex) }
            val newIndex = state.activeLayerIndex.coerceAtMost(layers.size - 1)
            state.copy(layers = layers, activeLayerIndex = newIndex)
        }
    }

    fun selectLayer(index: Int) {
        _uiState.update { state ->
            if (index !in state.layers.indices) state
            else state.copy(activeLayerIndex = index)
        }
    }

    fun toggleLayerVisibility(index: Int) {
        _uiState.update { state ->
            if (index !in state.layers.indices) return@update state
            val layers = state.layers.toMutableList()
            val layer = layers[index]
            layers[index] = layer.copy(visible = !layer.visible)
            state.copy(layers = layers)
        }
    }

    fun moveLayerToTop(index: Int) {
        _uiState.update { state ->
            if (index !in state.layers.indices || state.layers.size <= 1) return@update state
            val layers = state.layers.toMutableList()
            val layer = layers.removeAt(index)
            layers.add(layer)
            state.copy(
                layers = layers,
                activeLayerIndex = layers.size - 1
            )
        }
    }
}
