package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import codes.pepper.whimsicalart.feature.editor.domain.ExifOrientationReader
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.domain.boundedOrientedDims
import codes.pepper.whimsicalart.feature.editor.domain.decodeBoundedOriented
import codes.pepper.whimsicalart.feature.editor.domain.estimateStackOutputSize
import codes.pepper.whimsicalart.feature.editor.ui.CANVAS_CONTROLS_MIN_WIDTH
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Owns the EDITOR'S FOLD-PIPELINE PLANNING policy (docs/effect_stack.md →
 * Performance / preview cache + Choosing preview resolution + The full-size
 * image model): how big a preview composite must be given the visible viewport
 * and zoom ([previewScaleFor]/[quantizedScale]), what base resolution every fold
 * is derived from (the pristine EXIF-oriented source decode and its
 * `firstFull`/`firstPreview` bases, plus the scale-bucketed downscales), and the
 * memoized full-size output-footprint estimate that lets a crop/transform
 * re-shape that decision ([estimateStackFootprint]).
 *
 * The class is deliberately state-bearing but PASSIVE: it keeps no coroutines,
 * no UI state and no renderer. The ViewModel decides WHEN a fold runs, owns the
 * full/preview [FoldImageCache]s and the async tail, and resolves the source URI
 * through the injected [imageUri] supplier; this planner only answers the
 * resolution and base questions and carries their caches.
 */
