package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.graphics.Bitmap
import android.graphics.Color
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * Shared soft-edge band used by both background passes ([BackgroundReplacer] and
 * BokehProcessor's Blur mode) so the subject boundary fades identically in
 * Replace and Blur: for a few pixels inward from the mask path the background —
 * the backdrop or the blurred copy — fades into the sharp subject edge instead
 * of hard-switching at the mask threshold.
 */
object SoftEdgeBand {

    /** Default width of the inward fading band, in mask pixels (a few pixels). */
    const val DEFAULT_WIDTH = 4

    /** How many freshly computed [depthMap] results to retain. */
    private const val DEPTH_CACHE_CAPACITY = 4

    /**
     * Memoized [depthMap] results, keyed by the mask's identity and the
     * parameter pair. The 4-pass simultaneous erosion over the whole mask is the
     * single most expensive artefact in the background replace/blur previews, and
     * it is recomputed every placement drag tick even though the segmented mask
     * reference does NOT change during the gesture (only the placement transform
     * does). Retaining the result per (mask, threshold, width) makes the hot drag
     * path pure lookups.
     *
     * The mask is the WEAK key (Bitmap does not override equals/hashCode, so the
     * map keys by identity): a cleared segmentation's entry is collected with the
     * mask instead of pinning a full-res bitmap for the life of the process. The
     * result array is never mutated after publication, so concurrent readers
     * share it safely. Access is synchronized (the singleton is reachable from
     * the composite-fold and preview-look Default threads at once).
     */
    private data class DepthParam(val maskThreshold: Int, val bandWidth: Int)

    private val depthCache = WeakHashMap<Bitmap, MutableMap<DepthParam, IntArray>>()

    /**
     * Distance, in mask pixels, of each subject pixel from the subject's
     * boundary, measured inward and clamped to [bandWidth]: 0 on the boundary
     * (adjoining the background), [bandWidth] for the subject core. Non-subject
     * pixels (mask alpha below [maskThreshold]) read 0 too, so callers can use
     * the same index for the threshold switch and the band ramp.
     *
     * Cheap iterative morphological erosion: a pixel survives one step when all
     * four neighbours still are subject, so a boundary pixel lands on depth 0
     * and the core clamps at [bandWidth].
     */
    fun depthMap(mask: Bitmap, maskThreshold: Int, bandWidth: Int): IntArray {
        val params = DepthParam(maskThreshold, bandWidth)
        synchronized(depthCache) {
            depthCache[mask]?.get(params)?.let { return it }
        }
        val computed = computeDepthMap(mask, maskThreshold, bandWidth)
        synchronized(depthCache) {
            var byMask = depthCache[mask]
            if (byMask == null || byMask.size >= DEPTH_CACHE_CAPACITY) {
                byMask = mutableMapOf()
                depthCache[mask] = byMask
            }
            byMask[params] = computed
        }
        return computed
    }

    private fun computeDepthMap(mask: Bitmap, maskThreshold: Int, bandWidth: Int): IntArray {
        val w = mask.width.coerceAtLeast(1)
        val h = mask.height.coerceAtLeast(1)
        val n = w * h
        val maskPixels = IntArray(n)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)
        val subject = BooleanArray(n)
        for (i in 0 until n) {
            subject[i] = Color.alpha(maskPixels[i]) >= maskThreshold
        }
        val depth = IntArray(n)
        var prev = subject
        for (step in 1..bandWidth) {
            val cur = BooleanArray(n)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    // Neighbours outside the mask grid are clamped onto the grid
                    // (the subject extends beyond the image edge), so a flank
                    // touching the image border never erodes.
                    cur[i] = prev[i] &&
                        prev[(y * w) + (x - 1).coerceIn(0, w - 1)] &&
                        prev[(y * w) + (x + 1).coerceIn(0, w - 1)] &&
                        prev[((y - 1).coerceIn(0, h - 1)) * w + x] &&
                        prev[((y + 1).coerceIn(0, h - 1)) * w + x]
                }
            }
            for (i in 0 until n) {
                if (cur[i]) depth[i] = step
            }
            prev = cur
        }
        return depth
    }

    /**
     * Linear mix of [fg] toward [bg] by the fraction [t] of [fg] kept
     * (0 → [bg], 1 → [fg]); each channel rounds independently.
     */
    fun blend(fg: Int, bg: Int, t: Float): Int {
        val a = (Color.alpha(fg) * t + Color.alpha(bg) * (1 - t)).roundToInt()
        val r = (Color.red(fg) * t + Color.red(bg) * (1 - t)).roundToInt()
        val g = (Color.green(fg) * t + Color.green(bg) * (1 - t)).roundToInt()
        val b = (Color.blue(fg) * t + Color.blue(bg) * (1 - t)).roundToInt()
        return Color.argb(
            a.coerceIn(0, 255), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)
        )
    }
}