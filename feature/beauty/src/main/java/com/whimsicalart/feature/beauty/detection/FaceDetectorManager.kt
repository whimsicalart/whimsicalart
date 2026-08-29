package com.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
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
    val rightCheek: PointF?
)

open class FaceDetectorManager {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
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
            rightCheek = pos(FaceLandmark.RIGHT_CHEEK)
        )
    }

    open fun close() {
        detector.close()
    }
}
