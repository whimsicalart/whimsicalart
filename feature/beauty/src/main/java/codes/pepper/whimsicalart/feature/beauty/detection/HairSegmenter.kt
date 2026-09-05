package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap

/**
 * Produces a hair-region alpha mask for a photo so the hair-colour tool can tint
 * the TRUE hair silhouette instead of the geometric "dome" above the face bounds.
 *
 * [segment] returns an ARGB_8888 [Bitmap] the same size as [source]; the ALPHA of
 * each pixel encodes how confidently it belongs to hair (`0` = not hair, `255` =
 * hair), or `null` when segmentation is unavailable (missing model asset, native
 * runtime error, …) so callers fall back to the heuristic dome.
 *
 * This isolates the MediaPipe Tasks (LiteRT) `ImageSegmenter`, which runs the
 * bundled `hair_segmenter.tflite` (a model with custom ops that only the
 * MediaPipe/LiteRT runtime can resolve) natively and therefore cannot execute on
 * the JVM under Robolectric. The pure mask -> path conversion that drives
 * rendering lives in [HairMaskProcessor], which is unit-testable.
 */
interface HairSegmenter {
    fun segment(source: Bitmap): Bitmap?

    /** Releases native resources. No-op by default so fakes are trivially testable. */
    fun close() = Unit
}