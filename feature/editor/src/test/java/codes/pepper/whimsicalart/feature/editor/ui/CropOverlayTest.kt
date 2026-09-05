package codes.pepper.whimsicalart.feature.editor.ui

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
    fun `touch up to 20 percent inside an edge resizes on that axis not move`() {
        // crop rect 200x140; 20% inside = 40px horizontal, 28px vertical.
        val crop = Rect(100f, 120f, 300f, 260f)
        // ~15% inside the left edge, vertically centred -> resize left.
        assertEquals(CropDragMode.RESIZE_LEFT, hitTestCrop(crop, Offset(130f, 190f)))
        // ~15% inside the right edge -> resize right.
        assertEquals(CropDragMode.RESIZE_RIGHT, hitTestCrop(crop, Offset(270f, 190f)))
        // ~15% inside the top edge, horizontally centred -> resize top.
        assertEquals(CropDragMode.RESIZE_TOP, hitTestCrop(crop, Offset(200f, 130f)))
        // ~15% inside the bottom edge -> resize bottom.
        assertEquals(CropDragMode.RESIZE_BOTTOM, hitTestCrop(crop, Offset(200f, 250f)))
    }

    @Test
    fun `touch inside a corner band resizes on both axes`() {
        val crop = Rect(100f, 120f, 300f, 260f)
        // ~15% inside both the left and top edges -> top-left corner resize.
        assertEquals(CropDragMode.RESIZE_TOP_LEFT, hitTestCrop(crop, Offset(130f, 140f)))
        // ~15% inside both the right and bottom edges -> bottom-right corner resize.
        assertEquals(CropDragMode.RESIZE_BOTTOM_RIGHT, hitTestCrop(crop, Offset(270f, 240f)))
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

    // --- scaleCropRect (pinch) ---------------------------------------------

    @Test
    fun `pinch scale grows the rect around its center keeping it centred`() {
        val initial = Rect(0.2f, 0.3f, 0.6f, 0.7f) // centre (0.4, 0.5), w/h 0.4
        val scaled = scaleCropRect(initial, 2f)
        assertEquals(0.4f, scaled.center.x, 0.001f)
        assertEquals(0.5f, scaled.center.y, 0.001f)
        assertEquals(0.8f, scaled.width, 0.001f)
        assertEquals(0.8f, scaled.height, 0.001f)
        assertEquals(0f, scaled.left, 0.001f)
        assertEquals(0.1f, scaled.top, 0.001f)
    }

    @Test
    fun `pinch scale shrinks the rect around its center`() {
        val initial = Rect(0.1f, 0.1f, 0.9f, 0.9f)
        val scaled = scaleCropRect(initial, 0.5f)
        assertEquals(0.5f, scaled.center.x, 0.001f)
        assertEquals(0.5f, scaled.center.y, 0.001f)
        assertEquals(0.4f, scaled.width, 0.001f)
        assertEquals(0.4f, scaled.height, 0.001f)
    }

    @Test
    fun `pinch scale respects a fixed aspect ratio`() {
        val initial = Rect(0.1f, 0.25f, 0.5f, 0.75f) // 0.4 wide x 0.5 high (ratio 0.8)
        val scaled = scaleCropRect(initial, 1f, ratio = 1f)
        assertEquals(0.3f, scaled.center.x, 0.001f)
        assertEquals(0.5f, scaled.center.y, 0.001f)
        assertEquals(0.4f, scaled.width, 0.001f)
        assertEquals(0.4f, scaled.height, 0.001f)
    }

    @Test
    fun `pinch scale is clamped to the normalized bounds`() {
        val initial = Rect(0.2f, 0.3f, 0.4f, 0.5f)
        val scaled = scaleCropRect(initial, 10f)
        assertEquals(0f, scaled.left, 0.001f)
        assertEquals(0f, scaled.top, 0.001f)
        assertEquals(1f, scaled.right, 0.001f)
        assertEquals(1f, scaled.bottom, 0.001f)
    }

    @Test
    fun `pinch scale is clamped to the minimum size`() {
        val initial = Rect(0.2f, 0.3f, 0.6f, 0.7f)
        val scaled = scaleCropRect(initial, 0.001f)
        assertEquals(NORMALIZED_MIN_SIZE, scaled.width, 0.001f)
        assertEquals(NORMALIZED_MIN_SIZE, scaled.height, 0.001f)
    }

    // --- Manual resize + aspect-ratio enforcement (regression) ---------------
    // These document the contract that guards the gesture fix: the manual resize
    // handler (dragCropRect) and the pinch handler (scaleCropRect) must not fight.
    // The pinch handler SKIPS zoom == 1 (single-finger drags), because an identity
    // scale would re-fit the rect to the aspect ratio and clobber the manual
    // corner/edge resize. The tests below prove the manual resize DOES enforce the
    // selected ratio (Fix: "aspect ratio must be enforced on the crop frame") and
    // that an identity pinch would otherwise refit it (the reason the handler
    // must gate on real zoom).

    @Test
    fun `manual edge resize enforces the selected 16-9 ratio`() {
        // Frame is wider than tall; a 16:9 ratio must produce a frame whose
        // width/height equals 16/9 when resizing an edge.
        val initial = Rect(0.1f, 0.3f, 0.6f, 0.6f) // w 0.5, h 0.3
        val ratio = 16f / 9f
        val resized = dragCropRect(initial, CropDragMode.RESIZE_BOTTOM, dy = 0.2f, ratio = ratio)
        assertEquals(
            "resized frame must match the selected 16:9 ratio",
            ratio, resized.width / resized.height, 0.001f
        )
    }

    @Test
    fun `manual corner resize keeps the selected 1-1 ratio anchored to the opposite corner`() {
        val initial = Rect(0.1f, 0.1f, 0.9f, 0.9f)
        val resized = dragCropRect(
            initial, CropDragMode.RESIZE_TOP_RIGHT, dx = -0.05f, dy = -0.02f, ratio = 1f
        )
        // Dragging the top-right corner must keep the opposite (bottom-left) corner
        // fixed and force the frame to the 1:1 ratio.
        assertEquals(1f, resized.width / resized.height, 0.001f)
        assertEquals(0.1f, resized.left, 0.001f)
        assertEquals(0.9f, resized.bottom, 0.001f)
    }

    @Test
    fun `identity pinch refits to ratio so the handler must skip zoom equals one`() {
        // Documents WHY the gesture handler must ignore zoom == 1 (single-finger
        // drags): an identity scale re-fits to the ratio, which would otherwise
        // overwrite a manual resize that the user just performed.
        val initial = Rect(0.2f, 0.4f, 0.6f, 0.7f) // w 0.4, h 0.3 (ratio ~1.33, not 1:1)
        val refit = scaleCropRect(initial, 1f, ratio = 1f)
        assertEquals(1f, refit.width / refit.height, 0.001f)
        assertTrue(
            "identity pinch must not be applied on single-finger drags",
            kotlin.math.abs(refit.width - initial.width) > 0.001f
        )
    }
}
