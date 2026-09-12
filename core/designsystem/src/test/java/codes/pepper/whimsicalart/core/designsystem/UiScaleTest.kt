package codes.pepper.whimsicalart.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [computeUiScale]: an orientation-aware, window-anchored chrome scale with NO
 * device-dimension term. Portrait anchors 320x640, landscape 912x432, keyed on
 * `winWidth < winHeight`; the same `min` over both window axes (a split halves
 * ONE axis, so it always trips the scale); floored at 0.6, capped at 1.0.
 *
 * Spec examples (docs/effects_reference.md): on a 437×972 dp device a
 * half-height portrait split gives `min(437/320, 486/640) ≈ 0.76`.
 */
class UiScaleTest {

    private fun assertScale(
        expected: Float,
        width: Float,
        height: Float,
        epsilon: Float = 0.0001f
    ) {
        assertEquals("window ${width}x$height", expected, computeUiScale(width, height), epsilon)
    }

    @Test
    fun fullPhonePortraitWindowIsUnscaled() {
        // 392.7x872.7dp portrait meets both anchors -> 1.0.
        assertScale(1.0f, width = 392.7f, height = 872.7f)
    }

    @Test
    fun fullPhoneLandscapeWindowIsLargelyUnscaled() {
        // 872.7x392.7dp landscape: min(392.7/432, 872.7/912) ~ 0.909.
        assertScale(0.9092f, width = 872.7f, height = 392.7f, epsilon = 0.0005f)
    }

    @Test
    fun halfHeightPortraitSplitMatchesTheSpecExample() {
        // 437x972dp device half-height split -> window 437x486, portrait:
        // min(437/320, 486/640) = 0.7594 (the spec's ~0.76 example).
        assertScale(0.7594f, width = 437f, height = 486f, epsilon = 0.0005f)
    }

    @Test
    fun halfWidthPortraitSplitTripsTheScale() {
        // half-width split -> 218.5x972: min(218.5/320, 972/640) = 0.6828,
        // above the 0.6 floor but well below 1.0.
        assertScale(0.6828f, width = 218.5f, height = 972f, epsilon = 0.0005f)
    }

    @Test
    fun extremeSplitFloorsAtZeroPointSix() {
        assertScale(0.6f, width = 100f, height = 972f)
    }

    @Test
    fun anyAxisBelowItsAnchorTripsTheScale() {
        // Width exactly the anchor, height slightly under: min(1.0, 636/640).
        assertScale(0.99375f, width = 320f, height = 636f)
        // Height exactly the anchor, width slightly under: min(316/320, 1.0).
        assertScale(0.9875f, width = 316f, height = 640f)
    }

    @Test
    fun landscapeUsesItsOwnAnchors() {
        // Landscape window 912x432 exactly -> 1.0.
        assertScale(1.0f, width = 912f, height = 432f)
        // Landscape split halves the height: 912x216 -> min(216/432, 1.0)=0.5 -> 0.6.
        assertScale(0.6f, width = 912f, height = 216f)
        // Slightly short in the short axis: 400/432.
        assertScale(0.9259f, width = 912f, height = 400f, epsilon = 0.0005f)
    }

    @Test
    fun squareWindowUsesLandscapeAnchors() {
        // winWidth == winHeight: not portrait, so landscape anchors.
        assertScale(0.6579f, width = 600f, height = 600f, epsilon = 0.0005f)
        assertScale(0.6f, width = 200f, height = 200f)
    }

    @Test
    fun degenerateWindowDimsDefaultToOne() {
        assertEquals(1.0f, computeUiScale(0f, 200f), 0.0001f)
        assertEquals(1.0f, computeUiScale(100f, 0f), 0.0001f)
        assertEquals(1.0f, computeUiScale(-10f, 200f), 0.0001f)
    }

    @Test
    fun hugeWindowNeverUpscales() {
        assertScale(1.0f, width = 1200f, height = 2400f)
    }
}