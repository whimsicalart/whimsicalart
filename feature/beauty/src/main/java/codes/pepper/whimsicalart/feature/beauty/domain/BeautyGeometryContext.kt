package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Produces a [BeautyGeometry] for a given image state. Decoupled from the
 * concrete ML pipeline so [BeautyGeometryContext] is unit-testable with a fake
 * generator (the on-device face-detection + face-mesh + skin/hair segmentation
 * cannot run under Robolectric). The optional [maxPixelCountHint] forwards the
 * geometry track's predicted full-size pixel count to the resolver so the
 * adaptive ML input resolution is evaluated against the FINAL content footprint (see
 * [codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryResolver]);
 * fakes and callers that only have [image] may leave it null.
 *
 * [neededFields] is the per-field lazy contract (docs/effect_stack.md → Shared
 * geometry context): the generate runs ONLY the ML stages for the requested
 * fields, against the caller's [image]. The context uses it for incremental
 * resolution — a missing field against an otherwise-valid base is computed on
 * its own, then merged into the retained geometry, so the other fields never
 * re-run.
 */
fun interface BeautyGeometryGenerator {
    suspend fun generate(
        image: Bitmap,
        maxPixelCountHint: Int?,
        neededFields: Set<GeometryField>
    ): BeautyGeometry

    /** Convenience overload — full resolve (every field), no prefix-fold hint. */
    suspend fun generate(image: Bitmap): BeautyGeometry = generate(image, null, GeometryField.ALL)

    /** Convenience overload when no prefix-fold pixel count is available. */
    suspend fun generate(image: Bitmap, maxPixelCountHint: Int?): BeautyGeometry =
        generate(image, maxPixelCountHint, GeometryField.ALL)
}

/**
 * The editor's SHARED, lazily-resolved beauty geometry context.
 *
 * The editor owns one instance and hands it to every beauty effect, but the
 * editor does NOT read its data. Its only obligations on this interface are
 * two explicit operations:
 *
 *  - [markStale]: call when an effect that DEFORMS the image is applied (rotate
 *    CW/CCW, H/V flip, crop — and geometry-affecting beauty ops such as reshape),
 *    so the next lazy generation produces a fresh geometry against the deformed
 *    image rather than reusing a geometry that no longer matches the pixel space.
 *  - [flatten]: call when merging layers.
 *
 * The context holds exactly one piece of geometry state: a **single last
 * geometry** together with the set of **fields already resolved on it**, a
 * single [stale] flag, the **retained geometry base bitmap** the last geometry
 * was generated against (plus its dimensions), and the **base instance**. The
 * resolution guard therefore regenerates when the last geometry is missing, the
 * stale flag is set, the base's **dimensions** changed (a preview-resolution
 * fold never reuses geometry produced for the full-size image, and vice-versa),
 * or the caller passes a **different base instance** than the one the last
 * geometry was traced from — which the geometry track uses as its "the gap
 * changed" signal: an unchanged segment keeps the SAME base instance across fold
 * passes (reuse), and any change to the deforming/occluding effects upstream
 * produces a new instance (regenerate). All of this is decided here — the editor
 * never picks REUSE vs RESOLVE.
 *
 * When the base is valid but the caller asks for a **field the last geometry
 * does not yet carry**, only that missing field is resolved against the retained
 * base bitmap (per-field lazy resolution) and merged in — the present fields and
 * the base itself never re-derive, so adding a hair-color layer to an already
 * resolved segment pays only the hair ML stage.
 *
 * There is deliberately **no list**: old [BeautyGeometry] instances live on only
 * as long as the layer that carries them; once that layer is regenerated the
 * value drops out of use and JVM GC frees it, so the context never needs to
 * accumulate a history to reclaim memory.
 *
 * Geometry generation is LAZY and owned by the beauty side (via [geometryFor]):
 * when an effect that needs geometry is applied, it selects the last geometry; if
 * there is no last geometry yet OR the guard (stale / dimensions / base
 * instance) says the last one no longer matches, it lazily generates a
 * new one (via the [BeautyGeometryGenerator]) against the passed base, replaces
 * the last-geometry slot, the base reference and clears the stale flag. Whatever
 * geometry is selected (reused or freshly generated) is what the effect stores
 * as its own render parameter, so a later fold needs no geometry decision-making
 * of its own.
 */
interface BeautyGeometryContext {
    /** Marks the last geometry stale; the next [geometryFor] will regenerate. */
    fun markStale()

    /** Flattens / settles the shared geometry (called by the editor on merge). */
    fun flatten()

    /**
     * Lazily resolves the geometry fields [neededFields] for [image]: returns
     * the last previously-used geometry (or grows it with the fields the caller
     * now needs), or — if there is no last geometry yet or that last geometry is
     * stale / was generated against a different base / different dimensions —
     * lazily generates the requested fields, replaces the last-geometry slot and
     * the base reference, and clears the stale flag. [maxPixelCountHint] is
     * forwarded to the generator (see [BeautyGeometryGenerator]).
     */
    suspend fun geometryFor(
        image: Bitmap,
        maxPixelCountHint: Int?,
        neededFields: Set<GeometryField>
    ): BeautyGeometry

