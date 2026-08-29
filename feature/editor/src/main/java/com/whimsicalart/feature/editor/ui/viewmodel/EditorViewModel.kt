package com.whimsicalart.feature.editor.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whimsicalart.feature.editor.domain.BitmapRenderer
import com.whimsicalart.feature.editor.domain.EditorRenderBundle
import com.whimsicalart.feature.editor.domain.ImageSaver
import com.whimsicalart.feature.editor.domain.SaveConfig
import com.whimsicalart.feature.editor.ui.EditorColorMatrix
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class EditorUiState(
    val imageUri: Uri? = null,
    val originalBitmap: Bitmap? = null,
    val editedBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val selectedTool: EditTool? = null,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val sharpness: Float = 0f,
    val exposure: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vignette: Float = 0f,
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropRect: Rect? = null,
    val selectedFrameId: String? = null,
    val isComparing: Boolean = false,
    val history: List<EditorHistory> = emptyList(),
    val historyIndex: Int = -1
)

data class EditorHistory(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val sharpness: Float,
    val exposure: Float,
    val shadows: Float,
    val highlights: Float,
    val temperature: Float,
    val tint: Float,
    val vignette: Float,
    val rotation: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean
)

enum class EditTool {
    CROP,
    ROTATE,
    FLIP,
    BRIGHTNESS,
    CONTRAST,
    SATURATION,
    SHARPEN,
    EXPOSURE,
    SHADOWS,
    HIGHLIGHTS,
    TEMPERATURE,
    TINT,
    VIGNETTE,
    STICKERS,
    TEXT,
    FRAMES,
    MOSAIC,
    BLUR_BRUSH,
    PEN
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageSaver: ImageSaver
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun setImageUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(imageUri = uri)
    }

    fun setBitmaps(original: Bitmap, edited: Bitmap) {
        _uiState.value = _uiState.value.copy(
            originalBitmap = original,
            editedBitmap = edited
        )
    }

    fun selectTool(tool: EditTool?) {
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun updateBrightness(value: Float) {
        _uiState.value = _uiState.value.copy(brightness = value)
    }

    fun updateContrast(value: Float) {
        _uiState.value = _uiState.value.copy(contrast = value)
    }

    fun updateSaturation(value: Float) {
        _uiState.value = _uiState.value.copy(saturation = value)
    }

    fun updateSharpness(value: Float) {
        _uiState.value = _uiState.value.copy(sharpness = value)
    }

    fun updateExposure(value: Float) {
        _uiState.value = _uiState.value.copy(exposure = value)
    }

    fun updateShadows(value: Float) {
        _uiState.value = _uiState.value.copy(shadows = value)
    }

    fun updateHighlights(value: Float) {
        _uiState.value = _uiState.value.copy(highlights = value)
    }

    fun updateTemperature(value: Float) {
        _uiState.value = _uiState.value.copy(temperature = value)
    }

    fun updateTint(value: Float) {
        _uiState.value = _uiState.value.copy(tint = value)
    }

    fun updateVignette(value: Float) {
        _uiState.value = _uiState.value.copy(vignette = value)
    }

    fun rotateLeft() {
        val state = _uiState.value
        _uiState.value = state.copy(rotation = normalizeRotation(state.rotation - 90f))
        saveToHistory()
    }

    fun rotateRight() {
        val state = _uiState.value
        _uiState.value = state.copy(rotation = normalizeRotation(state.rotation + 90f))
        saveToHistory()
    }

    private fun normalizeRotation(rotation: Float): Float {
        return ((rotation % 360f) + 360f) % 360f
    }

    fun flipHorizontal() {
        val state = _uiState.value
        _uiState.value = state.copy(flipHorizontal = !state.flipHorizontal)
        saveToHistory()
    }

    fun flipVertical() {
        val state = _uiState.value
        _uiState.value = state.copy(flipVertical = !state.flipVertical)
        saveToHistory()
    }

    fun updateCrop(rect: Rect) {
        _uiState.value = _uiState.value.copy(cropRect = rect)
    }

    fun updateFrame(frameId: String?) {
        _uiState.value = _uiState.value.copy(selectedFrameId = frameId)
    }

    fun applyCrop() {
        saveToHistory()
    }

    fun startComparing() {
        _uiState.value = _uiState.value.copy(isComparing = true)
    }

    fun stopComparing() {
        _uiState.value = _uiState.value.copy(isComparing = false)
    }

    fun resetAdjustments() {
        _uiState.value = _uiState.value.copy(
            brightness = 0f,
            contrast = 0f,
            saturation = 0f,
            sharpness = 0f,
            exposure = 0f,
            shadows = 0f,
            highlights = 0f,
            temperature = 0f,
            tint = 0f,
            vignette = 0f
        )
    }

    private fun saveToHistory() {
        val state = _uiState.value
        val historyEntry = EditorHistory(
            brightness = state.brightness,
            contrast = state.contrast,
            saturation = state.saturation,
            sharpness = state.sharpness,
            exposure = state.exposure,
            shadows = state.shadows,
            highlights = state.highlights,
            temperature = state.temperature,
            tint = state.tint,
            vignette = state.vignette,
            rotation = state.rotation,
            flipHorizontal = state.flipHorizontal,
            flipVertical = state.flipVertical
        )

        val newHistory = state.history.take(state.historyIndex + 1) + historyEntry
        _uiState.value = state.copy(
            history = newHistory,
            historyIndex = newHistory.size - 1
        )
    }

    fun undo() {
        val state = _uiState.value
        if (state.historyIndex > 0) {
            val prevState = state.history[state.historyIndex - 1]
            _uiState.value = state.copy(
                brightness = prevState.brightness,
                contrast = prevState.contrast,
                saturation = prevState.saturation,
                sharpness = prevState.sharpness,
                exposure = prevState.exposure,
                shadows = prevState.shadows,
                highlights = prevState.highlights,
                temperature = prevState.temperature,
                tint = prevState.tint,
                vignette = prevState.vignette,
                rotation = prevState.rotation,
                flipHorizontal = prevState.flipHorizontal,
                flipVertical = prevState.flipVertical,
                historyIndex = state.historyIndex - 1
            )
        }
    }

    fun redo() {
        val state = _uiState.value
        if (state.historyIndex < state.history.size - 1) {
            val nextState = state.history[state.historyIndex + 1]
            _uiState.value = state.copy(
                brightness = nextState.brightness,
                contrast = nextState.contrast,
                saturation = nextState.saturation,
                sharpness = nextState.sharpness,
                exposure = nextState.exposure,
                shadows = nextState.shadows,
                highlights = nextState.highlights,
                temperature = nextState.temperature,
                tint = nextState.tint,
                vignette = nextState.vignette,
                rotation = nextState.rotation,
                flipHorizontal = nextState.flipHorizontal,
                flipVertical = nextState.flipVertical,
                historyIndex = state.historyIndex + 1
            )
        }
    }

    fun saveImage(config: SaveConfig, bundle: EditorRenderBundle, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val bitmap = withContext(Dispatchers.IO) {
                decodeFromUri(_uiState.value.imageUri)?.let { renderEditedBitmap(it, bundle) }
            }
            if (bitmap == null) {
                _uiState.update { it.copy(isSaving = false) }
                onResult(false)
                return@launch
            }
            val saved = withContext(Dispatchers.IO) {
                imageSaver.saveImage(bitmap, config) != null
            }
            _uiState.update { it.copy(isSaving = false) }
            onResult(saved)
        }
    }

    fun shareImage(config: SaveConfig, bundle: EditorRenderBundle) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val bitmap = withContext(Dispatchers.IO) {
                decodeFromUri(_uiState.value.imageUri)?.let { renderEditedBitmap(it, bundle) }
            }
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    imageSaver.shareImage(bitmap, config, context)
                }
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun renderEditedBitmap(input: Bitmap, bundle: EditorRenderBundle): Bitmap {
        val state = _uiState.value
        val matrix = EditorColorMatrix.buildValues(
            brightness = state.brightness,
            contrast = state.contrast,
            saturation = state.saturation,
            exposure = state.exposure,
            temperature = state.temperature,
            tint = state.tint,
            shadows = state.shadows,
            highlights = state.highlights,
            filterMatrix = bundle.filterMatrix
        )
        return BitmapRenderer.render(
            context = context,
            input = input,
            rotationDegrees = state.rotation,
            flipHorizontal = state.flipHorizontal,
            flipVertical = state.flipVertical,
            cropRect = state.cropRect,
            colorMatrix = matrix,
            vignette = state.vignette,
            sharpen = state.sharpness,
            stickers = bundle.stickers,
            texts = bundle.texts,
            strokes = bundle.strokes,
            frames = bundle.frames
        )
    }

    private fun decodeFromUri(uri: Uri?): Bitmap? {
        if (uri == null) return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }
}
