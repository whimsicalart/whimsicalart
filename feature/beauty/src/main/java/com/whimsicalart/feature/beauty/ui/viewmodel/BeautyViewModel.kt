package com.whimsicalart.feature.beauty.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whimsicalart.feature.beauty.detection.FaceDetectionResult
import com.whimsicalart.feature.beauty.detection.FaceDetectorManager
import com.whimsicalart.feature.beauty.domain.BeautyProcessor
import com.whimsicalart.feature.beauty.domain.BrushStroke
import com.whimsicalart.feature.beauty.domain.SkinDenoiseProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BeautyUiState(
    val imageUri: Uri? = null,
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val faceResult: FaceDetectionResult? = null,
    val selectedTool: BeautyTool? = null,
    val autoBeautyIntensity: Float = 0f,
    val skinSmoothingIntensity: Float = 0f,
    val teethWhiteningIntensity: Float = 0f,
    val eyeBrighteningIntensity: Float = 0f,
    val skinToneIntensity: Float = 0f,
    val darkCircleIntensity: Float = 0f,
    val spotRemovalIntensity: Float = 0f,
    val wrinkleRemovalIntensity: Float = 0f,
    val brushSize: Float = 30f,
    val brushOpacity: Float = 0.2f,
    val brushStrokes: List<BrushStroke> = emptyList(),
    val activeStroke: BrushStroke? = null,
    val faceSlimIntensity: Float = 0f,
    val eyeEnlargeIntensity: Float = 0f,
    val noseAdjustIntensity: Float = 0f,
    val jawAdjustIntensity: Float = 0f,
    val makeupColor: Int = 0xFFFF4081.toInt(),
    val lipstickIntensity: Float = 0f,
    val blushIntensity: Float = 0f,
    val eyeShadowIntensity: Float = 0f,
    val eyelinerIntensity: Float = 0f,
    val foundationIntensity: Float = 0f,
    val hairColorIntensity: Float = 0f,
    val skinDenoiseIntensity: Float = 0f
)

enum class BeautyTool {
    AUTO_BEAUTY,
    SKIN_SMOOTHING,
    TEETH_WHITENING,
    EYE_BRIGHTENING,
    BRIGHTNESS_PEN,
    DARK_CIRCLE_REMOVAL,
    SPOT_REMOVAL,
    WRINKLE_REMOVAL,
    SKIN_TONE,
    FACE_SLIM,
    EYE_ENLARGE,
    NOSE_ADJUST,
    JAW_ADJUST,
    LIPSTICK,
    BLUSH,
    EYE_SHADOW,
    EYELINER,
    FOUNDATION,
    HAIR_COLOR,
    SKIN_DENOISE
}

object MakeupPalette {
    val colors = listOf(
        0xFFFF4081.toInt(),
        0xFFE91E63.toInt(),
        0xFFF44336.toInt(),
        0xFFFF9800.toInt(),
        0xFFAD1457.toInt(),
        0xFF8D6E63.toInt(),
        0xFF66BB6A.toInt(),
        0xFFFFD600.toInt()
    )
}