    /** Convenience overload — full resolve (every field), no prefix-fold hint. */
    suspend fun geometryFor(image: Bitmap): BeautyGeometry = geometryFor(image, null, GeometryField.ALL)

    /** Convenience overload when no prefix-fold pixel count is available. */
    suspend fun geometryFor(image: Bitmap, maxPixelCountHint: Int?): BeautyGeometry =
        geometryFor(image, maxPixelCountHint, GeometryField.ALL)

    /** The most recently generated geometry (non-suspend peek); null if none. */
    fun lastGeometry(): BeautyGeometry?
}

/** Production [BeautyGeometryContext]: retained base + one last geometry + stale flag + per-field presence, lazy generation. */
class DefaultBeautyGeometryContext(
    private val generator: BeautyGeometryGenerator
) : BeautyGeometryContext {

    private val generationMutex = Mutex()

    @Volatile
    private var last: BeautyGeometry? = null
    /** The per-field presence flags of [last] — which ML stages already ran. */
    private val lastFields = mutableSetOf<GeometryField>()
    @Volatile
    private var stale = false
    /**
     * The RETAINED geometry base the last geometry was generated against. It is
     * a REFERENCE to the caller's fold base (owned by the geometry track, which
     * recycles it under its own lock) — the context NEVER recycles it; releasing
     * (see [releaseRetainedBase]) only drops the reference so incremental
     * per-field computation cannot keep re-deriving from a stale/base pair.
     */
    private var retainedBase: Bitmap? = null
    private var lastWidth = 0
    private var lastHeight = 0

    override fun markStale() {
        // Volatile so a geometry-changing edit landing on another thread while a
        // fold coroutine is generating is never permanently lost (the next
        // geometryFor reads it under the generation mutex).
        stale = true
        releaseRetainedBase()
    }

    override fun flatten() {
        // Flattening bakes the merged result; the merged layer re-uses the last
        // geometry for re-edit ([lastGeometry]). The retained base however is
        // released: nothing may keep incrementally resolving against a base the
        // merged stack no longer renders over.
        releaseRetainedBase()
    }

    private fun releaseRetainedBase() {
        // Only reference-release: the base is editor/track-owned (recycled under
        // geometryTrackLock), so recycling it here would hand a concurrent fold a
        // recycled bitmap — the device-only silent-death mechanism. The retained
        // subjectMask bitmap (if the context generated one) lives ON the last
        // geometry and is released with it on regeneration.
        retainedBase = null
        lastWidth = 0
        lastHeight = 0
    }

    override suspend fun geometryFor(
        image: Bitmap,
        maxPixelCountHint: Int?,
        neededFields: Set<GeometryField>
    ): BeautyGeometry {
        // A reuse is only sound when the last geometry was generated against the
        // SAME base instance at the SAME resolution as the caller's current base:
        // geometry coordinates live in the base's pixel space, so (a) a
        // preview-resolution fold never reuses a geometry produced for the
        // full-size image (and vice-versa) — the dims guard — and (b) any change
        // to the upstream deforming/occluding effects yields a new base instance,
        // so the previous geometry no longer matches the pixel content — the
        // identity guard. Both, plus the stale flag, force regeneration. When the
        // base is valid but the request adds fields the retained geometry lacks,
        // ONLY those fields re-resolve against the retained base (per-field lazy
        // resolution) and are merged in — the present fields and the base never
        // re-derive.
        //
        // The whole check-and-generate runs under ONE mutex so the generator's
        // native ML pipeline (a FRESH FaceDetectorManager + MediaPipe face-mesh
        // + hair/skin segmenters per generate — docs: Beauty tools terminate
        // silently on a real device) can never run TWICE concurrently from the
        // editor's overlapping fold coroutines (composite / layer-previews /
        // full-resolution), which doubles the transient memory spike and can kill
        // a fold with a recycled bitmap. Serializing every generation bounds the
        // peak at a single pipeline's working set.
        return generationMutex.withLock {
            val dimsMatch = lastWidth == image.width && lastHeight == image.height
            val sameBase = retainedBase === image
            if (last == null || stale || !dimsMatch || !sameBase) {
                val generated = generator.generate(image, maxPixelCountHint, neededFields)
                last = generated
                retainedBase = image
                lastWidth = image.width
                lastHeight = image.height
                lastFields.clear()
                lastFields += neededFields
                stale = false
            } else {
                val missing = neededFields - lastFields
                if (missing.isNotEmpty()) {
                    val partial = generator.generate(image, maxPixelCountHint, missing)
                    last = last!!.mergedWith(partial)
                    lastFields += missing
                }
            }
            last ?: error("geometryFor produced no geometry")
        }
    }

    override fun lastGeometry(): BeautyGeometry? = last
}
