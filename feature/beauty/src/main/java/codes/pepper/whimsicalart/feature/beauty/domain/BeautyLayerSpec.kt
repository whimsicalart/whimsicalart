package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Path

/**
 * A serializable snapshot of one beauty sub-tool's active parameters, ready to
 * be mapped into a [StackEffect]-compatible layer by the editor. The [toolKey]
 * uniquely identifies the sub-tool and matches the corresponding
 * `BeautyStackEffect.layerKey` prefix in the editor module — e.g.
 * `"beauty:smoothing"`, `"beauty:lipstick"`, etc.
 *
 * Geometry is NOT carried on the spec: the editor maps each spec into a
 * [StackEffect] layer and the shared geometry context lazily resolves each
 * effect's geometry at fold time against the image at its fold position.
 */
data class BeautyLayerSpec(
    /** Discriminator: matches the BeautyStackEffect.layerKey, e.g. "beauty:auto". */
    val toolKey: String,
    /** Primary intensity. 0 means off. Value range is tool-specific: 0..1 for
     *  most tools; -1..1 for skin-tone and the nose/jaw reshape dims. */
    val intensity: Float = 0f,
    /** Color for makeup tools (lipstick, blush, eye shadow, etc.). */
    val color: Int = 0,
    /** Pen strokes for the brightness pen. */
    val strokes: List<BrushStroke> = emptyList(),
    /** Pen brush opacity. */
    val opacity: Float = 1f
) {
    /** True when this spec carries no meaningful data (intensity 0 and no strokes). */
    val isActive: Boolean
        get() = intensity != 0f || strokes.isNotEmpty()
}
