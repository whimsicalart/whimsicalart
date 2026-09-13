package codes.pepper.whimsicalart.feature.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import codes.pepper.whimsicalart.feature.editor.domain.TextAlignment
import codes.pepper.whimsicalart.feature.editor.domain.TextBackgroundShape
import codes.pepper.whimsicalart.feature.editor.domain.TextFont
import codes.pepper.whimsicalart.feature.editor.domain.TextItem
import codes.pepper.whimsicalart.feature.editor.domain.TextStyleFlag
import codes.pepper.whimsicalart.feature.editor.ui.text.TextOverlay
import codes.pepper.whimsicalart.feature.editor.ui.text.toTextItem
import codes.pepper.whimsicalart.feature.editor.ui.text.toTextOverlay
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.MosaicStroke
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
            scale = 1f,
            hasShadow = false,
            hasOutline = false,
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
            scale = 1f,
            hasShadow = false,
            hasOutline = false,
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
        assertEquals(4, TextAlignment.entries.size)
        assertNotNull(TextAlignment.LEFT)
        assertNotNull(TextAlignment.CENTER)
        assertNotNull(TextAlignment.RIGHT)
        assertEquals(TextAlignment.JUSTIFIED, TextAlignment.entries.last())
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

    @Test
    fun `textOverlay toTextItem round trips through every param`() {
        val overlay = TextOverlay(
            id = "rt-1",
            text = "Round trip",
            position = Offset(123.5f, 77.25f),
            fontSize = 31f,
            color = Color(0x80FF8800),
            fontFamily = TextFont.SERIF,
            style = setOf(TextStyleFlag.BOLD, TextStyleFlag.UNDERLINE),
            alignment = TextAlignment.RIGHT,
            scale = 1.4f,
            rotation = -12f,
            hasShadow = true,
            shadowAngle = 200f,
            shadowDistance = 9.5f,
            shadowColor = Color(0x55000000),
            hasOutline = true,
            outlineSize = 3.25f,
            outlineColor = Color(0xFF2200AA),
            outlineBlur = 1.5f,
            backgroundShape = TextBackgroundShape.PILL,
            backgroundColor = Color(0x33FFFFFF)
        )
        val density = Density(2f)
        val item = overlay.toTextItem(density, frameWidthPx = 1000f, frameHeightPx = 800f)
        val restored = item.toTextOverlay(density, frameWidthPx = 1000f, frameHeightPx = 800f)

        assertEquals(overlay.id, restored.id)
        assertEquals(overlay.text, restored.text)
        assertEquals(overlay.position.x, restored.position.x, 0.01f)
        assertEquals(overlay.position.y, restored.position.y, 0.01f)
        assertEquals(overlay.fontSize, restored.fontSize, 0.01f)
        assertEquals(overlay.color, restored.color)
        assertEquals(overlay.fontFamily, restored.fontFamily)
        assertEquals(overlay.style, restored.style)
        assertEquals(overlay.alignment, restored.alignment)
        assertEquals(overlay.scale, restored.scale, 0.0001f)
        assertEquals(overlay.rotation, restored.rotation, 0.0001f)
        assertEquals(overlay.hasShadow, restored.hasShadow)
        assertEquals(overlay.shadowAngle, restored.shadowAngle, 0.01f)
        assertEquals(overlay.shadowDistance, restored.shadowDistance, 0.01f)
        assertEquals(overlay.shadowColor, restored.shadowColor)
        assertEquals(overlay.hasOutline, restored.hasOutline)
        assertEquals(overlay.outlineSize, restored.outlineSize, 0.01f)
        assertEquals(overlay.outlineColor, restored.outlineColor)
        assertEquals(overlay.outlineBlur, restored.outlineBlur, 0.01f)
        assertEquals(overlay.backgroundShape, restored.backgroundShape)
        assertEquals(overlay.backgroundColor, restored.backgroundColor)
    }

    @Test
    fun `toTextItem normalizes to the box size`() {
        val overlay = TextOverlay(
            id = "n-1",
            text = "Hi",
            position = Offset(250f, 200f),
            fontSize = 50f
        )
        val item = overlay.toTextItem(Density(1f), frameWidthPx = 1000f, frameHeightPx = 800f)
        assertEquals(0.25f, item.x, 0.0001f)
        assertEquals(0.25f, item.y, 0.0001f)
        assertEquals(0.05f, item.fontSize, 0.0001f)
        assertEquals(overlay.text, item.text)
        assertNotNull(item.background)
    }

    @Test
    fun `toTextItem normalizes against the fitted image rect and round trips`() {
        // P2: a letterboxed box (1000x1200, image frame 640x960 at offset 180,120)
        // must produce source fractions that match what the user painted over the
        // image, and the restore must land back on the same box position.
        val overlay = TextOverlay(
            id = "fr-1",
            text = "Fit",
            position = Offset(500f, 600f),
            fontSize = 60f
        )
        val density = Density(1f)
        val item = overlay.toTextItem(density, 640f, 960f, 180f, 120f)
        assertEquals(0.5f, item.x, 0.0001f)
        assertEquals(0.5f, item.y, 0.0001f)
        assertEquals(60f / 640f, item.fontSize, 0.0001f)
        val restored = item.toTextOverlay(density, 640f, 960f, 180f, 120f)
        assertEquals(overlay.position.x, restored.position.x, 0.01f)
        assertEquals(overlay.position.y, restored.position.y, 0.01f)
    }

    @Test
    fun `toTextItem without the letterbox offset equals the unletterboxed box case`() {
        // Backward-compat: offset 0 + frame == box keeps the old whole-box math.
        val overlay = TextOverlay(id = "n-2", text = "box", position = Offset(250f, 200f), fontSize = 50f)
        val item = overlay.toTextItem(Density(1f), 1000f, 800f, 0f, 0f)
        assertEquals(0.25f, item.x, 0.0001f)
        assertEquals(0.25f, item.y, 0.0001f)
    }

    @Test
    fun `transform added after committed text does not move the restored overlay`() {
        // Text anchored to its 1200x800 prefix footprint, captured against the
        // 900x600 fitted family frame. A transform appended BELOW the text does
        // not touch that footprint, so re-entry restores through the SAME frame
        // and the stored reference and must land at the exact dp position.
        val density = Density(1f)
        val overlay = TextOverlay(id = "a-t", text = "Hi", position = Offset(225f, 150f), fontSize = 45f)
        val item = overlay.toTextItem(density, 900f, 600f, 0f, 0f, 1200, 800)
        assertEquals(1200, item.referenceWidth)
        assertEquals(800, item.referenceHeight)

        val restored = item.toTextOverlay(density, 900f, 600f, 0f, 0f)
        assertEquals(overlay.position.x, restored.position.x, 0.01f)
        assertEquals(overlay.position.y, restored.position.y, 0.01f)
        assertEquals(overlay.fontSize, restored.fontSize, 0.01f)
    }

    @Test
    fun `anchored text restore re-expresses into a swapped base via the fit`() {
        // The text's prefix gains a 90-degree rotate: base 800x1200 fitted into
        // the same 900x600 box at scale 0.5 → 400x600 offset (250, 0). The stored
        // fractions belong to the 1200x800 anchor and map through the uniform fit
        // (scale 1/3, y-centre 166.67), not through the raw swapped-frame fraction.
        val density = Density(1f)
        val overlay = TextOverlay(id = "a-s", text = "Hi", position = Offset(225f, 150f), fontSize = 45f)
        val item = overlay.toTextItem(density, 900f, 600f, 0f, 0f, 1200, 800)

        val restored = item.toTextOverlay(density, 400f, 600f, 250f, 0f)
        // x = frameX(0.25, 1200) + offset = 0.25*1200/3 + 250 = 350; y = 166.67 + 0.25*800/3 = 233.33.
        assertEquals(350f, restored.position.x, 0.5f)
        assertEquals(233.33f, restored.position.y, 0.5f)
        // fontSize = measureX(45/900, 1200) = (45/900)*1200/3 = 20.
        assertEquals(20f, restored.fontSize, 0.5f)
    }
}

@RunWith(RobolectricTestRunner::class)
class MosaicToolTest {

    @Test
    fun `mosaicStroke stores values correctly`() {
        val stroke = MosaicStroke(
            id = "stroke-1",
            points = listOf(Offset(0f, 0f), Offset(10f, 10f), Offset(20f, 20f)),
            brushSize = 20f,
            opacity = 1f
        )

        assertEquals("stroke-1", stroke.id)
        assertEquals(3, stroke.points.size)
        assertEquals(20f, stroke.brushSize)
        assertEquals(1f, stroke.opacity)
    }
}
