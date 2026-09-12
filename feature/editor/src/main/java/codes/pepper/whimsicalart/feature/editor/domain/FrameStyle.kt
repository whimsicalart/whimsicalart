package codes.pepper.whimsicalart.feature.editor.domain

/**
 * The frame's outer-edge style (docs/effects_reference.md → *Frames*): shape
 * styles (an outright geometric band), edge & bevel profiles (the real
 * picture-frame / furniture edge treatments — each takes the **edge size**
 * slider), ornamental / moulding styles (wavy / zig-zag / concave / toothed /
 * floral / scalloped), and the special **Custom (Curved)** style with its own
 * four sliders. Rendered resolution-independently by [FrameGeometry] (the
 * committed pass in `BitmapRenderer.drawFrames` and the Compose overlay preview
 * share the same geometry).
 *
 * Enum order IS the chip order in the picker (`FrameStyle.entries`); the ⁎
 * styles ([hasEdgeSize]) expose the edge-size slider alongside thickness.
 */
enum class FrameStyle(val displayName: String) {

    /** A straight-edged rectangular band. */
    SOLID("Solid"),

    /** A rectangle with rounded corners (edge size = corner radius). */
    ROUNDED("Rounded"),

    /** A corner-chamfered / mitred band (edge size = chamfer depth). */
    BEVELED("Beveled"),

    /** An elliptical / oval band clamped to the largest ellipse that fits. */
    OVAL("Oval"),

    /** A heart-shaped band clamped to the largest heart that fits. */
    HEART("Heart"),

    /** Edge profile: the sides bow inward — concave — toward the corners. */
    INWARD_CURVED("Inward Curve Edge"),

    /** Edge profile: softly rounded corners (edge size = radius, 0% = squared). */
    CORNER_EASED("Corner Eased Edge"),

    /** Edge profile: a rim of rounded gear-teeth bumps. */
    GEAR_EDGE("Gear Edge"),

    /** Edge profile: an S-shaped double curve along the edge. */
    OGEE_EDGE("Ogee Edge"),

    /** Edge profile: two S-curves stacked. */
    DOUBLE_OGEE_EDGE("Double Ogee Edge"),

    /** Edge profile: a stepped flat with an inner chamfer. */
    DUPONT_EDGE("Dupont Edge"),

    /** Edge profile: a concave inward-quarter hollow. */
    COVE("Cove Edge"),

    /** Edge profile: a deep concave cove with a flat step. */
    FRENCH_COVE("French Cove Edge"),

    /** Edge profile: the dupont step capped by a half-round convex bead. */
    DUPONT_BULLNOSE("Dupont + \u00BD Bullnose Edge"),

    /** Edge profile: an S-curve that recedes into a hollow. */
    OGEE_COVE_EDGE("Ogee Cove Edge"),

    /** A band whose inner edge waves in and out. */
    WAVY("Wavy"),

    /** A zig-zag edge. */
    SAWTOOTH("Sawtooth"),

    /** An edge with alternating square teeth (the former "gear" style). */
    COGGED("Cogged"),

    /** A repeated flower / leaf flourish along the border. */
    FLORAL("Floral"),

    /** Moulding profile: a broad flat step that drops off a vertical tier. */
    WATERFALL("Waterfall"),

    /** One wide S-wave per side (formerly "Ogee"). */
    SINGLE_WAVE("Single Wave"),

    /** Two S-waves per side (formerly "Double Ogee"). */
    TWIN_WAVE("Twin Wave"),

    /** A flat band with a recessed mid-step (formerly "Dupont"). */
    RECESSED("Recessed"),

    /** An S-wave shifted along the side (formerly "Ogee Cove"). */
    OFFSET_WAVE("Offset Wave"),

    /** A row of inward half-curves scooping into the band. */
    WATER_WAVES_INWARD("Water Waves Inward"),

    /** A row of half-curves bulging outward into the photo. */
    WATER_WAVES_OUTWARD("Water Waves Outward"),

    /** Free-form edge driven by angle / curve distance / strength / count. */
    CUSTOM("Custom (Curved)");

    /** The ⁎ styles: the edge-size slider is visible for these. */
    val hasEdgeSize: Boolean
        get() = when (this) {
            ROUNDED, BEVELED,
            INWARD_CURVED, CORNER_EASED, GEAR_EDGE, OGEE_EDGE, DOUBLE_OGEE_EDGE,
            DUPONT_EDGE, COVE, FRENCH_COVE, DUPONT_BULLNOSE, OGEE_COVE_EDGE -> true
            else -> false
        }

    /** True for the [CUSTOM] free-form style (owns the four custom sliders). */
    val isCustom: Boolean get() = this == CUSTOM
}