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
import codes.pepper.whimsicalart.core.domain.UniformFit
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
        val bitmapWidth = bitmapSize.width.toInt()
        val bitmapHeight = bitmapSize.height.toInt()

        fun toBitmap(offset: Offset, fit: UniformFit): PointF = PointF(
            (offset.x - fit.offsetX) / fit.measureX(1f, bitmapWidth),
            (offset.y - fit.offsetY) / fit.measureY(1f, bitmapHeight)
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            // Captured against the SOURCE reference frame fitted
                            // into the box (bitmapSize) — the same frame both the
                            // committed bake and the draggable placement use.
                            val fit = UniformFit.of(maxWidth.toInt(), maxHeight.toInt(), bitmapWidth, bitmapHeight)
                            onDragStart(toBitmap(position, fit))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fit = UniformFit.of(maxWidth.toInt(), maxHeight.toInt(), bitmapWidth, bitmapHeight)
                            onDragMove(toBitmap(change.position, fit))
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
                    )
                }
        ) {
            val all = activeStroke?.let { strokes + it } ?: strokes
            all.forEach { stroke ->
                val opacity = stroke.opacity.coerceIn(0f, 1f)
                // Anchored strokes re-express through the uniform fit of their
                // reference into the box (docs/effect_stack.md → *Positional
                // layers*); an unset reference fits against the source dims —
                // the historical letterbox, preserved exactly.
                val refW = stroke.referenceWidth.takeIf { it > 0 } ?: bitmapWidth
                val refH = stroke.referenceHeight.takeIf { it > 0 } ?: bitmapHeight
                val fit = UniformFit.of(maxWidth.toInt(), maxHeight.toInt(), refW, refH)
                val radius = (fit.measureX(stroke.size, refW) / 2f).coerceAtLeast(1f)
                stroke.points.forEach { point ->
                    drawCircle(
                        color = Color(color).copy(alpha = opacity),
                        radius = radius,
                        center = Offset(
                            fit.frameX(point.x, refW),
                            fit.frameY(point.y, refH)
                        ),
                        blendMode = BlendMode.Plus
                    )
                }
            }
        }
    }
}