@HiltViewModel
class BeautyViewModel @Inject constructor(
    private val faceDetectorManager: FaceDetectorManager,
    private val beautyProcessor: BeautyProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(BeautyUiState())
    val uiState: StateFlow<BeautyUiState> = _uiState.asStateFlow()

    private var renderJob: Job? = null
    private var detectionJob: Job? = null

    internal var processingDispatcher: CoroutineDispatcher = Dispatchers.Default

    private fun Bitmap.recycleIfDifferent(other: Bitmap?) {
        if (this !== other && !isRecycled) recycle()
    }

    private fun Bitmap?.recycleSuperseded(original: Bitmap?) {
        if (this != null && this !== original && !isRecycled) recycle()
    }

    fun setImageUri(uri: Uri, bitmap: Bitmap) {
        detectionJob?.cancel()
        renderJob?.cancel()
        val state = _uiState.value
        _uiState.value = state.copy(
            imageUri = uri,
            originalBitmap = bitmap,
            processedBitmap = bitmap
        )
        state.originalBitmap?.recycleIfDifferent(bitmap)
        state.processedBitmap?.recycleSuperseded(state.originalBitmap)
        detectFaces(bitmap)
    }

    fun selectTool(tool: BeautyTool?) {
        _uiState.update { it.copy(selectedTool = tool) }
    }

    fun updateAutoBeautyIntensity(intensity: Float) {
        _uiState.update { it.copy(autoBeautyIntensity = intensity) }
        refreshProcessed()
    }

    fun updateSkinSmoothingIntensity(intensity: Float) {
        _uiState.update { it.copy(skinSmoothingIntensity = intensity) }
        refreshProcessed()
    }

    fun updateTeethWhiteningIntensity(intensity: Float) {
        _uiState.update { it.copy(teethWhiteningIntensity = intensity) }
        refreshProcessed()
    }

    fun updateEyeBrighteningIntensity(intensity: Float) {
        _uiState.update { it.copy(eyeBrighteningIntensity = intensity) }
        refreshProcessed()
    }

    fun updateSkinToneIntensity(intensity: Float) {
        _uiState.update { it.copy(skinToneIntensity = intensity) }
        refreshProcessed()
    }

    fun updateDarkCircleIntensity(intensity: Float) {
        _uiState.update { it.copy(darkCircleIntensity = intensity) }
        refreshProcessed()
    }

    fun updateSpotRemovalIntensity(intensity: Float) {
        _uiState.update { it.copy(spotRemovalIntensity = intensity) }
        refreshProcessed()
    }

    fun updateWrinkleRemovalIntensity(intensity: Float) {
        _uiState.update { it.copy(wrinkleRemovalIntensity = intensity) }
        refreshProcessed()
    }

    fun updateFaceSlimIntensity(intensity: Float) {
        _uiState.update { it.copy(faceSlimIntensity = intensity) }
        refreshProcessed()
    }

    fun updateEyeEnlargeIntensity(intensity: Float) {
        _uiState.update { it.copy(eyeEnlargeIntensity = intensity) }
        refreshProcessed()
    }

    fun updateNoseAdjustIntensity(intensity: Float) {
        _uiState.update { it.copy(noseAdjustIntensity = intensity) }
        refreshProcessed()
    }

    fun updateJawAdjustIntensity(intensity: Float) {
        _uiState.update { it.copy(jawAdjustIntensity = intensity) }
        refreshProcessed()
    }

    fun updateMakeupColor(color: Int) {
        _uiState.update { it.copy(makeupColor = color) }
        refreshProcessed()
    }

    fun updateLipstickIntensity(intensity: Float) {
        _uiState.update { it.copy(lipstickIntensity = intensity) }
        refreshProcessed()
    }

    fun updateBlushIntensity(intensity: Float) {
        _uiState.update { it.copy(blushIntensity = intensity) }
        refreshProcessed()
    }

    fun updateEyeShadowIntensity(intensity: Float) {
        _uiState.update { it.copy(eyeShadowIntensity = intensity) }
        refreshProcessed()
    }

    fun updateEyelinerIntensity(intensity: Float) {
        _uiState.update { it.copy(eyelinerIntensity = intensity) }
        refreshProcessed()
    }

    fun updateFoundationIntensity(intensity: Float) {
        _uiState.update { it.copy(foundationIntensity = intensity) }
        refreshProcessed()
    }

    fun updateHairColorIntensity(intensity: Float) {
        _uiState.update { it.copy(hairColorIntensity = intensity) }
        refreshProcessed()
    }

    fun updateSkinDenoiseIntensity(intensity: Float) {
        _uiState.update { it.copy(skinDenoiseIntensity = intensity) }
        refreshProcessed()
    }

    fun updateBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size) }
    }

    fun updateBrushOpacity(opacity: Float) {
        _uiState.update { it.copy(brushOpacity = opacity) }
    }

    fun startStroke(point: PointF) {
        val state = _uiState.value
        val stroke = BrushStroke(
            points = listOf(point),
            size = state.brushSize,
            opacity = state.brushOpacity
        )
        _uiState.update { it.copy(activeStroke = stroke) }
    }

    fun addStrokePoint(point: PointF) {
        val active = _uiState.value.activeStroke ?: return
        _uiState.update {
            it.copy(
                activeStroke = active.copy(points = active.points + point)
            )
        }
    }

    fun endStroke(point: PointF?) {
        val state = _uiState.value
        val active = state.activeStroke ?: return
        val stroke = if (point != null) {
            active.copy(points = active.points + point)
        } else {
            active
        }
        _uiState.update {
            it.copy(
                activeStroke = null,
                brushStrokes = it.brushStrokes + stroke
            )
        }
        refreshProcessed()
    }

    fun undoLastStroke() {
        val strokes = _uiState.value.brushStrokes
        if (strokes.isEmpty()) return
        _uiState.update { it.copy(brushStrokes = strokes.dropLast(1)) }
        refreshProcessed()
    }

    fun clearBrushStrokes() {
        _uiState.update { it.copy(brushStrokes = emptyList(), activeStroke = null) }
        refreshProcessed()
    }

    private fun detectFaces(bitmap: Bitmap) {
        detectionJob?.cancel()
        detectionJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessing = true) }
                val result = withContext(processingDispatcher) {
                    faceDetectorManager.detectFaces(bitmap)
                }
                _uiState.update {
                    it.copy(faceResult = result, isProcessing = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message, isProcessing = false)
                }
            }
        }
    }

    private fun refreshProcessed() {
        val state = _uiState.value
        val original = state.originalBitmap ?: return
        val faceResult = state.faceResult ?: return

        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val holder = AtomicReference<Bitmap?>()
            try {
                delay(RENDER_DEBOUNCE_MS)
                _uiState.update { it.copy(isProcessing = true) }
                withContext(processingDispatcher) {
                    holder.set(render(state, original, faceResult))
                    coroutineContext.ensureActive()
                }
                val rendered = holder.get() ?: return@launch
                val previous = _uiState.value.processedBitmap
                _uiState.update {
                    it.copy(processedBitmap = rendered, isProcessing = false)
                }
                previous.recycleSuperseded(original)
            } catch (e: CancellationException) {
                holder.get().recycleSuperseded(original)
                _uiState.update { it.copy(isProcessing = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message, isProcessing = false)
                }
            }
        }
    }

    internal suspend fun render(
        state: BeautyUiState,
        original: Bitmap,
        faceResult: FaceDetectionResult
    ): Bitmap {
        var result = original
        try {
            if (state.autoBeautyIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyAutoBeauty(it, faceResult, state.autoBeautyIntensity)
                }
            }
            if (state.skinSmoothingIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applySkinSmoothing(
                        it, faceResult, 20f, state.skinSmoothingIntensity
                    )
                }
            }
            if (state.teethWhiteningIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyTeethWhitening(it, faceResult, state.teethWhiteningIntensity)
                }
            }
            if (state.eyeBrighteningIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyEyeBrightening(it, faceResult, state.eyeBrighteningIntensity)
                }
            }
            if (state.darkCircleIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyDarkCircleRemoval(it, faceResult, state.darkCircleIntensity)
                }
            }
            if (state.spotRemovalIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applySpotRemoval(it, faceResult, state.spotRemovalIntensity)
                }
            }
            if (state.wrinkleRemovalIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyWrinkleRemoval(it, faceResult, state.wrinkleRemovalIntensity)
                }
            }
            if (state.skinToneIntensity != 0f) {
                result = stage(result, original) {
                    beautyProcessor.applySkinTone(it, faceResult, state.skinToneIntensity)
                }
            }
            if (state.faceSlimIntensity != 0f ||
                state.eyeEnlargeIntensity != 0f ||
                state.noseAdjustIntensity != 0f ||
                state.jawAdjustIntensity != 0f
            ) {
                result = stage(result, original) {
                    beautyProcessor.applyFaceReshape(
                        it, faceResult,
                        state.faceSlimIntensity,
                        state.eyeEnlargeIntensity,
                        state.noseAdjustIntensity,
                        state.jawAdjustIntensity
                    )
                }
            }
            if (state.lipstickIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyLipstick(it, faceResult, state.makeupColor, state.lipstickIntensity)
                }
            }
            if (state.blushIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyBlush(it, faceResult, state.makeupColor, state.blushIntensity)
                }
            }
            if (state.eyeShadowIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyEyeShadow(it, faceResult, state.makeupColor, state.eyeShadowIntensity)
                }
            }
            if (state.eyelinerIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyEyeliner(it, faceResult, state.makeupColor, state.eyelinerIntensity)
                }
            }
            if (state.foundationIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyFoundation(it, faceResult, state.makeupColor, state.foundationIntensity)
                }
            }
            if (state.hairColorIntensity > 0f) {
                result = stage(result, original) {
                    beautyProcessor.applyHairColor(it, faceResult, state.makeupColor, state.hairColorIntensity)
                }
            }
            if (state.skinDenoiseIntensity > 0f) {
                result = stage(result, original) {
                    SkinDenoiseProcessor.denoise(it, denoiseSoftness(state.skinDenoiseIntensity))
                }
            }
            if (state.brushStrokes.isNotEmpty() || state.activeStroke != null) {
                val strokes = state.brushStrokes + listOfNotNull(state.activeStroke)
                result = stage(result, original) {
                    beautyProcessor.applyBrightnessPen(it, strokes, state.brushOpacity)
                }
            }

            return result
        } catch (e: CancellationException) {
            result.recycleSuperseded(original)
            throw e
        }
    }

    internal suspend fun stage(
        current: Bitmap,
        original: Bitmap,
        transform: (Bitmap) -> Bitmap
    ): Bitmap {
        coroutineContext.ensureActive()
        val next = transform(current)
        if (next !== current && current !== original && !current.isRecycled) {
            current.recycle()
        }
        return next
    }

    /**
     * The slider exposes a 0..1 [SkinDenoiseProcessor] "softness"-boundary: its
     * [intensity] grows from left (0, no effect) to right (1, strongest), but the
     * wavelet denoiser is calibrated so higher softness PRESERVES more detail.
     * Invert so a larger slider value produces a stronger effect.
     */
    internal fun denoiseSoftness(intensity: Float): Float =
        (1f - intensity).coerceIn(0f, 1f)

    fun resetToOriginal() {
        renderJob?.cancel()
        val state = _uiState.value
        val original = state.originalBitmap ?: return
        val previous = state.processedBitmap
        _uiState.update {
            it.copy(
                processedBitmap = original,
                autoBeautyIntensity = 0f,
                skinSmoothingIntensity = 0f,
                teethWhiteningIntensity = 0f,
                eyeBrighteningIntensity = 0f,
                skinToneIntensity = 0f,
                darkCircleIntensity = 0f,
                spotRemovalIntensity = 0f,
                wrinkleRemovalIntensity = 0f,
                faceSlimIntensity = 0f,
                eyeEnlargeIntensity = 0f,
                noseAdjustIntensity = 0f,
                jawAdjustIntensity = 0f,
                lipstickIntensity = 0f,
                blushIntensity = 0f,
                eyeShadowIntensity = 0f,
                eyelinerIntensity = 0f,
                foundationIntensity = 0f,
                hairColorIntensity = 0f,
                skinDenoiseIntensity = 0f,
                brushStrokes = emptyList(),
                activeStroke = null
            )
        }
        previous.recycleSuperseded(original)
    }

    override fun onCleared() {
        renderJob?.cancel()
        detectionJob?.cancel()
        super.onCleared()
        faceDetectorManager.close()
    }

    private companion object {
        const val RENDER_DEBOUNCE_MS = 120L
    }
}