package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyControls
import codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurBrushPanel
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurEditorState
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurViewModel
import codes.pepper.whimsicalart.feature.editor.ui.background.BackgroundPanel
import codes.pepper.whimsicalart.feature.editor.ui.frames.FrameControls
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicEditorState
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicPanel
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicViewModel
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenBrushPanel
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenEditorState
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenViewModel
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalEditorState
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalPanel
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalViewModel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextEditorPanel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextEditorUiState
import codes.pepper.whimsicalart.feature.editor.ui.text.TextEditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorUiState
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.filters.domain.FilterPresets
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPresets
import codes.pepper.whimsicalart.feature.stickers.ui.StickerPickerPanel
import codes.pepper.whimsicalart.feature.stickers.ui.viewmodel.StickersUiState
import codes.pepper.whimsicalart.feature.stickers.ui.viewmodel.StickersViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The active tool's panel + params column (the scrolling "controls" region of
 * [EditorWorkspaceLayout]). Stateless: every value comes in via [uiState] / the
 * tool states, every mutation goes back out through the tool ViewModels. The
 * OCR recognition coroutine is the only editable state owned here (it is purely
 * a UI-mechanics affordance of the Text panel).
 */
@Composable
internal fun EditorToolPanelHost(
    uiState: EditorUiState,
    viewModel: EditorViewModel,
    stickersState: StickersUiState,
    stickersViewModel: StickersViewModel,
    textState: TextEditorUiState,
    textEditorViewModel: TextEditorViewModel,
    mosaicState: MosaicEditorState,
    mosaicViewModel: MosaicViewModel,
    blurState: BlurEditorState,
    blurViewModel: BlurViewModel,
    penState: PenEditorState,
    penViewModel: PenViewModel,
    removalState: RemovalEditorState,
    removalViewModel: RemovalViewModel,
    density: Density,
    editBoxSize: Size,
    imageViewSize: Size,
    eyeEnabled: Boolean,
    selectedAspectRatio: CropAspectRatio,
    onAspectRatioSelected: (CropAspectRatio) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var recognizingText by remember { mutableStateOf(false) }
    val selectedFilterId = uiState.selectedFilterId
    when (uiState.selectedTool) {
        EditTool.STICKERS -> {
            val selectedPlacement = stickersState.placedStickers.firstOrNull {
                it.id == stickersState.selectedStickerId
            }
            val selectedSticker = selectedPlacement?.let {
                StickerPresets.getStickerById(it.stickerId)
            }
            val fallbackCenter = with(density) {
                Offset(
                    (editBoxSize.width / 2f).toDp().value,
                    (editBoxSize.height / 2f).toDp().value
                )
            }
            StickerPickerPanel(
                selectedCategory = stickersState.selectedCategory,
                onCategorySelected = { stickersViewModel.selectCategory(it) },
                stickers = stickersState.availableStickers,
                onStickerSelected = { sticker ->
                    stickersViewModel.placeSticker(sticker, fallbackCenter)
                },
                selectedPlacement = selectedPlacement,
                selectedSticker = selectedSticker,
                onOpacityChange = { value ->
                    selectedPlacement?.let { stickersViewModel.setOpacity(it.id, value) }
                },
                onRotationChange = { value ->
                    selectedPlacement?.let { stickersViewModel.rotateSticker(it.id, value) }
                },
                onTintChange = { color ->
                    selectedPlacement?.let { stickersViewModel.setTint(it.id, color) }
                },
                onLineStyleChange = { style ->
                    selectedPlacement?.let { stickersViewModel.setLineStyle(it.id, style) }
                },
                onLineThicknessChange = { value ->
                    selectedPlacement?.let { stickersViewModel.setLineThickness(it.id, value) }
                },
                onLineColourChange = { color ->
                    selectedPlacement?.let { stickersViewModel.setLineColour(it.id, color) }
                },
                onRemove = {
                    selectedPlacement?.let { stickersViewModel.removeSticker(it.id) }
                },
                onEmojiPicked = { emoji ->
                    stickersViewModel.placeEmoji(emoji, fallbackCenter)
                },
                onGalleryPicked = { uri, aspectRatio ->
                    stickersViewModel.placeGallerySticker(uri, fallbackCenter, aspectRatio)
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
                faceDetectionAvailable = mosaicViewModel.faceDetectorAvailable,
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
                onBrushOpacityChange = { penViewModel.updateBrushOpacity(it) },
                onBrushHardnessChange = { penViewModel.updateBrushHardness(it) },
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
                subjectDetectionFailed = uiState.subjectDetectionFailed,
                selectedBackgroundRes = uiState.selectedBackgroundRes,
                hasCustomBackground = uiState.backgroundImage != null && uiState.selectedBackgroundRes == null,
                placementScale = uiState.backgroundPlacementScale,
                placementRotationDegrees = uiState.backgroundPlacementRotationDegrees,
                freeScaleUp = uiState.backgroundFreeScaleUp,
                onModeChange = { viewModel.updateBackgroundMode(it) },
                onBlurRadiusChange = { viewModel.updateBackgroundBlurRadius(it) },
                onShapeChange = { viewModel.updateBackgroundShape(it) },
                onSegmentSubject = { viewModel.segmentSubject() },
                onClearSubject = { viewModel.clearSubjectMask() },
                onSelectPreset = { res -> viewModel.loadBackground(res) },
                onCustomBackground = { uri -> viewModel.setCustomBackground(uri) },
                onClearBackground = { viewModel.clearBackground() },
                onScaleChange = { viewModel.updateBackgroundPlacementScale(it) },
                onRotationChange = { viewModel.updateBackgroundPlacementRotation(it) },
                onFreeScaleUpChange = { viewModel.toggleBackgroundFreeScaleUp() }
            )
        }
        EditTool.TEXT -> {
            TextEditorPanel(
                state = textState,
                onTextChange = { textEditorViewModel.updateCurrentText(it) },
                onFontSizeChange = { textEditorViewModel.updateFontSize(it) },
                onColorChange = { textEditorViewModel.updateColor(it) },
                onTextOpacityChange = { textEditorViewModel.updateTextOpacity(it) },
                onFontChange = { textEditorViewModel.updateFont(it) },
                onStyleToggle = { textEditorViewModel.toggleStyle(it) },
                onAlignmentChange = { textEditorViewModel.updateAlignment(it) },
                onRotationChange = { textEditorViewModel.updateCurrentRotation(it) },
                onShadowToggle = { textEditorViewModel.toggleShadow() },
                onShadowAngleChange = { textEditorViewModel.updateShadowAngle(it) },
                onShadowDistanceChange = { textEditorViewModel.updateShadowDistance(it) },
                onShadowColorChange = { textEditorViewModel.updateShadowColor(it) },
                onOutlineToggle = { textEditorViewModel.toggleOutline() },
                onOutlineSizeChange = { textEditorViewModel.updateOutlineSize(it) },
                onOutlineColorChange = { textEditorViewModel.updateOutlineColor(it) },
                onOutlineBlurChange = { textEditorViewModel.updateOutlineBlur(it) },
                onBackgroundShapeChange = {
                    textEditorViewModel.updateBackgroundShape(it)
                },
                onBackgroundColorChange = {
                    textEditorViewModel.updateBackgroundColor(it)
                },
                onBackgroundOpacityChange = {
                    textEditorViewModel.updateBackgroundOpacity(it)
                },
                onBackgroundBlurChange = {
                    textEditorViewModel.updateBackgroundBlur(it)
                },
                onRecognizeText = {
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
                },
                recognizingText = recognizingText,
                ocrAvailable = viewModel.ocrIsAvailable,
                onRemove = { textEditorViewModel.removeSelectionAndReset() },
                onAdd = { textEditorViewModel.addText() }
            )
        }
        EditTool.FRAMES -> {
            FrameControls(
                innerSelectedStyle = uiState.innerFrameStyle,
                innerThickness = uiState.innerFrameThickness,
                innerColor = Color(uiState.innerFrameColor),
                innerEdgeSize = uiState.innerFrameEdgeSize,
                innerCustomAngle = uiState.innerFrameCustomAngle,
                innerCustomCurveDistance = uiState.innerFrameCustomCurveDistance,
                innerCustomStrength = uiState.innerFrameCustomStrength,
                innerCustomCount = uiState.innerFrameCustomCount,
                innerLines = uiState.innerFrameLines,
                innerLinesEnabled = uiState.innerFrameLinesEnabled,
                innerLineDistance = uiState.innerFrameLineDistance,
                innerLineThickness = uiState.innerFrameLineThickness,
                outerSelectedStyle = uiState.outerFrameStyle,
                outerThickness = uiState.outerFrameThickness,
                outerColor = Color(uiState.outerFrameColor),
                outerEdgeSize = uiState.outerFrameEdgeSize,
                outerCustomAngle = uiState.outerFrameCustomAngle,
                outerCustomCurveDistance = uiState.outerFrameCustomCurveDistance,
                outerCustomStrength = uiState.outerFrameCustomStrength,
                outerCustomCount = uiState.outerFrameCustomCount,
                outerLines = uiState.outerFrameLines,
                outerLinesEnabled = uiState.outerFrameLinesEnabled,
                outerLineDistance = uiState.outerFrameLineDistance,
                outerLineThickness = uiState.outerFrameLineThickness,
                onInnerStyleSelected = { style -> viewModel.updateInnerFrameStyle(style) },
                onInnerThicknessChange = { value -> viewModel.updateInnerFrameThickness(value) },
                onInnerColorChange = { color -> viewModel.updateInnerFrameColor(color.toArgb()) },
                onInnerEdgeSizeChange = { value -> viewModel.updateInnerFrameEdgeSize(value) },
                onInnerCustomAngleChange = { value -> viewModel.updateInnerFrameCustomAngle(value) },
                onInnerCustomCurveDistanceChange = { value -> viewModel.updateInnerFrameCustomCurveDistance(value) },
                onInnerCustomStrengthChange = { value -> viewModel.updateInnerFrameCustomStrength(value) },
                onInnerCustomCountChange = { value -> viewModel.updateInnerFrameCustomCount(value) },
                onInnerLinesChange = { value -> viewModel.updateInnerFrameLines(value) },
                onInnerLinesToggle = { enabled -> viewModel.updateInnerFrameLinesEnabled(enabled) },
                onInnerLineDistanceChange = { value -> viewModel.updateInnerFrameLineDistance(value) },
                onInnerLineThicknessChange = { value -> viewModel.updateInnerFrameLineThickness(value) },
                onOuterStyleSelected = { style -> viewModel.updateOuterFrameStyle(style) },
                onOuterThicknessChange = { value -> viewModel.updateOuterFrameThickness(value) },
                onOuterColorChange = { color -> viewModel.updateOuterFrameColor(color.toArgb()) },
                onOuterEdgeSizeChange = { value -> viewModel.updateOuterFrameEdgeSize(value) },
                onOuterCustomAngleChange = { value -> viewModel.updateOuterFrameCustomAngle(value) },
                onOuterCustomCurveDistanceChange = { value -> viewModel.updateOuterFrameCustomCurveDistance(value) },
                onOuterCustomStrengthChange = { value -> viewModel.updateOuterFrameCustomStrength(value) },
                onOuterCustomCountChange = { value -> viewModel.updateOuterFrameCustomCount(value) },
                onOuterLinesChange = { value -> viewModel.updateOuterFrameLines(value) },
                onOuterLinesToggle = { enabled -> viewModel.updateOuterFrameLinesEnabled(enabled) },
                onOuterLineDistanceChange = { value -> viewModel.updateOuterFrameLineDistance(value) },
                onOuterLineThicknessChange = { value -> viewModel.updateOuterFrameLineThickness(value) }
            )
        }
        EditTool.SKIN_DENOISE -> {
            AdjustmentSlider(
                label = "Skin Denoise",
                value = uiState.skinDenoise,
                valueRange = 0f..1f,
                isPercent = true,
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
                        if (uiState.enhanceEnabled) "Auto-Enhance On" else "Auto-Enhance Off",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = MaterialTheme.typography.labelMedium.fontSize * LocalUiScale.current
                        )
                    )
                }
            )
        }
        EditTool.FILTERS -> {
            val s = LocalUiScale.current
            Column(modifier = Modifier.padding(vertical = 4.dp * s)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp * s),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * s)
                ) {
                    Text(
                        text = "Color Filters",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp * s, vertical = 8.dp * s),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * s)
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
                        .padding(horizontal = 16.dp * s),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * s)
                ) {
                    Text(
                        text = "Lens Filters",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp * s, vertical = 8.dp * s),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * s)
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
                params = uiState.beauty,
                selectedTool = uiState.selectedBeautyTool,
                onToolSelected = { viewModel.selectBeautyTool(it) },
                onChange = { viewModel.setBeautyParams(it) }
            )
        }
        EditTool.CROP -> {
            val s = LocalUiScale.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp * s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AspectRatioSelector(
                    selectedAspectRatio = selectedAspectRatio,
                    onAspectRatioSelected = { onAspectRatioSelected(it) },
                    enabled = !eyeEnabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        EditTool.TRANSFORM -> {
            TransformToolbar(
                // The free-rotate slider mirrors the LIVE twist angle too (the raw
                // live accumulation wraps for display only via rotatedByDelta), so
                // the slider tracks the fingers mid-gesture and ends on the value
                // that commits.
                rotation = rotatedByDelta(uiState.rotation, uiState.liveRotationDegrees),
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
        else -> {}
    }
}
