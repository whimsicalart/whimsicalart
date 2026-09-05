package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit-backed [OcrDetector]. [recognize] blocks on the caller thread (expected:
 * called from Dispatchers.Default / IO). Thin native seam — ML Kit inference does
 * not execute under Robolectric, so this is intentionally not unit-tested; tests
 * target the pure [OcrTextExtractor].
 */
@Singleton
class MlKitOcrDetector @Inject constructor() : OcrDetector {

    // Created lazily so merely constructing the detector (e.g. in unit tests that
    // exercise the ViewModel) never initializes the ML Kit client.
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val pending = AtomicInteger(0)

    override suspend fun recognize(bitmap: Bitmap): List<OcrLine> {
        if (pending.get() > 0) return emptyList() // avoid concurrent calls on same recognizer
        pending.incrementAndGet()
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(recognizer.process(image))
            visionText.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrLine(
                        text = line.text,
                        boundingBox = RectF(
                            box.left.toFloat(), box.top.toFloat(),
                            box.right.toFloat(), box.bottom.toFloat()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            pending.decrementAndGet()
        }
    }
}