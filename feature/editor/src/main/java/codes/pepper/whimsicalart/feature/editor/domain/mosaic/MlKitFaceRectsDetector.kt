package codes.pepper.whimsicalart.feature.editor.domain.mosaic

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit-backed [FaceRectsDetector]. [detectFaces] blocks on the caller thread
 * (expected: called from Dispatchers.Default / IO). Thin native seam — ML Kit
 * inference does not execute under Robolectric, so this is intentionally not
 * unit-tested; tests target the pure [PrivacyMaskBuilder].
 */
@Singleton
class MlKitFaceRectsDetector @Inject constructor() : FaceRectsDetector {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(options)
    private val pending = AtomicInteger(0)

    override fun detectFaces(source: Bitmap): List<Rect> {
        if (pending.get() > 0) return emptyList() // avoid concurrent calls on same detector
        pending.incrementAndGet()
        return try {
            val image = InputImage.fromBitmap(source, 0)
            Tasks.await(detector.process(image)).map { it.boundingBox }
        } catch (e: Exception) {
            emptyList()
        } finally {
            pending.decrementAndGet()
        }
    }

    override fun close() {
        detector.close()
    }
}