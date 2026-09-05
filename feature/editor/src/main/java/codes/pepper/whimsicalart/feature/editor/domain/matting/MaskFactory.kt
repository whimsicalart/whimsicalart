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
        for (y in 0 until height) {
            for (x in 0 until width) {
                val a = (confidence[y * width + x] * scale).toInt().coerceIn(0, 255)
                mask.setPixel(x, y, Color.argb(a, 255, 255, 255))
            }
        }
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
        val out = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                val a = if (Color.alpha(mask.getPixel(x, y)) >= level) 255 else 0
                out.setPixel(x, y, Color.argb(a, 255, 255, 255))
            }
        }
        return out
    }
}
