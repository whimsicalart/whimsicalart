package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Path
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult

/**
 * The ML field a geometry consumer actually requires. Per-field lazy resolution
 * (docs/effect_stack.md → Shared geometry context) lets a generate run ONLY the
 * ML stages the requesting tool renders with: a lipstick layer needs only FACE
 * detection/mesh; auto / smoothing / spots / skin-tone / foundation add SKIN;
 * hair color adds HAIR; the background tool needs only SUBJECT_MASK.
 */
enum class GeometryField {
    FACE,
    SKIN,
    HAIR,
    SUBJECT_MASK;

    companion object {
        /** Every field — the default request for a full (legacy) resolve. */
        val ALL: Set<GeometryField> = enumValues<GeometryField>().toSet()

        /** The fields the beauty tools consume (everything but SUBJECT_MASK). */
        val BEAUTY: Set<GeometryField> = setOf(FACE, SKIN, HAIR)
    }
}

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
 * outlines in the same image space; [subjectMask] is the background tool's
 * person alpha mask in that space (never computed by [BeautyGeometryResolver] —
 * the editor supplies it through the same context via
 * [GeometryField.SUBJECT_MASK]).
 */
data class BeautyGeometry(
    val faceResult: FaceDetectionResult? = null,
    val skinPath: Path? = null,
    val hairPath: Path? = null,
    val subjectMask: Bitmap? = null,
    /**
     * The pixel-space dimensions (width × height of the image state) the
     * geometry's coordinates are expressed in. Populated (per the
     * geometry-track resolution rule, docs/effect_stack.md → Lazy beauty
     * geometry) with the geometry-track BASE the fields were resolved over:
     * never the full-size original — the caps at 1–4 MP — so the editor can
     * rescale a capped-resolution geometry up to the full-size colour fold it
     * renders over ([rescaledTo]). 0 = unknown/legacy value, treated as "already
     * at the consuming resolution" (nothing to rescale).
     */
    val baseWidth: Int = 0,
    val baseHeight: Int = 0
) {
    /**
     * Merges a single-field (or partial) resolution into this geometry, keeping
     * every already-present field the partial did not (re)compute. Used by the
     * shared context's incremental per-field resolution against a retained base.
     * The pixel-space base dims carried by the partial (when present) win:
     * both resolve against the SAME retained base, and the partial is newer.
     */
    fun mergedWith(partial: BeautyGeometry): BeautyGeometry = BeautyGeometry(
        faceResult = partial.faceResult ?: faceResult,
        skinPath = partial.skinPath ?: skinPath,
        hairPath = partial.hairPath ?: hairPath,
        subjectMask = partial.subjectMask ?: subjectMask,
        baseWidth = partial.baseWidth.takeIf { it > 0 } ?: baseWidth,
        baseHeight = partial.baseHeight.takeIf { it > 0 } ?: baseHeight
    )

    /**
     * Returns this geometry expressed in the [targetWidth] × [targetHeight]
     * pixel space, rescaling every coordinate field by the axis ratios between
     * its recorded [baseWidth]/[baseHeight] and the target (no-op when the
     * geometry is unanchored or already matches). The editor calls this after
     * the geometry track cap (docs/effect_stack.md → Lazy beauty geometry) so a
     * geometry resolved against a 1–4 MP capped base lands exactly where the
     * full-size colour fold renders it; the swap-in stays seamless because the
     * detection itself ran on the same (≤ 4 MP) input in both spaces.
     */
    fun rescaledTo(targetWidth: Int, targetHeight: Int): BeautyGeometry {
        if (baseWidth <= 0 || baseHeight <= 0 ||
            (baseWidth == targetWidth && baseHeight == targetHeight)
        ) {
            return this
        }
        val sx = targetWidth.toFloat() / baseWidth
        val sy = targetHeight.toFloat() / baseHeight
        return copy(
            faceResult = faceResult?.scaled(sx, sy),
            skinPath = skinPath?.scaled(sx, sy),
            hairPath = hairPath?.scaled(sx, sy),
            subjectMask = subjectMask?.scaledTo(targetWidth, targetHeight),
            baseWidth = targetWidth,
            baseHeight = targetHeight
        )
    }

    companion object {
        val NONE = BeautyGeometry()
    }
}
