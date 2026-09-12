package codes.pepper.whimsicalart.feature.editor.ui.pen

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PenViewModelTest {

    private val vm = PenViewModel()

    @Test
    fun `stroke captures the selected opacity and hardness`() {
        vm.updateBrushOpacity(0.4f)
        vm.updateBrushHardness(0.3f)
        vm.onDragStart(Offset(10f, 10f))
        val current = requireNotNull(vm.uiState.value.currentStroke)
        assertEquals(0.4f, current.opacity)
        assertEquals(0.3f, current.hardness)
    }

    @Test
    fun `committed stroke keeps the brush opacity and hardness`() {
        vm.updateBrushOpacity(0.5f)
        vm.updateBrushHardness(0.8f)
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(40f, 40f))
        vm.onDragEnd()
        val committed = vm.uiState.value.activeLayer?.strokes?.last()
        assertEquals(0.5f, committed?.opacity)
        assertEquals(0.8f, committed?.hardness)
    }

    @Test
    fun `opacity and hardness are clamped to zero-to-one`() {
        vm.updateBrushOpacity(1.5f)
        vm.updateBrushHardness(-0.2f)
        assertEquals(1f, vm.uiState.value.brushOpacity)
        assertEquals(0f, vm.uiState.value.brushHardness)
    }

    @Test
    fun `opacity and hardness default to opaque hard strokes`() {
        assertEquals(1f, vm.uiState.value.brushOpacity)
        assertEquals(1f, vm.uiState.value.brushHardness)
    }

    @Test
    fun `settling a stroke increments the committed-strokes version`() {
        assertEquals(0, vm.uiState.value.strokesVersion)
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(40f, 40f))
        vm.onDragEnd()
        assertEquals("one settled stroke bumps the version", 1, vm.uiState.value.strokesVersion)
        vm.onDragStart(Offset(0f, 0f))
        vm.onDragMove(Offset(30f, 30f))
        vm.onDragEnd()
        assertEquals("a second settled stroke bumps it again", 2, vm.uiState.value.strokesVersion)
        vm.undo()
        assertEquals("undoing a stroke bumps the version", 3, vm.uiState.value.strokesVersion)
        vm.redo()
        assertEquals("redoing a stroke bumps the version", 4, vm.uiState.value.strokesVersion)
        vm.clear()
        assertEquals("clearing committed strokes bumps the version", 5, vm.uiState.value.strokesVersion)
    }
}