package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.graphics.PointF
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import codes.pepper.whimsicalart.feature.beauty.domain.SkinDenoiseProcessor
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometry
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryBaseSource
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryContext
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryGenerator
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryResolver
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyLayerSpec
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyProcessor
import codes.pepper.whimsicalart.feature.beauty.domain.BrushStroke
import codes.pepper.whimsicalart.feature.beauty.domain.DefaultBeautyGeometryContext
import codes.pepper.whimsicalart.feature.beauty.domain.GeometryField
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyParams
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyTool
import codes.pepper.whimsicalart.feature.editor.domain.BitmapRenderer
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode
import codes.pepper.whimsicalart.core.domain.wrapDegrees
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.domain.StackRoot
import codes.pepper.whimsicalart.feature.editor.domain.backgroundBlurRadiusPx
import codes.pepper.whimsicalart.feature.editor.domain.decodeBoundedOriented
import codes.pepper.whimsicalart.feature.editor.data.EditorCacheStore
import codes.pepper.whimsicalart.feature.editor.data.FoldImageCache
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
import codes.pepper.whimsicalart.feature.editor.domain.FrameStyle
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
import codes.pepper.whimsicalart.feature.editor.domain.matting.SoftEdgeBand
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.domain.ocr.OcrOverlayFragment
import codes.pepper.whimsicalart.feature.editor.domain.ocr.OcrTextExtractor
import codes.pepper.whimsicalart.feature.editor.ui.background.BackgroundPresets
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
    // Pixel dimensions of the CURRENT fold base — `firstFull`, or its preview
    // stand-in `firstPreview`, in the effect_stack.md model: the pristine
    // decode before any merge, the flattened (possibly LARGER) merged result
    // after one. Source-space geometry (the crop grid, imageFitSize/imageViewSize
    // letterboxing) must anchor to THIS, never the pristine `originalBitmap`:
    // after a merge the crop overlay would otherwise be constrained to the old
    // (pre-merge) extent instead of spanning the merged base image.
    val baseImageSize: Size? = null,
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
    val isImageLoading: Boolean = false,
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
    // Transient live twist accumulated during a two-finger rotate gesture. It is
    // applied ONLY for on-screen feedback (the canvas rotates by rotation + live,
    // or by live alone over a composite that already bakes rotation) and is never
    // persisted in history: the raw accumulation crosses 0°/360° without wrapping
    // so the preview tracks the fingers smoothly, and the wrapped value is folded
    // into `rotation` once (at gesture end) when the heavy side effects run.
    val liveRotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    // The transform that the CURRENTLY DISPLAYED compositePreview has already
    // baked into its pixels (stamped when a fold publishes). The canvas restores
    // the unbaked remainder live on top of the composite — (rotation − baked) and
    // a XOR on the flips — so the transform toolbar (slider / CW-CCW / flips)
    // gets the same instant feedback as the twist while the re-fold chews on the
    // new angle (the folder re-bakes it and the remainder returns to zero).
    val compositeBakedRotation: Float = 0f,
    val compositeBakedFlipHorizontal: Boolean = false,
    val compositeBakedFlipVertical: Boolean = false,
    val cropRect: Rect? = null,
    val innerFrameStyle: FrameStyle? = null,
    val innerFrameThickness: Float = 0.06f,
    val innerFrameColor: Int = Color.BLACK,
    val innerFrameEdgeSize: Float = 0.2f,
    val innerFrameCustomAngle: Float = 0f,
    val innerFrameCustomCurveDistance: Float = -0.5f,
    val innerFrameCustomStrength: Float = 0f,
    val innerFrameCustomCount: Float = 2f,
    val innerFrameLines: Int = 0,
    val innerFrameLinesEnabled: Boolean = false,
    val innerFrameLineDistance: Float = 0.02f,
    val innerFrameLineThickness: Float = 0.02f,
    val outerFrameStyle: FrameStyle? = null,
    val outerFrameThickness: Float = 0.06f,
    val outerFrameColor: Int = Color.BLACK,
    val outerFrameEdgeSize: Float = 0.2f,
    val outerFrameCustomAngle: Float = 0f,
    val outerFrameCustomCurveDistance: Float = -0.5f,
    val outerFrameCustomStrength: Float = 0f,
    val outerFrameCustomCount: Float = 2f,
    val outerFrameLines: Int = 0,
    val outerFrameLinesEnabled: Boolean = false,
    val outerFrameLineDistance: Float = 0.02f,
    val outerFrameLineThickness: Float = 0.02f,
    val isComparing: Boolean = false,
    val history: List<StackDocument> = emptyList(),
    val historyIndex: Int = -1,
    val backgroundMode: BackgroundMode = BackgroundMode.BLUR,
    val backgroundBlurRadius: Float = 50f,
    val backgroundShape: BokehShape = BokehShape.CIRCLE,
    val subjectMask: Bitmap? = null,
    val isSegmenting: Boolean = false,
    // True when the last person-segmentation attempt produced no mask (the
    // bundled interpreter / model I/O failed on this device). Surfaces as a
    // persistent "Subject detection failed" panel state so the Blur/Replace
    // controls don't silently dead-end on a null mask (see Background tools bug).
    val subjectDetectionFailed: Boolean = false,
    val selectedBackgroundRes: Int? = null,
    val backgroundImage: Bitmap? = null,
    val backgroundPlacementScale: Float = 1f,
    val backgroundPlacementPanX: Float = 0f,
    val backgroundPlacementPanY: Float = 0f,
    val backgroundPlacementRotationDegrees: Float = 0f,
    val backgroundFreeScaleUp: Boolean = false,
    val enhanceEnabled: Boolean = false,
    val styleFilter: StyleFilter? = null,
    val selectedFilterId: String? = null,
    val enabledLensFilterIds: Set<String> = emptySet(),
    val effectStack: List<StackEffect> = emptyList(),
    val hasMerged: Boolean = false,
    val isLayersVisible: Boolean = false,
    val layerThumbnails: Map<String, Bitmap> = emptyMap(),
    val layerFingerprints: Map<String, String> = emptyMap(),
    // The committed-brush-strokes version each published compositePreview was
    // folded with (one per brush tool). The brush overlays keep their committed
    // strokes drawn until the published composite's version reaches the tool's
    // current strokesVersion, so a live-applied stroke never disappears into a
    // dead gap before the regenerated composite lands (see UI/UX "Brush tools —
    // no dead gap").
    val publishedMosaicStrokesVersion: Int = 0,
    val publishedRemovalStrokesVersion: Int = 0,
    val publishedBlurStrokesVersion: Int = 0,
    val publishedPenStrokesVersion: Int = 0,
    // True once ANY real edit has landed since the document was opened or since
    // the last successful save/share — the editor's "discard changes?" signal
    // (docs/user_guide.md → The editor → Leaving the editor). Deliberately NOT
    // layers/history emptiness (a merge collapses the layer list without
    // reverting anything); bumped from the ONE funnel (saveToHistory) plus the
    // undo/redo bypasses, and cleared only by a SUCCESSFUL save or share.
    val hasUnsavedChanges: Boolean = false
) {
    /**
     * The decorative lines actually drawn for the inner tier: the count when its
     * "Lines" toggle is ON, 0 when off (the toggle-off state is the ONLY 0-lines
     * state — docs/effects_reference.md → *Frame lines*). Every downstream frame
     * layer / outer-extent computation reads this, so a disabled tier never leaks
     * a line strip into the committed effect or the outer extent math.
     */
    val effectiveInnerFrameLines: Int get() = if (innerFrameLinesEnabled) innerFrameLines else 0

    /** See [effectiveInnerFrameLines]; the mirror for the outer tier. */
    val effectiveOuterFrameLines: Int get() = if (outerFrameLinesEnabled) outerFrameLines else 0
}

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
    private val ocrDetector: MnnOcrDetector,
    private val beautyProcessor: BeautyProcessor = BeautyProcessor(),
    private val beautyResolver: BeautyGeometryResolver =
        BeautyGeometryResolver { _, _, _ -> BeautyGeometry.NONE },
    private val cacheStore: EditorCacheStore = EditorCacheStore(context)
) : ViewModel() {

    private val historyStore = EditorHistoryStore()

    /**
     * The pristine document snapshot the current image was opened with (empty
     * effect stack + default params). The saveToHistory dirty-bump compares each
     * newly pushed checkpoint against it when history is still empty, so the
     * very first baseline push of an untouched session (a bare tool tap — see
     * [selectTool]) is NOT counted as a change.
     */
    private var cleanBaseline: StackDocument = StackDocument(effectStack = emptyList())

    /**
     * Fold-pipeline planning policy: preview resolution (viewport/zoom →
     * [RenderPipelinePlanner.previewScaleFor]/[RenderPipelinePlanner.quantizedScale]),
     * the fold bases (`firstFull`/`firstPreview` + their scale-bucketed
     * downscales) and the memoized footprint estimate. Passive state + pure
     * math — the ViewModel keeps this class's full/preview [FoldImageCache]s and
     * the async fold orchestration (see the planner's KDoc).
     */
    private val planner = RenderPipelinePlanner(
        context = context,
        imageUri = { _uiState.value.imageUri },
        effectFingerprint = ::stackEffectFingerprint
    )

    /**
     * Geometry-track planning policy: the pure deciders behind the beauty /
     * background ML pipeline — where the selected tool lands in the stack, which
     * geometry-changing effects precede the background, the segment-0 base, the
     * adaptive ML budget + fold scale, and the footprint-adaptive background
     * artifact rescaling (see the tracker's KDoc). The fold RUNNER and its
     * bounded intermediate cache stay in this ViewModel (that is orchestration).
     */
    private val geometryTracker = GeometryTracker(
        originalGeometryImage = { originalGeometryImage },
        selectedTool = { _uiState.value.selectedTool },
        selectedBeautyTool = { _uiState.value.selectedBeautyTool },
        effectStack = { _uiState.value.effectStack }
    )

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var sharpnessJob: kotlinx.coroutines.Job? = null
    private var sharpnessDecodedSource: Bitmap? = null
    private var sharpnessPreviewRunning = false
    private var sharpnessPreviewDirty = false

    // Background placement drag coalescing: pan/scale/rotate ticks fire the
    // live-look pass (refreshPreviewLook). Debouncing the placement path means a
    // fast drag settles into ONE trailing look pass instead of re-rendering per
    // tick — the interactive placement grid (and the composite fold, which
    // re-keys on the uiState change and stays live) remain the immediate
    // feedback, the expensive look pass just catches up once the gesture stops.
    private var placementLookDebounce: kotlinx.coroutines.Job? = null

    // Continuous committed-composite preview: renders the whole effect stack
    // (with geometric base steps baked in) into `compositePreview`. Debounced +
    // cancel-and-restart so a frenetic slider drag re-renders with the latest
    // value rather than stacking many full-image renders. The fold itself is
    // cheap for unchanged parts via the incremental cache below.
    private var compositeJob: kotlinx.coroutines.Job? = null
    private var compositeRunning = false
    private var compositeDirty = false
    /**
     * Monotonic token bumped on every [refreshCompositePreview]; a composite
     * fold (preview loop or full fold) only PUBLISHES when the token has not
     * advanced past the request it rendered — a newer request's fold must never
     * be overwritten by (or report the strokes version of) a superseded one.
     */
    private var compositeRequestSeq = 0L

    /**
     * The settle-window job that folds a finished two-finger twist into
     * [EditorUiState.rotation] after the gesture has been still for
     * [TWIST_COMMIT_SETTLE_MS]. Restarted on every [applyTwistDelta], cancelled by
     * any committed rotation change, so the heavy side effects run exactly once
     * per gesture.
     */
    private var twistCommitJob: Job? = null

    /**
     * The composite render request the coalescing fold loop consumes. Refreshed
     * on EVERY [refreshCompositePreview] call — including the calls that arrive
     * while a fold is already running (they only set [compositeDirty]) — so the
     * in-flight loop re-folds the FRESHEST bundle each iteration instead of the
     * bundle captured when the job started. Without this, a rapid placement /
     * slider gesture stream coalesced forever against a STALE bundle: the
     * published composite never reflected the last pan/scale/rotate, so the
     * replacement image visibly failed to follow the placement grid.
     */
    private data class CompositeRenderRequest(
        val bundle: EditorRenderBundle,
        val includeTail: Boolean,
        val excludeCurrentTool: Boolean,
        val zoom: Float,
        val mosaicStrokesVersion: Int = 0,
        val removalStrokesVersion: Int = 0,
        val blurStrokesVersion: Int = 0,
        val penStrokesVersion: Int = 0
    )

    private var compositeRequest: CompositeRenderRequest? = null

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
    //    tool (the base the tool is applied to). It is anchored at the number of
    //    effects its prefix covers (anchorCount), so later edits reuse it as the
    //    fold start or as the anchor a missing prefix gap is folded from; when a
    //    stale-prefix refold for the last effect happens it is opportunistically
    //    re-anchored at ⌊(N-1)/2⌋ (docs/effect_stack.md → Choosing the base
    //    image).
    //  - prevImage : the composite of every effect BEFORE the LAST one (N-1) —
    //    the input the last effect is applied to, so the last tool folds over it
    //    instead of over lastImage (which would re-apply itself). When the edited
    //    tool IS the last effect, prevImage coincides with currentImage.
    //  - lastImage : the full composite of the WHOLE stack (the preview result).
    //
    // The three caches (current + prev + last; original is not a cache) each
    // exist at TWO resolutions (docs/effect_stack.md → Performance / preview
    // cache):
    //  - PREVIEW resolution: the common path — used for the on-screen composite
    //    while zoom ≤100% and as the stand-in while a full-size fold runs. Sized
    //    to the visible pixel footprint (visibleDrawnPixels + margin), so it is a
    //    small fraction of the full image.
    //  - FULL resolution: computed only when the user zooms past 100% (or a zoom
    //    is already set) or a full-res result (save / merge / share) is needed.
    //
    // Each cache entry is keyed by a fingerprint of the effect list that produced
    // it. If the current prefix still matches the cache's signature, a render
    // re-uses the cached currentImage/prevImage/lastImage and only re-folds the
    // missing gap/tail — never re-folding the unchanged prefix from the base. The
    // cache is BYTE-IDENTICAL to a from-base re-fold at the SAME resolution; it
    // only avoids repeating fold work. At most three intermediates per resolution
    // (current + prev + last) are held alongside the original.
    // The caches themselves (full/preview [FoldImageCache]s) now live in the
    // data-layer [EditorCacheStore]; this ViewModel drives them through the fold
    // controller below (see the fold cache field docs).

    // The full-size fold base and its preview-resolution counterpart
    // (docs/effect_stack.md → The full-size image model + Choosing preview
    // resolution) now live + are read/written through [planner]; the ViewModel
    // still repoints `firstFull` to the merged result here, but the reference is
    // held (and the preview counterpart derived) inside the planner.

    // Test-only fold-to-index diagnostics (not a production API): which of the
    // controller's three rules fired on the last requests. Every controller call
    // increments EXACTLY one of them, so their sum is the fold-to-index entry
    // call count (the "single point of entrance" probe).
    internal var indexZeroFoldCountForTest = 0
    internal var cacheHitCountForTest = 0
    internal var foldCallsForTest = 0

    // Test-only probe for the geometry track (spec → Geometry is a separate
    // process): how many geometry-base folds ran OUTSIDE the colour fold
    // controller. Non-zero after a deforming fold proves that process bypasses
    // the colour caches/entry as designed.
    internal var geometryTrackBaseCallsForTest = 0

    // Per-layer preview cache (docs/effect_stack.md → Performance / preview
    // cache): one PREVIEW-resolution composite per StackEffect layer, keyed by
    // layerKey, sized to the current preview scale bucket, and fingerprint-tagged
    // with the effect that produced it. The Layers window — and its row
    // thumbnails, which are plain downscales of these previews — serves from this
    // cache instead of ever running a full fold. Building all layers is one
    // incremental pass (each layer's preview is the previous layer's preview
    // folded with one more effect, reusing the shared beauty geometry context), so
    // it stays cheap. All entries are independent copies; evicted (and recycled)
    // on fingerprint invalidation or when the preview scale bucket changes. The
    // cache itself (bitmaps + fingerprints) lives in [LayerPreviewManager].
    private val layerPreviewManager = LayerPreviewManager(::stackEffectFingerprint)
    private var layerPreviewScaleBucket = -1f
    private var layerPreviewsJob: Job? = null

    // Viewing footprint + // preview resolution decision moved into [planner].
    // The scale-artifact cache lives in [geometryTracker].

    private lateinit var segmenter: SelfieMattingSegmenter

    /**
     * The editor's SHARED beauty geometry context (docs/effect_stack.md →
     * Shared geometry context (beauty + background)): the SAME instance the
     * beauty features and the background tool resolve their geometry through,
     * per-field lazy (face/hair/skin for beauty; SUBJECT_MASK for background).
     * Deliberately a class-body PROPERTY, not a constructor default: its
     * generator must address the attached [segmenter], and constructor
     * default-arg lambdas cannot reference `this`/instance members. The
     * SUBJECT_MASK request segments EXACTLY the base the caller hands in — which
     * [segmentSubject] guarantees to be the geometry-track base at the
     * background layer's position (never the raw original / resident bitmap) —
     * so the mask lands in the same pixel space the committed [BackgroundEffect]
     * renders over. A beauty request (FACE/SKIN/HAIR, possibly mixed with
     * SUBJECT_MASK — no current consumer does the mix) resolves through the
     * injected [beautyResolver], the app-scoped ML pipeline from
     * feature:beauty. Merged when both kinds are requested.
     */
    private val beautyGeometryContext: BeautyGeometryContext by lazy {
        DefaultBeautyGeometryContext(
            generator = BeautyGeometryGenerator { image, maxPixelCountHint, neededFields ->
                if (GeometryField.SUBJECT_MASK in neededFields) {
                    val subject = BeautyGeometry(
                        subjectMask = runCatching { segmenter.segment(image) }.getOrNull()
                    )
                    val beautyFields = neededFields - GeometryField.SUBJECT_MASK
                    if (beautyFields.isEmpty()) subject
                    else subject.mergedWith(
                        beautyResolver.resolve(image, maxPixelCountHint, beautyFields)
                    )
                } else if (neededFields.isEmpty()) {
                    // Pen (empty set) folds no geometry.
                    BeautyGeometry.NONE
                } else {
                    beautyResolver.resolve(image, maxPixelCountHint, neededFields)
                }
            }
        )
    }

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

    // Serializes all geometryTrackCache access. The editor's overlapping fold
    // coroutines (composite / layer-previews / full-resolution) share this map;
    // without a lock a concurrent read/put/clear corrupts the HashMap and can
    // hang the clear loop — one of the two device-only crash mechanisms (docs:
    // Beauty tools terminate silently on a real device).
    private val geometryTrackLock = Any()

    init {
        segmenter = SelfieMattingSegmenter(context)
        // Warm the on-device OCR probe off the main thread so the first
        // isAvailable read (the UI gate for Recognize Text) is already decided.
        viewModelScope.launch(Dispatchers.Default) { ocrDetector.isAvailable }
    }

    fun setImageUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            imageUri = uri,
            layerThumbnails = emptyMap(),
            layerFingerprints = emptyMap()
        )
        // A different photo invalidates the cached preview decode from the
        // previous image, otherwise sharpen/enhance/style re-use stale pixels.
        // Dropped WITHOUT recycling: the running sharpen/preview worker holds its
        // own reference and may be mid-`getPixel` on the old decode — recycling
        // would free the pixels under it (HWUI abort); GC reclaims it once the
        // worker finishes (same rule as invalidateFoldCache(clearOriginal)).
        sharpnessDecodedSource = null
        sharpnessJob?.cancel()
        sharpnessPreviewRunning = false
        sharpnessPreviewDirty = false
        placementLookDebounce?.cancel()
        // A new photo invalidates the incremental fold caches including the
        // cached original decode (whose pixels no longer match the new URI), and
        // every per-layer preview built from it.
        clearLayerPreviews()
        invalidateFoldCache(clearOriginal = true)
    }

    /**
     * Decodes + orients [uri] on [Dispatchers.IO] and publishes the result as
     * `originalBitmap`/`editedBitmap`. Previously the editor's first photo decode
     * ran on the main thread via `LaunchedEffect(imageUri)` in `EditorScreen`,
     * which skipped ~39 frames during editor init (a HWUI `Image decoding logging
     * dropped!` burst on the main thread right before `Skipped 39 frames` in
     * `log.7.txt`). [onLoaded] lets the screen run follow-up main-thread work
     * after the decode lands. Only the most recent request wins — a fast photo
     * switch cancels the in-flight decode so a stale bitmap never overwrites the
     * newer image.
     */
    private var imageLoadJob: Job? = null

    fun loadImageBitmap(uri: Uri, onLoaded: (Bitmap?) -> Unit = {}) {
        imageLoadJob?.cancel()
        imageLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImageLoading = true, error = null)
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    decodeBoundedOriented(context.contentResolver, uri)
                }.getOrNull()
            }
            _uiState.value = _uiState.value.copy(isImageLoading = false)
            if (decoded != null) {
                _uiState.value = _uiState.value.copy(
                    originalBitmap = decoded,
                    editedBitmap = decoded,
                    baseImageSize = Size(decoded.width.toFloat(), decoded.height.toFloat()),
                    hasUnsavedChanges = false
                )
                cleanBaseline = StackDocument(effectStack = emptyList())
            } else {
                // Never leave a blank canvas silently: a shared uri whose decode
                // fails (permission revoked / source gone / bogus content) must
                // surface an explicit error the screen can retry from.
                _uiState.value = _uiState.value.copy(
                    error = "Unable to open the image. It may be unavailable, or permission was denied."
                )
            }
            onLoaded(decoded)
        }
    }

    /** Re-attempts [loadImageBitmap] for the current [EditorUiState.imageUri]. */
    fun retryLoadImage() {
        val uri = _uiState.value.imageUri ?: return
        loadImageBitmap(uri)
    }

    fun setBitmaps(original: Bitmap, edited: Bitmap) {
        _uiState.value = _uiState.value.copy(
            originalBitmap = original,
            editedBitmap = edited,
            baseImageSize = Size(edited.width.toFloat(), edited.height.toFloat()),
            hasUnsavedChanges = false
        )
        cleanBaseline = StackDocument(effectStack = emptyList())
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
        val before = _uiState.value
        saveToHistory()
        _uiState.update { state ->
            state.copy(effectStack = mergeBeautyIntoStack(state.effectStack, listOf(effect)))
        }
        stalenessForDerivedDelta(
            buildStackEffects(before, EditorRenderBundle()),
            buildStackEffects(_uiState.value, EditorRenderBundle())
        )
        refreshPreviewLook()
    }

    /** Removes one beauty sub-tool layer by its [BeautyStackEffect.layerKey] from the stack. */
    fun removeBeautyEffect(layerKey: String) {
        val before = _uiState.value
        saveToHistory()
        _uiState.update { state ->
            state.copy(
                effectStack = state.effectStack.filterNot {
                    it is BeautyStackEffect && it.layerKey == layerKey
                }
            )
        }
        stalenessForDerivedDelta(
            buildStackEffects(before, EditorRenderBundle()),
            buildStackEffects(_uiState.value, EditorRenderBundle())
        )
        refreshPreviewLook()
    }

    /** Clears every beauty sub-tool layer from the stack (keeps non-beauty layers). */
    fun clearBeautyEffects() {
        val before = _uiState.value
        if (before.effectStack.any { it is BeautyStackEffect }) {
            saveToHistory()
            _uiState.update { it.copy(effectStack = it.effectStack.filterNot { e -> e is BeautyStackEffect }) }
        }
        stalenessForDerivedDelta(
            buildStackEffects(before, EditorRenderBundle()),
            buildStackEffects(_uiState.value, EditorRenderBundle())
        )
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
        if (effects.any { it.changesGeometry }) invalidateSubjectMask()
        val before = _uiState.value
        _uiState.update { it.copy(effectStack = mergeBeautyIntoStack(it.effectStack, effects)) }
        stalenessForDerivedDelta(
            buildStackEffects(before, EditorRenderBundle()),
            buildStackEffects(_uiState.value, EditorRenderBundle())
        )
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
     * The beauty pen is a Category B brush tool (BEAUTY + BRIGHTNESS_PEN): while
     * the tool's eye is OFF the single-finger drag paints instead of panning, and
     * the drag events land here with NORMALIZED (source-fraction) coordinates —
     * the overlay converts its box geometry so the strokes stay glued to the base
     * image no matter how the user has panned/zoomed the viewport.
     *
     * Strokes are appended straight into the editor-owned beauty params and folded
     * into `effectStack` as a live `beauty:pen` layer (no undo entry per drag
     * point; the shift to another tool / a later snapshot boundary commits the
     * undo entry).
     */
    fun startBeautyPenStroke(position: PointF) {
        updateBeautyParams {
            val baseWidth = beautyPenBaseWidth()
            it.copy(
                activeStroke = BrushStroke(
                    points = mutableListOf(position),
                    size = it.brushSize / baseWidth,
                    opacity = it.brushOpacity
                )
            )
        }
    }

    /**
     * Extends the active stroke with [position], spacing sample points so that
     * consecutive points are at least half the brush radius apart (like the
     * standalone pen's `sampleAlong`). A point closer than that is dropped, and
     * every append copies the points list so snapshot/undo entries never share a
     * mutable list.
     */
    fun moveBeautyPenStroke(position: PointF) {
        updateBeautyParams { params ->
            val active = params.activeStroke ?: return@updateBeautyParams params
            val minStep = active.size / 2f
            val last = active.points.lastOrNull()
            if (last == null || distanceSquared(last, position) >= minStep * minStep) {
                params.copy(activeStroke = active.copy(points = active.points + position))
            } else {
                params
            }
        }
    }

    /**
     * Commits the active stroke into the committed brush strokes and folds the
     * `beauty:pen` layer into `effectStack`.
     */
    fun endBeautyPenStroke() {
        _uiState.update { state ->
            val active = state.beauty.activeStroke ?: return@update state
            state.copy(
                beauty = state.beauty.copy(
                    brushStrokes = state.beauty.brushStrokes + active,
                    activeStroke = null
                )
            )
        }
        setBeautyEffects(_uiState.value.beauty.buildBeautySpecs().toBeautyEffects())
    }

    private fun beautyPenBaseWidth(): Float {
        val bmp = _uiState.value.originalBitmap
        return bmp?.width?.toFloat() ?: DEFAULT_PEN_BASE_WIDTH
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
        if (lipstick > 0f) list += BeautyLayerSpec("beauty:lipstick", lipstick, color = lipstickColor)
        if (blush > 0f) list += BeautyLayerSpec("beauty:blush", blush, color = blushColor)
        if (eyeShadow > 0f) list += BeautyLayerSpec("beauty:eye_shadow", eyeShadow, color = eyeShadowColor)
        if (eyeliner > 0f) list += BeautyLayerSpec("beauty:eyeliner", eyeliner, color = eyelinerColor)
        if (foundation > 0f) list += BeautyLayerSpec("beauty:foundation", foundation, color = foundationColor)
        if (hair > 0f) list += BeautyLayerSpec("beauty:hair", hair, color = hairColor)
        if (brushStrokes.isNotEmpty() || activeStroke != null) {
            list += BeautyLayerSpec(
                toolKey = "beauty:pen",
                strokes = brushStrokes + listOfNotNull(activeStroke),
                opacity = brushOpacity,
                color = penColor
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

    /**
     * Clears the single [BeautyParams] field behind a removed beauty sub-tool
     * [layerKey], so the editor-owned state and the effect stack stay in sync and
     * the deleted layer is not resurrected by the derived spec list. Identity
     * (unchanged) when the key maps to nothing active.
     */
    private fun BeautyParams.neutralizeEffectParam(layerKey: String): BeautyParams = when (layerKey) {
        "beauty:auto" -> copy(auto = 0f)
        "beauty:smoothing" -> copy(smoothing = 0f)
        "beauty:teeth" -> copy(teeth = 0f)
        "beauty:eye_brighten" -> copy(eyeBrighten = 0f)
        "beauty:dark_circles" -> copy(darkCircle = 0f)
        "beauty:spots" -> copy(spots = 0f)
        "beauty:wrinkles" -> copy(wrinkles = 0f)
        "beauty:skin_tone" -> copy(skinTone = 0f)
        "beauty:slim" -> copy(faceSlim = 0f)
        "beauty:eye_enlarge" -> copy(eyeEnlarge = 0f)
        "beauty:nose" -> copy(nose = 0f)
        "beauty:jaw" -> copy(jaw = 0f)
        "beauty:lipstick" -> copy(lipstick = 0f)
        "beauty:blush" -> copy(blush = 0f)
        "beauty:eye_shadow" -> copy(eyeShadow = 0f)
        "beauty:eyeliner" -> copy(eyeliner = 0f)
        "beauty:foundation" -> copy(foundation = 0f)
        "beauty:hair" -> copy(hair = 0f)
        "beauty:pen" -> copy(brushStrokes = emptyList(), activeStroke = null)
        else -> this
    }

    /**
     * Resets the editor-owned [EditorUiState] fields that DERIVE a removed
     * [StackEffect] back to their defaults, mirroring [BeautyParams] above. Every
     * non-beauty layer in the stack is derived from these live fields
     * ([buildStackSnapshot] / [buildStackEffects]), so leaving them set after a
     * removal makes the next lost-focus commit resurrect the very layer that was
     * just deleted. Neutralising here — BEFORE the entry is dropped — lets the
     * post-removal snapshot carry the cleared params (undo still restores the
     * pre-removal params because its checkpoint was captured earlier). Identity
     * (unchanged) for effects with no editor-owned source in `uiState` (brush /
     * sticker / text markers — their live sub-VM state is released by the
     * [codes.pepper.whimsicalart.feature.editor.ui.EditorScreen] caller).
     */
    private fun EditorUiState.neutralizeEffectParams(effect: StackEffect): EditorUiState = when (effect) {
        is codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect ->
            when (effect.tool) {
                EditTool.BRIGHTNESS -> copy(brightness = 0f)
                EditTool.CONTRAST -> copy(contrast = 0f)
                EditTool.SATURATION -> copy(saturation = 0f)
                EditTool.SHARPEN -> copy(sharpness = 0f)
                EditTool.EXPOSURE -> copy(exposure = 0f)
                EditTool.SHADOWS -> copy(shadows = 0f)
                EditTool.HIGHLIGHTS -> copy(highlights = 0f)
                EditTool.TEMPERATURE -> copy(temperature = 0f)
                EditTool.TINT -> copy(tint = 0f)
                EditTool.VIGNETTE -> copy(vignette = 0f)
                else -> this
            }
        is codes.pepper.whimsicalart.feature.editor.domain.TransformEffect ->
            copy(rotation = 0f, flipHorizontal = false, flipVertical = false, liveRotationDegrees = 0f)
        is codes.pepper.whimsicalart.feature.editor.domain.CropEffect -> copy(cropRect = null)
        is codes.pepper.whimsicalart.feature.editor.domain.FilterEffect ->
            copy(selectedFilterId = null, enabledLensFilterIds = emptySet())
        is codes.pepper.whimsicalart.feature.editor.domain.StyleEffect ->
            copy(styleFilter = null, selectedFilterId = null, enabledLensFilterIds = emptySet())
        is codes.pepper.whimsicalart.feature.editor.domain.SkinDenoiseEffect -> copy(skinDenoise = 0f)
        is codes.pepper.whimsicalart.feature.editor.domain.EnhanceEffect -> copy(enhanceEnabled = false)
        is codes.pepper.whimsicalart.feature.editor.domain.FrameEffect -> copy(
            innerFrameStyle = null,
            outerFrameStyle = null
        )
        is codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect -> copy(
            subjectMask = null,
            backgroundImage = null,
            selectedBackgroundRes = null,
            backgroundPlacementScale = 1f,
            backgroundPlacementPanX = 0f,
            backgroundPlacementPanY = 0f,
            backgroundPlacementRotationDegrees = 0f,
            backgroundFreeScaleUp = false
        )
        is BeautyStackEffect -> copy(beauty = beauty.neutralizeEffectParam(effect.layerKey))
        else -> this
    }

    /**
     * Full reset of EVERY user-facing tool control back to its default original
     * value. Used by [mergeLayers]: after a merge flattens the stack, no control
     * from any tool (transform rotation/flips, crop, frame style and its sliders,
     * background placement, lens filters, ...) may keep showing a stale value.
     * This is the one-shot, all-inclusive counterpart of the per-effect
     * [neutralizeEffectParams] used by narrower flows (undo / redo).
     */
    private fun EditorUiState.fullResetControls(): EditorUiState = copy(
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
        selectedFilterId = null,
        enabledLensFilterIds = emptySet(),
        rotation = 0f,
        flipHorizontal = false,
        flipVertical = false,
        cropRect = null,
        innerFrameStyle = null,
        innerFrameThickness = 0.06f,
        innerFrameColor = android.graphics.Color.BLACK,
        innerFrameEdgeSize = 0.2f,
        innerFrameCustomAngle = 0f,
        innerFrameCustomCurveDistance = -0.5f,
        innerFrameCustomStrength = 0f,
        innerFrameCustomCount = 2f,
        innerFrameLines = 0,
        innerFrameLinesEnabled = false,
        innerFrameLineDistance = 0.02f,
        innerFrameLineThickness = 0.02f,
        outerFrameStyle = null,
        outerFrameThickness = 0.06f,
        outerFrameColor = android.graphics.Color.BLACK,
        outerFrameEdgeSize = 0.2f,
        outerFrameCustomAngle = 0f,
        outerFrameCustomCurveDistance = -0.5f,
        outerFrameCustomStrength = 0f,
        outerFrameCustomCount = 2f,
        outerFrameLines = 0,
        outerFrameLinesEnabled = false,
        outerFrameLineDistance = 0.02f,
        outerFrameLineThickness = 0.02f,
        backgroundMode = BackgroundMode.BLUR,
        backgroundBlurRadius = 50f,
        backgroundShape = BokehShape.CIRCLE,
        subjectMask = null,
        selectedBackgroundRes = null,
        backgroundImage = null,
        backgroundPlacementScale = 1f,
        backgroundPlacementPanX = 0f,
        backgroundPlacementPanY = 0f,
        backgroundPlacementRotationDegrees = 0f,
        backgroundFreeScaleUp = false,
        beauty = BeautyParams()
    )

    fun selectTool(tool: EditTool?) {
        // COARSE_GRAINED pre-tool baseline: the checkpoint that begins an edit
        // session and that undo returns to after stepping back through it
        // (docs/effect_stack.md → Undo / redo).
        saveToHistory(HistoryKind.COARSE_GRAINED)
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
        twistCommitJob?.cancel()
        twistCommitJob = null
        val normalized = normalizeRotation(rotation)
        if (normalized == _uiState.value.rotation) {
            // Even a no-op set drops a pending live twist: a storyboard rotation
            // (slider / buttons) supersedes whatever live accumulation is queued.
            _uiState.update { it.copy(liveRotationDegrees = 0f) }
            return
        }
        _uiState.value = _uiState.value.copy(rotation = normalized, liveRotationDegrees = 0f)
        geometryContext.markStale()
        invalidateSubjectMask()
        saveToHistory()
    }

    private fun normalizeRotation(rotation: Float): Float {
        return ((rotation % 360f) + 360f) % 360f
    }

    /**
     * Two-finger twist (Transform tool): accumulates the gesture's RAW rotation
     * deltas into [EditorUiState.liveRotationDegrees] WITHOUT wrapping (see
     * [normalizeRotation]) so the angle can cross 0°/360° mid-gesture without
     * jumping a full turn. Only the transient live value changes — no history,
     * no geometry invalidation, no re-fold — so the canvas stays at frame rate.
     * A settle window ([commitTwistRotation]) runs the committed-path side
     * effects exactly once after the fingers stop.
     */
    fun applyTwistDelta(deltaDegrees: Float) {
        if (deltaDegrees == 0f) return
        _uiState.update { it.copy(liveRotationDegrees = it.liveRotationDegrees + deltaDegrees) }
        twistCommitJob?.cancel()
        twistCommitJob = viewModelScope.launch {
            delay(TWIST_COMMIT_SETTLE_MS)
            if (isActive) commitTwistRotation()
        }
    }

    /**
     * Folds the settled live twist into [EditorUiState.rotation] at gesture end.
     * Runs the committed-path side effects ONCE (single history entry, single
     * geometry-stale + subject-mask invalidation via [setRotation]).
     *
     * Display continuity is preserved by the baked-transform bridge: the canvas
     * shows `(rotation − compositeBakedRotation) + live`, so the moment `rotation`
     * becomes the target the remainder equals the just-cleared twist — the angle
     * never jumps while the re-fold chews, and when the fold publishes the new
     * bake the remainder returns to zero on its own (no pending-clear bookkeeping).
     */
    private fun commitTwistRotation() {
        val state = _uiState.value
        val live = state.liveRotationDegrees
        if (live == 0f) return
        val target = normalizeRotation(state.rotation + live)
        _uiState.update { it.copy(liveRotationDegrees = 0f) }
        setRotation(target)
    }

    fun flipHorizontal() {
        val state = _uiState.value
        _uiState.value = state.copy(flipHorizontal = !state.flipHorizontal)
        geometryContext.markStale()
        invalidateSubjectMask()
        saveToHistory()
    }

    fun flipVertical() {
        val state = _uiState.value
        _uiState.value = state.copy(flipVertical = !state.flipVertical)
        geometryContext.markStale()
        invalidateSubjectMask()
        saveToHistory()
    }

    /**
     * The geometry track's stale-trigger sites also invalidate a live subject
     * mask: a deforming effect (rotate / flip / crop / reshape / occluding
     * overlay / reorder-with-deformer) between the geometry base and the
     * background layer changes the pixel space the mask was segmented in, so the
     * stored `uiState.subjectMask` no longer matches what the background renders
     * over. Clearing it makes the next Background-tool entry re-segment against
     * the new fold base (selectTool(BACKGROUND) auto-detects while the mask is
     * absent) — mirroring how the shared geometry context regenerates for the
     * beauty tools. The committed BackgroundEffect keeps its own mask parameter
     * until re-entry, so a reorder never drops a layer mid-session.
     */
    private fun invalidateSubjectMask() {
        _uiState.update { it.copy(subjectMask = null, subjectDetectionFailed = false) }
    }

    fun updateCrop(rect: Rect) {
        _uiState.value = _uiState.value.copy(cropRect = rect)
    }

    fun updateInnerFrameStyle(style: FrameStyle?) {
        // A frame is an occluding overlay: changing it re-checks the shared
        // beauty geometry (docs/effect_stack.md → Which effects change geometry)
        // so downstream geometry outlines around it.
        if (style != _uiState.value.innerFrameStyle) geometryContext.markStale()
        if (style != _uiState.value.innerFrameStyle) invalidateSubjectMask()
        saveToHistory()
        _uiState.value = _uiState.value.copy(
            innerFrameStyle = style,
            innerFrameThickness = if (style == null) 0f else _uiState.value.innerFrameThickness
        )
    }

    fun updateOuterFrameStyle(style: FrameStyle?) {
        if (style != _uiState.value.outerFrameStyle) geometryContext.markStale()
        if (style != _uiState.value.outerFrameStyle) invalidateSubjectMask()
        saveToHistory()
        _uiState.value = _uiState.value.copy(
            outerFrameStyle = style,
            outerFrameThickness = if (style == null) 0f else _uiState.value.outerFrameThickness
        )
    }

    /** Live-only inner thickness change; nothing on screen but the band moves. */
    fun updateInnerFrameThickness(thickness: Float) {
        _uiState.value = _uiState.value.copy(innerFrameThickness = thickness)
    }

    /** Live-only outer thickness change; nothing on screen but the band moves. */
    fun updateOuterFrameThickness(thickness: Float) {
        _uiState.value = _uiState.value.copy(outerFrameThickness = thickness)
    }

    /** Live-only inner colour change (opaque band restamped on commit). */
    fun updateInnerFrameColor(color: Int) {
        _uiState.value = _uiState.value.copy(innerFrameColor = color)
    }

    /** Live-only outer colour change (opaque band restamped on commit). */
    fun updateOuterFrameColor(color: Int) {
        _uiState.value = _uiState.value.copy(outerFrameColor = color)
    }

    /** Live-only inner edge-size change for the ⁎ styles (0 = squared corner). */
    fun updateInnerFrameEdgeSize(edgeSize: Float) {
        _uiState.value = _uiState.value.copy(innerFrameEdgeSize = edgeSize)
    }

    /** Live-only outer edge-size change for the ⁎ styles (0 = squared corner). */
    fun updateOuterFrameEdgeSize(edgeSize: Float) {
        _uiState.value = _uiState.value.copy(outerFrameEdgeSize = edgeSize)
    }

    /** Live-only inner Custom (Curved) start-position change (−180…+180). */
    fun updateInnerFrameCustomAngle(angle: Float) {
        _uiState.value = _uiState.value.copy(innerFrameCustomAngle = angle)
    }

    /** Live-only outer Custom (Curved) start-position change (−180…+180). */
    fun updateOuterFrameCustomAngle(angle: Float) {
        _uiState.value = _uiState.value.copy(outerFrameCustomAngle = angle)
    }

    /** Live-only inner Custom (Curved) expansion change (−1…+1). */
    fun updateInnerFrameCustomCurveDistance(distance: Float) {
        _uiState.value = _uiState.value.copy(innerFrameCustomCurveDistance = distance)
    }

    /** Live-only outer Custom (Curved) expansion change (−1…+1). */
    fun updateOuterFrameCustomCurveDistance(distance: Float) {
        _uiState.value = _uiState.value.copy(outerFrameCustomCurveDistance = distance)
    }

    /** Live-only inner Custom (Curved) shaping change (0 = arc, 1 = triangle point). */
    fun updateInnerFrameCustomStrength(strength: Float) {
        _uiState.value = _uiState.value.copy(innerFrameCustomStrength = strength)
    }

    /** Live-only outer Custom (Curved) shaping change (0 = arc, 1 = triangle point). */
    fun updateOuterFrameCustomStrength(strength: Float) {
        _uiState.value = _uiState.value.copy(outerFrameCustomStrength = strength)
    }

    /** Live-only inner Custom (Curved) half-curve count change (1–10). */
    fun updateInnerFrameCustomCount(count: Float) {
        _uiState.value = _uiState.value.copy(innerFrameCustomCount = count)
    }

    /** Live-only outer Custom (Curved) half-curve count change (1–10). */
    fun updateOuterFrameCustomCount(count: Float) {
        _uiState.value = _uiState.value.copy(outerFrameCustomCount = count)
    }

    /** Live-only inner decorative line count change (1–10; 0 only via the toggle). */
    fun updateInnerFrameLines(lines: Int) {
        _uiState.value = _uiState.value.copy(innerFrameLines = lines.coerceIn(1, 10))
    }

    /** Live-only outer decorative line count change (1–10; 0 only via the toggle). */
    fun updateOuterFrameLines(lines: Int) {
        _uiState.value = _uiState.value.copy(outerFrameLines = lines.coerceIn(1, 10))
    }

    /** Live-only inner "Lines" toggle: off draws 0 lines (the count is kept for
     * re-enabling); on starts at 1 when the count is still 0. */
    fun updateInnerFrameLinesEnabled(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            innerFrameLinesEnabled = enabled,
            innerFrameLines = if (enabled && current.innerFrameLines == 0) 1 else current.innerFrameLines
        )
    }

    /** Live-only outer "Lines" toggle (mirror of [updateInnerFrameLinesEnabled]). */
    fun updateOuterFrameLinesEnabled(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            outerFrameLinesEnabled = enabled,
            outerFrameLines = if (enabled && current.outerFrameLines == 0) 1 else current.outerFrameLines
        )
    }

    /** Live-only inner line-spacing change (0–20% of the short edge). */
    fun updateInnerFrameLineDistance(distance: Float) {
        _uiState.value = _uiState.value.copy(innerFrameLineDistance = distance)
    }

    /** Live-only outer line-spacing change (0–20% of the short edge). */
    fun updateOuterFrameLineDistance(distance: Float) {
        _uiState.value = _uiState.value.copy(outerFrameLineDistance = distance)
    }

    /** Live-only inner line-thickness change (0–10% of the short edge). */
    fun updateInnerFrameLineThickness(thickness: Float) {
        _uiState.value = _uiState.value.copy(innerFrameLineThickness = thickness)
    }

    /** Live-only outer line-thickness change (0–10% of the short edge). */
    fun updateOuterFrameLineThickness(thickness: Float) {
        _uiState.value = _uiState.value.copy(outerFrameLineThickness = thickness)
    }

    fun applyCrop() {
        geometryContext.markStale()
        invalidateSubjectMask()
        saveToHistory()
    }

    fun clearCrop() {
        _uiState.update { it.copy(cropRect = null) }
    }

    fun clearTransform() {
        twistCommitJob?.cancel()
        twistCommitJob = null
        val before = _uiState.value
        _uiState.update {
            it.copy(rotation = 0f, flipHorizontal = false, flipVertical = false, liveRotationDegrees = 0f)
        }
        geometryContext.markStale()
        invalidateSubjectMask()
        stalenessForDerivedDelta(
            buildStackEffects(before, EditorRenderBundle()),
            buildStackEffects(_uiState.value, EditorRenderBundle())
        )
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
        if (mode == BackgroundMode.REPLACE) autoSelectFirstBackgroundPreset()
        _uiState.update { it.copy(backgroundMode = mode) }
        refreshPreviewLook()
    }

    fun updateBackgroundBlurRadius(radius: Float) {
        _uiState.update { it.copy(backgroundBlurRadius = radius) }
        // While dragging the radius the composite fold IS the live feedback
        // (it coalesces its own 300 ms debounce); the expensive per-tick look
        // pass is settled on the same debounce as the placement grid instead of
        // re-running a full sharpen/denoise/style + blur pass per event.
        schedulePlacementLookRefresh()
    }

    fun updateBackgroundShape(shape: BokehShape) {
        _uiState.update { it.copy(backgroundShape = shape) }
        schedulePlacementLookRefresh()
    }

    fun updateBackgroundPlacementScale(scale: Float) {
        _uiState.update {
            it.copy(
                backgroundPlacementScale = codes.pepper.whimsicalart.feature.editor.domain.matting.BackgroundReplacer
                    .clampPlacementScale(scale, it.backgroundFreeScaleUp)
            )
        }
        // The placement is part of the LIVE look (the sharpened preview renders
        // the placed replacement at the placement's CURRENT transform, like blur
        // re-renders on radius change), so a moved/scaled/rotated placement must
        // re-run the look pass — the composite-fold path re-keys on the uiState
        // change, and refreshPreviewLook keeps the fallback look in lockstep.
        schedulePlacementLookRefresh()
    }

    /** Moves the replacement placement by [panX]/[panY] (fractions of the canvas). */
    fun updateBackgroundPlacementPan(panX: Float, panY: Float) {
        _uiState.update {
            it.copy(
                backgroundPlacementPanX = panX.coerceIn(-0.5f, 0.5f),
                backgroundPlacementPanY = panY.coerceIn(-0.5f, 0.5f)
            )
        }
        schedulePlacementLookRefresh()
    }

    fun updateBackgroundPlacementRotation(degrees: Float) {
        // Wrapped into [0°, 360°) so the placement grid, the committed bitmap and
        // the rotate slider all describe the same angle (spec "Rotation wraps at
        // 360°" — same rule as stickers/text overlays).
        _uiState.update {
            it.copy(backgroundPlacementRotationDegrees = wrapDegrees(degrees))
        }
        schedulePlacementLookRefresh()
    }

    fun toggleBackgroundFreeScaleUp() {
        _uiState.update {
            val free = !it.backgroundFreeScaleUp
            it.copy(
                backgroundFreeScaleUp = free,
                backgroundPlacementScale = if (free) {
                    it.backgroundPlacementScale
                } else {
                    it.backgroundPlacementScale.coerceAtMost(1f)
                }
            )
        }
    }

    private fun resetBackgroundPlacement() {
        _uiState.update {
            it.copy(
                backgroundPlacementScale = 1f,
                backgroundPlacementPanX = 0f,
                backgroundPlacementPanY = 0f,
                backgroundPlacementRotationDegrees = 0f,
                backgroundFreeScaleUp = false
            )
        }
    }

    /**
     * Runs on-device person segmentation and stores the subject alpha mask.
     * The segmentation runs against the shared geometry context's TRACK BASE at
     * the background layer's position ([subjectMaskGeometryBase]) — the same
     * pixel space the committed [BackgroundEffect] renders over — never the raw
     * original or the resident edited bitmap. No decode happens here: the track
     * base is the already-resident original geometry image / its fold, so
     * opening the Background tool never allocates a second full-res copy.
     * Guarded by [EditorUiState.isSegmenting]; cleared on any geometry-changing
     * commit ([invalidateSubjectMask]) so a stale mask is never reused.
     */
    fun segmentSubject() {
        if (_uiState.value.isSegmenting) return
        _uiState.update { it.copy(isSegmenting = true) }
        viewModelScope.launch {
            // TWO-STAGE mask (docs/effects_reference.md → *Background*): the
            // provisional preview-res mask is resolved FIRST and published
            // immediately, so Blur/Replace reacts as soon as the first ML pass
            // lands; the full-quality geometry-track mask (adaptive 1–4 MP) is
            // then resolved in the background and swapped in for crisp edges.
            // Both go through the SAME shared geometry context, so each segment
            // × resolution is evaluated once (never the same evaluation twice).
            val source = _uiState.value.originalBitmap ?: _uiState.value.editedBitmap
            val provisionalScale = provisionalMaskScaleFor(
                source?.width ?: 0, source?.height ?: 0
            )
            val provisional = segmentMaskAt(provisionalScale)
            if (provisional != null) {
                // Publish the provisional mask straight away: the composite fold
                // + live look pick it up and the blur becomes visible while the
                // higher-quality pass is still running.
                _uiState.update { it.copy(subjectMask = provisional, error = null) }
                refreshPreviewLook()
            }
            val full = segmentMaskAt(scale = 1f)
            // The full-quality pass wins; if it failed the provisional mask is
            // still a valid subject mask (so the tool keeps working).
            val resolved = full ?: provisional
            _uiState.update {
                it.copy(
                    isSegmenting = false,
                    subjectMask = resolved,
                    subjectDetectionFailed = resolved == null
                )
            }
            if (resolved == null) {
                _uiState.update { it.copy(error = "Subject detection failed.") }
            } else {
                _uiState.update { it.copy(error = null) }
            }
            refreshPreviewLook()
        }
    }

    /**
     * Resolves the SUBJECT_MASK field through the SHARED geometry context
     * against the GEOMETRY TRACK base at the background layer's position for
     * [scale] — the same pixel space the committed [BackgroundEffect] renders
     * over (see [segmentSubject] and [subjectMaskGeometryBase]). The context
     * retains the base + resolved geometry per resolution, so a second request
     * for the same resolution after an unchanged stack reuses it without
     * re-segmenting.
     */
    private suspend fun segmentMaskAt(scale: Float): Bitmap? =
        withContext(Dispatchers.Default) {
            subjectMaskGeometryBase(scale)
                ?.let { base ->
                    geometryContext.geometryFor(
                        base,
                        geometryTracker.resolverPixelHintFor(base, backgroundGeometryEffects()),
                        setOf(GeometryField.SUBJECT_MASK)
                    ).subjectMask
                }
        }

    /**
     * The provisional two-stage mask scale: keeps the first-pass geometry base
     * inside a preview-sized "≤ ~2K" budget (`[PROVISIONAL_MASK_MAX_DIM]` on the
     * longest side; source smaller than that resolves at 1: first pass). `1f` is
     * returned when the source is not measurable.
     */
    internal fun provisionalMaskScaleFor(sourceWidth: Int, sourceHeight: Int): Float {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1f
        val maxDim = maxOf(sourceWidth, sourceHeight)
        if (maxDim <= PROVISIONAL_MASK_MAX_DIM) return 1f
        return PROVISIONAL_MASK_MAX_DIM.toFloat() / maxDim
    }

    fun clearSubjectMask() {
        _uiState.update { it.copy(subjectMask = null, subjectDetectionFailed = false) }
        refreshPreviewLook()
    }

    /**
     * Whether the on-device OCR runtime can initialize on this device/emulator.
     * Read lazily (first access probes the engine); the UI hides the
     * Recognize Text action when false.
     */
    val ocrIsAvailable: Boolean
        get() = ocrDetector.isAvailable

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
            OcrTextExtractor.overlayFragments(lines, bitmap.width, bitmap.height, bitmap)
        }
    }

    /** Loads one of the bundled (free-to-use) replacement backgrounds. */
    fun loadBackground(@androidx.annotation.DrawableRes res: Int) {
        viewModelScope.launch {
            val image = withContext(Dispatchers.IO) { decodeBackgroundDrawable(res) }
            if (image != null) {
                _uiState.update { it.copy(selectedBackgroundRes = res, backgroundImage = image) }
                refreshPreviewLook()
            }
        }
    }

    /**
     * First-time Replace auto-pick: when the user selects **Replace** and no
     * placement background has been chosen yet (no [EditorUiState.backgroundImage]
     * and no [EditorUiState.selectedBackgroundRes]), the first preset is loaded
     * so the tool enters the placement state immediately. Placement VALUES are
     * left untouched — once a background exists (preset or gallery), re-selecting
     * Replace restores that same background and its placement exactly.
     */
    private fun autoSelectFirstBackgroundPreset() {
        val state = _uiState.value
        if (state.backgroundImage != null || state.selectedBackgroundRes != null) return
        val first = BackgroundPresets.presets.firstOrNull() ?: return
        viewModelScope.launch {
            // Re-check under the coroutine: the user may already have tapped a
            // preset (or another transition) while this one was decoding.
            if (_uiState.value.backgroundImage != null || _uiState.value.selectedBackgroundRes != null) {
                return@launch
            }
            val image = withContext(Dispatchers.IO) { decodeBackgroundDrawable(first.drawableRes) }
            if (image != null) {
                _uiState.update {
                    it.copy(selectedBackgroundRes = first.drawableRes, backgroundImage = image)
                }
                refreshPreviewLook()
            }
        }
    }

    private fun decodeBackgroundDrawable(@androidx.annotation.DrawableRes res: Int): Bitmap? = runCatching {
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

    /** Uses a user-picked photo as the replacement background. */
    fun setCustomBackground(uri: Uri) {
        viewModelScope.launch {
            val image = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()?.let { cacheStore.downscale(it, 1920) }
            }
            if (image != null) {
                _uiState.update { it.copy(selectedBackgroundRes = null, backgroundImage = image) }
                refreshPreviewLook()
            }
        }
    }

    fun clearBackground() {
        _uiState.update {
            it.copy(
                subjectMask = null,
                subjectDetectionFailed = false,
                backgroundImage = null,
                selectedBackgroundRes = null
            )
        }
        resetBackgroundPlacement()
        refreshPreviewLook()
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
     * while each effect's params are refreshed in place from [stack]. Layers the
     * snapshot no longer contains are treated as removed: that layer AND the
     * whole preview tail after it are evicted (Removal recomputation), even when
     * the remaining layers' fingerprints are unchanged.
     */
    fun syncLayerState(stack: List<StackEffect>, fingerprints: Map<String, String>) {
        val current = _uiState.value
        val merged = orderedMerge(current.effectStack, stack)
        // The LAYER COMMIT is itself an undo checkpoint (docs/effect_stack.md →
        // Undo / redo): when the folded stack actually changes (a tool lost
        // focus with changed params), record a COARSE_GRAINED checkpoint BEFORE
        // the mutation so a single Undo reverts exactly this commit, and one
        // AFTER so Redo restores the committed layer. An unchanged re-entry
        // (same merged stack) records nothing — revisiting an untouched tool
        // never floods the history.
        val stackChanged = merged != current.effectStack
        if (stackChanged) saveToHistory(HistoryKind.COARSE_GRAINED)
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
        // Layered removal (spec → Removal recomputation): when the merged stack
        // dropped one or more layers (e.g. the Layers delete → clearTool →
        // lost-focus commit), every layer that came AFTER the removed one must be
        // recomputed — the remaining layers' fingerprints are unchanged, so the
        // per-layer preview tail would otherwise be reused although its input
        // changed. The removed layers and that whole tail are evicted here; the
        // next Layers open re-folds them incrementally.
        val mergedKeys = merged.mapTo(mutableSetOf()) { it.layerKey }
        val removedKeys = current.effectStack
            .asSequence()
            .filter { it.layerKey !in mergedKeys }
            .map(StackEffect::layerKey)
            .toSet()
        val tailKeys: Set<String> = if (removedKeys.isEmpty()) {
            emptySet()
        } else {
            val orderBefore = current.effectStack
                .withIndex()
                .associate { it.value.layerKey to it.index }
            val firstRemoved = removedKeys.minOf { orderBefore.getValue(it) }
            merged
                .filter { (orderBefore[it.layerKey] ?: -1) > firstRemoved }
                .map(StackEffect::layerKey)
                .toSet()
        }
        val evictionKeys = staleKeys + removedKeys + tailKeys
        _uiState.update {
            it.copy(
                effectStack = merged,
                layerThumbnails = it.layerThumbnails - evictionKeys,
                layerFingerprints = if (changedIndex == -1) it.layerFingerprints - evictionKeys
                else it.layerFingerprints - (staleKeys + removedKeys + tailKeys)
            )
        }
        if (evictionKeys.isNotEmpty()) evictLayerPreviews(evictionKeys)
        // Populate per-layer previews at COMMIT time (docs/effect_stack.md →
        // Per-layer preview cache): a committed layer must always carry its
        // preview-sized bitmap immediately, so the interactive preview fold and
        // the Layers window resume from carried previews instead of a null tail
        // (the Layers rows then pre-fill instead of blanking and lazily folding
        // one by one). The rebuild is incremental — the fingerprint fast-path
        // reuses every unchanged cached preview and only the changed layer +
        // tail are re-folded — and it cancels any earlier pass. An unchanged
        // re-entry (same merged stack, nothing evicted) does no work.
        if (stackChanged || evictionKeys.isNotEmpty()) refreshLayerPreviews()
        // Re-commit of an ALREADY-STACKED layer with changed parameters (spec →
        // Re-commit of a re-edited layer at a non-tail position): apply the same
        // position rule as a removal at that position, but flag-only — NO value
        // transfers — because the layer stays and its neighbours' images are the
        // composites of the PRE-edit stack, which a now-stale whole/prev can no
        // longer follow. The signal is the committed params themselves: the FIRST
        // stacked layer whose freshly committed value differs (fingerprint) from
        // the already-stored one. A no-change re-entry (same values — the re-
        // covered case after an earlier eviction of layerFingerprints) or a
        // first commit of a NEW layer (never in the stack) is NOT an event.
        val reCommittedIndex = merged.indexOfFirst { candidate ->
            val old = current.effectStack.firstOrNull { it.layerKey == candidate.layerKey }
            old != null && stackEffectFingerprint(old) != stackEffectFingerprint(candidate)
        }
        val reCommittedKey = merged.getOrNull(reCommittedIndex)?.layerKey
        if (reCommittedIndex != -1 && reCommittedKey != null) {
            // The derived fold keeps every already-stacked layer at its stack
            // position (orderedMerge refreshes in place), so the stacked list
            // gives the re-committed layer's exact fold position and count
            // without needing the caller's render bundle.
            val stackedIndex = current.effectStack.indexOfFirst { it.layerKey == reCommittedKey }
            propagateFullSizeRecommitStaleness(stackedIndex, current.effectStack.size)
        }
        // Post-mutation COARSE_GRAINED checkpoint so Redo restores the committed layer.
        if (stackChanged) saveToHistory(HistoryKind.COARSE_GRAINED)
        // Geometry intent is discovered+committed here (lost-focus commit, the
        // single uniform boundary for overlay/brush placement): a commit that
        // added or changed any geometry-affecting layer — sticks a sticker,
        // types text, hard-covers with pen / mosaic / object removal — marks the
        // shared beauty geometry stale, like every other deforming effect
        // (docs/effect_stack.md → Which effects change geometry; face-makeup and
        // blur-brush layers are NOT geometry-changing and stay exempt).
        val geomChanged = merged.any { e ->
            e.changesGeometry &&
                (current.layerFingerprints[e.layerKey] ?: "") != fingerprints[e.layerKey]
        }
        if (geomChanged) geometryContext.markStale()
        if (geomChanged) invalidateSubjectMask()
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
        saveToHistory(HistoryKind.COARSE_GRAINED)
        val beforeMove = _uiState.value
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
        if (hasGeomChange) invalidateSubjectMask()
        // Move = the removal rule at the lowest affected derived position
        // (the delta diff applies it exactly — a reorder that leaves the session
        // render unchanged marks nothing).
        stalenessForDerivedDelta(
            buildStackEffects(beforeMove, EditorRenderBundle()),
            buildStackEffects(_uiState.value, EditorRenderBundle())
        )
        saveToHistory(HistoryKind.COARSE_GRAINED)
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
     * unchanged Layers window does no work. Fired from the commit path
     * ([syncLayerState]) and again on Layers open — the second pass is a no-op
     * fast-path, so a committed layer always carries its preview immediately.
     */
    fun refreshLayerPreviews() {
        layerPreviewsJob?.cancel()
        layerPreviewsJob = viewModelScope.launch {
            val state = _uiState.value
            val stack = state.effectStack
            if (stack.isEmpty()) return@launch
            val dims = estimateStackFootprint(stack) ?: return@launch
            val scale = planner.previewScaleFor(dims.first, dims.second)
            if (layerPreviewScaleBucket != scale) {
                clearLayerPreviews()
                layerPreviewScaleBucket = scale
            }
            // The per-layer chain base: at preview resolution it is ALWAYS
            // `firstPreview` bucketed to the scale — `firstFull` (the pristine
            // decode, or the merged result after a merge) lohalo-downscaled to the
            // preview ceiling — never a fresh decode of the full source, and never
            // the resident `editedBitmap` directly.
            val base = planner.cachedBaseDecode(scale) ?: return@launch
            val (thumbs, fingerprints) = rebuildCommittedPreviews(stack = stack, scale = scale, base = base)
            publishLayerPreviews(thumbs, fingerprints)
        }
    }

    /** Publishes the rebuilt per-layer preview artifacts into [EditorUiState]. */
    internal fun publishLayerPreviews(thumbs: Map<String, Bitmap>, fingerprints: Map<String, String>) {
        _uiState.update {
            it.copy(
                layerThumbnails = thumbs,
                layerFingerprints = fingerprints
            )
        }
    }

    /**
     * The one incremental per-layer preview fold pass: each layer's preview is
     * the previous layer's preview folded with one more effect (byte-identical
     * to folding from the base); layers whose fingerprint is unchanged are reused
     * untouched. Every layer's result is stored BOTH in the bounded preview cache
     * ([LayerPreviewManager], dense enough for the Layers fast-path and the
     * row thumbnails) AND as the layer's own **carried** [StackEffect.committedPreview]
     * — the field the next layer's fold resumes from, and the interactive
     * preview path folds from (docs/effect_stack.md → *Per-layer preview cache*).
     *
     * Runs the whole chain from [base]; the callers (commit / Layers-window open)
     * have already evicted the stale tail (prefix-contagious), so unchanged
     * prefixes short-circuit through the fingerprint fast-path and only the
     * changed layer + tail are actually re-folded. `internal` so tests drive the
     * real production pass deterministically (no coroutine hopping).
     *
     * This pass intentionally does NOT route through the fold-to-index colour
     * controller (spec → Centralized full-size controller, "no parallel
     * whole-stack fold may remain"): it is a per-LAYER incremental builder that
     * publishes one preview bitmap PER layerKey (resuming each from the previous
     * layer's carried preview), not a whole-stack composite — so no parallel
     * whole-stack fold exists here. Whole-stack composites (the editor preview,
     * save / merge / zoom > 100%) all go through [renderCommittedBitmap] → the
     * controller; this builder is the documented layer-by-layer cache path that
     * keeps the two cache spaces (per-layer preview cache vs the controller's
     * 3-image full/preview cache) separate.
     *
     * @return the published layer thumbnails and the fingerprints map to commit.
     */
    internal suspend fun rebuildCommittedPreviews(
        stack: List<StackEffect>,
        scale: Float,
        base: Bitmap
    ): Pair<HashMap<String, Bitmap>, Map<String, String>> =
        layerPreviewManager.rebuildCommittedPreviews(
            stack = stack,
            scale = scale,
            base = base,
            renderLayerTail = { i, prevComposite ->
                renderStackWithBeautyGeometry(
                    context, base, stack.take(i + 1),
                    resumeAt = i, resumeBitmap = prevComposite,
                    adaptBackgroundArtifacts = scale < 1f,
                    scale = scale
                )
            }
        )

    /** Drops (and recycles) the per-layer preview cache and its fingerprints. */
    private fun clearLayerPreviews() {
        layerPreviewManager.clearLayerPreviews(_uiState.value.effectStack)
    }

    /** Drops (and recycles) the per-layer previews for [staleKeys]. */
    private fun evictLayerPreviews(staleKeys: Set<String>) {
        layerPreviewManager.evictLayerPreviews(_uiState.value.effectStack, staleKeys)
    }

    /** Downscales a layer's preview to the ≤100×100 Layers-row thumbnail box. */
    private fun scaleToLayerThumb(preview: Bitmap): Bitmap =
        layerPreviewManager.scaleToLayerThumb(preview)

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
            // A merge flattens every layer: record it as a coarse_grained structural
            // checkpoint (pre + post) so undo restores the pre-merge stack and
            // redo restores the merged document (docs/effect_stack.md → Undo /
            // redo → layer commit).
            saveToHistory(HistoryKind.COARSE_GRAINED)
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
                    invalidateSubjectMask()
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
                // Every tool control is reset to its default original value (a
                // merge is all-inclusive and unconditional — rotation/flips,
                // crop, frames, filters, background placement included).
                it.fullResetControls().copy(
                    editedBitmap = merged ?: it.originalBitmap,
                    baseImageSize = merged?.let { m ->
                        Size(m.width.toFloat(), m.height.toFloat())
                    } ?: it.originalBitmap?.let { o ->
                        Size(o.width.toFloat(), o.height.toFloat())
                    } ?: it.baseImageSize,
                    effectStack = listOf(MergedEffect(image = merged)),
                    hasMerged = true,
                    isLayersVisible = false,
                    layerThumbnails = mapOf(mergedKey to scaleToLayerThumb(mergedThumb)),
                    layerFingerprints = mapOf(mergedKey to mergedFp)
                )
            }
            // After the mutation: the folded stack is now empty, so repoint
            // `firstFull` to the merged result (the full-size base every further
            // fold starts from) and regenerate `firstPreview` from it — the first
            // post-merge layer folds from the post-merge preview base
            // (docs/effect_stack.md → Choosing preview resolution + The full-size
            // image model). The old references are DROPPED, never recycled: a
            // render worker may still be mid-read on them (the same rule
            // [invalidateFoldCache] follows for the clearOriginal path).
            planner.firstFullBitmap = merged
            planner.firstPreviewBitmap = null
            invalidateFoldCache()
            planner.clearScaleBucketOriginalCache()
            clearLayerPreviews()
            refreshPreviewLook()
            saveToHistory(HistoryKind.COARSE_GRAINED)
        }
    }

    /**
* Removes a single effect from the stack.  Captures undo history before and
     * after the mutation so [undo] fully restores the entry plus its editor-owned
     * parameters (adjustments, filters, beauty sub-tools, etc.).  External tool
     * state (stickers, text, pen brushes) must be released by the caller — see
     * [codes.pepper.whimsicalart.feature.editor.ui.EditorScreen] onRemove wiring.
     *
     * Removal also neutralises every editor-owned field the removed entry was
     * DERIVED from ([EditorUiState.neutralizeEffectParams] — the beauty
     * sub-tool [BeautyParams] field, the adjustment slider, transform/crop
     * state, filter/style selection, denoise, enhance, frame and background
     * params).  With those cleared, the next lost-focus commit cannot
     * resurrect the deleted layer.  The Layers window's LaunchedEffect
     * regenerates the evicted tail previews against the new (layer-less) stack.
     */
    fun removeStackEffect(id: String) {
        val current = _uiState.value
        val removedIndex = current.effectStack.indexOfFirst { it.id == id }
        if (removedIndex == -1) return
        val effect = current.effectStack[removedIndex]
        if (effect is MergedEffect) return

        // --- pre-removal snapshot (full state including editor-owned params) ---
        saveToHistory(HistoryKind.COARSE_GRAINED)
        val beforeRemoval = _uiState.value

        // Neutralise the editor-owned params behind the removed layer BEFORE
        // dropping the entry so the post-removal snapshot also carries the
        // cleared params (undo still restores the pre-removal params from its
        // earlier checkpoint). Every non-beauty layer is DERIVED from these live
        // fields, so clearing them here is what stops the next lost-focus commit
        // from resurrecting the deleted layer.
        val remaining = current.effectStack.filterNot { it.id == id }
        val removedKey = effect.layerKey
        val tailKeys = remaining.drop(removedIndex).map(StackEffect::layerKey).toSet()
        val evictionKeys = tailKeys + removedKey
        _uiState.update {
            it.neutralizeEffectParams(effect).copy(
                effectStack = remaining,
                layerThumbnails = it.layerThumbnails - evictionKeys,
                layerFingerprints = it.layerFingerprints - evictionKeys
            )
        }
        evictLayerPreviews(evictionKeys)

        // Removing a deforming/occluding layer (or anything that shifts a
        // geometry-requiring layer's base) restructures the pixel space the
        // background subject mask was segmented in: invalidate the live mask so
        // the next entry re-segments against the new fold base.
        val removalShiftsGeometry = effect.changesGeometry ||
            remaining.drop(removedIndex).any { it.changesGeometry }
        if (removalShiftsGeometry) invalidateSubjectMask()

        // --- post-removal snapshot (entry + param cleared) ---
        saveToHistory(HistoryKind.COARSE_GRAINED)
        // Fine-grained full-size staleness (rule 1-4 by the removed derived
        // position) instead of the coarse wholesale wipe — removal of the derived
        // last reuses prevImage (rule 2), anything earlier marks prev+last stale
        // (rule 3), combined with rule 4 by the cached currentPosition.
        stalenessForDerivedDelta(
            buildStackEffects(beforeRemoval, EditorRenderBundle()),
            buildStackEffects(_uiState.value, EditorRenderBundle())
        )
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
                next.copy(styleFilter = effect.filter, enabledLensFilterIds = effect.lensPassIds)
            is codes.pepper.whimsicalart.feature.editor.domain.FilterEffect -> next =
                next.copy(enabledLensFilterIds = effect.lensPassIds)
            is codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect -> next =
                next.copy(
                    backgroundMode = effect.mode,
                    subjectMask = effect.subjectMask,
                    backgroundBlurRadius = effect.blurRadius,
                    backgroundShape = effect.shape,
                    backgroundImage = effect.backgroundImage,
                    backgroundPlacementScale = effect.placementScale,
                    backgroundPlacementPanX = effect.placementPanX,
                    backgroundPlacementPanY = effect.placementPanY,
                    backgroundPlacementRotationDegrees = effect.placementRotationDegrees,
                    backgroundFreeScaleUp = effect.freeScaleUp,
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
     * Schedules the live-look pass after a background placement change,
     * cancel-and-restart debounced so a fast pan/scale/rotate drag coalesces
     * into a single trailing render. The placement uiState itself updates
     * synchronously (the grid and composite fold track the drag live); only the
     * expensive per-tick look pass is settled.
     */
    private fun schedulePlacementLookRefresh() {
        placementLookDebounce?.cancel()
        placementLookDebounce = viewModelScope.launch {
            delay(BACKGROUND_PLACEMENT_LOOK_DEBOUNCE_MS)
            refreshPreviewLook()
        }
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
                        val source = sharpnessDecodedSource ?: cacheStore.decodeFromUri(
                            uri, RenderPipelinePlanner.SEGMENT_SOURCE_MAX_DIM
                        )?.also {
                            sharpnessDecodedSource = it
                        }
                        source?.let {
                            // Preview-first: the live-look passes run on a
                            // preview-scale base, never the full bounded source.
                            // The scale is the SAME 5%-bucket the composite fold
                            // uses (previewScaleFor), so the stand-in preview always
                            // stays within the fold's display resolution — at most
                            // ~viewport × zoom, never the full 2048-bounded decode.
                            val dims = planner.sourceDims(_uiState.value.imageUri) ?: (it.width to it.height)
                            val scale = planner.previewScaleFor(dims.first, dims.second)
                            val previewW = (it.width * scale).roundToInt().coerceAtLeast(1)
                            val previewH = (it.height * scale).roundToInt().coerceAtLeast(1)
                            val base = if (previewW < it.width || previewH < it.height) {
                                Bitmap.createScaledBitmap(it, previewW, previewH, true)
                            } else {
                                it
                            }
                            var look = base
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
                                try {
                                    when (current.backgroundMode) {
                                        BackgroundMode.BLUR -> {
                                            val radiusPx = backgroundBlurRadiusPx(current.backgroundBlurRadius, look.width)
                                            look = recycleAndApply(
                                                look,
                                                BokehProcessor().applyBackgroundBlur(
                                                    look, mask, radiusPx, current.backgroundShape,
                                                    softEdgeWidth = SoftEdgeBand.DEFAULT_WIDTH
                                                )
                                            )
                                        }
                                        BackgroundMode.REPLACE -> {
                                            val image = current.backgroundImage
                                            if (image != null) {
                                                look = recycleAndApply(
                                                    look,
                                                    BackgroundReplacer.place(
                                                        look,
                                                        mask,
                                                        image,
                                                        BackgroundReplacer.PlacementTransform(
                                                            scale = current.backgroundPlacementScale,
                                                            panX = current.backgroundPlacementPanX,
                                                            panY = current.backgroundPlacementPanY,
                                                            rotationDegrees = current.backgroundPlacementRotationDegrees,
                                                            freeScaleUp = current.backgroundFreeScaleUp
                                                        ),
                                                        softEdgeWidth = SoftEdgeBand.DEFAULT_WIDTH
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    // Degrade gracefully: show the look without the
                                    // background effect rather than killing the
                                    // preview — but never SWALLOW it silently (the
                                    // background-blur preview used to no-op with no
                                    // trace when the blur radius was huge / an OOM
                                    // hit the pixel loop). Always log a warning so a
                                    // device-only failure is visible in logcat.
                                    android.util.Log.w(
                                        "WhimsicalArt",
                                        "background preview (${current.backgroundMode}) " +
                                            "failed; falling back to unblurred preview",
                                        t
                                    )
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
        // Cooperative-cancel the render passes first (leftover-memory hygiene).
        // This alone is NOT sufficient to prevent the teardown crash: Job.cancel()
        // cannot preempt a worker already inside a synchronous full-image
        // getPixel loop (e.g. the background-replace preview mid-`place()`), so
        // recycling any bitmap it may still hold would free pixels it is about to
        // read → HWUI "cannot access an invalid/free'd bitmap here!" → SIGABRT.
        // The editor screen is being torn down anyway, so the bitmaps below are
        // DROPPED WITHOUT recycling and reclaimed by GC once the running workers
        // finish (or on process death) — the same drop-without-recycle rule the
        // geometry track and invalidateFoldCache(clearOriginal) already follow.
        sharpnessJob?.cancel()
        compositeJob?.cancel()
        fullFoldJob?.cancel()
        layerPreviewsJob?.cancel()
        placementLookDebounce?.cancel()
        // The cached source is a private decode owned by this ViewModel; the
        // in-state preview bitmaps are handed to the screen and not touched here.
        sharpnessDecodedSource = null
        // Fold caches, downscaled originals / scaled artifacts, the pristine
        // decode and the regenerated geometry base are private to this ViewModel
        // but may be mid-read by one of the workers cancelled above → drop
        // without recycling (invalidateFoldCache(clearOriginal = true) never
        // recycles, by design).
        invalidateFoldCache(clearOriginal = true)
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
        visibleWidthPx: Int = planner.viewportWidthPx,
        visibleHeightPx: Int = planner.viewportHeightPx,
        zoom: Float = planner.viewZoom,
        mosaicStrokesVersion: Int = 0,
        removalStrokesVersion: Int = 0,
        blurStrokesVersion: Int = 0,
        penStrokesVersion: Int = 0
    ) {
        // An UNKNOWN viewport (the canvas has not been measured yet — onSizeChanged
        // hasn't fired) must not render anything: folding with a 0×0 default would
        // clamp previewScaleFor to its 0.05 floor and publish a tiny, heavily
        // upscaled (blurry) stand-in that nothing re-fires to replace. Instead the
        // screen keys its preview effect on the measured canvas size, so the first
        // real render waits for layout and reports an explicit viewport here.
        if (visibleWidthPx > 0 && visibleHeightPx > 0) {
            planner.viewportWidthPx = visibleWidthPx
            planner.viewportHeightPx = visibleHeightPx
        } else if (planner.viewportWidthPx <= 0 || planner.viewportHeightPx <= 0) {
            return
        }
        planner.viewZoom = zoom.coerceIn(0.5f, 5f)
        // Always record the LATEST request, even when a fold is already running
        // (that path only sets the dirty flag): the running loop re-reads it each
        // iteration, so a fast gesture stream (pan/scale/rotate/slider) converges
        // on the newest bundle instead of re-folding the one captured at launch
        // forever (the "placement image does not follow the grid" bug).
        compositeRequest = CompositeRenderRequest(
            bundle, includeTail, excludeCurrentTool, zoom, mosaicStrokesVersion, removalStrokesVersion, blurStrokesVersion, penStrokesVersion
        )
        compositeRequestSeq++
        if (compositeRunning) {
            compositeDirty = true
        } else {
            compositeRunning = true
            compositeJob = viewModelScope.launch {
                try {
                    while (isActive) {
                        val request = compositeRequest ?: break
                        val requestSeq = compositeRequestSeq
                        val dims = estimateStackFootprint(
                            foldedEffects(request.bundle, request.includeTail, request.excludeCurrentTool)
                        ) ?: break
                        val scale = planner.previewScaleFor(dims.first, dims.second)
                        val rendered = try {
                            withContext(Dispatchers.Default) {
                                renderCommittedBitmap(
                                    request.bundle, request.includeTail, request.excludeCurrentTool, scale
                                )
                            }
                        } catch (t: Throwable) {
                            // Never SWALLOW a composite-fold failure silently — that
                            // is how the device-only background render used to keep a
                            // stale, un-blurred composite on screen with no trace.
                            android.util.Log.w(
                                "WhimsicalArt",
                                "composite fold (scale=$scale, tail=${request.includeTail}, " +
                                    "exclude=${request.excludeCurrentTool}) failed; keeping previous composite",
                                t
                            )
                            null
                        }
                        if (rendered == null) {
                            android.util.Log.w(
                                "WhimsicalArt",
                                "composite fold produced no bitmap " +
                                    "(scale=$scale, tail=${request.includeTail}, exclude=${request.excludeCurrentTool})"
                            )
                        }
                        if (!isActive) break
                        // Publish the fold of the CURRENT controls only. A newer
                        // refresh (requestSeq advanced) may have landed while this
                        // render ran; an older request's fold — and the strokes
                        // version it claims — must not be presented as current
                        // (that is how a tool-excluded composite used to hide the
                        // committed strokes the moment the live-apply flip turned
                        // the overlay off: the "brush dead gap" / "blur vanishes".
                        if (rendered != null && requestSeq == compositeRequestSeq) {
                            // NOTE: the previous compositePreview is NOT recycled here.
                            // `asImageBitmap()` shares the underlying Bitmap with the
                            // on-screen Image; recycling a frame the composable may
                            // still be drawing would crash. Old frames are GC'd
                            // (matching the existing sharpenedPreview pattern).
                            _uiState.update {
                                it.copy(
                                    compositePreview = displayBitmap(
                                        rendered, request.bundle, request.includeTail, request.excludeCurrentTool
                                    ),
                                    // Stamp the transform THIS fold baked: the display
                                    // restores only the unbaked remainder on top of the
                                    // composite, so the slider/flip feedback and the
                                    // folded pixels stay in sync the moment it lands.
                                    compositeBakedRotation = it.rotation,
                                    compositeBakedFlipHorizontal = it.flipHorizontal,
                                    compositeBakedFlipVertical = it.flipVertical,
                                    publishedMosaicStrokesVersion = request.mosaicStrokesVersion,
                                    publishedRemovalStrokesVersion = request.removalStrokesVersion,
                                    publishedBlurStrokesVersion = request.blurStrokesVersion,
                                    // The pen has no live-apply round-trip: only
                                    // folds that actually include the current tool
                                    // bake the pen into the composite. A fold that
                                    // EXCLUDES it (eye OFF) must not claim the pen
                                    // folded, or the eye-ON keep-alive would drop
                                    // the overlay over a composite that lacks the
                                    // trace during the first-time fold.
                                    publishedPenStrokesVersion = if (
                                        request.includeTail && !request.excludeCurrentTool
                                    ) {
                                        request.penStrokesVersion
                                    } else {
                                        0
                                    }
                                )
                            }
                        }
                        if (!compositeDirty) break
                        compositeDirty = false
                    }
                } finally {
                    compositeRunning = false
                }
            }
        }
        // Past 100% zoom: the preview stand-in was just shown; schedule the
        // full-size fold (debounced + cancel-and-restart, so a fast pinch or
        // slider drag coalesces into one fresh fold) unless a fresh full-size
        // composite is already cached for these exact controls. Checked on EVERY
        // refresh — including while the preview fold is still running — because
        // a state change (e.g. the live-apply flip dropping the current tool from
        // the exclusion) arriving mid-render used to early-return here and leave
        // a stale full fold from the PREVIOUS controls to publish as "current".
        if (planner.viewZoom > 1f && !isFullCompositeFresh(bundle, includeTail, excludeCurrentTool)) {
            requestFullFold(
                bundle, includeTail, excludeCurrentTool, mosaicStrokesVersion, removalStrokesVersion, blurStrokesVersion, penStrokesVersion,
                compositeRequestSeq
            )
        }
    }

    /** The effect list the committed fold actually folds (tool excluded if asked). */
    private fun committedEffects(
        bundle: EditorRenderBundle,
        excludeCurrentTool: Boolean
    ): List<StackEffect> {
        val source = buildStackEffects(_uiState.value, bundle)
        return if (excludeCurrentTool) {
            effectsBeforeTool(source, _uiState.value.selectedTool, _uiState.value.selectedBeautyTool)
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
        // Tail folded OUT (eye OFF) over ZERO committed effects: `currentToolIndex`
        // is effects.size (0) and `1..0` is an empty range — the coerce would
        // throw "Cannot coerce value to an empty range". Short-circuit to an empty
        // list; the composite then falls back to the pristine preview-sized
        // original (renderCommittedBitmap's empty-effects path).
        return if (includeTail || effects.isEmpty()) {
            effects
        } else {
            effects.take((geometryTracker.currentToolIndex(effects) + 1).coerceIn(1, effects.size))
        }
    }

    /** The fold cache key for a given effect list + resolution bucket. */
    private fun foldKey(effects: List<StackEffect>, scale: Float): String =
        "${planner.quantizedScale(scale)}:" + effects.joinToString("|") { stackEffectFingerprint(it) }

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
        val last = cacheStore.fullCache.lastImage
        if (last == null || last.isRecycled) return false
        return compositeCacheIsFresh(
            includeTail = true,
            hasValidCachedImage = true,
            signatureMatches = cacheStore.fullCache.stackSignature == fullSizeKey(bundle, excludeCurrentTool)
        )
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
        val last = cacheStore.fullCache.lastImage
        if (includeTail && last != null && !last.isRecycled &&
            cacheStore.fullCache.stackSignature == fullSizeKey(bundle, excludeCurrentTool)
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
        excludeCurrentTool: Boolean,
        mosaicStrokesVersion: Int = 0,
        removalStrokesVersion: Int = 0,
        blurStrokesVersion: Int = 0,
        penStrokesVersion: Int = 0,
        requestSeq: Long = 0L
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
            // Publish only the fold of the LATEST controls. A newer refresh (e.g.
            // the live-apply flip moved excludeCurrentTool true→false while this
            // fold was mid-flight) has rescheduled a newer fold; publishing THIS
            // one would show a composite WITHOUT the just-committed brush strokes
            // while still stamping their strokesVersion as published — ordering
            // the brush overlay to hide its committed strokes over a composite
            // that does not contain them (the "blur vanishes on pinch-zoom" /
            // "brush dead gap" reports).
            if (isActive && full != null && requestSeq == compositeRequestSeq) {
                _uiState.update {
                    it.copy(
                        compositePreview = full,
                        compositeBakedRotation = it.rotation,
                        compositeBakedFlipHorizontal = it.flipHorizontal,
                        compositeBakedFlipVertical = it.flipVertical,
                        publishedMosaicStrokesVersion = mosaicStrokesVersion,
                        publishedRemovalStrokesVersion = removalStrokesVersion,
                        publishedBlurStrokesVersion = blurStrokesVersion,
                        publishedPenStrokesVersion = if (
                            includeTail && !excludeCurrentTool
                        ) {
                            penStrokesVersion
                        } else {
                            0
                        }
                    )
                }
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

    private fun saveToHistory(kind: HistoryKind = HistoryKind.FINE_GRAINED) {
        _uiState.update { current ->
            val pushed = historyStore.push(current, kind)
            val newDoc = pushed.history.last()
            // The ONE dirty-bump funnel (docs/user_guide.md → Leaving the editor):
            // a checkpoint lands → mark the document as having unsaved changes.
            // Guard the selectTool false positive: entering a tool pushes a
            // COARSE_GRAINED baseline even when nothing changed (re-tapping the
            // same tool, or the very first tap of a still-pristine session) — a
            // pushed snapshot identical to the previous checkpoint changes
            // NOTHING. `kind` is bookkeeping, never user-visible content, so the
            // comparison ignores it.
            val baseline = current.history.lastOrNull() ?: cleanBaseline
            val noActualChange = baseline.copy(kind = newDoc.kind) == newDoc
            if (noActualChange) pushed else pushed.copy(hasUnsavedChanges = true)
        }
    }

    fun undo() {
        twistCommitJob?.cancel()
        twistCommitJob = null
        val state = _uiState.value
        if (state.historyIndex > 0) {
            val prev = state.history[state.historyIndex - 1]
            _uiState.value = historyStore.restore(state, prev, state.historyIndex - 1)
                .copy(liveRotationDegrees = 0f, hasUnsavedChanges = true)
        }
        invalidateFoldCache()
        refreshPreviewLook()
    }

    fun redo() {
        twistCommitJob?.cancel()
        twistCommitJob = null
        val state = _uiState.value
        if (state.historyIndex < state.history.size - 1) {
            val next = state.history[state.historyIndex + 1]
            _uiState.value = historyStore.restore(state, next, state.historyIndex + 1)
                .copy(liveRotationDegrees = 0f, hasUnsavedChanges = true)
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
            // A successful save clears the unsaved-changes signal: the photo is
            // now on disk, so backing out must not warn. A failed save keeps it.
            if (saved) _uiState.update { it.copy(hasUnsavedChanges = false) }
            onResult(saved)
        }
    }

    fun shareImage(config: SaveConfig, bundle: EditorRenderBundle, onShared: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val shared = renderCommittedBitmap(bundle)?.let { bitmap ->
                withContext(Dispatchers.IO) {
                    imageSaver.shareImage(bitmap, config, context)
                }
            } ?: false
            _uiState.update { it.copy(isSaving = false) }
            // The share sheet launching IS the success moment (the photo is out
            // of the editor); if the chooser failed to launch the edits are NOT
            // committed anywhere, so the signal stays.
            if (shared) _uiState.update { it.copy(hasUnsavedChanges = false) }
            onShared(shared)
        }
    }

    /**
     * The COMMITTED (save/merge/share) render path — a single left-to-right fold
     * using the GEOMETRY TRACK ([renderStackWithBeautyGeometry] with
     * `scale = 1f`): every beauty layer's geometry is bound upfront by folding
     * only the geometry-changing effects up to the layer over the ORIGINAL
     * geometry image ([originalGeometryImage]), never the running colour image
     * (docs/effect_stack.md → Original geometry image and original geometry).
     * Because this is the FULL-SIZE fold, binding resolves the full-quality
     * geometry inline — the block-on-commit point (a save/merge/share awaits the
     * full geometry before rendering, while previews resolved the
     * preview-resolution geometry ahead of it). A multi-MP source folds at the
     * GEOMETRY-TRACK scale inside the adaptive 1–4 MP ML budget
     * ([geometryTrackScaleFor]) and the render loop rescales that geometry up to
     * the full-size fold it renders over, so the committed path never folds a
     * deforming effect or allocates a deformation intermediate at full size.
     * Each effect renders with the geometry it was bound (its own parameter), so
     * the fold and editor never pick geometry.
     */
    internal suspend fun renderCommittedBitmap(
        bundle: EditorRenderBundle,
        includeTail: Boolean = true,
        excludeCurrentTool: Boolean = false,
        scale: Float = 1f
    ): Bitmap? {
        // The committed list this fold folds (the current tool excluded on
        // request). A no-effect fold is the index-0 fast path of the central
        // fold-to-index controller — the base itself, no fold work.
        val effects = committedEffects(bundle, excludeCurrentTool)
        if (effects.isEmpty()) return foldToIndex(bundle, effects, 0, includeTail, scale)
        val base = planner.foldBase(scale) ?: return null
        // Preview-resolution interactive fold: resume from the nearest freshly
        // committed layer's CARRIED preview (docs/effect_stack.md → Per-layer
        // preview cache) instead of the recyclable 3-image cache — output is
        // byte-identical to folding the whole stack from the base. NOTE the fold's
        // [effects] are freshly DERIVED instances (orderedMerge) whose carried
        // field is null, so the anchor matches them by layerKey + fingerprint and
        // resumes from the LIVE stack layer that carries the preview bitmap. Falls
        // back to the controller when no fresh carried preview exists (first tool
        // of a cold session, an evicted/recycled/scale-changed prefix).
        if (scale < 1f) {
            val anchor = committedPreviewAnchor(effects, geometryTracker.currentToolIndex(effects), base.width, base.height)
            if (anchor != null) {
                val resume = _uiState.value.effectStack
                    .firstOrNull { it.layerKey == effects[anchor].layerKey }
                    ?.committedPreview
                if (resume != null && !resume.isRecycled &&
                    resume.width == base.width && resume.height == base.height
                ) {
                    return renderStackWithBeautyGeometry(
                        context, base, effects,
                        resumeAt = anchor + 1,
                        resumeBitmap = resume,
                        adaptBackgroundArtifacts = true,
                        scale = scale
                    )
                }
            }
        }
        return foldToIndex(bundle, effects, effects.size, includeTail, scale)
    }

    /**
     * The single fold entry point — **fold-to-index** (docs/effect_stack.md →
     * Centralized full-size controller and fold-to-index): folds the committed
     * [effects] up to [targetCount] effects — **index 0 = NO effect** (the base
     * itself) — at the requested [scale] (preview vs full). Every full-size
     * requester (save, merge, zoom > 100%) and the whole-stack composite fold
     * route through here so the caches populate, refresh and invalidate in one
     * consistent way.
     *
     * Rules:
     * 1. Index 0 → return the resolved base copy with no fold work.
     * 2. Same position + fresh → a cached image at the requested position
     *    (`lastImage` at the whole stack, `prevImage` at N-1, `currentImage` at
     *    its anchor) is returned immediately, no fold.
     * 3. Otherwise fold the MINIMUM effects to the target, resuming from the
     *    nearest non-stale cache (the resume/anchor logic in [renderIncremental];
     *    the rules above leave only genuinely missing or stale positions).
     *
     * Cache decisions follow the spec: the result lands in `lastImage` when the
     * request was the WHOLE stack and it was stale; `prevImage` is refreshed when
     * N-1 was rendered during the fold and it was stale; `currentImage` is stored
     * at the requested (non-0/last/last-1) index so the next request at that
     * position returns it promptly ([renderIncremental] also anchors it at `⌊N/2⌋`
     * when last-or-`N-1` is requested and it was stale). `includeTail = false`
     * is the trimmed eye-OFF mode — a legitimate NON-cached fold, not a separate
     * code path.
     */
    internal suspend fun foldToIndex(
        bundle: EditorRenderBundle,
        effects: List<StackEffect>,
        targetCount: Int,
        includeTail: Boolean = true,
        scale: Float = 1f
    ): Bitmap? {
        val base = planner.foldBase(scale) ?: return null
        // Rule 1 — index 0: the base itself, no fold work.
        if (targetCount <= 0) {
            indexZeroFoldCountForTest++
            return base.copy(Bitmap.Config.ARGB_8888, true)
        }
        if (!includeTail) {
            // Trimmed eye-OFF mode (docs/effect_stack.md): the tool-trimmed
            // prefix; its output is not a cache position in the 0/last/prev/
            // current sense, so it never populates the caches.
            return renderIncremental(
                context, base, effects, includeTail = false, scale = scale
            )
        }
        if (effects.isEmpty()) {
            // Nothing to fold — the base itself (keeps the `1..size` coerce below
            // out of the empty range).
            return base.copy(Bitmap.Config.ARGB_8888, true)
        }
        val target = targetCount.coerceIn(1, effects.size)
        val cache = if (scale >= 1f) cacheStore.fullCache else cacheStore.previewCache
        val bucketKey = planner.quantizedScale(scale)
        fun prefixSig(count: Int): String =
            "$bucketKey:" + effects.take(count).joinToString("|") { stackEffectFingerprint(it) }
        val prevCount = (effects.size - 1).coerceAtLeast(0)
        // Rule 2 — same position + fresh cache hit: return it immediately.
        when (target) {
            effects.size -> {
                if (prevCount >= 0 && cache.stackSignature == prefixSig(effects.size) &&
                    cache.lastImage != null && !cache.lastImage!!.isRecycled
                ) {
                    cacheHitCountForTest++
                    return cache.lastImage!!.copy(Bitmap.Config.ARGB_8888, true)
                }
            }
            prevCount -> {
                if (prevCount > 0 && cache.prevSig == prefixSig(prevCount) &&
                    cache.prevImage != null && !cache.prevImage!!.isRecycled
                ) {
                    cacheHitCountForTest++
                    return cache.prevImage!!.copy(Bitmap.Config.ARGB_8888, true)
                }
            }
            else -> {
                if (target > 0 && cache.anchorCount == target &&
                    cache.currentSig == prefixSig(target) &&
                    cache.currentImage != null && !cache.currentImage!!.isRecycled
                ) {
                    cacheHitCountForTest++
                    return cache.currentImage!!.copy(Bitmap.Config.ARGB_8888, true)
                }
            }
        }
        // Rule 3 — fold the minimum effects to the target, resuming from the
        // nearest non-stale cache.
        foldCallsForTest++
        return renderIncremental(
            context, base, effects, scale = scale, targetCount = target
        )
    }

    /**
     * The fine-grained, flag-only FULL-SIZE stale transition for a structural
     * change (spec → *Staleness transitions on structural changes*; the intended
     * replacement for the coarse [invalidateFoldCache] at commit/removal/move
     * sites). [derivedIndex] / [derivedCount] are the affected position and the
     * length of the PRE-change derived effect list the caches were folded
     * against. The rules:
     *  1. [tailAppend] (a new effect appended at the derived tail) → `prevImage`
     *     takes `lastImage`'s value; `lastImage` stale.
     *  2. removal of the derived last (`derivedIndex == derivedCount-1`) → the
     *     mirror transfer; `prevImage` stale.
     *  3. anything at or before the previous layer (`≤ N-2`) → both `prevImage`
     *     and `lastImage` stale.
     *  4. the affected position at/before the cached `currentPosition` → combine
     *     `currentImage` stale.
     * No render is performed and no bitmap is recycled; every same-position reuse
     * stays signature-gated in [foldToIndex], so a transition that over-preserves
     * merely re-folds — it can never serve a wrong composite.
     */
    internal fun propagateFullSizeStaleness(
        derivedIndex: Int,
        derivedCount: Int,
        tailAppend: Boolean = false
    ) {
        val cache = cacheStore.fullCache
        when {
            tailAppend -> cache.applyTailCommit()
            derivedIndex == derivedCount - 1 -> cache.applyRemoveLast()
            else -> cache.applyStaleAtOrBeforePrev()
        }
        if (derivedIndex <= cache.anchorCount) cache.applyCurrentStale()
    }

    /**
     * The commit-time counterpart of [propagateFullSizeStaleness] — RE-COMMIT
     * of an already-stacked layer (spec → *Re-commit of a re-edited layer at a
     * non-tail position*). Same position rule as if the layer at [derivedIndex]
     * were removed, but the layer STAYS and **no value transfers happen**:
     * `lastImage` ← `prevImage` / `prevImage` ← `lastImage` accompany only a
     * real removal/tail-commit. `p == N-1` → only `lastImage` stale (`prevImage`
     * is the composite before the re-edited last effect and is untouched);
     * `p ≤ N-2` → `prevImage` and `lastImage` stale; `p ≤ currentPosition` →
     * `currentImage` stale too. Callers fire this ONLY when the committed result
     * actually differs from the value already stored on the layer — a no-change
     * re-entry is not a staleness event.
     */
    internal fun propagateFullSizeRecommitStaleness(derivedIndex: Int, derivedCount: Int) {
        val cache = cacheStore.fullCache
        if (derivedIndex == derivedCount - 1) {
            cache.stackSignature = ""
        } else {
            cache.applyStaleAtOrBeforePrev()
        }
        if (derivedIndex <= cache.anchorCount) cache.applyCurrentStale()
    }

    /**
     * The index into [effects] of the nearest layer at-or-before the edited tool
     * that carries a fresh committed preview the interactive fold can resume
     * from. [effects] are freshly derived instances whose carried preview is
     * null, so the match is by layerKey + fingerprint against the live stack.
     * Returns null when nothing is resumable — the first tool of a cold session,
     * or an evicted / recycled / resolution-changed prefix.
     */
    internal fun committedPreviewAnchor(
        effects: List<StackEffect>,
        toolIndex: Int,
        baseWidth: Int,
        baseHeight: Int
    ): Int? {
        val liveCarried = _uiState.value.effectStack.associateBy(StackEffect::layerKey)
        for (j in (toolIndex.coerceAtLeast(1) - 1) downTo 0) {
            val e = effects[j]
            val live = liveCarried[e.layerKey] ?: continue
            if (stackEffectFingerprint(live) != stackEffectFingerprint(e)) continue
            val p = live.committedPreview
            if (p == null || p.isRecycled) continue
            if (p.width != baseWidth || p.height != baseHeight) continue
            return j
        }
        return null
    }

    /**
     * The ordered effect list folded over the base while [selectedTool] is being
     * edited in a "show only the preceding effects" mode: everything BEFORE the
     * tool's first stack entry. If the tool is not in the stack yet (a fresh
     * session) the whole stack is returned — there is nothing of this tool to
     * exclude, and its live data is supplied by the tool's own overlay.
     *
     * The beauty pen is ONE sub-layer (`beauty:pen`) of the multi-layer BEAUTY
     * group, but every beauty sub-layer shares `tool == EditTool.BEAUTY`. Cutting
     * at the first `EditTool.BEAUTY` effect would therefore drop ALL beauty
     * layers — including auto/smoothing that PRECEDE the pen layer — from the
     * pen's composite base. When [selectedBeautyTool] is the pen, the cut lands
     * on the first `beauty:pen` layer instead, so the base folds everything
     * before the pen (the other beauty layers stay under it).
     */
    internal fun effectsBeforeTool(
        effects: List<StackEffect>,
        selectedTool: EditTool?,
        selectedBeautyTool: BeautyTool? = null
    ): List<StackEffect> {
        if (selectedTool == null) return effects
        val idx = if (selectedTool == EditTool.BEAUTY &&
            selectedBeautyTool == BeautyTool.BRIGHTNESS_PEN
        ) {
            effects.indexOfFirst { it.layerKey == "beauty:pen" }
        } else {
            effects.indexOfFirst { it.tool == selectedTool }
        }
        return if (idx == -1) effects else effects.take(idx)
    }

    /**
     * Renders [effects] over [base] using the incremental 3-image fold cache
     * (originalImage / currentImage / prevImage / lastImage — see the field docs
     * and docs/effect_stack.md → Core model + Behaviour rules), maintained at the
     * given [scale] resolution (preview vs full — see the fold cache field docs).
     *
     * - **Prefix cache**: `currentImage` holds the composite of every effect
     *   BEFORE the currently-edited tool (`effects[0..anchorCount)`), keyed by the
     *   signature of that prefix. When the prefix is unchanged it becomes the
     *   fold start again — either directly or as the anchor a missing prefix gap
     *   is folded from.
     * - **Last-effect cache**: `prevImage` holds the composite of every effect
     *   BEFORE the last one (`effects[0..N-1)`), keyed by its signature — the base
     *   the last effect is folded over. It coincides with `currentImage` when the
     *   edited tool IS the last effect.
     * - **Full-stack cache**: `lastImage` holds the composite of the WHOLE stack
     *   (the preview result), keyed by the full-stack signature; if the whole
     *   stack is unchanged it is reused outright (the tail reapply would produce
     *   the identical bitmap).
     *
     * Base selection follows docs/effect_stack.md → *Choosing the base image*:
     * the last non-stale image from (originalImage, currentImage, prevImage) that
     * sits at-or-before the edited tool is used as the fold start, with the
     * missing prefix gap folded on top; when a stale-prefix refold for the last
     * effect is needed, `currentImage` is opportunistically re-anchored at
     * `⌊(N-1)/2⌋`. The output is always byte-identical to a full from-base fold
     * at the SAME [scale] — the cache only avoids repeating unchanged fold work,
     * and never changes the produced image.
     */
    private suspend fun renderIncremental(
        context: Context,
        base: Bitmap,
        effects: List<StackEffect>,
        toolIndex: Int = geometryTracker.currentToolIndex(effects),
        includeTail: Boolean = true,
        scale: Float = 1f,
        targetCount: Int = effects.size
    ): Bitmap {
        val cache = if (scale >= 1f) cacheStore.fullCache else cacheStore.previewCache
        // Tail folding (eye OFF on a Category A tool): show only the effects up
        // to and including the currently-edited tool, dropping everything after
        // it (the "tail"). We fold `effects.take(idx + 1)` instead of the whole
        // stack and bypass the full-stack cache (which holds the full composite).
        if (!includeTail) {
            // Empty stack: nothing to show beyond the pristine base (same empty-
            // effects contract as foldedEffects — never coerce into the empty
            // range `1..0`).
            if (effects.isEmpty()) return base.copy(Bitmap.Config.ARGB_8888, true)
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
        // `target` is the count of effects the fold stops AFTER: the composite
        // of effects[0..target). foldToIndex guarantees 1..size (index 0 short-
        // circuits before reaching here); the trim keeps naive callers sane.
        val target = targetCount.coerceIn(1, effects.size)
        val idx = toolIndex.coerceIn(0, effects.size)
        val bucketKey = planner.quantizedScale(scale)
        fun prefixSig(count: Int): String =
            "$bucketKey:" + effects.take(count).joinToString("|") { stackEffectFingerprint(it) }
        val fullTarget = target == effects.size
        val fullSig = prefixSig(effects.size)

        // Fast path: the ENTIRE stack is unchanged since the last render → the
        // cached lastImage is already the exact full composite. Return an
        // INDEPENDENT copy so the caller owns a bitmap that is never recycled
        // out from under it when the cache is invalidated (`lastImage` is a
        // cache, not a caller-owned value).
        if (fullTarget && fullSig == cache.stackSignature && cache.lastImage != null &&
            !cache.lastImage!!.isRecycled
        ) {
            return cache.lastImage!!.copy(Bitmap.Config.ARGB_8888, true)
        }

        val prevCount = (effects.size - 1).coerceAtLeast(0)
        val prevSig = prefixSig(prevCount)
        val currentTool = _uiState.value.selectedTool
        // A captured prevImage is only a stable "composite of committed effects
        // before the last one" if the edited tool does not straddle the N-1
        // boundary (a multi-layer tail tool holds live layers on both sides).
        val prevStraddledByTool = currentTool != null &&
            effects.take(prevCount).any { it.tool == currentTool } &&
            effects.drop(prevCount).any { it.tool == currentTool }

        // ---- Choose the fold start: the composite of effects[0..startCount). ----
        // The cached prefix is only usable when it sits at-or-BEFORE the target
        // (a cache beyond the target already contains effects we must not apply).
        val prefixTarget = if (idx < target) idx else target
        var startCount = 0
        var foldStart: Bitmap? = null
        var foldStartTransient = false
        val currentUsable = cache.anchorCount <= target &&
            cache.currentImage != null && !cache.currentImage!!.isRecycled &&
            cache.currentSig == prefixSig(cache.anchorCount)
        val prevUsable = target >= prevCount && prevCount > 0 &&
            cache.prevImage != null && !cache.prevImage!!.isRecycled &&
            cache.prevSig == prevSig

        when {
            // The edited tool occupies the LAST layer and a fresh prevImage is
            // cached → it IS the tool's base (folding the tool over lastImage
            // would re-apply it on top of itself).
            prevUsable && idx == prevCount && !prevStraddledByTool -> {
                foldStart = cache.prevImage
                startCount = idx
            }
            currentUsable -> {
                if (cache.anchorCount == target) {
                    foldStart = cache.currentImage
                    startCount = target
                } else {
                    // Reuse the cached currentImage and fold only the missing
                    // gap up to the fold target (<= the tool's base); the cached
                    // currentImage itself is left in place (docs → Choosing the
                    // base image).
                    foldStart = renderStackWithBeautyGeometry(
                        context, base, effects.take(prefixTarget),
                        resumeAt = cache.anchorCount, resumeBitmap = cache.currentImage,
                        adaptBackgroundArtifacts = scale < 1f,
                        scale = scale
                    )
                    foldStartTransient = true
                    startCount = prefixTarget
                }
            }
            idx == 0 -> {
                // FIRST-LAYER BASE IS UNCONDITIONAL (docs/effect_stack.md →
                // *Per-layer preview cache*): the fold starts from the pristine
                // `base` (a preview-sized copy of the original) — never a stale
                // or absent currentImage/prevImage/previous committed preview.
                // foldStart stays null so the tail fold below runs the target
                // prefix from `input` with no resume bitmap.
            }
            else -> {
                // Refold the prefix from the base. When the refold targets the
                // LAST effect, opportunistically anchor currentImage at the
                // stack's middle (⌊(N-1)/2⌋) so a later mid-stack edit reuses it.
                val mid = if (idx == prevCount && target >= prevCount) {
                    (prevCount / 2).coerceAtLeast(1)
                } else {
                    -1
                }
                if (mid in 1 until prefixTarget) {
                    val midOut = renderStackWithBeautyGeometry(
                        context, base, effects.take(mid),
                        adaptBackgroundArtifacts = scale < 1f,
                        scale = scale
                    )
                    cache.currentImage?.takeUnless(Bitmap::isRecycled)?.recycle()
                    cache.currentImage = midOut.copy(Bitmap.Config.ARGB_8888, true)
                    cache.anchorCount = mid
                    cache.currentSig = prefixSig(mid)
                    foldStart = renderStackWithBeautyGeometry(
                        context, base, effects.take(prefixTarget),
                        resumeAt = mid, resumeBitmap = midOut,
                        adaptBackgroundArtifacts = scale < 1f,
                        scale = scale
                    )
                    foldStartTransient = true
                    if (foldStart !== midOut && !midOut.isRecycled) midOut.recycle()
                    startCount = prefixTarget
                } else {
                    foldStart = renderStackWithBeautyGeometry(
                        context, base, effects.take(prefixTarget),
                        adaptBackgroundArtifacts = scale < 1f,
                        scale = scale
                    )
                    cache.currentImage?.takeUnless(Bitmap::isRecycled)?.recycle()
                    cache.currentImage = foldStart.copy(Bitmap.Config.ARGB_8888, true)
                    cache.anchorCount = prefixTarget
                    cache.currentSig = prefixSig(prefixTarget)
                    foldStartTransient = true
                    startCount = prefixTarget
                }
            }
        }

        // ---- Fold the tail from the fold start up to the target, capturing
        // prevImage when the pass renders the N-1 composite (and it is stale). ----
        val resumeBitmap = if (startCount == 0) null else foldStart
        val capturePrev = !prevStraddledByTool && prevCount > 0 &&
            cache.prevSig != prevSig && target >= prevCount
        var result: Bitmap
        // A slot replace must never recycle a pixel buffer another slot still
        // references: a fine-grained stale transition (spec → Staleness
        // transitions on structural changes) can make `prevImage` and `lastImage`
        // alias the same composite for a while, and recycling that buffer out
        // from under the aliased slot would poison it. Replace = drop the
        // reference; GC reclaims it once every holder lets go.
        fun recycleSlotReplace(old: Bitmap?) {
            if (old != null && !old.isRecycled &&
                old !== cache.prevImage && old !== cache.lastImage && old !== cache.currentImage
            ) {
                old.recycle()
            }
        }
        if (capturePrev && startCount < prevCount) {
            val midPass = renderStackWithBeautyGeometry(
                context, base, effects.take(prevCount),
                resumeAt = startCount, resumeBitmap = resumeBitmap,
                adaptBackgroundArtifacts = scale < 1f,
                scale = scale
            )
            recycleSlotReplace(cache.prevImage)
            cache.prevImage = midPass.copy(Bitmap.Config.ARGB_8888, true)
            cache.prevSig = prevSig
            result = if (fullTarget) {
                renderStackWithBeautyGeometry(
                    context, base, effects.take(target),
                    resumeAt = prevCount, resumeBitmap = midPass,
                    adaptBackgroundArtifacts = scale < 1f,
                    scale = scale
                )
            } else {
                midPass
            }
            if (result !== midPass && !midPass.isRecycled) midPass.recycle()
        } else {
            if (capturePrev && startCount == prevCount && target >= prevCount) {
                recycleSlotReplace(cache.prevImage)
                cache.prevImage = foldStart!!.copy(Bitmap.Config.ARGB_8888, true)
                cache.prevSig = prevSig
            }
            result = renderStackWithBeautyGeometry(
                context, base, effects.take(target),
                resumeAt = startCount, resumeBitmap = resumeBitmap,
                adaptBackgroundArtifacts = scale < 1f,
                scale = scale
            )
        }
        if (fullTarget) {
            recycleSlotReplace(cache.lastImage)
            cache.lastImage = result.copy(Bitmap.Config.ARGB_8888, true)
            cache.stackSignature = fullSig
        } else if (target != prevCount && cache.anchorCount != target) {
            // Rule 4 — store currentImage at the requested mid-stack index so a
            // next request at that position returns it without regenerating.
            cache.currentImage?.takeUnless(Bitmap::isRecycled)?.recycle()
            cache.currentImage = result.copy(Bitmap.Config.ARGB_8888, true)
            cache.anchorCount = target
            cache.currentSig = prefixSig(target)
        }
        val out = result.copy(Bitmap.Config.ARGB_8888, true)
        // `result` is a transient fresh fold output UNLESS it aliases the
        // caller/cache-owned start bitmaps in the all-noop-tail degenerate case;
        // recycle only the fresh intermediate we are not handing out.
        if (result !== base && result !== foldStart && !result.isRecycled) result.recycle()
        if (foldStartTransient && result !== foldStart && !foldStart!!.isRecycled) {
            foldStart!!.recycle()
        }
        return out
    }

    /** The pristine full-size source the geometry track is grounded in. */
    private val pristineOriginal: Bitmap?
        get() = planner.cachedOriginalDecode() ?: _uiState.value.originalBitmap

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
     * The geometry-changing effects of the stack UP TO the background layer's
     * position — the shared geometry context's base source for the subject mask
     * (docs/effect_stack.md → Shared geometry context (beauty + background)):
     * same geometry-track fold the beauty tools use, so a mask is never taken
     * over the raw original/resident bitmap. When a committed background layer
     * exists the position is that layer's index (its mask re-resolves against
     * the same base it renders over); otherwise the background is treated as
     * landing at the END of the current stack, so the mask covers whatever
     * deformation the run will already contain. Made `internal` so the fold-vs-
     * position contract is unit-testable.
     */
    internal fun backgroundGeometryEffects(): List<StackEffect> =
        geometryTracker.backgroundGeometryEffects()

    /**
     * The full-size geometry-track base the subject mask must be segmented
     * against: the geometry-changing effects up to the background layer's
     * position folded over the original geometry image — at the geometry-track
     * scale ([geometryTrackScaleFor]) when [scale] ≥ 1, so a multi-MP source is
     * folded inside the adaptive 1–4 MP ML budget (never at full size). This is
     * exactly the pixel space the committed [BackgroundEffect] renders in (the
     * render loop rescales the resolved geometry up to that footprint), so a
     * prior crop/rotate needs no inverse mapping of the mask. `internal` for
     * tests.
     */
    internal suspend fun subjectMaskGeometryBase(scale: Float): Bitmap? {
        val effects = backgroundGeometryEffects()
        val geometryScale = if (scale >= 1f) geometryTrackScaleFor(effects) else scale
        return geometryTrackBase(effects, geometryScale)
    }

    /**
     * The GEOMETRY-TRACK fold scale for [geomEffects] on the FULL-SIZE path:
     * `1` for an at/under-budget footprint, otherwise the uniform
     * `sqrt(cap / footprint)` scale that keeps the folded segment base inside the
     * adaptive 1–4 MP ML budget (policy and KDoc in [GeometryTracker] — this
     * ViewModel delegate is kept for the unit tests).
     */
    @androidx.annotation.VisibleForTesting
    internal fun geometryTrackScaleFor(geomEffects: List<StackEffect>): Float =
        geometryTracker.geometryTrackScaleFor(geomEffects)

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
        geometryTrackBaseCallsForTest++
        val bucket = planner.quantizedScale(scale)
        val key = "$bucket:" + geomEffects.joinToString("|") { stackEffectFingerprint(it) }
        synchronized(geometryTrackLock) {
            geometryTrackCache[key]?.takeUnless(Bitmap::isRecycled)?.let { return it }
        }
        if (geomEffects.isEmpty()) return geometryTracker.geometryTrackSegment0Base(scale)
        val prev = geometryTrackBase(geomEffects.dropLast(1), scale) ?: return null
        val last = geomEffects.last()
        val renderable = if (last is BeautyStackEffect) {
            // The resolver's ML-input reference is the base's OWN pixel count
            // when the base is already at/under the ML cap (every preview base
            // and every capped committed/zoom>100% base — the geometry-track
            // cap in [geometryTrackScaleFor]): ML then runs the FULL base, so
            // the preview and the capped full-quality passes detect at the same
            // input resolution. Only a full-size base above the cap (the merge
            // re-ground) falls back to the full-size footprint estimate.
            val hint = geometryTracker.resolverPixelHintFor(prev, geomEffects)
            last.withGeometry(geometryContext.geometryFor(prev, hint, last.neededFields))
                .withProcessor(beautyProcessor)
        } else {
            last
        }
        val base = renderable.render(prev, context)
        synchronized(geometryTrackLock) {
            if (geometryTrackCache.size >= GEOMETRY_TRACK_CACHE_MAX) {
                // Drop the map WITHOUT recycling: a concurrent fold may still hold
                // an entry (recycling it would make that fold draw recycled
                // pixels — the silent-death path). The bounded size is preserved;
                // GC reclaims the dropped intermediates once the folds release
                // them.
                geometryTrackCache.clear()
            }
            geometryTrackCache[key] = base
        }
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
     * preview-resolution base, a full/committed fold against the capped
     * full-quality base (the full-size footprint folded inside the adaptive
     * 1–4 MP budget — [geometryTrackScaleFor] — and the render loop rescales
     * the resolved geometry up to the pixels it renders over; the committed
     * render awaits it: block-on-commit). The editor stays geometry-agnostic —
     * it hands bases through the [geometryBaseSourceFor] callback and lets the
     * context decide reuse vs resolve.
     */
    private suspend fun bindBeautyGeometries(
        effects: List<StackEffect>,
        scale: Float
    ): List<StackEffect> {
        val source = geometryBaseSourceFor(effects)
        return bindBeautyGeometries(effects, scale) { i, s -> source.geometryBase(i, s) }
    }

    /**
     * Binds every beauty layer's geometry from the geometry track. [base] hands
     * the track base at each beauty layer's index (see [geometryBaseSourceFor]);
     * made `internal`-testable so the per-layer failure contract can be locked
     * down (a null base degrades ONLY that layer).
     */
    internal suspend fun bindBeautyGeometries(
        effects: List<StackEffect>,
        scale: Float,
        base: suspend (Int, Float) -> Bitmap?
    ): List<StackEffect> {
        if (effects.none { it is BeautyStackEffect }) return effects
        val out = ArrayList<StackEffect>(effects.size)
        for (i in effects.indices) {
            val e = effects[i]
            if (e is BeautyStackEffect) {
                val geomEffects = effects.take(i).filter { it.changesGeometry }
                // The FULL-SIZE (committed / zoom>100% / save) fold runs at the
                // geometry-track scale ([geometryTrackScaleFor]) — a multi-MP
                // footprint folds inside the adaptive 1–4 MP ML budget instead
                // of at full size, so no full-size deformation intermediate is
                // ever allocated on this path. Preview-scale binds (this [scale]
                // < 1) and the merge re-ground keep their scale unchanged.
                val usedScale = if (scale >= 1f) geometryTrackScaleFor(geomEffects) else scale
                val trackBase = base(i, usedScale)
                if (trackBase == null) {
                    // Per-layer failure handling: a null track base (the segment-0
                    // original geometry image recycled/absent, or a folded segment
                    // that failed to render) degrades ONLY this layer to identity —
                    // layers already bound keep their geometry and the REST of the
                    // list keeps folding. Never return the whole unbound list here:
                    // that silently turned EVERY beauty layer of the fold into
                    // `current` (identity), visually "some/all beauty effects not
                    // applying at all".
                    android.util.Log.w(
                        "WhimsicalArt",
                        "beauty geometry track base null at index $i (scale $usedScale); " +
                            "layer degrades to identity, remaining layers still bind"
                    )
                    out += e
                } else {
                    // The hint is the capped base's OWN pixel count, so the plane
                    // of the resolved geometry MATCHES the base the render loop
                    // will rescale it from/into (see the render-loop rescale in
                    // renderStackWithBeautyGeometry): preview and capped passes
                    // detect at the same input resolution. The base dims are
                    // recorded on the geometry so the full-size colour fold can
                    // rescale a capped-resolution geometry up to the image it
                    // actually renders over.
                    val geometry = geometryContext
                        .geometryFor(trackBase, geometryTracker.resolverPixelHintFor(trackBase, geomEffects), e.neededFields)
                        .copy(baseWidth = trackBase.width, baseHeight = trackBase.height)
                    out += e.withGeometry(geometry)
                }
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
        // The first-layer base is UNCONDITIONAL: when the fold starts at position
        // 0 (no preceding layer) it is always [input] (the pre-resolved pristine
        // base — [cachedBaseDecode] downscaled or the merged base), never a stale
        // or absent cached composite. A resume bitmap that has been recycled by a
        // racing cache owner (invalidateFoldCache / the other resolution cache)
        // must not seed the fold — its first effect would read the recycled
        // bitmap's `width` and crash. Drop the resume and fold the whole list
        // from [input] instead.
        val resumeIsLive = resumeBitmap != null && !resumeBitmap.isRecycled
        val startIndex = if (!resumeIsLive) 0 else resumeAt.coerceIn(0, effects.size)
        val start = if (startIndex == 0) input else resumeBitmap!!
        var current = start
        for (i in startIndex until effects.size) {
            if (current.isRecycled) {
                // Never feed a recycled bitmap to an effect: fall back to the
                // pristine [input] so the first effect still has a live base.
                // A recycled [input] itself is a broken caller contract — return
                // it rather than loop forever.
                if (current === input) return input
                current = input
            }
            val effect = resolved[i]
            val oriented = if (adaptBackgroundArtifacts && effect is BackgroundEffect) {
                geometryTracker.adaptBackgroundTo(current, effect)
            } else {
                effect
            }
            val toRender = if (oriented is BeautyStackEffect) {
                // Geometry is already bound by the geometry track; only the single
                // injected BeautyProcessor is handed to the effect so its render
                // never constructs its own processor. The FULL-SIZE fold renders
                // the capped geometry-track pass (see bindBeautyGeometries): the
                // geometry was resolved against the capped base (≤ 4 MP) and its
                // recorded base dims differ from [current]'s full-size footprint,
                // so it is rescaled HERE to cover exactly the pixels this fold
                // renders. Preview folds already run at the base's footprint and
                // are untouched. An unanchored/legacy geometry (baseWidth == 0)
                // is left as-is.
                val geometry = oriented.geometry
                val footprintAligned = if (
                    geometry != null && geometry.baseWidth > 0 && geometry.baseHeight > 0 &&
                    (geometry.baseWidth != current.width || geometry.baseHeight != current.height)
                ) {
                    oriented.withGeometry(geometry.rescaledTo(current.width, current.height))
                } else {
                    oriented
                }
                footprintAligned.withProcessor(beautyProcessor)
            } else {
                oriented
            }
            val next = toRender.render(current, context)
            if (next == null || next.isRecycled) continue
            if (next !== current && current !== start && current !== input && !current.isRecycled) {
                current.recycle()
            }
            current = next
        }
        return current
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
        if (clearOriginal) {
            // The photo itself changes (or the ViewModel is being torn down):
            // every cached intermediate is stale AND an in-flight render pass may
            // still be mid-read on any of them — a worker inside a full-image
            // getPixel/putPixel loop cannot be preempted by Job.cancel(). So drop
            // EVERY bitmap reference WITHOUT recycling (the drop-without-recycle
            // rule already in place for geometryTrackCache) and let GC reclaim the
            // pixels once the running passes release them. Recycling out from
            // under an in-loop worker frees bytes it is about to read → HWUI
            // "cannot access an invalid/free'd bitmap here!" → SIGABRT, the
            // editor-teardown crash (/tmp/next.md).
            cacheStore.fullCache.dropWithoutRecycle()
            cacheStore.previewCache.dropWithoutRecycle()
            // The original decode, the named full-size base and its preview
            // counterpart describe the OLD photo: the planner drops all of them
            // (without recycling — an in-flight fold may still hold a reference)
            // and the next base resolution regenerates them.
            planner.clearOriginalCaches()
            geometryTracker.clearScaledArtifactCache()
            // The geometry track is grounded in the old photo: a fresh photo gets
            // a fresh reference-base and a cleared segment cache. Dropped without
            // recycling — an in-flight fold coroutine may still hold an entry;
            // GC reclaims them once the folds release them (see geometryTrackBase).
            synchronized(geometryTrackLock) {
                geometryTrackCache.clear()
            }
            // The regenerated base (if any) is an independent instance; drop it
            // without recycling for the same in-flight-fold reason as above.
            regeneratedGeometryImage = null
        } else {
            cacheStore.fullCache.clear()
            cacheStore.previewCache.clear()
        }
        planner.clearFootprintMemo()
    }

    /**
     * The fine-grained structural-change counterpart of [invalidateFoldCache]:
     * diffs the derived fold lists (session render order) before/after a
     * commit/removal/move and applies the spec's positional stale transition to
     * the FULL-SIZE cache only:
     *  - the derived lists match → NO cache is marked (a stack reorder that ends
     *    up with the same session render leaves every composite valid);
     *  - a pure tail append → rule 1 (the `prevImage` ← `lastImage` value
     *    transfer);
     *  - removal of the derived last → rule 2 (the mirror transfer);
     *  - any change at or before the previous layer → rule 3 (prev+last stale),
     *    combined with rule 4 when the affected position is at/before the cached
     *    `currentPosition`.
     * The preview-resolution cache is always cleared cheaply — per the spec,
     * preview scale carries NO stale flags and re-folds on demand. Never recycles
     * full-size intermediates; correctness is preserved by the signature-gated
     * same-position reuse in [foldToIndex].
     */
    private fun stalenessForDerivedDelta(
        before: List<StackEffect>,
        after: List<StackEffect>
    ) {
        cacheStore.previewCache.clear()
        planner.clearFootprintMemo()
        val beforeSigs = before.map(::stackEffectFingerprint)
        val afterSigs = after.map(::stackEffectFingerprint)
        if (beforeSigs == afterSigs) return
        val delta = deriveStalenessDelta(beforeSigs, afterSigs)
        propagateFullSizeStaleness(delta.firstDiffIndex, before.size, delta.tailAppend)
    }

    // The base/footprint/resolution policy moved to [RenderPipelinePlanner]
    // ([RenderPipelinePlanner.foldBase] resolves a render pass's fold base; base
    // decode / preview bases / ceiling / scale math all live there). The
    // ViewModel keeps the state-dependent START-footprint resolver and the
    // screen-facing setters/hooks below.

    /** Memoized estimated composite footprint for the current fold list (see the planner). */
    private fun estimateStackFootprint(effects: List<StackEffect>): Pair<Int, Int>? =
        planner.estimateStackFootprint(effects) {
            val state = _uiState.value
            state.editedBitmap?.takeIf { state.hasMerged && !it.isRecycled }
                ?.let { it.width to it.height }
                ?: planner.sourceDims(_uiState.value.imageUri)
        }

    /**
     * Tracks the on-screen canvas size (px) so the preview-resolution computation
     * knows the visible footprint. Called by the screen on `onSizeChanged`.
     */
    fun setViewBox(widthPx: Int, heightPx: Int) {
        planner.setViewBox(widthPx, heightPx)
    }

    /** Current stored canvas viewport (px); (0, 0) = not yet measured. Test hook. */
    internal fun currentViewportPx(): Pair<Int, Int> =
        planner.viewportWidthPx to planner.viewportHeightPx

    /** Tracks the display zoom so a >100% zoom triggers a full-size fold. */
    fun setZoom(zoom: Float) {
        planner.setZoom(zoom)
    }

    /** The preview-resolution scale for the current viewport/zoom (see the planner). */
    internal fun previewScaleFor(sourceWidth: Int, sourceHeight: Int): Float =
        planner.previewScaleFor(sourceWidth, sourceHeight)

    /** Test hook: the fixed, orientation-invariant preview ceiling (px). */
    internal fun previewCeilingPx(): Int = planner.previewCeilingPx()

    /** Test hook: the full-size fold cache (fields + stale flags). */
    internal fun fullSizeFoldCacheForTest(): FoldImageCache = cacheStore.fullCache

    /** Test hook: the preview-resolution fold cache (fields + stale flags). */
    internal fun previewFoldCacheForTest(): FoldImageCache = cacheStore.previewCache

    /** Test hook: the always-present preview base (`firstPreview`). */
    internal fun firstPreviewForTest(): Bitmap? = planner.firstPreviewBase()

    /** Test hook: the full-size fold base (`firstFull`). */
    internal fun firstFullForTest(): Bitmap? = planner.firstFullBase()

    /**
     * Test hook: the "single point of entrance" probe — every call to the
     * fold-to-index controller fires exactly one of its three rule counters, so
     * this sum is the number of times a colour composite entered the controller.
     */
    internal fun foldControllerEntryCallsForTest(): Int =
        indexZeroFoldCountForTest + cacheHitCountForTest + foldCallsForTest

    private companion object {
        const val FULL_FOLD_DEBOUNCE_MS = 500L
        // Idle window after the last twist event before the accumulated live
        // rotation is committed to the stack (the "gesture end" settle). While
        // the fingers move, [applyTwistDelta] keeps restarting this window.
        const val TWIST_COMMIT_SETTLE_MS = 250L
        // Longest side of the two-stage background mask's PROVISIONAL
        // (preview-res) fold (docs/effects_reference.md → *Background*): the
        // first subject-mask pass runs at ≤ this budget and is published
        // immediately; the full-quality geometry-track pass (adaptive 1–4 MP)
        // then refines it.
        const val PROVISIONAL_MASK_MAX_DIM = 2048
        // Placement drag settle window: while the user drags the replacement
        // grid the uiState updates per tick but the live-look pass is held off
        // until the gesture has been still for this long (the composite fold and
        // the placement grid are the live feedback).
        const val BACKGROUND_PLACEMENT_LOOK_DEBOUNCE_MS = 120L
        const val GEOMETRY_TRACK_CACHE_MAX = 32
        // Nominal source width used to normalise the beauty-pen brush size when no
        // image is loaded (tests only; real use always has a decoded original).
        const val DEFAULT_PEN_BASE_WIDTH = 2000f
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
        val hasFilterLook = bundle.filterMatrix != null || bundle.styleFilter != null ||
            bundle.lensPassIds.isNotEmpty()
        if (hasFilterLook) {
            if (bundle.styleFilter == null) {
                list += FilterEffect(
                    filterMatrix = bundle.filterMatrix,
                    lensPassIds = bundle.lensPassIds
                )
            } else {
                // Filters is a SINGLE effect (docs/effects_reference.md →
                // *Filters*): a selected style carries the lens/color matrix and
                // real-pass lens ids along with it under the same `layer:FILTER`.
                list += StyleEffect(
                    filter = bundle.styleFilter,
                    filterMatrix = bundle.filterMatrix,
                    lensPassIds = bundle.lensPassIds
                )
            }
        }
        if (state.skinDenoise != 0f) list += SkinDenoiseEffect(intensity = state.skinDenoise)
        if (state.enhanceEnabled) list += EnhanceEffect(enabled = true)
        // The background is the only committed effect whose params are NOT
        // re-derived from scalar state but from a raster artifact (the subject
        // mask). That mask lives in two places: the LIVE uiState.subjectMask
        // (segmented on every Background-tool entry) and the COMMITTED
        // BackgroundEffect's own subjectMask. A geometry-changing commit (pen
        // stroke, rotate, frame, …) invalidates the LIVE mask (see
        // invalidateSubjectMask) so re-entry re-segments against the new fold
        // base — but that must NEVER drop the already-committed background:
        // the committed layer is self-sufficient (it carries its mask until
        // re-entry restores the live one, EditorViewModel.kt:1224-1225), so it
        // is emitted from the committed stack whenever the live mask is absent.
        // Without this fallback, orderedMerge would delete the layer from the
        // effectStack and the background would vanish from preview AND saved
        // output (docs: background removal + pen stroke report).
        val backgroundEffect: BackgroundEffect? =
            bundle.background?.let { bg ->
                BackgroundEffect(
                    mode = bg.mode,
                    subjectMask = bg.subjectMask,
                    blurRadius = bg.blurRadius,
                    shape = bg.shape,
                    backgroundImage = bg.backgroundImage,
                    placementScale = bg.placementScale,
                    placementPanX = bg.placementPanX,
                    placementPanY = bg.placementPanY,
                    placementRotationDegrees = bg.placementRotationDegrees,
                    freeScaleUp = bg.freeScaleUp
                )
            } ?: state.effectStack.filterIsInstance<BackgroundEffect>().lastOrNull()
        if (backgroundEffect != null) list += backgroundEffect
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
    is FilterEffect -> "filter:${effect.filterMatrix?.contentHashCode() ?: -1}:" +
        "${effect.lensPassIds.sorted()}"
    is EnhanceEffect -> "enhance:${effect.enabled}"
    is StyleEffect -> "style:${effect.filter?.id ?: "null"}:" +
        "${effect.filterMatrix?.contentHashCode() ?: -1}:${effect.lensPassIds.sorted()}"
    is BackgroundEffect -> "bg:${effect.mode}:${effect.blurRadius}:" +
        "${effect.shape}:${effect.subjectMask != null}:${effect.backgroundImage?.hashCode() ?: -1}:" +
        "${effect.placementScale}:${effect.placementPanX}:${effect.placementPanY}:" +
        "${effect.placementRotationDegrees}:${effect.freeScaleUp}"
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
        if (refreshed != null) {
            // Preserve the carried committed preview across the merge for an
            // UNCHANGED layer (same fingerprint): the derived instance is fresh
            // with a null carried field, so without this every commit would
            // null-carry the ENTIRE live stack and force the next preview fold
            // to re-fold the tail (docs/effect_stack.md → Per-layer preview
            // cache — "a preview-sized image always exists ... for every layer
            // up to and including the last committed one"). The [committedPreview]
            // bitmap is a session cache, never a parameter, so sharing the
            // reference between the old and refreshed instances is safe.
            if (stackEffectFingerprint(e) == stackEffectFingerprint(refreshed)) {
                val carried = e.committedPreview
                if (carried != null && !carried.isRecycled) {
                    refreshed.committedPreview = carried
                }
            }
            merged += refreshed
        }
    }
    // Append any newly-active effects (fresh tools) not already represented.
    val present = merged.mapTo(mutableSetOf()) { it.layerKey }
    for (e in derived) {
        if (present.add(e.layerKey)) merged += e
    }
    return merged
}

// Squared euclidean distance between two beauty-pen sample points (PointF has no
// arithmetic operators). Keeps the "skip points closer than half the brush
// radius" spacing check resolution-independent.
private fun distanceSquared(a: PointF, b: PointF): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}
