package com.whimsicalart.feature.beauty.ui

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.whimsicalart.feature.beauty.domain.BrushStroke
import kotlin.math.min

@Composable
fun BeautyBrushOverlay(
    bitmapSize: Size?,
    activeStroke: BrushStroke?,
    onStart: (PointF) -> Unit,
    onMove: (PointF) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (bitmapSize == null) return

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

        fun toBitmap(offset: Offset): PointF {
            return PointF(
                (offset.x - offsetX) / scale,
                (offset.y - offsetY) / scale
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            onStart(toBitmap(position))
                        },
                        onDrag = { change, _ ->
                            onMove(toBitmap(change.position))
                        },
                        onDragEnd = { onEnd() },
                        onDragCancel = { onEnd() }
                    )
                }
        ) {
            val stroke = activeStroke ?: return@Canvas
            val radius = stroke.size * scale / 2f
            stroke.points.forEach { point ->
                drawCircle(
                    color = Color.White.copy(alpha = (stroke.opacity * 0.9f).coerceIn(0f, 1f)),
                    radius = radius,
                    center = Offset(
                        offsetX + point.x * scale,
                        offsetY + point.y * scale
                    )
                )
            }
        }
    }
}