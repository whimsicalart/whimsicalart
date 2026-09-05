package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap

/**
 * Produces a skin-region alpha mask for a photo so the skin-retouch tools
 * (smoothing, spot removal, skin tone, foundation) follow the TRUE skin pixels
 * instead of a geometric polygon derived from the face landmarks.
 *
 * [segment] returns an ARGB_8888 [Bitmap] the same size as [source]; the ALPHA
 * of each pixel encodes how confidently it belongs to face skin (`0` = not skin,
 * `255` = skin), or `null` when segmentation is unavailable (missing model
 * asset, native runtime error, …) so callers fall back to the geometric face
 * contour. The mask naturally excludes the eyes, brows and mouth which the ML
 * model classifies as non-skin.
 *
 * This isolates the MediaPipe Tasks (LiteRT) [ImageSegmenter], which runs the
 * bundled `skin_segmenter.tflite` (the SelfieMulticlass model) natively and
 * therefore cannot execute on the JVM under Robolectric. The pure mask -> path
 * conversion that drives rendering lives in [SkinMaskProcessor], which is
 * unit-testable.
 */
interface SkinSegmenter {
    fun segment(source: Bitmap): Bitmap?

    /** Releases native resources. No-op by default so fakes are trivially testable. */
    fun close() = Unit
}