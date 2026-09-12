package codes.pepper.whimsicalart.feature.editor.ui.mosaic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import codes.pepper.whimsicalart.feature.editor.domain.mosaic.MediaPipeFaceRectsDetector
import codes.pepper.whimsicalart.feature.editor.domain.mosaic.PrivacyMaskBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MosaicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val faceDetector: MediaPipeFaceRectsDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(MosaicEditorState())
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
        _uiState.update { it.copy(isErasing = !it.isErasing, liveMosaicApplied = false) }
    }

    fun clear() {
        debounceJob?.cancel()
        _uiState.update {
            it.copy(
                strokes = emptyList(),
                currentStroke = null,
                liveMosaicApplied = false,
                strokesVersion = it.strokesVersion + (if (it.strokes.isNotEmpty()) 1 else 0)
            )
        }
    }

    fun clearSuggestions() {
        _uiState.update { it.copy(suggestedRegions = emptyList()) }
    }

    /**
     * Runs face detection on the current image and stores suggested privacy regions
     * (normalized) in state. Safe no-op when no image / no faces / detector fails.
     * The detection bitmap is decoded DOWN-SAMPLED (long side ≤ [DETECTION_MAX_LONG_SIDE_PX])
     * so the detector's owned ARGB copy + native working set stay in the low-MB range —
     * the full-res photo previously produced a ~241 MB allocation and OOM-crashed
     * (tracker: "Suggest faces (privacy)" CRASHES). Regions are normalized, so the
     * sampled decode does not shift them.
     */
    fun suggestPrivacyRegions(imageUri: Uri?) {
        if (imageUri == null || _uiState.value.isSuggesting) return
        _uiState.update { it.copy(isSuggesting = true) }
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.Default) {
                try {
                    val bitmap = decodeForDetection(imageUri)
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
                } catch (e: OutOfMemoryError) {
                    // Downsampling is the real fix; an OOM should still degrade to
                    // "no suggestions" rather than crash the editor.
                    emptyList()
                }
            }
            _uiState.update {
                it.copy(suggestedRegions = snapshot, isSuggesting = false)
            }
        }
    }

    /** Bounds-reading, down-sampled decode for face detection (see [suggestPrivacyRegions]). */
    private fun decodeForDetection(uri: Uri): Bitmap? = try {
        val bounds = readBounds(uri) ?: return null
        val sampleSize = detectionSampleSize(maxOf(bounds.outWidth, bounds.outHeight))
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            })
        }
    } catch (e: Exception) {
        null
    }

    private fun readBounds(uri: Uri): BitmapFactory.Options? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeStream(stream, null, this)
            }.takeIf { it.outWidth > 0 && it.outHeight > 0 }
        }
    } catch (e: Exception) {
        null
    }

    override fun onCleared() {
        debounceJob?.cancel()
        faceDetector.close()
    }

    fun onDragStart(position: Offset) {
        _uiState.update { state ->
            if (state.isErasing) {
                val erased = filterErased(state.strokes, listOf(position), state.brushSize)
                state.copy(
                    strokes = erased,
                    liveMosaicApplied = false,
                    strokesVersion = state.strokesVersion + (if (erased != state.strokes) 1 else 0)
                )
            } else {
                state.copy(
                    currentStroke = MosaicStroke(
                        id = UUID.randomUUID().toString(),
                        points = listOf(position),
                        brushSize = state.brushSize,
                        opacity = state.opacity
                    ),
                    liveMosaicApplied = false
                )
            }
        }
        restartDebounce()
    }

    fun onDragMove(position: Offset) {
        _uiState.update { state ->
            if (state.isErasing) {
                val erased = filterErased(state.strokes, listOf(position), state.brushSize)
                val changed = erased != state.strokes
                state.copy(
                    strokes = erased,
                    liveMosaicApplied = false,
                    strokesVersion = state.strokesVersion + (if (changed) 1 else 0)
                )
            } else {
                val current = state.currentStroke ?: return@update state.copy(liveMosaicApplied = false)
                val last = current.points.lastOrNull()
                if (last != null && (position - last).getDistance() < 3f) {
                    state.copy(liveMosaicApplied = false)
                } else {
                    state.copy(
                        currentStroke = current.copy(points = current.points + position),
                        liveMosaicApplied = false
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
                    liveMosaicApplied = false,
                    strokesVersion = state.strokesVersion + 1
                )
            } else {
                state.copy(currentStroke = null, liveMosaicApplied = false)
            }
        }
        restartDebounce()
    }

    /**
     * (Re)arms the half-second stillness/release debounce (docs/effects_reference.md
     * → Live-applied brush effects): any resumed stroking cancels the pending
     * countdown and the real mosaic only fires after a full uninterrupted 500 ms.
     * Every new stroke motion calls this, so a mid-stroke pause still triggers it
     * while the very next motion restarts it from zero. When it fires while the
     * finger is still down, the in-progress stroke is frozen into
     * [MosaicEditorState.strokes], so the live fold can show the pixelation for it.
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
                next.copy(liveMosaicApplied = true)
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

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}

/** Detection decode cap: NV21 of a 1280×960 sample ≈ 1.8 MB, far under the device heap. */
internal const val DETECTION_MAX_LONG_SIDE_PX = 1280

/**
 * Largest power-of-two [BitmapFactory.Options.inSampleSize] that keeps the image's
 * LONG side at or under [maxLongSidePx] — pure so the OOM guard is unit-testable.
 */
internal fun detectionSampleSize(longSidePx: Int, maxLongSidePx: Int = DETECTION_MAX_LONG_SIDE_PX): Int {
    var sample = 1
    while (longSidePx / sample > maxLongSidePx) {
        sample *= 2
    }
    return sample
}