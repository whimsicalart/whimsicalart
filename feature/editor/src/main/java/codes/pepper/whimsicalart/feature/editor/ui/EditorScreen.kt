package codes.pepper.whimsicalart.feature.editor.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.core.domain.GeometryAnchor
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.EditorColorMatrix
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundLayer
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode
import codes.pepper.whimsicalart.feature.editor.domain.FrameLayer
import codes.pepper.whimsicalart.feature.editor.domain.FrameTier
import codes.pepper.whimsicalart.feature.editor.domain.SaveConfig
import codes.pepper.whimsicalart.feature.editor.domain.TextBackgroundShape
import codes.pepper.whimsicalart.feature.editor.domain.StrokeLayer
import codes.pepper.whimsicalart.feature.editor.domain.PenBrushKind
import codes.pepper.whimsicalart.feature.editor.domain.StrokeType
import codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
import codes.pepper.whimsicalart.feature.editor.domain.PositionalReconcilers
import codes.pepper.whimsicalart.feature.editor.domain.toFramePixels
import codes.pepper.whimsicalart.feature.editor.domain.toBeautyEffects
import codes.pepper.whimsicalart.feature.editor.domain.TextItem
import codes.pepper.whimsicalart.feature.editor.domain.renderEmojiGlyph
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter
import codes.pepper.whimsicalart.feature.editor.domain.ocr.OcrOverlayFragment
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.filters.domain.FilterPresets
import codes.pepper.whimsicalart.feature.stickers.domain.EMOJI_STICKER_BASE_SIZE_DP
import codes.pepper.whimsicalart.feature.stickers.domain.StickerItem
import codes.pepper.whimsicalart.feature.stickers.domain.StickerLineStyle
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPlacement
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPresets
import codes.pepper.whimsicalart.feature.stickers.domain.baseSizeDp
import codes.pepper.whimsicalart.feature.stickers.domain.toStickerItem
import codes.pepper.whimsicalart.feature.stickers.domain.toStickerPlacement
import codes.pepper.whimsicalart.feature.stickers.ui.DEFAULT_LINE_COLOUR
import codes.pepper.whimsicalart.feature.stickers.ui.StickerPickerPanel
import codes.pepper.whimsicalart.feature.stickers.ui.viewmodel.StickersViewModel
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurBrushOverlay
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurBrushPanel
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurStroke
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurViewModel
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicOverlay
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicPanel
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicStroke
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicViewModel
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenBrushPanel
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenBrushType
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenStroke
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenStrokeOverlay
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenViewModel
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalOverlay
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalPanel
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalStroke
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalViewModel
import codes.pepper.whimsicalart.feature.editor.ui.background.BackgroundPanel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextEditorPanel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextEditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextOverlay
import codes.pepper.whimsicalart.feature.editor.ui.text.TextOverlayLayer
import codes.pepper.whimsicalart.feature.editor.ui.text.toTextItem
import codes.pepper.whimsicalart.feature.editor.ui.text.toTextOverlay
import codes.pepper.whimsicalart.feature.editor.ui.frames.FrameControls
import codes.pepper.whimsicalart.feature.editor.ui.frames.FramePreview
import codes.pepper.whimsicalart.feature.editor.ui.frames.outerPreviewExtent
import codes.pepper.whimsicalart.feature.editor.ui.layers.LayersPanel
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyBrushOverlay
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyControls
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyTool
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.domain.MergedEffect
import codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect
import codes.pepper.whimsicalart.feature.editor.domain.EnhanceEffect
import codes.pepper.whimsicalart.feature.editor.domain.SkinDenoiseEffect
import codes.pepper.whimsicalart.feature.editor.domain.StyleEffect
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect
import codes.pepper.whimsicalart.feature.editor.domain.FilterEffect
import codes.pepper.whimsicalart.feature.editor.domain.BitmapDiffEffect
import codes.pepper.whimsicalart.feature.editor.domain.StickerEffect
import codes.pepper.whimsicalart.feature.editor.domain.TextEffect
import codes.pepper.whimsicalart.feature.editor.domain.FrameEffect
import codes.pepper.whimsicalart.feature.editor.domain.BitmapRenderer
import codes.pepper.whimsicalart.feature.editor.domain.StickerOverlayRenderer
import codes.pepper.whimsicalart.feature.editor.domain.CropEffect
import codes.pepper.whimsicalart.feature.editor.domain.TransformEffect
import codes.pepper.whimsicalart.feature.editor.domain.estimateStackOutputSize
import codes.pepper.whimsicalart.feature.editor.domain.stickerLinePaint
import codes.pepper.whimsicalart.feature.editor.domain.stickerSilhouettePath
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorUiState
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.buildBeautySpecs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
    stickersViewModel: StickersViewModel = hiltViewModel(),
    textEditorViewModel: TextEditorViewModel = hiltViewModel(),
    mosaicViewModel: MosaicViewModel = hiltViewModel(),
    blurViewModel: BlurViewModel = hiltViewModel(),
    penViewModel: PenViewModel = hiltViewModel(),
    removalViewModel: RemovalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val stickersState by stickersViewModel.uiState.collectAsState()
    val textState by textEditorViewModel.uiState.collectAsState()
    val mosaicState by mosaicViewModel.uiState.collectAsState()
    val blurState by blurViewModel.uiState.collectAsState()
    val penState by penViewModel.uiState.collectAsState()
    val removalState by removalViewModel.uiState.collectAsState()
    var selectedAspectRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    // "Preview now" crop toggle: hides the grid + disables ratios so the photo is
    // shown cleanly. A pure display affordance — NOT a control parameter. Also acts
    // as the crop tool's pan/zoom "eye" inspection toggle (see effects_reference).
    // Eye (inspect/preview) toggle on EVERY tool. For Category B tools
    // (non-pan/zoom-capable) it turns on pan/zoom + hold-to-compare and suspends
    // the tool's own gestures so the user can inspect and compare. For Category A
    // tools the eye controls only tail folding (pan/zoom/compare are always on).
    // Default state follows the category: Category A = on, Category B = off. The
    // default is re-applied whenever the selected tool changes.
    var eyeEnabled by remember { mutableStateOf(true) }
    // Background is a hybrid tool: in Replace mode with a placement image
    // selected it is a Category B (placement) state — the eye defaults off, the
    // viewport resets and the placement grid owns the gestures; in Blur mode and
    // in Replace mode without a placement image it stays Category A (eye on).
    val isBackgroundPlacement =
        uiState.selectedTool == EditTool.BACKGROUND &&
            uiState.backgroundMode == BackgroundMode.REPLACE &&
            uiState.backgroundImage != null
    LaunchedEffect(uiState.selectedTool, uiState.selectedBeautyTool, isBackgroundPlacement) {
        eyeEnabled = if (isBackgroundPlacement) {
            false
        } else {
            !isNonPanZoomCapable(uiState.selectedTool, uiState.selectedBeautyTool)
        }
    }
    val selectedFilterId = uiState.selectedFilterId

    // While the Layers window is open, the system back button closes the panel
    // instead of navigating away from the editor.
    BackHandler(enabled = uiState.isLayersVisible) {
        viewModel.toggleLayers()
    }

    // While the inline Beauty panel is open, back closes it (returning to the
    // editor) instead of navigating away from the editor.
    BackHandler(enabled = uiState.selectedTool == EditTool.BEAUTY) {
        viewModel.selectTool(null)
    }

    // Removes ONE effect entry (by id) from the effect stack — the correct,
    // targeted Layers-delete path — instead of clearing the whole underlying
    // tool state. Editor-owned params (adjustments, filters, style, background,
    // beauty, crop, transform, frame) are neutralised inside removeStackEffect.
    // External tool live-state (stickers/text/pen held by their own ViewModels)
    // is released HERE so a removed entry isn't resurrected by the derived
    // snapshot, while sibling layers of the same tool keep their own data.
    val removeLayer: (StackEffect) -> Unit = { effect ->
        when (effect) {
            is BitmapDiffEffect -> {
                when (effect.tool) {
                    EditTool.PEN -> penViewModel.clear()
                    EditTool.MOSAIC -> mosaicViewModel.clear()
                    EditTool.BLUR_BRUSH -> blurViewModel.clear()
                    EditTool.OBJECT_REMOVAL -> removalViewModel.clear()
                    else -> Unit
                }
            }
            is StickerEffect -> stickersViewModel.clearAllStickers()
            is TextEffect -> textEditorViewModel.clearAllText()
            else -> Unit
        }
        viewModel.removeStackEffect(effect.id)
    }
    // Debounced preview look: while a slider is dragged the preview is NOT
    // recomputed on every tick. The adjustment values are snapshotted only once
    // they have been still for ~[PREVIEW_DEBOUNCE_MS], so the displayed photo
    // updates with a short settle delay instead of per-slider-movement.
    val settledLook = producePreviewLook(
        brightness = uiState.brightness,
        contrast = uiState.contrast,
        saturation = uiState.saturation,
        exposure = uiState.exposure,
        temperature = uiState.temperature,
        tint = uiState.tint,
        shadows = uiState.shadows,
        highlights = uiState.highlights,
        filterId = selectedFilterId,
        lensIds = uiState.enabledLensFilterIds
    )
    val previewColorFilter = remember(settledLook) {
        val matrix = EditorColorMatrix.build(
            brightness = settledLook.brightness,
            contrast = settledLook.contrast,
            saturation = settledLook.saturation,
            exposure = settledLook.exposure,
            temperature = settledLook.temperature,
            tint = settledLook.tint,
            shadows = settledLook.shadows,
            highlights = settledLook.highlights,
            filterMatrix = FilterPresets.concatFilterMatrices(
                settledLook.filterId, settledLook.lensIds
            )
        )
        ColorFilter.colorMatrix(matrix)
    }
    // Rotated pixel dimensions of the source image, so the crop overlay can be
    // confined to the letterboxed (ContentScale.Fit) image rect and match the
    // bitmap region that is actually cropped on save. The anchor is the CURRENT
    // fold base ([EditorUiState.baseImageSize]) — the pristine decode before any
    // merge, the flattened merged result (possibly larger) after one — NOT the
    // pristine `originalBitmap`: a crop opened after a layers merge must span the
    // merged base image's extent, not the old pre-merge region.
    val rotatedImageSize = remember(uiState.baseImageSize, uiState.rotation) {
        cropImageSize(uiState.baseImageSize, uiState.rotation)
    }
