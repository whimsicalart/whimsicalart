package codes.pepper.whimsicalart.feature.editor

import androidx.compose.ui.geometry.Size
import codes.pepper.whimsicalart.feature.editor.ui.ImageCanvasFrame
import codes.pepper.whimsicalart.feature.editor.ui.imageFrameOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The fitted-image frame that source-space tools normalize against (P2). The box
 * is originally the whole edit target; the image shown with ContentScale.Fit
 * within it is letterboxed (offset by the leftover bars), so a point painted
 * over the image must be measured against this frame — not the whole box — to
 * bake to the correct source fraction.
 */
class ImageCanvasFrameTest {

    @Test
    fun `landscape image letterboxed in a tall box is vertically offset`() {
        // 1000x1200 box, 4:3 image fitted → 1000x750 at offset y=(1200-750)/2.
        val frame = imageFrameOf(Size(1000f, 1200f), Size(1000f, 750f))
        assertEquals(0f, frame.offsetX, 0.0001f)
        assertEquals(225f, frame.offsetY, 0.0001f)
        assertEquals(1000f, frame.size.width, 0.0001f)
        assertEquals(750f, frame.size.height, 0.0001f)
    }

    @Test
    fun `portrait image letterboxed in a wide box is horizontally offset`() {
        val frame = imageFrameOf(Size(1200f, 1000f), Size(600f, 1000f))
        assertEquals(300f, frame.offsetX, 0.0001f)
        assertEquals(0f, frame.offsetY, 0.0001f)
        assertEquals(600f, frame.size.width, 0.0001f)
        assertEquals(1000f, frame.size.height, 0.0001f)
    }

    @Test
    fun `image matching the box aspect has no letterbox offset`() {
        val frame = imageFrameOf(Size(1000f, 750f), Size(1000f, 750f))
        assertEquals(ImageCanvasFrame(0f, 0f, Size(1000f, 750f)), frame)
    }

    @Test
    fun `larger image than box has zero offset and keeps image size`() {
        val frame = imageFrameOf(Size(500f, 500f), Size(1000f, 1000f))
        assertEquals(0f, frame.offsetX, 0.0001f)
        assertEquals(0f, frame.offsetY, 0.0001f)
        assertEquals(1000f, frame.size.width, 0.0001f)
        assertEquals(1000f, frame.size.height, 0.0001f)
    }

    @Test
    fun `zero image size gives a zero-size frame with no offset`() {
        val frame = imageFrameOf(Size(1000f, 1000f), Size.Zero)
        assertEquals(0f, frame.offsetX, 0.0001f)
        assertEquals(0f, frame.offsetY, 0.0001f)
        assertEquals(Size.Zero, frame.size)
    }
}