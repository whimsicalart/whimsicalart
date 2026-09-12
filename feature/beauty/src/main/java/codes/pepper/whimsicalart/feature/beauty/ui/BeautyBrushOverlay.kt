package codes.pepper.whimsicalart.feature.beauty.ui

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import codes.pepper.whimsicalart.feature.beauty.domain.BrushStroke
import kotlin.math.min

/**
 * The beauty pen's live drag/brush overlay (EditTool.BEAUTY + BRIGHTNESS_PEN).
 *
 * Like every source-space brush tool the overlay is drawn in the RAW source
 * coordinate space and is wrapped in the shared viewport pan/zoom transform
 * (`.panZoomLayer` in the editor), so committed strokes stay glued to the base
 * image while the user pans and zooms. Points are captured against [bitmapSize]
 * (the SOURCE dims, never a downscaled preview) fitted+centred into the box —
 * exactly the letterbox the editor's ContentScale.Fit image uses — and stored as
 * NORMALIZED fractions. The committed bake ([BeautyPenEffect] →
 * `applyBrightnessPen`) maps those same fractions against whatever resolution it
 * renders at, keeping the stroke resolution-independent and zoom/lock consistent.
 *
 * The preview mirrors the bake: hard colour-tinted ADD circles ([BlendMode.Plus]
 * ≈ the processor's PorterDuff ADD) so what the user paints looks like the
 * committed stroke.
 */
@Composable
fun BeautyBrushOverlay(
    bitmapSize: Size?,
    strokes: List<BrushStroke>,
    activeStroke: BrushStroke?,
    color: Int,
    onDragStart: (PointF) -> Unit,
    onDragMove: (PointF) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (bitmapSize == null || bitmapSize.width <= 0f || bitmapSize.height <= 0f) return

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()
        val bitmapWidth = bitmapSize.width
        val bitmapHeight = bitmapSize.height
        val scale = min(maxWidth / bitmapWidth, maxHeight / bitmapHeight)
        val drawWidth = bitmapWidth * scale
        val drawHeight = bitmapHeight * scale
        val offsetX = (maxWidth - drawWidth) / 2f
        val offsetY = (maxHeight - drawHeight) / 2f

        fun toBitmap(offset: Offset): PointF = PointF(
            (offset.x - offsetX) / drawWidth,
            (offset.y - offsetY) / drawHeight
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            onDragStart(toBitmap(position))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onDragMove(toBitmap(change.position))
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
                    )
                }
        ) {
            val all = activeStroke?.let { strokes + it } ?: strokes
            all.forEach { stroke ->
                val opacity = stroke.opacity.coerceIn(0f, 1f)
                val radius = (stroke.size * drawWidth / 2f).coerceAtLeast(1f)
                stroke.points.forEach { point ->
                    drawCircle(
                        color = Color(color).copy(alpha = opacity),
                        radius = radius,
                        center = Offset(
                            offsetX + point.x * drawWidth,
                            offsetY + point.y * drawHeight
                        ),
                        blendMode = BlendMode.Plus
                    )
                }
            }
        }
    }
}