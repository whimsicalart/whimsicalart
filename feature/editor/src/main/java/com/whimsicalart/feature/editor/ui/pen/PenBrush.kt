package com.whimsicalart.feature.editor.ui.pen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun PenStrokeOverlay(
    strokes: List<PenStroke>,
    currentStroke: PenStroke?,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val all = currentStroke?.let { strokes + it } ?: strokes

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> onDragStart(offset) },
                onDrag = { change, _ ->
                    change.consume()
                    onDragMove(change.position)
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() }
            )
        }
    ) {
        all.forEach { stroke -> drawPenStroke(stroke) }
    }
}

private fun DrawScope.drawPenStroke(stroke: PenStroke) {
    val width = stroke.size.coerceAtLeast(2f)
    if (stroke.points.isEmpty()) return
    if (stroke.points.size == 1) {
        drawPenDot(stroke.points[0], width, stroke)
        return
    }
    for (i in 0 until stroke.points.size - 1) {
        drawPenSegment(stroke.points[i], stroke.points[i + 1], width, i, stroke)
    }
}

private fun DrawScope.drawPenDot(center: Offset, width: Float, stroke: PenStroke) {
    drawPenSegment(center, center, width, 0, stroke)
}

private fun DrawScope.drawPenSegment(
    a: Offset,
    b: Offset,
    width: Float,
    index: Int,
    stroke: PenStroke
) {
    when (stroke.brushType) {
        PenBrushType.SOLID -> {
            drawLine(
                color = stroke.color,
                start = a,
                end = b,
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }
        PenBrushType.GLOW -> {
            drawLine(
                color = stroke.color,
                start = a,
                end = b,
                strokeWidth = width * 4f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = stroke.color.copy(alpha = 0.25f),
                start = a,
                end = b,
                strokeWidth = width * 7f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = a,
                end = b,
                strokeWidth = width * 0.6f,
                cap = StrokeCap.Round
            )
        }
        PenBrushType.NEON -> {
            drawLine(
                color = stroke.color.copy(alpha = 0.35f),
                start = a,
                end = b,
                strokeWidth = width * 3.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = stroke.color,
                start = a,
                end = b,
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }
        PenBrushType.RAINBOW -> {
            val color = rainbowColor(index, stroke)
            drawLine(
                color = color,
                start = a,
                end = b,
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun rainbowColor(index: Int, stroke: PenStroke): Color {
    val hue = (index * 18f) % 360f
    return Color.hsv(hue, 0.9f, 1f).copy(alpha = stroke.color.alpha.coerceIn(0f, 1f))
}

private val penPalette = listOf(
    Color.Black,
    Color.White,
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFFFDD835),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
    Color(0xFF8E24AA),
    Color(0xFF6D4C41)
)

@Composable
fun PenBrushPanel(
    state: PenEditorState,
    onBrushTypeChange: (PenBrushType) -> Unit,
    onColorChange: (Color) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onAddLayer: () -> Unit,
    onRemoveLayer: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onToggleLayerVisibility: (Int) -> Unit,
    onMoveLayerToTop: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val showClearConfirm = remember { mutableStateOf(false) }
    val showRemoveLayerConfirm = remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            PenBrushType.entries.forEach { type ->
                FilterChip(
                    selected = state.selectedBrushType == type,
                    onClick = { onBrushTypeChange(type) },
                    label = {
                        Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            penPalette.forEach { color ->
                val selected = state.selectedColor == color
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onColorChange(color) }
                        )
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Size", modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = state.brushSize,
                onValueChange = onBrushSizeChange,
                valueRange = 2f..48f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("${state.brushSize.toInt()}")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Layers", modifier = Modifier.padding(end = 8.dp))
            TextButton(onClick = onAddLayer) { Text("Add") }
            TextButton(
                onClick = { showRemoveLayerConfirm.value = true },
                enabled = state.layers.size > 1
            ) { Text("Delete") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.layers.forEachIndexed { index, layer ->
                val active = state.activeLayerIndex == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectLayer(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        layer.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (layer.visible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline
                    )
                    Row {
                        TextButton(
                            onClick = { onToggleLayerVisibility(index) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(if (layer.visible) "Hide" else "Show")
                        }
                        if (!active && state.layers.size > 1) {
                            TextButton(
                                onClick = { onMoveLayerToTop(index) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Top")
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onUndo,
                enabled = state.strokes.isNotEmpty()
            ) { Text("Undo") }
            TextButton(
                onClick = onRedo,
                enabled = state.undoStack.isNotEmpty()
            ) { Text("Redo") }
            TextButton(onClick = { showClearConfirm.value = true }) { Text("Clear All") }
        }
    }

    if (showClearConfirm.value) {
        AlertDialog(
            onDismissRequest = { showClearConfirm.value = false },
            title = { Text("Clear pen strokes?") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirm.value = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRemoveLayerConfirm.value) {
        AlertDialog(
            onDismissRequest = { showRemoveLayerConfirm.value = false },
            title = { Text("Delete active layer?") },
            text = { Text("The active layer and its strokes will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveLayer()
                    showRemoveLayerConfirm.value = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveLayerConfirm.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
