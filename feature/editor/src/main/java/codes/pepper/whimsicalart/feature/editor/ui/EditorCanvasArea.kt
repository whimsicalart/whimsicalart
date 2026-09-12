package codes.pepper.whimsicalart.feature.editor.ui

import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyBrushOverlay
import codes.pepper.whimsicalart.feature.editor.domain.FrameTier
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurBrushOverlay
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurEditorState
import codes.pepper.whimsicalart.feature.editor.ui.blur.BlurViewModel
import codes.pepper.whimsicalart.feature.editor.ui.frames.FramePreview
import codes.pepper.whimsicalart.feature.editor.ui.frames.outerPreviewExtent
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicEditorState
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicOverlay
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicViewModel
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenEditorState
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenStrokeOverlay
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenViewModel
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalEditorState
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalOverlay
import codes.pepper.whimsicalart.feature.editor.ui.removal.RemovalViewModel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextBlurFrame
import codes.pepper.whimsicalart.feature.editor.ui.text.TextEditorUiState
import codes.pepper.whimsicalart.feature.editor.ui.text.TextEditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.text.TextOverlayLayer
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorUiState
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.stickers.ui.viewmodel.StickersUiState
import codes.pepper.whimsicalart.feature.stickers.ui.viewmodel.StickersViewModel

/**
 * The letterboxed canvas: the surface + every tool overlay (the "canvasContent"
 * of [EditorWorkspaceLayout]), its zoom/compare gestures, the eye row, and the
 * orientation-aware workspace wiring. Pure presentation — [uiState] and the tool
 * states feed it, [actions] + the tool ViewModels carry every interaction out.
 */
