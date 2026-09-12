package codes.pepper.whimsicalart.feature.editor.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import codes.pepper.whimsicalart.feature.editor.domain.decodeBoundedOriented
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Data-layer seam that owns the editor's IN-MEMORY image storage: the
 * incremental fold caches ([fullCache]/[previewCache]) at both resolutions plus
 * the pure decode/downsampling helpers ([decodeFromUri], [downscale]).
 *
 * Physically a data layer (feature/editor/data) behind the repo's stated Clean
 * Architecture: injected via Hilt into [EditorViewModel] and deliberately
 * UNscoped so it lives for exactly the ViewModel's lifetime (the fold caches
 * hold live bitmaps — promoting them to a singleton would hold those pixels for
 * the whole process). A disk-aware persistence implementation can later replace
 * its internals behind the same constructor seam without touching the fold
 * orchestration; the render state machines / staleness transitions are NOT part
 * of this store (they stay with the ViewModel's fold controller).
 */
class EditorCacheStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * The incremental fold cache at FULL resolution. See the [FoldImageCache]
     * docs and the owner's fold-cache notes for the staleness rules that govern
     * the entries.
     */
    internal val fullCache = FoldImageCache()

    /**
     * The incremental fold cache at PREVIEW resolution (the common on-screen
     * path and full-size stand-in). Same class as [fullCache].
     */
    internal val previewCache = FoldImageCache()

    /**
     * Bounded downscale preserving aspect: returns [bitmap] unchanged when its
     * longest side already fits [maxDim], otherwise a scaled copy whose longest
     * side is [maxDim] (used for e.g. the liberation of result-sized buffers to
     * preview scale).
     */
    fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= maxDim) return bitmap
        val w = (bitmap.width * maxDim / max).coerceAtLeast(1)
        val h = (bitmap.height * maxDim / max).coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /**
     * Decodes [uri] bounded to [maxDim] with EXIF orientation baked in, or null
     * when the uri is null / decode fails. Callers pass the dimension bound that
     * matches their pipeline stage (e.g. the segment source's ceiling) so the
     * photo is never materialized at full resolution just to be downsampled.
     */
    fun decodeFromUri(uri: Uri?, maxDim: Int): Bitmap? {
        if (uri == null) return null
        return runCatching {
            // Never decode the photo at full resolution just to segment it: the
            // mask is upscaled to the working bitmap afterwards anyway, and a
            // full-size transient copy risks an OOM abort on low-memory devices.
            // Bakes EXIF orientation identically to the editor-entry decode so
            // the segment source matches the oriented original's geometry.
            decodeBoundedOriented(context.contentResolver, uri, maxDim)
        }.getOrNull()
    }
}

/**
 * The incremental fold cache at ONE resolution (preview or full — the ViewModel
 * keeps one instance per resolution, see the fold cache field docs). Holds the
 * composite of the effect prefix BEFORE the current tool (`currentImage`,
 * anchored at `anchorCount` effects), the composite BEFORE the last effect
 * (`prevImage`), the full-stack composite (`lastImage`), and the fingerprints of
 * the effect lists that produced them.
 */
internal class FoldImageCache(
    var anchorCount: Int = 0,
    var currentSig: String = "",
    var prevSig: String = "",
    var stackSignature: String = "",
    var currentImage: Bitmap? = null,
    var prevImage: Bitmap? = null,
    var lastImage: Bitmap? = null
) {
    /**
     * Rule 1 — COMMIT AT THE TAIL (spec → Staleness transitions on structural
     * changes): the input of the new last effect is the old whole stack, so
     * `prevImage` takes `lastImage`'s VALUE (a pointer transfer tagged with the
     * old whole-stack key), and `lastImage` is marked stale (its signature is
     * cleared; the bitmap is kept and re-folded on the next whole-stack request,
     * whose expected key will not match). A previously-stale `lastImage` yields a
     * freshly-invalid `prevImage`. Pure flag/pointer transition — never renders,
     * never recycles.
     */
    fun applyTailCommit() {
        val lastWasFresh = stackSignature.isNotEmpty()
        prevImage = lastImage
        prevSig = if (lastWasFresh) stackSignature else ""
        stackSignature = ""
    }

    /**
     * Rule 2 — REMOVAL OF THE LAST LAYER (N → N-1), the mirror of rule 1:
     * `lastImage` takes `prevImage`'s VALUE (the old previous composite IS the
     * new final image), and `prevImage` is marked stale (the new N-2 composite
     * must be re-folded). If `prevImage` was already stale the transferred
     * `lastImage` is invalid too. Pure flag/pointer transition.
     */
    fun applyRemoveLast() {
        val prevWasFresh = prevSig.isNotEmpty()
        lastImage = prevImage
        stackSignature = if (prevWasFresh) prevSig else ""
        prevSig = ""
    }

    /**
     * Rule 3 — REMOVAL/INSERT at or before the PREVIOUS layer (index ≤ N-2):
     * both `prevImage` and `lastImage` become stale — their bases changed.
     */
    fun applyStaleAtOrBeforePrev() {
        prevSig = ""
        stackSignature = ""
    }

    /**
     * Rule 4 — REMOVAL at or before the cached `currentPosition`: `currentImage`
     * becomes stale (its prefix is gone / repositioned). Combines with rules 1-3.
     */
    fun applyCurrentStale() {
        currentSig = ""
    }

    fun clear() {
        currentImage?.takeUnless(Bitmap::isRecycled)?.recycle()
        currentImage = null
        prevImage?.takeUnless(Bitmap::isRecycled)?.recycle()
        prevImage = null
        lastImage?.takeUnless(Bitmap::isRecycled)?.recycle()
        lastImage = null
        anchorCount = 0
        currentSig = ""
        prevSig = ""
        stackSignature = ""
    }

    /**
     * Drops every cached bitmap WITHOUT recycling them. The teardown / photo-
     * change path must never recycle fold intermediates: a render worker may be
     * mid-`getPixel` on them and cannot be preempted by `Job.cancel()` — freeing
     * the pixels underneath it aborts the process (HWUI "cannot access an
     * invalid/free'd bitmap here!"). GC reclaims the bitmaps once the running
     * passes release them (or on process death).
     */
    fun dropWithoutRecycle() {
        currentImage = null
        prevImage = null
        lastImage = null
        anchorCount = 0
        currentSig = ""
        prevSig = ""
        stackSignature = ""
    }
}