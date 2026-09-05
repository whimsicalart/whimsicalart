package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.PointF

data class BrushStroke(
    val points: List<PointF> = emptyList(),
    val size: Float = 30f,
    val opacity: Float = 0.2f
)