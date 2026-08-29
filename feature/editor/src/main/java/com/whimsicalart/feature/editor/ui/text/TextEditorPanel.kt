package com.whimsicalart.feature.editor.ui.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TextEditorPanel(
    state: TextEditorUiState,
    onTextChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit,
    onBackgroundShapeChange: (TextBackgroundShape) -> Unit,
    onBackgroundColorChange: (Color) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColors = listOf(
        Color.White, Color.Black, Color.Red, Color(0xFFFFC107),
        Color(0xFF00BCD4), Color(0xFF4CAF50), Color(0xFF9C27B0)
    )
    val bgColors = listOf(
        Color.Black, Color.White, Color(0xFF9E9E9E), Color(0xFF2196F3),
        Color(0xFFE91E63), Color(0xFFFF9800), Color.Transparent
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = state.currentText,
            onValueChange = onTextChange,
            label = { Text("Text") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Size", modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = state.currentFontSize,
                onValueChange = onFontSizeChange,
                valueRange = 10f..72f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("${state.currentFontSize.toInt()}")
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            textColors.forEach { color ->
                ColorSwatch(
                    color = color,
                    selected = state.currentColor == color,
                    onClick = { onColorChange(color) }
                )
            }
        }

        Row(modifier = Modifier.padding(top = 4.dp)) {
            TextBackgroundShape.entries.forEach { shape ->
                FilterChip(
                    selected = state.currentBackgroundShape == shape,
                    onClick = { onBackgroundShapeChange(shape) },
                    label = { Text(shape.name.lowercase().capitalize()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            bgColors.forEach { color ->
                ColorSwatch(
                    color = color,
                    selected = state.currentBackgroundColor == color,
                    onClick = { onBackgroundColorChange(color) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = onAdd) { Text("Add") }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = CircleShape
    if (color == Color.Transparent) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(24.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable { onClick() }
        )
    } else {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(24.dp)
                .clip(shape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = shape
                )
                .clickable { onClick() }
        )
    }
}

private fun String.capitalize(): String = replaceFirstChar { it.uppercase() }