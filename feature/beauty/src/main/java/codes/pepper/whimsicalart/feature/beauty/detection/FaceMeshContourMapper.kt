package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.PointF
import android.graphics.Rect
import kotlin.math.atan2

/**
 * Pure, JVM-testable mapping between the MediaPipe Face Mesh's 478 normalized
 * landmarks and the [FaceLandmarks] / [FaceDetectionResult] models used by the
 * beauty tools.
 *
 * MediaPipe Face Mesh orders eye/brow groups by the SUBJECT's left/right (index
 * 33..246 is the subject's right eye, which appears on the image LEFT when the
 * subject faces the camera). The swapped groups below therefore land in the
 * image-space fields the legacy ML Kit result carried (LEFT_EYE = subject's left
 * eye = image right).
 *
 * The Face Landmarker performs face detection internally, so the whole
 * [FaceDetectionResult] (bounds, anchors, dense contours, head-pose proxies) is
 * derived from the mesh here — the app no longer runs a separate face detector.
 * Keeping this mapping independent of any ML runtime means it can be unit-tested
 * on the JVM (Robolectric), which the native Face Landmarker itself cannot.
 */
object FaceMeshContourMapper {

    /** Expected landmark count for the bundled face_landmarker task (478). */
    const val LANDMARK_COUNT = 478