// The beauty pen paints in the SOURCE coordinate space (like the standalone
    // pen): strokes carry position fractions and a width fraction, both
    // resolution-independent, so they bake correctly whether the overlay runs
    // against the pristine fold base (the rotation-neutral source dims here) or
    // the committed preview. A degenerate base never happens in practice -- the
    // overlay is only shown while a photo is loaded -- but Size.Zero keeps the
    // overlay's own guard active if it ever does.
    val beautyPenBaseSize = remember(uiState.originalBitmap) {
        uiState.originalBitmap?.let { Size(it.width.toFloat(), it.height.toFloat()) }
            ?: Size.Zero
    }
    // Single shared zoom/pan transform for the photo viewport. Hoisted here so
    // the tap-and-hold compare overlay reveals the original at the same scale,
    // position, rotation and flips as the live preview they are looking at.
    val viewportTransform = remember { ViewportTransform() }
    // Pre-placement viewport snapshot, restored when the background placement
    // state is left (see the LaunchedEffect below).
    val savedPlacementViewport = remember { mutableStateOf<ViewportTransform?>(null) }

    // Pan/zoom capability of the current screen. Crop + brush tools are
    // "non pan/zoom-capable" by default (their single-finger drags paint or
    // drag the crop frame instead of moving the photo). The eye toggle re-enables
    // pan/zoom for inspection; all other tools are pan/zoom-capable by default.
    val selectedTool = uiState.selectedTool
    // Everything derives from the single classification: Category B tools can pan/zoom
    // only while the eye is enabled (the NON_COMPARE_TOOLS + the beauty pen sub-tool,
    // see isNonPanZoomCapable); every other (Category A) tool is pan/zoom-capable always.
    val canPanZoom = if (isBackgroundPlacement) {
        eyeEnabled
    } else {
        if (isNonPanZoomCapable(selectedTool, uiState.selectedBeautyTool)) eyeEnabled else true
    }

    // Whether the main preview shows the folded composite (`compositePreview`)
    // instead of the raw source + display-only rotation. For source-space
    // placement tools (crop grid, brush, sticker, text) the composite is folded
    // UP TO but excluding the active tool's own layer while the tool is being
    // edited (eye OFF), so the tool's interactive overlay (stroke / grid /
    // sticker / text) paints once over the fold of every preceding effect — NOT
    // the raw original. With the eye ON the full committed composite is shown
    // (the tool's own effect is baked into the pixels, overlays hidden).
    val useCompositePreview = uiState.compositePreview != null

    // On entry to any non-pan/zoom-capable (Category B) tool the preview holds
    // still: stale pan/zoom is not carried over, so the viewport resets and the
    // image fits at the default position/scale. The background placement state
    // holds still the same way.
    LaunchedEffect(selectedTool, uiState.selectedBeautyTool) {
        if (isNonPanZoomCapable(selectedTool, uiState.selectedBeautyTool)) {
            viewportTransform.reset()
        }
    }
    // Background placement save/restore (spec: "Switching Replace → Blur is
    // non-destructive on the canvas"): entering the placement state resets the
    // viewport to the default fit so the grid owns the gestures, but the
    // PRE-placement pan/zoom must be restored when the placement is left (Blur
    // back, or leaving the Background tool) — the canvas returns to the subject
    // image's pan / zoom / rotation as if the replacement had never been
    // applied, while the placement control values stay stored.
    LaunchedEffect(isBackgroundPlacement) {
        if (isBackgroundPlacement) {
            savedPlacementViewport.value = ViewportTransform().also { it.copyFrom(viewportTransform) }
            viewportTransform.reset()
        } else {
            savedPlacementViewport.value?.let(viewportTransform::copyFrom)
            savedPlacementViewport.value = null
        }
    }
    // Crop-and-Transform-only quirk: disabling the crop/transform eye button
    // resets the viewport to the original position and scale; every other eye
    // tool keeps its changed pan/scale when the eye is disabled. The background
    // placement state holds still at the default fit the same way on leaving its
    // eye OFF.
    LaunchedEffect(selectedTool, eyeEnabled, isBackgroundPlacement) {
        if ((selectedTool == EditTool.CROP || selectedTool == EditTool.TRANSFORM) && !eyeEnabled) {
            viewportTransform.reset()
        }
        if (isBackgroundPlacement && !eyeEnabled) {
            viewportTransform.reset()
        }
    }
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }
    // "Discard changes?" confirmation shown when backing out of the editor with
    // unsaved edits (docs/user_guide.md → Leaving the editor). The actual
    // commit-then-evaluate decision lives in [exitGuard], assigned once the tool
    // commit helpers exist (it needs commitCurrentTool, defined lower).
    var showDiscardDialog by remember { mutableStateOf(false) }
    var exitGuard: () -> Unit = {}
    var recognizingText by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editBoxSize by remember { mutableStateOf(Size.Zero) }
    // The letterboxed (ContentScale.Fit) image rect the ACTUAL DISPLAYED bitmap
    // occupies within the full viewport box. That bitmap is the COMPOSITE FOLD
    // whenever one is shown — and a canvas-reshaping effect (crop, a frame's
    // outer extent, a grown background placement) changes the fold's output
    // aspect, so the rect MUST be derived from the composite's own pixel dims
    // (their aspect IS the displayed aspect), never from the bare rotated base.
    // Deriving it from `rotatedImageSize` only made every source-space captured
    // fraction (text/stickers/strokes) land at a shifted pixel in the eye-ON
    // view and the saved photo after such a reshape; before any effect exists
    // the displayed bitmap is the rotated EXIF-oriented base itself, which the
    // fallback below reproduces (previously a raw `inJustDecodeBounds` header
    // reported the UN-oriented aspect and every fitted-rect overlay drifted).
    val imageViewSize = fittedImageSize(
        editBoxSize,
        uiState.compositePreview?.let {
            Size(it.width.toFloat(), it.height.toFloat())
        } ?: rotatedImageSize
    )
    // On-screen size of the letterboxed image content (ContentScale.Fit) within
    // the full viewport box. Vignette + frame must be confined to this rect AND
    // carry the same viewport transform as the image, so they move/scale with
    // it while panning/zooming instead of staying locked to the viewport edges.
    val imageFitSize = remember(rotatedImageSize, imageViewSize) {
        if (rotatedImageSize.width <= 0f || rotatedImageSize.height <= 0f ||
            imageViewSize.width <= 0f || imageViewSize.height <= 0f
        ) {
            Size.Zero
        } else {
            val fit = min(
                imageViewSize.width / rotatedImageSize.width,
                imageViewSize.height / rotatedImageSize.height
            )
            Size(rotatedImageSize.width * fit, rotatedImageSize.height * fit)
        }
    }
    // The fitted image rect (position + size in box px) that source-space tools
    // capture against. Strokes/text/stickers are normalized against THIS rect —
    // subtracting the letterbox offset and dividing by its size — so the baked
    // source fraction matches the live overlay position regardless of box aspect
    // (previously the whole-box normalization drifted on every letterboxed photo).
    val imageFrame = imageFrameOf(editBoxSize, imageViewSize)
    val density = LocalDensity.current

    // Per-family anchored capture/restore frames (docs/effect_stack.md →
    // *Positional layers*): each source-space family is normalized against ITS
    // OWN committed prefix footprint fitted into the box — never the full
    // composite frame. A transform/crop added AFTER a layer therefore never
    // silently re-anchors it on the next commit, and a re-entry reconstructs
    // the overlay from the stored reference geometry instead of re-normalising
    // against the current composite (which may still reflect the stale,
    // full-stack dims). `refWidth`/`refHeight` are the full-size prefix
    // footprint each captured fraction is stored against.
    //
    // The prefix is folded from the stack's TRUE base — `baseImageSize`, the
    // dims the fold actually starts from — never `rotatedImageSize`. The
    // committed stack ALREADY carries the Transform layer that turns that base
    // into the rotated footprint, so starting from `rotatedImageSize` would
    // apply the base rotation TWICE (the "sticker shifts once a transform is
    // applied" report): the stale double-rotated reference no longer equals the
    // base the layer folds over, and every later render re-places the object
    // against that wrong frame.
    fun familyAnchor(tool: EditTool): FamilyAnchor = familyAnchorFor(
        uiState.effectStack,
        tool,
        uiState.baseImageSize ?: rotatedImageSize,
        editBoxSize
    )
    val keyboardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(imageUri) {
        viewModel.setImageUri(imageUri)
        viewModel.loadImageBitmap(imageUri)
    }

    LaunchedEffect(Unit) {
        // Entering split screen regenerates the activity and can re-run this
        // effect before the outer Box's focusRequester node is composed →
        // requestFocus would throw "FocusRequester is not initialized". Never
        // crash on a not-yet-attached requester.
        runCatching { keyboardFocusRequester.requestFocus() }
    }

    LaunchedEffect(editBoxSize) {
        if (editBoxSize.width > 0f && editBoxSize.height > 0f) {
            val center = with(density) {
                Offset(
                    (editBoxSize.width / 2f).toDp().value,
                    (editBoxSize.height / 2f).toDp().value
                )
            }
            textEditorViewModel.updateCanvasCenter(center)
            stickersViewModel.updateCanvasCenter(center)
        }
    }

    LaunchedEffect(uiState.selectedTool) {
        if (uiState.selectedTool == EditTool.TEXT) {
            // Re-entering the tool restores the committed, parameterised
            // TextItem list (the stack entry is the source of truth) so every
            // item stays re-editable; with no committed text we fall back to the
            // add-as-you-type flow. The overlay reconstructs against the TEXT
            // family's OWN anchored frame, and each item carries its stored
            // reference base — so a transform added after the text never
            // dislodges the restored overlay from where the render shows it.
            val textEffect = uiState.effectStack.lastOrNull { it is TextEffect } as? TextEffect
            if (textEffect != null && textEffect.layers.isNotEmpty()) {
                val textFrame = familyAnchor(EditTool.TEXT).frame
                val restored = textEffect.layers.map { item ->
                    item.toTextOverlay(
                        density, textFrame.size.width, textFrame.size.height,
                        textFrame.offsetX, textFrame.offsetY
                    )
                }
                textEditorViewModel.restoreOverlays(restored)
            } else {
                textEditorViewModel.startAddingText()
            }
        }
        if (uiState.selectedTool == EditTool.STICKERS) {
            // Re-entering the tool restores the committed, parameterised
            // StickerItem list (the stack entry is the source of truth) so every
            // placement stays re-editable and selectable — against the STICKERS
            // family's own anchored frame and the item's stored reference base.
            val stickerEffect = uiState.effectStack.lastOrNull { it is StickerEffect } as? StickerEffect
            if (stickerEffect != null && stickerEffect.layers.isNotEmpty()) {
                val stickerFrame = familyAnchor(EditTool.STICKERS).frame
                val restored = stickerEffect.layers.mapNotNull { item ->
                    val base = item.baseSizeDp()
                    base?.let {
                        item.toStickerPlacement(
                            density, stickerFrame.size.width, stickerFrame.size.height,
                            it, stickerFrame.offsetX, stickerFrame.offsetY,
                            item.referenceWidth, item.referenceHeight
                        )
                    }
                }
                if (restored.size == stickerEffect.layers.size) {
                    stickersViewModel.restoreOverlays(restored)
                }
            }
        }
        // Brush tools: re-entering the tool re-presents the COMMITTED strokes
        // through the family's CURRENT anchored frame (docs/effect_stack.md →
        // *Positional layers*), so a brush never rides stale screen positions
        // after an earlier effect changed the layer's base — mirroring the
        // sticker/text re-entry restore.
        val brushTool = when (uiState.selectedTool) {
            EditTool.MOSAIC -> EditTool.MOSAIC
            EditTool.BLUR_BRUSH -> EditTool.BLUR_BRUSH
            EditTool.PEN -> EditTool.PEN
            EditTool.OBJECT_REMOVAL -> EditTool.OBJECT_REMOVAL
            else -> null
        }
        if (brushTool != null) {
            val effect = uiState.effectStack.lastOrNull {
                it is BitmapDiffEffect && it.tool == brushTool
            } as? BitmapDiffEffect
            if (effect != null && effect.strokes.isNotEmpty()) {
                val a = familyAnchor(brushTool)
                val frame = a.frame
                val restored = effect.strokes.mapIndexed { i, stroke ->
                    val (pts, size) = stroke.toFramePixels(
                        frame.offsetX, frame.offsetY, frame.size.width, frame.size.height
                    )
                    when (brushTool) {
                        EditTool.MOSAIC -> MosaicStroke(
                            id = "restored:$i",
                            points = pts.map { (x, y) -> Offset(x, y) },
                            brushSize = size,
                            opacity = stroke.opacity
                        )
                        EditTool.BLUR_BRUSH -> BlurStroke(
                            id = "restored:$i",
                            points = pts.map { (x, y) -> Offset(x, y) },
                            brushSize = size,
                            opacity = stroke.opacity
                        )
                        EditTool.PEN -> PenStroke(
                            id = "restored:$i",
                            points = pts.map { (x, y) -> Offset(x, y) },
                            brushType = when (stroke.brush) {
                                PenBrushKind.GLOW -> PenBrushType.GLOW
                                PenBrushKind.NEON -> PenBrushType.NEON
                                PenBrushKind.RAINBOW -> PenBrushType.RAINBOW
                                PenBrushKind.SOLID -> PenBrushType.SOLID
                            },
                            color = Color(stroke.color ?: android.graphics.Color.BLACK),
                            size = size,
                            opacity = stroke.opacity,
                            hardness = stroke.hardness.coerceIn(0f, 1f)
                        )
                        else -> RemovalStroke(
                            id = "restored:$i",
                            points = pts.map { (x, y) -> Offset(x, y) },
                            brushSize = size
                        )
                    }
                }
                when (brushTool) {
                    EditTool.MOSAIC -> mosaicViewModel.restoreStrokes(
                        restored.mapNotNull { it as? MosaicStroke }
                    )
                    EditTool.BLUR_BRUSH -> blurViewModel.restoreStrokes(
                        restored.mapNotNull { it as? BlurStroke }
                    )
                    EditTool.PEN -> penViewModel.restoreStrokes(
                        restored.mapNotNull { it as? PenStroke }
                    )
                    EditTool.OBJECT_REMOVAL -> removalViewModel.restoreStrokes(
                        restored.mapNotNull { it as? RemovalStroke }
                    )
                    else -> Unit
                }
            }
        }
    }

    // App-wide chrome scale (multi-window / split-screen): provided ONCE at the
    // app root from the measured WINDOW (see AppUiScale + computeUiScale), so
    // the editor, gallery, settings, camera, collage, onboarding and every
    // dialog/popup share the same factor. Everything that reads
    // LocalUiScale.current is chrome only — the canvas letterbox formulas
    // receive it as an explicit reserve-size parameter, and canvas-space
    // gesture / preview↔canvas px math never rescales.
    val actions = EditorActions(
        // The toolbar back button routes through the SAME discard guard as the
        // system back (exitGuard is (re)assigned below once commitCurrentTool
        // exists — it always is before any button press can fire).
        onBack = { exitGuard() },
        onToggleLayers = { viewModel.toggleLayers() },
        onUndo = { viewModel.undo() },
        onRedo = { viewModel.redo() },
        onRequestSave = { showSaveDialog = true },
        onRetryLoadImage = { viewModel.retryLoadImage() },
        onSelectTool = { viewModel.selectTool(it) },
        onEyeToggle = { eyeEnabled = !eyeEnabled },
        onSetCrop = { viewModel.updateCrop(it) },
        onApplyCrop = { viewModel.applyCrop() }
    )
    val uiScale = LocalUiScale.current
    Box(modifier = Modifier.fillMaxSize()) {
    // Outer container owns focus/key events and keeps the snackbar pinned at
    // the bottom regardless of how tall the (possibly scrolling) controls get.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(keyboardFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                handleEditorKeyEvent(viewModel, event)
            }
    ) {
        Column(Modifier.fillMaxSize()) {
        EditorTopBar(uiState, actions)
            EditorCanvasArea(
                imageUri = imageUri,
                uiState = uiState,
                actions = actions,
                viewModel = viewModel,
                stickersState = stickersState,
                stickersViewModel = stickersViewModel,
                textState = textState,
                textEditorViewModel = textEditorViewModel,
                mosaicState = mosaicState,
                mosaicViewModel = mosaicViewModel,
                blurState = blurState,
                blurViewModel = blurViewModel,
                penState = penState,
                penViewModel = penViewModel,
                removalState = removalState,
                removalViewModel = removalViewModel,
                uiScale = uiScale,
                viewportTransform = viewportTransform,
                canPanZoom = canPanZoom,
                useCompositePreview = useCompositePreview,
                isBackgroundPlacement = isBackgroundPlacement,
                eyeEnabled = eyeEnabled,
                selectedAspectRatio = selectedAspectRatio,
                previewColorFilter = previewColorFilter,
                rotatedImageSize = rotatedImageSize,
                imageViewSize = imageViewSize,
                imageFitSize = imageFitSize,
                imageFrame = imageFrame,
                beautyPenBaseSize = beautyPenBaseSize,
                onCanvasBoxSized = { size ->
                    editBoxSize = size
                    viewModel.setViewBox(size.width.toInt(), size.height.toInt())
                },
                controls = {
                    EditorToolPanelHost(
                        uiState = uiState,
                        viewModel = viewModel,
                        stickersState = stickersState,
                        stickersViewModel = stickersViewModel,
                        textState = textState,
                        textEditorViewModel = textEditorViewModel,
                        mosaicState = mosaicState,
                        mosaicViewModel = mosaicViewModel,
                        blurState = blurState,
                        blurViewModel = blurViewModel,
                        penState = penState,
                        penViewModel = penViewModel,
                        removalState = removalState,
                        removalViewModel = removalViewModel,
                        density = density,
                        editBoxSize = editBoxSize,
                        imageViewSize = imageViewSize,
                        eyeEnabled = eyeEnabled,
                        selectedAspectRatio = selectedAspectRatio,
                        onAspectRatioSelected = { selectedAspectRatio = it },
                        snackbarHostState = snackbarHostState
                    )
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            // Pinned at the bottom edge, outside the scrolled controls so it is
            // always visible even while a tall panel (Stickers/Text) is scrolled.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }

    // The last sticker fold computed against a VALID fitted image rect. A zoom /
    // viewport change can transiently re-measure the frame to zero size; the fold
    // then reuses this last good set so already-placed stickers never disappear
    // from the canvas ("add a sticker -> zoom -> sticker vanishes"). Hoisted here
    // (not inside buildRenderBundle) because it is a plain, non-composable fun.
    val lastGoodStickerItems = remember { mutableStateOf<List<StickerItem>>(emptyList()) }

    fun buildRenderBundle(): EditorRenderBundle {
        // The capture/normalize space for every source-space family is ITS OWN
        // anchored frame (docs/effect_stack.md → *Positional layers*): the
        // family's committed PREFIX footprint fitted into the box — never the
        // full composite frame. A transform/crop added AFTER a layer must not
        // reset that layer's position, and each captured fraction is stored
        // against the full-size prefix footprint (`refWidth`/`refHeight`) so
        // the same fractions map through any later base change via a uniform
        // centre-origin fit.
        val fr = imageFrame
        fun norm(p: Offset, fr: ImageCanvasFrame): Pair<Float, Float> =
            if (fr.size.width > 0f && fr.size.height > 0f) {
                ((p.x - fr.offsetX) / fr.size.width) to ((p.y - fr.offsetY) / fr.size.height)
            } else {
                0f to 0f
            }
        fun normWidth(px: Float, fr: ImageCanvasFrame): Float =
            if (fr.size.width > 0f) px / fr.size.width else 0f

        val stickerAnchor = familyAnchor(EditTool.STICKERS)
        val storedStickers =
            (uiState.effectStack.lastOrNull { it is StickerEffect } as? StickerEffect)?.layers
        val stickerItems = if (stickerAnchor.frame.size.width > 0f && stickerAnchor.frame.size.height > 0f) {
            val derived = stickersState.placedStickers.mapNotNull { placement ->
                val sticker = StickerPresets.getStickerById(placement.stickerId)
                placement.toStickerItem(
                    sticker, density,
                    stickerAnchor.frame.size.width, stickerAnchor.frame.size.height,
                    stickerAnchor.frame.offsetX, stickerAnchor.frame.offsetY,
                    stickerAnchor.refWidth, stickerAnchor.refHeight
                )
            }
            // Freeze the committed reference (never refresh it on an unrelated
            // rebuild); re-anchor only when the layer's own base changed
            // (docs/effect_stack.md → *Positional layers*, rules 1+4).
            PositionalReconcilers.stickers(
                stored = storedStickers,
                storedRef = storedRefOfStickers(storedStickers),
                derived = derived,
                newBase = anchorOf(stickerAnchor)
            ).also { lastGoodStickerItems.value = it }
        } else {
            // Degenerate fitted rect (transient mid-zoom / mid-rebound): keep the
            // last valid fold so placed stickers survive instead of vanishing.
            lastGoodStickerItems.value
        }

        val textAnchor = familyAnchor(EditTool.TEXT)
        val storedTexts = (uiState.effectStack.lastOrNull { it is TextEffect } as? TextEffect)?.layers
        val textLayers = PositionalReconcilers.texts(
            stored = storedTexts,
            storedRef = storedRefOfTexts(storedTexts),
            derived = textState.textOverlays.map { overlay ->
                overlay.toTextItem(
                    density,
                    textAnchor.frame.size.width, textAnchor.frame.size.height,
                    textAnchor.frame.offsetX, textAnchor.frame.offsetY,
                    textAnchor.refWidth, textAnchor.refHeight
                )
            },
            newBase = anchorOf(textAnchor)
        )

        val strokeLayers = buildList {
            val mosaicA = familyAnchor(EditTool.MOSAIC)
            val storedMosaic = storedStrokes(uiState.effectStack, EditTool.MOSAIC)
            addAll(PositionalReconcilers.strokes(storedMosaic, storedRefOfStrokes(storedMosaic), mosaicState.strokes.map { s ->
                StrokeLayer(
                    type = StrokeType.MOSAIC,
                    points = s.points.map { norm(it, mosaicA.frame) },
                    brushSize = normWidth(s.brushSize, mosaicA.frame),
                    opacity = s.opacity,
                    referenceWidth = mosaicA.refWidth,
                    referenceHeight = mosaicA.refHeight
                )
            }, anchorOf(mosaicA)))
            val blurA = familyAnchor(EditTool.BLUR_BRUSH)
            val storedBlur = storedStrokes(uiState.effectStack, EditTool.BLUR_BRUSH)
            addAll(PositionalReconcilers.strokes(storedBlur, storedRefOfStrokes(storedBlur), blurState.strokes.map { s ->
                StrokeLayer(
                    type = StrokeType.BLUR,
                    points = s.points.map { norm(it, blurA.frame) },
                    brushSize = normWidth(s.brushSize, blurA.frame),
                    opacity = s.opacity,
                    referenceWidth = blurA.refWidth,
                    referenceHeight = blurA.refHeight
                )
            }, anchorOf(blurA)))
            val penA = familyAnchor(EditTool.PEN)
            val storedPen = storedStrokes(uiState.effectStack, EditTool.PEN)
            addAll(PositionalReconcilers.strokes(storedPen, storedRefOfStrokes(storedPen), penState.strokes.map { s ->
                StrokeLayer(
                    type = StrokeType.PEN,
                    points = s.points.map { norm(it, penA.frame) },
                    brushSize = normWidth(s.size, penA.frame),
                    // The pen opacity becomes the colour's alpha so the whole
                    // stroke (border included) is scaled by it (docs/effects_reference.md
                    // → Pen controls).
                    color = s.color.copy(alpha = s.opacity.coerceIn(0f, 1f)).toArgb(),
                    hardness = s.hardness.coerceIn(0f, 1f),
                    // Carry the brush type so GLOW / NEON / RAINBOW commit the
                    // way they preview (hardness remains solid/rainbow only).
                    brush = s.brushType.toPenBrushKind(),
                    referenceWidth = penA.refWidth,
                    referenceHeight = penA.refHeight
                )
            }, anchorOf(penA)))
            val removalA = familyAnchor(EditTool.OBJECT_REMOVAL)
            val storedRemoval = storedStrokes(uiState.effectStack, EditTool.OBJECT_REMOVAL)
            addAll(PositionalReconcilers.strokes(storedRemoval, storedRefOfStrokes(storedRemoval), removalState.strokes.map { s ->
                StrokeLayer(
                    type = StrokeType.REMOVAL,
                    points = s.points.map { norm(it, removalA.frame) },
                    brushSize = normWidth(s.brushSize, removalA.frame),
                    referenceWidth = removalA.refWidth,
                    referenceHeight = removalA.refHeight
                )
            }, anchorOf(removalA)))
        }

        val frameLayers = if (fr.size.width > 0f) {
            // One layer carries BOTH tiers so they commit, reorder, merge and
            // revert together (docs/effects_reference.md → *Frame tiers*).
            val layer = FrameLayer(
                inner = FrameTier(
                    style = uiState.innerFrameStyle,
                    thickness = uiState.innerFrameThickness,
                    color = uiState.innerFrameColor,
                    edgeSize = uiState.innerFrameEdgeSize,
                    customAngle = uiState.innerFrameCustomAngle,
                    customCurveDistance = uiState.innerFrameCustomCurveDistance,
                    customStrength = uiState.innerFrameCustomStrength,
                    customCount = uiState.innerFrameCustomCount,
                    lines = uiState.effectiveInnerFrameLines,
                    lineDistance = uiState.innerFrameLineDistance,
                    lineThickness = uiState.innerFrameLineThickness
                ),
                outer = FrameTier(
                    style = uiState.outerFrameStyle,
                    thickness = uiState.outerFrameThickness,
                    color = uiState.outerFrameColor,
                    edgeSize = uiState.outerFrameEdgeSize,
                    customAngle = uiState.outerFrameCustomAngle,
                    customCurveDistance = uiState.outerFrameCustomCurveDistance,
                    customStrength = uiState.outerFrameCustomStrength,
                    customCount = uiState.outerFrameCustomCount,
                    lines = uiState.effectiveOuterFrameLines,
                    lineDistance = uiState.outerFrameLineDistance,
                    lineThickness = uiState.outerFrameLineThickness
                )
            )
            if (layer.enabled) listOf(layer) else emptyList()
        } else {
            emptyList()
        }

        val backgroundLayer = uiState.subjectMask?.let { mask ->
            BackgroundLayer(
                mode = uiState.backgroundMode,
                subjectMask = mask,
                blurRadius = uiState.backgroundBlurRadius,
                shape = uiState.backgroundShape,
                backgroundImage = uiState.backgroundImage,
                placementScale = uiState.backgroundPlacementScale,
                placementPanX = uiState.backgroundPlacementPanX,
                placementPanY = uiState.backgroundPlacementPanY,
                placementRotationDegrees = uiState.backgroundPlacementRotationDegrees,
                freeScaleUp = uiState.backgroundFreeScaleUp
            )
        }

        return EditorRenderBundle(
            filterMatrix = FilterPresets.concatFilterMatrices(
                uiState.selectedFilterId, uiState.enabledLensFilterIds
            ),
            styleFilter = uiState.styleFilter,
            lensPassIds = uiState.enabledLensFilterIds.filterTo(linkedSetOf()) {
                FilterPresets.isRealPassLensFilter(it)
            },
            stickers = stickerItems,
            texts = textLayers,
            strokes = strokeLayers,
            frames = frameLayers,
            background = backgroundLayer,
            suggestedRegions = mosaicState.suggestedRegions
        )
    }

    // Commit the current tool's parameters to the reversible stack **on lost
    // focus** — when the user switches tool, opens the Layers window or saves.
    // This matches the committed-milestone spec (effect_stack.md "Commit on
    // lost focus"): parameters are stored only when the tool loses focus, and
    // buildStackSnapshot already excludes defaults, so an effect that returns to
    // default is dropped by syncLayerState's orderedMerge. The live preview
    // renders independently of this bookkeeping, so committing here (rather than
    // continuously per param change) never affects the preview.
    // BEAUTY is committed through this same single path (its "continuous merge
    // into the stack" was removed — setBeautyEffects now only updates the live
    // params): buildStackSnapshot derives the beauty sub-set from the params,
    // so leaving the tool stores exactly what the focused preview showed, in
    // one reversible layer set.
    val commitCurrentTool: () -> Unit = {
        if (editBoxSize.width > 0f) {
            val snapshot = buildStackSnapshot(uiState = uiState, bundle = buildRenderBundle())
            val fingerprints = stackFingerprints(snapshot)
            viewModel.syncLayerState(snapshot, fingerprints)
            // Per-layer previews are populated at commit time INSIDE
            // syncLayerState (a committed layer always carries its preview-sized
            // bitmap immediately — the interactive preview fold resumes from it
            // and Layers pre-fills); an unchanged re-entry does no work.
        }
    }
    // Leaving the editor (toolbar back / system back, with unsaved changes):
    // COMMIT the current tool FIRST — pending slider tweaks and sub-VM content
    // (text/sticker/brush) are invisible to the dirty flag until a lost-focus
    // commit lands (docs/user_guide.md → Leaving the editor), so the flag is
    // only exact after commitCurrentTool(). Then: dirty → "Discard changes?"
    // dialog; clean → navigate away as usual.
    exitGuard = {
        commitCurrentTool()
        if (viewModel.uiState.value.hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }
    // The editor's top-level back guard. Its enabled condition is mutually
    // exclusive with the in-editor panel handlers above (layers / beauty), which
    // are registered EARLIER yet keep precedence because they can only be
    // enabled when these two flags are off — so a system back first closes those
    // panels and only an un-consumed back reaches the discard confirmation.
    BackHandler(
        enabled = !uiState.isLayersVisible && uiState.selectedTool != EditTool.BEAUTY
    ) {
        exitGuard()
    }
    LaunchedEffect(
        uiState.selectedTool,
        uiState.isLayersVisible
    ) {
        if (editBoxSize.width > 0f) {
            commitCurrentTool()
        }
    }

    // Continuously render the committed composite into the preview base. Re-runs
    // whenever any committed-relevant state (uiState or a source-space sub-VM) or
    // the active tool changes; the VM debounces + cancels + reuses the fold cache,
    // so unchanged stacks are cheap.
    //
    // The key EXCLUDES the preview bitmaps (compositePreview / sharpenedPreview)
    // that this very effect publishes — otherwise each published preview would
    // change `uiState`, re-fire the effect, and restart a render forever. The
    // published strokes-versions are derived publishes in the same class: the
    // effect's own fold stamps them, so including them made every fold re-fire
    // the effect once (and, with a running fold, discard a just-rendered frame).
    // The data-class snapshot copy keeps all the committed-parameter fields but
    // drops the derived preview buffers.
    val compositeKey = uiState.copy(
        compositePreview = null,
        sharpenedPreview = null,
        liveRotationDegrees = 0f,
        compositeBakedRotation = 0f,
        compositeBakedFlipHorizontal = false,
        compositeBakedFlipVertical = false,
        publishedMosaicStrokesVersion = 0,
        publishedRemovalStrokesVersion = 0,
        publishedBlurStrokesVersion = 0
    )

    // Per-layer previews are built at commit time inside the ViewModel (one
    // incremental fold pass); this Layers-open pass is then a safe no-op
    // fast-path — the row thumbnails refresh as downscales of the cached
    // previews, so no full fold ever runs for the Layers path. Rows show the
    // placeholder box only while a preview is genuinely not yet built (cold
    // session before any commit).
    LaunchedEffect(uiState.isLayersVisible, uiState.effectStack) {
        if (!uiState.isLayersVisible) return@LaunchedEffect
        if (uiState.effectStack.isEmpty()) return@LaunchedEffect
        viewModel.refreshLayerPreviews()
    }

    LaunchedEffect(
        compositeKey,
        eyeEnabled,
        stickersState,
        textState,
        mosaicState,
        blurState,
        penState,
        removalState,
        editBoxSize
    ) {
        if (editBoxSize.width <= 0f) return@LaunchedEffect
        val liveRemoval =
            uiState.selectedTool == EditTool.OBJECT_REMOVAL &&
                !eyeEnabled &&
                removalState.liveRemovalApplied &&
                removalState.strokes.isNotEmpty()
        val liveBlur =
            uiState.selectedTool == EditTool.BLUR_BRUSH &&
                !eyeEnabled &&
                blurState.liveBlurApplied &&
                blurState.strokes.isNotEmpty()
        val liveMosaic =
            uiState.selectedTool == EditTool.MOSAIC &&
                !eyeEnabled &&
                mosaicState.liveMosaicApplied &&
                mosaicState.strokes.isNotEmpty()
        val liveApply = liveRemoval || liveBlur || liveMosaic
        // The resolution must never depend on the VM's stored-default viewport:
        // pass the measured canvas (editBoxSize) and current zoom explicitly, the
        // same way the zoom flow does below. editBoxSize is also a KEY so the
        // first render waits for the canvas to be measured (no 0.05-floor stand-in)
        // and a canvas resize (rotation / insets / fullscreen) re-folds at the new
        // resolution.
        val visibleWidthPx = editBoxSize.width.toInt()
        val visibleHeightPx = editBoxSize.height.toInt()
        val canvasZoom = viewportTransform.canvasZoom
        if (isSourceSpaceTool(uiState.selectedTool, uiState.selectedBeautyTool)) {
            // Source-space placement tools draw their own live overlay (strokes /
            // crop grid / stickers / text), so the composite base must be the fold
            // of the effects BEFORE the tool — eye OFF: exclude the tool's own
            // layer (the overlay supplies it interactively); eye ON: show the full
            // committed composite with the layer baked in. Exception: while the
            // object-removal / blur-brush debounce has fired (liveApply), the
            // real inpaint/blur IS the preview, so the tool's layer stays in the
            // fold (the overlay no longer draws the committed strokes). Either
            // way the tail never folds past the current tool while the eye is
            // OFF: the eye hides everything above the current tool (docs →
            // *Eye toggle and the current tool*).
            val (includeTail, excludeCurrentTool) = liveFoldFlags(eyeEnabled, liveApply)
            viewModel.refreshCompositePreview(
                buildRenderBundle(),
                includeTail = includeTail,
                excludeCurrentTool = excludeCurrentTool,
                visibleWidthPx = visibleWidthPx,
                visibleHeightPx = visibleHeightPx,
                canvasZoom = canvasZoom,
                mosaicStrokesVersion = mosaicState.strokesVersion,
                removalStrokesVersion = removalState.strokesVersion,
                blurStrokesVersion = blurState.strokesVersion,
                penStrokesVersion = penState.strokesVersion
            )
        } else {
            // Eye ON → full composite (prefix + current + tail); eye OFF → only
            // prefix + current (tail folded out). Tail folding applies to the
            // non-source-space (Category A) tools shown via the composite preview.
            viewModel.refreshCompositePreview(
                buildRenderBundle(),
                includeTail = eyeEnabled,
                visibleWidthPx = visibleWidthPx,
                visibleHeightPx = visibleHeightPx,
                canvasZoom = canvasZoom
            )
        }
    }

    // A canvasZoom change re-derives the preview resolution (docs/effect_stack.md →
    // Choosing preview resolution) and, once the display outgrows the preview
    // bitmap (displayScale > 1), also drives a full-size composite swap. Keyed
    // off the pure Compose transform so it never re-fires the stack effect above;
    // debounced + best-latest so a fast pinch only triggers one render after the
    // gesture settles, coalesced by the ViewModel.
    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        snapshotFlow { viewportTransform.canvasZoom }
            .drop(1)
            .debounce(300L)
            .collectLatest { canvasZoom ->
                if (editBoxSize.width <= 0f) return@collectLatest
                val visibleWidthPx = editBoxSize.width.toInt()
                val visibleHeightPx = editBoxSize.height.toInt()
if (isSourceSpaceTool(uiState.selectedTool, uiState.selectedBeautyTool)) {
                    val liveRemoval =
                        uiState.selectedTool == EditTool.OBJECT_REMOVAL &&
                            !eyeEnabled &&
                            removalState.liveRemovalApplied &&
                            removalState.strokes.isNotEmpty()
                    val liveBlur =
                        uiState.selectedTool == EditTool.BLUR_BRUSH &&
                            !eyeEnabled &&
                            blurState.liveBlurApplied &&
                            blurState.strokes.isNotEmpty()
                    val liveMosaic =
                        uiState.selectedTool == EditTool.MOSAIC &&
                            !eyeEnabled &&
                            mosaicState.liveMosaicApplied &&
                            mosaicState.strokes.isNotEmpty()
                    val liveApply = liveRemoval || liveBlur || liveMosaic
                    val (includeTail, excludeCurrentTool) = liveFoldFlags(eyeEnabled, liveApply)
                    viewModel.refreshCompositePreview(
                        buildRenderBundle(),
                        includeTail = includeTail,
                        excludeCurrentTool = excludeCurrentTool,
                        visibleWidthPx = visibleWidthPx,
                        visibleHeightPx = visibleHeightPx,
                        canvasZoom = canvasZoom,
                        mosaicStrokesVersion = mosaicState.strokesVersion,
                        removalStrokesVersion = removalState.strokesVersion,
                        blurStrokesVersion = blurState.strokesVersion,
                        penStrokesVersion = penState.strokesVersion
                    )
                } else {
                    viewModel.refreshCompositePreview(
                        buildRenderBundle(),
                        includeTail = eyeEnabled,
                        visibleWidthPx = visibleWidthPx,
                        visibleHeightPx = visibleHeightPx,
                        canvasZoom = canvasZoom
                    )
                }
            }
    }

    if (showSaveDialog) {
        SaveDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { config: SaveConfig ->
                showSaveDialog = false
                commitCurrentTool()
                viewModel.saveImage(config, buildRenderBundle()) { saved ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (saved) "Photo saved" else "Save failed"
                        )
                    }
                }
            },
            onShare = { config: SaveConfig ->
                showSaveDialog = false
                commitCurrentTool()
                viewModel.shareImage(config, buildRenderBundle()) { shared ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (shared) "Photo shared" else "Share failed"
                        )
                    }
                }
            }
        )
    }

    if (showDiscardDialog) {
        val ds = LocalUiScale.current
        val dialogLabel = MaterialTheme.typography.bodyLarge.copy(
            fontSize = MaterialTheme.typography.bodyLarge.fontSize * ds
        )
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = {
                Text(
                    text = "Discard changes?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize * ds
                    )
                )
            },
            text = { Text("Your edits will be lost and the photo will be reverted.", style = dialogLabel) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("Discard", style = dialogLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing", style = dialogLabel)
                }
            }
        )
    }

    if (uiState.isLayersVisible) {
        LayersPanel(
            effects = uiState.effectStack,
            thumbnails = uiState.layerThumbnails,
            hasMerged = uiState.hasMerged,
            onSelect = { effect -> viewModel.selectStackEffect(effect.id) },
            onRemove = { effect -> removeLayer(effect) },
            onMerge = { viewModel.mergeLayers(buildRenderBundle()) },
            onReorder = { from, to -> viewModel.moveEffect(from, to) },
            onBack = { viewModel.toggleLayers() }
        )
    }

    }
}

