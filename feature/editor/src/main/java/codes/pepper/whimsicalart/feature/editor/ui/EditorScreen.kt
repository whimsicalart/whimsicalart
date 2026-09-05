package codes.pepper.whimsicalart.feature.editor.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.EditorColorMatrix
import codes.pepper.whimsicalart.feature.editor.domain.BitmapRenderer
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundLayer
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode
import codes.pepper.whimsicalart.feature.editor.domain.FrameLayer
import codes.pepper.whimsicalart.feature.editor.domain.SaveConfig
import codes.pepper.whimsicalart.feature.editor.domain.StickerLayer
import codes.pepper.whimsicalart.feature.editor.domain.StrokeLayer
import codes.pepper.whimsicalart.feature.editor.domain.StrokeType
import codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
import codes.pepper.whimsicalart.feature.editor.domain.TextLayer
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter
import codes.pepper.whimsicalart.feature.editor.domain.ocr.OcrOverlayFragment
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.filters.domain.FilterPresets
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPlacement
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPresets
import codes.pepper.whimsicalart.feature.stickers.ui.StickerPicker
import codes.pepper.whimsicalart.feature.stickers.ui.viewmodel.StickersViewModel
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurBrushOverlay
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurBrushPanel
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurViewModel
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicOverlay
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicPanel
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicViewModel
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenBrushPanel
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenStrokeOverlay
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenViewModel
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalOverlay
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalPanel
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalViewModel
import codes.pepper.whimsicalart.feature.editor.ui.background.BackgroundPanel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextEditorPanel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextEditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextOverlay
import codes.pepper.whimsicalart.feature.editor.ui.text.TextOverlayLayer
import codes.pepper.whimsicalart.feature.editor.ui.frames.FramePicker
import codes.pepper.whimsicalart.feature.editor.ui.frames.FramePreview
import codes.pepper.whimsicalart.feature.editor.ui.frames.FramePresets
import codes.pepper.whimsicalart.feature.editor.ui.layers.LayersPanel
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyControls
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
import codes.pepper.whimsicalart.feature.editor.domain.CropEffect
import codes.pepper.whimsicalart.feature.editor.domain.TransformEffect
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorUiState
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
    LaunchedEffect(uiState.selectedTool) {
        eyeEnabled = !isNonPanZoomCapable(uiState.selectedTool)
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

    // Removes an effect from the stack by resetting the underlying tool state to
    // neutral, so the derived snapshot drops the entry and the final image /
    // following thumbnails recompute.
    val clearTool: (EditTool) -> Unit = { tool ->
        when (tool) {
            EditTool.BRIGHTNESS -> viewModel.updateBrightness(0f)
            EditTool.CONTRAST -> viewModel.updateContrast(0f)
            EditTool.SATURATION -> viewModel.updateSaturation(0f)
            EditTool.SHARPEN -> viewModel.updateSharpness(0f)
            EditTool.EXPOSURE -> viewModel.updateExposure(0f)
            EditTool.SHADOWS -> viewModel.updateShadows(0f)
            EditTool.HIGHLIGHTS -> viewModel.updateHighlights(0f)
            EditTool.TEMPERATURE -> viewModel.updateTemperature(0f)
            EditTool.TINT -> viewModel.updateTint(0f)
            EditTool.VIGNETTE -> viewModel.updateVignette(0f)
            EditTool.SKIN_DENOISE -> viewModel.updateSkinDenoise(0f)
            EditTool.ENHANCE -> if (viewModel.uiState.value.enhanceEnabled) viewModel.toggleEnhance()
            EditTool.FILTERS -> {
                viewModel.setStyleFilter(null)
                viewModel.setSelectedFilterId(null)
                viewModel.clearLensFilters()
            }
            EditTool.BACKGROUND -> viewModel.clearBackground()
            EditTool.MOSAIC -> mosaicViewModel.clear()
            EditTool.BLUR_BRUSH -> blurViewModel.clear()
            EditTool.PEN -> penViewModel.clear()
            EditTool.OBJECT_REMOVAL -> removalViewModel.clear()
            EditTool.STICKERS -> stickersViewModel.clearAllStickers()
            EditTool.TEXT -> textEditorViewModel.clearAllText()
            EditTool.FRAMES -> viewModel.updateFrame(null)
            EditTool.CROP -> viewModel.clearCrop()
            EditTool.TRANSFORM -> viewModel.clearTransform()
            EditTool.BEAUTY -> viewModel.clearBeautyEffects()
        }
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
    // bitmap region that is actually cropped on save.
    val rotatedImageSize = remember(uiState.originalBitmap, uiState.rotation) {
        val bmp = uiState.originalBitmap
        if (bmp == null) Size.Zero
        else rotatedBoundsSize(bmp.width.toFloat(), bmp.height.toFloat(), uiState.rotation)
    }
    // Single shared zoom/pan transform for the photo viewport. Hoisted here so
    // the tap-and-hold compare overlay reveals the original at the same scale,
    // position, rotation and flips as the live preview they are looking at.
    val viewportTransform = remember { ViewportTransform() }

    // Pan/zoom capability of the current screen. Crop + brush tools are
    // "non pan/zoom-capable" by default (their single-finger drags paint or
    // drag the crop frame instead of moving the photo). The eye toggle re-enables
    // pan/zoom for inspection; all other tools are pan/zoom-capable by default.
    val selectedTool = uiState.selectedTool
    // Everything derives from the single classification: Category B tools (the
    // NON_COMPARE_TOOLS set = non-pan/zoom-capable) can pan/zoom only while the
    // eye is enabled; every other (Category A) tool is pan/zoom-capable always.
    val canPanZoom = if (isNonPanZoomCapable(selectedTool)) eyeEnabled else true

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
    // image fits at the default position/scale.
    LaunchedEffect(selectedTool) {
        if (isNonPanZoomCapable(selectedTool)) {
            viewportTransform.reset()
        }
    }
    // Crop-only quirk: disabling the crop eye button resets the viewport to the
    // original position and scale; every other eye tool keeps its changed
    // pan/scale when the eye is disabled.
    LaunchedEffect(selectedTool, eyeEnabled) {
        if (selectedTool == EditTool.CROP && !eyeEnabled) {
            viewportTransform.reset()
        }
    }

    // When the Crop tool's "eye" preview-now toggle is on, show the actual
    // cropped region (already oriented by rotation/flips) as the displayed
    // image, so the user sees the final composition instead of the full photo
    // with a grid. Computed once per state change; null means no crop preview.
    val cropPreviewBitmap = remember(
        uiState.originalBitmap, uiState.cropRect,
        uiState.rotation, uiState.flipHorizontal, uiState.flipVertical,
        eyeEnabled
    ) {
        if (!eyeEnabled) {
            null
        } else {
            val base = uiState.originalBitmap
            if (base == null) {
                null
            } else {
                BitmapRenderer.cropPreview(
                    base,
                    uiState.rotation,
                    uiState.flipHorizontal,
                    uiState.flipVertical,
                    uiState.cropRect
                )
            }
        }
    }
    // Cheap header-only bounds decode (no full bitmap) so overlays like the
    // vignette can be confined to the actual ContentScale.Fit image rect
    // instead of painting over the whole viewport (letterbox bars included).
    val context = LocalContext.current
    val sourceBounds = remember(imageUri) {
        runCatching {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    Size(options.outWidth.toFloat(), options.outHeight.toFloat())
                } else {
                    null
                }
            }
        }.getOrNull() ?: null
    }
    val rotatedPreviewSize = remember(sourceBounds, uiState.rotation) {
        if (sourceBounds == null || sourceBounds.width <= 0f) Size.Zero
        else rotatedBoundsSize(sourceBounds.width, sourceBounds.height, uiState.rotation)
    }
    var showSaveDialog by remember { mutableStateOf(false) }
    var recognizingText by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editBoxSize by remember { mutableStateOf(Size.Zero) }
    val imageViewSize = if (editBoxSize.width > 0f && rotatedPreviewSize.width > 0f) {
        val scale = (editBoxSize.width / rotatedPreviewSize.width)
            .coerceAtMost(editBoxSize.height / rotatedPreviewSize.height)
        Size(rotatedPreviewSize.width * scale, rotatedPreviewSize.height * scale)
    } else {
        Size.Zero
    }
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
    val density = LocalDensity.current
    val keyboardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(imageUri) {
        viewModel.setImageUri(imageUri)
        val original = decodeBounded(context.contentResolver, imageUri)
        if (original != null) {
            viewModel.setBitmaps(original, original)
        }
    }

    LaunchedEffect(Unit) {
        keyboardFocusRequester.requestFocus()
    }

    LaunchedEffect(uiState.selectedTool) {
        if (uiState.selectedTool == EditTool.TEXT) {
            textEditorViewModel.startAddingText()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(keyboardFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                handleEditorKeyEvent(viewModel, event)
            }
    ) {
        TopAppBar(
            title = { Text("Edit Photo") },
            navigationIcon = {
                IconButton(onClick = { if (uiState.isLayersVisible) viewModel.toggleLayers() else onBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = { viewModel.toggleLayers() }) {
                        Icon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = "Layers",
                            tint = if (uiState.isLayersVisible) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.historyIndex > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (uiState.historyIndex > 0) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.historyIndex < uiState.history.lastIndex
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (uiState.historyIndex < uiState.history.lastIndex) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Save"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                // Clip the panned/zoomed preview to its own area so the image
                // content never draws over the top chrome (TopAppBar + Layers/
                // undo/redo/Save) or the bottom tool controls.
                .clipToBounds()
                .onSizeChanged {
                editBoxSize = Size(it.width.toFloat(), it.height.toFloat())
                viewModel.setViewBox(it.width, it.height)
            }
                .pointerInput(uiState.selectedTool, eyeEnabled) {
                    if (isCompareTool(uiState.selectedTool, eyeEnabled)) {
                        detectTapGestures(
                            onLongPress = { viewModel.startComparing() },
                            onPress = {
                                try {
                                    awaitRelease()
                                } finally {
                                    viewModel.stopComparing()
                                }
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ZoomableImage(
                model = imageUri,
                contentDescription = "Photo to edit",
                rotation = when {
                    useCompositePreview -> 0f
                    eyeEnabled -> 0f
                    else -> uiState.rotation
                },
                flipHorizontal = when {
                    useCompositePreview -> false
                    eyeEnabled -> false
                    else -> uiState.flipHorizontal
                },
                flipVertical = when {
                    useCompositePreview -> false
                    eyeEnabled -> false
                    else -> uiState.flipVertical
                },
                colorFilter = if (useCompositePreview) null else previewColorFilter,
                bitmapOverride = when {
                    useCompositePreview -> uiState.compositePreview?.asImageBitmap()
                    else -> cropPreviewBitmap?.asImageBitmap()
                        ?: uiState.sharpenedPreview?.asImageBitmap()
                },
                transform = viewportTransform,
                panZoomEnabled = canPanZoom
            )

            if (!useCompositePreview && uiState.vignette != 0f && imageViewSize.width > 0f) {
                PhotoBoundedLayer(
                    imageFitSize = imageFitSize,
                    rotation = uiState.rotation,
                    flipHorizontal = uiState.flipHorizontal,
                    flipVertical = uiState.flipVertical,
                    transform = viewportTransform
                ) {
                    VignetteOverlay(
                        strength = uiState.vignette,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (uiState.selectedTool == EditTool.CROP && !eyeEnabled) {
                CropOverlay(
                    imageSize = rotatedImageSize,
                    aspectRatio = selectedAspectRatio,
                    initialCrop = uiState.cropRect ?: Rect(0f, 0f, 1f, 1f),
                    onCropChanged = { viewModel.updateCrop(it) }
                )
            }

            if (uiState.selectedTool == EditTool.STICKERS && !eyeEnabled) {
                StickerOverlay(
                    placedStickers = stickersState.placedStickers,
                    selectedStickerId = stickersState.selectedStickerId,
                    onMove = { stickerId, position ->
                        stickersViewModel.moveSticker(stickerId, position)
                    },
                    onScale = { stickerId, scale ->
                        stickersViewModel.scaleSticker(stickerId, scale)
                    },
                    onSelect = { stickerId ->
                        stickersViewModel.selectSticker(stickerId)
                    },
                    interactionEnabled = !eyeEnabled
                )
            }

            if (uiState.selectedTool == EditTool.TEXT && !eyeEnabled &&
                textState.textOverlays.isNotEmpty()
            ) {
                TextOverlayLayer(
                    overlays = textState.textOverlays,
                    onMove = { overlayId, position ->
                        textEditorViewModel.moveOverlay(overlayId, position)
                    },
                    interactionEnabled = !eyeEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (uiState.selectedTool == EditTool.MOSAIC && !eyeEnabled) {
                MosaicOverlay(
                    strokes = mosaicState.strokes,
                    currentStroke = mosaicState.currentStroke,
                    suggestedRegions = mosaicState.suggestedRegions,
                    onDragStart = { mosaicViewModel.onDragStart(it) },
                    onDragMove = { mosaicViewModel.onDragMove(it) },
                    onDragEnd = { mosaicViewModel.onDragEnd() },
                    modifier = Modifier
                        .fillMaxSize()
                        .panZoomLayer(viewportTransform),
                    brushingEnabled = !eyeEnabled
                )
            }

            if (uiState.selectedTool == EditTool.BLUR_BRUSH && !eyeEnabled) {
                BlurBrushOverlay(
                    strokes = blurState.strokes,
                    currentStroke = blurState.currentStroke,
                    onDragStart = { blurViewModel.onDragStart(it) },
                    onDragMove = { blurViewModel.onDragMove(it) },
                    onDragEnd = { blurViewModel.onDragEnd() },
                    modifier = Modifier
                        .fillMaxSize()
                        .panZoomLayer(viewportTransform),
                    brushingEnabled = !eyeEnabled
                )
            }

            if (uiState.selectedTool == EditTool.PEN && !eyeEnabled) {
                PenStrokeOverlay(
                    strokes = penState.strokes,
                    currentStroke = penState.currentStroke,
                    onDragStart = { penViewModel.onDragStart(it) },
                    onDragMove = { penViewModel.onDragMove(it) },
                    onDragEnd = { penViewModel.onDragEnd() },
                    modifier = Modifier
                        .fillMaxSize()
                        .panZoomLayer(viewportTransform),
                    brushingEnabled = !eyeEnabled
                )
            }

            if (uiState.selectedTool == EditTool.OBJECT_REMOVAL && !eyeEnabled) {
                RemovalOverlay(
                    strokes = removalState.strokes,
                    currentStroke = removalState.currentStroke,
                    onDragStart = { removalViewModel.onDragStart(it) },
                    onDragMove = { removalViewModel.onDragMove(it) },
                    onDragEnd = { removalViewModel.onDragEnd() },
                    modifier = Modifier
                        .fillMaxSize()
                        .panZoomLayer(viewportTransform),
                    brushingEnabled = !eyeEnabled
                )
            }

            val frameId = uiState.selectedFrameId
            if (!useCompositePreview && frameId != null && imageViewSize.width > 0f) {
                FramePresets.getFrameById(frameId)?.let { frame ->
                    PhotoBoundedLayer(
                        imageFitSize = imageFitSize,
                        rotation = uiState.rotation,
                        flipHorizontal = uiState.flipHorizontal,
                        flipVertical = uiState.flipVertical,
                        transform = viewportTransform,
                        applyTransform = false
                    ) {
                        FramePreview(
                            frame = frame,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (uiState.isComparing && uiState.originalBitmap != null && uiState.editedBitmap != null) {
                CompareOverlay(
                    originalBitmap = uiState.originalBitmap!!,
                    editedBitmap = uiState.editedBitmap!!,
                    isComparing = true,
                    rotation = uiState.rotation,
                    flipHorizontal = uiState.flipHorizontal,
                    flipVertical = uiState.flipVertical,
                    transform = viewportTransform
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (uiState.selectedTool == EditTool.CROP) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AspectRatioSelector(
                        selectedAspectRatio = selectedAspectRatio,
                        onAspectRatioSelected = { selectedAspectRatio = it },
                        enabled = !eyeEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Shared inspect (preview) eye toggle for every non-pan/zoom-capable
            // (Category B) tool. Enabling it turns on pan/zoom and hold-to-compare
            // and suspends the tool's own gestures so the user can inspect/compare.
            val isCatB = isNonPanZoomCapable(uiState.selectedTool)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isCatB && uiState.selectedTool == EditTool.CROP ->
                            if (eyeEnabled) "Showing full result (inspect)" else "Edit / preview current crop"
                        isCatB ->
                            if (eyeEnabled) "Showing full result (inspect)" else "Show current effect only"
                        eyeEnabled -> "Showing full result"
                        else -> "Preview (without later effects)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { eyeEnabled = !eyeEnabled }) {
                    Icon(
                        imageVector = if (eyeEnabled) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (eyeEnabled) {
                            "Hide later effects / exit inspect"
                        } else {
                            "Show full result"
                        }
                    )
                }
            }

        if (uiState.selectedTool == EditTool.TRANSFORM) {
            TransformToolbar(
                rotation = uiState.rotation,
                onRotationChanged = { viewModel.setRotation(it) },
                onFlip = { tool ->
                    when (tool) {
                        TransformTool.FLIP_HORIZONTAL -> viewModel.flipHorizontal()
                        TransformTool.FLIP_VERTICAL -> viewModel.flipVertical()
                        else -> Unit
                    }
                }
            )
        }

        EditorToolbar(
            selectedTool = uiState.selectedTool,
            onToolSelected = { viewModel.selectTool(it) }
        )

        when (uiState.selectedTool) {
            EditTool.STICKERS -> {
                StickerPicker(
                    selectedCategory = stickersState.selectedCategory,
                    onCategorySelected = { stickersViewModel.selectCategory(it) },
                    stickers = stickersState.availableStickers,
                    onStickerSelected = { sticker ->
                        stickersViewModel.placeSticker(
                            sticker,
                            Offset(100f, 100f)
                        )
                    }
                )
            }
            EditTool.BLUR_BRUSH -> {
                BlurBrushPanel(
                    state = blurState,
                    onBrushSizeChange = { blurViewModel.updateBrushSize(it) },
                    onOpacityChange = { blurViewModel.updateOpacity(it) },
                    onToggleErasing = { blurViewModel.toggleErasing() },
                    onClear = { blurViewModel.clear() }
                )
            }
            EditTool.MOSAIC -> {
                MosaicPanel(
                    state = mosaicState,
                    imageUri = uiState.imageUri,
                    onBrushTypeChange = { mosaicViewModel.selectBrushType(it) },
                    onBrushSizeChange = { mosaicViewModel.updateBrushSize(it) },
                    onOpacityChange = { mosaicViewModel.updateOpacity(it) },
                    onToggleErasing = { mosaicViewModel.toggleErasing() },
                    onClear = { mosaicViewModel.clear() },
                    onSuggestFaces = { mosaicViewModel.suggestPrivacyRegions(uiState.imageUri) },
                    onClearSuggestions = { mosaicViewModel.clearSuggestions() }
                )
            }
            EditTool.PEN -> {
                PenBrushPanel(
                    state = penState,
                    onBrushTypeChange = { penViewModel.selectBrushType(it) },
                    onColorChange = { penViewModel.selectColor(it) },
                    onBrushSizeChange = { penViewModel.updateBrushSize(it) },
                    onUndo = { penViewModel.undo() },
                    onRedo = { penViewModel.redo() },
                    onClear = { penViewModel.clear() },
                    onAddLayer = { penViewModel.addLayer() },
                    onRemoveLayer = { penViewModel.removeLayer() },
                    onSelectLayer = { penViewModel.selectLayer(it) },
                    onToggleLayerVisibility = { penViewModel.toggleLayerVisibility(it) },
                    onMoveLayerToTop = { penViewModel.moveLayerToTop(it) }
                )
            }
            EditTool.OBJECT_REMOVAL -> {
                RemovalPanel(
                    state = removalState,
                    onBrushSizeChange = { removalViewModel.updateBrushSize(it) },
                    onToggleErasing = { removalViewModel.toggleErasing() },
                    onClear = { removalViewModel.clear() }
                )
            }
            EditTool.BACKGROUND -> {
                BackgroundPanel(
                    mode = uiState.backgroundMode,
                    blurRadius = uiState.backgroundBlurRadius,
                    shape = uiState.backgroundShape,
                    hasSubjectMask = uiState.subjectMask != null,
                    isSegmenting = uiState.isSegmenting,
                    selectedBackgroundRes = uiState.selectedBackgroundRes,
                    hasCustomBackground = uiState.backgroundImage != null && uiState.selectedBackgroundRes == null,
                    onModeChange = { viewModel.updateBackgroundMode(it) },
                    onBlurRadiusChange = { viewModel.updateBackgroundBlurRadius(it) },
                    onShapeChange = { viewModel.updateBackgroundShape(it) },
                    onSegmentSubject = { viewModel.segmentSubject() },
                    onClearSubject = { viewModel.clearSubjectMask() },
                    onSelectPreset = { res -> viewModel.loadBackground(res) },
                    onCustomBackground = { uri -> viewModel.setCustomBackground(uri) },
                    onClearBackground = { viewModel.clearBackground() }
                )
            }
            EditTool.TEXT -> {
                if (textState.isEditing) {
                    TextEditorPanel(
                        state = textState,
                        onTextChange = { textEditorViewModel.updateCurrentText(it) },
                        onFontSizeChange = { textEditorViewModel.updateFontSize(it) },
                        onColorChange = { textEditorViewModel.updateColor(it) },
                        onBackgroundShapeChange = {
                            textEditorViewModel.updateBackgroundShape(it)
                        },
                        onBackgroundColorChange = {
                            textEditorViewModel.updateBackgroundColor(it)
                        },
                        onAdd = {
                            textEditorViewModel.addText(Offset(120f, 120f))
                        },
                        onCancel = {
                            textEditorViewModel.startAddingText()
                            viewModel.selectTool(null)
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        TextButton(
                            onClick = { textEditorViewModel.startAddingText() }
                        ) {
                            Text("Add New Text")
                        }
                        TextButton(
                            enabled = !recognizingText,
                            onClick = {
                                scope.launch {
                                    recognizingText = true
                                    val fragments = withContext(Dispatchers.Default) {
                                        viewModel.recognizeText()
                                    }
                                    recognizingText = false
                                    val overlays = fragments.map { frag ->
                                        buildOcrTextOverlay(frag, density, editBoxSize, imageViewSize)
                                    }
                                    textEditorViewModel.addRecognizedOverlays(overlays)
                                    if (overlays.isEmpty()) {
                                        snackbarHostState.showSnackbar("No text recognized in this photo.")
                                    }
                                }
                            }
                        ) {
                            Text(if (recognizingText) "Recognizing…" else "Recognize Text")
                        }
                    }
                }
            }
            EditTool.FRAMES -> {
                FramePicker(
                    selectedFrameId = uiState.selectedFrameId,
                    onFrameSelected = { frameId -> viewModel.updateFrame(frameId) }
                )
            }
            EditTool.SKIN_DENOISE -> {
                AdjustmentSlider(
                    label = "Skin Denoise",
                    value = uiState.skinDenoise,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateSkinDenoise(it) }
                )
            }
            EditTool.BRIGHTNESS -> {
                AdjustmentSlider(
                    label = "Brightness",
                    value = uiState.brightness,
                    onValueChange = { viewModel.updateBrightness(it) }
                )
            }
            EditTool.CONTRAST -> {
                AdjustmentSlider(
                    label = "Contrast",
                    value = uiState.contrast,
                    onValueChange = { viewModel.updateContrast(it) }
                )
            }
            EditTool.SATURATION -> {
                AdjustmentSlider(
                    label = "Saturation",
                    value = uiState.saturation,
                    onValueChange = { viewModel.updateSaturation(it) }
                )
            }
            EditTool.SHARPEN -> {
                AdjustmentSlider(
                    label = "Sharpness",
                    value = uiState.sharpness,
                    onValueChange = { viewModel.updateSharpness(it) },
                    valueRange = 0f..100f
                )
            }
            EditTool.EXPOSURE -> {
                AdjustmentSlider(
                    label = "Exposure",
                    value = uiState.exposure,
                    onValueChange = { viewModel.updateExposure(it) }
                )
            }
            EditTool.SHADOWS -> {
                AdjustmentSlider(
                    label = "Shadows",
                    value = uiState.shadows,
                    onValueChange = { viewModel.updateShadows(it) }
                )
            }
            EditTool.HIGHLIGHTS -> {
                AdjustmentSlider(
                    label = "Highlights",
                    value = uiState.highlights,
                    onValueChange = { viewModel.updateHighlights(it) }
                )
            }
            EditTool.TEMPERATURE -> {
                AdjustmentSlider(
                    label = "Temperature",
                    value = uiState.temperature,
                    onValueChange = { viewModel.updateTemperature(it) }
                )
            }
            EditTool.TINT -> {
                AdjustmentSlider(
                    label = "Tint",
                    value = uiState.tint,
                    onValueChange = { viewModel.updateTint(it) }
                )
            }
            EditTool.VIGNETTE -> {
                AdjustmentSlider(
                    label = "Vignette",
                    value = uiState.vignette,
                    onValueChange = { viewModel.updateVignette(it) },
                    valueRange = 0f..100f
                )
            }
            EditTool.ENHANCE -> {
                FilterChip(
                    selected = uiState.enhanceEnabled,
                    onClick = { viewModel.toggleEnhance() },
                    label = {
                        Text(
                            if (uiState.enhanceEnabled) "Auto-Enhance On" else "Auto-Enhance Off"
                        )
                    }
                )
            }
            EditTool.FILTERS -> {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Color Filters",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterPresets.colorFilters.forEach { filter ->
                            filterChipStyle(
                                label = filter.name,
                                selected = if (filter.id == "original") {
                                    uiState.styleFilter == null &&
                                        (selectedFilterId == null || selectedFilterId == "original")
                                } else {
                                    selectedFilterId == filter.id
                                },
                                onClick = { viewModel.setSelectedFilterId(filter.id) }
                            )
                        }
                        StyleFilter.entries.forEach { sf ->
                            filterChipStyle(
                                label = sf.displayName,
                                selected = uiState.styleFilter == sf,
                                onClick = { viewModel.setStyleFilter(sf) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Lens Filters",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterPresets.lensFilters.forEach { filter ->
                            filterChipStyle(
                                label = filter.name,
                                selected = filter.id in uiState.enabledLensFilterIds,
                                onClick = { viewModel.toggleLensFilter(filter.id) }
                            )
                        }
                    }
                }
            }
            EditTool.BEAUTY -> {
                BeautyControls(
                    // Beauty is a set of reversible sub-tool layers in the single
                    // effectStack: each folds at its user-chosen position over the
                    // effects before it (only its ML geometry is resolved against
                    // the pristine-geometry image). The editor owns the per-tool
                    // params (uiState.beauty) and re-derives the ordered
                    // BeautyStackEffect layers from them on every change, so the
                    // stateless panel just reports current values + edits.
                    params = uiState.beauty,
                    selectedTool = uiState.selectedBeautyTool,
                    onToolSelected = { viewModel.selectBeautyTool(it) },
                    onChange = { viewModel.setBeautyParams(it) }
                )
            }
            else -> {}
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    fun buildRenderBundle(): EditorRenderBundle {
        val bw = editBoxSize.width
        val bh = editBoxSize.height
        fun normX(x: Float) = if (bw > 0f) (with(density) { x.dp.toPx() }) / bw else 0f
        fun normY(y: Float) = if (bh > 0f) (with(density) { y.dp.toPx() }) / bh else 0f

        val stickerLayers = stickersState.placedStickers.mapNotNull { placement ->
            val sticker = StickerPresets.getStickerById(placement.stickerId) ?: return@mapNotNull null
            if (bw <= 0f || bh <= 0f) return@mapNotNull null
            StickerLayer(
                drawableRes = sticker.drawableRes,
                x = normX(placement.x),
                y = normY(placement.y),
                width = normX(sticker.width * placement.scaleX),
                height = normY(sticker.height * placement.scaleY),
                rotationDegrees = placement.rotation,
                opacity = placement.opacity
            )
        }

        val textLayers = textState.textOverlays.map { overlay ->
            TextLayer(
                text = overlay.text,
                color = overlay.color.toArgb(),
                fontSizeSp = normX(overlay.fontSize),
                x = normX(overlay.position.x),
                y = normY(overlay.position.y),
                rotationDegrees = overlay.rotation
            )
        }

        val strokeLayers = buildList {
            mosaicState.strokes.forEach { s ->
                add(
                    StrokeLayer(
                        type = StrokeType.MOSAIC,
                        points = s.points.map { p ->
                            (if (bw > 0f) p.x / bw else 0f) to (if (bh > 0f) p.y / bh else 0f)
                        },
                        brushSize = s.brushSize / (if (bw > 0f) bw else 1f),
                        opacity = s.opacity
                    )
                )
            }
            blurState.strokes.forEach { s ->
                add(
                    StrokeLayer(
                        type = StrokeType.BLUR,
                        points = s.points.map { p ->
                            (if (bw > 0f) p.x / bw else 0f) to (if (bh > 0f) p.y / bh else 0f)
                        },
                        brushSize = s.brushSize / (if (bw > 0f) bw else 1f),
                        opacity = s.opacity
                    )
                )
            }
            penState.strokes.forEach { s ->
                add(
                    StrokeLayer(
                        type = StrokeType.PEN,
                        points = s.points.map { p ->
                            (if (bw > 0f) p.x / bw else 0f) to (if (bh > 0f) p.y / bh else 0f)
                        },
                        brushSize = s.size / (if (bw > 0f) bw else 1f),
                        color = s.color.toArgb()
                    )
                )
            }
            removalState.strokes.forEach { s ->
                add(
                    StrokeLayer(
                        type = StrokeType.REMOVAL,
                        points = s.points.map { p ->
                            (if (bw > 0f) p.x / bw else 0f) to (if (bh > 0f) p.y / bh else 0f)
                        },
                        brushSize = s.brushSize / (if (bw > 0f) bw else 1f)
                    )
                )
            }
        }

        val frameLayers = if (bw > 0f) {
            uiState.selectedFrameId
                ?.let { id -> FramePresets.getFrameById(id) }
                ?.let { frame ->
                    listOf(
                        FrameLayer(
                            borderWidth = with(density) { frame.borderWidth.dp.toPx() } / bw,
                            cornerRadius = with(density) { frame.cornerRadius.dp.toPx() } / bw,
                            color = frame.borderColor,
                            shadowColor = if (frame.hasShadow) frame.shadowColor else null,
                            shadowRadius = with(density) { frame.shadowRadius.dp.toPx() } / bw
                        )
                    )
                }
                ?: emptyList()
        } else {
            emptyList()
        }

        val backgroundLayer = uiState.subjectMask?.let { mask ->
            BackgroundLayer(
                mode = uiState.backgroundMode,
                subjectMask = mask,
                blurRadius = uiState.backgroundBlurRadius,
                shape = uiState.backgroundShape,
                backgroundImage = uiState.backgroundImage
            )
        }

        return EditorRenderBundle(
            filterMatrix = FilterPresets.concatFilterMatrices(
                uiState.selectedFilterId, uiState.enabledLensFilterIds
            ),
            styleFilter = uiState.styleFilter,
            stickers = stickerLayers,
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
    val commitCurrentTool: () -> Unit = {
        if (editBoxSize.width > 0f) {
            val snapshot = buildStackSnapshot(uiState = uiState, bundle = buildRenderBundle())
            viewModel.syncLayerState(snapshot, stackFingerprints(snapshot))
        }
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
    // data-class snapshot copy keeps all the committed-parameter fields but drops
    // the derived preview buffers.
    val compositeKey = uiState.copy(
        compositePreview = null,
        sharpenedPreview = null
    )

    // Per-layer previews are built in the background at the current preview
    // resolution inside the ViewModel (one incremental fold pass) whenever the
    // Layers window opens or its stack changes; the row thumbnails are published
    // as downscales of the cached previews — no full fold ever runs for the
    // Layers path. Rows show the placeholder box until their preview arrives.
    LaunchedEffect(uiState.isLayersVisible, uiState.effectStack) {
        if (!uiState.isLayersVisible) return@LaunchedEffect
        if (uiState.effectStack.isEmpty()) return@LaunchedEffect
        viewModel.refreshLayerPreviews(buildRenderBundle())
    }

    LaunchedEffect(
        compositeKey,
        eyeEnabled,
        stickersState,
        textState,
        mosaicState,
        blurState,
        penState,
        removalState
    ) {
        if (editBoxSize.width <= 0f) return@LaunchedEffect
        if (uiState.selectedTool in SOURCE_SPACE_PREVIEW_TOOLS) {
            // Source-space placement tools draw their own live overlay (strokes /
            // crop grid / stickers / text), so the composite base must be the fold
            // of the effects BEFORE the tool — eye OFF: exclude the tool's own
            // layer (the overlay supplies it interactively); eye ON: show the full
            // committed composite with the layer baked in.
            viewModel.refreshCompositePreview(
                buildRenderBundle(),
                includeTail = true,
                excludeCurrentTool = !eyeEnabled
            )
        } else {
            // Eye ON → full composite (prefix + current + tail); eye OFF → only
            // prefix + current (tail folded out). Tail folding applies to the
            // non-source-space (Category A) tools shown via the composite preview.
            viewModel.refreshCompositePreview(
                buildRenderBundle(),
                includeTail = eyeEnabled
            )
        }
    }

    // A zoom change re-derives the preview resolution (docs/effect_stack.md →
    // Choosing preview resolution) and, past 100%, also drives a full-size
    // composite swap. Keyed off the pure Compose transform so it never re-fires
    // the stack effect above; debounced + best-latest so a fast pinch only
    // triggers one render after the gesture settles, coalesced by the ViewModel.
    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        snapshotFlow { viewportTransform.scale }
            .drop(1)
            .debounce(300L)
            .collectLatest { zoom ->
                if (editBoxSize.width <= 0f) return@collectLatest
                val visibleWidthPx = editBoxSize.width.toInt()
                val visibleHeightPx = editBoxSize.height.toInt()
                if (uiState.selectedTool in SOURCE_SPACE_PREVIEW_TOOLS) {
                    viewModel.refreshCompositePreview(
                        buildRenderBundle(),
                        includeTail = true,
                        excludeCurrentTool = !eyeEnabled,
                        visibleWidthPx = visibleWidthPx,
                        visibleHeightPx = visibleHeightPx,
                        zoom = zoom
                    )
                } else {
                    viewModel.refreshCompositePreview(
                        buildRenderBundle(),
                        includeTail = eyeEnabled,
                        visibleWidthPx = visibleWidthPx,
                        visibleHeightPx = visibleHeightPx,
                        zoom = zoom
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
                viewModel.shareImage(config, buildRenderBundle())
            }
        )
    }

    if (uiState.isLayersVisible) {
        LayersPanel(
            effects = uiState.effectStack,
            thumbnails = uiState.layerThumbnails,
            hasMerged = uiState.hasMerged,
            onSelect = { effect -> viewModel.selectStackEffect(effect.id) },
            onRemove = { effect ->
                when (effect) {
                    is codes.pepper.whimsicalart.feature.editor.domain.FilterEffect ->
                        viewModel.setSelectedFilterId(null)
                    else -> clearTool(effect.tool)
                }
            },
            onMerge = { viewModel.mergeLayers(buildRenderBundle()) },
            onReorder = { from, to -> viewModel.moveEffect(from, to) },
            onBack = { viewModel.toggleLayers() }
        )
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
private fun buildStackSnapshot(
    uiState: EditorUiState,
    bundle: EditorRenderBundle
): List<StackEffect> {
    val list = mutableListOf<StackEffect>()

    // Beauty: pulled straight from the single ordered effectStack (honouring
    // user-chosen position, so `syncLayerState`'s orderedMerge preserves it).
    list += uiState.effectStack.filterIsInstance<BeautyStackEffect>()

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

    // Filter-strip look.
    if (bundle.filterMatrix != null) list += FilterEffect(filterMatrix = bundle.filterMatrix)

    if (uiState.skinDenoise != 0f) list += SkinDenoiseEffect(intensity = uiState.skinDenoise)
    if (uiState.enhanceEnabled) list += EnhanceEffect(enabled = true)
    if (bundle.styleFilter != null) list += StyleEffect(filter = bundle.styleFilter)
    bundle.background?.let { bg ->
        list += BackgroundEffect(
            mode = bg.mode,
            subjectMask = bg.subjectMask,
            blurRadius = bg.blurRadius,
            shape = bg.shape,
            backgroundImage = bg.backgroundImage
        )
    }

    // Brush / overlay layers carry their real content.
    if (bundle.strokes.any { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.PEN }) {
        list += BitmapDiffEffect(
            tool = EditTool.PEN,
            strokes = bundle.strokes.filter { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.PEN }
        )
    }
    if (bundle.strokes.any { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.MOSAIC }) {
        list += BitmapDiffEffect(
            tool = EditTool.MOSAIC,
            strokes = bundle.strokes.filter { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.MOSAIC },
            suggestedRegions = bundle.suggestedRegions
        )
    }
    if (bundle.strokes.any { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.BLUR }) {
        list += BitmapDiffEffect(
            tool = EditTool.BLUR_BRUSH,
            strokes = bundle.strokes.filter { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.BLUR }
        )
    }
    if (bundle.strokes.any { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.REMOVAL }) {
        list += BitmapDiffEffect(
            tool = EditTool.OBJECT_REMOVAL,
            strokes = bundle.strokes.filter { it.type == codes.pepper.whimsicalart.feature.editor.domain.StrokeType.REMOVAL }
        )
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
            is FilterEffect -> "filter:${effect.filterMatrix?.contentHashCode() ?: -1}"
            is EnhanceEffect -> "enhance:${effect.enabled}"
            is StyleEffect -> "style:${effect.filter?.id ?: "null"}"
            is BackgroundEffect -> "bg:${effect.mode}:" +
                "${effect.blurRadius}:${effect.shape}:${effect.subjectMask != null}"
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
private fun BoxScope.PhotoBoundedLayer(
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
                scaleX = transform.scale * if (flipsApplied) -1f else 1f
                scaleY = transform.scale * if (flipvApplied) -1f else 1f
                rotationZ = if (applyTransform) rotation else 0f
                translationX = transform.offsetX
                translationY = transform.offsetY
            },
        content = content
    )
}

@Composable
private fun VignetteOverlay(strength: Float, modifier: Modifier = Modifier) {
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
private fun StickerOverlay(
    placedStickers: List<StickerPlacement>,
    selectedStickerId: String?,
    onMove: (String, Offset) -> Unit,
    onScale: (String, Float) -> Unit,
    onSelect: (String?) -> Unit,
    interactionEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        placedStickers.forEach { placement ->
            val sticker = StickerPresets.getStickerById(placement.stickerId) ?: return@forEach
            val widthDp = sticker.width * placement.scaleX
            val heightDp = sticker.height * placement.scaleY
            // Re-read the freshest placement/selection inside the gesture
            // coroutine (keyed only on the stable sticker id) so drags do not
            // re-anchor to a stale position and pinch always sees the current
            // scale instead of the value captured at composition.
            val currentPlacement by rememberUpdatedState(placement)
            Image(
                painter = rememberAsyncImagePainter(sticker.drawableRes),
                contentDescription = sticker.name,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset(
                        x = (placement.x - widthDp / 2f).dp,
                        y = (placement.y - heightDp / 2f).dp
                    )
                    .size(width = widthDp.dp, height = heightDp.dp)
                    .then(
                        if (interactionEnabled) {
                            Modifier
                                .pointerInput(placement.stickerId) {
                                    detectTapGestures(onTap = {
                                        onSelect(placement.stickerId)
                                    })
                                }
                                .pointerInput(placement.stickerId) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val p = currentPlacement
                                        onSelect(p.stickerId)
                                        when {
                                            zoom != 1f -> onScale(
                                                p.stickerId,
                                                (p.scaleX * zoom).coerceIn(0.3f, 5f)
                                            )
                                            pan != Offset.Zero -> onMove(
                                                p.stickerId,
                                                with(density) {
                                                    Offset(
                                                        p.x + pan.x.toDp().value,
                                                        p.y + pan.y.toDp().value
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                        } else {
                            Modifier
                        }
                    )
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
private fun isNonPanZoomCapable(tool: EditTool?): Boolean = tool in NON_COMPARE_TOOLS

// Category B — non-pan/zoom-capable tools. CROP, FRAMES, STICKERS, TEXT and all
// brush tools (PEN, MOSAIC, BLUR_BRUSH, OBJECT_REMOVAL). TRANSFORM is NOT here:
// it is a pure control tool (rotate/Flip) with no single-finger canvas gesture,
// so it stays pan/zoom-capable (Category A) — no eye button, hold-to-compare always.
private val NON_COMPARE_TOOLS = setOf(
    EditTool.CROP,
    EditTool.FRAMES,
    EditTool.STICKERS,
    EditTool.TEXT,
    EditTool.PEN,
    EditTool.MOSAIC,
    EditTool.BLUR_BRUSH,
    EditTool.OBJECT_REMOVAL
)

// Whether holding the preview should reveal the original photo.
//  - Category A tools: always (holding shows the plain original).
//  - Category B tools: only while their eye (inspect/preview) button is enabled —
//    in the normal editing state a press-and-hold is the tool's own gesture and
//    would hide the user's placed object / drawn stroke / crop frame.
private fun isCompareTool(tool: EditTool?, eyeEnabled: Boolean): Boolean =
    tool == null || tool !in NON_COMPARE_TOOLS || eyeEnabled

// Applies the shared viewport pan/zoom (scale + translate, no rotation/flip —
// brush tools never rotate) to a raw source-space overlay so committed brush
// strokes / mosaic tiles follow the base image while the user pans and zooms.
private fun Modifier.panZoomLayer(transform: ViewportTransform): Modifier =
    this.graphicsLayer {
        scaleX = transform.scale
        scaleY = transform.scale
        translationX = transform.offsetX
        translationY = transform.offsetY
    }

// Tools that draw their interactive overlays in the RAW (un-transformed) source
// coordinate space — crop grid, brush strokes, placed stickers, text boxes. When
// one of these is the active focused tool the preview stays on the raw live
// source so those overlays line up; the committed composite (which bakes the
// geometry) is shown at rest / for all other tools (see `useCompositePreview`).
// This is an ORTHOGONAL concern to pan/zoom capability: FRAMES is
// non-pan/zoom-capable (Category B) but has no source-space overlay, and
// TRANSFORM is pan/zoom-capable (Category A) but has no source-space overlay,
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

@Composable
private fun filterChipStyle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

private const val MAX_COMPARE_DIMENSION = 2048

private fun decodeBounded(contentResolver: ContentResolver, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_COMPARE_DIMENSION) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }
}

/**
 * Builds a user-editable [TextOverlay] from an OCR fragment, placed inside the
 * edit box at the detected line's position. The image is centered in the edit
 * box, so we offset by the letterbox (container - image)/2, then map the
 * fragment's normalized anchor into dp. Font size keeps the detected line's
 * proportion of the image width.
 */
private fun buildOcrTextOverlay(
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
    val fontSizePx = fragment.fontSizeFraction * imagePx.width
    val scale = 1f / density.density
    return TextOverlay(
        id = java.util.UUID.randomUUID().toString(),
        text = fragment.text,
        position = Offset(xPx * scale, yPx * scale),
        fontSize = fontSizePx * scale
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
private fun rotatedBoundsSize(width: Float, height: Float, rotationDegrees: Float): Size {
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
