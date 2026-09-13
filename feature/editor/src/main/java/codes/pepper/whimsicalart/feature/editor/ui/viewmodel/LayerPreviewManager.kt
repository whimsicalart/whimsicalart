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
     * Each layer folds at ITS OWN pinned scale — `scale_i = min(1,
     * previewSizeMaxSide / estimatedOutputSize(prefix i).maxSide)` — supplied by
     * [computePrefixScale], so every layer's committed preview has max side ==
     * the fixed preview ceiling (capped at the layer's own prefix output when
     * smaller; never an upscale). When the scale switches mid-chain (a
     * crop/transform shrinks or grows a prefix's output), the chain RE-BASES:
     * [renderLayerTail] is handed a `null` resume composite so it folds the whole
     * prefix from a base at the NEW scale — the only way each prefix's preview
     * can sit at its own pinned resolution while keeping the incremental chain
     * byte-identical to a base fold. Within a steady scale run the chain resumes
     * from the previous layer's carried preview exactly as before.
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
     * @param base the fold base at the FIRST layer's scale (the chain start; on
     *   a scale switch the renderer re-resolves its own base via [renderLayerTail]).
     * @param computePrefixScale the i-th layer's OWN pinned preview scale, derived
     *   from its prefix's estimated output footprint.
     * @param renderLayerTail folds effect-stack prefix i into the layer's preview
     *   bitmap. A NON-NULL [Bitmap] second arg resumes the incremental chain from
     *   that composite at the given scale; a NULL second arg signals a scale
     *   switch (re-base): the whole prefix is folded from a base at the given
     *   scale. May return null only when no base can be resolved.
     * @return the published layer thumbnails and the fingerprints map to commit.
     */
    suspend fun rebuildCommittedPreviews(
        stack: List<StackEffect>,
        base: Bitmap,
        computePrefixScale: (i: Int) -> Float,
        renderLayerTail: suspend (i: Int, prevComposite: Bitmap?, scale: Float) -> Bitmap?
    ): Pair<HashMap<String, Bitmap>, Map<String, String>> {
        if (stack.isEmpty()) return HashMap<String, Bitmap>() to emptyMap()
        var prevComposite: Bitmap = base
        // The scale run the incremental chain currently walks. When the pinned
        // per-layer scale switches, the tail is RE-BASED (never resumed from a
        // preview at the old scale — that would pin the tail at the wrong
        // resolution).
        var prevScale = computePrefixScale(0)
        // Prefix-validity: while every layer up to i is cached with a matching
        // fingerprint at the same dims, prevComposite can walk the cache; at
        // the first stale layer the whole tail is re-folded incrementally.
        var chainValid = true
        for (i in stack.indices) {
            val effect = stack[i]
            val key = effect.layerKey
            val fp = fingerprintOf(effect)
            val layerScale = computePrefixScale(i)
            if (layerScale != prevScale) chainValid = false
            val cached = layerPreviewCache[key]
            if (chainValid && cached != null && !cached.isRecycled &&
                layerPreviewFingerprints[key] == fp &&
                cached.width == prevComposite.width && cached.height == prevComposite.height
            ) {
                // Unchanged layer at the SAME pinned scale: reuse the cached
                // bitmap and carry it on the (possibly fresh) instance — no
                // re-render, no new bitmap.
                effect.committedPreview = cached
                prevComposite = cached
                prevScale = layerScale
                continue
            }
            chainValid = false
            val rebase = layerScale != prevScale
            val rendered = if (rebase || effect !is MergedEffect) {
                withContext(Dispatchers.Default) {
                    renderLayerTail(i, if (rebase) null else prevComposite, layerScale)
                }
            } else {
                // A merged layer is the identity bake of its base at preview
                // resolution (its image is full-size; folding it would leak
                // full-size pixels into the preview cache). Folded at the steady
                // scale it is carried forward untouched; on a scale switch the
                // renderer re-bakes it at the new scale (the `rebase` branch).
                prevComposite
            }
            if (rendered == null) {
                // The base for this layer's scale could not be resolved — leave
                // the layer without a carried preview and keep the chain invalid.
                prevScale = layerScale
                continue
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
            prevScale = layerScale
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