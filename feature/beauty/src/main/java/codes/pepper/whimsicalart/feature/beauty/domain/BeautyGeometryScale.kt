package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult
import codes.pepper.whimsicalart.feature.beauty.detection.FaceLandmarks
import kotlin.math.roundToInt

/**
 * Axis rescale helpers for [BeautyGeometry]'s pixel-space fields. Shared by the
 * resolver (which scales its ML result back to the base it was handed) and the
 * editor's geometry-track cap (which rescales a geometry resolved against a
 * 1–4 MP capped base up to the full-size colour fold it renders over).
 * All coordinates stay in the image-pixel space of the bitmap they are applied
 * to — these helpers only change which pixel space that is.
 */
internal fun FaceDetectionResult.scaled(sx: Float, sy: Float): FaceDetectionResult {
    if (sx == 1f && sy == 1f) return this
    return FaceDetectionResult(
        faces = faces.map { face ->
            face.copy(
                bounds = Rect(
                    (face.bounds.left * sx).roundToInt(),
                    (face.bounds.top * sy).roundToInt(),
                    (face.bounds.right * sx).roundToInt(),
                    (face.bounds.bottom * sy).roundToInt()
                ),
                landmarks = face.landmarks.scaled(sx, sy)
            )
        },
        imageWidth = (imageWidth * sx).roundToInt(),
        imageHeight = (imageHeight * sy).roundToInt()
    )
}

internal fun FaceLandmarks.scaled(sx: Float, sy: Float): FaceLandmarks = FaceLandmarks(
    leftEye = leftEye?.scaled(sx, sy),
    rightEye = rightEye?.scaled(sx, sy),
    nose = nose?.scaled(sx, sy),
    mouthLeft = mouthLeft?.scaled(sx, sy),
    mouthRight = mouthRight?.scaled(sx, sy),
    leftCheek = leftCheek?.scaled(sx, sy),
    rightCheek = rightCheek?.scaled(sx, sy),
    faceContour = faceContour.scaled(sx, sy),
    leftEyeContour = leftEyeContour.scaled(sx, sy),
    rightEyeContour = rightEyeContour.scaled(sx, sy),
    leftEyebrowContour = leftEyebrowContour.scaled(sx, sy),
    rightEyebrowContour = rightEyebrowContour.scaled(sx, sy),
    lipsContour = lipsContour.scaled(sx, sy),
    mouthApertureContour = mouthApertureContour.scaled(sx, sy)
)

internal fun PointF.scaled(sx: Float, sy: Float): PointF = PointF(x * sx, y * sy)

internal fun List<PointF>.scaled(sx: Float, sy: Float): List<PointF> =
    if (sx == 1f && sy == 1f) this else map { it.scaled(sx, sy) }

internal fun Path.scaled(sx: Float, sy: Float): Path {
    if (sx == 1f && sy == 1f) return this
    val m = Matrix().apply { setScale(sx, sy) }
    return Path().apply { transform(m) }
}

internal fun Bitmap.scaledTo(targetWidth: Int, targetHeight: Int): Bitmap {
    if (width == targetWidth && height == targetHeight) return this
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}