package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Orientation-aware workspace split (docs/effects_reference.md →
 * "Canvas layout and gesture scope"):
 *
 *  (i)   landscape → canvas box width = [canvasWidth] on the left, controls in a
 *        right-side panel that always keeps ≥ CANVAS_CONTROLS_MIN_WIDTH;
 *  (ii)  the eye row sits directly below the canvas in BOTH orientations;
 *  (iii) portrait keeps the old column order (canvas → eye → toolbar → controls)
 *        with the canvas width always filling the container;
 *  (iv)  touches over the right-scrolling controls must never reach the canvas'
 *        gesture handlers, while taps on the canvas do.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric's default screen is only 320dp wide, which would clamp every
// fixed-size container — give the workspace tests a screen big enough to host
// a landscape canvas + ≥600dp controls panel (density mdpi keeps dp ↔ px 1:1).
@Config(qualifiers = "w1600dp-h1000dp-mdpi")
class EditorWorkspaceLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    companion object {
        private const val TAG_CANVAS = "canvasBox"
        private const val TAG_EYE = "eyeRow"
        private const val TAG_PANEL = "rightPanel"
    }

    private var canvasTaps = 0

    private fun sizeInRoot(tag: String) =
        composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun compose(
        isLandscape: Boolean,
        containerWidth: Dp,
        containerHeight: Dp,
        canvasWidth: Dp,
        canvasHeight: Dp
    ) {
        canvasTaps = 0
        composeRule.setContent {
            Box(Modifier.size(containerWidth, containerHeight)) {
                EditorWorkspaceLayout(
                    isLandscape = isLandscape,
                    canvasHeight = canvasHeight,
                    canvasWidth = canvasWidth,
                    canvasContentModifier = Modifier
                        .testTag(TAG_CANVAS)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { canvasTaps++ })
                        },
                    canvasContent = {},
                    eyeRow = { Box(Modifier.fillMaxWidth().height(24.dp).testTag(TAG_EYE)) },
                    controlsHeader = { Box(Modifier.fillMaxWidth().height(32.dp)) },
                    controls = { Box(Modifier.fillMaxWidth().height(48.dp).testTag(TAG_PANEL)) }
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun shouldPutCanvasOnTheLeftAndControlsInWideRightPanelWhenLandscape() {
        compose(
            isLandscape = true,
            containerWidth = 1400.dp,
            containerHeight = 800.dp,
            canvasWidth = 800.dp,
            canvasHeight = 700.dp
        )

        val canvas = sizeInRoot(TAG_CANVAS)
        val panel = sizeInRoot(TAG_PANEL)

        assertTrue("canvas box width must equal min(height, width − 600dp)", (canvas.right - canvas.left) == 800.dp)
        assertEquals("canvas must start at the left edge", 0.dp, canvas.left)
        assertTrue("right panel must sit to the right of the canvas", panel.left >= canvas.right)
        assertTrue(
            "right panel must keep at least 600dp of width",
            (panel.right - panel.left).value >= CANVAS_CONTROLS_MIN_WIDTH.value
        )
    }

    @Test
    fun shouldKeepEyeRowBelowCanvasInLandscape() {
        compose(
            isLandscape = true,
            containerWidth = 1400.dp,
            containerHeight = 800.dp,
            canvasWidth = 800.dp,
            canvasHeight = 700.dp
        )

        val canvas = sizeInRoot(TAG_CANVAS)
        val eye = sizeInRoot(TAG_EYE)

        assertTrue("eye row must be below the canvas", eye.top >= canvas.bottom)
    }

    @Test
    fun shouldKeepEyeRowBelowCanvasInPortrait() {
        compose(
            isLandscape = false,
            containerWidth = 600.dp,
            containerHeight = 800.dp,
            canvasWidth = 600.dp,
            canvasHeight = 600.dp
        )

        val canvas = sizeInRoot(TAG_CANVAS)
        val eye = sizeInRoot(TAG_EYE)
        val panel = sizeInRoot(TAG_PANEL)

        assertTrue("eye row must be below the canvas", eye.top >= canvas.bottom)
        assertTrue("controls must be below the eye row", panel.top >= eye.bottom)
        assertTrue("canvas must span the portrait container width", (canvas.right - canvas.left) == 600.dp)
    }

    @Test
    fun shouldNeverForwardTapsOverTheControlsToTheCanvas() {
        compose(
            isLandscape = true,
            containerWidth = 1400.dp,
            containerHeight = 800.dp,
            canvasWidth = 800.dp,
            canvasHeight = 700.dp
        )

        composeRule.onNodeWithTag(TAG_PANEL).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals("a tap over the right panel must not reach the canvas", 0, canvasTaps)

        composeRule.onNodeWithTag(TAG_CANVAS).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals("a tap on the canvas must reach its gesture handler", 1, canvasTaps)
    }

    @Test
    fun shouldCollapseLandscapeCanvasForNarrowAreas() {
        assertEquals(0.dp, landscapeCanvasWidth(500.dp, 800.dp))
    }

    @Test
    fun shouldKeepPortraitCanvasHeightFormula() {
        // eye row reserved first, then the controls minimum: 600 − 56 − 200 = 344.
        assertEquals(344.dp, portraitCanvasHeight(800.dp, 600.dp))
        // the width bound still binds beneath the (eye-row-adjusted) vertical budget.
        assertEquals(500.dp, portraitCanvasHeight(500.dp, 800.dp))
    }

    @Test
    fun shouldScaleThePortraitReserveWithUiScale() {
        // eye row + reserve collapse with uiScale: 600 − 56*0.6 − 200*0.6 = 446.4.
        assertEquals(446.4f, portraitCanvasHeight(800.dp, 600.dp, 0.6f).value, 0.001f)
        // the width bound still binds when beneath the scaled reserve.
        assertEquals(300.dp, portraitCanvasHeight(300.dp, 600.dp, 0.6f))
    }

    @Test
    fun portraitReserveShouldSurviveTheEyeRowAndControlsHeader() {
        // the eye row must be excluded BEFORE the controls reserve, so a short
        // window keeps the eye row (56) plus a full 200 dp for the tool bar +
        // controls below it.
        assertEquals(344.dp, portraitCanvasHeight(800.dp, 600.dp))
        // a taller window with the same width: canvas can only reach min(800, width).
        assertEquals(800.dp, portraitCanvasHeight(800.dp, 1056.dp))
        // scaled eye row + reserve: 1056 − 56*0.6 − 200*0.6 = 902.4.
        assertEquals(902.4f, portraitCanvasHeight(1056.dp, 1056.dp, 0.6f).value, 0.001f)
        // the binding constraint must hold in the eye-row-adjusted math too.
        assertEquals(250.dp, portraitCanvasHeight(250.dp, 600.dp))
    }

    @Test
    fun landscapeGeometryShouldTakeTheBindingConstraint() {
        // eye row excluded from the square cap: min(800 − 56, 1400 − 600) = 744.
        assertEquals(744.dp, landscapeCanvasWidth(1400.dp, 800.dp))
        // the right-panel width bound still binds when beneath the cap.
        assertEquals(200.dp, landscapeCanvasWidth(800.dp, 800.dp))
    }

    @Test
    fun shouldScaleTheLandscapeReserveWithUiScale() {
        // reserve 600dp collapses with the chrome: min(800 − 33.6, 700 − 360) = 340.
        assertEquals(340.dp, landscapeCanvasWidth(700.dp, 800.dp, 0.6f))
        // the (eye-row-adjusted) height bound still binds when beneath the reserve.
        assertEquals(266.4f, landscapeCanvasWidth(1400.dp, 300.dp, 0.6f).value, 0.001f)
    }

    @Test
    fun shouldDetectLandscapeByVisibleAreaNotDeviceOrientation() {
        assertTrue(isLandscapeLayout(1400.dp, 800.dp))
        assertTrue(!isLandscapeLayout(800.dp, 1400.dp))
        assertTrue(!isLandscapeLayout(800.dp, 800.dp))
    }
}