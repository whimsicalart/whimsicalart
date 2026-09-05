package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.graphics.Bitmap

/**
 * Produces a subject (foreground / person) alpha mask for a photo, so the app
 * can defocus or replace the background while keeping the subject sharp.
 *
 * The returned [Bitmap] is ARGB_8888 and has exactly the same dimensions as
 * [source]. The ALPHA channel of each pixel encodes how confidently that pixel
 * belongs to the subject: `0` = background, `255` = subject, with soft values
 * in between marking the (feathered) boundary. Returns `null` if segmentation
 * is unavailable (missing model asset, native runtime error, …) so callers can
 * degrade gracefully.
 *
 * This mirrors how the reference app (Meitu, via its proprietary
 * `libmlabsegment.so`) and the open-source MediaPipe Selfie Segmentation /
 * ML Kit path separate a person from the background: a small on-device neural
 * network produces a low-resolution person-confidence mask that is then
 * up-scaled and used to drive the background effect.
 */
interface SubjectSegmenter {
    fun segment(source: Bitmap): Bitmap?
}
