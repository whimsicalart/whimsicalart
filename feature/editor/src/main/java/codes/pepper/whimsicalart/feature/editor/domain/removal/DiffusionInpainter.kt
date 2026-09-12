package codes.pepper.whimsicalart.feature.editor.domain.removal

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/**
 * Deterministic, pure-JVM classical content-aware fill: peels the painted region
 * inward from its boundary, overwriting each mask pixel with the average of its
 * already-known (unmasked) neighbours. The fill colours therefore propagate from
 * the surrounding background into the hole, so a brushed object is replaced by
 * plausible adjacent content without a hard blur halo.
 *
 * The onion-peel pass is bounded to the mask's bounding rectangle instead of the
 * whole image, so [inpaint] cost scales with the hole's own footprint — never the
 * canvas — and [fillRegion] lets the removal layer resolve EACH stroke against the
 * canvas that already has the earlier strokes baked in (only the newly brushed
 * region is ever touched). Masks are passed as row-major alpha ARGB [IntArray]s —
 * the stamping halves of the pipeline batch the mask once instead of touching it
 * through per-pixel [Bitmap] get/set, so a stroke is never blocked on JNI calls.
 *
 * No Android runtime beyond [Bitmap], so it is fully unit-testable under
 * Robolectric (unlike a native learned inpainting model, which would be slotted
 * in behind [InpaintSegmenter] instead).
 */
object DiffusionInpainter : InpaintSegmenter {

    /** Iteration + scan accounting for a [fillRegion] pass (perf assertions). */
    data class ScanStats(
        val iterations: Int,
        val scannedPixels: Long,
        val filledPixels: Long
    )

    /**
     * Whole-image inpaint over the [mask]'s full bounding box. Returns a fresh
     * bitmap copy with the masked pixels filled; the [mask]'s (tight) bounding
     * box is computed from the alpha channel so pixels outside it are "known"
     * and never revisited.
     */
    override fun inpaint(source: Bitmap, mask: Bitmap): Bitmap? {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val maskPixels = IntArray(w * h)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)
        maskBounds(maskPixels, w, h)?.let { bounds ->
            fill(pixels, maskPixels, w, h, bounds)
        }
        // createBitmap(pixels, ...) always yields a mutable bitmap, which sidesteps
        // the immutable-copy pitfall for non-ARGB_8888 sources under Robolectric.
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * In-place fill of [pixels] restricted to the caller-supplied [x0,y0,x1,y1)
     * rectangle: every pixel OUTSIDE the rectangle (and every inside pixel whose
     * mask alpha is below the threshold) is treated as already-known source
     * material, so only the branded region is overwritten. [mask] is the canvas'
     * row-major ARGB data ([w]×[h]; alpha ≥ [MASK_THRESHOLD] marks the hole).
     * Returns the pass [ScanStats].
     */
    fun fillRegion(
        pixels: IntArray,
        mask: IntArray,
        w: Int,
        h: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int
    ): ScanStats {
        val bounds = Rect(
            x0.coerceIn(0, w),
            y0.coerceIn(0, h),
            x1.coerceIn(0, w),
            y1.coerceIn(0, h)
        )
        if (bounds.left >= bounds.right || bounds.top >= bounds.bottom) {
            return ScanStats(0, 0, 0)
        }
        return fill(pixels, mask, w, h, bounds)
    }

    /**
     * Bounding rectangle (inclusive pixel indices) of every masked pixel
     * (alpha >= [MASK_THRESHOLD]), or null when nothing is masked.
     */
    private fun maskBounds(mask: IntArray, w: Int, h: Int): Rect? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            var row = y * w
            for (x in 0 until w) {
                if (Color.alpha(mask[row]) >= MASK_THRESHOLD) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                row++
            }
        }
        if (maxX < 0) return null
        return Rect(minX, minY, maxX + 1, maxY + 1)
    }

    private fun fill(
        pixels: IntArray,
        mask: IntArray,
        w: Int,
        h: Int,
        bounds: Rect
    ): ScanStats {
        // needs[c] is true while the pixel still has to be filled. Only the
        // bounding rectangle is ever consulted; pixels outside it are "known".
        val needs = BooleanArray(w * h)
        var filledPixels = 0L
        for (y in bounds.top until bounds.bottom) {
            var row = y * w
            for (x in bounds.left until bounds.right) {
                if (Color.alpha(mask[row + x]) >= MASK_THRESHOLD) {
                    needs[row + x] = true
                    filledPixels++
                }
            }
        }
        if (filledPixels == 0L) return ScanStats(0, 0, 0)

        // Reused across layers: one boundary buffer sized to the region (never
        // the canvas), re-stamped each pass rather than re-allocated.
        val boundaryX = IntArray((bounds.right - bounds.left) * (bounds.bottom - bounds.top))
        val boundaryY = IntArray((bounds.right - bounds.left) * (bounds.bottom - bounds.top))

        var iteration = 0
        var scannedPixels = 0L
        while (iteration < MAX_ITERATIONS) {
            var any = false
            // First collect the boundary pixels so every layer reads the previous
            // state rather than partially-filled neighbours within the same pass.
            var count = 0
            for (y in bounds.top until bounds.bottom) {
                val row = y * w
                for (x in bounds.left until bounds.right) {
                    val idx = row + x
                    if (!needs[idx]) continue
                    scannedPixels++
                    if (hasKnownNeighbour(needs, x, y, w, h)) {
                        boundaryX[count] = x
                        boundaryY[count] = y
                        count++
                    }
                }
            }
            if (count == 0) break
            for (c in 0 until count) {
                val x = boundaryX[c]
                val y = boundaryY[c]
                pixels[y * w + x] = averageKnownNeighbours(pixels, needs, x, y, w, h)
                needs[y * w + x] = false
                any = true
            }
            if (!any) break
            iteration++
        }
        return ScanStats(iteration, scannedPixels, filledPixels)
    }

    private fun hasKnownNeighbour(
        needs: BooleanArray,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ): Boolean {
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until w && ny in 0 until h && !needs[ny * w + nx]) return true
            }
        }
        return false
    }

    private fun averageKnownNeighbours(
        pixels: IntArray,
        needs: BooleanArray,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val idx = ny * w + nx
                if (needs[idx]) continue
                val c = pixels[idx]
                r += Color.red(c)
                g += Color.green(c)
                b += Color.blue(c)
                count++
            }
        }
        if (count == 0L) return pixels[y * w + x]
        return Color.rgb(
            (r / count).toInt(),
            (g / count).toInt(),
            (b / count).toInt()
        )
    }

    private const val MASK_THRESHOLD = 128
    private const val MAX_ITERATIONS = 200
}