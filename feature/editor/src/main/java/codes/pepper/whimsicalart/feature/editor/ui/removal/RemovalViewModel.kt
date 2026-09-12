package codes.pepper.whimsicalart.feature.editor.ui.removal

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val isErasing: Boolean = false,
    /**
     * True while the on-screen preview shows the real content-aware removal for
     * the committed strokes (so the editor hides the white brush and folds the
     * strokes in). False while the user is mid-stroke or the 500 ms debounce is
     * still counting down.
     */
    val liveRemovalApplied: Boolean = false,
    /**
     * Monotonically increasing version of the committed [strokes] list, bumped
     * whenever a stroke is settled / erased / cleared (the editor keeps the
     * white brush drawn until a composite folding these strokes is published).
     */
    val strokesVersion: Int = 0
)

@HiltViewModel
class RemovalViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RemovalEditorState())
    val uiState = _uiState.asStateFlow()

    private var debounceJob: Job? = null

    fun updateBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size) }
    }

    fun toggleErasing() {
        debounceJob?.cancel()
        _uiState.update { it.copy(isErasing = !it.isErasing, liveRemovalApplied = false) }
    }

    fun clear() {
        debounceJob?.cancel()
        _uiState.update {
            it.copy(
                strokes = emptyList(),
                currentStroke = null,
                liveRemovalApplied = false,
                strokesVersion = it.strokesVersion + (if (it.strokes.isNotEmpty()) 1 else 0)
            )
        }
    }

    fun onDragStart(position: Offset) {
        _uiState.update { state ->
            if (state.isErasing) {
                val erased = eraseStrokes(state.strokes, position, state.brushSize)
                state.copy(
                    strokes = erased,
                    liveRemovalApplied = false,
                    strokesVersion = state.strokesVersion + (if (erased != state.strokes) 1 else 0)
                )
            } else {
                state.copy(
                    currentStroke = RemovalStroke(
                        id = UUID.randomUUID().toString(),
                        points = listOf(position),
                        brushSize = state.brushSize
                    ),
                    liveRemovalApplied = false
                )
            }
        }
        restartDebounce()
    }

    fun onDragMove(position: Offset) {
        _uiState.update { state ->
            if (state.isErasing) {
                val erased = eraseStrokes(state.strokes, position, state.brushSize)
                state.copy(
                    strokes = erased,
                    liveRemovalApplied = false,
                    strokesVersion = state.strokesVersion + (if (erased != state.strokes) 1 else 0)
                )
            } else {
                val current = state.currentStroke ?: return@update state.copy(liveRemovalApplied = false)
                val last = current.points.lastOrNull()
                if (last != null && (position - last).getDistance() < 3f) {
                    state.copy(liveRemovalApplied = false)
                } else {
                    state.copy(
                        currentStroke = current.copy(points = current.points + position),
                        liveRemovalApplied = false
                    )
                }
            }
        }
        restartDebounce()
    }

    fun onDragEnd() {
        _uiState.update { state ->
            val current = state.currentStroke
            if (current != null && current.points.isNotEmpty()) {
                state.copy(
                    strokes = state.strokes + current,
                    currentStroke = null,
                    liveRemovalApplied = false,
                    strokesVersion = state.strokesVersion + 1
                )
            } else {
                state.copy(currentStroke = null, liveRemovalApplied = false)
            }
        }
        restartDebounce()
    }

    override fun onCleared() {
        debounceJob?.cancel()
        super.onCleared()
    }

    /**
     * (Re)arms the half-second stillness/release debounce (docs/effects_reference.md
     * → Object removal previewing): any resumed stroking cancels the pending
     * countdown and the real removal only fires after a full uninterrupted
     * 500 ms. Every new stroke motion calls this, so a mid-stroke pause still
     * triggers it while the very next motion restarts it from zero. When it fires
     * while the finger is still down, the in-progress stroke is frozen into
     * [RemovalEditorState.strokes], so the live fold can show the fill for it too.
     */
    private fun restartDebounce() {
        debounceJob?.cancel()
        if (_uiState.value.strokes.isEmpty() && _uiState.value.currentStroke == null) return
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _uiState.update { state ->
                val toCommit = state.currentStroke?.takeIf { it.points.isNotEmpty() }
                val next = if (toCommit != null) {
                    state.copy(
                        strokes = state.strokes + toCommit,
                        currentStroke = null,
                        strokesVersion = state.strokesVersion + 1
                    )
                } else {
                    state
                }
                next.copy(liveRemovalApplied = true)
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

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}