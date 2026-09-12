package codes.pepper.whimsicalart.feature.editor.ui.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Background-shape row must stay on ONE scrollable line (next.md → *Text
 * panel: the Background-shape row wraps*): on a narrow / uiScale-reduced
 * window the six chips are horizontally scrollable instead of wrapping "Blur"
 * onto a second line.
 */
@RunWith(RobolectricTestRunner::class)
class TextEditorPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun renderBackgroundRow(
        windowWidth: Dp = 640.dp,
        ocrAvailable: Boolean = true
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(windowWidth)) {
                    TextEditorPanel(
                        state = TextEditorUiState(),
                        onTextChange = {},
                        onFontSizeChange = {},
                        onColorChange = {},
                        onTextOpacityChange = {},
                        onFontChange = {},
                        onStyleToggle = {},
                        onAlignmentChange = {},
                        onRotationChange = {},
                        onShadowToggle = {},
                        onShadowAngleChange = {},
                        onShadowDistanceChange = {},
                        onShadowColorChange = {},
                        onOutlineToggle = {},
                        onOutlineSizeChange = {},
                        onOutlineColorChange = {},
                        onOutlineBlurChange = {},
                        onBackgroundShapeChange = {},
                        onBackgroundColorChange = {},
                        onBackgroundOpacityChange = {},
                        onBackgroundBlurChange = {},
                        onRecognizeText = {},
                        recognizingText = false,
                        ocrAvailable = ocrAvailable,
                        onRemove = {},
                        onAdd = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun shouldOfferRecognizeTextWhenTheOcrEngineIsAvailable() {
        renderBackgroundRow()
        composeRule.onNodeWithText("Recognize Text").assertExists()
    }

    @Test
    fun shouldHideRecognizeTextWhenTheOcrEngineIsUnavailable() {
        renderBackgroundRow(ocrAvailable = false)
        composeRule.onNodeWithText("Recognize Text").assertDoesNotExist()
    }

    @Test
    fun shouldKeepAllShapeChipsReachableAndScrollableOnANarrowWindow() {
        renderBackgroundRow(windowWidth = 280.dp)

        // The full set stays reachable on one continuously scrollable row.
        for (chip in listOf("None", "Rect", "Rounded", "Oval", "Pill", "Blur")) {
            composeRule.onNodeWithText(chip).assertExists()
        }
        val node = composeRule.onNodeWithTag("backgroundShapeRow").fetchSemanticsNode()
        assertTrue("background row must be horizontally scrollable", node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null)
        assertTrue("background row must expose a scroll-by action", node.config.getOrNull(SemanticsActions.ScrollBy) != null)
    }

    @Test
    fun shouldKeepBackgroundRowToASingleLineInsteadOfWrapping() {
        renderBackgroundRow(windowWidth = 280.dp)

        val node = composeRule.onNodeWithTag("backgroundShapeRow").fetchSemanticsNode()
        val maxSingleLinePx = with(composeRule.density) { 60.dp.toPx() }
        // One FilterChip line (~32dp) plus spacing — a wrapped second line would
        // push this well past a single chip height.
        assertTrue(
            "background row wrapped instead of scrolling (height = ${node.size.height}px)",
            node.size.height < maxSingleLinePx
        )
    }
}