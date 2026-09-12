package codes.pepper.whimsicalart.feature.editor.ui.mosaic

import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rule 2 (docs/effect_stack.md → Preview rendering model): the live pixel-mosaic
 * stand-in overlay and the committed raster must draw the SAME cell geometry at
 * every resolution. The overlay's box-px brush size is the raster's
 * `brushSize × surfaceWidth` at preview resolution, so calling both formulas
 * with the same numbers must give the same cell.
 */
class MosaicCellParityTest {

    /** The committed formula, copied from BitmapRenderer.kt:1412 (applyPixelMosaic). */
    private fun rasterCellPx(brushSizeNorm: Float, surfaceWidth: Int, surfaceHeight: Int): Int =
        (brushSizeNorm * surfaceWidth).roundToInt()
            .coerceAtLeast((min(surfaceWidth, surfaceHeight) / 140f).coerceAtLeast(1f).roundToInt())

    @Test
    fun `stand-in cell equals the committed raster cell at every resolution`() {
        val surfaces = listOf(
            500 to 500, 500 to 700, 800 to 600, 1000 to 667,
            2000 to 1500, 3000 to 2000, 4000 to 3000
        )
        val brushFractions = listOf(0.004f, 0.01f, 0.05f, 0.1f, 0.2f, 0.5f, 1f)
        for ((w, h) in surfaces) {
            for (frac in brushFractions) {
                val brushPx = (frac * w).roundToInt().toFloat()
                val overlay = pixelMosaicCellPx(brushPx, w.toFloat(), h.toFloat())
                val raster = rasterCellPx(frac, w, h)
                assertEquals("cell mismatch at ${w}x$h brush=<$frac>", raster, overlay)
            }
        }
    }

    @Test
    fun `live stand-in cells are no longer inflated forty percent`() {
        // Historical bug: the overlay drew `brushSize * 1.4` (+) a fixed 8px floor,
        // so a 10px box brush previewed as a 14px committed-looking cell.
        assertEquals(10, pixelMosaicCellPx(10f, 400f, 400f))
    }

    @Test
    fun `relative short-edge floor is shared with the committed pass`() {
        val w = 3000f
        val h = 2000f
        // A brush a hair below the floor still lands on the raster's 14px floor
        // (2000/140 ≈ 14.3 → 14), not the overlay's old independent 8px constant.
        val floorExpected = (min(w, h) / 140f).roundToInt()
        assertEquals(14, floorExpected)
        assertEquals(floorExpected, pixelMosaicCellPx(0.3f, w, h))
        assertEquals(14, rasterCellPx(0.0001f, w.toInt(), h.toInt()))
        assertEquals(pixelMosaicCellPx(0.3f, w, h), rasterCellPx(0.0001f, w.toInt(), h.toInt()))
    }
}