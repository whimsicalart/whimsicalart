package codes.pepper.whimsicalart.feature.editor.domain.removal

import android.graphics.Bitmap

/**
 * Content-aware object-removal seam. Produces a version of [source] with the
 * region covered by [mask] (alpha `255` = to remove, `0` = keep) synthesised
 * from the surrounding pixels, so painted objects disappear into the background.
 *
 * Returns a new ARGB_8888 [Bitmap] the same size as [source], or `null` when
 * inpainting is unavailable so callers can degrade gracefully (leave [source]
 * as-is and keep the mask unpainted).
 *
 * The default production implementation is [DiffusionInpainter] (a deterministic,
 * pure-JVM classical fill that propagates boundary colours inward). It is fully
 * testable under Robolectric. A learned inpainting model (e.g. a mobile LaMa /
 * DeepFillv2 conversion) can later be swapped in behind this interface without
 * touching the render pipeline; because a model runs natively it would mirror the
 * editor's existing [codes.pepper.whimsicalart.feature.editor.domain.matting.SubjectSegmenter]
 * isolation pattern (interface + pure helpers).
 */
interface InpaintSegmenter {
    fun inpaint(source: Bitmap, mask: Bitmap): Bitmap?
}