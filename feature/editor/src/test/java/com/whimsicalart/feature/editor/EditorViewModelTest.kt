package com.whimsicalart.feature.editor

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.test.core.app.ApplicationProvider
import com.whimsicalart.feature.editor.domain.ImageSaver
import com.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import com.whimsicalart.feature.editor.ui.viewmodel.EditTool
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {

    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context))
    }

    @Test
    fun `initial state has default values`() {
        val state = viewModel.uiState.value
        assertNull(state.imageUri)
        assertFalse(state.isProcessing)
        assertNull(state.error)
        assertNull(state.selectedTool)
        assertEquals(0f, state.brightness)
        assertEquals(0f, state.contrast)
        assertEquals(0f, state.saturation)
        assertEquals(0f, state.sharpness)
        assertEquals(0f, state.rotation)
        assertFalse(state.flipHorizontal)
        assertFalse(state.flipVertical)
        assertNull(state.cropRect)
        assertTrue(state.history.isEmpty())
        assertEquals(-1, state.historyIndex)
    }

    @Test
    fun `setImageUri updates state`() {
        val uri = Uri.parse("content://media/1")
        viewModel.setImageUri(uri)
        assertEquals(uri, viewModel.uiState.value.imageUri)
    }

    @Test
    fun `selectTool updates selectedTool`() {
        viewModel.selectTool(EditTool.BRIGHTNESS)
        assertEquals(EditTool.BRIGHTNESS, viewModel.uiState.value.selectedTool)
    }

    @Test
    fun `selectTool null clears selection`() {
        viewModel.selectTool(EditTool.CROP)
        viewModel.selectTool(null)
        assertNull(viewModel.uiState.value.selectedTool)
    }

    @Test
    fun `updateBrightness updates state`() {
        viewModel.updateBrightness(50f)
        assertEquals(50f, viewModel.uiState.value.brightness)
    }

    @Test
    fun `updateContrast updates state`() {
        viewModel.updateContrast(-30f)
        assertEquals(-30f, viewModel.uiState.value.contrast)
    }

    @Test
    fun `updateSaturation updates state`() {
        viewModel.updateSaturation(75f)
        assertEquals(75f, viewModel.uiState.value.saturation)
    }

    @Test
    fun `updateSharpness updates state`() {
        viewModel.updateSharpness(42f)
        assertEquals(42f, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `rotateLeft wraps to 270 degrees`() {
        viewModel.rotateLeft()
        assertEquals(270f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `rotateRight increments rotation by 90`() {
        viewModel.rotateRight()
        assertEquals(90f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `rotateLeft followed by rotateRight returns to zero`() {
        viewModel.rotateLeft()
        assertEquals(270f, viewModel.uiState.value.rotation)
        viewModel.rotateRight()
        assertEquals(0f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `rotateRight wraps around to 0 after four rotations`() {
        for (i in 1..4) viewModel.rotateRight()
        assertEquals(0f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `flipHorizontal toggles state`() {
        assertFalse(viewModel.uiState.value.flipHorizontal)
        viewModel.flipHorizontal()
        assertTrue(viewModel.uiState.value.flipHorizontal)
        viewModel.flipHorizontal()
        assertFalse(viewModel.uiState.value.flipHorizontal)
    }

    @Test
    fun `flipVertical toggles state`() {
        assertFalse(viewModel.uiState.value.flipVertical)
        viewModel.flipVertical()
        assertTrue(viewModel.uiState.value.flipVertical)
        viewModel.flipVertical()
        assertFalse(viewModel.uiState.value.flipVertical)
    }

    @Test
    fun `updateCrop updates cropRect`() {
        val rect = Rect(10f, 20f, 100f, 200f)
        viewModel.updateCrop(rect)
        assertEquals(rect, viewModel.uiState.value.cropRect)
    }

    @Test
    fun `resetAdjustments clears all adjustment values`() {
        viewModel.updateBrightness(50f)
        viewModel.updateContrast(-30f)
        viewModel.updateSaturation(75f)
        viewModel.updateSharpness(42f)
        
        viewModel.resetAdjustments()
        
        assertEquals(0f, viewModel.uiState.value.brightness)
        assertEquals(0f, viewModel.uiState.value.contrast)
        assertEquals(0f, viewModel.uiState.value.saturation)
        assertEquals(0f, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `rotateRight saves to history`() {
        viewModel.rotateRight()
        assertEquals(1, viewModel.uiState.value.history.size)
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `undo restores previous state`() {
        viewModel.rotateRight()
        viewModel.rotateRight()

        viewModel.undo()

        assertEquals(90f, viewModel.uiState.value.rotation)
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `redo restores next state`() {
        viewModel.rotateRight()
        viewModel.rotateRight()

        viewModel.undo()
        viewModel.redo()

        assertEquals(180f, viewModel.uiState.value.rotation)
        assertEquals(1, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `undo at index 0 does nothing`() {
        viewModel.rotateRight()
        viewModel.undo()
        viewModel.undo()
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `redo at end does nothing`() {
        viewModel.rotateRight()
        viewModel.redo()
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }
}
