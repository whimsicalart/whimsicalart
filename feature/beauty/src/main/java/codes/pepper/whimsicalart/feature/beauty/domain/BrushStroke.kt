package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.PointF

/**
 * A single beauty-pen brush stroke.
 *
 * [points] and [size] are NORMALIZED fractions of the image the stroke was
 * captured against (the source bitmap fitted into the box). [referenceWidth]×
 * [referenceHeight] record those full-size source dims (docs/effect_stack.md →
 * *Positional layers*): every render maps the stroke through the uniform fit of
 * the reference into its destination, so the baked stroke keeps its on-image
 * position and width when an earlier effect changes the fold base. `0×0`
 * (default) keeps the un-anchored identity mapping.
 */
data class BrushStroke(
    val points: List<PointF> = emptyList(),
    val size: Float = 30f,
    val opacity: Float = 0.2f,
    val referenceWidth: Int = 0,
    val referenceHeight: Int = 0
)