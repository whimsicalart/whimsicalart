package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlin.math.hypot

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ONE_ONE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_TWO("3:2", 3f / 2f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    NINE_SIXTEEN("9:16", 9f / 16f)
}

@Composable
fun CropOverlay(
    imageSize: Size,
    aspectRatio: CropAspectRatio,
    initialCrop: Rect = Rect(0f, 0f, 1f, 1f),
    onCropChanged: (Rect) -> Unit,
    onCropApplied: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var overlaySize by remember { mutableStateOf(Size.Zero) }
    var cropRect by remember(initialCrop) { mutableStateOf(initialCrop) }
    var lastCommittedCrop by remember(initialCrop) { mutableStateOf(initialCrop) }

    // The ratio enforced on the frame is the NORMALISED target so it displays
    // (and commits) at exactly the selected ratio on the letterboxed image —
    // a raw 1:1 would otherwise show as a non-square on a non-square image.
    val ratioTarget = aspectRatio.ratio?.let { normalizedAspectRatio(it, imageSize) }

    // When the user switches aspect ratio (or toggles Free), reshape the crop
    // frame to the new ratio — centred and clipped to the current bounds — so
    // the ratio buttons visibly take effect instead of leaving the frame as-is.
    // Re-keyed on imageSize so a rotation/photo change recomputes the target.
    LaunchedEffect(aspectRatio, imageSize) {
        val ratio = ratioTarget ?: return@LaunchedEffect
        val reshaped = reshapeCropToRatio(cropRect, ratio)
        cropRect = reshaped
        onCropChanged(reshaped)
        if (reshaped != lastCommittedCrop) {
            lastCommittedCrop = reshaped
            onCropApplied()
        }
    }

    val fit = fitRect(overlaySize, imageSize)
    val fitWidth = fit.size.width
    val fitHeight = fit.size.height
    val fitOffsetX = fit.offset.x
    val fitOffsetY = fit.offset.y

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(
                aspectRatio, imageSize, fitWidth, fitHeight, fitOffsetX, fitOffsetY
            ) {
                // A SINGLE unified gesture handler drives the crop frame. Two
                // separate detectors (one detectDragGestures + one
                // detectTransformGestures) on the same node fight over the same
                // pointer stream — detectTransformGestures consumes every move
                // (including single-finger "pan"), so the border drag (move /
                // resize) never receives a clean event and appears broken. Here
                // the same stream is interpreted by pointer count: multi-touch
                // pinches scale the selection, a single-finger drag moves or
                // resizes it. Both work together — pinch is a plus, not a
                // replacement.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPosition = down.position
                    // Where the gesture began (display px) mapped onto the crop
                    // frame determines move vs. which edge/corner resizes.
                    var dragMode: CropDragMode? = null
                    if (fitWidth > 0f && fitHeight > 0f) {
                        val displayCrop = Rect(
                            left = fitOffsetX + cropRect.left * fitWidth,
                            top = fitOffsetY + cropRect.top * fitHeight,
                            right = fitOffsetX + cropRect.right * fitWidth,
                            bottom = fitOffsetY + cropRect.bottom * fitHeight
                        )
                        dragMode = hitTestCrop(displayCrop, startPosition)
                    }
                    // Snapshot the rect at gesture start so single-finger drags
                    // follow the pointer absolutely (delta from start), avoiding
                    // accumulated clamp jitter across events.
                    val initialRect = cropRect
                    var lastSpan = 0f
                    var sawPinch = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            // Multi-finger pinch: scale the selection about its
                            // centre (the span change between this and the previous
                            // event is the zoom factor), keeping the aspect ratio
                            // locked when selected.
                            sawPinch = true
                            val p0 = pressed[0].position
                            val p1 = pressed[1].position
                            val dx = p1.x - p0.x
                            val dy = p1.y - p0.y
                            val span = hypot(dx, dy)
                            if (lastSpan > 0f) {
                                val zoom = span / lastSpan
                                if (abs(zoom - 1f) >= 1e-4f) {
                                    val newRect = scaleCropRect(cropRect, zoom, ratioTarget)
                                    cropRect = newRect
                                    onCropChanged(newRect)
                                }
                            }
                            lastSpan = span
                        } else if (pressed.isNotEmpty() && !sawPinch && dragMode != null) {
                            // Single-finger drag: move the frame, or resize the
                            // edge/corner the gesture started on (a pinch that
                            // ended mid-gesture never falls through to a drag).
                            val pointer = pressed[0]
                            val dX = if (fitWidth > 0f) {
                                (pointer.position.x - startPosition.x) / fitWidth
                            } else 0f
                            val dY = if (fitHeight > 0f) {
                                (pointer.position.y - startPosition.y) / fitHeight
                            } else 0f
                            val newRect = dragCropRect(
                                initial = initialRect,
                                mode = dragMode,
                                dx = dX,
                                dy = dY,
                                ratio = ratioTarget
                            )
                            cropRect = newRect
                            onCropChanged(newRect)
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    // One-shot commit at the end of an adjustment gesture: the
                    // crop was dragged/resized/pinched from its last committed
                    // state → record undo history + mark the shared geometry
                    // stale exactly once per gesture. A bare tap/window change
                    // (crop untouched) stays silent.
                    if (cropRect != lastCommittedCrop) {
                        lastCommittedCrop = cropRect
                        onCropApplied()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Display the crop rect scaled to the letterboxed image rect.
            val displayCrop = Rect(
                left = fitOffsetX + cropRect.left * fitWidth,
                top = fitOffsetY + cropRect.top * fitHeight,
                right = fitOffsetX + cropRect.right * fitWidth,
                bottom = fitOffsetY + cropRect.bottom * fitHeight
            )
            // Dark overlay outside crop area
            val path = Path().apply {
                addRect(Rect(Offset.Zero, size))
                addRect(displayCrop)
            }
            drawPath(
                path = path,
                color = Color.Black.copy(alpha = 0.5f)
            )

            // Crop border
            drawRect(
                topLeft = displayCrop.topLeft,
                size = displayCrop.size,
                color = Color.White,
                style = Stroke(width = 2f)
            )

            // Corner handles
            val handleSize = 20f
            val handles = listOf(
                Offset(displayCrop.left, displayCrop.top),
                Offset(displayCrop.right, displayCrop.top),
                Offset(displayCrop.left, displayCrop.bottom),
                Offset(displayCrop.right, displayCrop.bottom)
            )

            handles.forEach { handle ->
                drawRect(
                    color = Color.White,
                    topLeft = Offset(handle.x - handleSize / 2, handle.y - handleSize / 2),
                    size = Size(handleSize, handleSize)
                )
            }

            // Grid lines (rule of thirds)
            val thirdWidth = displayCrop.width / 3
            val thirdHeight = displayCrop.height / 3

            for (i in 1..2) {
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(displayCrop.left + thirdWidth * i, displayCrop.top),
                    end = Offset(displayCrop.left + thirdWidth * i, displayCrop.bottom),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(displayCrop.left, displayCrop.top + thirdHeight * i),
                    end = Offset(displayCrop.right, displayCrop.top + thirdHeight * i),
                    strokeWidth = 1f
                )
            }
        }
    }
}