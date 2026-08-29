package com.whimsicalart.feature.editor.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.whimsicalart.feature.editor.domain.EditorRenderBundle
import com.whimsicalart.feature.editor.domain.FrameLayer
import com.whimsicalart.feature.editor.domain.SaveConfig
import com.whimsicalart.feature.editor.domain.StickerLayer
import com.whimsicalart.feature.editor.domain.StrokeLayer
import com.whimsicalart.feature.editor.domain.StrokeType
import com.whimsicalart.feature.editor.domain.TextLayer
import com.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import com.whimsicalart.feature.editor.ui.viewmodel.EditTool
import com.whimsicalart.feature.filters.domain.FilterPresets
import com.whimsicalart.feature.filters.ui.FilterSelector
import com.whimsicalart.feature.stickers.domain.StickerPlacement
import com.whimsicalart.feature.stickers.domain.StickerPresets
import com.whimsicalart.feature.stickers.ui.StickerPicker
import com.whimsicalart.feature.stickers.ui.viewmodel.StickersViewModel
import com.whimsicalart.feature.editor.ui.blur.BlurBrushOverlay
import com.whimsicalart.feature.editor.ui.blur.BlurBrushPanel
import com.whimsicalart.feature.editor.ui.blur.BlurViewModel
import com.whimsicalart.feature.editor.ui.mosaic.MosaicOverlay
import com.whimsicalart.feature.editor.ui.mosaic.MosaicPanel
import com.whimsicalart.feature.editor.ui.mosaic.MosaicViewModel
import com.whimsicalart.feature.editor.ui.pen.PenBrushPanel
import com.whimsicalart.feature.editor.ui.pen.PenStrokeOverlay
import com.whimsicalart.feature.editor.ui.pen.PenViewModel
import com.whimsicalart.feature.editor.ui.text.TextEditorPanel
import com.whimsicalart.feature.editor.ui.text.TextEditorViewModel
import com.whimsicalart.feature.editor.ui.text.TextOverlayLayer
import com.whimsicalart.feature.editor.ui.frames.FramePicker
import com.whimsicalart.feature.editor.ui.frames.FramePreview
import com.whimsicalart.feature.editor.ui.frames.FramePresets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    onOpenBeauty: (() -> Unit)? = null,
    viewModel: EditorViewModel = hiltViewModel(),
    stickersViewModel: StickersViewModel = hiltViewModel(),
    textEditorViewModel: TextEditorViewModel = hiltViewModel(),
    mosaicViewModel: MosaicViewModel = hiltViewModel(),
    blurViewModel: BlurViewModel = hiltViewModel(),
    penViewModel: PenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val stickersState by stickersViewModel.uiState.collectAsState()
    val textState by textEditorViewModel.uiState.collectAsState()
    val mosaicState by mosaicViewModel.uiState.collectAsState()
    val blurState by blurViewModel.uiState.collectAsState()
    val penState by penViewModel.uiState.collectAsState()
    var selectedAspectRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    var selectedFilterId by remember { mutableStateOf<String?>(null) }
    val selectedFilter = remember(selectedFilterId) {
        selectedFilterId?.let { FilterPresets.getFilterById(it) }
    }
    val previewColorFilter = remember(
        selectedFilter,
        uiState.brightness, uiState.contrast, uiState.saturation,
        uiState.exposure, uiState.temperature, uiState.tint,
        uiState.shadows, uiState.highlights
    ) {
        val matrix = EditorColorMatrix.build(
            brightness = uiState.brightness,
            contrast = uiState.contrast,
            saturation = uiState.saturation,
            exposure = uiState.exposure,
            temperature = uiState.temperature,
            tint = uiState.tint,
            shadows = uiState.shadows,
            highlights = uiState.highlights,
            filterMatrix = selectedFilter?.previewColorMatrix?.array
        )
        ColorFilter.colorMatrix(matrix)
    }
    // Rotated pixel dimensions of the source image, so the crop overlay can be
    // confined to the letterboxed (ContentScale.Fit) image rect and match the
    // bitmap region that is actually cropped on save.
    val rotatedImageSize = remember(uiState.originalBitmap, uiState.rotation) {
        val bmp = uiState.originalBitmap
        val quarterTurns = (((uiState.rotation % 360f) + 360f) % 360f / 90f).roundToInt() % 4
        if (bmp == null) Size.Zero
        else if (quarterTurns % 2 == 1) Size(bmp.height.toFloat(), bmp.width.toFloat())
        else Size(bmp.width.toFloat(), bmp.height.toFloat())
    }
    var showSaveDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editBoxSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    val keyboardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(imageUri) {
        viewModel.setImageUri(imageUri)
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
                IconButton(onClick = onBack) {
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
                .onSizeChanged { editBoxSize = Size(it.width.toFloat(), it.height.toFloat()) },
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isComparing && uiState.originalBitmap != null && uiState.editedBitmap != null) {
                CompareOverlay(
                    originalBitmap = uiState.originalBitmap!!,
                    editedBitmap = uiState.editedBitmap!!,
                    isComparing = true,
                    rotation = uiState.rotation,
                    flipHorizontal = uiState.flipHorizontal,
                    flipVertical = uiState.flipVertical
                )
            } else {
                ZoomableImage(
                    model = imageUri,
                    contentDescription = "Photo to edit",
                    rotation = uiState.rotation,
                    flipHorizontal = uiState.flipHorizontal,
                    flipVertical = uiState.flipVertical,
                    colorFilter = previewColorFilter
                )
            }

            if (uiState.vignette != 0f) {
                VignetteOverlay(strength = uiState.vignette)
            }

            if (uiState.selectedTool == EditTool.CROP) {
                CropOverlay(
                    imageSize = rotatedImageSize,
                    aspectRatio = selectedAspectRatio,
                    onCropChanged = { viewModel.updateCrop(it) }
                )
            }

            if (uiState.selectedTool == EditTool.STICKERS) {
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
                    }
                )
            }

            if (textState.textOverlays.isNotEmpty()) {
                TextOverlayLayer(
                    overlays = textState.textOverlays,
                    onMove = { overlayId, position ->
                        textEditorViewModel.moveOverlay(overlayId, position)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (uiState.selectedTool == EditTool.MOSAIC) {
                MosaicOverlay(
                    strokes = mosaicState.strokes,
                    currentStroke = mosaicState.currentStroke,
                    onDragStart = { mosaicViewModel.onDragStart(it) },
                    onDragMove = { mosaicViewModel.onDragMove(it) },
                    onDragEnd = { mosaicViewModel.onDragEnd() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (uiState.selectedTool == EditTool.BLUR_BRUSH) {
                BlurBrushOverlay(
                    strokes = blurState.strokes,
                    currentStroke = blurState.currentStroke,
                    onDragStart = { blurViewModel.onDragStart(it) },
                    onDragMove = { blurViewModel.onDragMove(it) },
                    onDragEnd = { blurViewModel.onDragEnd() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (uiState.selectedTool == EditTool.PEN) {
                PenStrokeOverlay(
                    strokes = penState.strokes,
                    currentStroke = penState.currentStroke,
                    onDragStart = { penViewModel.onDragStart(it) },
                    onDragMove = { penViewModel.onDragMove(it) },
                    onDragEnd = { penViewModel.onDragEnd() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            uiState.selectedFrameId
                ?.let { id -> FramePresets.getFrameById(id) }
                ?.let { frame -> FramePreview(frame = frame) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (uiState.selectedTool == EditTool.CROP) {
            AspectRatioSelector(
                selectedAspectRatio = selectedAspectRatio,
                onAspectRatioSelected = { selectedAspectRatio = it }
            )
        }

        if (uiState.selectedTool == EditTool.ROTATE || uiState.selectedTool == EditTool.FLIP) {
            TransformToolbar(
                onToolSelected = { tool ->
                    when (tool) {
                        TransformTool.ROTATE_LEFT -> viewModel.rotateLeft()
                        TransformTool.ROTATE_RIGHT -> viewModel.rotateRight()
                        TransformTool.FLIP_HORIZONTAL -> viewModel.flipHorizontal()
                        TransformTool.FLIP_VERTICAL -> viewModel.flipVertical()
                    }
                }
            )
        }

        FilterSelector(
            filters = FilterPresets.filters,
            selectedFilterId = selectedFilterId,
            onFilterSelected = { filter ->
                selectedFilterId = filter.id
            }
        )

        EditorToolbar(
            selectedTool = uiState.selectedTool,
            onToolSelected = { viewModel.selectTool(it) },
            onOpenBeauty = onOpenBeauty
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
                    onBrushTypeChange = { mosaicViewModel.selectBrushType(it) },
                    onBrushSizeChange = { mosaicViewModel.updateBrushSize(it) },
                    onOpacityChange = { mosaicViewModel.updateOpacity(it) },
                    onToggleErasing = { mosaicViewModel.toggleErasing() },
                    onClear = { mosaicViewModel.clear() }
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
                    TextButton(
                        onClick = { textEditorViewModel.startAddingText() }
                    ) {
                        Text("Add New Text")
                    }
                }
            }
            EditTool.FRAMES -> {
                FramePicker(
                    selectedFrameId = uiState.selectedFrameId,
                    onFrameSelected = { frameId -> viewModel.updateFrame(frameId) }
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
        }

        val frameLayers = if (bw > 0f) {
            uiState.selectedFrameId
                ?.let { id -> FramePresets.getFrameById(id) }
                ?.let { frame ->
                    listOf(
                        FrameLayer(
                            borderWidth = with(density) { frame.borderWidth.dp.toPx() } / bw,
                            cornerRadius = with(density) { frame.cornerRadius.dp.toPx() } / bw,
                            color = frame.borderColor
                        )
                    )
                }
                ?: emptyList()
        } else {
            emptyList()
        }

        return EditorRenderBundle(
            filterMatrix = selectedFilter?.previewColorMatrix?.array,
            stickers = stickerLayers,
            texts = textLayers,
            strokes = strokeLayers,
            frames = frameLayers
        )
    }

    if (showSaveDialog) {
        SaveDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { config: SaveConfig ->
                showSaveDialog = false
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
                viewModel.shareImage(config, buildRenderBundle())
            }
        )
    }
}

@Composable
private fun VignetteOverlay(strength: Float) {
    val alpha = (kotlin.math.abs(strength) / 100f * 0.6f).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
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
            )
        }
    }
}

private const val ADJUSTMENT_STEP = 5f

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
        else -> Unit
    }
}
