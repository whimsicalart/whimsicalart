package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF

/**
 * A single recognized text line in source bitmap coordinates. Pure data;
 * produced by an on-device OCR engine. A rotation-aware engine (PP-OCR via MNN)
 * additionally reports the tilted region as a [quad] and its baseline angle, so
 * imported text can reconstruct the source line's tilt and ink colour; the ML
 * Kit path fills only [boundingBox] with rotation 0 and no quad.
 */
data class OcrLine(
    val text: String,
    val boundingBox: RectF,
    val rotationDegrees: Float = 0f,
    val quad: List<PointF>? = null
)

/**
 * On-device text recognition. The concrete implementation wraps ML Kit and can
 * only run on a real device/emulator (native runtime), so it is isolated behind
 * this interface and unit tests target the pure extractor instead.
 */
interface OcrDetector {
    /**
     * Whether this OCR runtime can initialize on the current device/emulator
     * (e.g. native library load for the MNN path, or Play-Services-backed
     * clients). Probed lazily by implementations and meant to be read without
     * eagerly initializing the engine; when `false` the UI hides the
     * Recognize Text action so the rest of the text tool stays usable.
     */
    val isAvailable: Boolean

    suspend fun recognize(bitmap: Bitmap): List<OcrLine>
}