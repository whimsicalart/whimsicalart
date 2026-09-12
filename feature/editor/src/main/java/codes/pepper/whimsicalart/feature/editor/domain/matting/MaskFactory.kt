package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Pure, testable helpers for turning a low-resolution person-confidence tensor
 * into a source-sized subject mask. Keeping this independent of any ML runtime
 * means the alpha mask it produces can be unit-tested on the JVM (Robolectric),
 * which the model inference itself cannot.
 */
object MaskFactory {

    /**
     * Builds a [width] x [height] ARGB_8888 bitmap whose alpha channel encodes
     * the person confidence of [confidence] (a row-major, tightly packed array
     * of `width * height` floats in `[0, 1]`). RGB is set to white so the mask
     * can be up-scaled with a normal bilinear filter. Returns a fully transparent
     * mask when [confidence] is the wrong size.
     */
    fun fromConfidence(
        confidence: FloatArray,
        width: Int,
        height: Int,
        scale: Float = 255f
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (confidence.size < width * height) {
            mask.eraseColor(Color.TRANSPARENT)
            return mask
        }
        // One batched write-out instead of one setPixel per mask pixel (up to
        // ~66k native calls per 257x257 selfie-matting decode).
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            pixels[i] = Color.argb((confidence[i] * scale).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        mask.setPixels(pixels, 0, width, 0, 0, width, height)
        return mask
    }

    /**
     * Up-scales a small mask to [targetWidth] x [targetHeight] with a bilinear
     * filter, preserving the soft confidence boundary produced by the model.
     * Returns a new bitmap sized to the target.
     */
    fun upscale(
        mask: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        dest: Bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    ): Bitmap {
        val scaled = Bitmap.createScaledBitmap(mask, targetWidth, targetHeight, true)
        val canvas = android.graphics.Canvas(dest)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        if (scaled !== mask) scaled.recycle()
        return dest
    }

    /**
     * Thresholds a soft alpha mask to a hard `0 or 255` mask. Used to decide
     * which side of a pixel is background for effects that do not blend.
     */
    fun threshold(mask: Bitmap, level: Int = 128): Bitmap {
        val width = mask.width
        val height = mask.height
        // One batched read + one batched write; the threshold math (alpha
        // lookup per pixel) is unchanged.
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val a = if (Color.alpha(pixels[i]) >= level) 255 else 0
            pixels[i] = Color.argb(a, 255, 255, 255)
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }
}
