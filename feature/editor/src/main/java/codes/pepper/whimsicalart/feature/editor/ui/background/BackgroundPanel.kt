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
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehShape

@Composable
fun BackgroundPanel(
    mode: BackgroundMode,
    blurRadius: Float,
    shape: BokehShape,
    hasSubjectMask: Boolean,
    isSegmenting: Boolean,
    selectedBackgroundRes: Int?,
    hasCustomBackground: Boolean,
    onModeChange: (BackgroundMode) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onShapeChange: (BokehShape) -> Unit,
    onSegmentSubject: () -> Unit,
    onClearSubject: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onCustomBackground: (android.net.Uri) -> Unit,
    onClearBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onCustomBackground(uri) }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SubjectSection(
            hasSubjectMask = hasSubjectMask,
            isSegmenting = isSegmenting,
            onSegmentSubject = onSegmentSubject,
            onClearSubject = onClearSubject
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            BackgroundMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { onModeChange(m) },
                    label = { Text(if (m == BackgroundMode.BLUR) "Blur" else "Replace") },
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
                    Text("Intensity", modifier = Modifier.padding(end = 8.dp))
                    Slider(
                        value = blurRadius,
                        onValueChange = onBlurRadiusChange,
                        valueRange = 5f..40f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("${blurRadius.toInt()}")
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    BokehShape.entries.forEach { s ->
                        FilterChip(
                            selected = shape == s,
                            onClick = { onShapeChange(s) },
                            label = { Text(s.name.lowercase().replaceFirstChar { it.titlecase() }) },
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        Text("Choose from gallery")
                    }
                    if (hasCustomBackground || selectedBackgroundRes != null) {
                        TextButton(onClick = onClearBackground) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectSection(
    hasSubjectMask: Boolean,
    isSegmenting: Boolean,
    onSegmentSubject: () -> Unit,
    onClearSubject: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isSegmenting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Text("Detecting subject…", modifier = Modifier.padding(start = 8.dp))
            }
        } else if (hasSubjectMask) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Subject detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearSubject) { Text("Redetect") }
            }
        } else {
            // Auto-detection is triggered when the Background tool is selected;
            // just offer a manual re-run for convenience.
            TextButton(onClick = onSegmentSubject) { Text("Redetect subject") }
        }
    }
}

@Composable
private fun PresetThumbnail(
    painter: Painter,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick)
            .width(108.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painter,
            contentDescription = label,
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp)
                )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
