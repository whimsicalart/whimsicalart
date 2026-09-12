package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

import android.graphics.Bitmap
import codes.pepper.whimsicalart.feature.editor.domain.MergedEffect
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Bounds on the per-layer preview cache and its published row thumbnails. */
private const val LAYER_THUMB_MAX = 100
private const val LAYER_PREVIEW_CACHE_MAX = 64

/**
 * Owns the per-layer preview cache of the editor: the bounded preview bitmaps
 * keyed by [StackEffect.layerKey] PLUS their fingerprints, the incremental
 * rebuild pass, eviction and the ≤100×100 Layers-row thumbnails
 * (docs/effect_stack.md → Per-layer preview cache). Rendering is injected via
 * [renderLayerTail] so this store stays a pure administrator of bitmaps and
 * fingerprints — the ViewModel supplies the actual fold through it.
 */
internal class LayerPreviewManager(
    private val fingerprintOf: (StackEffect) -> String
) {
    // One cached preview per layer, dense enough for the Layers fast-path and the
    // row thumbnails; entries are independent copies; evicted (and recycled) on
    // fingerprint invalidation or when the preview scale bucket changes.
    private val layerPreviewCache = HashMap<String, Bitmap>()
    private val layerPreviewFingerprints = HashMap<String, String>()

    /**
     * The one incremental per-layer preview fold pass: each layer's preview is
     * the previous layer's preview folded with one more effect (byte-identical
     * to folding from the base); layers whose fingerprint is unchanged are reused
     * untouched. Every layer's result is stored BOTH in this bounded cache (dense
     * enough for the Layers fast-path and the row thumbnails) AND as the layer's
     * own **carried** [StackEffect.committedPreview] — the field the next layer's
     * fold resumes from, and the interactive preview path folds from.
     *
     * Runs the whole chain from [base]; the callers (commit / Layers-window open)
     * have already evicted the stale tail (prefix-contagious), so unchanged
     * prefixes short-circuit through the fingerprint fast-path and only the
     * changed layer + tail are actually re-folded.
     *
     * This pass intentionally does NOT route through the fold-to-index colour
     * controller (spec → Centralized full-size controller, "no parallel
     * whole-stack fold may remain"): it is a per-LAYER incremental builder that
     * publishes one preview bitmap PER layerKey (resuming each from the previous
     * layer's carried preview), not a whole-stack composite — so no parallel
     * whole-stack fold exists here. Whole-stack composites (the editor preview,
     * save / merge / zoom > 100%) all go through the controller; this builder is
     * the documented layer-by-layer cache path that keeps the two cache spaces
     * (per-layer preview cache vs the controller's 3-image full/preview cache)
     * separate.
     *
     * @param renderLayerTail folds effect stack prefix i (resuming the fold from
     *   [prevComposite]) into the next layer's preview bitmap.
     * @return the published layer thumbnails and the fingerprints map to commit.
     */
    suspend fun rebuildCommittedPreviews(
        stack: List<StackEffect>,
        scale: Float,
        base: Bitmap,
        renderLayerTail: suspend (i: Int, prevComposite: Bitmap) -> Bitmap
    ): Pair<HashMap<String, Bitmap>, Map<String, String>> {
        var prevComposite: Bitmap = base
        // Prefix-validity: while every layer up to i is cached with a matching
        // fingerprint at the same dims, prevComposite can walk the cache; at
        // the first stale layer the whole tail is re-folded incrementally.
        var chainValid = true
        for (i in stack.indices) {
            val effect = stack[i]
            val key = effect.layerKey
            val fp = fingerprintOf(effect)
            val cached = layerPreviewCache[key]
            if (chainValid && cached != null && !cached.isRecycled &&
                layerPreviewFingerprints[key] == fp &&
                cached.width == prevComposite.width && cached.height == prevComposite.height
            ) {
                // Unchanged layer: reuse the cached bitmap and carry it on the
                // (possibly fresh) instance — no re-render, no new bitmap.
                effect.committedPreview = cached
                prevComposite = cached
                continue
            }
            chainValid = false
            val rendered = if (effect is MergedEffect) {
                // A merged layer is the identity bake of its base at preview
                // resolution (its image is full-size; folding it would leak
                // full-size pixels into the preview cache).
                prevComposite
            } else {
                withContext(Dispatchers.Default) { renderLayerTail(i, prevComposite) }
            }
            val stored = rendered.copy(Bitmap.Config.ARGB_8888, true)
            if (rendered !== prevComposite && !rendered.isRecycled) rendered.recycle()
            layerPreviewCache[key]?.takeUnless(Bitmap::isRecycled)?.recycle()
            layerPreviewCache[key] = stored
            layerPreviewFingerprints[key] = fp
            // The layer carries its committed preview for the session — the base
            // the NEXT layer folds from (docs/effect_stack.md → Per-layer
            // preview cache).
            effect.committedPreview = stored
            prevComposite = stored
        }
        // Bounded LRU-by-insertion: drop the oldest entries beyond the cap and
        // null the carried field on the live layer holding a dropped bitmap so a
        // recycled preview is never resumed from.
        while (layerPreviewCache.size > LAYER_PREVIEW_CACHE_MAX) {
            val oldest = layerPreviewCache.keys.firstOrNull() ?: break
            layerPreviewCache.remove(oldest)?.takeUnless(Bitmap::isRecycled)?.recycle()
            layerPreviewFingerprints.remove(oldest)
            stack.firstOrNull { it.layerKey == oldest }?.committedPreview = null
        }
        // Publish row thumbnails = previews downscaled to ≤100×100.
        val thumbs = HashMap<String, Bitmap>()
        for (e in stack) {
            val p = layerPreviewCache[e.layerKey] ?: continue
            thumbs[e.layerKey] = scaleToLayerThumb(p)
        }
        return thumbs to layerPreviewFingerprints.toMap()
    }

    /** Drops (and recycles) the per-layer preview cache and its fingerprints. */
    fun clearLayerPreviews(stack: List<StackEffect>) {
        layerPreviewCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
        layerPreviewCache.clear()
        layerPreviewFingerprints.clear()
        stack.forEach { it.committedPreview = null }
    }

    /** Drops (and recycles) the per-layer previews for [staleKeys]. */
    fun evictLayerPreviews(stack: List<StackEffect>, staleKeys: Set<String>) {
        staleKeys.forEach { key ->
            layerPreviewCache.remove(key)?.takeUnless(Bitmap::isRecycled)?.recycle()
            layerPreviewFingerprints.remove(key)
            stack.firstOrNull { it.layerKey == key }?.committedPreview = null
        }
    }

    /** Downscales a layer's preview to the ≤100×100 Layers-row thumbnail box. */
    fun scaleToLayerThumb(preview: Bitmap): Bitmap {
        val maxDim = maxOf(preview.width, preview.height).coerceAtLeast(1)
        if (maxDim <= LAYER_THUMB_MAX) {
            return preview.copy(Bitmap.Config.ARGB_8888, false)
        }
        val s = LAYER_THUMB_MAX.toFloat() / maxDim
        return Bitmap.createScaledBitmap(
            preview,
            (preview.width * s).roundToInt().coerceAtLeast(1),
            (preview.height * s).roundToInt().coerceAtLeast(1),
            true
        )
    }
}