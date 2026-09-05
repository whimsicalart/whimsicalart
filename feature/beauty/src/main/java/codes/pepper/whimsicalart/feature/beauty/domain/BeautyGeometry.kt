package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Path
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult

/**
 * The resolved ML geometry a beauty application needs. It is computed once from
 * a given image state (the base photo for the common beauty-first ordering) and
 * its coordinates are expressed in that image's pixel space.
 *
 * A single instance is SHARED across beauty effects that apply to the same
 * image state. Because landmarks/contours are expressed in the image space they
 * were computed on, a DEFORMATION upstream in the fold (crop, H/V flip, rotate,
 * or a geometry-altering beauty op like eye/nose/jaw/face-slim re-shape) changes
 * that pixel space and therefore invalidates the geometry: a new instance must
 * be recomputed against the deformed image for any beauty effect placed after
 * the deformation.
 *
 * [faceResult] carries the landmarks/contours; [skinPath]/[hairPath] are mask
 * outlines in the same image space.
 */
data class BeautyGeometry(
    val faceResult: FaceDetectionResult? = null,
    val skinPath: Path? = null,
    val hairPath: Path? = null
) {
    companion object {
        val NONE = BeautyGeometry()
    }
}
