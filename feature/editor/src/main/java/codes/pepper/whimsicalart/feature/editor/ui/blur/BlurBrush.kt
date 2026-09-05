package codes.pepper.whimsicalart.feature.editor.ui.blur

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

@Composable
fun BlurBrushOverlay(
    strokes: List<BlurStroke>,
    currentStroke: BlurStroke?,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    brushingEnabled: Boolean = true
) {
    val all = currentStroke?.let { strokes + it } ?: strokes

    Canvas(
        modifier = modifier.then(
            if (brushingEnabled) {
                Modifier.pointerInput(Unit) {
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
            } else {
                Modifier
            }
        )
    ) {
        all.forEach { stroke -> drawBlurStroke(stroke) }
    }
}

private fun DrawScope.drawBlurStroke(stroke: BlurStroke) {
    val radius = stroke.brushSize.coerceAtLeast(10f)
    val alpha = stroke.opacity.coerceIn(0f, 1f)
    if (stroke.points.size <= 1) {
        drawBlurDisc(stroke.points[0], radius, alpha)
        return
    }
    for (i in 0 until stroke.points.size - 1) {
        val a = stroke.points[i]
        val b = stroke.points[i + 1]
        val dist = (b - a).getDistance()
        val steps = ceil(dist / (radius * 0.5f)).toInt().coerceAtLeast(1)
        for (s in 0..steps) {
            drawBlurDisc(
                Offset(
                    a.x + (b.x - a.x) * s / steps,
                    a.y + (b.y - a.y) * s / steps
                ),
                radius,
                alpha
            )
        }
    }
}

private fun DrawScope.drawBlurDisc(center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFB9C6D6).copy(alpha = alpha * 0.35f),
                Color(0xFFA8B7C9).copy(alpha = alpha * 0.18f),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color(0xFF9FB0C4).copy(alpha = alpha * 0.12f),
        radius = radius * 0.42f,
        center = center
    )
    drawCircle(
        color = Color(0xFF9FB0C4).copy(alpha = alpha * 0.06f),
        radius = radius * 0.75f,
        center = center
    )
}

@Composable
fun BlurBrushPanel(
    state: BlurEditorState,
    onBrushSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
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
                valueRange = 10f..80f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("${state.brushSize.toInt()}")
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Strength", modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = state.opacity,
                onValueChange = onOpacityChange,
                valueRange = 0.1f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("${(state.opacity * 100).toInt()}%")
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = state.isErasing,
                onClick = onToggleErasing,
                label = { Text("Eraser") },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                )
            )
            TextButton(onClick = { showClearConfirm.value = true }) { Text("Clear All") }
        }
    }

    if (showClearConfirm.value) {
        AlertDialog(
            onDismissRequest = { showClearConfirm.value = false },
            title = { Text("Clear blur strokes?") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirm.value = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm.value = false }) { Text("Cancel") }
            }
        )
    }
}