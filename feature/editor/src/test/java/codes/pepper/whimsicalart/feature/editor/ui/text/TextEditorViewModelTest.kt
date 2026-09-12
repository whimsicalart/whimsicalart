package codes.pepper.whimsicalart.feature.editor.ui.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import codes.pepper.whimsicalart.feature.editor.domain.TextAlignment
import codes.pepper.whimsicalart.feature.editor.domain.TextBackgroundShape
import codes.pepper.whimsicalart.feature.editor.domain.TextFont
import codes.pepper.whimsicalart.feature.editor.domain.TextStyleFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * P12 — the Text tool's eye-off editing path (docs/effects_reference.md → *Text*
 * → *Eye button*): on eye OFF the committed text effect is EXCLUDED from the
 * composite fold, so the live overlay (`TextOverlayLayer` ← `textState.textOverlays`)
 * is the ONLY preview of the current item AND the only source the stack commit
 * folds from. These tests pin down that every font property edit (size / weight /
 * shadow / outline / colour) updates the typed or selected item — the exact list
 * the eye-off overlay renders — and that the committed `TextItem` conversion
 * (`toTextItem`, what the `TextEffect` entry rasterises) carries the same styling,
 * so nothing is lost between the eye-off preview and the commit.
 */
@RunWith(RobolectricTestRunner::class)
class TextEditorViewModelTest {

    @Test
    fun `font property edits update the live draft overlay while the composite is excluded`() {
        val vm = TextEditorViewModel()
        vm.startAddingText()
        vm.updateCurrentText("Hello")
        assertEquals("Hello", vm.uiState.value.textOverlays.single().text)

        vm.updateFontSize(48f)
        vm.toggleStyle(TextStyleFlag.BOLD)
        vm.toggleStyle(TextStyleFlag.ITALIC)
        vm.updateFont(TextFont.SERIF)
        vm.toggleShadow()
        vm.updateShadowAngle(135f)
        vm.updateShadowDistance(10f)
        vm.updateShadowColor(Color.Red)
        vm.toggleOutline()
        vm.updateOutlineSize(4f)
        vm.updateOutlineColor(Color.Cyan)
        vm.updateOutlineBlur(0.5f)

        val edited = vm.uiState.value.textOverlays.single()
        assertEquals(48f, edited.fontSize)
        assertEquals(setOf(TextStyleFlag.BOLD, TextStyleFlag.ITALIC), edited.style)
        assertEquals(TextFont.SERIF, edited.fontFamily)
        assertTrue(edited.hasShadow)
        assertEquals(135f, edited.shadowAngle)
        assertEquals(10f, edited.shadowDistance)
        assertEquals(Color.Red.toArgb(), edited.shadowColor.toArgb())
        assertTrue(edited.hasOutline)
        assertEquals(4f, edited.outlineSize)
        assertEquals(0.5f, edited.outlineBlur)
        assertEquals(Color.Cyan.toArgb(), edited.outlineColor.toArgb())
    }

    @Test
    fun `style edits typed before any text are inherited by the first draft`() {
        val vm = TextEditorViewModel()
        vm.startAddingText()
        vm.updateFontSize(30f)
        vm.toggleStyle(TextStyleFlag.BOLD)
        vm.updateColor(Color.Blue)
        vm.updateTextOpacity(0.5f)
        vm.toggleShadow()
        vm.toggleOutline()
        vm.updateOutlineColor(Color.Yellow)

        vm.updateCurrentText("Style first")

        val overlay = vm.uiState.value.textOverlays.single()
        assertEquals("Style first", overlay.text)
        assertEquals(30f, overlay.fontSize)
        assertTrue(TextStyleFlag.BOLD in overlay.style)
        assertEquals(Color.Blue.copy(alpha = 0.5f).toArgb(), overlay.color.toArgb())
        assertTrue(overlay.hasShadow)
        assertTrue(overlay.hasOutline)
        assertEquals(Color.Yellow.toArgb(), overlay.outlineColor.toArgb())
    }

