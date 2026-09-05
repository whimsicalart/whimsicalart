package codes.pepper.whimsicalart.feature.editor.ui.mosaic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun MosaicOverlay(
    strokes: List<MosaicStroke>,
    currentStroke: MosaicStroke?,
    suggestedRegions: List<android.graphics.RectF>,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    brushingEnabled: Boolean = true
) {
    val allStrokes = currentStroke?.let { strokes + it } ?: strokes

    Canvas(
        modifier = modifier
            .then(
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
        suggestedRegions.forEach { region ->
            drawRect(
                color = Color(0x22FFEB3B),
                topLeft = Offset(region.left * size.width, region.top * size.height),
                size = Size(region.width() * size.width, region.height() * size.height)
            )
            drawRect(
                color = Color(0x88FFC107),
                topLeft = Offset(region.left * size.width, region.top * size.height),
                size = Size(region.width() * size.width, region.height() * size.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
            )
        }
        allStrokes.forEach { stroke ->
            when (stroke.brushType) {
                MosaicBrushType.PIXEL -> drawPixelMosaic(stroke)
                MosaicBrushType.BLUR -> drawBlurStroke(stroke)
                MosaicBrushType.CUSTOM -> drawPatternStroke(stroke)
            }
        }
    }
}

private fun DrawScope.drawPixelMosaic(stroke: MosaicStroke) {
    val cell = ((stroke.brushSize * 1.4f).coerceAtLeast(8f))
    drawAlong(stroke.points, spacing = cell / 2f) { p ->
        drawRect(
            color = patternColor(p, stroke),
            topLeft = Offset(p.x - cell / 2f, p.y - cell / 2f),
            size = Size(cell, cell)
        )
    }
}

private fun DrawScope.drawBlurStroke(stroke: MosaicStroke) {
    val radius = stroke.brushSize.coerceAtLeast(6f)
    drawAlong(stroke.points, spacing = radius * 0.6f) { p ->
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.8f * stroke.opacity),
                    Color.White.copy(alpha = 0f)
                ),
                center = p,
                radius = radius
            ),
            radius = radius,
            center = p
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.35f * stroke.opacity),
            radius = radius * 0.45f,
            center = p
        )
    }
}

private fun DrawScope.drawPatternStroke(stroke: MosaicStroke) {
    val patternSize = stroke.brushSize.coerceAtLeast(10f)
    drawAlong(stroke.points, spacing = patternSize * 0.5f) { p ->
        drawSparkle(
            center = p,
            radius = patternSize,
            color = patternColor(p, stroke),
            alpha = stroke.opacity
        )
    }
}

private inline fun drawAlong(
    points: List<Offset>,
    spacing: Float,
    block: (Offset) -> Unit
) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        block(points[0])
        return
    }
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val distance = (b - a).getDistance()
        val steps = ceil(distance / spacing).toInt().coerceAtLeast(1)
        for (s in 0..steps) {
            block(
                Offset(
                    a.x + (b.x - a.x) * s / steps,
                    a.y + (b.y - a.y) * s / steps
                )
            )
        }
    }
}

private val mosaicPalette = listOf(
    Color(0xFFEF9A9A), Color(0xFF90CAF9), Color(0xFFA5D6A7),
    Color(0xFFFFE082), Color(0xFFCE93D8), Color(0xFF80CBC4),
    Color(0xFFBCAAA4), Color(0xFFB0BEC5)
)

private fun patternColor(p: Offset, stroke: MosaicStroke): Color {
    val hash = (p.x * 73856093).toInt() xor (p.y * 19349663).toInt()
    val idx = abs(hash) % mosaicPalette.size
    return mosaicPalette[idx].copy(alpha = stroke.opacity)
}

private fun DrawScope.drawSparkle(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    val path = Path()
    val a = radius
    val b = radius * 0.28f
    path.moveTo(center.x, center.y - a)
    path.lineTo(center.x + b, center.y - b)
    path.lineTo(center.x + a, center.y)
    path.lineTo(center.x + b, center.y + b)
    path.lineTo(center.x, center.y + a)
    path.lineTo(center.x - b, center.y + b)
    path.lineTo(center.x - a, center.y)
    path.lineTo(center.x - b, center.y - b)
    path.close()

    drawPath(
        path = path,
        color = color.copy(alpha = alpha.coerceIn(0f, 1f))
    )
}