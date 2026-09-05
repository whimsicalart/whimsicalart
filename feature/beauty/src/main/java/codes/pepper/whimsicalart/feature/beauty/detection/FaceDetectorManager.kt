package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FaceDetectionResult(
    val faces: List<DetectedFace>,
    val imageWidth: Int,
    val imageHeight: Int
)

data class DetectedFace(
    val bounds: Rect,
    val leftEyeOpenProbability: Float,
    val rightEyeOpenProbability: Float,
    val smilingProbability: Float,
    val eulerY: Float,
    val eulerZ: Float,
    val landmarks: FaceLandmarks
)

data class FaceLandmarks(
    val leftEye: PointF?,
    val rightEye: PointF?,
    val nose: PointF?,
    val mouthLeft: PointF?,
    val mouthRight: PointF?,
    val leftCheek: PointF?,
    val rightCheek: PointF?,
    val faceContour: List<PointF> = emptyList(),
    val leftEyeContour: List<PointF> = emptyList(),
    val rightEyeContour: List<PointF> = emptyList(),
    val leftEyebrowContour: List<PointF> = emptyList(),
    val rightEyebrowContour: List<PointF> = emptyList(),
    val lipsContour: List<PointF> = emptyList()
)

open class FaceDetectorManager {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val detector: FaceDetector by lazy { FaceDetection.getClient(options) }

    open suspend fun detectFaces(bitmap: Bitmap): FaceDetectionResult {
        val image = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val detectedFaces = faces.map { face ->
                        DetectedFace(
                            bounds = face.boundingBox,
                            leftEyeOpenProbability = face.leftEyeOpenProbability ?: 0f,
                            rightEyeOpenProbability = face.rightEyeOpenProbability ?: 0f,
                            smilingProbability = face.smilingProbability ?: 0f,
                            eulerY = face.headEulerAngleY,
                            eulerZ = face.headEulerAngleZ,
                            landmarks = extractLandmarks(face)
                        )
                    }

                    continuation.resume(
                        FaceDetectionResult(
                            faces = detectedFaces,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height
                        )
                    )
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }

    private fun extractLandmarks(face: Face): FaceLandmarks {
        fun pos(landmarkType: Int): PointF? =
            face.getLandmark(landmarkType)?.position?.let { PointF(it.x.toFloat(), it.y.toFloat()) }
        return FaceLandmarks(
            leftEye = pos(FaceLandmark.LEFT_EYE),
            rightEye = pos(FaceLandmark.RIGHT_EYE),
            nose = pos(FaceLandmark.NOSE_BASE),
            mouthLeft = pos(FaceLandmark.MOUTH_LEFT),
            mouthRight = pos(FaceLandmark.MOUTH_RIGHT),
            leftCheek = pos(FaceLandmark.LEFT_CHEEK),
            rightCheek = pos(FaceLandmark.RIGHT_CHEEK),
            faceContour = contour(face, FaceContour.FACE),
            leftEyeContour = contour(face, FaceContour.LEFT_EYE),
            rightEyeContour = contour(face, FaceContour.RIGHT_EYE),
            leftEyebrowContour = contour(face, FaceContour.LEFT_EYEBROW_TOP),
            rightEyebrowContour = contour(face, FaceContour.RIGHT_EYEBROW_TOP),
            lipsContour = buildLipsContour(face)
        )
    }

    private fun contour(face: Face, contourType: Int): List<PointF> =
        face.getContour(contourType)
            ?.points
            ?.map { point -> PointF(point.x.toFloat(), point.y.toFloat()) }
            ?: emptyList()

    /**
     * Closed outer-mouth outline for lip masks: the upper-lip top edge joined
     * with the lower-lip bottom edge (traversed back-to-front so the result is
     * a single closed loop, matching the reference's per-feature mask contour).
     */
    private fun buildLipsContour(face: Face): List<PointF> {
        val upper = contour(face, FaceContour.UPPER_LIP_TOP)
        val lower = contour(face, FaceContour.LOWER_LIP_BOTTOM).asReversed()
        return if (upper.isNotEmpty() && lower.isNotEmpty()) upper + lower else emptyList()
    }

    open fun close() {
        detector.close()
    }
}
