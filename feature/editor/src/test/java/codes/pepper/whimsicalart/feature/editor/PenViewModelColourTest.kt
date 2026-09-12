package codes.pepper.whimsicalart.feature.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import codes.pepper.whimsicalart.core.designsystem.ColourPickerController
import codes.pepper.whimsicalart.feature.editor.ui.pen.PenViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * P9 — pen custom colours (docs/effects_reference.md → *Pen controls* → "Colour
 * choices come from the shared picker"): the pen colour controls render the shared
 * compact colour row whose rainbow cell opens the full picker popup, and any colour
 * picked there flows through [PenViewModel.selectColor] into committed strokes.
 * The helper widgets (colour row, popup) live in core:designsystem; here we drive
 * the exact controller→pen chain a popup confirm would produce.
 */
@RunWith(RobolectricTestRunner::class)
class PenViewModelColourTest {

    @Test
    fun `a custom colour picked in the popup becomes the pen stroke colour`() {
        val controller = ColourPickerController(0xFF000000.toInt())
        controller.hue = 200f
        controller.saturation = 0.8f
        controller.value = 0.9f
        controller.alpha = 1f
        val custom = Color(controller.toArgb())

        val viewModel = PenViewModel()
        viewModel.selectColor(custom)
        viewModel.onDragStart(Offset(10f, 10f))
        viewModel.onDragMove(Offset(40f, 40f))
        viewModel.onDragEnd()

        val stroke = viewModel.uiState.value.activeLayer!!.strokes.single()
        assertEquals(custom.toArgb(), stroke.color.toArgb())
    }

    @Test
    fun `a compact preset tap becomes the pen stroke colour`() {
        val viewModel = PenViewModel()
        viewModel.selectColor(Color(0xFFE53935.toInt()))
        viewModel.onDragStart(Offset(20f, 20f))
        viewModel.onDragMove(Offset(50f, 60f))
        viewModel.onDragEnd()

        val stroke = viewModel.uiState.value.activeLayer!!.strokes.single()
        assertEquals(0xFFE53935.toInt(), stroke.color.toArgb())
    }
}