/**
 * Orientation-aware split between the canvas and the tool controls
 * (docs/effects_reference.md → "Canvas layout and gesture scope").
 *
 * portrait (visible area taller than wide):
 *
 *     Column { [canvasContent] sized [canvasHeight] · [eyeRow] · toolbar · scrollable [controls] }
 *
 * landscape (visible area wider than tall — split-screen/foldable safe):
 *
 *     Row { Column([canvasWidth] + [eyeRow]) · Column(≥ CANVAS_CONTROLS_MIN_WIDTH) { toolbar · scrollable [controls] } }
 *
 * The caller ([canvasHeight] / [canvasWidth]) owns the letterbox-first split
 * math ([portraitLetterboxSplit] / [landscapeLetterboxSplit]): the canvas takes
 * its square cap → 50% floor → remainder and the tool controls absorb every dp
 * the letterbox leaves unused (no percentage ceiling) — this workspace only
 * allocates the computed sizes; the controls region is the split remainder, not
 * the wrap-sized content. The panel's natural height (toolbar + tool controls
 * content, wrap-sized) is still reported through [onControlsNaturallySized] for
 * the scroll-fill threshold: content taller than the allocated remainder
 * scrolls.
 *
 * [canvasContentModifier] carries the sizing/clipping/gesture chain for the
 * canvas box (which differs per orientation: height-fixed in portrait,
 * width-fixed in landscape) and is applied after the size modifier so the
 * Box wrapper only ever sees the values the gestures already assumed.
 * The eye row always hugs the canvas bottom edge, never the side panel, and
 * keeps its own 56 dp × uiScale band (excluded BEFORE the split — see
 * [portraitLetterboxSplit] / [landscapeLetterboxSplit]).
 */
