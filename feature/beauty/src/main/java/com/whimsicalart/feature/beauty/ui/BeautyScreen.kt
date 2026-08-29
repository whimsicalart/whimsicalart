package com.whimsicalart.feature.beauty.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whimsicalart.feature.beauty.ui.viewmodel.BeautyTool
import com.whimsicalart.feature.beauty.ui.viewmodel.BeautyViewModel
import com.whimsicalart.feature.beauty.ui.viewmodel.MakeupPalette
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeautyScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    viewModel: BeautyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(imageUri) {
        val bitmap = runCatching {
            decodeBounded(context.contentResolver, imageUri)
        }.getOrNull()
        if (bitmap != null) {
            viewModel.setImageUri(imageUri, bitmap)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Beauty Tools") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isProcessing && uiState.processedBitmap == null -> {
                    CircularProgressIndicator()
                }
                uiState.processedBitmap != null -> {
                    val processedBitmap = uiState.processedBitmap!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                        zoomOffsetX += pan.x
                                        zoomOffsetY += pan.y
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = zoomOffsetX
                                    translationY = zoomOffsetY
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = processedBitmap.asImageBitmap(),
                                contentDescription = "Processed photo",
                                modifier = Modifier.fillMaxSize()
                            )

                            if (uiState.selectedTool == BeautyTool.BRIGHTNESS_PEN) {
                                BeautyBrushOverlay(
                                    bitmapSize = Size(
                                        processedBitmap.width.toFloat(),
                                        processedBitmap.height.toFloat()
                                    ),
                                    activeStroke = uiState.activeStroke,
                                    onStart = { viewModel.startStroke(it) },
                                    onMove = { viewModel.addStrokePoint(it) },
                                    onEnd = { viewModel.endStroke(null) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (uiState.isProcessing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        if (uiState.selectedTool == BeautyTool.BRIGHTNESS_PEN) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = { viewModel.undoLastStroke() }) {
                                    Text("Undo")
                                }
                                TextButton(onClick = { viewModel.clearBrushStrokes() }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
                else -> {
                    CircularProgressIndicator()
                }
            }
        }

        BeautyToolbar(
            selectedTool = uiState.selectedTool,
            onToolSelected = { viewModel.selectTool(it) }
        )

        when (uiState.selectedTool) {
            BeautyTool.AUTO_BEAUTY -> {
                BeautySlider(
                    label = "Auto Beauty",
                    value = uiState.autoBeautyIntensity,
                    onValueChange = { viewModel.updateAutoBeautyIntensity(it) }
                )
            }
            BeautyTool.SKIN_SMOOTHING -> {
                BeautySlider(
                    label = "Skin Smoothing",
                    value = uiState.skinSmoothingIntensity,
                    onValueChange = { viewModel.updateSkinSmoothingIntensity(it) }
                )
            }
            BeautyTool.TEETH_WHITENING -> {
                BeautySlider(
                    label = "Teeth Whitening",
                    value = uiState.teethWhiteningIntensity,
                    onValueChange = { viewModel.updateTeethWhiteningIntensity(it) }
                )
            }
            BeautyTool.EYE_BRIGHTENING -> {
                BeautySlider(
                    label = "Eye Brightening",
                    value = uiState.eyeBrighteningIntensity,
                    onValueChange = { viewModel.updateEyeBrighteningIntensity(it) }
                )
            }
            BeautyTool.DARK_CIRCLE_REMOVAL -> {
                BeautySlider(
                    label = "Dark Circle Removal",
                    value = uiState.darkCircleIntensity,
                    onValueChange = { viewModel.updateDarkCircleIntensity(it) }
                )
            }
            BeautyTool.SPOT_REMOVAL -> {
                BeautySlider(
                    label = "Spot Removal",
                    value = uiState.spotRemovalIntensity,
                    onValueChange = { viewModel.updateSpotRemovalIntensity(it) }
                )
            }
            BeautyTool.WRINKLE_REMOVAL -> {
                BeautySlider(
                    label = "Wrinkle Removal",
                    value = uiState.wrinkleRemovalIntensity,
                    onValueChange = { viewModel.updateWrinkleRemovalIntensity(it) }
                )
            }
            BeautyTool.SKIN_TONE -> {
                BeautySlider(
                    label = "Skin Tone",
                    value = uiState.skinToneIntensity,
                    valueRange = -1f..1f,
                    onValueChange = { viewModel.updateSkinToneIntensity(it) }
                )
            }
            BeautyTool.BRIGHTNESS_PEN -> {
                BeautySlider(
                    label = "Brush Size",
                    value = uiState.brushSize,
                    valueRange = 10f..80f,
                    isPercent = false,
                    onValueChange = { viewModel.updateBrushSize(it) }
                )
                BeautySlider(
                    label = "Brush Opacity",
                    value = uiState.brushOpacity,
                    onValueChange = { viewModel.updateBrushOpacity(it) }
                )
            }
            BeautyTool.FACE_SLIM -> {
                BeautySlider(
                    label = "Face Slimming",
                    value = uiState.faceSlimIntensity,
                    onValueChange = { viewModel.updateFaceSlimIntensity(it) }
                )
            }
            BeautyTool.EYE_ENLARGE -> {
                BeautySlider(
                    label = "Eye Enlarging",
                    value = uiState.eyeEnlargeIntensity,
                    onValueChange = { viewModel.updateEyeEnlargeIntensity(it) }
                )
            }
            BeautyTool.NOSE_ADJUST -> {
                BeautySlider(
                    label = "Nose Adjustment",
                    value = uiState.noseAdjustIntensity,
                    valueRange = -1f..1f,
                    onValueChange = { viewModel.updateNoseAdjustIntensity(it) }
                )
            }
            BeautyTool.JAW_ADJUST -> {
                BeautySlider(
                    label = "Jaw Adjustment",
                    value = uiState.jawAdjustIntensity,
                    onValueChange = { viewModel.updateJawAdjustIntensity(it) }
                )
            }
            BeautyTool.LIPSTICK,
            BeautyTool.BLUSH,
            BeautyTool.EYE_SHADOW,
            BeautyTool.EYELINER,
            BeautyTool.FOUNDATION,
            BeautyTool.HAIR_COLOR -> {
                ColorPicker(
                    selectedColor = uiState.makeupColor,
                    onColorSelected = { viewModel.updateMakeupColor(it) }
                )
                when (uiState.selectedTool) {
                    BeautyTool.LIPSTICK -> BeautySlider(
                        label = "Lipstick",
                        value = uiState.lipstickIntensity,
                        onValueChange = { viewModel.updateLipstickIntensity(it) }
                    )
                    BeautyTool.BLUSH -> BeautySlider(
                        label = "Blush",
                        value = uiState.blushIntensity,
                        onValueChange = { viewModel.updateBlushIntensity(it) }
                    )
                    BeautyTool.EYE_SHADOW -> BeautySlider(
                        label = "Eye Shadow",
                        value = uiState.eyeShadowIntensity,
                        onValueChange = { viewModel.updateEyeShadowIntensity(it) }
                    )
                    BeautyTool.EYELINER -> BeautySlider(
                        label = "Eyeliner",
                        value = uiState.eyelinerIntensity,
                        onValueChange = { viewModel.updateEyelinerIntensity(it) }
                    )
                    BeautyTool.FOUNDATION -> BeautySlider(
                        label = "Foundation",
                        value = uiState.foundationIntensity,
                        onValueChange = { viewModel.updateFoundationIntensity(it) }
                    )
                    BeautyTool.HAIR_COLOR -> BeautySlider(
                        label = "Hair Color",
                        value = uiState.hairColorIntensity,
                        onValueChange = { viewModel.updateHairColorIntensity(it) }
                    )
                    else -> {}
                }
            }
            BeautyTool.SKIN_DENOISE -> {
                BeautySlider(
                    label = "Denoise",
                    value = uiState.skinDenoiseIntensity,
                    onValueChange = { viewModel.updateSkinDenoiseIntensity(it) }
                )
            }
            null -> {}
        }
    }
}

@Composable
private fun BeautyToolbar(
    selectedTool: BeautyTool?,
    onToolSelected: (BeautyTool?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BeautyTool.entries.forEach { tool ->
            FilterChip(
                selected = selectedTool == tool,
                onClick = {
                    onToolSelected(if (selectedTool == tool) null else tool)
                },
                label = {
                    Text(
                        text = when (tool) {
                            BeautyTool.AUTO_BEAUTY -> "Auto"
                            BeautyTool.SKIN_SMOOTHING -> "Smooth"
                            BeautyTool.TEETH_WHITENING -> "Teeth"
                            BeautyTool.EYE_BRIGHTENING -> "Eyes"
                            BeautyTool.BRIGHTNESS_PEN -> "Pen"
                            BeautyTool.DARK_CIRCLE_REMOVAL -> "Circles"
                            BeautyTool.SPOT_REMOVAL -> "Spots"
                            BeautyTool.WRINKLE_REMOVAL -> "Wrinkles"
                            BeautyTool.SKIN_TONE -> "Skin Tone"
                            BeautyTool.FACE_SLIM -> "Slim"
                            BeautyTool.EYE_ENLARGE -> "Eyes"
                            BeautyTool.NOSE_ADJUST -> "Nose"
                            BeautyTool.JAW_ADJUST -> "Jaw"
                            BeautyTool.LIPSTICK -> "Lips"
                            BeautyTool.BLUSH -> "Blush"
                            BeautyTool.EYE_SHADOW -> "Eye Sh."
                            BeautyTool.EYELINER -> "Liner"
                            BeautyTool.FOUNDATION -> "Base"
                            BeautyTool.HAIR_COLOR -> "Hair"
                            BeautyTool.SKIN_DENOISE -> "Denoise"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun ColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MakeupPalette.colors.forEach { color ->
            val isSelected = selectedColor == color
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

@Composable
private fun BeautySlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isPercent: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (isPercent) "${(value * 100).toInt()}%" else value.toInt().toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )
    }
}

private const val MAX_PREVIEW_DIMENSION = 2048

private fun decodeBounded(contentResolver: ContentResolver, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_PREVIEW_DIMENSION) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }
}