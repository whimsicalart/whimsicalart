package codes.pepper.whimsicalart.feature.editor.ui.removal

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

@Composable
fun RemovalPanel(
    state: RemovalEditorState,
    onBrushSizeChange: (Float) -> Unit,
    onToggleErasing: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showClearConfirm = remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Size", modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = state.brushSize,
                onValueChange = onBrushSizeChange,
                valueRange = 12f..80f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("${state.brushSize.toInt()}")
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = state.isErasing,
                onClick = onToggleErasing,
                label = { Text("Eraser") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                )
            )
            TextButton(onClick = { showClearConfirm.value = true }) {
                Text("Clear All")
            }
        }
    }

    if (showClearConfirm.value) {
        AlertDialog(
            onDismissRequest = { showClearConfirm.value = false },
            title = { Text("Remove selected strokes?") },
            text = { Text("The painted areas will be restored to the original.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirm.value = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm.value = false }) { Text("Cancel") }
            }
        )
    }
}