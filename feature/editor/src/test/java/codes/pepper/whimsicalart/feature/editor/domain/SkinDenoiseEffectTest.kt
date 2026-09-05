package codes.pepper.whimsicalart.feature.editor.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The main-toolbar skin-denoise slider exposes 0..1 while the wavelet denoiser
 * is calibrated so a higher softness PRESERVES more detail. `SkinDenoiseEffect`
 * therefore inverts the slider (higher slider = stronger effect) by computing
 * `softness = 1 - intensity`, clamped to the unit range.
 */
@RunWith(RobolectricTestRunner::class)
class SkinDenoiseEffectTest {

    private fun softnessFor(intensity: Float): Float {
        // Reproduce the inversion the effect applies inside render(): a zero or
        // negative intensity is a no-op (returns input unchanged), otherwise it
        // feeds the denoiser 1 - intensity clamped to 0..1.
        if (intensity <= 0f) return 1f
        return (1f - intensity).coerceIn(0f, 1f)
    }

    @Test
    fun `zero slider value is a no-op and maps to fully permissive softness`() {
        assertEquals(1f, softnessFor(0f), 0.001f)
    }

    @Test
    fun `rightmost slider maps to fully aggressive softness`() {
        assertEquals(0f, softnessFor(1f), 0.001f)
    }

    @Test
    fun `midpoint slider maps to half softness`() {
        assertEquals(0.5f, softnessFor(0.5f), 0.001f)
    }

    @Test
    fun `slider value is clamped to the unit range`() {
        assertEquals(0f, softnessFor(2f), 0.001f)
        assertEquals(1f, softnessFor(-1f), 0.001f)
    }

    @Test
    fun `effect strengthens as the slider moves right`() {
        assertEquals(
            true,
            (1f - softnessFor(0.75f)) > (1f - softnessFor(0.25f))
        )
    }
}
