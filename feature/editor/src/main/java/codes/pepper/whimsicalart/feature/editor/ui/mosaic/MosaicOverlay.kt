package codes.pepper.whimsicalart.feature.editor.ui.mosaic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import codes.pepper.whimsicalart.feature.editor.ui.ImageCanvasFrame
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun MosaicOverlay(
    strokes: List<MosaicStroke>,
    currentStroke: MosaicStroke?,
    suggestedRegions: List<android.graphics.RectF>,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    brushingEnabled: Boolean = true,
    /**
     * True when the preview is currently showing the REAL pixelation for the
     * committed strokes, so only the in-progress stroke keeps the patterned
     * stand-in overlay (docs/effects_reference.md → Live-applied brush effects).
     * The suggested-regions frame stays independent of this folding.
     */
    showLiveResult: Boolean = false,
    /**
     * The fitted image rect (letterbox offset + size) used only to anchor the
     * ML suggested-regions guides over the image; stroke capture stays in box px.
     */
    imageFrame: ImageCanvasFrame = ImageCanvasFrame.DEFAULT
) {
    val allStrokes = when {
        showLiveResult -> currentStroke?.let { listOf(it) } ?: emptyList()
        else -> currentStroke?.let { strokes + it } ?: strokes
    }

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
            val ax = imageFrame.offsetX + region.left * imageFrame.size.width
            val ay = imageFrame.offsetY + region.top * imageFrame.size.height
            drawRect(
                color = Color(0x22FFEB3B),
                topLeft = Offset(ax, ay),
                size = Size(region.width() * imageFrame.size.width, region.height() * imageFrame.size.height)
            )
            drawRect(
                color = Color(0x88FFC107),
                topLeft = Offset(ax, ay),
                size = Size(region.width() * imageFrame.size.width, region.height() * imageFrame.size.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
            )
        }
        allStrokes.forEach { stroke ->
            drawPixelMosaic(stroke)
        }
    }
}

private fun DrawScope.drawPixelMosaic(stroke: MosaicStroke) {
    // Mirrors the committed raster's cell exactly (BitmapRenderer.kt applyPixelMosaic):
    // `brushSize` rounded in box px with the relative short-edge floor, so the live
    // stand-in cells are the SAME size as the committed pixelation at any resolution.
    val cell = pixelMosaicCellPx(stroke.brushSize, size.width, size.height)
    drawAlong(stroke.points, spacing = cell / 2f) { p ->
        drawRect(
            color = patternColor(p, stroke),
            topLeft = Offset(p.x - cell / 2f, p.y - cell / 2f),
            size = Size(cell.toFloat(), cell.toFloat())
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

/**
 * The live pixel-mosaic cell in box px, byte-for-byte the committed raster's
 * formula (BitmapRenderer.kt applyPixelMosaic): the brush size rounded to px,
 * floored by the relative short-edge cell so a 3000px-wide photo still gets a
 * chunkier pixelation than a 500px preview. Both sides consume the SAME
 * brush size — the overlay's box-px brushSize equals the raster's
 * `brushSize × surfaceWidth` at preview resolution — so the stand-in cells and
 * the committed cells are interchangeable at every resolution.
 */
internal fun pixelMosaicCellPx(brushSizePx: Float, canvasWidthPx: Float, canvasHeightPx: Float): Int =
    brushSizePx
        .roundToInt()
        .coerceAtLeast((minOf(canvasWidthPx, canvasHeightPx) / 140f).coerceAtLeast(1f).roundToInt())

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