package codes.pepper.whimsicalart.core.domain

import kotlin.math.min

/**
 * The anchored reference base of a positional layer (docs/effect_stack.md →
 * *Positional layers — anchored reference base, centre-origin, uniform fit*).
 *
 * The full-size (source-pixel) width × height of the image a layer's normalized
 * geometry was captured against. `0×0` is the legacy "unset" state: the layer
 * renders directly against whatever image it runs over (identity mapping), so
 * every anchored path degrades to the un-anchored behaviour when unset.
 */
data class GeometryAnchor(val width: Int = 0, val height: Int = 0) {
    val isSet: Boolean get() = width > 0 && height > 0
}

/**
 * The centre-origin uniform fit of an anchor's reference frame inside a
 * destination image: `scale = min(dst/ref)` on BOTH axes (never a per-axis
 * stretch), with the scaled reference frame centred in the destination.
 *
 * An unset or non-positive reference resolves to the identity fit, so anchored
 * and legacy geometry share every mapping path and an unanchored layer keeps
 * its historical rendering byte-for-byte.
 */
data class UniformFit(val scale: Float, val offsetX: Float, val offsetY: Float) {

    /** The reference-frame x-fraction of [referenceWidth] in destination pixels. */
    fun frameX(xFraction: Float, referenceWidth: Int): Float =
        offsetX + xFraction * referenceWidth * scale

    /** The reference-frame y-fraction of [referenceHeight] in destination pixels. */
    fun frameY(yFraction: Float, referenceHeight: Int): Float =
        offsetY + yFraction * referenceHeight * scale

    /** An x-axis measure fraction of [referenceWidth] in destination pixels (uniform scale). */
    fun measureX(widthFraction: Float, referenceWidth: Int): Float =
        widthFraction * referenceWidth * scale

    /** A y-axis measure fraction of [referenceHeight] in destination pixels (uniform scale). */
    fun measureY(heightFraction: Float, referenceHeight: Int): Float =
        heightFraction * referenceHeight * scale

    companion object {
        /** Fits nothing (destination == reference at scale 1, no offset). */
        val IDENTITY = UniformFit(1f, 0f, 0f)

        /**
         * Fits a [referenceWidth]×[referenceHeight] frame inside a
         * [destinationWidth]×[destinationHeight] image without distortion.
         */
        fun of(
            destinationWidth: Int,
            destinationHeight: Int,
            referenceWidth: Int,
            referenceHeight: Int
        ): UniformFit {
            if (referenceWidth <= 0 || referenceHeight <= 0) return IDENTITY
            if (destinationWidth <= 0 || destinationHeight <= 0) return IDENTITY
            val f = min(
                destinationWidth.toFloat() / referenceWidth,
                destinationHeight.toFloat() / referenceHeight
            )
            return UniformFit(
                f,
                (destinationWidth - referenceWidth * f) / 2f,
                (destinationHeight - referenceHeight * f) / 2f
            )
        }
    }
}

/** The geometry of a positional layer re-expressed against its NEW base. */
data class ReAnchored(
    val x: Float,
    val y: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val anchor: GeometryAnchor
)

/**
 * Re-anchors geometry stored against [oldAnchor] into a new full-size base of
 * [newWidth]×[newHeight] (spec rule 4): every stored fraction — centre [x]/[y]
 * of the reference and [widthFraction]/[heightFraction] of its dimensions — is
 * re-expressed through the same centre-origin uniform fit, so the layer keeps
 * its on-image position, size and aspect when its base changes. The returned
 * geometry is expressed against the new base and carries it as its anchor.
 *
 * An unset anchor adopts the new base without changing any fraction (identity)
 * and still records [newWidth]×[newHeight], so back-filled layers inherit an
 * exact anchor the first time they re-anchor.
 */
fun reAnchor(
    x: Float,
    y: Float,
    widthFraction: Float,
    heightFraction: Float,
    oldAnchor: GeometryAnchor,
    newWidth: Int,
    newHeight: Int
): ReAnchored {
    val nw = newWidth.coerceAtLeast(1)
    val nh = newHeight.coerceAtLeast(1)
    if (!oldAnchor.isSet) {
        return ReAnchored(x, y, widthFraction, heightFraction, GeometryAnchor(nw, nh))
    }
    val fit = UniformFit.of(nw, nh, oldAnchor.width, oldAnchor.height)
    return ReAnchored(
        x = fit.frameX(x, oldAnchor.width) / nw,
        y = fit.frameY(y, oldAnchor.height) / nh,
        widthFraction = fit.measureX(widthFraction, oldAnchor.width) / nw,
        heightFraction = fit.measureY(heightFraction, oldAnchor.height) / nh,
        anchor = GeometryAnchor(nw, nh)
    )
}