    // MediaPipe Face Mesh index groups (subject-relative eye/brow naming).
    private val LEFT_EYE = intArrayOf(
        362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398
    )
    private val RIGHT_EYE = intArrayOf(
        33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246
    )
    private val LEFT_EYEBROW = intArrayOf(336, 296, 334, 293, 300, 276, 283, 282, 295, 285)
    private val RIGHT_EYEBROW = intArrayOf(70, 63, 105, 66, 107, 55, 65, 52, 53, 46)
    private val FACE_OVAL = intArrayOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378,
        400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21,
        54, 103, 67, 109
    )
    private val LIPS_OUTER = intArrayOf(
        61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0,
        37, 39, 40, 185
    )

    // The mouth opening (the aperture where the teeth show) is the inner-lip
    // ring: the two MediaPipe groups `lipsUpperInner` + `lipsLowerInner` which
    // together close the loop the outer ring wraps around. Feeding the whole
    // ring to the teeth aperture keeps whitening confined to the actual opening
    // instead of the outer-lip lens.
    private val LIPS_UPPER_INNER = intArrayOf(
        78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308
    )
    private val LIPS_LOWER_INNER = intArrayOf(
        324, 318, 402, 317, 14, 87, 178, 88, 95
    )

    // Lips outer corners (index 61 is the subject's right corner = image left;
    // 291 the subject's left corner = image right). ML Kit's mouthLeft/mouthRight
    // follow the image x-axis, so 61 -> mouthLeft, 291 -> mouthRight.
    private const val LIP_CORNER_IMAGE_LEFT = 61
    private const val LIP_CORNER_IMAGE_RIGHT = 291
    // Nose anchor: MediaPipe's NOSE_BRIDGE (168) sits BETWEEN the eyes, so the
    // nose machinery (BeautyProcessor.applyNose's reshape centre) acted on the
    // inter-eye area. The anchor is the nose TIP (1) — the lower end of the
    // nose body (bridge -> tip) — clearly below the eye line, matching what ML
    // Kit's NOSE_BASE already means for the non-mesh path.
    private const val NOSE_TIP = 1

    // Blendshape keys (Face Landmarker emits camelCase; stored lowercased above).
    private const val BLENDSHAPE_BLINK_LEFT = "eyeblinkleft"
    private const val BLENDSHAPE_BLINK_RIGHT = "eyeblinkright"
    private const val BLENDSHAPE_SMILE_LEFT = "mouthsmileleft"
    private const val BLENDSHAPE_SMILE_RIGHT = "mouthsmileright"
    // Eye corners on the 478-mesh (subject's right 33 = image left; subject's
    // left 263 = image right) - the eye-line slope proxies eulerZ (roll).
    private const val EYE_CORNER_SUBJECT_RIGHT = 33
    private const val EYE_CORNER_SUBJECT_LEFT = 263
    // Yaw proxy: horizontal offset of the nose tip from the bounds centre,
    // saturated to [-1, 1] and scaled to degrees.
    private const val YAW_MAX_DEGREES = 45f

    /** Scales a list of normalized `[0,1]` landmarks into image pixel coordinates. */
    fun toImagePoints(
        normalized: List<PointF>,
        imageWidth: Int,
        imageHeight: Int
    ): List<PointF> = normalized.map { p ->
        PointF(p.x * imageWidth, p.y * imageHeight)
    }

    /** Axis-aligned bounding box of a point cloud; empty rect when it is empty. */
    fun boundsOf(points: List<PointF>): Rect {
        if (points.isEmpty()) return Rect(0, 0, 0, 0)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return Rect(
            minX.toInt(), minY.toInt(),
            Math.ceil(maxX.toDouble()).toInt(), Math.ceil(maxY.toDouble()).toInt()
        )
    }

    /**
     * Groups a 478-point Mesh (image pixels) into a [FaceLandmarks], reusing the
     * same field semantics ML Kit populates. Anchors (eye/nose/mouth) come from
     * the corresponding group's first point; cheeks are not part of Face Mesh and
     * are left null (only blush reads cheeks, and it degrades to the face
     * bounding box fallback when they are absent).
     */
    fun toContours(points: List<PointF>): FaceLandmarks {
        fun group(indices: IntArray): List<PointF> {
            val result = ArrayList<PointF>(indices.size)
            for (i in indices) points.getOrNull(i)?.let { result.add(it) }
            return result
        }

        val leftEye = group(LEFT_EYE)
        val rightEye = group(RIGHT_EYE)
        val lips = group(LIPS_OUTER)
        val mouthAperture = group(LIPS_UPPER_INNER) + group(LIPS_LOWER_INNER)

        return FaceLandmarks(
            leftEye = leftEye.firstOrNull(),
            rightEye = rightEye.firstOrNull(),
            nose = points.getOrNull(NOSE_TIP),
            mouthLeft = points.getOrNull(LIP_CORNER_IMAGE_LEFT),
            mouthRight = points.getOrNull(LIP_CORNER_IMAGE_RIGHT),
            leftCheek = null,
            rightCheek = null,
            faceContour = group(FACE_OVAL),
            leftEyeContour = leftEye,
            rightEyeContour = rightEye,
            leftEyebrowContour = group(LEFT_EYEBROW),
            rightEyebrowContour = group(RIGHT_EYEBROW),
            lipsContour = lips,
            mouthApertureContour = mouthAperture
        )
    }

    /**
     * Derives the full [FaceDetectionResult] (the legacy ML Kit face-detection
     * shape) from one or more Face Meshes. The Face Landmarker already detects
     * faces, so bounds/anchors/dense contours come straight from the mesh; the
     * head-pose + eye-open/smile probabilities are geometric approximations built
     * from the 478-mesh blendshapes — [FaceDetectionResult] consumers never read
     * those probabilities in production today, so the approximation is safe.
     */
    fun toFaceDetectionResult(
        meshFaces: List<FaceMeshFace>,
        imageWidth: Int,
        imageHeight: Int
    ): FaceDetectionResult {
        val faces = meshFaces.map { mesh ->
            DetectedFace(
                bounds = mesh.bounds,
                leftEyeOpenProbability = openProbability(mesh, BLENDSHAPE_BLINK_LEFT),
                rightEyeOpenProbability = openProbability(mesh, BLENDSHAPE_BLINK_RIGHT),
                smilingProbability = smileProbability(mesh),
                eulerY = yawDegrees(mesh),
                eulerZ = rollDegrees(mesh),
                landmarks = toContours(mesh.points)
            )
        }
        return FaceDetectionResult(faces = faces, imageWidth = imageWidth, imageHeight = imageHeight)
    }

    private fun openProbability(mesh: FaceMeshFace, blinkKey: String): Float =
        (1f - (mesh.blendshapes[blinkKey] ?: 0f)).coerceIn(0f, 1f)

    private fun smileProbability(mesh: FaceMeshFace): Float =
        maxOf(mesh.blendshapes[BLENDSHAPE_SMILE_LEFT] ?: 0f, mesh.blendshapes[BLENDSHAPE_SMILE_RIGHT] ?: 0f)

    /** Roll (eulerZ) approximation: the eye-line angle in degrees. */
    private fun rollDegrees(mesh: FaceMeshFace): Float {
        val left = mesh.points.getOrNull(EYE_CORNER_SUBJECT_RIGHT) ?: return 0f
        val right = mesh.points.getOrNull(EYE_CORNER_SUBJECT_LEFT) ?: return 0f
        return Math.toDegrees(atan2((right.y - left.y).toDouble(), (right.x - left.x).toDouble())).toFloat()
    }

    /** Yaw (eulerY) approximation: signed nose-tip offset from the bounds centre. */
    private fun yawDegrees(mesh: FaceMeshFace): Float {
        val nose = mesh.points.getOrNull(NOSE_TIP) ?: return 0f
        val halfWidth = (mesh.bounds.width() / 2f).coerceAtLeast(1f)
        val normalizedOffset = ((nose.x - mesh.bounds.exactCenterX()) / halfWidth).coerceIn(-1f, 1f)
        return normalizedOffset * YAW_MAX_DEGREES
    }
}