internal class RenderPipelinePlanner(
    private val context: Context,
    private val imageUri: () -> Uri?,
    private val effectFingerprint: (StackEffect) -> String
) {

    // Viewing footprint (the region where the preview is actually drawn): the
    // planner's only awareness of the on-screen canvas — everything else
    // (pan/zoom composition) is a pure Compose transform. Drives the
    // preview-resolution computation ([previewScaleFor]). Set via
    // [setViewBox]/[setZoom].
    internal var viewportWidthPx = 0
    internal var viewportHeightPx = 0
    internal var viewZoom = 1f

    // The full-size fold base and its preview-resolution counterpart
    // (docs/effect_stack.md → The full-size image model + Choosing preview
    // resolution): `firstFull` starts as a reference to the pristine
    // `originalImage` decode and is repointed to the full-size merged result
    // after Merge Layers; `firstPreview` is the always-present, load-time,
    // lohalo-downscaled preview base whose max side equals `previewSizeMaxSide`,
    // generated from `firstFull`. [firstFullBitmap] is written directly by the
    // ViewModel after a merge.
    internal var firstFullBitmap: Bitmap? = null
    internal var firstPreviewBitmap: Bitmap? = null
    private var previewSizeMaxSide = 0
    private val previewOriginalScaleCache = HashMap<Int, Bitmap>()
    private var cachedSourceWidth = 0
    private var cachedSourceHeight = 0

    /** Memoized estimated composite footprint (see [estimateStackFootprint]). */
    private var estimatedFootprintSignature: String? = null
    private var estimatedFootprint: Pair<Int, Int>? = null

    fun setViewBox(widthPx: Int, heightPx: Int) {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        if (viewportWidthPx == w && viewportHeightPx == h) return
        viewportWidthPx = w
        viewportHeightPx = h
    }

    fun setZoom(zoom: Float) {
        viewZoom = zoom.coerceIn(0.5f, 5f)
    }

    /**
     * The preview resolution scale (relative to the fold base, ≤ 1): the visible
     * pixel footprint at the effective zoom (capped at 100% — beyond that the
     * full-size fold is produced alongside) plus a [PREVIEW_MARGIN] pad, divided
     * by the base's longest side. [sourceWidth]/[sourceHeight] are the ACTUAL
     * base dims (the bounded, EXIF-oriented decode capped at
     * [SEGMENT_SOURCE_MAX_DIM]) — for a 4000px source capped to 2000px on a
     * 1080px viewport this is ~0.675 at 100% zoom. Never below 5% of the base
     * (a face ML segment still needs enough texture to be accurate).
     */
    /**
     * The fold cache's resolution key: [scale] snapped to 5%-bucket granularity
     * (0.05 steps, at least one bucket). A zoom that stays within a bucket
     * reuses the cached composite at the nearest size (the display scales it,
     * imperceptibly); crossing a bucket re-folds at the new size. Rounds UP
     * (ceiling) so the rasterized preview is ALWAYS at least the padded visible
     * footprint (`viewportMax × effectiveZoom × PREVIEW_MARGIN`) — a down-round
     * can undershoot the display footprint and show a (slightly) upscaled,
     * softer image. Keeps a pinch gesture from rebuilding the preview composite
     * on every tick.
     */
    fun quantizedScale(scale: Float): Float {
        if (scale >= 1f) return 1f
        return (ceil(scale * 20f) / 20f).coerceIn(0.05f, 1f)
    }

    fun previewScaleFor(sourceWidth: Int, sourceHeight: Int): Float {
        val sourceMax = maxOf(sourceWidth, sourceHeight).coerceAtLeast(1)
        val viewportMax = maxOf(viewportWidthPx, viewportHeightPx).coerceAtLeast(1)
        val effectiveZoom = viewZoom.coerceIn(0.5f, 1f)
        val previewMax = viewportMax * effectiveZoom * PREVIEW_MARGIN
        return quantizedScale((previewMax / sourceMax).coerceIn(0.05f, 1f))
    }

    /**
     * The fixed, orientation-invariant preview ceiling
     * (`previewSizeMaxSide`): `max(minSide, maxSide − 600dp)` where `minSide` =
     * `min(deviceWidthPx, deviceHeightPx)` and `maxSide` is the other one. The
     * landscape editor layout reserves a 600 dp right controls panel, so the wide
     * side is trimmed by it; the ceiling is floored at the shorter side so
     * portrait and near-square devices keep the full short-side resolution.
     * Computed once and never re-derived on rotation (the formula is
     * orientation-symmetric) or zoom. A device usable in either orientation keeps
     * the preview-sized images dimensioned for the worst-case orientation.
     */
    fun ensurePreviewCeiling() {
        if (previewSizeMaxSide <= 0) {
            @Suppress("DEPRECATION")
            val dm = context.resources.displayMetrics
            val maxSide = maxOf(dm.widthPixels, dm.heightPixels)
            val minSide = minOf(dm.widthPixels, dm.heightPixels)
            previewSizeMaxSide =
                maxOf(minSide, maxSide - (CANVAS_CONTROLS_MIN_WIDTH.value * dm.density).roundToInt())
                    .coerceAtLeast(1)
        }
    }

    /** The fixed, orientation-invariant preview ceiling (px). */
    fun previewCeilingPx(): Int {
        ensurePreviewCeiling()
        return previewSizeMaxSide
    }

    /**
     * Returns the pristine decoded source — cached across renders (so the
     * expensive blocking URI decode happens once per photo) and always kept
     * (never recycled) as the ultimate base of every fold and hold-to-compare,
     * exactly like `originalImage` in the effect_stack.md model. Recycled only
     * when the photo itself changes ([clearOriginalCaches]).
     */
    fun cachedOriginalDecode(): Bitmap? {
        foldCacheOriginal?.let {
            if (!it.isRecycled) return it
            foldCacheOriginal = null
        }
        val decoded = decodeFromUri() ?: return null
        foldCacheOriginal = decoded
        cachedSourceWidth = decoded.width
        cachedSourceHeight = decoded.height
        return decoded
    }

    private var foldCacheOriginal: Bitmap? = null

    /**
     * The full-size fold base (`firstFull`; docs/effect_stack.md → The full-size
     * image model): a reference to the pristine [cachedOriginalDecode] before any
     * merge, repointed to the full-size merged result after [mergeLayers]. Every
     * full-size fold (scale ≥ 1) and every preview fold starts from this.
     */
    fun firstFullBase(): Bitmap? {
        firstFullBitmap?.let { if (!it.isRecycled) return it }
        firstFullBitmap = null
        val full = cachedOriginalDecode() ?: return null
        firstFullBitmap = full
        return full
    }

    /**
     * The always-present preview base (`firstPreview`; docs/effect_stack.md →
     * Choosing preview resolution): a lohalo downscale of `firstFull` whose MAX
     * side equals `min(previewSizeMaxSide, firstFull max side)` (never an
     * upscale) and whose other side preserves the source aspect ratio. Computed
     * lazily on first preview-base need, memoized for the session, and regenerated
     * from the merged result after a merge (dropping the old reference — never
     * recycling — so an in-flight fold worker is not reading freed pixels).
     */
    fun firstPreviewBase(): Bitmap? {
        firstPreviewBitmap?.let { if (!it.isRecycled) return it }
        ensurePreviewCeiling()
        val full = firstFullBase() ?: return null
        val maxSide = maxOf(full.width, full.height).coerceAtLeast(1)
        // Never upscale: a source already at-or-below the ceiling IS the preview
        // base (max side stays the source's, which is ≤ the ceiling).
        val target = minOf(previewSizeMaxSide, maxSide)
        if (target >= maxSide) {
            firstPreviewBitmap = full
            return full
        }
        val factor = target.toFloat() / maxSide
        val w = (full.width * factor).roundToInt().coerceAtLeast(1)
        val h = (full.height * factor).roundToInt().coerceAtLeast(1)
        val preview = Bitmap.createScaledBitmap(full, w, h, false)
        firstPreviewBitmap = preview
        return preview
    }

    /**
     * Returns the fold base at the requested [scale]: the pristine full-size
     * first base at scale ≥ 1 (in [firstFullBitmap]); otherwise a preview
     * resolution **lohalo downscale of `firstPreview`** — the always-present
     * preview-ceiling base — memoized per scale bucket (permille) so a steady
     * zoom keeps reusing the same preview base. The base is ALWAYS derived from
     * `firstPreview`, never a fresh bilinear decode of the full source
     * (docs/effect_stack.md → Choosing preview resolution): when the requested
     * bucket sits at or above `firstPreview`'s own footprint the base IS
     * `firstPreview` itself, otherwise `firstPreview` is downscaled to the bucket
     * dims (the same footprint the old full-source decode produced). The bucket
     * bitmap is always a fresh smaller bitmap derived from `firstPreview` — never
     * an alias of the cache-owned preview base — so recycling one cache never
     * touches the other.
     */
    fun cachedBaseDecode(scale: Float): Bitmap? {
        if (scale >= 1f) return firstFullBase()
        val first = firstPreviewBase() ?: return null
        val full = firstFullBase() ?: return null
        // Bucket index matches quantizedScale's 0.05-bucket key so the base and
        // the fold cache always agree on which resolution is in play.
        val key = (scale * 20f).roundToInt().coerceAtLeast(1)
        previewOriginalScaleCache[key]?.let { if (!it.isRecycled) return it }
        if (previewOriginalScaleCache.size > 4) {
            previewOriginalScaleCache.forEach { (_, b) -> if (!b.isRecycled) b.recycle() }
            previewOriginalScaleCache.clear()
        }
        val w = (full.width * scale).roundToInt().coerceAtLeast(1)
        val h = (full.height * scale).roundToInt().coerceAtLeast(1)
        // At or above the preview ceiling the base is `firstPreview` itself.
        if (w >= first.width && h >= first.height) return first
        val small = Bitmap.createScaledBitmap(first, w, h, false)
        previewOriginalScaleCache[key] = small
        return small
    }

    /**
     * The base every fold starts from (docs/effect_stack.md → The full-size image
     * model): the named `firstFull` — the pristine decode before any merge, the
     * flattened merged result after one (a merge repoints it). At preview
     * resolution the base is `firstPreview` — `firstFull` lohalo-downscaled to
     * the preview ceiling, bucketed to the current [scale] — never a fresh
     * bilinear decode of the full source.
     */
    fun foldBase(scale: Float = 1f): Bitmap? {
        if (scale >= 1f) return firstFullBase()
        return cachedBaseDecode(scale)
    }

    /**
     * Source footprint (bounds only — no pixel decode), cached per photo.
     *
     * Reports the dims the fold base will ACTUALLY have: the bounded, EXIF-
     * oriented decode ([decodeFromUri]/[decodeBoundedOriented]) capped at
     * [SEGMENT_SOURCE_MAX_DIM], not the raw header dims. Seeding the preview
     * scale from the raw header (e.g. 4000×3000) while the base caps at
     * SEGMENT_SOURCE_MAX_DIM made the FIRST preview land at ~half its intended
     * resolution (soft/upscaled) until a later pass re-estimated after a decode
     * had populated the cache — and that wrong value stuck in the memoized
     * footprint.
     */
    fun sourceDims(uri: Uri?): Pair<Int, Int>? {
        if (cachedSourceWidth > 0 && cachedSourceHeight > 0) {
            return cachedSourceWidth to cachedSourceHeight
        }
        foldCacheOriginal?.let {
            cachedSourceWidth = it.width
            cachedSourceHeight = it.height
            return cachedSourceWidth to cachedSourceHeight
        }
        if (uri == null) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
        if (opts.outWidth > 0 && opts.outHeight > 0) {
            val orientation = runCatching {
                ExifOrientationReader.Default.read(context.contentResolver, uri)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val (w, h) = boundedOrientedDims(
                opts.outWidth, opts.outHeight, orientation, SEGMENT_SOURCE_MAX_DIM
            )
            cachedSourceWidth = w
            cachedSourceHeight = h
            return cachedSourceWidth to cachedSourceHeight
        }
        return null
    }

    /**
     * Estimates the FULL-SIZE output footprint of folding [effects] from the
     * pristine base — the source decode, or the merged bitmap after Merge
     * Layers — by prefix-folding each effect's [StackEffect.estimateOutputSize]
     * (integer math only, never a pixel fold or bitmap allocation). This
     * footprint replaces the original decode dims in the preview-resolution
     * decision (docs/effect_stack.md → *Choosing preview resolution*): a crop
     * shrinks the composite footprint so the multiplier grows (the preview stays
     * sharp at the same viewport), a transform mirrors its swap/grow, and a crop
     * is normalized against the already rotated/transformed composite exactly as
     * in the fold renderer. Memoized by the effect-list signature so steady
     * refresh loops don't re-walk the stack, and reset whenever the fold cache
     * resets ([clearFootprintMemo]).
     */
    fun estimateStackFootprint(
        effects: List<StackEffect>,
        startDims: () -> Pair<Int, Int>?
    ): Pair<Int, Int>? {
        val signature = effects.joinToString("|") { effectFingerprint(it) }
        if (estimatedFootprintSignature != signature) {
            val footprint = estimateStackFootprintUncached(effects, startDims) ?: return null
            estimatedFootprintSignature = signature
            estimatedFootprint = footprint
        }
        return estimatedFootprint
    }

    /** Performs the estimator fold — one integer pass per effect, no pixels. */
    private fun estimateStackFootprintUncached(
        effects: List<StackEffect>,
        startDims: () -> Pair<Int, Int>?
    ): Pair<Int, Int>? {
        val start = startDims() ?: return null
        return estimateStackOutputSize(effects, start.first, start.second)
    }

    /** Drops every preview-bucket downscale (no recycling — see [clearOriginalCaches]). */
    fun clearScaleBucketOriginalCache() {
        previewOriginalScaleCache.clear()
    }

    /**
     * Drops every bitmap reference the planner owns (original decode, the
     * `firstFull`/`firstPreview` bases, the scale-bucketed downscales and the
     * cached source dims) WITHOUT recycling: an in-flight render pass may still
     * be mid-read on any of them, and the same drop-without-recycle rule the
     * ViewModel's full/preview caches follow applies (GC reclaims the pixels
     * once the running passes release them).
     */
    fun clearOriginalCaches() {
        foldCacheOriginal = null
        firstFullBitmap = null
        firstPreviewBitmap = null
        previewOriginalScaleCache.clear()
        cachedSourceWidth = 0
        cachedSourceHeight = 0
    }

    /** Resets the estimate memo so the next [estimateStackFootprint] re-walks the stack. */
    fun clearFootprintMemo() {
        estimatedFootprintSignature = null
        estimatedFootprint = null
    }

    private fun decodeFromUri(): Bitmap? {
        val uri = imageUri() ?: return null
        return runCatching {
            // Never decode the photo at full resolution just to segment it: the
            // mask is upscaled to the working bitmap afterwards anyway, and a
            // full-size transient copy risks an OOM abort on low-memory devices.
            // Bakes EXIF orientation identically to the editor-entry decode so
            // the segment source matches the oriented original's geometry.
            decodeBoundedOriented(context.contentResolver, uri, SEGMENT_SOURCE_MAX_DIM)
        }.getOrNull()
    }

    companion object {
        const val PREVIEW_MARGIN = 1.25f
        const val SEGMENT_SOURCE_MAX_DIM = 2048
    }
}