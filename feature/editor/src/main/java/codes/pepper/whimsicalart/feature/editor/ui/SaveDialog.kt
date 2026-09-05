package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.feature.editor.domain.ImageFormat
import codes.pepper.whimsicalart.feature.editor.domain.ImageQuality
import codes.pepper.whimsicalart.feature.editor.domain.Resolution
import codes.pepper.whimsicalart.feature.editor.domain.SaveConfig

@Composable
fun SaveDialog(
    onDismiss: () -> Unit,
    onSave: (SaveConfig) -> Unit,
    onShare: (SaveConfig) -> Unit
) {
    var format by remember { mutableStateOf(ImageFormat.JPEG) }
    var quality by remember { mutableStateOf(ImageQuality.HIGH) }
    var resolution by remember { mutableStateOf(Resolution.ORIGINAL) }
    var album by remember { mutableStateOf("WhimsicalArt") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChipRow(
                    label = "Format",
                    options = listOf("JPEG", "PNG"),
                    selected = format.name,
                    onSelect = {
                        format = if (it == "JPEG") ImageFormat.JPEG else ImageFormat.PNG
                    }
                )
                ChipRow(
                    label = "Quality",
                    options = ImageQuality.entries.map { it.name },
                    selected = quality.name,
                    onSelect = { quality = ImageQuality.valueOf(it) }
                )
                ChipRow(
                    label = "Resolution",
                    options = Resolution.entries.map { it.label },
                    selected = resolution.label,
                    onSelect = { label -> resolution = Resolution.entries.first { it.label == label } }
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album / folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onShare(config(format, quality, resolution, album)) }) {
                    Text("Share")
                }
                TextButton(onClick = { onSave(config(format, quality, resolution, album)) }) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

private fun config(
    format: ImageFormat,
    quality: ImageQuality,
    resolution: Resolution,
    album: String
): SaveConfig {
    val folder = album.trim().ifEmpty { "WhimsicalArt" }
    return SaveConfig(
        format = format,
        quality = quality,
        resolution = resolution,
        album = folder
    )
}