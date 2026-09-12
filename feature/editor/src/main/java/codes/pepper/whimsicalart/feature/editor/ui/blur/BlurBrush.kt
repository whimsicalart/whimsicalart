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
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale

@Composable
fun BlurBrushOverlay(
    strokes: List<BlurStroke>,
    currentStroke: BlurStroke?,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    brushingEnabled: Boolean = true,
    /**
     * True when the preview is currently showing the REAL blur for the
     * committed strokes, so only the in-progress stroke keeps the fake
     * tint overlay (docs/effects_reference.md → Live-applied brush effects).
     */
    showLiveResult: Boolean = false
) {
    val all = when {
        showLiveResult -> currentStroke?.let { listOf(it) } ?: emptyList()
        else -> currentStroke?.let { strokes + it } ?: strokes
    }

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
                valueRange = 10f..80f,
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
                text = "Strength",
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
            val chipLabelStyle = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * s
            )
            FilterChip(
                selected = state.isErasing,
                onClick = onToggleErasing,
                label = { Text("Eraser", style = chipLabelStyle) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
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
            title = { Text("Clear blur strokes?", style = dialogTitleStyle) },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirm.value = false
                }) { Text("Clear", style = dialogStyle) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm.value = false }) { Text("Cancel", style = dialogStyle) }
            }
        )
    }
}