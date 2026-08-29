package com.whimsicalart.feature.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropOverlayTest {

    @Test
    fun `portrait image in a landscape box keeps aspect and centers`() {
        // Box 1000x800, image (rotated) 300x500 -> scale = min(1000/300, 800/500) = min(3.333, 1.6) = 1.6
        val fit = fitRect(Size(1000f, 800f), Size(300f, 500f))
        assertEquals(300f * 1.6f, fit.size.width, 0.001f) // 480
        assertEquals(500f * 1.6f, fit.size.height, 0.001f) // 800
        // Centred horizontally: (1000 - 480)/2 = 260, vertically 0.
        assertEquals(260f, fit.offset.x, 0.001f)
        assertEquals(0f, fit.offset.y, 0.001f)
    }

    @Test
    fun `landscape image fills width and centres vertically`() {
        // Box 800x1000, image 400x200 -> scale = min(800/400, 1000/200) = min(2, 5) = 2
        val fit = fitRect(Size(800f, 1000f), Size(400f, 200f))
        assertEquals(400f * 2f, fit.size.width, 0.001f) // 800
        assertEquals(200f * 2f, fit.size.height, 0.001f) // 400
        assertEquals(0f, fit.offset.x, 0.001f)
        assertEquals(300f, fit.offset.y, 0.001f) // (1000-400)/2
    }

    @Test
    fun `unavailable image size falls back to the whole box`() {
        val fit = fitRect(Size(500f, 500f), Size.Zero)
        assertEquals(500f, fit.size.width, 0.001f)
        assertEquals(500f, fit.size.height, 0.001f)
        assertEquals(Offset.Zero, fit.offset)
    }

    @Test
    fun `fits within the box in both dimensions`() {
        val fit = fitRect(Size(600f, 900f), Size(1000f, 1200f))
        assertTrue(fit.size.width <= 600f)
        assertTrue(fit.size.height <= 900f)
        // Aspect ratio preserved.
        assertEquals(1000f / 1200f, fit.size.width / fit.size.height, 0.001f)
    }

    // --- hitTestCrop -------------------------------------------------------

    @Test
    fun `touch on a corner selects that corner mode`() {
        val crop = Rect(100f, 120f, 300f, 260f)
        assertEquals(CropDragMode.RESIZE_TOP_LEFT, hitTestCrop(crop, Offset(108f, 128f)))
        assertEquals(CropDragMode.RESIZE_TOP_RIGHT, hitTestCrop(crop, Offset(292f, 128f)))
        assertEquals(CropDragMode.RESIZE_BOTTOM_LEFT, hitTestCrop(crop, Offset(108f, 252f)))
        assertEquals(CropDragMode.RESIZE_BOTTOM_RIGHT, hitTestCrop(crop, Offset(292f, 252f)))
    }

    @Test
    fun `touch on an edge selects that edge mode, not move`() {
        val crop = Rect(100f, 120f, 300f, 260f)
        assertEquals(CropDragMode.RESIZE_LEFT, hitTestCrop(crop, Offset(110f, 180f)))
        assertEquals(CropDragMode.RESIZE_RIGHT, hitTestCrop(crop, Offset(290f, 180f)))
        assertEquals(CropDragMode.RESIZE_TOP, hitTestCrop(crop, Offset(200f, 130f)))
        assertEquals(CropDragMode.RESIZE_BOTTOM, hitTestCrop(crop, Offset(200f, 250f)))
    }

    @Test
    fun `touch strictly inside the frame moves the crop`() {
        val crop = Rect(100f, 120f, 300f, 260f)
        assertEquals(CropDragMode.MOVE, hitTestCrop(crop, Offset(200f, 190f)))
    }

    @Test
    fun `touch outside the frame is ignored`() {
        val crop = Rect(100f, 120f, 300f, 260f)
        assertNull(hitTestCrop(crop, Offset(30f, 40f)))
        assertNull(hitTestCrop(crop, Offset(350f, 300f)))
        assertNull(hitTestCrop(crop, Offset(150f, 10f)))
    }

    // --- dragCropRect ------------------------------------------------------

    @Test
    fun `move translates the whole rect keeping its size`() {
        val initial = Rect(0.2f, 0.3f, 0.6f, 0.7f)
        val moved = dragCropRect(initial, CropDragMode.MOVE, 0.1f, -0.05f)
        assertEquals(0.3f, moved.left, 0.001f)
        assertEquals(0.25f, moved.top, 0.001f)
        assertEquals(0.7f, moved.right, 0.001f)
        assertEquals(0.65f, moved.bottom, 0.001f)
        assertEquals(initial.width, moved.width, 0.001f)
        assertEquals(initial.height, moved.height, 0.001f)
    }

    @Test
    fun `resize left shrinks the left edge anchored to the right`() {
        val initial = Rect(0.2f, 0.3f, 0.6f, 0.7f)
        val resized = dragCropRect(initial, CropDragMode.RESIZE_LEFT, 0.1f)
        assertEquals(0.3f, resized.left, 0.001f)
        assertEquals(0.6f, resized.right, 0.001f)
        assertEquals(0.3f, resized.top, 0.001f)
        assertEquals(0.7f, resized.bottom, 0.001f)
    }

    @Test
    fun `resize bottom right grows the frame from the top left`() {
        val initial = Rect(0.2f, 0.3f, 0.6f, 0.7f)
        val resized = dragCropRect(initial, CropDragMode.RESIZE_BOTTOM_RIGHT, 0.1f, 0.2f)
        assertEquals(0.2f, resized.left, 0.001f)
        assertEquals(0.3f, resized.top, 0.001f)
        assertEquals(0.7f, resized.right, 0.001f)
        assertEquals(0.9f, resized.bottom, 0.001f)
    }

    @Test
    fun `dragging outside the frame leaves the rect untouched`() {
        val initial = Rect(0.2f, 0.3f, 0.6f, 0.7f)
        assertEquals(initial, dragCropRect(initial, null, 0.3f, 0.3f))
    }

    @Test
    fun `resize is clamped to the minimum size`() {
        val initial = Rect(0f, 0f, 1f, 1f)
        val resized = dragCropRect(initial, CropDragMode.RESIZE_LEFT, 1f)
        assertEquals(0.95f, resized.left, 0.001f)
        assertEquals(0.05f, resized.width, 0.001f)
    }

    @Test
    fun `move is clamped inside the normalized box preserving size`() {
        val initial = Rect(0f, 0f, 0.5f, 0.5f)
        val moved = dragCropRect(initial, CropDragMode.MOVE, -1f, -1f)
        assertEquals(0f, moved.left, 0.001f)
        assertEquals(0f, moved.top, 0.001f)
        assertEquals(0.5f, moved.right, 0.001f)
        assertEquals(0.5f, moved.bottom, 0.001f)
        assertEquals(0.5f, moved.width, 0.001f)
        assertEquals(0.5f, moved.height, 0.001f)
    }

    // --- dragCropRect with aspect ratio ------------------------------------

    @Test
    fun `ratio keeps a left resize square anchored to the right edge`() {
        val initial = Rect(0.2f, 0.2f, 0.6f, 0.7f)
        val resized = dragCropRect(initial, CropDragMode.RESIZE_LEFT, 0.05f, ratio = 1f)
        assertEquals(0.1f, resized.left, 0.001f)
        assertEquals(0.6f, resized.right, 0.001f)
        assertEquals(0.5f, resized.width, 0.001f)
        assertEquals(0.5f, resized.height, 0.001f)
    }

    @Test
    fun `ratio keeps a bottom resize anchored to the top edge`() {
        val initial = Rect(0.2f, 0.3f, 0.7f, 0.6f)
        val resized = dragCropRect(initial, CropDragMode.RESIZE_BOTTOM, dy = 0.3f, ratio = 1f)
        assertEquals(0.3f, resized.top, 0.001f)
        assertEquals(0.8f, resized.bottom, 0.001f)
        assertEquals(0.5f, resized.width, 0.001f)
        assertEquals(0.5f, resized.height, 0.001f)
    }

    @Test
    fun `ratio corner resize is anchored to the opposite corner`() {
        val initial = Rect(0.1f, 0.1f, 0.9f, 0.9f)
        val resized = dragCropRect(
            initial,
            CropDragMode.RESIZE_TOP_LEFT,
            dx = -0.05f,
            dy = -0.02f,
            ratio = 1f
        )
        assertEquals(0.9f, resized.right, 0.001f)
        assertEquals(0.9f, resized.bottom, 0.001f)
        assertEquals(0.85f, resized.width, 0.001f)
        assertEquals(0.85f, resized.height, 0.001f)
    }
}
