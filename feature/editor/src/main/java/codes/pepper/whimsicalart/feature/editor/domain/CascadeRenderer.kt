package codes.pepper.whimsicalart.feature.editor.domain

import android.content.Context
import android.graphics.Bitmap

/**
 * Generic left-to-right fold engine for the ordered effect stack.
 *
 * Each [StackEffect] is self-contained and knows how to render itself given the
 * CURRENT running image and its own parameters (via [StackEffect.render]). This
 * engine merely threads the image left-to-right: it starts from [base] and, for
 * every effect in order, sets `current = effect.render(current, context)`.
 *
 * The SAME engine is reused for small thumbnails (a small input bitmap) and the
 * final full-size image, so thumbnail previews are byte-consistent with the
 * saved result and every added effect folds on top of the already-cropped,
 * already-transformed image.
 *
 * Bitmap lifetime: [base] is owned by the caller and is never recycled here.
 * Intermediate fold outputs created by transforming effects are recycled once
 * they are superseded by the next effect; draw-in-place effects return the same
 * bitmap they were given, so they are never recycled.
 */
object CascadeRenderer {

    fun fold(
        context: Context,
        base: Bitmap,
        effects: List<StackEffect>
    ): Bitmap {
        var current = base
        for (effect in effects) {
            val next = effect.render(current, context)
            if (next !== current && current !== base && !current.isRecycled) {
                current.recycle()
            }
            current = next
        }
        return current
    }
}
