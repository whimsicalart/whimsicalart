package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import codes.pepper.whimsicalart.feature.beauty.domain.SkinDenoiseProcessor
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometry
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryBaseSource
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryContext
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryGenerator
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyLayerSpec
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyProcessor
import codes.pepper.whimsicalart.feature.beauty.domain.DefaultBeautyGeometryContext
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyParams
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyTool
import codes.pepper.whimsicalart.feature.editor.domain.BitmapRenderer
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.domain.StackRoot
import codes.pepper.whimsicalart.feature.editor.domain.MergedEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautySlimEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyEyeEnlargeEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyNoseEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyJawEffect
import codes.pepper.whimsicalart.feature.editor.domain.toBeautyEffects
import codes.pepper.whimsicalart.feature.editor.domain.CropEffect
import codes.pepper.whimsicalart.feature.editor.domain.TransformEffect
import codes.pepper.whimsicalart.feature.editor.domain.SkinDenoiseEffect
import codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect
import codes.pepper.whimsicalart.feature.editor.domain.FilterEffect
import codes.pepper.whimsicalart.feature.editor.domain.EnhanceEffect
import codes.pepper.whimsicalart.feature.editor.domain.StyleEffect
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect
import codes.pepper.whimsicalart.feature.editor.domain.BitmapDiffEffect
import codes.pepper.whimsicalart.feature.editor.domain.StickerEffect
import codes.pepper.whimsicalart.feature.editor.domain.TextEffect
import codes.pepper.whimsicalart.feature.editor.domain.FrameEffect
import codes.pepper.whimsicalart.feature.editor.domain.StrokeType
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.SaveConfig
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehShape
import codes.pepper.whimsicalart.feature.editor.domain.enhance.EnhanceProcessor
import codes.pepper.whimsicalart.feature.editor.domain.enhance.EnhanceSettings
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilterProcessor
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehProcessor
import codes.pepper.whimsicalart.feature.editor.domain.matting.BackgroundReplacer
import codes.pepper.whimsicalart.feature.editor.domain.matting.SelfieMattingSegmenter
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MlKitOcrDetector
import codes.pepper.whimsicalart.feature.editor.domain.ocr.OcrOverlayFragment
import codes.pepper.whimsicalart.feature.editor.domain.ocr.OcrTextExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class EditorUiState(
    val imageUri: Uri? = null,
    val originalBitmap: Bitmap? = null,
    val editedBitmap: Bitmap? = null,
    // Per-sub-tool beauty parameters — the editor is the single owner of both
    // this state AND the effect stack it derives from it (they are NOT held in
    // the beauty panel's own ViewModel anymore).
    val selectedBeautyTool: BeautyTool? = null,
    val beauty: BeautyParams = BeautyParams(),
    val sharpenedPreview: Bitmap? = null,
    // The continuously-rendered committed composite shown as the main preview
    // base: the full effect stack folded over the pristine photo (see
    // effect_stack.md "lastImage is the image shown in the preview"). Geometric
    // base steps (transform/crop) are baked INTO these pixels, so the committed
    // display does NOT re-apply the display-only rotation/flips on top. Null
    // while a source-space placement tool (crop grid / brush / sticker / text)
    // needs the raw live preview instead.
    val compositePreview: Bitmap? = null,
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
    val skinDenoise: Float = 0f,
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropRect: Rect? = null,
    val selectedFrameId: String? = null,
    val isComparing: Boolean = false,
    val history: List<StackDocument> = emptyList(),
    val historyIndex: Int = -1,
    val backgroundMode: BackgroundMode = BackgroundMode.BLUR,
    val backgroundBlurRadius: Float = 10f,
    val backgroundShape: BokehShape = BokehShape.CIRCLE,
    val subjectMask: Bitmap? = null,
    val isSegmenting: Boolean = false,
    val selectedBackgroundRes: Int? = null,
    val backgroundImage: Bitmap? = null,
    val enhanceEnabled: Boolean = false,
    val styleFilter: StyleFilter? = null,
    val selectedFilterId: String? = null,
    val enabledLensFilterIds: Set<String> = emptySet(),
    val effectStack: List<StackEffect> = emptyList(),
    val hasMerged: Boolean = false,
    val isLayersVisible: Boolean = false,
    val layerThumbnails: Map<String, Bitmap> = emptyMap(),
    val layerFingerprints: Map<String, String> = emptyMap()
)

/**
 * A full snapshot of the editor's reversible state at a single commit point.
 * Used for stack-aware undo/redo: every meaningful user action pushes a
 * [StackDocument]; undo/redo restores the snapshot (effectStack + all live
 * controls), which the screen's LaunchedEffect then syncs back into the stack.
 *
 * Large bitmaps (originalBitmap, subjectMask, backgroundImage) are captured
 * by reference since they don't change between undo steps in normal usage.
 */
data class StackDocument(
    val effectStack: List<StackEffect>,
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
    val skinDenoise: Float = 0f,
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropRect: Rect? = null,
    val selectedFrameId: String? = null,
    val enhanceEnabled: Boolean = false,
    val styleFilter: StyleFilter? = null,
    val backgroundMode: BackgroundMode = BackgroundMode.BLUR,
    val backgroundBlurRadius: Float = 10f,
    val backgroundShape: BokehShape = BokehShape.CIRCLE,
    val subjectMask: Bitmap? = null,
    val backgroundImage: Bitmap? = null,
    val selectedBackgroundRes: Int? = null,
    val selectedFilterId: String? = null,
    val enabledLensFilterIds: Set<String> = emptySet(),
    // Per-sub-tool beauty parameters (editor-owned), captured so undo/redo restores
    // both the effect stack AND the params that derive it.
    val beauty: BeautyParams = BeautyParams()
)

enum class EditTool {
    BEAUTY,
    CROP,
    TRANSFORM,
    FILTERS,
    SKIN_DENOISE,
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
    ENHANCE,
    STICKERS,
    TEXT,
    FRAMES,
    MOSAIC,
    BLUR_BRUSH,
    PEN,
    BACKGROUND,
    OBJECT_REMOVAL
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageSaver: ImageSaver,
    private val ocrDetector: MlKitOcrDetector,
    private val beautyProcessor: BeautyProcessor = BeautyProcessor(),
    private val beautyGeometryContext: BeautyGeometryContext =
        DefaultBeautyGeometryContext(BeautyGeometryGenerator { BeautyGeometry.NONE })
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var sharpnessJob: kotlinx.coroutines.Job? = null
    private var sharpnessDecodedSource: Bitmap? = null
    private var sharpnessPreviewRunning = false
    private var sharpnessPreviewDirty = false

    // Continuous committed-composite preview: renders the whole effect stack
    // (with geometric base steps baked in) into `compositePreview`. Debounced +
    // cancel-and-restart so a frenetic slider drag re-renders with the latest
    // value rather than stacking many full-image renders. The fold itself is
    // cheap for unchanged parts via the incremental cache below.
    private var compositeJob: kotlinx.coroutines.Job? = null
    private var compositeRunning = false
    private var compositeDirty = false

    // The full-size background fold (docs/effect_stack.md → Performance / preview
    // cache): scheduled only when zoomed past 100% (or a full-res result is
    // needed), debounced so a pinch or slider drag doesn't spawn one fold per
    // frame, and cancelled + re-issued whenever controls change mid-flight so it
    // always restarts with the freshest values. Until it lands, the small
    // preview-resolution composite keeps showing.
    private var fullFoldJob: Job? = null

    // Incremental fold cache — the designed 3-image model (see
    // docs/effect_stack.md → Core model + Behaviour rules):
    //
    //  - originalImage : the pristine decoded source (never mutated, the ultimate
    //    base of every fold). Cached so we don't re-decode from the URI (an
    //    expensive blocking op) on every committed render. Carries NO trace
    //    (no staleness flag / cursor) — it is always the full-size first base.
    //  - currentImage : the composite of every effect BEFORE the currently-edited
    //    tool (the base the tool is applied to).
    //  - lastImage : the full composite of the WHOLE stack (the preview result).
    //
    // The three caches (current + last; original is not a cache) each exist at
    // TWO resolutions (docs/effect_stack.md → Performance / preview cache):
    //  - PREVIEW resolution: the common path — used for the on-screen composite
    //    while zoom ≤100% and as the stand-in while a full-size fold runs. Sized
    //    to the visible pixel footprint (visibleDrawnPixels + margin), so it is a
    //    small fraction of the full image.
    //  - FULL resolution: computed only when the user zooms past 100% (or a zoom
    //    is already set) or a full-res result (save / merge / share) is needed.
    //
    // Each cache entry is keyed by a fingerprint of the effect list that produced
    // it. If the current prefix still matches the prefix signature (and the full
    // stack matches the stack signature), a render re-uses the cached
    // currentImage/lastImage and only re-folds the tail — never re-folding the
    // unchanged prefix from the base. The cache is BYTE-IDENTICAL to a from-base
    // re-fold at the SAME resolution; it only avoids repeating fold work. At most
    // two intermediates per resolution (current + last) are held alongside the
    // original.
    private var foldCacheOriginal: Bitmap? = null
    private val fullCache = FoldImageCache()
    private val previewCache = FoldImageCache()

    // Per-layer preview cache (docs/effect_stack.md → Performance / preview
    // cache): one PREVIEW-resolution composite per StackEffect layer, keyed by
    // layerKey, sized to the current preview scale bucket, and fingerprint-tagged
    // with the effect that produced it. The Layers window — and its row
    // thumbnails, which are plain downscales of these previews — serves from this
    // cache instead of ever running a full fold. Building all layers is one
    // incremental pass (each layer's preview is the previous layer's preview
    // folded with one more effect, reusing the shared beauty geometry context), so
    // it stays cheap. All entries are independent copies; evicted (and recycled)
    // on fingerprint invalidation or when the preview scale bucket changes.
    private val layerPreviewCache = HashMap<String, Bitmap>()
    private val layerPreviewFingerprints = HashMap<String, String>()
    private var layerPreviewScaleBucket = -1f
    private var layerPreviewsJob: Job? = null

    // Viewing footprint (the region where the preview is actually drawn) — the
    // editor's only awareness of the on-screen canvas; everything else (pan/zoom
    // composition) is a pure Compose transform. Drives the preview-resolution
    // computation (previewScale). Set by the screen via [setViewBox]/[setZoom].
    private var viewportWidthPx = 0
    private var viewportHeightPx = 0
    private var viewZoom = 1f
    private val previewOriginalScaleCache = HashMap<Int, Bitmap>()
    private val scaledArtifactCache = HashMap<String, Bitmap>()

    private lateinit var segmenter: SelfieMattingSegmenter

    /**
     * The editor's SHARED beauty geometry context (see [BeautyGeometryContext]).
     * Every beauty layer resolves its geometry through this same instance,
     * lazily, against the GEOMETRY TRACK base (see [bindBeautyGeometries]) — the
     * only image the context may be asked to trace from is that track, never the
     * colour running image. The editor does NOT read its data — it only calls
     * [BeautyGeometryContext.markStale] when a deforming effect is applied
     * (rotate/flip/crop/reshape) and [BeautyGeometryContext.flatten] on merge.
     */
    private val geometryContext: BeautyGeometryContext get() = beautyGeometryContext

    // GEOMETRY TRACK (docs/effect_stack.md → Original geometry image and
    // original geometry). Beauty geometry is grounded in a base separate from
    // the three colour fold caches: originalGeometryImage starts as a REFERENCE
    // to the pristine original (shared memory, never mutated); a merge that
    // baked a geometry-changing effect regenerates it as an INDEPENDENT instance
    // (regeneratedGeometryImage) by folding every deforming/occluding effect
    // over the pristine original. The colour caches never seed from / populate
    // this track.
    private var regeneratedGeometryImage: Bitmap? = null