@Composable
internal fun EditorWorkspaceLayout(
    isLandscape: Boolean,
    uiScale: Float,
    canvasHeight: Dp,
    canvasWidth: Dp,
    onControlsNaturallySized: (Dp) -> Unit,
    canvasContentModifier: Modifier,
    canvasContent: @Composable BoxScope.() -> Unit,
    eyeRow: @Composable () -> Unit,
    controlsHeader: @Composable () -> Unit,
    controls: @Composable () -> Unit
) {
    // The panel's natural (wrap-content) size is measured here — the header Box
    // and the scroll-wrapped content box are both sized by their content — then
    // reported up so the canvas can be sized from the CLAMPED panel (the panel
    // region itself is just the remaining chrome after the canvas, so it can
    // never outgrow the clamp the caller applied to the canvas).
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    var controlsContentHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(headerHeight, controlsContentHeight) {
        onControlsNaturallySized(headerHeight + controlsContentHeight)
    }

    // Toolbar (pinned) over the active tool's scrollable panel. Lifted so the
    // exact same subtree is used inside the portrait column and the landscape
    // right-side panel.
    val controlsColumn: @Composable ColumnScope.() -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    headerHeight = with(density) { it.height.toDp() }
                }
        ) {
            controlsHeader()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged {
                        controlsContentHeight = with(density) { it.height.toDp() }
                    }
            ) {
                controls()
            }
        }
    }

    if (isLandscape) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(canvasWidth)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(canvasContentModifier),
                    contentAlignment = Alignment.Center
                ) {
                    canvasContent()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    eyeRow()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .fillMaxHeight()
                    .navigationBarsPadding()
            ) {
                controlsColumn()
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
                    .then(canvasContentModifier),
                contentAlignment = Alignment.Center
            ) {
                canvasContent()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                eyeRow()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
            ) {
                controlsColumn()
            }
        }
    }
}

