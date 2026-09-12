package codes.pepper.whimsicalart.feature.editor.ui.mosaic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale

@Composable
fun MosaicPanel(
    state: MosaicEditorState,
    imageUri: android.net.Uri?,
    onBrushSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onToggleErasing: () -> Unit,
    onClear: () -> Unit,
    onSuggestFaces: () -> Unit,
    onClearSuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showClearConfirm = remember { mutableStateOf(false) }
    val s = LocalUiScale.current

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp * s, vertical = 8.dp * s)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Size",
                modifier = Modifier.padding(end = 8.dp * s),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                )
            )
            Slider(
                value = state.brushSize,
                onValueChange = onBrushSizeChange,
                valueRange = 6f..60f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp * s)
            )
            Text(
                text = "${state.brushSize.toInt()}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                )
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Opacity",
                modifier = Modifier.padding(end = 8.dp * s),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                )
            )
            Slider(
                value = state.opacity,
                onValueChange = onOpacityChange,
                valueRange = 0.1f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp * s)
            )
            Text(
                text = "${(state.opacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                )
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onSuggestFaces,
                enabled = imageUri != null && !state.isSuggesting
            ) {
                Text(if (state.isSuggesting) "Detecting…" else "Suggest faces (privacy)")
            }
            if (state.suggestedRegions.isNotEmpty()) {
                TextButton(onClick = onClearSuggestions) {
                    Text("Clear suggestions")
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            val labelStyle = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * s
            )
            FilterChip(
                selected = state.isErasing,
                onClick = onToggleErasing,
                label = { Text("Eraser", style = labelStyle) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                )
            )
            TextButton(onClick = { showClearConfirm.value = true }) {
                Text(
                    "Clear All",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            }
        }
    }

    if (showClearConfirm.value) {
        val dialogStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = MaterialTheme.typography.bodyLarge.fontSize * s
        )
        val dialogTitleStyle = MaterialTheme.typography.titleLarge.copy(
            fontSize = MaterialTheme.typography.titleLarge.fontSize * s
        )
        AlertDialog(
            onDismissRequest = { showClearConfirm.value = false },
            title = { Text("Clear mosaic strokes?", style = dialogTitleStyle) },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirm.value = false
                }) { Text("Clear", style = dialogStyle) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm.value = false }) {
                    Text("Cancel", style = dialogStyle)
                }
            }
        )
    }
}