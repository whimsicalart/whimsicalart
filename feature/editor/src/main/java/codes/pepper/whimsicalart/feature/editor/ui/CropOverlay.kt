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

/** Minimum normalized size of the crop frame on any side. */
internal const val NORMALIZED_MIN_SIZE = 0.05f

/** How close (display px) a touch must be to a frame edge/corner to resize it. */
private const val HANDLE_TOUCH_TOLERANCE = 28f

/**
 * Fraction of the crop frame's own dimension that counts as a resize zone
 * *inside* the frame. A touch up to this far inside an edge resizes on that axis
 * (instead of moving the frame); the corner regions (both axes inside) resize on
 * both axes.
 */
private const val RESIZE_INSIDE_FRACTION = 0.2f

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ONE_ONE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_TWO("3:2", 3f / 2f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    NINE_SIXTEEN("9:16", 9f / 16f)
}

/** Which part of the crop frame a drag started on. */
internal enum class CropDragMode {
    MOVE,
    RESIZE_LEFT,
    RESIZE_RIGHT,
    RESIZE_TOP,
    RESIZE_BOTTOM,
    RESIZE_TOP_LEFT,
    RESIZE_TOP_RIGHT,
    RESIZE_BOTTOM_LEFT,
    RESIZE_BOTTOM_RIGHT
}

/** The letterboxed rectangle (in box coordinates) where [imageSize] is displayed with Fit. */
internal data class FitRect(
    val offset: Offset,
    val size: Size
)

/**
 * Computes how an image of [imageSize] (rotated pixel dimensions) fits inside the
 * [overlaySize] box using ContentScale.Fit: the image is scaled to fit while
 * preserving its aspect ratio and centered, leaving letterbox bars on either side.
 * Falls back to the full box when [imageSize] is not available.
 */
internal fun fitRect(overlaySize: Size, imageSize: Size): FitRect {
    if (imageSize.width <= 0f || imageSize.height <= 0f) {
        return FitRect(Offset.Zero, overlaySize)
    }
    val scale = minOf(
        overlaySize.width / imageSize.width,
        overlaySize.height / imageSize.height
    )
    val w = imageSize.width * scale
    val h = imageSize.height * scale
    return FitRect(
        offset = Offset((overlaySize.width - w) / 2f, (overlaySize.height - h) / 2f),
        size = Size(w, h)
    )
}

/**
 * Determines which part of the crop frame a touch at [touch] (box coordinates)
 * landed on. Corners take priority over edges, edges over the interior.
 * Resize is triggered not only on the exact border but also up to ~20% inside
 * the frame (so an edge-banded drag resizes instead of moving); the 20% corner
 * regions resize on both axes and the remaining edge band on a single axis.
 * Returns null when the touch is outside the frame (letterbox bars), in which
 * case the drag should be ignored.
 */
internal fun hitTestCrop(
    crop: Rect,
    touch: Offset,
    tolerance: Float = HANDLE_TOUCH_TOLERANCE
): CropDragMode? {
    // A touch lands on an edge when it is within [tolerance] outside the frame
    // OR up to 20% of that dimension inside the frame.
    val insideX = crop.width * RESIZE_INSIDE_FRACTION
    val insideY = crop.height * RESIZE_INSIDE_FRACTION
    val reachX = maxOf(tolerance, insideX)
    val reachY = maxOf(tolerance, insideY)

    val nearLeft = touch.x >= crop.left - tolerance && touch.x <= crop.left + reachX
    val nearRight = touch.x <= crop.right + tolerance && touch.x >= crop.right - reachX
    val nearTop = touch.y >= crop.top - tolerance && touch.y <= crop.top + reachY
    val nearBottom = touch.y <= crop.bottom + tolerance && touch.y >= crop.bottom - reachY
    return when {
        nearLeft && nearTop -> CropDragMode.RESIZE_TOP_LEFT
        nearRight && nearTop -> CropDragMode.RESIZE_TOP_RIGHT
        nearLeft && nearBottom -> CropDragMode.RESIZE_BOTTOM_LEFT
        nearRight && nearBottom -> CropDragMode.RESIZE_BOTTOM_RIGHT
        nearLeft -> CropDragMode.RESIZE_LEFT
        nearRight -> CropDragMode.RESIZE_RIGHT
        nearTop -> CropDragMode.RESIZE_TOP
        nearBottom -> CropDragMode.RESIZE_BOTTOM
        touch.x >= crop.left && touch.x <= crop.right &&
            touch.y >= crop.top && touch.y <= crop.bottom -> CropDragMode.MOVE
        else -> null
    }
}