    // Per-resolution geometry-track cache: the signature of the cumulative
    // geometry-changing effects → the deformed base INSTANCE for each segment.
    // A segment base is built by folding the segment's boundary deforming effect
    // over the cached preceding segment base, so an unchanged segment keeps the
    // SAME instance across fold passes — the geometry context's identity guard
    // then reuses the resolved geometry (ML once per segment × resolution), and
    // only a changed deforming effect, a changed resolution bucket, or a merge
    // re-grounding produce a new instance and regenerate. Only FOLDED bases are
    // owned here; the segment-0 base (the original geometry image itself or a
    // scaledArtifact) is never stored.
    private val geometryTrackCache = HashMap<String, Bitmap>()

    init {
        segmenter = SelfieMattingSegmenter(context)
    }

    fun setImageUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            imageUri = uri,
            layerThumbnails = emptyMap(),
            layerFingerprints = emptyMap()
        )
        // A different photo invalidates the cached preview decode from the
        // previous image, otherwise sharpen/enhance/style re-use stale pixels.
        sharpnessDecodedSource?.recycle()
        sharpnessDecodedSource = null
        sharpnessJob?.cancel()
        sharpnessPreviewRunning = false
        sharpnessPreviewDirty = false
        // A new photo invalidates the incremental fold caches including the
        // cached original decode (whose pixels no longer match the new URI), and
        // every per-layer preview built from it.
        clearLayerPreviews()
        invalidateFoldCache(clearOriginal = true)
    }

    fun setBitmaps(original: Bitmap, edited: Bitmap) {
        _uiState.value = _uiState.value.copy(
            originalBitmap = original,
            editedBitmap = edited
        )
    }

    /**
     * Upserts a per-sub-tool beauty effect into the single ordered [effectStack].
     * Replaces any existing layer with the same [BeautyStackEffect.layerKey]
     * **in place** (so re-editing the same sub-tool re-applies at its current
     * position, and a user-reordered position is preserved) or appends a new one.
     * Non-beauty layers keep their positions. Geometry is resolved by the
     * geometry track ([bindBeautyGeometries]) against the stack folded over the
     * original geometry image, so a re-edited or moved beauty layer re-binds with
     * the track's current base for its position.
     */
    fun upsertBeautyEffect(effect: BeautyStackEffect) {
        saveToHistory()
        _uiState.update { state ->
            state.copy(effectStack = mergeBeautyIntoStack(state.effectStack, listOf(effect)))
        }
        invalidateFoldCache()
        refreshPreviewLook()
    }

    /** Removes one beauty sub-tool layer by its [BeautyStackEffect.layerKey] from the stack. */
    fun removeBeautyEffect(layerKey: String) {
        saveToHistory()
        _uiState.update { state ->
            state.copy(
                effectStack = state.effectStack.filterNot {
                    it is BeautyStackEffect && it.layerKey == layerKey
                }
            )
        }
        invalidateFoldCache()
        refreshPreviewLook()
    }

    /** Clears every beauty sub-tool layer from the stack (keeps non-beauty layers). */
    fun clearBeautyEffects() {
        val current = _uiState.value.effectStack
        if (current.any { it is BeautyStackEffect }) {
            saveToHistory()
            _uiState.update { it.copy(effectStack = it.effectStack.filterNot { e -> e is BeautyStackEffect }) }
        }
        invalidateFoldCache()
        refreshPreviewLook()
    }

    /**
     * Replaces the beauty sub-set of the single ordered [effectStack] (live
     * streaming from the beauty UI). Unlike [upsertBeautyEffect]/[removeBeautyEffect]
     * this does NOT push an undo entry per slider tick — drags update live and
     * commit via the stack sync / a later gesture boundary — avoiding undo-history
     * flooding. Each existing beauty layer is refreshed **in place** (its
     * user-reordered position is preserved); layers turned off are dropped; newly
     * active layers are appended; non-beauty layers keep their positions.
     */
    fun setBeautyEffects(effects: List<BeautyStackEffect>) {
        // Any geometry-changing beauty op (a reshape dimension) deforms the
        // image: mark the shared geometry context stale so a following beauty
        // layer regenerates.
        if (effects.any { it.changesGeometry }) geometryContext.markStale()
        _uiState.update { it.copy(effectStack = mergeBeautyIntoStack(it.effectStack, effects)) }
        invalidateFoldCache()
        refreshPreviewLook()
    }

    /**
     * Changes the selected beauty sub-tool (which control set the panel shows).
     * This is pure UI state and is NOT captured in undo history.
     */
    fun selectBeautyTool(tool: BeautyTool?) {
        _uiState.update { it.copy(selectedBeautyTool = tool) }
    }

    /**
     * Applies [transform] to the per-sub-tool beauty parameters in `EditorUiState`
     * (the editor owns them) and re-derives the beauty sub-set of `effectStack`
     * from the updated params — live, without a `saveToHistory()` per slider tick
     * (the stack-sync / a later gesture boundary commits the undo entry).
     */
    fun updateBeautyParams(transform: (BeautyParams) -> BeautyParams) {
        val newParams = transform(_uiState.value.beauty)
        _uiState.update { it.copy(beauty = newParams) }
        setBeautyEffects(newParams.buildBeautySpecs().toBeautyEffects())
    }

    /**
     * Replaces the editor-owned beauty parameters wholesale (reported by the
     * stateless beauty panel on every control change) and re-derives the beauty
     * sub-set of `effectStack` — live, without an undo entry per slider tick.
     */
    fun setBeautyParams(params: BeautyParams) {
        _uiState.update { it.copy(beauty = params) }
        setBeautyEffects(params.buildBeautySpecs().toBeautyEffects())
    }

    /**
     * Derives one [BeautyLayerSpec] per active beauty sub-tool (in the fold order)
     * from the editor-owned beauty parameters — the editor-side equivalent of the
     * former `BeautyViewModel.activeSpecs()`. Geometry is resolved lazily by the
     * shared geometry context at fold time, so specs carry no geometry.
     */
    private fun BeautyParams.buildBeautySpecs(): List<BeautyLayerSpec> {
        val list = mutableListOf<BeautyLayerSpec>()
        if (auto > 0f) list += BeautyLayerSpec("beauty:auto", auto)
        if (smoothing > 0f) list += BeautyLayerSpec("beauty:smoothing", smoothing)
        if (teeth > 0f) list += BeautyLayerSpec("beauty:teeth", teeth)
        if (eyeBrighten > 0f) list += BeautyLayerSpec("beauty:eye_brighten", eyeBrighten)
        if (darkCircle > 0f) list += BeautyLayerSpec("beauty:dark_circles", darkCircle)
        if (spots > 0f) list += BeautyLayerSpec("beauty:spots", spots)
        if (wrinkles > 0f) list += BeautyLayerSpec("beauty:wrinkles", wrinkles)
        if (skinTone != 0f) list += BeautyLayerSpec("beauty:skin_tone", skinTone)
        if (faceSlim != 0f) list += BeautyLayerSpec("beauty:slim", faceSlim)
        if (eyeEnlarge != 0f) list += BeautyLayerSpec("beauty:eye_enlarge", eyeEnlarge)
        if (nose != 0f) list += BeautyLayerSpec("beauty:nose", nose)
        if (jaw != 0f) list += BeautyLayerSpec("beauty:jaw", jaw)
        if (lipstick > 0f) list += BeautyLayerSpec("beauty:lipstick", lipstick, color = makeupColor)
        if (blush > 0f) list += BeautyLayerSpec("beauty:blush", blush, color = makeupColor)
        if (eyeShadow > 0f) list += BeautyLayerSpec("beauty:eye_shadow", eyeShadow, color = makeupColor)
        if (eyeliner > 0f) list += BeautyLayerSpec("beauty:eyeliner", eyeliner, color = makeupColor)
        if (foundation > 0f) list += BeautyLayerSpec("beauty:foundation", foundation, color = makeupColor)
        if (hair > 0f) list += BeautyLayerSpec("beauty:hair", hair, color = makeupColor)
        if (brushStrokes.isNotEmpty() || activeStroke != null) {
            list += BeautyLayerSpec(
                toolKey = "beauty:pen",
                strokes = brushStrokes + listOfNotNull(activeStroke),
                opacity = brushOpacity
            )
        }
        return list
    }

    /**
     * Merges the new [effects] beauty sub-set into [currentStack] (the single
     * ordered effect stack), preserving each existing beauty layer's position and
     * all non-beauty layers' positions: existing beauty layers are refreshed in
     * place by [StackEffect.layerKey], dropped ones are removed, and any newly
     * active beauty layers are appended in [effects] order.
     */
    private fun mergeBeautyIntoStack(
        currentStack: List<StackEffect>,
        effects: List<BeautyStackEffect>
    ): List<StackEffect> {
        val newByKey = effects.associateBy { it.layerKey }
        val result = mutableListOf<StackEffect>()
        for (e in currentStack) {
            if (e is BeautyStackEffect) {
                newByKey[e.layerKey]?.let { result += it }
            } else {
                result += e
            }
        }
        val present = result.mapTo(mutableSetOf()) { it.layerKey }
        for (e in effects) {
            if (present.add(e.layerKey)) result += e
        }
        return result
    }

    fun selectTool(tool: EditTool?) {
        saveToHistory()
        _uiState.value = _uiState.value.copy(selectedTool = tool)
        // Auto-detect the subject the moment the Background tool is opened, so
        // the user never has to tap a manual "auto-detect" button.
        if (tool == EditTool.BACKGROUND &&
            _uiState.value.subjectMask == null &&
            !_uiState.value.isSegmenting
        ) {
            segmentSubject()
        }
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
        refreshPreviewLook()
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

    fun updateSkinDenoise(value: Float) {
        _uiState.value = _uiState.value.copy(skinDenoise = value)
        refreshPreviewLook()
    }

    fun toggleEnhance() {
        saveToHistory()
        _uiState.value = _uiState.value.copy(enhanceEnabled = !_uiState.value.enhanceEnabled)
        refreshPreviewLook()
    }

fun setStyleFilter(filter: StyleFilter?) {
        saveToHistory()
        // Picking a style filter is mutually exclusive with the matrix-based
        // preset filters — the two share the same "Filters" tool slot.
        _uiState.update {
            it.copy(styleFilter = filter, selectedFilterId = if (filter != null) null else it.selectedFilterId)
        }
        refreshPreviewLook()
    }

    fun setSelectedFilterId(filterId: String?) {
        saveToHistory()
        // Mutually exclusive with the StyleFilter chips (same Filters tool slot).
        _uiState.update {
            it.copy(selectedFilterId = filterId, styleFilter = if (filterId != null) null else it.styleFilter)
        }
        refreshPreviewLook()
    }

    /** Toggles a simulated lens filter on/off. Lens filters multi-select/stack. */
    fun toggleLensFilter(filterId: String) {
        saveToHistory()
        _uiState.update {
            val current = it.enabledLensFilterIds
            it.copy(enabledLensFilterIds = if (filterId in current) current - filterId else current + filterId)
        }
        refreshPreviewLook()
    }

    fun clearLensFilters() {
        saveToHistory()
        _uiState.update { it.copy(enabledLensFilterIds = emptySet()) }
        refreshPreviewLook()
    }

    fun rotateLeft() {
        setRotation(normalizeRotation(_uiState.value.rotation - 90f))
    }

    fun rotateRight() {
        setRotation(normalizeRotation(_uiState.value.rotation + 90f))
    }

    /**
     * Sets an arbitrary (free) rotation angle in degrees, normalized to [0, 360).
     * This backs both the 90°-snap buttons (via [rotateLeft]/[rotateRight]) and
     * the transform tool's free-rotate slider. Any deformation marks the shared
     * beauty geometry context stale so downstream beauty geometry is recomputed.
     */
    fun setRotation(rotation: Float) {
        val normalized = normalizeRotation(rotation)
        if (normalized == _uiState.value.rotation) return
        _uiState.value = _uiState.value.copy(rotation = normalized)
        geometryContext.markStale()
        saveToHistory()
    }

    private fun normalizeRotation(rotation: Float): Float {
        return ((rotation % 360f) + 360f) % 360f
    }

    fun flipHorizontal() {
        val state = _uiState.value
        _uiState.value = state.copy(flipHorizontal = !state.flipHorizontal)
        geometryContext.markStale()
        saveToHistory()
    }

    fun flipVertical() {
        val state = _uiState.value
        _uiState.value = state.copy(flipVertical = !state.flipVertical)
        geometryContext.markStale()
        saveToHistory()
    }

    fun updateCrop(rect: Rect) {
        _uiState.value = _uiState.value.copy(cropRect = rect)
    }

    fun updateFrame(frameId: String?) {
        saveToHistory()
        _uiState.value = _uiState.value.copy(selectedFrameId = frameId)
    }

    fun applyCrop() {
        geometryContext.markStale()
        saveToHistory()
    }

    fun clearCrop() {
        _uiState.update { it.copy(cropRect = null) }
    }

    fun clearTransform() {
        _uiState.update {
            it.copy(rotation = 0f, flipHorizontal = false, flipVertical = false)
        }
        geometryContext.markStale()
        invalidateFoldCache()
        saveToHistory()
        refreshPreviewLook()
    }

    fun startComparing() {
        _uiState.value = _uiState.value.copy(isComparing = true)
    }

    fun stopComparing() {
        _uiState.value = _uiState.value.copy(isComparing = false)
    }

    fun updateBackgroundMode(mode: BackgroundMode) {
        _uiState.update { it.copy(backgroundMode = mode) }
        refreshPreviewLook()
    }

    fun updateBackgroundBlurRadius(radius: Float) {
        _uiState.update { it.copy(backgroundBlurRadius = radius) }
        refreshPreviewLook()
    }

    fun updateBackgroundShape(shape: BokehShape) {
        _uiState.update { it.copy(backgroundShape = shape) }
        refreshPreviewLook()
    }

    /** Runs on-device person segmentation and stores the subject alpha mask. */
    fun segmentSubject() {
        if (_uiState.value.isSegmenting) return
        val source = decodeFromUri(_uiState.value.imageUri) ?: return
        _uiState.update { it.copy(isSegmenting = true) }
        viewModelScope.launch {
            val mask = withContext(Dispatchers.Default) {
                segmenter.segment(source)
            }
            source.recycle()
            _uiState.update {
                it.copy(isSegmenting = false, subjectMask = mask)
            }
            if (mask == null) {
                _uiState.update { it.copy(error = "Subject detection failed.") }
            }
            refreshPreviewLook()
        }
    }

    fun clearSubjectMask() {
        _uiState.update { it.copy(subjectMask = null) }
        refreshPreviewLook()
    }

    /**
     * Recognizes text in the edited bitmap (falling back to the original) and
     * returns overlay-ready fragments. Runs OCR off the main thread; the caller
     * (the text tool) imports the fragments as editable text overlays. Returns
     * an empty list when OCR finds nothing or fails.
     */
    suspend fun recognizeText(): List<OcrOverlayFragment> {
        val bitmap = _uiState.value.editedBitmap
            ?: _uiState.value.originalBitmap
            ?: return emptyList()
        return withContext(Dispatchers.Default) {
            val lines = ocrDetector.recognize(bitmap)
            OcrTextExtractor.overlayFragments(lines, bitmap.width, bitmap.height)
        }
    }

    /** Loads one of the bundled (free-to-use) replacement backgrounds. */
    fun loadBackground(@androidx.annotation.DrawableRes res: Int) {
        viewModelScope.launch {
            val image = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = androidx.core.content.ContextCompat.getDrawable(context, res)
                    if (drawable == null) return@runCatching null
                    val max = 1920
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    val outW = if (maxOf(w, h) > max) w * max / maxOf(w, h) else w
                    val outH = if (maxOf(w, h) > max) h * max / maxOf(w, h) else h
                    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, outW, outH)
                    drawable.draw(android.graphics.Canvas(bmp))
                    bmp
                }.getOrNull()
            }
            if (image != null) {
                _uiState.update { it.copy(selectedBackgroundRes = res, backgroundImage = image) }
                refreshPreviewLook()
            }
        }
    }

    /** Uses a user-picked photo as the replacement background. */
    fun setCustomBackground(uri: Uri) {
        viewModelScope.launch {
            val image = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()?.let { downscale(it, 1920) }
            }
            if (image != null) {
                _uiState.update { it.copy(selectedBackgroundRes = null, backgroundImage = image) }
                refreshPreviewLook()
            }
        }
    }

    fun clearBackground() {
        _uiState.update {
            it.copy(subjectMask = null, backgroundImage = null, selectedBackgroundRes = null)
        }
        refreshPreviewLook()
    }

    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= maxDim) return bitmap
        val w = (bitmap.width * maxDim / max).coerceAtLeast(1)
        val h = (bitmap.height * maxDim / max).coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /**
     * Flows [next] into the preview pipeline as the new `look`, recycling the
     * previous [old] intermediate bitmap unless it is the shared cached source
     * (which stays alive for the whole session) or the very same bitmap. Keeps
     * a chain of sharpen/enhance/style/background passes from leaking bitmaps
     * during repeated preview refreshes.
     */
    private fun recycleAndApply(old: Bitmap, next: Bitmap): Bitmap {
        if (next !== old && old !== sharpnessDecodedSource) {
            old.recycle()
        }
        return next
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
        refreshPreviewLook()
    }

    /** Opens / closes the Layers panel. */
    fun toggleLayers() {
        _uiState.update { it.copy(isLayersVisible = !it.isLayersVisible) }
    }

    /**
     * Invalidates lazy thumbnails for every layer whose parameters changed, and
     * for every layer that appears after a changed layer (their input changed).
     * Called on every edit so the Layers window lazily recomputes stale layers
     * on its next open; layers that did not change keep their cached thumbnail
     * (no work is done while an unchanged window is merely revisited). The
     * corresponding per-layer preview-cache entries are dropped too, so the next
     * open rebuilds exactly the tail after the first changed layer.
     *
     * The snapshot is MERGED into the existing stack in the user's chosen order
     * (rather than overwriting it), so reordering the Layers list is preserved
     * while each effect's params are refreshed in place from [stack].
     */
    fun syncLayerState(stack: List<StackEffect>, fingerprints: Map<String, String>) {
        val current = _uiState.value
        val merged = orderedMerge(current.effectStack, stack)
        // Prefix-contagious invalidation: the first layer whose parameters
        // changed invalidates every layer after it; unchanged layers keep their
        // cached thumbnail.
        val changedIndex = merged.indexOfFirst { e ->
            current.layerFingerprints[e.layerKey] != fingerprints[e.layerKey]
        }
        val staleKeys: Set<String> = if (changedIndex == -1) {
            emptySet()
        } else {
            merged.drop(changedIndex).map(StackEffect::layerKey).toSet()
        }
        _uiState.update {
            it.copy(
                effectStack = merged,
                layerThumbnails = it.layerThumbnails - staleKeys,
                layerFingerprints = if (changedIndex == -1) it.layerFingerprints
                else it.layerFingerprints - staleKeys
            )
        }
        if (staleKeys.isNotEmpty()) evictLayerPreviews(staleKeys)
    }

    /**
     * Reorders the effect stack (Layers drag-to-reorder). Saves history on both
     * sides of the move (pre-move and post-move) so an immediate undo restores
     * the original ordering, invalidates every layer that shifted (their input
     * order changed — their per-layer previews are dropped too), then refreshes
     * the preview/final-render from the new order.
     */
    fun moveEffect(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.effectStack
        if (list.isEmpty() || fromIndex !in list.indices || toIndex !in list.indices) return
        if (fromIndex == toIndex) return
        saveToHistory()
        val moved = list.toMutableList()
        val item = moved.removeAt(fromIndex)
        moved.add(toIndex, item)
        val start = minOf(fromIndex, toIndex)
        val invalidated = moved.drop(start).map(StackEffect::layerKey).toSet()
        _uiState.update {
            it.copy(
                effectStack = moved,
                layerThumbnails = it.layerThumbnails - invalidated,
                layerFingerprints = it.layerFingerprints - invalidated
            )
        }
        evictLayerPreviews(invalidated)
        val hasGeomChange = moved.drop(start).any { it.changesGeometry }
        if (hasGeomChange) geometryContext.markStale()
        invalidateFoldCache()
        saveToHistory()
        refreshPreviewLook()
    }

    /**
     * (Re)builds the per-layer preview cache at the current preview resolution
     * and publishes the Layers-window row thumbnails as plain downscales of it
     * (docs/effect_stack.md → Performance / preview cache). One incremental fold
     * pass: each layer's preview is the previous layer's cached preview folded
     * with one more effect (byte-identical to folding from the base); layers
     * whose fingerprint is unchanged are reused untouched. The thumbnails then
     * replace the standalone `layerThumbnails` generation — no full fold ever
     * runs for the common Layers path. Rebuilds only the tail after the first
     * stale layer (see [syncLayerState]/[moveEffect] eviction), so revisiting an
     * unchanged Layers window does no work.
     */
    fun refreshLayerPreviews(bundle: EditorRenderBundle) {
        layerPreviewsJob?.cancel()
        layerPreviewsJob = viewModelScope.launch {
            val state = _uiState.value
            val stack = state.effectStack
            if (stack.isEmpty()) return@launch
            val dims = sourceDims() ?: return@launch
            val scale = previewScaleFor(dims.first, dims.second)
            if (layerPreviewScaleBucket != scale) {
                clearLayerPreviews()
                layerPreviewScaleBucket = scale
            }
            val mergedBase = state.editedBitmap?.takeIf { state.hasMerged && !it.isRecycled }
            val base = if (mergedBase != null) {
                val w = (mergedBase.width * scale).roundToInt().coerceAtLeast(1)
                val h = (mergedBase.height * scale).roundToInt().coerceAtLeast(1)
                scaledArtifact(mergedBase, w, h)
            } else {
                cachedBaseDecode(scale)
            } ?: return@launch
            var prevComposite: Bitmap = base
            // Prefix-validity: while every layer up to i is cached with a matching
            // fingerprint at the same dims, prevComposite can walk the cache; at
            // the first stale layer the whole tail is re-folded incrementally.
            var chainValid = true
            for (i in stack.indices) {
                val effect = stack[i]
                val key = effect.layerKey
                val fp = stackEffectFingerprint(effect)
                val cached = layerPreviewCache[key]
                if (chainValid && cached != null && !cached.isRecycled &&
                    layerPreviewFingerprints[key] == fp &&
                    cached.width == prevComposite.width && cached.height == prevComposite.height
                ) {
                    prevComposite = cached
                    continue
                }
                chainValid = false
                val rendered = if (effect is MergedEffect) {
                    // A merged layer is the identity bake of its base at preview
                    // resolution (its image is full-size; folding it would leak
                    // full-size pixels into the preview cache).
                    prevComposite
                } else {
                    withContext(Dispatchers.Default) {
                        renderStackWithBeautyGeometry(
                            context, base, stack.take(i + 1),
                            resumeAt = i, resumeBitmap = prevComposite,
                            adaptBackgroundArtifacts = scale < 1f,
                            scale = scale
                        )
                    }
                }
                val stored = rendered.copy(Bitmap.Config.ARGB_8888, true)
                if (rendered !== prevComposite && !rendered.isRecycled) rendered.recycle()
                layerPreviewCache[key]?.takeUnless(Bitmap::isRecycled)?.recycle()
                layerPreviewCache[key] = stored
                layerPreviewFingerprints[key] = fp
                prevComposite = stored
            }
            // Bounded LRU-by-insertion: drop the oldest entries beyond the cap.
            while (layerPreviewCache.size > LAYER_PREVIEW_CACHE_MAX) {
                val oldest = layerPreviewCache.keys.firstOrNull() ?: break
                layerPreviewCache.remove(oldest)?.takeUnless(Bitmap::isRecycled)?.recycle()
                layerPreviewFingerprints.remove(oldest)
            }
            // Publish row thumbnails = previews downscaled to ≤100×100.
            val thumbs = HashMap<String, Bitmap>()
            for (e in stack) {
                val p = layerPreviewCache[e.layerKey] ?: continue
                thumbs[e.layerKey] = scaleToLayerThumb(p)
            }
            _uiState.update {
                it.copy(
                    layerThumbnails = thumbs,
                    layerFingerprints = layerPreviewFingerprints.toMap()
                )
            }
        }
    }

    /** Drops (and recycles) the per-layer preview cache and its fingerprints. */
    private fun clearLayerPreviews() {
        layerPreviewCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
        layerPreviewCache.clear()
        layerPreviewFingerprints.clear()
    }

    /** Drops (and recycles) the per-layer previews for [staleKeys]. */
    private fun evictLayerPreviews(staleKeys: Set<String>) {
        staleKeys.forEach { key ->
            layerPreviewCache.remove(key)?.takeUnless(Bitmap::isRecycled)?.recycle()
            layerPreviewFingerprints.remove(key)
        }
    }

    /** Downscales a layer's preview to the ≤100×100 Layers-row thumbnail box. */
    private fun scaleToLayerThumb(preview: Bitmap): Bitmap {
        val maxDim = maxOf(preview.width, preview.height).coerceAtLeast(1)
        if (maxDim <= LAYER_THUMB_MAX) {
            return preview.copy(Bitmap.Config.ARGB_8888, false)
        }
        val s = LAYER_THUMB_MAX.toFloat() / maxDim
        return Bitmap.createScaledBitmap(
            preview,
            (preview.width * s).roundToInt().coerceAtLeast(1),
            (preview.height * s).roundToInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Replaces the reversible effect stack with [snapshot], assembled by the
     * screen from the raw per-tool parameters. The snapshot is the source of
     * truth for the Layers UI and stack-aware undo/redo; parameters are kept so
     * a tool's remembered values are restored when the user returns to it.
     */
    fun setEffectStack(snapshot: List<StackEffect>) {
        val fingerprints = snapshot.associate { it.layerKey to it.layerKey }
        syncLayerState(snapshot, fingerprints)
    }

    /**
     * Flattens the whole effect stack into a single photo and marks it merged.
     * Renders the current edits via [renderCommittedBitmap], keeps the original for
     * hold-to-compare, and resets all controls to neutral so revisiting a tool
     * applies fresh over the merged result.
     */
    fun mergeLayers(bundle: EditorRenderBundle) {
        viewModelScope.launch {
            val stack = buildStackEffects(_uiState.value, bundle)
            val deforming = stack.filter { it.changesGeometry }
            val merged = runCatching {
                if (deforming.isNotEmpty()) {
                    // A merge that baked deforming/occluding effects can no longer
                    // ground geometry in the pristine photo: mark the shared
                    // context stale AND regenerate originalGeometryImage as an
                    // independent instance by folding every deforming effect over
                    // the pristine original (its resolution may differ from the
                    // original after a crop/rotation — always grounded in the
                    // initial full-size image). originalImage itself stays frozen.
                    geometryContext.markStale()
                    regeneratedGeometryImage = geometryTrackBase(deforming, scale = 1f)
                } else {
                    // No geometry-changing effect merged: the geometry base can
                    // stay a reference to the pristine original (the two match).
                    geometryContext.flatten()
                }
                renderCommittedBitmap(bundle)
            }.getOrNull()
            val mergedFp = "merged:${merged?.hashCode()}"
            if (_uiState.value.originalBitmap == null && merged == null) return@launch
            val mergedKey = MergedEffect().layerKey
            _uiState.update { it ->
                val mergedThumb: Bitmap = merged ?: it.originalBitmap ?: return@update it
                // Merging flattens the geometry list: the new stack starts with
                // the baked MergedEffect, whose own geometry-changing baselines
                // (crop/transform in the old stack) were folded INTO the
                // regenerated originalGeometryImage above, so beauty geometry
                // remains grounded in the original geometry image's fold chain.
                it.copy(
                    editedBitmap = merged ?: it.originalBitmap,
                    effectStack = listOf(MergedEffect(image = merged)),
                    hasMerged = true,
                    isLayersVisible = false,
                    layerThumbnails = mapOf(mergedKey to mergedThumb),
                    layerFingerprints = mapOf(mergedKey to mergedFp),
                    brightness = 0f,
                    contrast = 0f,
                    saturation = 0f,
                    sharpness = 0f,
                    exposure = 0f,
                    shadows = 0f,
                    highlights = 0f,
                    temperature = 0f,
                    tint = 0f,
                    vignette = 0f,
                    skinDenoise = 0f,
                    enhanceEnabled = false,
                    styleFilter = null,
                    subjectMask = null,
                    backgroundImage = null,
                    selectedBackgroundRes = null,
                    beauty = BeautyParams()
                )
            }
            invalidateFoldCache()
            clearLayerPreviews()
            refreshPreviewLook()
        }
    }

    /** Removes a single effect (and its remembered parameters) from the stack. */
    fun removeStackEffect(id: String) {
        val effect = _uiState.value.effectStack.firstOrNull { it.id == id }
        if (effect == null || effect is MergedEffect) return
        val remaining = _uiState.value.effectStack.filterNot { it.id == id }
        _uiState.update { it.copy(effectStack = remaining) }
        invalidateFoldCache()
        refreshPreviewLook()
    }

    /**
     * Jumps to the tool that created [effectId], restoring that effect's
     * remembered parameters so it can be re-edited. No-op for a merged layer.
     */
    fun selectStackEffect(effectId: String) {
        val state = _uiState.value
        val effect = state.effectStack.firstOrNull { it.id == effectId } ?: return
        if (effect is MergedEffect) return
        var next = state.copy(selectedTool = effect.tool, isLayersVisible = false)
        when (effect) {
            is codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect -> next =
                restoreAdjustment(next, effect.tool, effect.value)
            is codes.pepper.whimsicalart.feature.editor.domain.CropEffect -> next =
                next.copy(cropRect = effect.rect)
            is codes.pepper.whimsicalart.feature.editor.domain.TransformEffect -> next =
                next.copy(
                    rotation = effect.rotation,
                    flipHorizontal = effect.flipHorizontal,
                    flipVertical = effect.flipVertical
                )
            is codes.pepper.whimsicalart.feature.editor.domain.EnhanceEffect -> next =
                next.copy(enhanceEnabled = effect.enabled)
            is codes.pepper.whimsicalart.feature.editor.domain.StyleEffect -> next =
                next.copy(styleFilter = effect.filter)
            is codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect -> next =
                next.copy(
                    backgroundMode = effect.mode,
                    subjectMask = effect.subjectMask,
                    backgroundBlurRadius = effect.blurRadius,
                    backgroundShape = effect.shape,
                    backgroundImage = effect.backgroundImage,
                    selectedBackgroundRes = if (effect.backgroundImage != null && effect.mode == BackgroundMode.REPLACE) 0 else next.selectedBackgroundRes
                )
            else -> Unit
        }
        _uiState.value = next
        refreshPreviewLook()
    }

    private fun restoreAdjustment(
        state: EditorUiState,
        tool: EditTool,
        value: Float
    ): EditorUiState = when (tool) {
        EditTool.BRIGHTNESS -> state.copy(brightness = value)
        EditTool.CONTRAST -> state.copy(contrast = value)
        EditTool.SATURATION -> state.copy(saturation = value)
        EditTool.SHARPEN -> state.copy(sharpness = value)
        EditTool.EXPOSURE -> state.copy(exposure = value)
        EditTool.SHADOWS -> state.copy(shadows = value)
        EditTool.HIGHLIGHTS -> state.copy(highlights = value)
        EditTool.TEMPERATURE -> state.copy(temperature = value)
        EditTool.TINT -> state.copy(tint = value)
        EditTool.VIGNETTE -> state.copy(vignette = value)
        else -> state
    }

    /**
     * Renders the live-look copy of the source for the preview: the unsharp
     * sharpen (when set) plus the Auto-Enhance/HDR and learned-look style
     * passes (when enabled). The cheap color-matrix / overlay compositing that
     * the preview normally relies on stays on the composable side; this off
     * screen pass only captures the per-pixel effects that are NOT expressible
     * as a color filter, so Enhance and Style now reflect live on screen.
     *
     * At most ONE pass is in flight at a time: while a pass runs, later slider
     * changes only set a dirty flag and the running pass re-runs with the latest
     * value until the drag settles. Without this, a frenetic drag stacks many
     * concurrent full-image renders, and `decodeFromUri` (a blocking call that
     * coroutine cancellation cannot interrupt) re-decodes the whole photo per
     * tick - the memory spike OOMs the app. The decoded source is therefore
     * also cached so the expensive decode happens once per image.
     */
    private fun refreshPreviewLook() {
        val state = _uiState.value
        val needsPass = state.sharpness != 0f ||
            state.skinDenoise != 0f ||
            state.enhanceEnabled ||
            state.styleFilter != null ||
            state.subjectMask != null
        if (!needsPass) {
            sharpnessJob?.cancel()
            sharpnessPreviewDirty = false
            if (state.sharpenedPreview != null) {
                _uiState.update { it.copy(sharpenedPreview = null) }
            }
            return
        }
        val uri = state.imageUri ?: return
        if (sharpnessPreviewRunning) {
            sharpnessPreviewDirty = true
            return
        }
        sharpnessPreviewRunning = true
        sharpnessJob = viewModelScope.launch {
            try {
                while (isActive) {
                    val current = _uiState.value
                    val preview = withContext(Dispatchers.Default) {
                        val source = sharpnessDecodedSource ?: decodeFromUri(uri)?.also {
                            sharpnessDecodedSource = it
                        }
                        source?.let {
                            var look = it
                            if (current.sharpness != 0f) {
                                look = BitmapRenderer.sharpenPreview(look, current.sharpness)
                            }
                            if (current.skinDenoise != 0f) {
                                val softness = (1f - current.skinDenoise).coerceIn(0f, 1f)
                                look = recycleAndApply(look, SkinDenoiseProcessor.denoise(look, softness))
                            }
                            if (current.enhanceEnabled) {
                                look = recycleAndApply(
                                    look, EnhanceProcessor.enhance(look, EnhanceSettings())
                                )
                            }
                            val style = current.styleFilter
                            if (style != null) {
                                look = recycleAndApply(look, StyleFilterProcessor.apply(look, style))
                            }
                            val mask = current.subjectMask
                            if (mask != null) {
                                when (current.backgroundMode) {
                                    BackgroundMode.BLUR -> {
                                        val radiusPx = (current.backgroundBlurRadius * look.width)
                                            .coerceAtLeast(1f)
                                        look = recycleAndApply(
                                            look,
                                            BokehProcessor().applyBackgroundBlur(
                                                look, mask, radiusPx, current.backgroundShape
                                            )
                                        )
                                    }
                                    BackgroundMode.REPLACE -> {
                                        val image = current.backgroundImage
                                        if (image != null) {
                                            look = recycleAndApply(
                                                look, BackgroundReplacer.composite(look, mask, image)
                                            )
                                        }
                                    }
                                }
                            }
                            look
                        }
                    }
                    if (!isActive) break
                    if (preview != null) {
                        _uiState.update { it.copy(sharpenedPreview = preview) }
                    }
                    if (!sharpnessPreviewDirty) break
                    sharpnessPreviewDirty = false
                }
            } finally {
                sharpnessPreviewRunning = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharpnessJob?.cancel()
        // The cached source is a private decode owned by this ViewModel; the
        // in-state preview bitmaps are handed to the screen and not recycled here.
        sharpnessDecodedSource?.recycle()
        sharpnessDecodedSource = null
        // Downscaled originals / scaled artifacts are private to this ViewModel.
        previewOriginalScaleCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
        previewOriginalScaleCache.clear()
        scaledArtifactCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
        scaledArtifactCache.clear()
        // Recycle fold cache intermediates held by this ViewModel.
        invalidateFoldCache(clearOriginal = true)
        compositeJob?.cancel()
        fullFoldJob?.cancel()
        // compositePreview is NOT recycled here (shared with the on-screen Image
        // via asImageBitmap, which would crash on a recycled bitmap during the
        // final frame); it is GC'd alongside the ViewModel, like sharpenedPreview.
        if (::segmenter.isInitialized) segmenter.close()
    }

    /**
     * Continuously renders the committed composite into [EditorUiState.compositePreview]
     * — the full effect stack folded over the pristine photo, with geometric base
     * steps (transform/crop/frame) baked into the pixels. The screen calls this
     * whenever committed-relevant state changes (the bundle it owns holds the
     * brush/sticker/text/background data the fold consumes); the render itself
     * runs on a background thread, is debounced + cancel-and-restart so a fast
     * slider drag doesn't stack renders, and reuses the incremental 3-image fold
     * cache (fast path when the stack is unchanged). The published bitmap is an
     * INDEPENDENT copy (never the cache-owned `lastImage`), so the screen owns it
     * and it is never recycled out from under the composable mid-frame.
     *
     * [excludeCurrentTool] drops the currently-selected tool's own layer(s) from
     * the fold (folding only the effects BEFORE it). Source-space placement tools
     * (brush / crop / sticker / text) draw their live overlay themselves, so while
     * such a tool is edited the composite base must be the fold of the preceding
     * effects — not the pristine image — with the tool's own layer provided as the
     * interactive overlay.
     *
     * [visibleWidthPx] / [visibleHeightPx] / [zoom] let the screen report the
     * viewing footprint; they drive the preview-resolution computation
     * (docs/effect_stack.md → Choosing preview resolution). The render always
     * starts with a PREVIEW-resolution fold (a small fraction of the source —
     * fast, non-blocking); the full-size composite is computed only when zoomed
     * past 100% (a debounced, cancellable background fold) or for a full-res
     * result, and — once fresh — keeps being shown even if zoom later drops
     * below 100%; the preview falls back to the small image only when the
     * full-size result becomes stale. Callers are free to pass defaults and let
     * the VM use its stored viewport/zoom state.
     */
    fun refreshCompositePreview(
        bundle: EditorRenderBundle,
        includeTail: Boolean = true,
        excludeCurrentTool: Boolean = false,
        visibleWidthPx: Int = viewportWidthPx,
        visibleHeightPx: Int = viewportHeightPx,
        zoom: Float = viewZoom
    ) {
        viewportWidthPx = visibleWidthPx.coerceAtLeast(1)
        viewportHeightPx = visibleHeightPx.coerceAtLeast(1)
        viewZoom = zoom.coerceIn(0.5f, 5f)
        if (compositeRunning) {
            compositeDirty = true
            return
        }
        compositeRunning = true
        compositeJob = viewModelScope.launch {
            try {
                while (isActive) {
                    val dims = sourceDims() ?: break
                    val scale = previewScaleFor(dims.first, dims.second)
                    val rendered = runCatching {
                        withContext(Dispatchers.Default) {
                            renderCommittedBitmap(bundle, includeTail, excludeCurrentTool, scale)
                        }
                    }.getOrNull()
                    if (!isActive) break
                    if (rendered != null) {
                        // NOTE: the previous compositePreview is NOT recycled here.
                        // `asImageBitmap()` shares the underlying Bitmap with the
                        // on-screen Image; recycling a frame the composable may
                        // still be drawing would crash. Old frames are GC'd
                        // (matching the existing sharpenedPreview pattern).
                        _uiState.update {
                            it.copy(compositePreview = displayBitmap(rendered, bundle, includeTail, excludeCurrentTool))
                        }
                    }
                    if (!compositeDirty) break
                    compositeDirty = false
                }
            } finally {
                compositeRunning = false
            }
        }
        // Past 100% zoom: the preview stand-in was just shown; schedule the
        // full-size fold (debounced + cancel-and-restart, so a fast pinch or
        // slider drag coalesces into one fresh fold) unless a fresh full-size
        // composite is already cached for these exact controls.
        if (viewZoom > 1f && !isFullCompositeFresh(bundle, includeTail, excludeCurrentTool)) {
            requestFullFold(bundle, includeTail, excludeCurrentTool)
        }
    }

    /** The effect list the committed fold actually folds (tool excluded if asked). */
    private fun committedEffects(
        bundle: EditorRenderBundle,
        excludeCurrentTool: Boolean
    ): List<StackEffect> {
        val source = buildStackEffects(_uiState.value, bundle)
        return if (excludeCurrentTool) {
            effectsBeforeTool(source, _uiState.value.selectedTool)
        } else {
            source
        }
    }

    /**
     * The list the fold runs over for the current tool/tail state: everything
     * when the tail is included (eye ON), otherwise up to and including the
     * current tool's first layer (eye OFF drops the tail).
     */
    private fun foldedEffects(
        bundle: EditorRenderBundle,
        includeTail: Boolean,
        excludeCurrentTool: Boolean
    ): List<StackEffect> {
        val effects = committedEffects(bundle, excludeCurrentTool)
        return if (includeTail) {
            effects
        } else {
            effects.take((currentToolIndex(effects) + 1).coerceIn(1, effects.size))
        }
    }

    /** The fold cache key for a given effect list + resolution bucket. */
    private fun foldKey(effects: List<StackEffect>, scale: Float): String =
        "${quantizedScale(scale)}:" + effects.joinToString("|") { stackEffectFingerprint(it) }

    /** The key the full-size (scale 1) composite cache holds for these controls. */
    private fun fullSizeKey(bundle: EditorRenderBundle, excludeCurrentTool: Boolean): String =
        foldKey(foldedEffects(bundle, includeTail = true, excludeCurrentTool), 1f)

    /**
     * A fresh full-size composite is cached for the exact current controls:
     * only when the tail is shown (eye OFF folds just the prefix — displaying
     * the last full-stack image would leak the hidden tail) and the stored key
     * matches.
     */
    private fun isFullCompositeFresh(
        bundle: EditorRenderBundle,
        includeTail: Boolean,
        excludeCurrentTool: Boolean
    ): Boolean {
        if (!includeTail) return false
        val last = fullCache.lastImage
        if (last == null || last.isRecycled) return false
        return fullCache.stackSignature == fullSizeKey(bundle, excludeCurrentTool)
    }

    /**
     * The composite to show right now: a fresh full-size fold when one is cached
     * for the current controls (once computed it stays in use even below 100%
     * zoom — it is strictly sharper than the preview), otherwise the small
     * preview-resolution fold just produced. Always an INDEPENDENT copy.
     */
    private fun displayBitmap(
        preview: Bitmap,
        bundle: EditorRenderBundle,
        includeTail: Boolean,
        excludeCurrentTool: Boolean
    ): Bitmap {
        val last = fullCache.lastImage
        if (includeTail && last != null && !last.isRecycled &&
            fullCache.stackSignature == fullSizeKey(bundle, excludeCurrentTool)
        ) {
            return last.copy(Bitmap.Config.ARGB_8888, true)
        }
        return preview.copy(Bitmap.Config.ARGB_8888, true)
    }

    /**
     * Launches the debounced full-size background fold (docs/effect_stack.md →
     * Performance / preview cache): starts ~500 ms after the last change, is
     * cancelled + re-issued by the next [refreshCompositePreview] while in
     * flight, and swaps the exact full-size composite in when ready. The small
     * preview-resolution image stays on screen until then.
     */
    private fun requestFullFold(
        bundle: EditorRenderBundle,
        includeTail: Boolean,
        excludeCurrentTool: Boolean
    ) {
        fullFoldJob?.cancel()
        fullFoldJob = viewModelScope.launch {
            delay(FULL_FOLD_DEBOUNCE_MS)
            if (!isActive) return@launch
            val full = runCatching {
                withContext(Dispatchers.Default) {
                    renderCommittedBitmap(bundle, includeTail, excludeCurrentTool, 1f)
                }
            }.getOrNull()
            if (isActive && full != null) {
                _uiState.update { it.copy(compositePreview = full) }
            }
        }
    }

    /** Clears the composite preview (falls back to the raw live source).
     *  Not recycled here for the same reason as the refresh path (shared with the
     *  on-screen Image). */
    fun clearCompositePreview() {
        if (_uiState.value.compositePreview == null) return
        _uiState.update { it.copy(compositePreview = null) }
    }

    private fun saveToHistory() {
        val state = _uiState.value
        val doc = StackDocument(
            effectStack = state.effectStack.toList(),
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
            skinDenoise = state.skinDenoise,
            rotation = state.rotation,
            flipHorizontal = state.flipHorizontal,
            flipVertical = state.flipVertical,
            cropRect = state.cropRect,
            selectedFrameId = state.selectedFrameId,
            enhanceEnabled = state.enhanceEnabled,
            styleFilter = state.styleFilter,
            backgroundMode = state.backgroundMode,
            backgroundBlurRadius = state.backgroundBlurRadius,
            backgroundShape = state.backgroundShape,
            subjectMask = state.subjectMask,
            backgroundImage = state.backgroundImage,
            selectedBackgroundRes = state.selectedBackgroundRes,
            selectedFilterId = state.selectedFilterId,
            enabledLensFilterIds = state.enabledLensFilterIds,
            beauty = state.beauty
        )

        val newHistory = state.history.take(state.historyIndex + 1) + doc
        _uiState.value = state.copy(
            history = newHistory,
            historyIndex = newHistory.size - 1
        )
    }

    /**
     * Copies the beauty sub-tool parameters from a [StackDocument] back onto a
     * state, so undo/redo restores both the effect stack and its deriving params.
     */
    private fun EditorUiState.withDocBeauty(doc: StackDocument): EditorUiState = copy(
        beauty = doc.beauty
    )

    fun undo() {
        val state = _uiState.value
        if (state.historyIndex > 0) {
            val prev = state.history[state.historyIndex - 1]
            _uiState.value = state.copy(
                effectStack = prev.effectStack,
                brightness = prev.brightness,
                contrast = prev.contrast,
                saturation = prev.saturation,
                sharpness = prev.sharpness,
                exposure = prev.exposure,
                shadows = prev.shadows,
                highlights = prev.highlights,
                temperature = prev.temperature,
                tint = prev.tint,
                vignette = prev.vignette,
                skinDenoise = prev.skinDenoise,
                rotation = prev.rotation,
                flipHorizontal = prev.flipHorizontal,
                flipVertical = prev.flipVertical,
                cropRect = prev.cropRect,
                selectedFrameId = prev.selectedFrameId,
                enhanceEnabled = prev.enhanceEnabled,
                styleFilter = prev.styleFilter,
                backgroundMode = prev.backgroundMode,
                backgroundBlurRadius = prev.backgroundBlurRadius,
                backgroundShape = prev.backgroundShape,
                subjectMask = prev.subjectMask,
                backgroundImage = prev.backgroundImage,
                selectedBackgroundRes = prev.selectedBackgroundRes,
                selectedFilterId = prev.selectedFilterId,
                enabledLensFilterIds = prev.enabledLensFilterIds,
                historyIndex = state.historyIndex - 1
            ).withDocBeauty(prev)
        }
        invalidateFoldCache()
        refreshPreviewLook()
    }

    fun redo() {
        val state = _uiState.value
        if (state.historyIndex < state.history.size - 1) {
            val next = state.history[state.historyIndex + 1]
            _uiState.value = state.copy(
                effectStack = next.effectStack,
                brightness = next.brightness,
                contrast = next.contrast,
                saturation = next.saturation,
                sharpness = next.sharpness,
                exposure = next.exposure,
                shadows = next.shadows,
                highlights = next.highlights,
                temperature = next.temperature,
                tint = next.tint,
                vignette = next.vignette,
                skinDenoise = next.skinDenoise,
                rotation = next.rotation,
                flipHorizontal = next.flipHorizontal,
                flipVertical = next.flipVertical,
                cropRect = next.cropRect,
                selectedFrameId = next.selectedFrameId,
                enhanceEnabled = next.enhanceEnabled,
                styleFilter = next.styleFilter,
                backgroundMode = next.backgroundMode,
                backgroundBlurRadius = next.backgroundBlurRadius,
                backgroundShape = next.backgroundShape,
                subjectMask = next.subjectMask,
                backgroundImage = next.backgroundImage,
                selectedBackgroundRes = next.selectedBackgroundRes,
                selectedFilterId = next.selectedFilterId,
                enabledLensFilterIds = next.enabledLensFilterIds,
                historyIndex = state.historyIndex + 1
            ).withDocBeauty(next)
        }
        invalidateFoldCache()
        refreshPreviewLook()
    }

    fun saveImage(config: SaveConfig, bundle: EditorRenderBundle, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val bitmap = renderCommittedBitmap(bundle)
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
            val bitmap = renderCommittedBitmap(bundle)
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    imageSaver.shareImage(bitmap, config, context)
                }
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    /**
     * The COMMITTED (save/merge/share) render path — a single left-to-right fold
     * using the GEOMETRY TRACK ([renderStackWithBeautyGeometry] with
     * `scale = 1f`): every beauty layer's geometry is bound upfront by folding
     * only the geometry-changing effects up to the layer over the ORIGINAL
     * geometry image ([originalGeometryImage]), never the running colour image
     * (docs/effect_stack.md → Original geometry image and original geometry).
     * Because this is the FULL-SIZE fold, binding resolves the full-size geometry
     * inline — the block-on-commit point (a save/merge/share awaits the full
     * geometry before rendering, while previews resolved the preview-resolution
     * geometry ahead of it). Each effect renders with the geometry it was bound
     * (its own parameter), so the fold and editor never pick geometry.
     */
    private suspend fun renderCommittedBitmap(
        bundle: EditorRenderBundle,
        includeTail: Boolean = true,
        excludeCurrentTool: Boolean = false,
        scale: Float = 1f
    ): Bitmap? {
        val (base, sourceEffects) = buildBaseAndEffects(bundle, scale) ?: return null
        // Source-space placement tools draw their own live layer (strokes / crop
        // grid / sticker / text); their composite base is the fold of the effects
        // BEFORE the tool, so the overlay paints over the composite of every prior
        // edit rather than the pristine image.
        val effects = if (excludeCurrentTool) {
            effectsBeforeTool(sourceEffects, _uiState.value.selectedTool)
        } else {
            sourceEffects
        }
        if (effects.isEmpty()) {
            // No effects precede the current tool — the base itself is the preview.
            return base.copy(Bitmap.Config.ARGB_8888, true)
        }
        return renderIncremental(
            context, base, effects,
            includeTail = includeTail, scale = scale
        )
    }

    /**
     * The ordered effect list folded over the base while [selectedTool] is being
     * edited in a "show only the preceding effects" mode: everything BEFORE the
     * tool's first stack entry. If the tool is not in the stack yet (a fresh
     * session) the whole stack is returned — there is nothing of this tool to
     * exclude, and its live data is supplied by the tool's own overlay.
     */
    internal fun effectsBeforeTool(
        effects: List<StackEffect>,
        selectedTool: EditTool?
    ): List<StackEffect> {
        if (selectedTool == null) return effects
        val idx = effects.indexOfFirst { it.tool == selectedTool }
        return if (idx == -1) effects else effects.take(idx)
    }

    /**
     * Renders [effects] over [base] using the incremental 3-image fold cache
     * (originalImage / currentImage / lastImage — see the field docs and
     * docs/effect_stack.md → Behaviour rules), maintained at the given [scale]
     * resolution (preview vs full — see the fold cache field docs).
     *
     * - **Prefix cache**: `currentImage` holds the composite of every effect
     *   BEFORE the currently-edited tool (the tool's [resource] input). It is
     *   keyed by the prefix signature; if the prefix is unchanged it is reused
     *   instead of re-folding from [base] on every render.
     * - **Full-stack cache**: `lastImage` holds the composite of the WHOLE stack
     *   (the preview result). It is keyed by the full-stack signature; if the
     *   whole stack is unchanged it is reused outright (the tail reapply would
     *   produce the identical bitmap).
     *
     * Only when one of the two signatures differs is the corresponding fold
     * re-run, starting from the cached `currentImage` (tail reapply) or, at most,
     * re-folding the prefix/whole stack from [base]. The output is always
     * byte-identical to a full from-base fold at the SAME [scale] — the cache
     * only avoids repeating unchanged fold work, and never changes the produced
     * image.
     */
    private suspend fun renderIncremental(
        context: Context,
        base: Bitmap,
        effects: List<StackEffect>,
        toolIndex: Int = currentToolIndex(effects),
        includeTail: Boolean = true,
        scale: Float = 1f
    ): Bitmap {
        val cache = if (scale >= 1f) fullCache else previewCache
        // Tail folding (eye OFF on a Category A tool): show only the effects up
        // to and including the currently-edited tool, dropping everything after
        // it (the "tail"). We fold `effects.take(idx + 1)` instead of the whole
        // stack and bypass the full-stack cache (which holds the full composite).
        if (!includeTail) {
            val idx = (toolIndex + 1).coerceIn(1, effects.size)
            val trimmed = effects.take(idx)
            val result = renderStackWithBeautyGeometry(
                context, base, trimmed,
                adaptBackgroundArtifacts = scale < 1f,
                scale = scale
            )
            val out = result.copy(Bitmap.Config.ARGB_8888, true)
            if (result !== base && !result.isRecycled) result.recycle()
            return out
        }
        val idx = toolIndex.coerceIn(0, effects.size)
        val prefix = effects.take(idx)
        // The cache is per-resolution: its signatures are keyed by the scale
        // bucket so a zoom that crosses a preview-resolution bucket re-folds at
        // the new size instead of reusing a stale-resolution composite.
        val bucketKey = quantizedScale(scale)
        val fullSig = "$bucketKey:" + effects.joinToString("|") { stackEffectFingerprint(it) }

        // Fast path: the ENTIRE stack is unchanged since the last render → the
        // cached lastImage is already the exact full composite. Return an
        // INDEPENDENT copy so the caller owns a bitmap that is never recycled
        // out from under it when the cache is invalidated (`lastImage` is a
        // cache, not a caller-owned value).
        if (fullSig == cache.stackSignature && cache.lastImage != null &&
            !cache.lastImage!!.isRecycled
        ) {
            return cache.lastImage!!.copy(Bitmap.Config.ARGB_8888, true)
        }

        // Full stack changed. Reuse the prefix cache if the prefix (effects
        // before the edited tool) is unchanged, so we only re-fold the tail.
        val prefixSig = "$bucketKey:" + prefix.joinToString("|") { stackEffectFingerprint(it) }
        if (prefixSig != cache.prefixSignature || cache.currentImage == null ||
            cache.currentImage!!.isRecycled
        ) {
            val rebuilt = renderStackWithBeautyGeometry(
                context, base, prefix,
                adaptBackgroundArtifacts = scale < 1f,
                scale = scale
            )
            cache.currentImage?.takeUnless(Bitmap::isRecycled)?.recycle()
            cache.currentImage = rebuilt
            cache.prefixSignature = prefixSig
        }

        val result = renderStackWithBeautyGeometry(
            context, base, effects,
            resumeAt = idx,
            resumeBitmap = cache.currentImage,
            adaptBackgroundArtifacts = scale < 1f,
            scale = scale
        )
        // Cache an independent copy as the new `lastImage` (for the future fast
        // path, which also returns a copy) and return an independent copy to the
        // caller too — so no caller ever receives a cache-owned bitmap that
        // [invalidateFoldCache] might later recycle (e.g. `mergeLayers`). This
        // degrades only the rare full-size/committed render by one copy; the
        // per-slider preview loop never takes this path.
        cache.lastImage?.takeUnless(Bitmap::isRecycled)?.recycle()
        cache.lastImage = result.copy(Bitmap.Config.ARGB_8888, true)
        cache.stackSignature = fullSig
        val out = result.copy(Bitmap.Config.ARGB_8888, true)
        // `result` is a transient fresh fold output UNLESS it aliases the
        // caller/cache-owned start bitmaps in the all-noop-tail degenerate case;
        // recycle only the fresh intermediate we are not handing out.
        if (result !== base && result !== cache.currentImage && !result.isRecycled) {
            result.recycle()
        }
        return out
    }

    /**
     * Index into [effects] of the currently-selected tool's FIRST effect; if the
     * tool has no effect in the stack yet (e.g. it was just opened), returns
     * [effects.size] so it is treated as a new effect appended at the end.
     */
    private fun currentToolIndex(effects: List<StackEffect>): Int {
        val tool = _uiState.value.selectedTool ?: return effects.size
        val first = effects.indexOfFirst { it.tool == tool }
        return if (first == -1) effects.size else first
    }

    /** The pristine full-size source the geometry track is grounded in. */
    private val pristineOriginal: Bitmap?
        get() = cachedOriginalDecode() ?: _uiState.value.originalBitmap

    /**
     * The bitmap beauty geometry is traced from (docs/effect_stack.md → Original
     * geometry image and original geometry): a **reference** to the pristine
     * original (shared memory, immutable) until a deforming merge regenerates it
     * into an **independent instance** ([regeneratedGeometryImage]). Its
     * resolution starts identical to the original and may differ after a merge
     * (a crop shrinks it, a rotation-enlargement grows it) — always grounded in
     * the initial full-size image.
     */
    private val originalGeometryImage: Bitmap?
        get() = regeneratedGeometryImage ?: pristineOriginal

    /**
     * The [BeautyGeometryBaseSource] callback bound to [effects] (see
     * docs/effect_stack.md → Original geometry image and original geometry): the
     * resolution asks it for the base at a beauty layer's proposed position and
     * it folds only the geometry-changing effects of the gap up to that position
     * over the original geometry image — or returns the original geometry image
     * itself when the gap is empty (no fold). The editor stays the sole owner of
     * the deformation fold; the resolution only consumes the returned base.
     */
    private fun geometryBaseSourceFor(effects: List<StackEffect>) =
        BeautyGeometryBaseSource { upToIndex, s ->
            geometryTrackBase(effects.take(upToIndex).filter { it.changesGeometry }, s)
        }

    /**
     * The geometry-track segment-0 base at [scale]: the original geometry image
     * itself (scale ≥ 1) or a preview-resolution downscale of it (memoized via
     * [scaledArtifact]). Its dims align with the colour fold base at the same
     * scale, so the resolved geometry's coordinates match the pixels the beauty
     * layer renders over. Never stored in [geometryTrackCache] (it is owned by
     * the decode / [scaledArtifactCache]); folded segments descend from it.
     */
    private fun geometryTrackSegment0Base(scale: Float): Bitmap? {
        val original = originalGeometryImage ?: return null
        if (scale >= 1f) return original
        val w = (original.width * scale).roundToInt().coerceAtLeast(1)
        val h = (original.height * scale).roundToInt().coerceAtLeast(1)
        return scaledArtifact(original, w, h)
    }

    /**
     * Folds the geometry-changing effects of [geomEffects] (stack order) over the
     * original geometry image and returns the deformed base INSTANCE for that
     * segment, memoized by its cumulative signature + resolution bucket. An
     * unchanged segment is served the SAME instance on every request, so the
     * geometry context's identity guard reuses the resolved geometry (ML runs
     * once per segment × resolution); a changed deforming effect (new signature)
     * or resolution switch (new bucket) yields a new instance and regenerates.
     * A deforming reshape layer folds by rendering it with the geometry it
     * resolved against the preceding segment base. Only FOLDED bases are owned
     * here; the cache is bounded and recycled on overflow.
     */
    private suspend fun geometryTrackBase(
        geomEffects: List<StackEffect>,
        scale: Float
    ): Bitmap? {
        val bucket = quantizedScale(scale)
        val key = "$bucket:" + geomEffects.joinToString("|") { stackEffectFingerprint(it) }
        geometryTrackCache[key]?.takeUnless(Bitmap::isRecycled)?.let { return it }
        if (geomEffects.isEmpty()) return geometryTrackSegment0Base(scale)
        val prev = geometryTrackBase(geomEffects.dropLast(1), scale) ?: return null
        val last = geomEffects.last()
        val renderable = if (last is BeautyStackEffect) {
            last.withGeometry(geometryContext.geometryFor(prev)).withProcessor(beautyProcessor)
        } else {
            last
        }
        val base = renderable.render(prev, context)
        if (geometryTrackCache.size >= GEOMETRY_TRACK_CACHE_MAX) {
            geometryTrackCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
            geometryTrackCache.clear()
        }
        geometryTrackCache[key] = base
        return base
    }

    /**
     * The geometry-track resolution pass (docs/effect_stack.md → Original
     * geometry image and original geometry): binds EVERY beauty layer's geometry
     * from the geometry track — the stack folded up to the layer applying ONLY
     * the geometry-changing effects over the original geometry image — never from
     * the colour running image (colour/adjustment work does not steer ML
     * landmarks). Consecutive beauty layers inside one segment share the same
     * base instance, so the shared context reuses the resolved geometry (ML once
     * per segment × resolution); the two-stage preview-then-full-size flow falls
     * out of the resolution buckets — a preview fold resolves against the
     * preview-resolution base, a full/committed fold against the full-size base
     * (and the committed render awaits it: block-on-commit). The editor stays
     * geometry-agnostic — it hands bases through the [geometryBaseSourceFor]
     * callback and lets the context decide reuse vs resolve.
     */
    private suspend fun bindBeautyGeometries(
        effects: List<StackEffect>,
        scale: Float
    ): List<StackEffect> {
        if (effects.none { it is BeautyStackEffect }) return effects
        val source = geometryBaseSourceFor(effects)
        val out = ArrayList<StackEffect>(effects.size)
        for (i in effects.indices) {
            val e = effects[i]
            if (e is BeautyStackEffect) {
                val base = source.geometryBase(i, scale) ?: return effects
                out += e.withGeometry(geometryContext.geometryFor(base))
            } else {
                out += e
            }
        }
        return out
    }

    /**
     * Folds the effect list left-to-right with each beauty layer's geometry as
     * bound by the GEOMETRY TRACK ([bindBeautyGeometries]; see
     * docs/effect_stack.md → Original geometry image and original geometry):
     * geometry is resolved against the stack folded up to the layer applying only
     * the geometry-changing effects over the original geometry image — not
     * against the colour running image. Geometry is NOT resolved in this fold:
     * it is a parameter each layer already carries, so the fold and the editor
     * never decide/pick geometry. Shared by the committed save/merge/share fold
     * and the Layers-window thumbnails so a post-deform beauty layer's thumbnail
     * reflects the same per-position result.
     */
    suspend fun renderStackWithBeautyGeometry(
        context: Context,
        input: Bitmap,
        effects: List<StackEffect>,
        resumeAt: Int = 0,
        resumeBitmap: Bitmap? = null,
        adaptBackgroundArtifacts: Boolean = false,
        scale: Float = 1f
    ): Bitmap {
        // The geometry track binds every beauty layer's geometry before the
        // colour fold (no-op for lists without beauty layers).
        val resolved = bindBeautyGeometries(effects, scale)
        // Tail-only (incremental) reapply: when [resumeBitmap] is supplied,
        // [input] is ignored as the fold start and [resumeBitmap] (the cached
        // composite of effects[0..resumeAt)) is used as the running image, and
        // only effects[resumeAt..] are folded — the tail. This is byte-identical
        // to folding everything from [input]: geometry was bound over the WHOLE
        // list (the geometry track accumulates every deformation regardless of
        // the colour resume point), so tail beauty layers render with exactly the
        // geometry a full fold would give them. Without [resumeBitmap] the whole
        // list is folded from [input] (resumeAt is ignored). The current
        // fold-start bitmap ([input] or [resumeBitmap]) is caller/cache-owned and
        // is never recycled by this fold.
        //
        // [adaptBackgroundArtifacts] (a preview-resolution fold): pixel artifacts
        // that are emitted at FULL size — the BackgroundEffect's subject mask and
        // replacement image — are scaled down to the running image's footprint
        // before the effect renders, so the preview fold stays cheap while the
        // full-size save/merge/share fold uses the real artifacts unchanged. All
        // other effects are resolution-independent (normalized coordinates), and
        // the beauty geometry is resolved per actual base dimensions, so a
        // preview fold is the faithful lower-resolution version of the full fold.
        val startIndex = if (resumeBitmap == null) 0 else resumeAt.coerceIn(0, effects.size)
        val start = if (startIndex == 0) input else resumeBitmap!!
        var current = start
        for (i in startIndex until effects.size) {
            val effect = resolved[i]
            val oriented = if (adaptBackgroundArtifacts && effect is BackgroundEffect) {
                adaptBackgroundTo(current, effect)
            } else {
                effect
            }
            val toRender = if (oriented is BeautyStackEffect) {
                // Geometry is already bound by the geometry track; only the single
                // injected BeautyProcessor is handed to the effect so its render
                // never constructs its own processor.
                oriented.withProcessor(beautyProcessor)
            } else {
                oriented
            }
            val next = toRender.render(current, context)
            if (next !== current && current !== start && !current.isRecycled) current.recycle()
            current = next
        }
        return current
    }

    /**
     * Returns [effect] with its full-size pixel artifacts downscaled to
     * [current]'s footprint for a preview-resolution fold. The subject mask is
     * binary (alphas 0/255), so a plain bilinear downscale is exact for the
     * hard edge; the replacement image is a photograph, so a bilinear downscale
     * is the same quality the base decode got. When the artifacts already match
     * the footprint, [effect] is returned unchanged.
     */
    private fun adaptBackgroundTo(current: Bitmap, effect: BackgroundEffect): BackgroundEffect {
        val mask = effect.subjectMask
        val bg = effect.backgroundImage
        val needMask = mask?.takeIf {
            it.width != current.width || it.height != current.height
        }
        val needBg = bg?.takeIf {
            it.width != current.width || it.height != current.height
        }
        if (needMask == null && needBg == null) return effect
        return effect.copy(
            subjectMask = if (needMask != null) scaledArtifact(mask, current.width, current.height) else mask,
            backgroundImage = if (needBg != null) scaledArtifact(bg, current.width, current.height) else bg
        )
    }

    /**
     * Returns [source] scaled to the target footprint, memoized in a small cache
     * (cleared on photo change). [source] is always a full-size artifact owned
     * elsewhere, so the result is a NEW bitmap held by the cache and never
     * recycled while the photo lives. [source] itself is returned unchanged when
     * it already matches.
     */
    private fun scaledArtifact(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (source.isRecycled) return null
        if (source.width == targetWidth && source.height == targetHeight) return source
        val key = "${source.hashCode()}:${targetWidth}x$targetHeight"
        scaledArtifactCache[key]?.let { if (!it.isRecycled) return it }
        if (scaledArtifactCache.size > 12) {
            scaledArtifactCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
            scaledArtifactCache.clear()
        }
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        scaledArtifactCache[key] = scaled
        return scaled
    }

    /**
     * Invalidates every cached fold intermediate (`currentImage` / `lastImage` /
     * prefix & stack signatures, at both preview and full resolution) so the next
     * render re-folds — but keeps the cached original decode alive unless
     * [clearOriginal] (used only when the photo itself changes, which also drops
     * every preview-resolution downscale and scaled artifact). Called on
     * stack-structure mutations (reorder, removal, merge, undo/redo, beauty list
     * change, tool switch) where any cached prefix or full composite is no longer
     * valid.
     */
    private fun invalidateFoldCache(clearOriginal: Boolean = false) {
        fullCache.clear()
        previewCache.clear()
        if (clearOriginal) {
            foldCacheOriginal?.takeUnless(Bitmap::isRecycled)?.recycle()
            foldCacheOriginal = null
            previewOriginalScaleCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
            previewOriginalScaleCache.clear()
            scaledArtifactCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
            scaledArtifactCache.clear()
            cachedSourceWidth = 0
            cachedSourceHeight = 0
            // The geometry track is grounded in the old photo: a fresh photo gets
            // a fresh reference-base and a cleared segment cache. The regenerated
            // base (if any) is an independent instance and is recycled here.
            geometryTrackCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
            geometryTrackCache.clear()
            regeneratedGeometryImage?.takeUnless(Bitmap::isRecycled)?.recycle()
            regeneratedGeometryImage = null
        }
    }

    /**
     * Resolves the [base] bitmap and ordered [effects] list for a render pass at
     * the requested [scale]. The fold always starts from the pristine decode
     * ([cachedBaseDecode]) downscaled to preview resolution when [scale] < 1;
     * every effect — including each per-sub-tool beauty layer — sits in the
     * user-ordered stack and consumes the fold before it. Only a merge flattens
     * the stack, after which the flattened result becomes the working base (and
     * is downscaled for a preview fold the same way).
     */
    private fun buildBaseAndEffects(
        bundle: EditorRenderBundle,
        scale: Float = 1f
    ): Pair<Bitmap, List<StackEffect>>? {
        val state = _uiState.value
        // After Merge Layers the flattened result (editedBitmap) becomes the
        // working base: folding any further effect on top of the MERGED image
        // (not the pristine photo) is what keeps the preview and a later
        // committed result consistent with what the user merged. The pristine
        // photo is still kept only for hold-to-compare. The stack's MergedEffect
        // entry is dropped by orderedMerge below, so using editedBitmap as the
        // base here is what carries the flattened content forward.
        val mergedBase = state.editedBitmap?.takeIf { state.hasMerged && !it.isRecycled }
        if (mergedBase != null) {
            if (scale >= 1f) return mergedBase to buildStackEffects(state, bundle)
            val w = (mergedBase.width * scale).roundToInt().coerceAtLeast(1)
            val h = (mergedBase.height * scale).roundToInt().coerceAtLeast(1)
            val base = scaledArtifact(mergedBase, w, h) ?: return null
            return base to buildStackEffects(state, bundle)
        }
        val base = cachedBaseDecode(scale) ?: return null
        return base to buildStackEffects(state, bundle)
    }

    /**
     * Returns the pristine decoded source — cached across renders (so the
     * expensive blocking URI decode happens once per photo) and always kept
     * (never recycled) as the ultimate base of every fold and hold-to-compare,
     * exactly like `originalImage` in the effect_stack.md model. Recycled only
     * when the photo itself changes ([invalidateFoldCache(clearOriginal = true)]).
     */
    private fun cachedOriginalDecode(): Bitmap? {
        foldCacheOriginal?.let {
            if (!it.isRecycled) return it
            foldCacheOriginal = null
        }
        val decoded = decodeFromUri(_uiState.value.imageUri) ?: return null
        foldCacheOriginal = decoded
        cachedSourceWidth = decoded.width
        cachedSourceHeight = decoded.height
        return decoded
    }

    /**
     * Returns the fold base at the requested [scale]: the pristine full-size
     * decode at scale ≥ 1 (in [foldCacheOriginal]); otherwise a preview
     * resolution bilinear downscale of it, memoized per scale bucket (permille)
     * so a steady zoom keeps reusing the same preview base. The preview base is
     * always a fresh smaller bitmap derived from the full-size original — never
     * an alias of it — so recycling one cache never touches the other.
     */
    private fun cachedBaseDecode(scale: Float): Bitmap? {
        if (scale >= 1f) return cachedOriginalDecode()
        val full = cachedOriginalDecode() ?: return null
        // Bucket index matches quantizedScale's 0.05-bucket key so the base and
        // the fold cache always agree on which resolution is in play.
        val key = (scale * 20f).roundToInt().coerceAtLeast(1)
        previewOriginalScaleCache[key]?.let { if (!it.isRecycled) return it }
        if (previewOriginalScaleCache.size > 4) {
            previewOriginalScaleCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
            previewOriginalScaleCache.clear()
        }
        val w = (full.width * scale).roundToInt().coerceAtLeast(1)
        val h = (full.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(full, w, h, true)
        previewOriginalScaleCache[key] = small
        return small
    }

    /** Source footprint (bounds only — no pixel decode), cached per photo. */
    private var cachedSourceWidth = 0
    private var cachedSourceHeight = 0

    private fun sourceDims(): Pair<Int, Int>? {
        if (cachedSourceWidth > 0 && cachedSourceHeight > 0) {
            return cachedSourceWidth to cachedSourceHeight
        }
        foldCacheOriginal?.let {
            cachedSourceWidth = it.width
            cachedSourceHeight = it.height
            return cachedSourceWidth to cachedSourceHeight
        }
        val uri = _uiState.value.imageUri ?: return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
        if (opts.outWidth > 0 && opts.outHeight > 0) {
            cachedSourceWidth = opts.outWidth
            cachedSourceHeight = opts.outHeight
            return cachedSourceWidth to cachedSourceHeight
        }
        return null
    }

    /**
     * Tracks the on-screen canvas size (px) so the preview-resolution computation
     * knows the visible footprint. Called by the screen on `onSizeChanged`.
     */
    fun setViewBox(widthPx: Int, heightPx: Int) {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        if (viewportWidthPx == w && viewportHeightPx == h) return
        viewportWidthPx = w
        viewportHeightPx = h
    }

    /** Tracks the display zoom so a >100% zoom triggers a full-size fold. */
    fun setZoom(zoom: Float) {
        viewZoom = zoom.coerceIn(0.5f, 5f)
    }

    /**
     * The preview resolution scale (relative to the source, ≤ 1): the visible
     * pixel footprint at the effective zoom (capped at 100% — beyond that the
     * full-size fold is produced alongside) plus a [PREVIEW_MARGIN] pad, divided
     * by the source's longest side. For a 4000px source on a 1080px viewport this
     * is ~0.34 at 100% zoom — a ~10x smaller fold. Never below 5% of the source
     * (a face ML segment still needs enough texture to be accurate).
     */
    /**
     * The fold cache's resolution key: [scale] snapped to 5%-bucket granularity
     * (0.05 steps, at least one bucket). A zoom that stays within a bucket
     * reuses the cached composite at the nearest size (the display scales it,
     * imperceptibly); crossing a bucket re-folds at the new size. Keeps a pinch
     * gesture from rebuilding the preview composite on every tick.
     */
    private fun quantizedScale(scale: Float): Float {
        return (scale * 20f).roundToInt().coerceAtLeast(1) / 20f
    }

    private fun previewScaleFor(sourceWidth: Int, sourceHeight: Int): Float {
        val sourceMax = maxOf(sourceWidth, sourceHeight).coerceAtLeast(1)
        val viewportMax = maxOf(viewportWidthPx, viewportHeightPx).coerceAtLeast(1)
        val effectiveZoom = viewZoom.coerceIn(0.5f, 1f)
        val previewMax = viewportMax * effectiveZoom * PREVIEW_MARGIN
        return quantizedScale((previewMax / sourceMax).coerceIn(0.05f, 1f))
    }

    private companion object {
        const val PREVIEW_MARGIN = 1.25f
        const val LAYER_THUMB_MAX = 100
        const val LAYER_PREVIEW_CACHE_MAX = 64
        const val FULL_FOLD_DEBOUNCE_MS = 500L
        const val GEOMETRY_TRACK_CACHE_MAX = 32
    }

    /**
     * Assembles the ordered, reversible effect list exactly as
     * `EditorScreen.buildStackSnapshot` does: the beauty sub-set from the stack,
     * then transform / crop, then adjustments/filter/enhance/style/background,
     * then brush/sticker/text overlays, then frames. The canonical order is a
     * default for freshly-added effects; the live fold always follows the user's
     * reordered `effectStack` (orderedMerge below). Kept in sync with the snapshot
     * so the final render and the Layers model agree on ordering.
     */
    private fun buildStackEffects(
        state: EditorUiState,
        bundle: EditorRenderBundle
    ): List<StackEffect> {
        val list = mutableListOf<StackEffect>()
        // Beauty: pulled straight from the single ordered effectStack (so its
        // user-chosen position is honoured). Because beauty is in the derived
        // list, orderedMerge below refreshes and preserves it in place.
        list += state.effectStack.filterIsInstance<BeautyStackEffect>()
        if (state.rotation != 0f || state.flipHorizontal || state.flipVertical) {
            list += TransformEffect(
                rotation = state.rotation,
                flipHorizontal = state.flipHorizontal,
                flipVertical = state.flipVertical
            )
        }
        if (state.cropRect != null) {
            list += CropEffect(rect = state.cropRect)
        }
        val adjustments = listOf(
            EditTool.BRIGHTNESS to state.brightness,
            EditTool.CONTRAST to state.contrast,
            EditTool.SATURATION to state.saturation,
            EditTool.SHARPEN to state.sharpness,
            EditTool.EXPOSURE to state.exposure,
            EditTool.SHADOWS to state.shadows,
            EditTool.HIGHLIGHTS to state.highlights,
            EditTool.TEMPERATURE to state.temperature,
            EditTool.TINT to state.tint,
            EditTool.VIGNETTE to state.vignette
        )
        adjustments.forEach { (tool, value) ->
            if (value != 0f) list += SingleAdjustmentEffect(tool = tool, value = value)
        }
        bundle.filterMatrix?.let { list += FilterEffect(filterMatrix = it) }
        if (state.skinDenoise != 0f) list += SkinDenoiseEffect(intensity = state.skinDenoise)
        if (state.enhanceEnabled) list += EnhanceEffect(enabled = true)
        bundle.styleFilter?.let { list += StyleEffect(filter = it) }
        bundle.background?.let { bg ->
            list += BackgroundEffect(
                mode = bg.mode,
                subjectMask = bg.subjectMask,
                blurRadius = bg.blurRadius,
                shape = bg.shape,
                backgroundImage = bg.backgroundImage
            )
        }
        if (bundle.strokes.any { it.type == StrokeType.PEN }) {
            list += BitmapDiffEffect(
                tool = EditTool.PEN,
                strokes = bundle.strokes.filter { it.type == StrokeType.PEN }
            )
        }
        if (bundle.strokes.any { it.type == StrokeType.MOSAIC }) {
            list += BitmapDiffEffect(
                tool = EditTool.MOSAIC,
                strokes = bundle.strokes.filter { it.type == StrokeType.MOSAIC },
                suggestedRegions = bundle.suggestedRegions
            )
        }
        if (bundle.strokes.any { it.type == StrokeType.BLUR }) {
            list += BitmapDiffEffect(
                tool = EditTool.BLUR_BRUSH,
                strokes = bundle.strokes.filter { it.type == StrokeType.BLUR }
            )
        }
        if (bundle.strokes.any { it.type == StrokeType.REMOVAL }) {
            list += BitmapDiffEffect(
                tool = EditTool.OBJECT_REMOVAL,
                strokes = bundle.strokes.filter { it.type == StrokeType.REMOVAL }
            )
        }
        if (bundle.stickers.isNotEmpty()) list += StickerEffect(layers = bundle.stickers)
        if (bundle.texts.isNotEmpty()) list += TextEffect(layers = bundle.texts)
        bundle.frames.firstOrNull()?.let { list += FrameEffect(layer = it) }
        // Order the final fold by the user's reorderable effectStack (the
        // ordered source of truth), refreshing params in place and appending any
        // newly active effects.
        return orderedMerge(state.effectStack, list)
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

/**
 * Merges the freshly-derived [derived] effect list into the existing [existing]
 * stack while PRESERVING the user's ordering: entries already in the stack keep
 * their position but get their params refreshed from [derived]; entries not yet
 * in the stack are appended; entries whose tool went inactive (absent from
 * [derived]) are dropped. This is what lets the Layers list be reordered while
 * remaining a live reflection of the current tool parameters.
 *
 * [existing] is the ordered source of truth; [derived] is the param snapshot in
 * a canonical (tool-grouped) order that is only used to refresh + supply new
 * entries.
 */
/**
 * Stable per-effect fingerprint capturing the effect's TYPE and PARAMETER
 * values — NOT geometry (which is lazily resolved at fold time) or the random
 * [StackEffect.id]. Two effects with identical parameters produce the same
 * fingerprint; a single slider tick changes it. Used by the incremental fold
 * cache to detect whether the prefix (effects before the current tool) or the
 * full stack has actually changed.
 */
private fun stackEffectFingerprint(effect: StackEffect): String = when (effect) {
    is SingleAdjustmentEffect -> "adj:${effect.tool}:${effect.value}"
    is FilterEffect -> "filter:${effect.filterMatrix?.contentHashCode() ?: -1}"
    is EnhanceEffect -> "enhance:${effect.enabled}"
    is StyleEffect -> "style:${effect.filter?.id ?: "null"}"
    is BackgroundEffect -> "bg:${effect.mode}:${effect.blurRadius}:" +
        "${effect.shape}:${effect.subjectMask != null}:${effect.backgroundImage?.hashCode() ?: -1}"
    is BitmapDiffEffect -> "brush:${effect.tool}:${effect.strokes.size}:" +
        "${effect.strokes.hashCode()}:${effect.diff?.hashCode() ?: -1}"
    is StickerEffect -> "sticker:${effect.layers.size}:${effect.layers.hashCode()}"
    is TextEffect -> "text:${effect.layers.size}:${effect.layers.hashCode()}"
    is FrameEffect -> "frame:${effect.layer?.hashCode() ?: -1}"
    is CropEffect -> "crop:${effect.rect}"
    is TransformEffect -> "transform:${effect.rotation}:${effect.flipHorizontal}:" +
        "${effect.flipVertical}"
    is SkinDenoiseEffect -> "denoise:${effect.intensity}"
    is StackRoot -> "root:${effect.image?.hashCode() ?: -1}"
    is MergedEffect -> "merged:${effect.image?.hashCode() ?: -1}"
    // BeautyStackEffect subtypes — geometry is excluded (resolved at fold time).
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyAutoEffect ->
        "beauty:auto:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautySmoothingEffect ->
        "beauty:smoothing:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyTeethEffect ->
        "beauty:teeth:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyEyeBrightenEffect ->
        "beauty:eyeBrighten:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyDarkCircleEffect ->
        "beauty:darkCircle:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautySpotEffect ->
        "beauty:spot:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyWrinkleEffect ->
        "beauty:wrinkle:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautySkinToneEffect ->
        "beauty:skinTone:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautySlimEffect ->
        "beauty:slim:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyEyeEnlargeEffect ->
        "beauty:eyeEnlarge:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyNoseEffect ->
        "beauty:nose:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyJawEffect ->
        "beauty:jaw:${effect.intensity}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyLipstickEffect ->
        "beauty:lipstick:${effect.intensity}:${effect.color}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyBlushEffect ->
        "beauty:blush:${effect.intensity}:${effect.color}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyEyeShadowEffect ->
        "beauty:eyeShadow:${effect.intensity}:${effect.color}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyEyelinerEffect ->
        "beauty:eyeliner:${effect.intensity}:${effect.color}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyFoundationEffect ->
        "beauty:foundation:${effect.intensity}:${effect.color}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyHairEffect ->
        "beauty:hair:${effect.intensity}:${effect.color}"
    is codes.pepper.whimsicalart.feature.editor.domain.BeautyPenEffect ->
        "beauty:pen:${effect.strokes.size}:${effect.strokes.hashCode()}"
}

private fun orderedMerge(
    existing: List<StackEffect>,
    derived: List<StackEffect>
): List<StackEffect> {
    if (existing.isEmpty()) return derived
    val derivedByKey = derived.associateBy { it.layerKey }
    val merged = mutableListOf<StackEffect>()
    for (e in existing) {
        // Refresh in place if the tool is still active; otherwise drop it.
        val refreshed = derivedByKey[e.layerKey]
        if (refreshed != null) merged += refreshed
    }
    // Append any newly-active effects (fresh tools) not already represented.
    val present = merged.mapTo(mutableSetOf()) { it.layerKey }
    for (e in derived) {
        if (present.add(e.layerKey)) merged += e
    }
    return merged
}

/**
 * The incremental fold cache at ONE resolution (preview or full — the ViewModel
 * keeps one instance per resolution, see the fold cache field docs). Holds the
 * composite of the effect prefix before the current tool (`currentImage`), the
 * full-stack composite (`lastImage`), and the fingerprints of the effect lists
 * that produced them.
 */
private class FoldImageCache(
    var prefixSignature: String = "",
    var stackSignature: String = "",
    var currentImage: Bitmap? = null,
    var lastImage: Bitmap? = null
) {
    fun clear() {
        currentImage?.takeUnless(Bitmap::isRecycled)?.recycle()
        currentImage = null
        lastImage?.takeUnless(Bitmap::isRecycled)?.recycle()
        lastImage = null
        prefixSignature = ""
        stackSignature = ""
    }
}
