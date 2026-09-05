package codes.pepper.whimsicalart.feature.editor.ui.blur

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BlurStroke(
    val id: String,
    val points: List<Offset>,
    val brushSize: Float = 30f,
    val opacity: Float = 0.6f
)

data class BlurEditorState(
    val strokes: List<BlurStroke> = emptyList(),
    val currentStroke: BlurStroke? = null,
    val brushSize: Float = 30f,
    val opacity: Float = 0.6f,
    val isErasing: Boolean = false
)

@HiltViewModel
class BlurViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BlurEditorState())
    val uiState = _uiState.asStateFlow()

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
                    strokes = eraseStrokes(state.strokes, position, state.brushSize)
                )
            } else {
                state.copy(
                    currentStroke = BlurStroke(
                        id = UUID.randomUUID().toString(),
                        points = listOf(position),
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
                    strokes = eraseStrokes(state.strokes, position, state.brushSize)
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

    private fun eraseStrokes(
        strokes: List<BlurStroke>,
        position: Offset,
        radius: Float
    ): List<BlurStroke> {
        return strokes.filterNot { stroke ->
            stroke.points.any { p -> (p - position).getDistance() < radius }
        }
    }
}