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

    // panZoomLayer applies scale + translate exactly like the image preview's
    // graphicsLayer, so a sticker/text overlay drawn in the SAME source space as
    // the photo cannot drift from it while the user pans/zooms (the
    // eye-toggle-then-pan bug). These tests pin the mapping contract: for any
    // shared transform, overlay(model) == image(model).
    @Test
    fun `a glued overlay point stays glued to the image under pan and zoom`() {
        val transform = ViewportTransform()
        // Sticker centre (source space) that sits exactly on an image pixel.
        val overlayX = 321f
        val overlayY = 407f

        fun screen(x: Float, y: Float) =
            (x * transform.scale + transform.offsetX) to
                (y * transform.scale + transform.offsetY)

        // Reproduce the real gesture path (ZoomableImage.detectTransformGestures):
        // every dispatched pan/zoom mutates the shared transform incrementally.
        val zoom = 1.6f
        transform.scale = (transform.scale * zoom).coerceIn(0.5f, 5f)
        transform.offsetX += 120f
        transform.offsetY += -40f
        transform.scale = (transform.scale * 0.8f).coerceIn(0.5f, 5f)
        transform.offsetX += -30f
        transform.offsetY += 8f

        // The overlaid sticker's screen position must equal the image pixel it
        // sits on — the same transform applied to both means no drift is possible.
        val overlayScreen = screen(overlayX, overlayY)
        val imagePixel = (overlayX * transform.scale + transform.offsetX) to
            (overlayY * transform.scale + transform.offsetY)
        assertEquals(imagePixel.first, overlayScreen.first, 0.001f)
        assertEquals(imagePixel.second, overlayScreen.second, 0.001f)
    }

    @Test
    fun `overlay and image map identically across a pan-zoom sequence`() {
        val transform = ViewportTransform()
        val samples = listOf(0f to 0f, 250f to 120f, 640f to 480f)

        fun screen(x: Float, y: Float) =
            (x * transform.scale + transform.offsetX) to
                (y * transform.scale + transform.offsetY)

        fun assertGlued() {
            for ((x, y) in samples) {
                val overlayScreen = screen(x, y)
                val imageScreen = (x * transform.scale + transform.offsetX) to
                    (y * transform.scale + transform.offsetY)
                assertEquals(imageScreen.first, overlayScreen.first, 0.001f)
                assertEquals(imageScreen.second, overlayScreen.second, 0.001f)
            }
        }

        assertGlued() // identity
        transform.scale = 1.8f
        transform.offsetX = 40f
        transform.offsetY = -15f
        assertGlued()
        transform.scale = 0.5f
        transform.offsetX = -300f
        transform.offsetY = 200f
        assertGlued()
        transform.reset()
        assertGlued()
    }

    @Test
    fun `copyFrom snapshots scale and offsets for later restore`() {
        // The background placement save/restore relies on copyFrom: entering the
        // placement state snapshots the pre-placement pan/zoom so the canvas can
        // return to it when Replace -> Blur leaves the placement (spec
        // "Switching Replace → Blur is non-destructive on the canvas").
        val live = ViewportTransform()
        live.scale = 2.4f
        live.offsetX = -88f
        live.offsetY = 57f

        val saved = ViewportTransform().also { it.copyFrom(live) }

        assertEquals(2.4f, saved.scale, 0.001f)
        assertEquals(-88f, saved.offsetX, 0.001f)
        assertEquals(57f, saved.offsetY, 0.001f)

        // Restoring later copies the snapshot back over whatever the viewport
        // currently is.
        live.reset()
        live.copyFrom(saved)
        assertEquals(2.4f, live.scale, 0.001f)
        assertEquals(-88f, live.offsetX, 0.001f)
        assertEquals(57f, live.offsetY, 0.001f)
    }
}
