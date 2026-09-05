package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * A single recognized text line with its bounding box in source bitmap
 * coordinates. Pure data; produced by an on-device OCR engine.
 */
data class OcrLine(
    val text: String,
    val boundingBox: RectF
)

/**
 * On-device text recognition. The concrete implementation wraps ML Kit and can
 * only run on a real device/emulator (native runtime), so it is isolated behind
 * this interface and unit tests target the pure extractor instead.
 */
interface OcrDetector {
    suspend fun recognize(bitmap: Bitmap): List<OcrLine>
}