@Composable
internal fun ColumnScope.EditorCanvasArea(
    imageUri: Uri,
    uiState: EditorUiState,
    actions: EditorActions,
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
    uiScale: Float,
    viewportTransform: ViewportTransform,
    canPanZoom: Boolean,
    useCompositePreview: Boolean,
    isBackgroundPlacement: Boolean,
    eyeEnabled: Boolean,
    selectedAspectRatio: CropAspectRatio,
    previewColorFilter: ColorFilter?,
    rotatedImageSize: Size,
    imageViewSize: Size,
    imageFitSize: Size,
    imageFrame: ImageCanvasFrame,
    beautyPenBaseSize: Size,
    onCanvasBoxSized: (Size) -> Unit,
    controls: @Composable () -> Unit
) {
    // Letterboxed canvas: it spans the full available width and its height is
    // min(available width, available height − 56 dp × uiScale − 200 dp × uiScale)
    // so the eye row keeps its own fixed band and the tool controls below keep
    // at least 200 dp × uiScale. Sizing the canvas this way also scopes every
    // gesture to it: touches over the controls region below hit the controls
    // (which scroll) and can never reach the canvas' pan/zoom, hold-to-compare,
    // or brush/placement gesture handlers.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
    ) {
        val canvasHeight = portraitCanvasHeight(maxWidth, maxHeight, uiScale)
        // Landscape (visible area wider than tall) mirrors the portrait column:
        // the canvas moves to the left with width min(available height − 56 dp ×
        // uiScale, available width − 600 dp × uiScale) and the tool bar + controls
        // move to a right-side panel at least 600 dp × uiScale wide (see
        // effects_reference "Canvas layout and gesture scope").
        //
        // Orientation must be derived from the *window* size, not the canvas box.
        // BoxWithConstraints returns the canvas area (window minus the top bar),
        // so its height is reduced while its width is not — biasing every portrait
        // split pane toward landscape. LocalConfiguration reflects the WM-reported
        // pane dimensions and is safe in split-screen / multi-window / foldable.
        val config = LocalConfiguration.current
        val landscape = isLandscapeLayout(config.screenWidthDp.dp, config.screenHeightDp.dp)
        val canvasWidth = landscapeCanvasWidth(maxWidth, maxHeight, uiScale)
        val isCatB = isNonPanZoomCapable(uiState.selectedTool, uiState.selectedBeautyTool)

        // The canvas content (preview + every tool overlay). The split between
        // the canvas and the tool controls is orientation-aware, so the single
        // big subtree is composed in exactly one place.
        val canvasContent: @Composable BoxScope.() -> Unit = {
            ZoomableImage(
                model = imageUri,
                contentDescription = "Photo to edit",
                rotation = displayRotation(
                    useCompositePreview = useCompositePreview,
                    committedRotation = uiState.rotation,
                    bakedRotation = uiState.compositeBakedRotation,
                    liveRotationDegrees = uiState.liveRotationDegrees,
                    eyeEnabled = eyeEnabled
                ),
                flipHorizontal = displayFlip(
                    useCompositePreview = useCompositePreview,
                    committed = uiState.flipHorizontal,
                    baked = uiState.compositeBakedFlipHorizontal,
                    eyeEnabled = eyeEnabled
                ),
                flipVertical = displayFlip(
                    useCompositePreview = useCompositePreview,
                    committed = uiState.flipVertical,
                    baked = uiState.compositeBakedFlipVertical,
                    eyeEnabled = eyeEnabled
                ),
                colorFilter = if (useCompositePreview) null else previewColorFilter,
                bitmapOverride = when {
                    useCompositePreview -> uiState.compositePreview?.asImageBitmap()
                    else -> uiState.sharpenedPreview?.asImageBitmap()
                },
                transform = viewportTransform,
                panZoomEnabled = canPanZoom,
                twistEnabled = uiState.selectedTool == EditTool.TRANSFORM && !eyeEnabled,
                // Raw deltas accumulate WITHOUT per-event wrapping (and without the
                // committed-path side effects); only the live preview moves, and the
                // wrapped angle is committed once the gesture settles.
                onTwistDegrees = { delta -> viewModel.applyTwistDelta(delta) }
            )

            if (uiState.isImageLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // A failed decode must never leave a silent blank canvas: show an
            // explicit error with retry/back instead (sharing-into-the-app cold
            // start lands here when the sender's transient grant is revoked or
            // the media permission has never been granted).
            if (uiState.error != null && uiState.originalBitmap == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.error!!,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * LocalUiScale.current
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp * LocalUiScale.current))
                    Row {
                        OutlinedButton(onClick = { actions.onBack() }) {
                            Text(
                                "Back",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize * LocalUiScale.current
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp * LocalUiScale.current))
                        Button(onClick = { actions.onRetryLoadImage() }) {
                            Text(
                                "Retry",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize * LocalUiScale.current
                                )
                            )
                        }
                    }
                }
            }

            if (!useCompositePreview && uiState.vignette != 0f && imageViewSize.width > 0f) {
                PhotoBoundedLayer(
                    imageFitSize = imageFitSize,
                    rotation = uiState.rotation + uiState.liveRotationDegrees,
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
                    onCropChanged = { actions.onSetCrop(it) },
                    onCropApplied = { actions.onApplyCrop() }
                )
            }

            if (isBackgroundPlacement && !eyeEnabled && imageViewSize.width > 0f) {
                BackgroundPlacementOverlay(
                    canvasSize = rotatedImageSize,
                    canvasFitSize = imageViewSize,
                    frameOffsetX = imageFrame.offsetX,
                    frameOffsetY = imageFrame.offsetY,
                    initialScale = uiState.backgroundPlacementScale,
                    initialPanX = uiState.backgroundPlacementPanX,
                    initialPanY = uiState.backgroundPlacementPanY,
                    initialRotationDegrees = uiState.backgroundPlacementRotationDegrees,
                    freeScaleUp = uiState.backgroundFreeScaleUp,
                    onScale = { viewModel.updateBackgroundPlacementScale(it) },
                    onPan = { panX, panY -> viewModel.updateBackgroundPlacementPan(panX, panY) },
                    onRotate = { degrees -> viewModel.updateBackgroundPlacementRotation(degrees) }
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
                    onRotate = { stickerId, rotation ->
                        stickersViewModel.rotateSticker(stickerId, rotation)
                    },
                    onResize = { stickerId, scale, position ->
                        stickersViewModel.resizeSticker(stickerId, scale, position)
                    },
                    onSelect = { stickerId ->
                        stickersViewModel.selectSticker(stickerId)
                    },
                    interactionEnabled = !eyeEnabled,
                    modifier = Modifier
                        .fillMaxSize()
                        .panZoomLayer(viewportTransform)
                )
            }

            if (uiState.selectedTool == EditTool.TEXT && !eyeEnabled &&
                textState.textOverlays.isNotEmpty()
            ) {
                TextOverlayLayer(
                    overlays = textState.textOverlays,
                    selectedOverlayId = textState.selectedOverlayId,
                    draftOverlayId = textState.draftOverlayId,
                    onMove = { overlayId, position ->
                        textEditorViewModel.moveOverlay(overlayId, position)
                    },
                    onScale = { overlayId, scale ->
                        textEditorViewModel.scaleOverlay(overlayId, scale)
                    },
                    onRotate = { overlayId, rotation ->
                        textEditorViewModel.rotateOverlay(overlayId, rotation)
                    },
                    onResize = { overlayId, scale, position ->
                        textEditorViewModel.resizeOverlay(overlayId, scale, position)
                    },
                    onSelect = { overlayId ->
                        textEditorViewModel.selectOverlay(overlayId)
                    },
                    interactionEnabled = !eyeEnabled,
                    sourceBitmap = if (useCompositePreview) {
                        uiState.compositePreview
                    } else {
                        uiState.sharpenedPreview
                    },
                    blurFrame = TextBlurFrame(
                        offsetXPx = imageFrame.offsetX,
                        offsetYPx = imageFrame.offsetY,
                        widthPx = imageFrame.size.width,
                        heightPx = imageFrame.size.height
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .panZoomLayer(viewportTransform)
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
                    brushingEnabled = !eyeEnabled,
                    showLiveResult = mosaicState.liveMosaicApplied &&
                        uiState.publishedMosaicStrokesVersion >= mosaicState.strokesVersion,
                    imageFrame = imageFrame
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
                    brushingEnabled = !eyeEnabled,
                    showLiveResult = blurState.liveBlurApplied &&
                        uiState.publishedBlurStrokesVersion >= blurState.strokesVersion
                )
            }

            if (uiState.selectedTool == EditTool.PEN &&
                (!eyeEnabled ||
                    // Keep-alive: with the eye ON the canvas swaps to the
                    // composite, but the pen strokes only enter the composite
                    // via a FIRST-TIME full-stack fold (the eye-OFF composite
                    // excluded them, unlike the live-applied tools). Keep the
                    // overlay up until that fold lands so the trace never
                    // dead-gaps mid-zoom.
                    uiState.publishedPenStrokesVersion < penState.strokesVersion)
            ) {
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

            if (isBeautyPenTool(uiState.selectedTool, uiState.selectedBeautyTool) && !eyeEnabled) {
                BeautyBrushOverlay(
                    bitmapSize = beautyPenBaseSize,
                    strokes = uiState.beauty.brushStrokes,
                    activeStroke = uiState.beauty.activeStroke,
                    color = uiState.beauty.penColor,
                    onDragStart = { viewModel.startBeautyPenStroke(it) },
                    onDragMove = { viewModel.moveBeautyPenStroke(it) },
                    onDragEnd = { viewModel.endBeautyPenStroke() },
                    modifier = Modifier
                        .fillMaxSize()
                        .panZoomLayer(viewportTransform)
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
                    brushingEnabled = !eyeEnabled,
                    showLiveResult = removalState.liveRemovalApplied &&
                        uiState.publishedRemovalStrokesVersion >= removalState.strokesVersion
                )
            }

            val innerFrameStyle = uiState.innerFrameStyle
            val outerFrameStyle = uiState.outerFrameStyle
            if (!useCompositePreview && (innerFrameStyle != null || outerFrameStyle != null) &&
                imageViewSize.width > 0f
            ) {
                val innerFrameTier = FrameTier(
                    style = innerFrameStyle,
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
                )
                val outerFrameTier = FrameTier(
                    style = outerFrameStyle,
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
                val outerExtentPx = outerPreviewExtent(
                    outerFrameTier,
                    imageFitSize.width.toInt(),
                    imageFitSize.height.toInt()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        // Grow by the outer extent on every side so the outer ring
                        // renders AROUND the fitted photo (docs/effects_reference.md
                        // → Outer-frame dimensions); the photo stays at (extent,
                        // extent) inside the enlarged box.
                        .size(
                            width = with(LocalDensity.current) {
                                (imageFitSize.width + 2 * outerExtentPx).toDp()
                            },
                            height = with(LocalDensity.current) {
                                (imageFitSize.height + 2 * outerExtentPx).toDp()
                            }
                        )
                        .graphicsLayer {
                            // Frames do NOT follow rotate/flip (the same opt-out as
                            // PhotoBoundedLayer's applyTransform = false): scale + pan
                            // only, the frame stays square around the canvas.
                            scaleX = viewportTransform.scale
                            scaleY = viewportTransform.scale
                            rotationZ = 0f
                            translationX = viewportTransform.offsetX
                            translationY = viewportTransform.offsetY
                        }
                ) {
                    FramePreview(
                        inner = innerFrameTier,
                        outer = outerFrameTier,
                        outerExtentPx = outerExtentPx,
                        modifier = Modifier.fillMaxSize()
                    )
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

        // The inspect label + eye button row. In BOTH orientations it sits
        // directly under the canvas (the landscape left column mirrors the
        // portrait column) - never under the side controls panel.
        val eyeRow: @Composable () -> Unit = {
            val s = LocalUiScale.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp * s, vertical = 4.dp * s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isCatB && uiState.selectedTool == EditTool.CROP ->
                            if (eyeEnabled) "Showing full result (inspect)" else "Edit / preview current crop"
                        isCatB ->
                            if (eyeEnabled) "Showing full result (inspect)" else "Show current effect only"
                        isBackgroundPlacement && !eyeEnabled ->
                            "Drag to pan · pinch to scale / rotate the placement"
                        eyeEnabled -> "Showing full result"
                        else -> "Preview (without later effects)"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = MaterialTheme.typography.labelMedium.fontSize * s
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { actions.onEyeToggle() },
                    modifier = Modifier.size((48.dp * s).coerceAtLeast(40.dp))
                ) {
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
                        },
                        modifier = Modifier.size(24.dp * s)
                    )
                }
            }
        }

        EditorWorkspaceLayout(
            isLandscape = landscape,
            canvasHeight = canvasHeight,
            canvasWidth = canvasWidth,
            canvasContentModifier = Modifier
                // Clip the panned/zoomed preview to its own area so the image
                // content never draws over the top chrome (TopAppBar + Layers/
                // undo/redo/Save) or the tool controls.
                .clipToBounds()
                .onSizeChanged {
                    onCanvasBoxSized(Size(it.width.toFloat(), it.height.toFloat()))
                }
                .pointerInput(uiState.selectedTool, uiState.selectedBeautyTool, eyeEnabled, isBackgroundPlacement) {
                    val compareEnabled = when {
                        isBackgroundPlacement -> eyeEnabled
                        else -> isCompareTool(uiState.selectedTool, uiState.selectedBeautyTool, eyeEnabled)
                    }
                    if (compareEnabled) {
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
            canvasContent = canvasContent,
            eyeRow = eyeRow,
            controlsHeader = {
                EditorToolbar(
                    selectedTool = uiState.selectedTool,
                    onToolSelected = { actions.onSelectTool(it) }
                )
            },
            controls = controls
        )
    }
}
