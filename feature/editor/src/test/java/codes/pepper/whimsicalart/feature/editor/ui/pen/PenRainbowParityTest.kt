package codes.pepper.whimsicalart.feature.editor.ui.pen

import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rule 2 (docs/effect_stack.md → Preview rendering model): the live SOLID/RAINBOW
 * stand-in and the committed raster must advance the hue at the SAME pace. The
 * live overlay now draws the committed rasterisation directly
 * ([StrokeOverlayRenderer.penStrokeRaster] via drawPenRasterStroke), so the hue
 * cycle and stamp pacing are shared by construction — these helpers pin the
 * documented sample-rate rule it rests on.
 */
class PenRainbowParityTest {

    @Test
    fun `hue advances eighteen degrees per sampled stamp like the committed raster`() {
        assertEquals(0f, rainbowHueDegrees(0))
        assertEquals(18f, rainbowHueDegrees(1))
        assertEquals(36f, rainbowHueDegrees(2))
        assertEquals(342f, rainbowHueDegrees(19))
        assertEquals(0f, rainbowHueDegrees(20)) // 360 wraps to 0
        assertEquals(18f, rainbowHueDegrees(101))
    }

    @Test
    fun `overlay stamp pacing stays within one stamp of the committed sample pass`() {
        // The committed raster samples at radius/2 with a roundToInt step count
        // (StrokeOverlayRenderer.kt sampleAlong); the documented overlay rule ceils
        // it. They must never drift apart — and agree exactly whenever the pacing
        // divides evenly.
        val rasterCount = { distPx: Float, radiusPx: Float ->
            (distPx / (radiusPx * 0.5f)).roundToInt().coerceAtLeast(1) + 1
        }
        var checked = 0
        for (radius in listOf(4f, 5f, 8f, 10f, 20f)) {
            val spacing = radius * 0.5f
            for (dist in 0..400) {
                val d = dist.toFloat()
                val overlay = penSegmentSampleCount(d, radius)
                val raster = rasterCount(d, radius)
                assertTrue(
                    "overlay=$overlay raster=$raster dist=$d radius=$radius",
                    abs(overlay - raster) <= 1
                )
                if (d % spacing == 0f) {
                    assertEquals("divisible pacing must match exactly, d=$d r=$radius", raster, overlay)
                }
                checked++
            }
        }
        assertTrue(checked > 2000)
    }
}