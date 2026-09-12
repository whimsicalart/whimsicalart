package codes.pepper.whimsicalart.core.domain

/**
 * Wraps a rotation angle in degrees into the half-open `[0°, 360°)` range.
 *
 * Used by every placement overlay (stickers, text, Background Replace): the
 * stored rotation — and the rotation sliders that mirror it — always describe
 * the same angle, a full turn returns to 0° and continues, and the pinch-twist
 * gesture and the slider agree in both directions (spec:
 * effects_reference.md → *Decorative / placement overlays* → *Rotation wraps at
 * 360°*).
 */
fun wrapDegrees(rotation: Float): Float = rotation.mod(360f)