package codes.pepper.whimsicalart.feature.editor.ui.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import codes.pepper.whimsicalart.feature.editor.domain.TextBackgroundShape
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec: docs/effects_reference.md → Text → *Selection* — the selection grid
 * always outlines the item's OUTER box regardless of the `TextBackgroundShape`
 * (the ROUNDED / OVAL / PILL clips are confined to the inner fill box, so they
 * can never shear the rectangular frame or its handles).
 *
 * Pure geometry, so it runs in plain JUnit.
 */
class TextSelectionGridTest {

    private val size = Size(120f, 80f)
    private val outerCorners = listOf(
        Offset(0f, 0f),
        Offset(size.width, 0f),
        Offset(0f, size.height),
        Offset(size.width, size.height)
    )

    @Test
    fun `frame anchors are the outer box corners for every background shape`() {
        for (shape in TextBackgroundShape.entries) {
            assertEquals("anchors for $shape", outerCorners, TextSelectionGrid.anchors(size))
        }
    }

    @Test
    fun `frame spans the full parsed bounds never insetting a corner`() {
        for (shape in TextBackgroundShape.entries) {
            val anchors = TextSelectionGrid.anchors(size)
            assertEquals("right extent for $shape", size.width, anchors.maxOf { it.x }, 0f)
            assertEquals("bottom extent for $shape", size.height, anchors.maxOf { it.y }, 0f)
            assertEquals(0f, anchors.minOf { it.x }, 0f)
            assertEquals(0f, anchors.minOf { it.y }, 0f)
        }
    }

    @Test
    fun `anchors track the item dimensions`() {
        val wide = TextSelectionGrid.anchors(Size(300f, 40f))
        assertEquals(Offset(300f, 0f), wide[1])
        assertEquals(Offset(300f, 40f), wide[3])
    }

    @Test
    fun `handle radius is a fixed 10 px`() {
        assertEquals(10f, TextSelectionGrid.HANDLE_RADIUS, 0f)
    }
}