    @Test
    fun `selecting an item loads its parameters and in-place edits update the item`() {
        val vm = TextEditorViewModel()
        // Re-entering the tool restores the committed list (stack source of truth).
        val first = TextOverlay(
            id = "a", text = "First", position = Offset(100f, 100f),
            fontSize = 40f, style = setOf(TextStyleFlag.BOLD)
        )
        val second = TextOverlay(
            id = "b", text = "Second", position = Offset(100f, 156f)
        )
        vm.restoreOverlays(listOf(first, second))
        assertEquals(2, vm.uiState.value.textOverlays.size)

        vm.selectOverlay("a")
        val state = vm.uiState.value
        assertEquals("First", state.currentText)
        assertEquals(40f, state.currentFontSize)
        assertTrue(TextStyleFlag.BOLD in state.currentStyle)

        vm.updateFontSize(60f)
        vm.updateOutlineSize(3f)

        val item = vm.uiState.value.textOverlays.single { it.id == "a" }
        assertEquals(60f, item.fontSize)
        assertEquals(3f, item.outlineSize)
        // The untouched sibling keeps its own values.
        assertEquals(24f, vm.uiState.value.textOverlays.single { it.id == "b" }.fontSize)
    }

    @Test
    fun `gesture rotation mirrors the wrapped value into the current rotation control`() {
        val vm = TextEditorViewModel()
        vm.startAddingText()
        vm.updateCurrentText("Hello")
        val id = vm.uiState.value.textOverlays.single().id

        vm.rotateOverlay(id, 45f)
        assertEquals(45f, vm.uiState.value.textOverlays.single().rotation, 1e-4f)
        assertEquals(45f, vm.uiState.value.currentRotation, 1e-4f)

        // A negative gesture rotation wraps into [0°, 360°).
        vm.rotateOverlay(id, -90f)
        assertEquals(270f, vm.uiState.value.textOverlays.single().rotation, 1e-4f)
        assertEquals(270f, vm.uiState.value.currentRotation, 1e-4f)
    }

    @Test
    fun `gesture rotation beyond 360 wraps both item and control back and continues`() {
        val vm = TextEditorViewModel()
        vm.startAddingText()
        vm.updateCurrentText("Spin")
        val id = vm.uiState.value.textOverlays.single().id

        vm.rotateOverlay(id, 361.5f)
        assertEquals(1.5f, vm.uiState.value.textOverlays.single().rotation, 1e-4f)
        assertEquals(1.5f, vm.uiState.value.currentRotation, 1e-4f)

        vm.rotateOverlay(id, 720f)
        assertEquals(0f, vm.uiState.value.textOverlays.single().rotation, 1e-4f)
        assertEquals(0f, vm.uiState.value.currentRotation, 1e-4f)

        vm.rotateOverlay(id, -180f)
        assertEquals(180f, vm.uiState.value.textOverlays.single().rotation, 1e-4f)
        assertEquals(180f, vm.uiState.value.currentRotation, 1e-4f)
    }

    @Test
    fun `slider and gesture rotations round-trip and always agree`() {
        val vm = TextEditorViewModel()
        vm.startAddingText()
        vm.updateCurrentText("Agree")
        val id = vm.uiState.value.textOverlays.single().id

        // Slider → item: the slider-driven path updates both.
        vm.updateCurrentRotation(120f)
        assertEquals(120f, vm.uiState.value.textOverlays.single().rotation, 1e-4f)
        assertEquals(120f, vm.uiState.value.currentRotation, 1e-4f)

        // Gesture → slider: rotateOverlay lands on the same value + the control.
        vm.rotateOverlay(id, 250f)
        assertEquals(250f, vm.uiState.value.textOverlays.single().rotation, 1e-4f)
        assertEquals(250f, vm.uiState.value.currentRotation, 1e-4f)

        // A wrapped gesture settles both at the same wrapped value.
        vm.rotateOverlay(id, 250f + 360f)
        assertEquals(250f, vm.uiState.value.textOverlays.single().rotation, 1e-4f)
        assertEquals(250f, vm.uiState.value.currentRotation, 1e-4f)
    }

    @Test
    fun `re-entering a text restores its wrapped rotation into the control`() {
        val vm = TextEditorViewModel()
        val overlay = TextOverlay(
            id = "a", text = "Tilted", position = Offset(100f, 100f),
            rotation = 374f
        )
        vm.restoreOverlays(listOf(overlay))

        vm.selectOverlay("a")
        assertEquals(14f, vm.uiState.value.currentRotation, 1e-4f)
    }

