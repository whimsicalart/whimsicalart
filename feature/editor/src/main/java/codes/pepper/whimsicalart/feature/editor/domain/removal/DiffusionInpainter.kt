package codes.pepper.whimsicalart.feature.editor.domain.removal

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Deterministic, pure-JVM classical content-aware fill: peels the painted region
 * inward from its boundary, overwriting each mask pixel with the average of its
 * already-known (unmasked) neighbours. The fill colours therefore propagate from
 * the surrounding background into the hole, so a brushed object is replaced by
 * plausible adjacent content without a hard blur halo.
 *
 * No Android runtime beyond [Bitmap], so it is fully unit-testable under
 * Robolectric (unlike a native learned inpainting model, which would be slotted
 * in behind [InpaintSegmenter] instead).
 */
object DiffusionInpainter : InpaintSegmenter {

    override fun inpaint(source: Bitmap, mask: Bitmap): Bitmap? {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        fill(pixels, mask, w, h)
        // createBitmap(pixels, ...) always yields a mutable bitmap, which sidesteps
        // the immutable-copy pitfall for non-ARGB_8888 sources under Robolectric.
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun fill(pixels: IntArray, mask: Bitmap, w: Int, h: Int) {
        // needs[c] is true while the pixel still has to be filled.
        val needs = BooleanArray(w * h)
        rangePixels(mask, w, h) { x, y ->
            needs[y * w + x] = Color.alpha(mask.getPixel(x, y)) >= MASK_THRESHOLD
        }

        var iteration = 0
        while (iteration < MAX_ITERATIONS) {
            var any = false
            // First collect the boundary pixels so every layer reads the previous
            // state rather than partially-filled neighbours within the same pass.
            val boundaryX = IntArray(w * h)
            val boundaryY = IntArray(w * h)
            var count = 0
            for (idx in needs.indices) {
                if (!needs[idx]) continue
                val x = idx % w
                val y = idx / w
                if (hasKnownNeighbour(needs, x, y, w, h)) {
                    boundaryX[count] = x
                    boundaryY[count] = y
                    count++
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

    private inline fun rangePixels(
        mask: Bitmap,
        w: Int,
        h: Int,
        block: (x: Int, y: Int) -> Unit
    ) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                block(x, y)
            }
        }
    }

    private const val MASK_THRESHOLD = 128
    private const val MAX_ITERATIONS = 200
}