package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/**
 * Committed-pass renderer for the crop-and-mount steps of [BitmapRenderer]:
 * the rotation/flip transform, the normalized crop window, and the Crop tool's
 * "eye" preview-now helper. Mirrors the live preview's rotation/flip/crop math
 * so the committed render and the preview composition agree. Draw math is
 * unchanged from `BitmapRenderer.transforms`/`crop`/`cropPreview`.
 */
internal object CropMountRenderer {

    /**
     * Produces the actual cropped region (after applying rotation + flips),
     * used by the Crop tool's "eye" preview-now toggle so the user sees the
     * final crop composition instead of the full photo with a grid. Mirrors the
     * rotation/flip/crop steps of [BitmapRenderer.render]. Returns null when
     * nothing is cropped.
     */
    fun cropPreview(
        input: Bitmap,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        cropRect: Rect?
    ): Bitmap? {
        if (cropRect == null) return null
        val oriented = transforms(input, rotationDegrees, flipHorizontal, flipVertical)
        return crop(oriented, cropRect)
    }

    internal fun transforms(
        input: Bitmap,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): Bitmap {
        val normalized = ((rotationDegrees % 360f) + 360f) % 360f
        val quarterTurns = (normalized / 90f).roundToInt() % 4
        val degrees = (quarterTurns * 90).toFloat()
        // True when [normalized] is a (near-)multiple of 90°, in which case the
        // existing cheap path applies — the canvas simply swaps dimensions and the
        // source fills it exactly, with no transparent gutter. For any other
        // (custom/free) angle the canvas must grow to fit the rotated image with a
        // TRANSPARENT background (white is applied only at JPEG export time).
        val remainder = normalized % 90f
        val isExact90 = remainder < 0.5f || remainder > 89.5f

        // Flip must apply BEFORE rotation (Android post-concat left-multiplies:
        // postScale then postRotate yields M = R·S, so a source point is flipped
        // first and rotated after — identical to the live preview, which composes
        // scale before rotation in its graphicsLayer).
        val transform = Matrix().apply {
            postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f)
            postRotate(normalized)
        }

        if (isExact90) {
            return Bitmap.createBitmap(
                input, 0, 0, input.width, input.height, transform, true
            )
        }

        // Custom angle: map the source rect through the matrix to find the rotated
        // bounding box, grow the canvas to exactly fit it (centred), and draw with
        // a filtered paint so the rotation stays smooth. Background is transparent.
        val srcRect = RectF(0f, 0f, input.width.toFloat(), input.height.toFloat())
        val rotatedRect = RectF()
        transform.mapRect(rotatedRect, srcRect)
        val outW = rotatedRect.width().roundToInt().coerceAtLeast(1)
        val outH = rotatedRect.height().roundToInt().coerceAtLeast(1)
        val centered = Matrix(transform)
        centered.postTranslate(-rotatedRect.left, -rotatedRect.top)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { isFilterBitmap = true }
        Canvas(out).drawBitmap(input, centered, paint)
        return out
    }

    internal fun crop(input: Bitmap, cropRect: Rect): Bitmap {
        val left = (cropRect.left * input.width).coerceIn(0f, input.width.toFloat())
        val top = (cropRect.top * input.height).coerceIn(0f, input.height.toFloat())
        val right = (cropRect.right * input.width).coerceIn(left, input.width.toFloat())
        val bottom = (cropRect.bottom * input.height).coerceIn(top, input.height.toFloat())
        var width = (right - left).roundToInt()
        var height = (bottom - top).roundToInt()
        if (width <= 0) width = 1
        if (height <= 0) height = 1
        return Bitmap.createBitmap(
            input,
            left.roundToInt(), top.roundToInt(),
            width, height
        )
    }
}