/**
 * Assembles the ordered, reversible effect stack from the current document
 * state. [bundle] carries the already-normalised photo-look + overlay layers
 * (filter matrix, style, stickers, texts, strokes, frames, background) so the
 * stack entries hold real content — not just empty markers — which makes them
 * the source of truth for the Layers UI, removal/undo and staleness detection.
 * The order mirrors the render pipeline (photo look first, then overlays, then
 * frames on top).
 */
internal fun buildStackSnapshot(
    uiState: EditorUiState,
    bundle: EditorRenderBundle
): List<StackEffect> {
    val list = mutableListOf<StackEffect>()

    // Beauty: committed on lost focus from the LIVE params — the single source
    // of truth (docs/effect_stack.md → Commit on lost focus, Beauty commit on
    // lost focus). The params describe every sub-tool's current value, so a
    // lost-focus commit stores exactly what the focused preview showed; the
    // orderedMerge no-op guard drops the layer(s) when the values were reverted
    // to their stored/default state, so a revert records no layer/checkpoint/
    // dirty. [BeautyStackEffect]s are data classes, so the equality check inside
    // syncLayerState ignores instance identity (the live fold derives FRESH
    // instances from the same specs each commit).
    list += uiState.beauty.buildBeautySpecs().toBeautyEffects()

    // Rotation/flip first, then the crop rect over the oriented image — both as
    // BASE steps (right after Beauty, before any adjustment/overlay), matching
    // the order the renderer actually applies them. This is what makes a crop
    // "apply": every effect added after it operates on the cropped+oriented
    // image, not the full frame. Transform and crop are separate reversible
    // layers.
    if (uiState.rotation != 0f || uiState.flipHorizontal || uiState.flipVertical) {
        list += TransformEffect(
            rotation = uiState.rotation,
            flipHorizontal = uiState.flipHorizontal,
            flipVertical = uiState.flipVertical
        )
    }
    if (uiState.cropRect != null) {
        list += CropEffect(rect = uiState.cropRect)
    }

    // Color adjustments (each non-neutral slider = one reversible effect).
    val adjustments = listOf(
        EditTool.BRIGHTNESS to uiState.brightness,
        EditTool.CONTRAST to uiState.contrast,
        EditTool.SATURATION to uiState.saturation,
        EditTool.SHARPEN to uiState.sharpness,
        EditTool.EXPOSURE to uiState.exposure,
        EditTool.SHADOWS to uiState.shadows,
        EditTool.HIGHLIGHTS to uiState.highlights,
        EditTool.TEMPERATURE to uiState.temperature,
        EditTool.TINT to uiState.tint,
        EditTool.VIGNETTE to uiState.vignette
    )
    adjustments.forEach { (tool, value) ->
        if (value != 0f) {
            list += SingleAdjustmentEffect(tool = tool, value = value)
        }
    }

    // Filter-strip look. Filters is a SINGLE effect (docs/effects_reference.md →
    // *Filters*): a selected style carries the lens/color matrix along with it
    // under the same `layer:FILTER` layer. Real-pass lens filters (red-eye, star,
    // anamorphic, astro) are carried as lensPassIds even when no matrix is active.
    val hasFilterLook = bundle.filterMatrix != null || bundle.styleFilter != null ||
        bundle.lensPassIds.isNotEmpty()
    if (hasFilterLook) {
        if (bundle.styleFilter == null) {
            list += FilterEffect(
                filterMatrix = bundle.filterMatrix,
                lensPassIds = bundle.lensPassIds
            )
        } else {
            list += StyleEffect(
                filter = bundle.styleFilter,
                filterMatrix = bundle.filterMatrix,
                lensPassIds = bundle.lensPassIds
            )
        }
    }

    if (uiState.skinDenoise != 0f) list += SkinDenoiseEffect(intensity = uiState.skinDenoise)
    if (uiState.enhanceEnabled) list += EnhanceEffect(enabled = true)
    // The background layer is the ONLY committed effect re-derived from a raster
    // artifact (the LIVE subjectMask) instead of scalar state. A geometry-change
    // commit (pen, rotate, frame, …) invalidates that live mask (see
    // EditorViewModel.invalidateSubjectMask) so the next bundle carries
    // background = null — but the committed BackgroundEffect is self-sufficient
    // and must survive: fall back to it whenever the live mask is absent, exactly
    // as buildStackEffects does for the fold. Without this, orderedMerge would
    // delete the layer from effectStack and the background would vanish from
    // preview AND saved output (background removal + pen stroke report).
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
        } ?: uiState.effectStack.filterIsInstance<BackgroundEffect>().lastOrNull()
    if (backgroundEffect != null) list += backgroundEffect

    // Brush / overlay layers carry their real content. Each family is emitted
    // from the bundle's live capture when present, ELSE from the last committed
    // layer — the same self-sufficiency rule as the background above: a snapshot
    // taken while a committed family's live strokes are momentarily empty (cold
    // sub-VM, mid-zoom recompute, restore pending) must not drop the layer,
    // otherwise syncLayerState's orderedMerge would delete it from the effectStack
    // and the brush would vanish from the committed document and the full-size
    // composite.
    val penStrokes = bundle.strokes.filter { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.PEN }
    if (penStrokes.isNotEmpty()) {
        list += BitmapDiffEffect(
            tool = EditTool.PEN,
            strokes = penStrokes
        )
    } else {
        uiState.effectStack.filterIsInstance<BitmapDiffEffect>()
            .lastOrNull { it.tool == EditTool.PEN }?.let { list += it }
    }
    val mosaicStrokes = bundle.strokes.filter { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.MOSAIC }
    if (mosaicStrokes.isNotEmpty()) {
        list += BitmapDiffEffect(
            tool = EditTool.MOSAIC,
            strokes = mosaicStrokes,
            suggestedRegions = bundle.suggestedRegions
        )
    } else {
        uiState.effectStack.filterIsInstance<BitmapDiffEffect>()
            .lastOrNull { it.tool == EditTool.MOSAIC }?.let { list += it }
    }
    val blurStrokes = bundle.strokes.filter { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.BLUR }
    if (blurStrokes.isNotEmpty()) {
        list += BitmapDiffEffect(
            tool = EditTool.BLUR_BRUSH,
            strokes = blurStrokes
        )
    } else {
        uiState.effectStack.filterIsInstance<BitmapDiffEffect>()
            .lastOrNull { it.tool == EditTool.BLUR_BRUSH }?.let { list += it }
    }
    val removalStrokes = bundle.strokes.filter { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.REMOVAL }
    if (removalStrokes.isNotEmpty()) {
        list += BitmapDiffEffect(
            tool = EditTool.OBJECT_REMOVAL,
            strokes = removalStrokes
        )
    } else {
        uiState.effectStack.filterIsInstance<BitmapDiffEffect>()
            .lastOrNull { it.tool == EditTool.OBJECT_REMOVAL }?.let { list += it }
    }
    if (bundle.stickers.isNotEmpty()) list += StickerEffect(layers = bundle.stickers)
    if (bundle.texts.isNotEmpty()) list += TextEffect(layers = bundle.texts)

    // Frame borders sit on top.
    bundle.frames.firstOrNull()?.let { list += FrameEffect(layer = it) }

    return list
}

/**
 * Stable per-tool fingerprint of a layer's parameters, used to detect an
 * *actual* control change so an unchanged (merely-revisited) effect window takes
 * no recompute action. Overlay/brush layers use their placeholder fingerprint
 * until their paint is baked.
 */
private fun stackFingerprints(stack: List<StackEffect>): Map<String, String> =
    stack.associate { effect ->
        val fp = when (effect) {
            is SingleAdjustmentEffect -> "adj:${effect.tool}:${effect.value}"
            is FilterEffect -> "filter:${effect.filterMatrix?.contentHashCode() ?: -1}:" +
                "${effect.lensPassIds.sorted()}"
            is EnhanceEffect -> "enhance:${effect.enabled}"
            is StyleEffect -> "style:${effect.filter?.id ?: "null"}:" +
                "${effect.filterMatrix?.contentHashCode() ?: -1}:" +
                "${effect.lensPassIds.sorted()}"
            is BackgroundEffect -> "bg:${effect.mode}:" +
                "${effect.blurRadius}:${effect.shape}:${effect.subjectMask != null}:" +
                "${effect.placementScale}:${effect.placementPanX}:${effect.placementPanY}:" +
                "${effect.placementRotationDegrees}:${effect.freeScaleUp}"
            is BitmapDiffEffect ->
                "brush:${effect.tool}:${effect.strokes.size}:${effect.strokes.hashCode()}"
            is StickerEffect -> "sticker:${effect.layers.size}:${effect.layers.hashCode()}"
            is TextEffect -> "text:${effect.layers.size}:${effect.layers.hashCode()}"
            is FrameEffect -> "frame:${effect.layer?.hashCode() ?: -1}"
            is CropEffect -> "crop:${effect.rect}"
            is TransformEffect -> "transform:${effect.rotation}:${effect.flipHorizontal}:" +
                "${effect.flipVertical}"
            else -> "static:${effect.tool}"
        }
        effect.layerKey to fp
    }

