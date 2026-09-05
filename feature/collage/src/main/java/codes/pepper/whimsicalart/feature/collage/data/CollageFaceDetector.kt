package codes.pepper.whimsicalart.feature.collage.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Detects a single "face crop focus" for a photo so the collage renderer can drift
 * its cover crop toward the subject. Reuses ML Kit's face detection (the same
 * engine the beauty module uses) and returns the fractional centre of the largest
 * detected face, or null when no face is found (the renderer then centre-crops).
 */
@Singleton
class CollageFaceDetector @Inject constructor() {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(options)

    /** @return the fractional face centre of the largest face, or null if none. */
    suspend fun detectFaceCenter(
        resolver: ContentResolver,
        uri: Uri
    ): PointF? {
        val bitmap = decode(resolver, uri) ?: return null
        return try {
            val face = largestFace(bitmap)
            face?.let {
                PointF(it.exactCenterX() / bitmap.width, it.exactCenterY() / bitmap.height)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? = try {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    private suspend fun largestFace(bitmap: Bitmap): android.graphics.Rect? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            detector.process(image)
                .addOnSuccessListener { faces ->
                    // Prefer the largest face as the composition focus.
                    val largest = faces.maxByOrNull { f -> f.boundingBox.width() * f.boundingBox.height() }
                    continuation.resume(largest?.boundingBox)
                }
                .addOnFailureListener { e -> continuation.resumeWithException(e) }
        }
    }

    fun close() {
        detector.close()
    }
}