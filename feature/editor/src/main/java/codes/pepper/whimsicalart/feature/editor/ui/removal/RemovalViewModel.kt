package codes.pepper.whimsicalart.feature.editor.ui.removal

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A painted stroke that marks the region to be inpainted (object removal). The
 * brush covers a solid disc/ribbon; at render time the union of all strokes is
 * turned into a mask and filled by [codes.pepper.whimsicalart.feature.editor.domain.removal.DiffusionInpainter].
 */
data class RemovalStroke(
    val id: String,
    val points: List<Offset>,
    val brushSize: Float = 30f
)

data class RemovalEditorState(
    val strokes: List<RemovalStroke> = emptyList(),
    val currentStroke: RemovalStroke? = null,
    val brushSize: Float = 30f,
    val isErasing: Boolean = false
)

@HiltViewModel
class RemovalViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RemovalEditorState())
    val uiState = _uiState.asStateFlow()

    fun updateBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size) }
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
                state.copy(strokes = eraseStrokes(state.strokes, position, state.brushSize))
            } else {
                state.copy(
                    currentStroke = RemovalStroke(
                        id = UUID.randomUUID().toString(),
                        points = listOf(position),
                        brushSize = state.brushSize
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
        strokes: List<RemovalStroke>,
        position: Offset,
        radius: Float
    ): List<RemovalStroke> {
        return strokes.filterNot { stroke ->
            stroke.points.any { p -> (p - position).getDistance() < radius }
        }
    }
}