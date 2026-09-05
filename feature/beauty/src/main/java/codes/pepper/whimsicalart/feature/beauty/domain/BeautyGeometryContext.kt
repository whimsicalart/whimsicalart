package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap

/**
 * Produces a [BeautyGeometry] for a given image state. Decoupled from the
 * concrete ML pipeline so [BeautyGeometryContext] is unit-testable with a fake
 * generator (the on-device face-detection + face-mesh + skin/hair segmentation
 * cannot run under Robolectric).
 */
fun interface BeautyGeometryGenerator {
    suspend fun generate(image: Bitmap): BeautyGeometry
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
 * The context holds exactly three pieces of state: a **single last geometry**, a
 * single [stale] flag, and the **base instance the last geometry was generated
 * against** (plus its dimensions). The resolution guard therefore regenerates
 * when the last geometry is missing, the stale flag is set, the base's
 * **dimensions** changed (a preview-resolution fold never reuses geometry
 * produced for the full-size image, and vice-versa), or the caller passes a
 * **different base instance** than the one the last geometry was traced from —
 * which the geometry track uses as its "the gap changed" signal: an unchanged
 * segment keeps the SAME base instance across fold passes (reuse), and any
 * change to the deforming/occluding effects upstream produces a new instance
 * (regenerate). All of this is decided here — the editor never picks REUSE vs
 * RESOLVE.
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
     * Lazily resolves the geometry for [image]: returns the last previously-used
     * geometry, or — if there is no last geometry yet or that last geometry is
     * stale / was generated against a different base / different dimensions —
     * lazily generates a fresh one, replaces the last-geometry slot and the base
     * reference, and clears the stale flag.
     */
    suspend fun geometryFor(image: Bitmap): BeautyGeometry

    /** The most recently generated geometry (non-suspend peek); null if none. */
    fun lastGeometry(): BeautyGeometry?
}

/** Production [BeautyGeometryContext]: single last geometry + stale flag + base guard, lazy generation. */
class DefaultBeautyGeometryContext(
    private val generator: BeautyGeometryGenerator
) : BeautyGeometryContext {

    private var last: BeautyGeometry? = null
    private var stale = false
    private var lastBase: Bitmap? = null
    private var lastWidth = 0
    private var lastHeight = 0

    override fun markStale() {
        stale = true
    }

    override fun flatten() {
        // Flattening bakes the merged result; there is nothing else to settle at
        // the context level. The merged layer re-uses the last geometry for
        // re-edit, which [lastGeometry] already exposes.
    }

    override suspend fun geometryFor(image: Bitmap): BeautyGeometry {
        // A reuse is only sound when the last geometry was generated against the
        // SAME base instance at the SAME resolution as the caller's current base:
        // geometry coordinates live in the base's pixel space, so (a) a
        // preview-resolution fold never reuses a geometry produced for the
        // full-size image (and vice-versa) — the dims guard — and (b) any change
        // to the upstream deforming/occluding effects yields a new base instance,
        // so the previous geometry no longer matches the pixel content — the
        // identity guard. Both, plus the stale flag, force regeneration.
        val dimsMatch = lastWidth == image.width && lastHeight == image.height
        val sameBase = lastBase === image
        if (last == null || stale || !dimsMatch || !sameBase) {
            last = generator.generate(image)
            lastWidth = image.width
            lastHeight = image.height
            lastBase = image
            stale = false
        }
        return last ?: error("geometryFor produced no geometry")
    }

    override fun lastGeometry(): BeautyGeometry? = last
}
