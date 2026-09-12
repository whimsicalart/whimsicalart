package codes.pepper.whimsicalart.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.min

/**
 * The orientation minimum-workable-window anchors the [uiScale] ratio is
 * computed against. They describe the smallest window where the editor fits at
 * scale 1.0 with a ~320 dp square letterbox, so the same rule holds on any
 * device and in multi-window.
 *
 * - **Portrait** (w < h): chrome = 64 top bar + 56 eye row + 200 controls
 *   reserve = 320 dp, plus a square letterbox whose side equals the width.
 *   `320 wide × 640 tall`.
 * - **Landscape** (w >= h): chrome = 600 dp controls panel + 120 dp (64 top bar
 *   + 56 eye row) + a square letterbox: `912 wide × 432 tall`.
 */
const val PORTRAIT_MIN_WINDOW_WIDTH_DP = 320f
const val PORTRAIT_MIN_WINDOW_HEIGHT_DP = 640f
const val LANDSCAPE_MIN_WINDOW_HEIGHT_DP = 432f
const val LANDSCAPE_MIN_WINDOW_WIDTH_DP = 912f

/**
 * Multiplier that the app's UI chrome multiplies its dp sizes by (top bars,
 * tool controls, eye row, sliders, presets, dialogs and their paddings/fonts),
 * so they stay usable in small windows (multi-window / split-screen). The real
 * value is computed ONCE at the app root from the measured WINDOW ([computeUiScale])
 * and provided for the whole app — editor, gallery, settings, camera, collage,
 * onboarding and every dialog/popup. Defaults to 1f (full-size window);
 * anything that is not chrome — the canvas itself, its letterbox geometry and
 * every canvas-space gesture/preview px mapping — must NOT read this.
 */
val LocalUiScale = staticCompositionLocalOf { 1f }

/**
 * Window-anchored UI chrome scale (docs/effects_reference.md → Canvas layout
 * and gesture scope). Unlike the former device-relative formula there is NO
 * device-dimension term: the ratio comes from the real available WINDOW sizes
 * in dp ([winWidthDp] × [winHeightDp], the two axes UNORDERED — orientation is
 * keyed on their relation, so un-mixed the same split resolves identically on
 * every device):
 *
 * - portrait (`winWidthDp < winHeightDp`):
 *   `uiScale = min(winWidth / 320.dp, winHeight / 640.dp)`
 * - landscape (`winWidthDp >= winHeightDp`):
 *   `uiScale = min(winHeight / 432.dp, winWidth / 912.dp)`
 *
 * floored at 0.6 (a half split must not collapse the UI) and capped at 1.0
 * (a window meeting both anchors never upscales). The `min` over both axes
 * means a split — which halves ONE axis — always trips the scale: on a
 * 437×972 dp device a half-height split gives `min(437/320, 486/640) ≈ 0.76`.
 * This must NOT influence the preview-resolution ceiling — that stays a
 * device-pixel bound (`RenderPipelinePlanner.ensurePreviewCeiling`), resilient
 * and agnostic of orientation and window split.
 */
fun computeUiScale(
    winWidthDp: Float,
    winHeightDp: Float
): Float {
    if (winWidthDp <= 0f || winHeightDp <= 0f) return 1f
    return if (winWidthDp < winHeightDp) {
        min(
            winWidthDp / PORTRAIT_MIN_WINDOW_WIDTH_DP,
            winHeightDp / PORTRAIT_MIN_WINDOW_HEIGHT_DP
        )
    } else {
        min(
            winHeightDp / LANDSCAPE_MIN_WINDOW_HEIGHT_DP,
            winWidthDp / LANDSCAPE_MIN_WINDOW_WIDTH_DP
        )
    }.coerceIn(0.6f, 1.0f)
}