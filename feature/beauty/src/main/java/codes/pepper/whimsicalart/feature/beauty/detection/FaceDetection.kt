package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.PointF
import android.graphics.Rect

/**
 * The face-geometry result model consumed by the beauty pipeline: bounds,
 * head-pose + expression probabilities and dense contour landmarks per face.
 *
 * Historically this was ML Kit's face-detection output. The app now runs only
 * the MediaPipe Face Landmarker (which tracks 478 points) and the equivalent
 * result is derived from the mesh by
 * [FaceMeshContourMapper.toFaceDetectionResult] — keeping this shape means every
 * downstream consumer (beauty tools, the lite-lens fallback path) is untouched
 * by the runtime swap.
 *
 * Debugging note: `leftEyeOpenProbability` / `rightEyeOpenProbability` /
 * `smilingProbability` / `eulerY` / `eulerZ` are derived approximations and no
 * production code reads them today; they are populated so the model stays
 * complete. Test suites must run under Robolectric because the model uses
 * [android.graphics] shapes whose fields are zeroed by the mockable Android jar
 * (a symptom: [bounds] collapse to an empty rect and `PointF`s to `[0,0]`).
 */
data class FaceDetectionResult(
    val faces: List<DetectedFace>,
    val imageWidth: Int,
    val imageHeight: Int
) {
    companion object {
        /** A fishless result — used for a graceful early-return when the input is unusable. */
        fun empty(): FaceDetectionResult = FaceDetectionResult(emptyList(), 0, 0)
    }
}

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
    val lipsContour: List<PointF> = emptyList(),
    val mouthApertureContour: List<PointF> = emptyList()
)