/**
 * Hosts a photo-bound overlay (vignette, frame) so it moves/scales with the
 * image while panning/zooming and stays confined to the letterboxed image rect
 * rather than the full viewport box. Applies the same [ViewportTransform]
 * (scale/pan/rotation/flips) as [ZoomableImage] and sizes the layer to
 * [imageFitSize].
 */
@Composable
internal fun BoxScope.PhotoBoundedLayer(
    imageFitSize: Size,
    rotation: Float,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    transform: ViewportTransform,
    applyTransform: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    if (imageFitSize.width <= 0f || imageFitSize.height <= 0f) return
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(
                width = with(LocalDensity.current) { imageFitSize.width.toDp() },
                height = with(LocalDensity.current) { imageFitSize.height.toDp() }
            )
            .graphicsLayer {
                // [applyTransform] lets consumers opt out of re-applying the baked
                // rotate/flip (effects_reference "Bake to input"): a Frame must draw
                // square around the rotated canvas, so it does NOT follow rotation.
                val flipsApplied = applyTransform && flipHorizontal
                val flipvApplied = applyTransform && flipVertical
                scaleX = transform.canvasZoom * if (flipsApplied) -1f else 1f
                scaleY = transform.canvasZoom * if (flipvApplied) -1f else 1f
                rotationZ = if (applyTransform) rotation else 0f
                translationX = transform.offsetX
                translationY = transform.offsetY
            },
        content = content
    )
}

@Composable
internal fun VignetteOverlay(strength: Float, modifier: Modifier = Modifier) {
    val alpha = (kotlin.math.abs(strength) / 100f * 0.6f).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = alpha)
                ),
                center = Offset(0.5f, 0.5f),
                radius = 1f
            )
        )
    )
}

@Composable
internal fun StickerOverlay(
    placedStickers: List<StickerPlacement>,
    selectedStickerId: String?,
    onMove: (String, Offset) -> Unit,
    onScale: (String, Float) -> Unit,
    onRotate: (String, Float) -> Unit,
    onResize: (String, Float, Offset) -> Unit,
    onSelect: (String?) -> Unit,
    interactionEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val insetPx = with(density) { OVERLAY_ACTION_INSET_DP.dp.toPx() }

    // Re-read the freshest placements/selection inside the gesture coroutine
    // (keyed only on the stable placement ids) so drags do not re-anchor to a
    // stale position and pinch always sees the current scale.
    val currentPlacements by rememberUpdatedState(placedStickers)
    val currentSelectedId by rememberUpdatedState(selectedStickerId)

    // Real rendered bounds (px) of every sticker, for the expanded hit areas.
    // A Gallery photo sizes its (rectangular) box from its carried aspect ratio
    // via `placement.baseSizeDp()`, so resize gestures anchor to the same box the
    // live sprite and the committed fold draw; emoji keep their square base.
    val targets = remember(placedStickers, density) {
        placedStickers.mapIndexedNotNull { index, placement ->
            val base = placement.baseSizeDp() ?: return@mapIndexedNotNull null
            OverlayHitTarget(
                id = placement.id,
                center = with(density) {
                    Offset(placement.x.dp.toPx(), placement.y.dp.toPx())
                },
                size = with(density) {
                    Size(
                        base.width.dp.toPx() * placement.scaleX,
                        base.height.dp.toPx() * placement.scaleY
                    )
                },
                order = index
            )
        }
    }
    val currentTargets by rememberUpdatedState(targets)

// Live resize geometry for any requested id: that sticker's real rendered
// bounds + rotation + uniform scale, read fresh on every event (drags
// re-anchor to the latest centre/scale, never a stale snapshot). The target
// may be an UNSELECTED sticker — the gesture resolved it with the grab rule.
val currentResizeTarget: (id: String) -> OverlayResizeTarget? = { id ->
    val target = currentTargets.firstOrNull { it.id == id }
    val placement = currentPlacements.firstOrNull { it.id == id }
    if (target != null && placement != null) {
        OverlayResizeTarget(
            id = id,
            center = target.center,
            size = target.size,
            rotationDegrees = placement.rotation,
            scale = placement.scaleX
        )
    } else {
        null
    }
}

    Box(modifier = modifier.fillMaxSize()) {
        // Gestures are resolved at the container level through the shared
        // resolver (spec: "Actionable area") so overlapping stickers hit-test by
        // the expanded area + centre-closeness rules, not by z-order.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(placedStickers.map { it.id }, selectedStickerId, interactionEnabled, insetPx) {
                    if (!interactionEnabled) return@pointerInput
                    detectTapGestures(onTap = { pos ->
                        val id = resolveOverlayTarget(
                            pos, currentTargets, insetPx, currentSelectedId, OverlayGestureKind.TAP
                        )
                        onSelect(id)
                    })
                }
                .pointerInput(placedStickers.map { it.id }, selectedStickerId, interactionEnabled, insetPx) {
                    if (interactionEnabled) {
                        // The target resolves ONCE at gesture start (per the shared
                        // "Actionable area" rules), then the whole move/pinch/rotate
                        // applies to that same sticker; grabbing selects it first.
                        overlayTransformGestures(
                            hitTargets = { currentTargets },
                            insetPx = insetPx,
                            selectedId = { currentSelectedId },
                            resizeTarget = currentResizeTarget,
                            onGesture = { id, pan, zoom, rotation ->
                                val p = currentPlacements.firstOrNull { it.id == id }
                                    ?: return@overlayTransformGestures
                                if (id != currentSelectedId) onSelect(id)
                                val move = with(density) {
                                    Offset(pan.x.toDp().value, pan.y.toDp().value)
                                }
                                onMove(id, Offset(p.x + move.x, p.y + move.y))
                                onScale(id, (p.scaleX * zoom).coerceIn(0.3f, 5f))
                                if (rotation != 0f) {
                                    onRotate(id, p.rotation + Math.toDegrees(rotation.toDouble()).toFloat())
                                }
                            },
                            onResize = { id, scale, center ->
                                // Corner/edge resize: the new uniform scale about the
                                // opposite corner, with the re-anchored centre back in
                                // dp for the placement model.
                                if (id != currentSelectedId) onSelect(id)
                                val position = with(density) {
                                    Offset(center.x.toDp().value, center.y.toDp().value)
                                }
                                onResize(id, scale, position)
                            }
                        )
                    }
                }
        ) {}

        placedStickers.forEach { placement ->
            StickerSprite(
                placement = placement,
                selected = interactionEnabled && placement.id == selectedStickerId,
                modifier = Modifier
            )
        }
    }
}