    @Test
    fun `restoring committed text auto-selects the last item so the panel opens in edit mode`() {
        val vm = TextEditorViewModel()
        val first = TextOverlay(id = "a", text = "First", position = Offset(100f, 100f))
        val last = TextOverlay(
            id = "b", text = "Last", position = Offset(100f, 156f),
            fontSize = 48f, style = setOf(TextStyleFlag.BOLD)
        )

        vm.restoreOverlays(listOf(first, last))

        val state = vm.uiState.value
        // The last inserted text is loaded into the field/controls and isEditing
        // stays on — there is no separate first-state screen any more.
        assertEquals("Last", state.currentText)
        assertEquals(48f, state.currentFontSize)
        assertTrue(TextStyleFlag.BOLD in state.currentStyle)
        assertEquals("b", state.selectedOverlayId)
        assertTrue(state.isEditing)
    }

    @Test
    fun `recognized overlays keep the editing panel open with the last OCR item selected`() {
        val vm = TextEditorViewModel()
        val seeded = TextOverlay(id = "seed", text = "Already", position = Offset(100f, 100f))
        vm.restoreOverlays(listOf(seeded))
        val recognized = listOf(
            TextOverlay(id = "ocr-1", text = "Hello", position = Offset(100f, 200f)),
            TextOverlay(id = "ocr-2", text = "World", position = Offset(100f, 256f))
        )

        vm.addRecognizedOverlays(recognized)

        val state = vm.uiState.value
        assertEquals(3, state.textOverlays.size)
        // No first-state fallback: the last OCR'd item is immediately editable.
        assertEquals("World", state.currentText)
        assertEquals("ocr-2", state.selectedOverlayId)
        assertTrue(state.isEditing)
    }

    @Test
    fun `a fully styled overlay keeps every font property through the commit conversion and back`() {
        val density = Density(density = 2f, fontScale = 1f)
        val w = 1000f
        val h = 800f
        val overlay = TextOverlay(
            id = "t1",
            text = "Styled",
            position = Offset(250f, 200f),
            fontSize = 40f,
            color = Color(0x80FF0000.toInt()),
            fontFamily = TextFont.SERIF,
            style = setOf(TextStyleFlag.BOLD, TextStyleFlag.UNDERLINE),
            alignment = TextAlignment.JUSTIFIED,
            scale = 1.3f,
            rotation = 30f,
            hasShadow = true,
            shadowAngle = 90f,
            shadowDistance = 10f,
            shadowColor = Color.Black,
            hasOutline = true,
            outlineSize = 3f,
            outlineColor = Color.White,
            outlineBlur = 0.4f,
            backgroundShape = TextBackgroundShape.ROUNDED,
            backgroundColor = Color(0x40000000.toInt())
        )

        val item = overlay.toTextItem(density, w, h)
        assertEquals(80f / w, item.fontSize, 1e-4f)
        assertEquals(500f / w, item.x, 1e-4f)
        assertEquals(400f / h, item.y, 1e-4f)
        assertEquals(0x80FF0000.toInt(), item.color)
        assertEquals(TextFont.SERIF, item.fontFamily)
        assertEquals(setOf(TextStyleFlag.BOLD, TextStyleFlag.UNDERLINE), item.style)
        assertEquals(TextAlignment.JUSTIFIED, item.alignment)
        assertEquals(1.3f, item.scale, 1e-4f)
        assertEquals(30f, item.rotationDegrees, 1e-4f)
        assertTrue(item.shadow.enabled)
        assertEquals(90f, item.shadow.angleDegrees, 1e-4f)
        assertEquals(20f / w, item.shadow.distance, 1e-4f)
        assertEquals(Color.Black.toArgb(), item.shadow.color)
        assertTrue(item.outline.enabled)
        assertEquals(6f / w, item.outline.size, 1e-4f)
        assertEquals(0.4f, item.outline.blur, 1e-4f)
        assertEquals(Color.White.toArgb(), item.outline.color)
        assertEquals(TextBackgroundShape.ROUNDED, item.background)
        assertEquals(0x40000000.toInt(), item.backgroundColor)

        val roundTrip = item.toTextOverlay(density, w, h)
        assertEquals(40f, roundTrip.fontSize, 1e-3f)
        assertEquals(TextFont.SERIF, roundTrip.fontFamily)
        assertEquals(setOf(TextStyleFlag.BOLD, TextStyleFlag.UNDERLINE), roundTrip.style)
        assertTrue(roundTrip.hasShadow)
        assertTrue(roundTrip.hasOutline)
        assertEquals(0.4f, roundTrip.outlineBlur, 1e-4f)
        assertEquals(TextBackgroundShape.ROUNDED, roundTrip.backgroundShape)
    }