/**
 * Computes the normalized crop rect after dragging [dx]/[dy] (in normalized units)
 * given the drag started with mode [mode] on [initial] (normalized 0..1). When
 * [ratio] is non-null the result is forced to match the aspect ratio, anchored to
 * the sidf facing the drag so the frame grows/shrinks predictably. A null [mode]
 * (drag outside the frame) leaves [initial] untouched.
 */
internal fun dragCropRect(
    initial: Rect,
    mode: CropDragMode?,
    dx: Float = 0f,
    dy: Float = 0f,
    ratio: Float? = null
): Rect {
    if (mode == null) return initial
    var rect = when (mode) {
        CropDragMode.MOVE -> {
            // Clamp the translation vector so the whole frame stays inside the
            // normalized box, preserving its size.
            val moveX = dx.coerceIn(-initial.left, 1f - initial.right)
            val moveY = dy.coerceIn(-initial.top, 1f - initial.bottom)
            Rect(
                initial.left + moveX,
                initial.top + moveY,
                initial.right + moveX,
                initial.bottom + moveY
            )
        }
        CropDragMode.RESIZE_LEFT -> Rect(
            initial.left + dx,
            initial.top,
            initial.right,
            initial.bottom
        )
        CropDragMode.RESIZE_RIGHT -> Rect(
            initial.left,
            initial.top,
            initial.right + dx,
            initial.bottom
        )
        CropDragMode.RESIZE_TOP -> Rect(
            initial.left,
            initial.top + dy,
            initial.right,
            initial.bottom
        )
        CropDragMode.RESIZE_BOTTOM -> Rect(
            initial.left,
            initial.top,
            initial.right,
            initial.bottom + dy
        )
        CropDragMode.RESIZE_TOP_LEFT -> Rect(
            initial.left + dx,
            initial.top + dy,
            initial.right,
            initial.bottom
        )
        CropDragMode.RESIZE_TOP_RIGHT -> Rect(
            initial.left,
            initial.top + dy,
            initial.right + dx,
            initial.bottom
        )
        CropDragMode.RESIZE_BOTTOM_LEFT -> Rect(
            initial.left + dx,
            initial.top,
            initial.right,
            initial.bottom + dy
        )
        CropDragMode.RESIZE_BOTTOM_RIGHT -> Rect(
            initial.left,
            initial.top,
            initial.right + dx,
            initial.bottom + dy
        )
    }
    if (ratio != null && mode != CropDragMode.MOVE) {
        rect = applyAspectRatio(rect, mode, ratio)
    }
    return clampCropRect(rect)
}

private fun applyAspectRatio(rect: Rect, mode: CropDragMode, ratio: Float): Rect {
    return when (mode) {
        CropDragMode.RESIZE_LEFT, CropDragMode.RESIZE_RIGHT -> {
            val height = rect.height
            val width = height * ratio
            if (mode == CropDragMode.RESIZE_LEFT) {
                Rect(rect.right - width, rect.top, rect.right, rect.bottom)
            } else {
                Rect(rect.left, rect.top, rect.left + width, rect.bottom)
            }
        }
        CropDragMode.RESIZE_TOP, CropDragMode.RESIZE_BOTTOM -> {
            val width = rect.width
            val height = width / ratio
            if (mode == CropDragMode.RESIZE_TOP) {
                Rect(rect.left, rect.bottom - height, rect.right, rect.bottom)
            } else {
                Rect(rect.left, rect.top, rect.right, rect.top + height)
            }
        }
        CropDragMode.RESIZE_TOP_LEFT,
        CropDragMode.RESIZE_TOP_RIGHT,
        CropDragMode.RESIZE_BOTTOM_LEFT,
        CropDragMode.RESIZE_BOTTOM_RIGHT -> cornerWithRatio(rect, mode, ratio)
        else -> rect
    }
}

