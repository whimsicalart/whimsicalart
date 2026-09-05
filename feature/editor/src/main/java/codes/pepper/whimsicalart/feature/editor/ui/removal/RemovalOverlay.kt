package codes.pepper.whimsicalart.feature.editor.ui.removal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.ceil

/**
 * Real-time preview of the object-removal brush: the painted region is shaded so
 * the user sees exactly which area will be inpainted at render time. The actual
 * fill happens in [codes.pepper.whimsicalart.feature.editor.domain.BitmapRenderer].
 */
@Composable
fun RemovalOverlay(
    strokes: List<RemovalStroke>,
    currentStroke: RemovalStroke?,
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
        all.forEach { stroke -> drawRemovalStroke(stroke) }
    }
}

private fun DrawScope.drawRemovalStroke(stroke: RemovalStroke) {
    val radius = stroke.brushSize.coerceAtLeast(8f)
    if (stroke.points.size <= 1) {
        drawRemovalDisc(stroke.points[0], radius)
        return
    }
    for (i in 0 until stroke.points.size - 1) {
        val a = stroke.points[i]
        val b = stroke.points[i + 1]
        val dist = (b - a).getDistance()
        val steps = ceil(dist / (radius * 0.5f)).toInt().coerceAtLeast(1)
        for (s in 0..steps) {
            drawRemovalDisc(
                Offset(
                    a.x + (b.x - a.x) * s / steps,
                    a.y + (b.y - a.y) * s / steps
                ),
                radius
            )
        }
    }
}

private fun DrawScope.drawRemovalDisc(center: Offset, radius: Float) {
    drawPath(
        path = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center.x - radius, center.y - radius,
                    center.x + radius, center.y + radius
                )
            )
        },
        color = Color(0x66FFFFFF)
    )
    drawCircle(color = Color(0x33FFFFFF), radius = radius, center = center)
}