    @Test
    fun `remove deletes only the selected overlay, clears the field, drops the draft and resets every control to its default`() {
        val vm = TextEditorViewModel()
        val first = TextOverlay(
            id = "a", text = "First", position = Offset(100f, 100f),
            fontSize = 40f, style = setOf(TextStyleFlag.BOLD)
        )
        val second = TextOverlay(id = "b", text = "Second", position = Offset(100f, 200f))
        vm.restoreOverlays(listOf(first, second))
        vm.updateCanvasCenter(Offset(50f, 60f))

        // Select one in-place and dirty EVERY control.
        vm.selectOverlay("a")
        vm.updateCurrentText("Edited")
        vm.updateFontSize(60f)
        vm.updateColor(Color.Blue)
        vm.updateTextOpacity(0.5f)
        vm.updateFont(TextFont.SERIF)
        vm.toggleStyle(TextStyleFlag.BOLD)
        vm.updateAlignment(TextAlignment.RIGHT)
        vm.updateCurrentRotation(90f)
        vm.toggleShadow()
        vm.toggleOutline()
        vm.updateBackgroundShape(TextBackgroundShape.ROUNDED)
        vm.updateBackgroundColor(Color.Cyan)
        vm.updateBackgroundOpacity(0.5f)
        vm.updateBackgroundBlur(10f)

        vm.removeSelectionAndReset()

        val state = vm.uiState.value
        // Only the SELECTED overlay is removed; the untouched sibling stays.
        assertEquals(listOf("b"), state.textOverlays.map { it.id })
        // Field cleared, selection and draft dropped.
        assertEquals("", state.currentText)
        assertEquals(null, state.selectedOverlayId)
        assertEquals(null, state.draftOverlayId)
        // Every current* control back at its first-run default.
        assertEquals(24f, state.currentFontSize)
        assertEquals(Color.White.toArgb(), state.currentColor.toArgb())
        assertEquals(1f, state.textOpacity)
        assertEquals(TextFont.DEFAULT, state.currentFont)
        assertTrue(state.currentStyle.isEmpty())
        assertEquals(TextAlignment.CENTER, state.currentAlignment)
        assertEquals(0f, state.currentRotation)
        assertTrue(!state.hasShadow)
        assertTrue(!state.hasOutline)
        assertEquals(TextBackgroundShape.NONE, state.currentBackgroundShape)
        assertEquals(Color.Black.toArgb(), state.currentBackgroundColor.toArgb())
        assertEquals(1f, state.backgroundOpacity)
        assertEquals(0f, state.currentBackgroundBlur)
        // The edit box centre survives the reset.
        assertEquals(Offset(50f, 60f), state.canvasCenter)
    }

    @Test
    fun `add still commits a fresh default-styled overlay after a remove reset`() {
        val vm = TextEditorViewModel()
        vm.restoreOverlays(
            listOf(TextOverlay(id = "a", text = "Old", position = Offset(100f, 100f)))
        )
        vm.selectOverlay("a")
        vm.updateFontSize(42f)
        vm.removeSelectionAndReset()
        assertTrue(vm.uiState.value.textOverlays.isEmpty())

        // The panel is fresh: typing + Add still commits a NEW default-styled
        // item (the live draft is a separate overlay — pre-existing behaviour).
        vm.updateCurrentText("Fresh")
        vm.addText()
        val added = vm.uiState.value.textOverlays.last()
        assertEquals("Fresh", added.text)
        assertEquals(24f, vm.uiState.value.currentFontSize)
        assertEquals("", vm.uiState.value.currentText)
        assertEquals(null, vm.uiState.value.draftOverlayId)
    }
}