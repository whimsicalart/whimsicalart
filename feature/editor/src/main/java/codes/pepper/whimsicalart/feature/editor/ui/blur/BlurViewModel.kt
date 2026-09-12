package codes.pepper.whimsicalart.feature.editor.ui.blur

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
    val isErasing: Boolean = false,
    /**
     * True while the on-screen preview shows the real gaussian-style blur for
     * the committed strokes (so the editor hides the fake grey-blue tint and
     * folds the strokes in). False while the user is mid-stroke or the 500 ms
     * debounce is still counting down (docs/effects_reference.md → Live-applied
     * brush effects).
     */
    val liveBlurApplied: Boolean = false,
    /**
     * Monotonically increasing version of the committed [strokes] list, bumped
     * whenever a stroke is settled / erased / cleared (the editor keeps the
     * fake tint drawn until a composite folding these strokes is published).
     */
    val strokesVersion: Int = 0
)

@HiltViewModel
class BlurViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BlurEditorState())
    val uiState = _uiState.asStateFlow()

    private var debounceJob: Job? = null

    fun updateBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size) }
    }

    fun updateOpacity(opacity: Float) {
        _uiState.update { it.copy(opacity = opacity) }
    }

    fun toggleErasing() {
        debounceJob?.cancel()
        _uiState.update { it.copy(isErasing = !it.isErasing, liveBlurApplied = false) }
    }

    fun clear() {
        debounceJob?.cancel()
        _uiState.update {
            it.copy(
                strokes = emptyList(),
                currentStroke = null,
                liveBlurApplied = false,
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
                    liveBlurApplied = false,
                    strokesVersion = state.strokesVersion + (if (erased != state.strokes) 1 else 0)
                )
            } else {
                state.copy(
                    currentStroke = BlurStroke(
                        id = UUID.randomUUID().toString(),
                        points = listOf(position),
                        brushSize = state.brushSize,
                        opacity = state.opacity
                    ),
                    liveBlurApplied = false
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
                    liveBlurApplied = false,
                    strokesVersion = state.strokesVersion + (if (erased != state.strokes) 1 else 0)
                )
            } else {
                val current = state.currentStroke ?: return@update state.copy(liveBlurApplied = false)
                val last = current.points.lastOrNull()
                if (last != null && (position - last).getDistance() < 3f) {
                    state.copy(liveBlurApplied = false)
                } else {
                    state.copy(
                        currentStroke = current.copy(points = current.points + position),
                        liveBlurApplied = false
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
                    liveBlurApplied = false,
                    strokesVersion = state.strokesVersion + 1
                )
            } else {
                state.copy(currentStroke = null, liveBlurApplied = false)
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
     * → Live-applied brush effects): any resumed stroking cancels the pending
     * countdown and the real blur only fires after a full uninterrupted 500 ms.
     * Every new stroke motion calls this, so a mid-stroke pause still triggers it
     * while the very next motion restarts it from zero. When it fires while the
     * finger is still down, the in-progress stroke is frozen into
     * [BlurEditorState.strokes], so the live fold can show the blur for it too.
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
                next.copy(liveBlurApplied = true)
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

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}