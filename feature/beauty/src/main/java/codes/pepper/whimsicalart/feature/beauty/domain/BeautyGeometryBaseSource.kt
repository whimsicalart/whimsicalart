package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap

/**
 * The interface the beauty tool is handed to obtain the image its geometry must
 * be resolved against (docs/effect_stack.md → Original geometry image and
 * original geometry). Implemented by the editor, consumed by the geometry-track
 * resolution:
 *
 * - When a geometry-changing (deforming / occluding / hard-stroke) effect sits
 *   between the original geometry image and the layer's position, it folds
 *   ONLY those geometry-changing effects of the gap over the original geometry
 *   image and returns the resulting bitmap — replaying the deformations up to
 *   the layer's position so the geometry matches the deformed pixels.
 * - When no geometry-changing effect exists in that range, it returns the
 *   original geometry image itself (no fold).
 *
 * [upToIndex] is the beauty layer's (proposed) position — exclusive — inside the
 * stack; [scale] selects the resolution bucket the caller resolves at (preview
 * first, full size in the background). The editor stays the single owner of the
 * deformation fold; the beauty tool only consumes the returned base. Geometry
 * never seeds from / populates the colour fold caches.
 */
fun interface BeautyGeometryBaseSource {
    /**
     * Returns the fold of the geometry-changing effects in `[0, upToIndex)` over
     * the original geometry image at [scale], or the original geometry image
     * itself when that range is empty; null when no base is available.
     */
    suspend fun geometryBase(upToIndex: Int, scale: Float): Bitmap?
}