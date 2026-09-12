package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.sqrt

/**
 * The rotated on-screen ("letterbox anchor") image size the crop overlay must be
 * confined to. [baseSize] is the CURRENT fold base's pixel dimensions
 * ([EditorUiState.baseImageSize]) — the pristine decode before any merge, the
 * flattened merged result (possibly LARGER than the pristine original) after
 * one. Deriving the crop grid from the pristine original instead would clamp a
 * post-merge crop to the OLD image extent instead of spanning the merged base
 * image. Returns [Size.Zero] when no base is loaded.
 */
internal fun cropImageSize(baseSize: Size?, rotationDegrees: Float): Size {
    if (baseSize == null || baseSize.width <= 0f || baseSize.height <= 0f) return Size.Zero
    return rotatedBoundsSize(baseSize.width, baseSize.height, rotationDegrees)
}

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
 * the side facing the drag so the frame grows/shrinks predictably. A null [mode]
 * (drag outside the frame) leaves [initial] untouched. NOTE: [ratio] is the
 * NORMALISED target (see [normalizedAspectRatio]) — pass the on-screen/pixel
 * ratio transformed into normalized space so the frame stays locked at exactly
 * the selected ratio on screen and in the committed pixels.
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
 * [ratio] is non-null (a NORMALISED target, see [normalizedAspectRatio]) the
 * scaled rect is re-fit to that aspect ratio (kept centered) so pinching a
 * fixed-ratio crop preserves the ratio. Returns a new [Rect] — the input is
 * left untouched.
 */
internal fun scaleCropRect(rect: Rect, scale: Float, ratio: Float? = null): Rect {
    val centerX = rect.center.x
    val centerY = rect.center.y
    val newWidth = rect.width * scale
    val newHeight = rect.height * scale
    if (ratio == null) {
        var left = centerX - newWidth / 2f
        var top = centerY - newHeight / 2f
        return clampCropRect(Rect(left, top, left + newWidth, top + newHeight))
    }
    // Preserve the ratio with the same conservative rule as before: keep the axis
    // that gives the milder constraint and reduce the OTHER one so the result is
    // never larger than the scaled rect.
    var w = newWidth
    var h = newHeight
    val curRatio = newWidth / newHeight
    if (curRatio > ratio) {
        h = newHeight
        w = h * ratio
    } else {
        w = newWidth
        h = w / ratio
    }
    // Clamp to the normalized box WITHOUT breaking the ratio: when an axis would
    // overflow, cap the LARGER one at the boundary and derive the other from it
    // so the frame keeps exactly [ratio] even at the image edges.
    if (w > 1f || h > 1f) {
        if (ratio >= 1f) {
            w = 1f
            h = w / ratio
        } else {
            h = 1f
            w = h * ratio
        }
    }
    val left = (centerX - w / 2f).coerceIn(0f, 1f - w)
    val top = (centerY - h / 2f).coerceIn(0f, 1f - h)
    return Rect(left, top, left + w, top + h)
}

/**
 * Maps an on-screen / final-pixel target aspect [ratio] (e.g. 1f for 1:1, 4f/3f
 * for 4:3) into the aspect the crop frame must have in NORMALISED image
 * coordinates so the frame BOTH displays at exactly [ratio] on the letterboxed
 * canvas AND commits to exactly [ratio] in pixels. The image is displayed with
 * ContentScale.Fit, so a normalised frame of aspect `r` spans `r·W` display
 * pixels wide per `H` pixels tall and crops to `(r·W)×(H·H)` — a normalised
 * width:height of `ratio·(H/W)` shows as `ratio` (and produces a `ratio` pixel
 * crop). Falls back to [ratio] verbatim when [imageSize] is unknown.
 */
internal fun normalizedAspectRatio(ratio: Float, imageSize: Size): Float {
    if (imageSize.width <= 0f || imageSize.height <= 0f) return ratio
    return ratio * imageSize.height / imageSize.width
}

/**
 * Reshapes [crop] to the fixed aspect [ratio] (a NORMALISED target — the raw
 * on-screen ratio run through [normalizedAspectRatio]) keeping the frame's
 * AREA constant, centred and clipped to the normalized box. Preserving the
 * area (instead of fitting the largest [ratio] frame inside [crop]) is what
 * stops the shrink-accumulation: alternating two fixed ratios keeps the same
 * apparent scale — each switch only reshapes to the new ratio at the previous
 * frame's size, shrinking/growing only when the new ratio cannot fit the box.
 * Used when a ratio button is selected/free is released so the frame takes the
 * selected ratio EXACTLY (1:1 → square, 4:3 → exactly 4:3, …).
 */
internal fun reshapeCropToRatio(crop: Rect, ratio: Float): Rect {
    val area = crop.width * crop.height
    if (area <= 0f) return crop
    var width = sqrt(area * ratio)
    var height = width / ratio
    // Clamp to the normalized box WITHOUT breaking the ratio: when an axis would
    // overflow, cap the LARGER one at the boundary and derive the other from it
    // so the frame keeps exactly [ratio] even at the image edges. A first
    // selection from the full image has area == 1, so this reproduces the
    // largest-fit frame of exactly today's behaviour.
    if (width > 1f || height > 1f) {
        if (ratio >= 1f) {
            width = 1f
            height = width / ratio
        } else {
            height = 1f
            width = height * ratio
        }
    }
    val left = crop.left + (crop.width - width) / 2f
    val top = crop.top + (crop.height - height) / 2f
    return clampCropRect(Rect(left, top, left + width, top + height))
}