private fun cornerWithRatio(rect: Rect, mode: CropDragMode, ratio: Float): Rect {
    // The corner opposite the dragged one stays fixed.
    val anchorX = when (mode) {
        CropDragMode.RESIZE_TOP_LEFT, CropDragMode.RESIZE_BOTTOM_LEFT -> rect.right
        else -> rect.left
    }
    val anchorY = when (mode) {
        CropDragMode.RESIZE_TOP_LEFT, CropDragMode.RESIZE_TOP_RIGHT -> rect.bottom
        else -> rect.top
    }
    val growLeft = mode == CropDragMode.RESIZE_TOP_LEFT || mode == CropDragMode.RESIZE_BOTTOM_LEFT
    val growUp = mode == CropDragMode.RESIZE_TOP_LEFT || mode == CropDragMode.RESIZE_TOP_RIGHT

    fun build(width: Float, height: Float): Rect = Rect(
        left = if (growLeft) anchorX - width else anchorX,
        top = if (growUp) anchorY - height else anchorY,
        right = if (growLeft) anchorX else anchorX + width,
        bottom = if (growUp) anchorY else anchorY + height
    )

    // Prefer driving the dimension the user dragged; fall back to the other axis
    // when that candidate would leave the normalized bounds.
    val widthDriven = maxOf(rect.width, NORMALIZED_MIN_SIZE)
    val heightFromWidth = widthDriven / ratio
    val fromWidth = build(widthDriven, heightFromWidth)
    if (fromWidth.left >= 0f && fromWidth.top >= 0f &&
        fromWidth.right <= 1f && fromWidth.bottom <= 1f
    ) {
        return fromWidth
    }
    val heightDriven = maxOf(rect.height, NORMALIZED_MIN_SIZE)
    return build(heightDriven * ratio, heightDriven)
}

private fun clampCropRect(rect: Rect): Rect {
    val min = NORMALIZED_MIN_SIZE
    val left = rect.left.coerceIn(0f, 1f - min)
    val top = rect.top.coerceIn(0f, 1f - min)
    val right = rect.right.coerceIn(minOf(left + min, 1f), 1f)
    val bottom = rect.bottom.coerceIn(minOf(top + min, 1f), 1f)
    return Rect(left, top, right, bottom)
}

/**
 * Scales the crop [rect] uniformly around its center by [scale] (normalized
 * units), then clamps it to the normalized box and the minimum size. When
 * [ratio] is non-null the scaled rect is re-fit to that aspect ratio (kept
 * centered) so pinching a fixed-ratio crop preserves the ratio. Returns a new
 * [Rect] — the input is left untouched.
 */
internal fun scaleCropRect(rect: Rect, scale: Float, ratio: Float? = null): Rect {
    val centerX = rect.center.x
    val centerY = rect.center.y
    val newWidth = rect.width * scale
    val newHeight = rect.height * scale
    var left = centerX - newWidth / 2f
    var top = centerY - newHeight / 2f
    var right = left + newWidth
    var bottom = top + newHeight
    if (ratio != null) {
        val curRatio = newWidth / newHeight
        var w = newWidth
        var h = newHeight
        if (curRatio > ratio) {
            w = h * ratio
        } else {
            h = w / ratio
        }
        left = centerX - w / 2f
        top = centerY - h / 2f
        right = left + w
        bottom = top + h
    }
    return clampCropRect(Rect(left, top, right, bottom))
}

@Composable
fun CropOverlay(
    imageSize: Size,
    aspectRatio: CropAspectRatio,
    initialCrop: Rect = Rect(0f, 0f, 1f, 1f),
    onCropChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    var overlaySize by remember { mutableStateOf(Size.Zero) }
    var cropRect by remember(initialCrop) { mutableStateOf(initialCrop) }

    // When the user switches aspect ratio (or toggles Free), reshape the crop
    // frame to the new ratio — centred and clipped to the current bounds — so
    // the ratio buttons visibly take effect instead of leaving the frame as-is.
    LaunchedEffect(aspectRatio) {
        val ratio = aspectRatio.ratio ?: return@LaunchedEffect
        val curWidth = cropRect.width
        val curHeight = cropRect.height
        if (curWidth <= 0f || curHeight <= 0f) return@LaunchedEffect
        val targetRatio = if (curWidth >= curHeight) ratio else 1f / ratio
        var width = curWidth
        var height = width / targetRatio
        if (height > curHeight) {
            height = curHeight
            width = height * targetRatio
        }
        val left = cropRect.left + (cropRect.width - width) / 2f
        val top = cropRect.top + (cropRect.height - height) / 2f
        val reshaped = clampCropRect(Rect(left, top, left + width, top + height))
        cropRect = reshaped
        onCropChanged(reshaped)
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
                aspectRatio, fitWidth, fitHeight, fitOffsetX, fitOffsetY
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
                                    val newRect = scaleCropRect(cropRect, zoom, aspectRatio.ratio)
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
                                ratio = aspectRatio.ratio
                            )
                            cropRect = newRect
                            onCropChanged(newRect)
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
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