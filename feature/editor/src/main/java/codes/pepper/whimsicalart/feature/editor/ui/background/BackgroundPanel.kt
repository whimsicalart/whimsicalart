package codes.pepper.whimsicalart.feature.editor.ui.background

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.core.domain.wrapDegrees
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehShape
import codes.pepper.whimsicalart.feature.editor.ui.OnOffChipToggle

@Composable
fun BackgroundPanel(
    mode: BackgroundMode,
    blurRadius: Float,
    shape: BokehShape,
    hasSubjectMask: Boolean,
    isSegmenting: Boolean,
    subjectDetectionFailed: Boolean = false,
    selectedBackgroundRes: Int?,
    hasCustomBackground: Boolean,
    placementScale: Float = 1f,
    placementRotationDegrees: Float = 0f,
    freeScaleUp: Boolean = false,
    onModeChange: (BackgroundMode) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onShapeChange: (BokehShape) -> Unit,
    onSegmentSubject: () -> Unit,
    onClearSubject: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onCustomBackground: (android.net.Uri) -> Unit,
    onClearBackground: () -> Unit,
    onScaleChange: (Float) -> Unit = {},
    onRotationChange: (Float) -> Unit = {},
    onFreeScaleUpChange: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onCustomBackground(uri) }
    val sc = LocalUiScale.current

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp * sc, vertical = 8.dp * sc)
    ) {
        SubjectSection(
            hasSubjectMask = hasSubjectMask,
            isSegmenting = isSegmenting,
            detectionFailed = subjectDetectionFailed,
            onSegmentSubject = onSegmentSubject,
            onClearSubject = onClearSubject
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            val chipLabelStyle = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * sc
            )
            BackgroundMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { onModeChange(m) },
                    label = { Text(if (m == BackgroundMode.BLUR) "Blur" else "Replace", style = chipLabelStyle) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        when (mode) {
            BackgroundMode.BLUR -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Intensity",
                        modifier = Modifier.padding(end = 8.dp * sc),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * sc
                        )
                    )
                    Slider(
                        value = blurRadius,
                        onValueChange = onBlurRadiusChange,
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp * sc)
                    )
                    Text(
                        text = "${blurRadius.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * sc
                        )
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    val shapeLabelStyle = MaterialTheme.typography.labelMedium.copy(
                        fontSize = MaterialTheme.typography.labelMedium.fontSize * sc
                    )
                    BokehShape.entries.forEach { s ->
                        FilterChip(
                            selected = shape == s,
                            onClick = { onShapeChange(s) },
                            label = {
                                Text(
                                    s.name.lowercase().replaceFirstChar { it.titlecase() },
                                    style = shapeLabelStyle
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
            BackgroundMode.REPLACE -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp * sc),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(BackgroundPresets.presets) { preset ->
                        PresetThumbnail(
                            painter = painterResource(preset.drawableRes),
                            label = preset.label,
                            selected = selectedBackgroundRes == preset.drawableRes,
                            onClick = { onSelectPreset(preset.drawableRes) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            galleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Text(
                            "Choose from gallery",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * sc
                            )
                        )
                    }
                    if (hasCustomBackground || selectedBackgroundRes != null) {
                        TextButton(onClick = onClearBackground) {
                            Text(
                                "Clear",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize * sc
                                )
                            )
                        }
                    }
                }
                if (hasCustomBackground || selectedBackgroundRes != null) {
                    PlacementControls(
                        scale = placementScale,
                        rotationDegrees = placementRotationDegrees,
                        freeScaleUp = freeScaleUp,
                        onScaleChange = onScaleChange,
                        onRotationChange = onRotationChange,
                        onFreeScaleUpChange = onFreeScaleUpChange
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectSection(
    hasSubjectMask: Boolean,
    isSegmenting: Boolean,
    detectionFailed: Boolean,
    onSegmentSubject: () -> Unit,
    onClearSubject: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val sc = LocalUiScale.current
        if (isSegmenting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp * sc),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Detecting subject…",
                    modifier = Modifier.padding(start = 8.dp * sc),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * sc
                    )
                )
            }
        } else if (hasSubjectMask) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Subject detected",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * sc
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearSubject) {
                    Text(
                        "Redetect",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * sc
                        )
                    )
                }
            }
        } else {
            // Either detection hasn't run yet, or it failed. Surface a
            // persistent explanation so the user knows WHY blur/replace
            // isn't working (instead of a single transient snackbar).
            if (detectionFailed) {
                Text(
                    "No subject detected — ensure a person is clearly visible, then tap below to re-run.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize * sc
                    ),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp * sc))
            }
            TextButton(onClick = onSegmentSubject) {
                Text(
                    "Redetect subject",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * sc
                    )
                )
            }
        }
    }
}

@Composable
private fun PlacementControls(
    scale: Float,
    rotationDegrees: Float,
    freeScaleUp: Boolean,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onFreeScaleUpChange: () -> Unit
) {
    val sc = LocalUiScale.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp * sc)) {
        Text(
            "Placement — drag / pinch / twist on the photo to move, scale and rotate",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * sc
            ),
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Size",
                modifier = Modifier.padding(end = 8.dp * sc),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * sc
                )
            )
            Slider(
                value = scale,
                onValueChange = onScaleChange,
                valueRange = if (freeScaleUp) 0.2f..2f else 0.2f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp * sc)
            )
            Text(
                text = "${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * sc
                )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Rotate",
                modifier = Modifier.padding(end = 8.dp * sc),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * sc
                )
            )
            Slider(
                // Wrapped [0°, 360°) — a full turn flips the slider back to 0°
                // and continues, mirroring the pinch-twist (spec "Rotation wraps
                // at 360°", same rule as stickers/text).
                value = rotationDegrees.coerceIn(0f, 360f),
                onValueChange = onRotationChange,
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp * sc)
            )
            Text(
                text = "${wrapDegrees(rotationDegrees).toInt()}°",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * sc
                )
            )
        }
        OnOffChipToggle("Free scale up", freeScaleUp) { onFreeScaleUpChange() }
        Text(
            "Allow enlarging the placement beyond its fit size (up to 2×)",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = MaterialTheme.typography.labelSmall.fontSize * sc
            ),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun PresetThumbnail(
    painter: Painter,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val sc = LocalUiScale.current
    Column(
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick)
            .width(108.dp * sc),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painter,
            contentDescription = label,
            modifier = Modifier
                .size(108.dp * sc)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp)
                )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * sc
            ),
            modifier = Modifier.padding(top = 4.dp * sc)
        )
    }
}
