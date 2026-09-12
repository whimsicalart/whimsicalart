package codes.pepper.whimsicalart.feature.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The AdjustmentSlider readout ([sliderReadout]) for the bare and percent ranges. */
class AdjustmentSliderReadoutTest {

    @Test
    fun `a plain value prints its integer`() {
        assertEquals("-100", sliderReadout(-100f, isPercent = false))
        assertEquals("42", sliderReadout(42.4f, isPercent = false))
        assertEquals("100", sliderReadout(100f, isPercent = false))
    }

    @Test
    fun `a percent value prints a live figure over the unchanged backend`() {
        assertEquals("0%", sliderReadout(0f, isPercent = true))
        assertEquals("70%", sliderReadout(0.7f, isPercent = true))
        assertEquals("100%", sliderReadout(1f, isPercent = true))
    }

    @Test
    fun `the percent readout rounds to the nearest whole on drag`() {
        assertEquals("7%", sliderReadout(0.071f, isPercent = true))
        assertEquals("99%", sliderReadout(0.999f, isPercent = true))
    }
}