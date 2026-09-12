package codes.pepper.whimsicalart.feature.editor.ui.frames

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import codes.pepper.whimsicalart.feature.editor.domain.FrameStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Frames controls have two LABELLED tier sections (docs/effects_reference.md →
 * *Frames*, "Controls"): **"Inner frame"** and **"Outer frame"**. A tier at
 * "None" shows ONLY its style chip row — its Thickness slider, line sliders and
 * colour picker are hidden (that frame tier is gone), and re-picking a style
 * shows them again with their retained values. The OUTER tier's style row omits
 * the Heart style.
 */
@RunWith(RobolectricTestRunner::class)
class FrameControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun styleChip(
        selected: FrameStyle?,
        thickness: Float,
        linesEnabled: Boolean = false,
        onThicknessChange: (Float) -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                FrameControls(
                    innerSelectedStyle = selected,
                    innerThickness = thickness,
                    innerColor = Color.White,
                    innerEdgeSize = 0.2f,
                    innerCustomAngle = 0f,
                    innerCustomCurveDistance = 0f,
                    innerCustomStrength = 0f,
                    innerCustomCount = 4f,
                    innerLines = 1,
                    innerLinesEnabled = linesEnabled,
                    innerLineDistance = 0.04f,
                    innerLineThickness = 0.02f,
                    outerSelectedStyle = null,
                    outerThickness = 0f,
                    outerColor = Color.White,
                    outerEdgeSize = 0.2f,
                    outerCustomAngle = 0f,
                    outerCustomCurveDistance = 0f,
                    outerCustomStrength = 0f,
                    outerCustomCount = 2f,
                    outerLines = 0,
                    outerLinesEnabled = false,
                    outerLineDistance = 0.04f,
                    outerLineThickness = 0.02f,
                    onInnerStyleSelected = {},
                    onInnerThicknessChange = onThicknessChange,
                    onInnerColorChange = {},
                    onInnerEdgeSizeChange = {},
                    onInnerCustomAngleChange = {},
                    onInnerCustomCurveDistanceChange = {},
                    onInnerCustomStrengthChange = {},
                    onInnerCustomCountChange = {},
                    onInnerLinesChange = {},
                    onInnerLinesToggle = {},
                    onInnerLineDistanceChange = {},
                    onInnerLineThicknessChange = {},
                    onOuterStyleSelected = {},
                    onOuterThicknessChange = {},
                    onOuterColorChange = {},
                    onOuterEdgeSizeChange = {},
                    onOuterCustomAngleChange = {},
                    onOuterCustomCurveDistanceChange = {},
                    onOuterCustomStrengthChange = {},
                    onOuterCustomCountChange = {},
                    onOuterLinesChange = {},
                    onOuterLinesToggle = {},
                    onOuterLineDistanceChange = {},
                    onOuterLineThicknessChange = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun sliderMatcher(expectedValue: Float, range: ClosedFloatingPointRange<Float>): SemanticsMatcher =
        SemanticsMatcher("Slider ${range.start}..${range.endInclusive} at $expectedValue") { node ->
            val info = node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
            info != null &&
                info.current == expectedValue &&
                info.range.start == range.start &&
                info.range.endInclusive == range.endInclusive
        }

    @Test
    fun shouldShowTierNamesAndStyleChipsOnlyWhenBothTiersAreNone() {
        styleChip(selected = null, thickness = 0.4f)

        composeRule.onNodeWithText("Inner frame").assertExists()
        composeRule.onNodeWithText("Outer frame").assertExists()
        // Both None tiers render their style chip row (including their own "None").
        composeRule.onAllNodesWithText("None").assertCountEquals(2)
        composeRule.onAllNodesWithText("Solid").assertCountEquals(2)
        // The OUTER tier's style row omits the Heart style.
        composeRule.onAllNodesWithText("Heart").assertCountEquals(1)
        composeRule.onNodeWithText("Thickness").assertDoesNotExist()
        composeRule.onNodeWithText("Lines").assertDoesNotExist()
        composeRule.onNodeWithTag(FRAME_COLOUR_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun shouldShowThicknessLinesAndColourPickerForTheStyleSelectedTierOnly() {
        styleChip(selected = FrameStyle.SOLID, thickness = 0.4f, linesEnabled = true)

        composeRule.onNodeWithText("Thickness").assertExists()
        composeRule.onAllNodesWithText("Lines").assertCountEquals(2)
        composeRule.onNodeWithText("Line Distance").assertExists()
        composeRule.onNodeWithText("Line Thickness").assertExists()
        composeRule.onNodeWithTag(FRAME_COLOUR_PICKER_TAG).assertExists()
        composeRule.onNode(sliderMatcher(0.4f, 0f..1f)).assertExists()
    }

    @Test
    fun shouldRestoreThicknessWithRetainedValueAfterRePickingAStyle() {
        var selected by mutableStateOf<FrameStyle?>(FrameStyle.SOLID)
        val thickness = mutableStateOf(0.7f)
        composeRule.setContent {
            MaterialTheme {
                FrameControls(
                    innerSelectedStyle = selected,
                    innerThickness = thickness.value,
                    innerColor = Color.Red,
                    innerEdgeSize = 0.2f,
                    innerCustomAngle = 0f,
                    innerCustomCurveDistance = 0f,
                    innerCustomStrength = 0f,
                    innerCustomCount = 4f,
                    innerLines = 0,
                    innerLinesEnabled = false,
                    innerLineDistance = 0.04f,
                    innerLineThickness = 0.02f,
                    outerSelectedStyle = null,
                    outerThickness = 0f,
                    outerColor = Color.White,
                    outerEdgeSize = 0.2f,
                    outerCustomAngle = 0f,
                    outerCustomCurveDistance = 0f,
                    outerCustomStrength = 0f,
                    outerCustomCount = 2f,
                    outerLines = 0,
                    outerLinesEnabled = false,
                    outerLineDistance = 0.04f,
                    outerLineThickness = 0.02f,
                    onInnerStyleSelected = { selected = it },
                    onInnerThicknessChange = { thickness.value = it },
                    onInnerColorChange = {},
                    onInnerEdgeSizeChange = {},
                    onInnerCustomAngleChange = {},
                    onInnerCustomCurveDistanceChange = {},
                    onInnerCustomStrengthChange = {},
                    onInnerCustomCountChange = {},
                    onInnerLinesChange = {},
                    onInnerLinesToggle = {},
                    onInnerLineDistanceChange = {},
                    onInnerLineThicknessChange = {},
                    onOuterStyleSelected = {},
                    onOuterThicknessChange = {},
                    onOuterColorChange = {},
                    onOuterEdgeSizeChange = {},
                    onOuterCustomAngleChange = {},
                    onOuterCustomCurveDistanceChange = {},
                    onOuterCustomStrengthChange = {},
                    onOuterCustomCountChange = {},
                    onOuterLinesChange = {},
                    onOuterLinesToggle = {},
                    onOuterLineDistanceChange = {},
                    onOuterLineThicknessChange = {}
                )
            }
        }
        composeRule.waitForIdle()

        // "None" hides the tier's thickness + colour; re-picking SOLID re-shows 0.7.
        selected = null
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Thickness").assertDoesNotExist()
        composeRule.onNodeWithTag(FRAME_COLOUR_PICKER_TAG).assertDoesNotExist()

        selected = FrameStyle.SOLID
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Thickness").assertExists()
        composeRule.onNodeWithTag(FRAME_COLOUR_PICKER_TAG).assertExists()
        composeRule.onNode(sliderMatcher(0.7f, 0f..1f)).assertExists()
    }

    @Test
    fun shouldHideLineSlidersWhenToggleOffAndShowThemWithRangeWhenOn() {
        var linesEnabled by mutableStateOf(false)
        var lines by mutableStateOf(1)
        composeRule.setContent {
            MaterialTheme {
                FrameControls(
                    innerSelectedStyle = FrameStyle.SOLID,
                    innerThickness = 0.4f,
                    innerColor = Color.White,
                    innerEdgeSize = 0.2f,
                    innerCustomAngle = 0f,
                    innerCustomCurveDistance = 0f,
                    innerCustomStrength = 0f,
                    innerCustomCount = 4f,
                    innerLines = lines,
                    innerLinesEnabled = linesEnabled,
                    innerLineDistance = 0.04f,
                    innerLineThickness = 0.02f,
                    outerSelectedStyle = null,
                    outerThickness = 0f,
                    outerColor = Color.White,
                    outerEdgeSize = 0.2f,
                    outerCustomAngle = 0f,
                    outerCustomCurveDistance = 0f,
                    outerCustomStrength = 0f,
                    outerCustomCount = 2f,
                    outerLines = 0,
                    outerLinesEnabled = false,
                    outerLineDistance = 0.04f,
                    outerLineThickness = 0.02f,
                    onInnerStyleSelected = {},
                    onInnerThicknessChange = {},
                    onInnerColorChange = {},
                    onInnerEdgeSizeChange = {},
                    onInnerCustomAngleChange = {},
                    onInnerCustomCurveDistanceChange = {},
                    onInnerCustomStrengthChange = {},
                    onInnerCustomCountChange = {},
                    onInnerLinesChange = { lines = it },
                    onInnerLinesToggle = { linesEnabled = it },
                    onInnerLineDistanceChange = {},
                    onInnerLineThicknessChange = {},
                    onOuterStyleSelected = {},
                    onOuterThicknessChange = {},
                    onOuterColorChange = {},
                    onOuterEdgeSizeChange = {},
                    onOuterCustomAngleChange = {},
                    onOuterCustomCurveDistanceChange = {},
                    onOuterCustomStrengthChange = {},
                    onOuterCustomCountChange = {},
                    onOuterLinesChange = {},
                    onOuterLinesToggle = {},
                    onOuterLineDistanceChange = {},
                    onOuterLineThicknessChange = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Off: only the toggle's own "Lines" label shows — the count slider,
        // Line Distance and Line Thickness are hidden (fewer controls).
        composeRule.onAllNodesWithText("Lines").assertCountEquals(1)
        composeRule.onNodeWithText("Line Distance").assertDoesNotExist()
        composeRule.onNodeWithText("Line Thickness").assertDoesNotExist()

        // On: the count slider (1–10) and the two spacing controls reappear.
        linesEnabled = true
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Lines").assertCountEquals(2)
        composeRule.onNodeWithText("Line Distance").assertExists()
        composeRule.onNodeWithText("Line Thickness").assertExists()
        composeRule.onNode(sliderMatcher(1f, 1f..10f)).assertExists()

        lines = 7
        composeRule.waitForIdle()
        composeRule.onNode(sliderMatcher(7f, 1f..10f)).assertExists()
    }
}