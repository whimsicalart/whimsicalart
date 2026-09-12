package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect

/**
 * A single face's 478-point Face Mesh, in image pixel coordinates. Kept as raw
 * point cloud + bounds so the [FaceMeshContourMapper] (a pure, JVM-testable
 * helper) can derive the dense eye / brow / lip / face-oval contours that the
 * beauty tools consume, independent of whichever runtime produced them.
 *
 * [blendshapes] carries the Face Landmarker's blendshape scores (keys lowercased:
 * `eyeblinkleft`, `mouthsmileright`, …) when the runtime exposes them — the
 * mapper translates a subset into the eye-open / smile probabilities the
 * [FaceDetectionResult] model carries (which no beauty processor reads today;
 * they are derived so the shared geometry model stays populated).
 */
data class FaceMeshFace(
    val points: List<PointF>,
    val bounds: Rect,
    val blendshapes: Map<String, Float> = emptyMap()
)

/**
 * Produces a dense 478-landmark Face Mesh for every face in [source].
 *
 * This isolates the MediaPipe Tasks (LiteRT) runtime - which loads the bundled
 * `face_landmarker.task` and runs the face-detection -> geometry pipeline
 * natively and therefore CANNOT execute on the JVM under Robolectric - behind a
 * seam, mirroring how [SubjectSegmenter][codes.pepper.whimsicalart.feature.editor.domain.matting.SubjectSegmenter]
 * hides the native selfie segmenter in the editor. The pure contour mapping
 * (and the derivation of the full [FaceDetectionResult] — bounds, landmarks,
 * head pose — from the mesh) lives in [FaceMeshContourMapper] so it is
 * unit-testable.
 *
 * Implementations must return an empty list when detection is unavailable (no
 * model asset, native runtime error, …) so callers degrade gracefully to an
 * empty face result (the beauty tools then simply do nothing).
 */
interface FaceMeshDetector {
    fun detect(source: Bitmap): List<FaceMeshFace>

    /** Releases native resources. No-op by default so fakes are trivially testable. */
    fun close() = Unit
}