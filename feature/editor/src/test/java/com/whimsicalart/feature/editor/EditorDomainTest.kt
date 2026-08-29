package com.whimsicalart.feature.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.whimsicalart.feature.editor.ui.text.TextAlignment
import com.whimsicalart.feature.editor.ui.text.TextFont
import com.whimsicalart.feature.editor.ui.text.TextOverlay
import com.whimsicalart.feature.editor.ui.mosaic.MosaicBrushType
import com.whimsicalart.feature.editor.ui.mosaic.MosaicStroke
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextOverlayTest {

    @Test
    fun `textOverlay stores values correctly`() {
        val overlay = TextOverlay(
            id = "test-1",
            text = "Hello World",
            position = Offset(100f, 200f),
            fontSize = 24f,
            color = Color.White,
            rotation = 0f,
            scaleX = 1f,
            scaleY = 1f,
            hasShadow = false,
            hasStroke = false,
            alignment = TextAlignment.CENTER
        )

        assertEquals("test-1", overlay.id)
        assertEquals("Hello World", overlay.text)
        assertEquals(Offset(100f, 200f), overlay.position)
        assertEquals(24f, overlay.fontSize)
        assertEquals(Color.White, overlay.color)
        assertEquals(TextAlignment.CENTER, overlay.alignment)
    }

    @Test
    fun `textOverlay copy preserves unchanged values`() {
        val original = TextOverlay(
            id = "test-1",
            text = "Hello",
            position = Offset(100f, 200f),
            fontSize = 24f,
            color = Color.White,
            rotation = 0f,
            scaleX = 1f,
            scaleY = 1f,
            hasShadow = false,
            hasStroke = false,
            alignment = TextAlignment.CENTER
        )

        val modified = original.copy(text = "Modified")

        assertEquals("Hello", original.text)
        assertEquals("Modified", modified.text)
        assertEquals(original.id, modified.id)
        assertEquals(original.position, modified.position)
    }

    @Test
    fun `textAlignment enum values`() {
        assertEquals(3, TextAlignment.entries.size)
        assertNotNull(TextAlignment.LEFT)
        assertNotNull(TextAlignment.CENTER)
        assertNotNull(TextAlignment.RIGHT)
    }

    @Test
    fun `textFont enum values`() {
        assertTrue(TextFont.entries.size >= 5)
        assertEquals("Default", TextFont.DEFAULT.displayName)
        assertEquals("Serif", TextFont.SERIF.displayName)
        assertEquals("Monospace", TextFont.MONOSPACE.displayName)
        assertEquals("Sans Serif", TextFont.SANS_SERIF.displayName)
        assertEquals("Handwriting", TextFont.HANDWRITING.displayName)
    }
}

@RunWith(RobolectricTestRunner::class)
class MosaicToolTest {

    @Test
    fun `mosaicStroke stores values correctly`() {
        val stroke = MosaicStroke(
            id = "stroke-1",
            points = listOf(Offset(0f, 0f), Offset(10f, 10f), Offset(20f, 20f)),
            brushType = MosaicBrushType.PIXEL,
            brushSize = 20f,
            opacity = 1f
        )

        assertEquals("stroke-1", stroke.id)
        assertEquals(3, stroke.points.size)
        assertEquals(MosaicBrushType.PIXEL, stroke.brushType)
        assertEquals(20f, stroke.brushSize)
        assertEquals(1f, stroke.opacity)
    }

    @Test
    fun `mosaicBrushType enum values`() {
        assertEquals(3, MosaicBrushType.entries.size)
        assertNotNull(MosaicBrushType.PIXEL)
        assertNotNull(MosaicBrushType.BLUR)
        assertNotNull(MosaicBrushType.CUSTOM)
    }
}
