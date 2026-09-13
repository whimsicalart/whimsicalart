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
 *        gesture handlers, while taps on the canvas do;
 *  (v)   the split is letterbox-FIRST with two levels: square cap → 50% floor →
 *        controls-minimum/remainder, and the controls/panel absorb every dp the
 *        letterbox leaves unused (NO percentage ceiling).
 *
 * The layout sub-tests (i)-(iv) feed explicit [canvasWidth]/[canvasHeight] to
 * the workspace allocator; the pure-helper sub-tests (v) verify the split math.
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
        canvasHeight: Dp,
        uiScale: Float = 1f,
        onControlsNaturallySized: (Dp) -> Unit = {}
    ) {
        canvasTaps = 0
        composeRule.setContent {
            Box(Modifier.size(containerWidth, containerHeight)) {
                EditorWorkspaceLayout(
                    isLandscape = isLandscape,
                    uiScale = uiScale,
                    canvasHeight = canvasHeight,
                    canvasWidth = canvasWidth,
                    onControlsNaturallySized = onControlsNaturallySized,
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

    // ── Letterbox-first split semantics (spec "Canvas layout and gesture scope") ─
    // The window budget is split letterbox-FIRST with two levels:
    //   portrait:  budget = availableHeight − 56 dp × uiScale; the letterbox
    //              takes its square cap (availableWidth) when the window holds
    //              it plus the controls minimum, else the 50% floor; the
    //              controls absorb every dp of the remainder (no 30% ceiling).
    //   landscape: availableWidth split against the square-cap height
    //              (availableHeight − 56 dp × uiScale); panel minimum =
    //              min(600 dp × uiScale, 70% × availableWidth); the canvas
    //              keeps square cap → floor → below-floor, the panel absorbs
    //              the rest.
    // The two values (letterbox, controls/panel) always sum to the budget.

    @Test
    fun portraitSplitShouldResolveBothValuesFromOnePairOfEyes() {
        // the pair sums to the budget (availableHeight − 56 dp eye row) exactly.
        val (canvas, controls) = portraitLetterboxSplit(800.dp, 600.dp)
        assertEquals(544.dp, canvas + controls)
        assertEquals(272.dp, canvas)
        assertEquals(272.dp, controls)
    }

    @Test
    fun portraitCanvasShouldTakeTheSquareCapWhenTheWindowHoldsItPlusControlsMin() {
        // budget = 744; square cap 500 + controls min 200 = 700 ≤ 744 → cap wins
        // and the controls absorb the leftover 44 dp (no 30% ceiling).
        assertEquals(500.dp, portraitCanvasHeight(500.dp, 800.dp))
        assertEquals(244.dp, portraitControlsHeight(500.dp, 800.dp))
    }

    @Test
    fun portraitCanvasShouldFallToTheFiftyPercentFloorWhenTheSquareCapDoesNotFit() {
        // budget = 544; square cap 800 + controls min > 544 → the 50% floor.
        assertEquals(272.dp, portraitCanvasHeight(800.dp, 600.dp))
        assertEquals(272.dp, portraitControlsHeight(800.dp, 600.dp))
    }

    @Test
    fun portraitCanvasShouldNeverFallBelowTheFloor() {
        // budget = 344; the controls min (103.2 = 30% of 344) is below the floor,
        // so the floor branch always fires in portrait: the canvas keeps 50% of
        // the budget and the controls stay above their minimum.
        assertEquals(172f, portraitCanvasHeight(800.dp, 400.dp).value, 0.001f)
        assertEquals(172f, portraitControlsHeight(800.dp, 400.dp).value, 0.001f)
        assertTrue(
            "controls stay above their minimum",
            portraitControlsHeight(800.dp, 400.dp) >= 103.2.dp
        )
    }

    @Test
    fun shouldScaleThePortraitSplitWithUiScale() {
        // budget = 600 − 56*0.6 = 566.4; square cap 800 + 120 > 566.4 → floor.
        assertEquals(283.2f, portraitCanvasHeight(800.dp, 600.dp, 0.6f).value, 0.001f)
        // the square cap binds when beneath the scaled budget.
        assertEquals(300.dp, portraitCanvasHeight(300.dp, 600.dp, 0.6f))
        assertEquals(266.4f, portraitControlsHeight(300.dp, 600.dp, 0.6f).value, 0.001f)
    }

    @Test
    fun portraitSplitShouldSurviveTheEyeRowAndSquareCap() {
        // eye row reserved first: budget = 1000 → square cap 800 + min 200 fits.
        assertEquals(800.dp, portraitCanvasHeight(800.dp, 1056.dp))
        // scaled: budget = 1022.4; cap 1056 + 120 > 1022.4 → 50% floor = 511.2.
        assertEquals(511.2f, portraitCanvasHeight(1056.dp, 1056.dp, 0.6f).value, 0.001f)
        // the width bound still binds beneath the scaled reserve.
        assertEquals(250.dp, portraitCanvasHeight(250.dp, 600.dp))
    }

    @Test
    fun landscapeSplitShouldResolveBothValuesFromOnePairOfEyes() {
        val (canvas, panel) = landscapeLetterboxSplit(1400.dp, 800.dp)
        assertEquals(1400.dp, canvas + panel)
        assertEquals(744.dp, canvas)
        assertEquals(656.dp, panel)
    }

    @Test
    fun landscapeCanvasShouldTakeTheSquareCapWhenTheWindowHoldsItPlusPanelMin() {
        // h = 744; square cap 744 + panel min 600 = 1344 ≤ 1400 → cap wins.
        assertEquals(744.dp, landscapeCanvasWidth(1400.dp, 800.dp))
        assertEquals(656.dp, landscapePanelWidth(1400.dp, 800.dp))
    }

    @Test
    fun landscapeCanvasShouldFallToTheFiftyPercentFloorWhenTheSquareCapDoesNotFit() {
        // 1200 ≥ 0.5×1200 (600) + 600 panel min → exactly the 50% floor.
        assertEquals(600.dp, landscapeCanvasWidth(1200.dp, 800.dp))
        assertEquals(600.dp, landscapePanelWidth(1200.dp, 800.dp))
    }

    @Test
    fun landscapePanelShouldKeepItsMinimumAndLetTheCanvasFallBelowTheFloor() {
        // the reachable below-floor branch: the 600 dp panel min exceeds half a
        // narrow 800 dp window, so the panel keeps its min and the letterbox the
        // rest (240 < the 400 dp floor).
        assertEquals(240.dp, landscapeCanvasWidth(800.dp, 800.dp))
        assertEquals(560.dp, landscapePanelWidth(800.dp, 800.dp))
    }

    @Test
    fun shouldScaleTheLandscapeSplitWithUiScale() {
        // 0.6 × 600 = 360 panel min; narrow window → panel min wins, canvas 340.
        assertEquals(340.dp, landscapeCanvasWidth(700.dp, 800.dp, 0.6f))
        assertEquals(360.dp, landscapePanelWidth(700.dp, 800.dp, 0.6f))
        // the square-cap height bound binds when beneath the panel reserve.
        assertEquals(266.4f, landscapeCanvasWidth(1400.dp, 300.dp, 0.6f).value, 0.001f)
        assertEquals(1133.6f, landscapePanelWidth(1400.dp, 300.dp, 0.6f).value, 0.001f)
    }

    @Test
    fun shouldDetectLandscapeByVisibleAreaNotDeviceOrientation() {
        assertTrue(isLandscapeLayout(1400.dp, 800.dp))
        assertTrue(!isLandscapeLayout(800.dp, 1400.dp))
        assertTrue(!isLandscapeLayout(800.dp, 800.dp))
    }

    @Test
    fun shouldReportTheNaturalToolbarPlusControlsHeight() {
        var reported: Dp? = null
        compose(
            isLandscape = false,
            containerWidth = 600.dp,
            containerHeight = 800.dp,
            canvasWidth = 600.dp,
            canvasHeight = 600.dp,
            onControlsNaturallySized = { reported = it }
        )
        // header (32dp) + tool controls (48dp) = the panel's natural height.
        assertEquals(80.dp, reported)
    }
}