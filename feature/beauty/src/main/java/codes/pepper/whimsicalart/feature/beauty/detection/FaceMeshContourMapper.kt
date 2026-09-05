package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.PointF
import android.graphics.Rect

/**
 * Pure, JVM-testable mapping between the MediaPipe Face Mesh's 478 normalized
 * landmarks and the [FaceLandmarks] contour set used by the beauty tools
 * (identical data shape to what ML Kit's face detection returns).
 *
 * MediaPipe Face Mesh orders eye/brow groups by the SUBJECT's left/right (index
 * 33..246 is the subject's right eye, which appears on the image LEFT when the
 * subject faces the camera). ML Kit uses continental labels (LEFT_EYE = subject's
 * left eye = image right). The indices below therefore swap the groups so the
 * mapped contours land in the same image-space fields ML Kit populates.
 *
 * Keeping this mapping independent of any ML runtime means the dense-contour
 * enrichment can be unit-tested on the JVM (Robolectric), which the native
 * Face Landmarker itself cannot.
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

    // Lips outer corners (index 61 is the subject's right corner = image left;
    // 291 the subject's left corner = image right). ML Kit's mouthLeft/mouthRight
    // follow the image x-axis, so 61 -> mouthLeft, 291 -> mouthRight.
    private const val LIP_CORNER_IMAGE_LEFT = 61
    private const val LIP_CORNER_IMAGE_RIGHT = 291
    private const val NOSE_BRIDGE = 168

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
     * same field semantics ML Kit populates. Anchors (eye/nose/mouth) come from the
     * corresponding group's first point; cheeks are not part of Face Mesh and are
     * left null (they only drive blush, whose [enrich] keeps ML Kit's values).
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

        return FaceLandmarks(
            leftEye = leftEye.firstOrNull(),
            rightEye = rightEye.firstOrNull(),
            nose = points.getOrNull(NOSE_BRIDGE),
            mouthLeft = points.getOrNull(LIP_CORNER_IMAGE_LEFT),
            mouthRight = points.getOrNull(LIP_CORNER_IMAGE_RIGHT),
            leftCheek = null,
            rightCheek = null,
            faceContour = group(FACE_OVAL),
            leftEyeContour = leftEye,
            rightEyeContour = rightEye,
            leftEyebrowContour = group(LEFT_EYEBROW),
            rightEyebrowContour = group(RIGHT_EYEBROW),
            lipsContour = lips
        )
    }

    /**
     * Merges a Face Mesh-derived dense contour set into the existing ML Kit
     * [FaceLandmarks]. Only the contours Face Mesh actually tracks are replaced
     * (face oval, eye, eyebrow, lips); the anchors Face Mesh cannot place well
     * (cheeks) are kept from [kit] so late-tool behavior (blush, face oval) is
     * unchanged. Each field falls back to [kit] when the Mesh is empty for it.
     */
    fun enrich(kit: FaceLandmarks, mesh: List<PointF>): FaceLandmarks {
        if (mesh.size < LANDMARK_COUNT) return kit
        val meshLandmarks = toContours(mesh)
        return FaceLandmarks(
            leftEye = meshLandmarks.leftEye ?: kit.leftEye,
            rightEye = meshLandmarks.rightEye ?: kit.rightEye,
            nose = meshLandmarks.nose ?: kit.nose,
            mouthLeft = meshLandmarks.mouthLeft ?: kit.mouthLeft,
            mouthRight = meshLandmarks.mouthRight ?: kit.mouthRight,
            leftCheek = kit.leftCheek,
            rightCheek = kit.rightCheek,
            faceContour = meshLandmarks.faceContour.ifEmpty { kit.faceContour },
            leftEyeContour = meshLandmarks.leftEyeContour.ifEmpty { kit.leftEyeContour },
            rightEyeContour = meshLandmarks.rightEyeContour.ifEmpty { kit.rightEyeContour },
            leftEyebrowContour = meshLandmarks.leftEyebrowContour.ifEmpty { kit.leftEyebrowContour },
            rightEyebrowContour = meshLandmarks.rightEyebrowContour.ifEmpty { kit.rightEyebrowContour },
            lipsContour = meshLandmarks.lipsContour.ifEmpty { kit.lipsContour }
        )
    }

    /** Sum of squared distances between two points (cheap ordering metric). */
    fun center(mesh: FaceMeshFace): PointF =
        PointF(mesh.bounds.exactCenterX(), mesh.bounds.exactCenterY())

    /**
     * Picks the Mesh face whose bounds centre is nearest to [kitCenter]. Used to
     * pair ML Kit detections (which carry the probabilities/blendshape-absent
     * metadata + face skin bounds) with a Face Mesh of the same subject even when
     * the two runtimes return faces in a different order.
     */
    fun nearestMesh(kitCenter: PointF, meshFaces: List<FaceMeshFace>): FaceMeshFace? {
        var best: FaceMeshFace? = null
        var bestDist = Float.MAX_VALUE
        for (mesh in meshFaces) {
            val c = center(mesh)
            val dx = c.x - kitCenter.x
            val dy = c.y - kitCenter.y
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = mesh
            }
        }
        return best
    }
}