package codes.pepper.whimsicalart.feature.editor.ui.mosaic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import codes.pepper.whimsicalart.feature.editor.domain.mosaic.MlKitFaceRectsDetector
import codes.pepper.whimsicalart.feature.editor.domain.mosaic.PrivacyMaskBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MosaicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val faceDetector: MlKitFaceRectsDetector
) : ViewModel() {

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

    fun clearSuggestions() {
        _uiState.update { it.copy(suggestedRegions = emptyList()) }
    }

    /**
     * Runs face detection on the current image and stores suggested privacy regions
     * (normalized) in state. Safe no-op when no image / no faces / detector fails.
     */
    fun suggestPrivacyRegions(imageUri: Uri?) {
        if (imageUri == null || _uiState.value.isSuggesting) return
        _uiState.update { it.copy(isSuggesting = true) }
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.Default) {
                val uri = imageUri
                val bitmap = decode(uri)
                if (bitmap == null) {
                    emptyList()
                } else {
                    try {
                        val faces = faceDetector.detectFaces(bitmap)
                        PrivacyMaskBuilder.suggestedRegions(
                            faces, bitmap.width, bitmap.height
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            _uiState.update {
                it.copy(suggestedRegions = snapshot, isSuggesting = false)
            }
        }
    }

    private fun decode(uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
    } catch (e: Exception) {
        null
    }

    override fun onCleared() {
        faceDetector.close()
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