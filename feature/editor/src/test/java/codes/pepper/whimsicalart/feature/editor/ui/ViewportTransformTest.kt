package codes.pepper.whimsicalart.feature.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportTransformTest {

    @Test
    fun `reset restores default scale and offsets`() {
        val transform = ViewportTransform()
        transform.scale = 2.5f
        transform.offsetX = 120f
        transform.offsetY = -50f

        transform.reset()

        assertEquals(1f, transform.scale, 0.001f)
        assertEquals(0f, transform.offsetX, 0.001f)
        assertEquals(0f, transform.offsetY, 0.001f)
    }

    @Test
    fun `reset from defaults keeps defaults`() {
        val transform = ViewportTransform()
        transform.reset()
        assertEquals(1f, transform.scale, 0.001f)
        assertEquals(0f, transform.offsetX, 0.001f)
        assertEquals(0f, transform.offsetY, 0.001f)
    }
}