@Composable
private fun StickerSprite(
    placement: StickerPlacement,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val preset = StickerPresets.getStickerById(placement.stickerId)
    val emojiText = placement.emojiText
    val imageUri = placement.imageUri
    // A Gallery photo keeps its own rectangular aspect ratio (spec: "Gallery
    // stickers keep the original image's aspect ratio"), so the on-canvas BOX is
    // the image's proportional box — never a square. `placement.baseSizeDp()`
    // anchors the long side at the emoji default and derives the other side from
    // the carried ratio (portrait taller / landscape wider); emoji (ratio 1) and
    // presets keep their full square base.
    val base = placement.baseSizeDp() ?: return
    val widthDp = base.width * placement.scaleX
    val heightDp = base.height * placement.scaleY
    val wPx = with(density) { widthDp.dp.toPx() }.roundToInt().coerceAtLeast(1)
    val hPx = with(density) { heightDp.dp.toPx() }.roundToInt().coerceAtLeast(1)

    // The live art is rasterized with the SAME per-sticker path the committed
    // fold uses (StickerOverlayRenderer.drawStickers), so the preview fill and
    // silhouette line draw identically to the committed result (spec "Rendering-
    // in-identically"). Only preset and emoji art carry a line today; a Gallery
    // sticker (own pixels, non-colour-enabled) keeps the async Coil render below.
    val artRaster: Bitmap? = remember(placement.stickerId, emojiText, wPx, hPx) {
        when {
            emojiText != null -> renderEmojiGlyph(emojiText, maxOf(wPx, hPx))
            preset != null -> runCatching {
                ContextCompat.getDrawable(context, preset.drawableRes)?.let { drawable ->
                    StickerOverlayRenderer.asBitmap(drawable, wPx, hPx)
                }
            }.getOrNull()
            else -> null
        }
    }

    // The silhouette line (colour-enabled stickers only): the art's alpha
    // contour traced from the same raster the fold strokes, at the sprite's box
    // size. The line is stroked with the SAME android Path + Paint the committed
    // fold uses (stickerSilhouettePath / stickerLinePaint) so the on-canvas line
    // matches the committed result exactly ("Rendering-in-identically").
    val lineStyle = placement.lineStyle
    val lineColour = placement.lineColor ?: DEFAULT_LINE_COLOUR
    val lineWidthPx = placement.lineThickness.coerceIn(0f, 1f) * min(wPx, hPx)
    val lineContour: Path? = remember(
        placement.id,
        placement.lineStyle,
        placement.lineThickness,
        placement.lineColor,
        preset?.id,
        wPx,
        hPx
    ) {
        if (lineStyle != StickerLineStyle.NONE && lineWidthPx > 0f) {
            artRaster?.let { stickerSilhouettePath(it, wPx, hPx) }
        } else {
            null
        }
    }
    val linePaint: Paint? = remember(
        placement.lineStyle,
        lineWidthPx,
        lineColour,
        placement.opacity
    ) {
        if (lineContour != null && lineColour != null) {
            stickerLinePaint(
                style = lineStyle,
                widthPx = lineWidthPx,
                color = lineColour,
                alpha = (placement.opacity.coerceIn(0f, 1f) * 255).roundToInt()
            )
        } else {
            null
        }
    }
    val lineModifier: Modifier = if (linePaint != null && lineContour != null) {
        Modifier.drawWithContent {
            drawContent()
            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(lineContour, linePaint) }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .offset(
                x = (placement.x - widthDp / 2f).dp,
                y = (placement.y - heightDp / 2f).dp
            )
            .size(width = widthDp.dp, height = heightDp.dp)
            .graphicsLayer {
                rotationZ = placement.rotation
                scaleX = if (placement.isFlipped) -1f else 1f
            }
            .alpha(placement.opacity)
            .then(
                if (selected) {
                    Modifier.drawBehind {
                        drawRect(color = Color.White, style = Stroke(width = 2f))
                        val handle = 10f
                        listOf(
                            Offset.Zero,
                            Offset(size.width, 0f),
                            Offset(0f, size.height),
                            Offset(size.width, size.height)
                        ).forEach { c ->
                            drawCircle(color = Color(0xFF3D84FF), radius = handle, center = c)
                            drawCircle(color = Color.White, radius = handle * 0.45f, center = c)
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        if (preset != null) {
            val art = artRaster
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = preset.name,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = placement.tintColor?.let { ColorFilter.tint(Color(it)) },
                    modifier = Modifier.fillMaxSize().then(lineModifier)
                )
            }
        } else if (emojiText != null) {
            val glyph = artRaster?.asImageBitmap()
            if (glyph != null) {
                Image(
                    bitmap = glyph,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().then(lineModifier)
                )
            }
        } else if (imageUri != null) {
            // Gallery sticker: the picked photo's own pixels are the art
            // (spec: non-custom-colour, so no colorFilter/tint here). The box
            // above already equals the photo's aspect ratio, so ContentScale.Fit
            // fills it exactly — no letterbox bars, no distortion.
            Image(
                painter = rememberAsyncImagePainter(Uri.parse(imageUri)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private const val ADJUSTMENT_STEP = 5f

/** Debounce (ms) before the live photo-look preview re-renders after a slider settles. */
private const val PREVIEW_DEBOUNCE_MS = 500L

private fun handleEditorKeyEvent(
    viewModel: EditorViewModel,
    event: androidx.compose.ui.input.key.KeyEvent
): Boolean {
    if (event.type != KeyEventType.KeyDown ||
        event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
    ) {
        return false
    }
    return when (event.key) {
        Key.DirectionRight -> {
            viewModel.selectTool(nextTool(viewModel.uiState.value.selectedTool))
            true
        }
        Key.DirectionLeft -> {
            viewModel.selectTool(previousTool(viewModel.uiState.value.selectedTool))
            true
        }
        Key.DirectionUp -> {
            adjustSelectedValue(viewModel, ADJUSTMENT_STEP)
            true
        }
        Key.DirectionDown -> {
            adjustSelectedValue(viewModel, -ADJUSTMENT_STEP)
            true
        }
        else -> false
    }
}

private fun nextTool(current: EditTool?): EditTool? {
    val entries = EditTool.entries
    val index = current?.let(entries::indexOf) ?: -1
    return entries[(index + 1) % entries.size]
}

private fun previousTool(current: EditTool?): EditTool? {
    val entries = EditTool.entries
    val index = current?.let(entries::indexOf) ?: 0
    return entries[(index - 1 + entries.size) % entries.size]
}

private fun adjustSelectedValue(viewModel: EditorViewModel, step: Float) {
    val tool = viewModel.uiState.value.selectedTool ?: return
    val state = viewModel.uiState.value
    when (tool) {
        EditTool.BRIGHTNESS -> viewModel.updateBrightness(state.brightness + step)
        EditTool.CONTRAST -> viewModel.updateContrast(state.contrast + step)
        EditTool.SATURATION -> viewModel.updateSaturation(state.saturation + step)
        EditTool.SHARPEN -> viewModel.updateSharpness(state.sharpness + step)
        EditTool.EXPOSURE -> viewModel.updateExposure(state.exposure + step)
        EditTool.SHADOWS -> viewModel.updateShadows(state.shadows + step)
        EditTool.HIGHLIGHTS -> viewModel.updateHighlights(state.highlights + step)
        EditTool.TEMPERATURE -> viewModel.updateTemperature(state.temperature + step)
        EditTool.TINT -> viewModel.updateTint(state.tint + step)
        EditTool.VIGNETTE -> viewModel.updateVignette(state.vignette + step)
        // Skin denoise is on a 0..1 scale, so its increment step must be
        // proportional to the 0..1 range (not the ±100 adjustment tools).
        EditTool.SKIN_DENOISE -> viewModel.updateSkinDenoise(
            (state.skinDenoise + (if (step > 0) 0.05f else -0.05f)).coerceIn(0f, 1f)
        )
        else -> Unit
    }
}

/**
 * The single source of truth for preview interaction: a tool is either
 * pan/zoom-capable (Category A, the default) or non-pan/zoom-capable
 * (Category B). Category B is exactly `NON_COMPARE_TOOLS` below — the
 * non-pan/zoom-capable set IS the compare-excluded set; they are the same
 * tools. Every Category B tool:
 *
 *  - in its normal editing state holds still (viewport reset on entry, no
 *    stale pan/zoom) and does NOT provide hold-to-compare (a press there is
 *    the tool's own gesture — brush / drag the crop frame / place an overlay);
 *  - provides an eye (preview) button that, ONLY when enabled, turns on BOTH
 *    pan/zoom AND hold-to-compare and suspends the tool's own gestures.
 *
 * The one quirk: disabling the crop eye resets the viewport; every other eye
 * tool keeps its changed pan/scale when its eye is disabled.
 */
// The beauty pen sub-tool (EditTool.BEAUTY + BeautyTool.BRIGHTNESS_PEN) is a
// Category B brush exactly like the standalone PEN: its classification keys off
// the *sub-tool*, never the whole BEAUTY tool — every other beauty sub-tool stays
// Category A (eye on, pan/zoom always).
internal fun isBeautyPenTool(tool: EditTool?, beautyTool: BeautyTool?): Boolean =
    tool == EditTool.BEAUTY && beautyTool == BeautyTool.BRIGHTNESS_PEN

internal fun isNonPanZoomCapable(tool: EditTool?, beautyTool: BeautyTool?): Boolean =
    tool in NON_COMPARE_TOOLS || isBeautyPenTool(tool, beautyTool)

// Category B — non-pan/zoom-capable tools. CROP, FRAMES, STICKERS, TEXT, the
// TRANSFORM tool and all brush tools (PEN, MOSAIC, BLUR_BRUSH, OBJECT_REMOVAL)
// plus the beauty pen (BEAUTY + BRIGHTNESS_PEN — see [isNonPanZoomCapable],
// which consults the sub-tool, so the OTHER beauty sub-tools stay Category A).
// Eye row under the canvas (inspect label + eye button). Spec
// (effects_reference "Canvas layout and gesture scope"): the eye row is a
// fixed-height band — 56 dp × uiScale — reserved BEFORE the controls reserve,
// so the eye row never comes out of the tool bar + controls minimum.
internal val CANVAS_EYE_ROW_HEIGHT = 56.dp

// Portrait: the bottom tool bar + tool controls share ONE vertical budget with
// the letterboxed canvas — the window below the top bar minus the eye row
// (56 dp × uiScale), which is excluded BEFORE the split and never comes out of
// the canvas/controls floors. The split is letterbox-first with two levels
// (effects_reference "Canvas layout and gesture scope"): the canvas keeps its
// square cap when the window affords it, otherwise its 50% floor. The controls
// have NO percentage ceiling — they absorb every dp the letterbox leaves
// unused. The 30% figure only bounds the CONTROLS MINIMUM in the last-resort
// branch (structurally unreachable in portrait because 30% < 50%, reachable as
// the landscape panel's 70% floor when the 600 dp reserve exceeds half the
// window width).
private val CANVAS_CONTROLS_MIN_HEIGHT = 200.dp
internal const val PORTRAIT_CONTROLS_MIN_FRACTION = 0.30f

// Landscape mirror rule (effects_reference "Canvas layout and gesture scope"):
// the right controls panel shares ONE horizontal budget with the letterboxed
// canvas ([available width] split against [available height] − eye row). The
// canvas keeps its square cap → 50% floor → below-floor; the panel absorbs the
// remainder with no 70% ceiling. The 70% only bounds the PANEL MINIMUM in the
// last-resort branch.
internal val CANVAS_CONTROLS_MIN_WIDTH = 600.dp
internal const val LANDSCAPE_PANEL_MIN_FRACTION = 0.70f

/**
 * The portrait letterbox-first split (effects_reference "Canvas layout and
 * gesture scope"): one pair of eyes resolves BOTH the letterbox height and the
 * tool-controls height from the shared vertical budget
 * `availableHeight − 56 dp × uiScale` (the eye row is excluded BEFORE the split
 * and never comes out of the two floors). The letterbox spans `availableWidth`
 * and the split follows the two-level rule — square cap → 50% floor →
 * controls-minimum/remainder — so the two values always sum to the budget and
 * the controls have no percentage ceiling.
 *
 * @return (letterbox height, controls height).
 */
@VisibleForTesting
internal fun portraitLetterboxSplit(
    availableWidth: Dp,
    availableHeight: Dp,
    uiScale: Float = 1f
): Pair<Dp, Dp> {
    val budget = (availableHeight - CANVAS_EYE_ROW_HEIGHT * uiScale).coerceAtLeast(0.dp)
    val controlsMin = minOf(
        CANVAS_CONTROLS_MIN_HEIGHT * uiScale,
        budget * PORTRAIT_CONTROLS_MIN_FRACTION
    )
    val letterbox = when {
        // Square cap: the window holds the square canvas plus the controls min.
        budget >= availableWidth + controlsMin -> availableWidth
        // 50% floor: the window holds both floors (always true in portrait —
        // the controls min is capped at 30% of the budget).
        budget >= budget * 0.5f + controlsMin -> budget * 0.5f
        // Last resort: the controls keep their minimum and the letterbox the
        // rest (below its floor only if the window cannot hold both floors).
        else -> budget - controlsMin
    }.coerceIn(0.dp, budget)
    return letterbox to (budget - letterbox).coerceAtLeast(0.dp)
}

/** Portrait letterbox height for the shared canvas/controls budget. */
@VisibleForTesting
internal fun portraitCanvasHeight(
    availableWidth: Dp,
    availableHeight: Dp,
    uiScale: Float = 1f
): Dp = portraitLetterboxSplit(availableWidth, availableHeight, uiScale).first

/** Portrait tool-controls height — the split remainder past the letterbox. */
@VisibleForTesting
internal fun portraitControlsHeight(
    availableWidth: Dp,
    availableHeight: Dp,
    uiScale: Float = 1f
): Dp = portraitLetterboxSplit(availableWidth, availableHeight, uiScale).second

/**
 * The landscape letterbox-first split (effects_reference "Canvas layout and
 * gesture scope"): one pair of eyes resolves BOTH the letterbox width and the
 * right-panel width from the shared horizontal budget [availableWidth]. The
 * letterbox height is the square cap `availableHeight − 56 dp × uiScale` and
 * the split follows the two-level rule — square cap → 50% floor →
 * panel-minimum/remainder — so both values sum to the window width and the
 * panel has no percentage ceiling.
 *
 * @return (letterbox width, panel width).
 */
@VisibleForTesting
internal fun landscapeLetterboxSplit(
    availableWidth: Dp,
    availableHeight: Dp,
    uiScale: Float = 1f
): Pair<Dp, Dp> {
    val h = (availableHeight - CANVAS_EYE_ROW_HEIGHT * uiScale).coerceAtLeast(0.dp)
    val panelMin = minOf(
        CANVAS_CONTROLS_MIN_WIDTH * uiScale,
        availableWidth.coerceAtLeast(0.dp) * LANDSCAPE_PANEL_MIN_FRACTION
    )
    val letterbox = when {
        // Square cap: the window (width) holds the square letterbox + the panel min.
        availableWidth >= h + panelMin -> h
        // 50% floor: the window holds both floors.
        availableWidth >= availableWidth * 0.5f + panelMin -> availableWidth * 0.5f
        // Last resort: the panel keeps its minimum and the letterbox the rest
        // (below its floor — the reachable branch when the 600 dp panel reserve
        // exceeds half the window width).
        else -> availableWidth - panelMin
    }.coerceIn(0.dp, availableWidth.coerceAtLeast(0.dp))
    return letterbox to (availableWidth - letterbox).coerceAtLeast(0.dp)
}

/** Landscape letterbox width for the shared canvas/panel budget. */
@VisibleForTesting
internal fun landscapeCanvasWidth(
    availableWidth: Dp,
    availableHeight: Dp,
    uiScale: Float = 1f
): Dp = landscapeLetterboxSplit(availableWidth, availableHeight, uiScale).first

/** Landscape right-panel width — the split remainder past the letterbox. */
@VisibleForTesting
internal fun landscapePanelWidth(
    availableWidth: Dp,
    availableHeight: Dp,
    uiScale: Float = 1f
): Dp = landscapeLetterboxSplit(availableWidth, availableHeight, uiScale).second

/** Landscape when the visible area is wider than tall (split-screen/foldable safe). */
internal fun isLandscapeLayout(availableWidth: Dp, availableHeight: Dp): Boolean =
    availableWidth > availableHeight

private val NON_COMPARE_TOOLS = setOf(
    EditTool.CROP,
    EditTool.FRAMES,
    EditTool.STICKERS,
    EditTool.TEXT,
    EditTool.PEN,
    EditTool.MOSAIC,
    EditTool.BLUR_BRUSH,
    EditTool.OBJECT_REMOVAL,
    EditTool.TRANSFORM
)

// Whether holding the preview should reveal the original photo.
//  - Category A tools: always (holding shows the plain original).
//  - Category B tools: only while their eye (inspect/preview) button is enabled —
//    in the normal editing state a press-and-hold is the tool's own gesture and
//    would hide the user's placed object / drawn stroke / crop frame.
internal fun isCompareTool(
    tool: EditTool?,
    beautyTool: BeautyTool?,
    eyeEnabled: Boolean
): Boolean =
    tool == null || !isNonPanZoomCapable(tool, beautyTool) || eyeEnabled

// Applies the shared viewport pan/zoom (scale + translate, no rotation/flip —
// brush tools never rotate) to a raw source-space overlay so committed brush
// strokes / mosaic tiles follow the base image while the user pans and zooms.
internal fun Modifier.panZoomLayer(transform: ViewportTransform): Modifier =
    this.graphicsLayer {
        scaleX = transform.canvasZoom
        scaleY = transform.canvasZoom
        translationX = transform.offsetX
        translationY = transform.offsetY
    }

// Tools that draw their interactive overlays in the RAW (un-transformed) source
// coordinate space — crop grid, brush strokes, placed stickers, text boxes. When
// one of these is the active focused tool the preview stays on the raw live
// source so those overlays line up; the committed composite (which bakes the
// geometry) is shown at rest / for all other tools (see `useCompositePreview`).
// This is an ORTHOGONAL concern to pan/zoom capability: FRAMES and TRANSFORM
// are non-pan/zoom-capable (Category B) but have no source-space overlay,
// so neither is listed here.
private val SOURCE_SPACE_PREVIEW_TOOLS = setOf(
    EditTool.CROP,
    EditTool.STICKERS,
    EditTool.TEXT,
    EditTool.MOSAIC,
    EditTool.BLUR_BRUSH,
    EditTool.PEN,
    EditTool.OBJECT_REMOVAL
)

// Tools that draw their interactive overlays in the RAW (un-transformed) source
// coordinate space also include the beauty pen (BEAUTY + BRIGHTNESS_PEN) — see
// SOURCE_SPACE_PREVIEW_TOOLS above for the rationale; the classification keys off
// the beauty sub-tool (the other beauty sub-tools fold to the full composite).
internal fun isSourceSpaceTool(tool: EditTool?, beautyTool: BeautyTool?): Boolean =
    tool in SOURCE_SPACE_PREVIEW_TOOLS || isBeautyPenTool(tool, beautyTool)

/**
 * The composite fold flags for a source-space tool (docs/effect_stack.md → *Eye
 * toggle and the current tool*): eye ON shows the whole committed stack; eye OFF
 * stops the fold AT the current tool — the tool's own layer stays folded (not
 * excluded) while its live-apply has fired, otherwise only the layers before the
 * tool run (the overlay draws the interactive strokes). The tail never folds past
 * the current tool with the eye off, live-apply or not.
 */
internal fun liveFoldFlags(eyeEnabled: Boolean, liveApply: Boolean): Pair<Boolean, Boolean> =
    Pair(eyeEnabled, !eyeEnabled && !liveApply)

@Composable
internal fun filterChipStyle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val s = LocalUiScale.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                )
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

private fun PenBrushType.toPenBrushKind(): PenBrushKind = when (this) {
    PenBrushType.SOLID -> PenBrushKind.SOLID
    PenBrushType.GLOW -> PenBrushKind.GLOW
    PenBrushType.NEON -> PenBrushKind.NEON
    PenBrushType.RAINBOW -> PenBrushKind.RAINBOW
}

/**
 * The fitted image rect (ContentScale.Fit) within the editor box, in box px:
 * [offsetX]/[offsetY] is the letterbox top-left and [size] the on-screen image
 * size. Source-space tools (pen/mosaic/blur/removal strokes, text, stickers)
 * normalize captured box px against THIS rect — subtracting the letterbox
 * offset and dividing by its size — so the baked source fraction equals the
 * fraction the user actually painted over, at every zoom (P2).
 */
data class ImageCanvasFrame(
    val offsetX: Float,
    val offsetY: Float,
    val size: Size
) {
    companion object {
        /** Whole-box frame (offset 0, size 0): no image known / letterbox-free. */
        val DEFAULT = ImageCanvasFrame(offsetX = 0f, offsetY = 0f, size = Size.Zero)
    }
}

/**
 * The anchored capture/restore frame of one source-space family
 * (docs/effect_stack.md → *Positional layers*): the frame is the family's
 * committed PREFIX footprint fitted into the box (the rect the layer's real
 * fold base would display at), and the full-size [refWidth]×[refHeight] are
 * that footprint — the reference each captured fraction is stored against.
 */
internal data class FamilyAnchor(
    val frame: ImageCanvasFrame,
    val refWidth: Int,
    val refHeight: Int
)

internal fun imageFrameOf(box: Size, image: Size): ImageCanvasFrame {
    val offX = if (box.width > 0f && image.width > 0f && box.width > image.width) {
        (box.width - image.width) / 2f
    } else {
        0f
    }
    val offY = if (box.height > 0f && image.height > 0f && box.height > image.height) {
        (box.height - image.height) / 2f
    } else {
        0f
    }
    return ImageCanvasFrame(offsetX = offX, offsetY = offY, size = image)
}

/**
 * The [FamilyAnchor] (fitted frame + full-size reference dims) of [tool]'s
 * family in [effects], against the stack's TRUE fold base [baseImageSize].
 * [familyPrefixSize] folds the committed prefix — which already contains any
 * Transform/Crop below the family — from that base, so the reference is the
 * family's ACTUAL own-position base footprint, never the current viewport's
 * (a pre-rotated `rotatedImageSize` would double-count the rotation already in
 * the prefix). Pure so the reference-capture rule is unit-testable without the
 * composable.
 */
internal fun familyAnchorFor(
    effects: List<StackEffect>,
    tool: EditTool,
    baseImageSize: Size,
    editBoxSize: Size
): FamilyAnchor {
    val prefix = familyPrefixSize(effects, tool, baseImageSize)
    return FamilyAnchor(
        frame = imageFrameOf(editBoxSize, fittedImageSize(editBoxSize, prefix)),
        refWidth = prefix.width.roundToInt(),
        refHeight = prefix.height.roundToInt()
    )
}

/**
 * The full-size footprint of the committed stack BEFORE the first [tool] layer
 * (docs/effect_stack.md → *Positional layers*): the pixel base that tool's
 * layer actually folds over. With no committed layer for [tool] (index −1) the
 * new layer would append at the END of the stack, so its base is the whole
 * committed stack. Computed with the pure size estimators — never a fold.
 */
internal fun familyPrefixSize(
    effects: List<StackEffect>,
    tool: EditTool,
    base: Size
): Size {
    if (base.width <= 0f || base.height <= 0f) return Size.Zero
    val idx = effects.indexOfFirst { it.tool == tool }
    val prefix = if (idx < 0) effects else effects.take(idx)
    val (w, h) = estimateStackOutputSize(prefix, base.width.roundToInt(), base.height.roundToInt())
    return Size(w.toFloat(), h.toFloat())
}

/**
 * The reference dims of a [FamilyAnchor] as a [GeometryAnchor].
 */
internal fun anchorOf(anchor: FamilyAnchor): GeometryAnchor =
    GeometryAnchor(anchor.refWidth, anchor.refHeight)

/**
 * The anchored reference base carried by a committed positional layer — the
 * reference of its FIRST item (every item of a family is captured against the
 * same anchor). `0×0` when the layer has no items or was never anchored.
 */
internal fun storedRefOfStickers(layers: List<StickerItem>?): GeometryAnchor =
    GeometryAnchor(
        layers?.firstOrNull()?.referenceWidth ?: 0,
        layers?.firstOrNull()?.referenceHeight ?: 0
    )

internal fun storedRefOfTexts(layers: List<TextItem>?): GeometryAnchor =
    GeometryAnchor(
        layers?.firstOrNull()?.referenceWidth ?: 0,
        layers?.firstOrNull()?.referenceHeight ?: 0
    )

internal fun storedRefOfStrokes(layers: List<StrokeLayer>?): GeometryAnchor =
    GeometryAnchor(
        layers?.firstOrNull()?.referenceWidth ?: 0,
        layers?.firstOrNull()?.referenceHeight ?: 0
    )

/** The committed stroke list of a brush [tool], or null when not in the stack. */
private fun storedStrokes(effects: List<StackEffect>, tool: EditTool): List<StrokeLayer>? =
    (effects.lastOrNull { it is BitmapDiffEffect && it.tool == tool } as? BitmapDiffEffect)?.strokes

/**
 * The ContentScale.Fit size of [canvasSize] inside [box] (both in px): the
 * canvas scale is the smaller of the two axis ratios, mirroring exactly what
 * the editor's image/composite composable (and the crop grid's `fitRect`) show.
 * Returns [Size.Zero] while either input is degenerate — the same guard the
 * inline fit previously carried in `imageViewSize`.
 */
internal fun fittedImageSize(box: Size, canvasSize: Size): Size {
    if (box.width <= 0f || box.height <= 0f ||
        canvasSize.width <= 0f || canvasSize.height <= 0f
    ) {
        return Size.Zero
    }
    // docs/effect_stack.md → Scale terms: `canvasFitScale` — a bitmap's
    // on-screen side ÷ its own pixel side (the letterbox fit ratio, before any
    // user zoom). Extracted as a named field (the preview/fold path shares the
    // same formula via the planner).
    val canvasFitScale = (box.width / canvasSize.width)
        .coerceAtMost(box.height / canvasSize.height)
    return Size(canvasSize.width * canvasFitScale, canvasSize.height * canvasFitScale)
}

/**
 * Builds a user-editable [TextOverlay] from an OCR fragment, placed inside the
 * edit box at the detected line's position. The image is centered in the edit
 * box, so we offset by the letterbox (container - image)/2, then map the
 * fragment's normalized anchor into dp. Font size keeps the detected line's
 * height proportion of the image (the glyphs are as tall as the source line),
 * and the fragment's detected rotation and ink colour are reapplied so the
 * imported item reconstructs the source region's position/size/rotation/colour.
 */
internal fun buildOcrTextOverlay(
    fragment: OcrOverlayFragment,
    density: Density,
    containerPx: Size,
    imagePx: Size
): TextOverlay {
    val imageLeft = if (containerPx.width > imagePx.width) {
        (containerPx.width - imagePx.width) / 2f
    } else {
        0f
    }
    val imageTop = if (containerPx.height > imagePx.height) {
        (containerPx.height - imagePx.height) / 2f
    } else {
        0f
    }
    val xPx = imageLeft + fragment.normalizedX * imagePx.width
    val yPx = imageTop + fragment.normalizedY * imagePx.height
    val fontSizePx = fragment.fontSizeFraction * imagePx.height
    val scale = 1f / density.density
    return TextOverlay(
        id = java.util.UUID.randomUUID().toString(),
        text = fragment.text,
        position = Offset(xPx * scale, yPx * scale),
        fontSize = fontSizePx * scale,
        rotation = fragment.rotationDegrees,
        color = fragment.colorArgb?.let { Color(it) } ?: Color.White,
        backgroundShape = TextBackgroundShape.BLUR,
        backgroundBlur = 0.65f
    )
}

/** The photo-look values that drive the live preview, snapshotted after they settle. */
private data class DebouncedLook(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val exposure: Float,
    val temperature: Float,
    val tint: Float,
    val shadows: Float,
    val highlights: Float,
    val filterId: String?,
    val lensIds: Set<String>
)

/**
 * Returns a debounced snapshot of the photo-look values: while any slider is
 * being dragged the returned value stays at the last settled state, and only
 * updates once the inputs have been unchanged for [PREVIEW_DEBOUNCE_MS]. The
 * initial composition returns the current inputs immediately.
 */
@Composable
private fun producePreviewLook(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    exposure: Float,
    temperature: Float,
    tint: Float,
    shadows: Float,
    highlights: Float,
    filterId: String?,
    lensIds: Set<String>
): DebouncedLook {
    var settled by remember {
        mutableStateOf(
            DebouncedLook(
                brightness, contrast, saturation, exposure, temperature, tint,
                shadows, highlights, filterId, lensIds
            )
        )
    }
    LaunchedEffect(
        brightness, contrast, saturation, exposure, temperature, tint,
        shadows, highlights, filterId, lensIds
    ) {
        delay(PREVIEW_DEBOUNCE_MS)
        settled = DebouncedLook(
            brightness, contrast, saturation, exposure, temperature, tint,
            shadows, highlights, filterId, lensIds
        )
    }
    return settled
}

/**
 * Returns the bounding-box display size of an image of [width]×[height] after it
 * is rotated by [rotationDegrees] (degrees). For 90° multiples this is the exact
 * dimension swap; for a custom/free angle it returns the grown bounding rectangle
 * that the actual rotated bitmap fills (kept consistent with
 * [codes.pepper.whimsicalart.feature.editor.domain.BitmapRenderer.transforms]).
 * [rotationDegrees] is not normalized here on purpose so it works with the live
 * slider values directly.
 */
internal fun rotatedBoundsSize(width: Float, height: Float, rotationDegrees: Float): Size {
    val normalized = ((rotationDegrees % 360f) + 360f) % 360f
    val quarterTurns = (normalized / 90f).roundToInt() % 4
    val remainder = normalized % 90f
    val isExact90 = remainder < 0.5f || remainder > 89.5f
    if (isExact90) {
        return if (quarterTurns % 2 == 1) Size(height, width) else Size(width, height)
    }
    val radians = Math.toRadians(normalized.toDouble())
    val cosA = abs(cos(radians)).toFloat()
    val sinA = abs(sin(radians)).toFloat()
    val outW = width * cosA + height * sinA
    val outH = width * sinA + height * cosA
    return Size(outW, outH)
}
