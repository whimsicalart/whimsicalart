package codes.pepper.whimsicalart.feature.editor.ui.pen

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
    val size: Float = 12f,
    /**
     * 0–1 colour opacity: makes the drawn art translucent, applied on top of
     * (never replacing) the chosen colour.
     */
    val opacity: Float = 1f,
    /**
     * 0–1 edge hardness relative to size: 1 = fully solid across the whole size
     * (hard edge), 0 = only the centre fully solid fading out to the size (soft
     * edge), intermediate values blend between the two.
     */
    val hardness: Float = 1f
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
    val brushOpacity: Float = 1f,
    val brushHardness: Float = 1f,
    val undoStack: List<PenStroke> = emptyList(),
    /**
     * Monotonic counter bumped whenever the committed stroke set changes; the
     * canvas keep-alive compares it against the folded composite's published
     * version so the pen trace never dead-gaps while the eye-ON first-time
     * full-stack fold is pending (same contract as MosaicTool.strokesVersion).
     */
    val strokesVersion: Int = 0
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

    fun updateBrushOpacity(opacity: Float) {
        _uiState.update { it.copy(brushOpacity = opacity.coerceIn(0f, 1f)) }
    }

    fun updateBrushHardness(hardness: Float) {
        _uiState.update { it.copy(brushHardness = hardness.coerceIn(0f, 1f)) }
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
                    size = state.brushSize,
                    opacity = state.brushOpacity,
                    hardness = state.brushHardness
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
                    undoStack = emptyList(),
                    strokesVersion = state.strokesVersion + 1
                )
            } else {
                state.copy(currentStroke = null)
            }
        }
    }

    /**
     * Re-presents the committed strokes into the ACTIVE layer's live state on
     * tool re-entry, translated through the CURRENT anchored frame
     * (docs/effect_stack.md → *Positional layers*): the committed fractions are
     * mapped ref→frame by the same uniform fit the renderer uses, so a
     * re-entered brush never rides stale screen positions after an earlier
     * effect changed the layer's base.
     */
    fun restoreStrokes(strokes: List<PenStroke>) {
        _uiState.update { state ->
            val activeIndex = state.activeLayerIndex
            val layer = state.layers.getOrNull(activeIndex) ?: return@update state
            val layers = state.layers.toMutableList()
            layers[activeIndex] = layer.copy(strokes = strokes)
            state.copy(
                layers = layers,
                currentStroke = null,
                strokesVersion = state.strokesVersion + 1
            )
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
                undoStack = listOf(removed) + state.undoStack,
                strokesVersion = state.strokesVersion + 1
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
                undoStack = state.undoStack.drop(1),
                strokesVersion = state.strokesVersion + 1
            )
        }
    }

    fun clear() {
        _uiState.update { state ->
            val activeIndex = state.activeLayerIndex
            val layer = state.layers.getOrNull(activeIndex) ?: return@update state
            val layers = state.layers.toMutableList()
            layers[activeIndex] = layer.copy(strokes = emptyList())
            state.copy(layers = layers, undoStack = emptyList(), strokesVersion = state.strokesVersion + 1)
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
            state.copy(layers = layers, activeLayerIndex = newIndex, strokesVersion = state.strokesVersion + 1)
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
            state.copy(layers = layers, strokesVersion = state.strokesVersion + 1)
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
                activeLayerIndex = layers.size - 1,
                strokesVersion = state.strokesVersion + 1
            )
        }
    }
}
