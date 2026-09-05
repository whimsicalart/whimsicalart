package codes.pepper.whimsicalart.feature.editor.domain.mosaic

import android.graphics.Rect
import android.graphics.RectF

/**
 * Turns detected face rectangles into normalized privacy-mask regions, expanding
 * each face a little so the surrounding hair/neck is covered too. Pure mapping over
 * [Rect]/[RectF], so it is directly unit-testable under Robolectric without any
 * native face runtime.
 */
object PrivacyMaskBuilder {

    val DEFAULT_MARGIN_RATIO = 0.25f

    /**
     * @param faceRects face bounding boxes in source pixels
     * @param imageWidth source width in px
     * @param imageHeight source height in px
     * @param marginRatio extra region around each face as a fraction of face size
     * @return normalized (0..1) suggested mask regions, one per face
     */
    fun suggestedRegions(
        faceRects: List<Rect>,
        imageWidth: Int,
        imageHeight: Int,
        marginRatio: Float = DEFAULT_MARGIN_RATIO
    ): List<RectF> {
        if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
        return faceRects.mapNotNull { expand(it, imageWidth, imageHeight, marginRatio) }
    }

    private fun expand(
        face: Rect,
        imageWidth: Int,
        imageHeight: Int,
        marginRatio: Float
    ): RectF? {
        if (face.isEmpty) return null
        val marginX = face.width() * marginRatio
        val marginY = face.height() * marginRatio
        val left = (face.left - marginX) / imageWidth
        val top = (face.top - marginY) / imageHeight
        val right = (face.right + marginX) / imageWidth
        val bottom = (face.bottom + marginY) / imageHeight
        return RectF(
            left.coerceIn(0f, 1f),
            top.coerceIn(0f, 1f),
            right.coerceIn(0f, 1f),
            bottom.coerceIn(0f, 1f)
        ).takeUnless { it.width() <= 0f || it.height() <= 0f }
    }
}