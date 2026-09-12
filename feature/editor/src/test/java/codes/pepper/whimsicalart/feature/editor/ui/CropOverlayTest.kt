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

    // --- normalizedAspectRatio + reshapeCropToRatio (exact fixed ratios) ------
    // The image is letterboxed with ContentScale.Fit: a frame whose NORMALISED
    // aspect is `r` displays as r·(W/H) on screen and crops to r·(W/H)·(H/W) =
    // r pixels. So the frame must be locked to `ratio·(H/W)` in normalised units
    // for the selected ratio to be EXACT (1:1 square etc.) both on screen and in
    // the committed pixels — the regression this guards against.

    @Test
    fun `normalized target compensates for the image aspect`() {
        // Landscape image 400x200: a displayed 1:1 square needs a 0.5-wide-for-1
        // normalized frame (fit stretches normalized width by 2).
        assertEquals(0.5f, normalizedAspectRatio(1f, Size(400f, 200f)), 0.001f)
        // Portrait image 200x400: a displayed 1:1 square needs a 2:1 frame.
        assertEquals(2f, normalizedAspectRatio(1f, Size(200f, 400f)), 0.001f)
        // 4:3 on a 3:2 image.
        assertEquals(
            (4f / 3f) * (2f / 3f), normalizedAspectRatio(4f / 3f, Size(3f, 2f)), 0.001f
        )
        // Unknown image size falls back verbatim.
        assertEquals(1f, normalizedAspectRatio(1f, Size.Zero), 0.001f)
    }

    private fun displayAspect(frame: Rect, image: Size): Float =
        frame.width / frame.height * image.width / image.height

    @Test
    fun `selected ratios reshape to EXACT on-screen ratios on landscape and portrait`() {
        // Verify every supported ratio, on both a landscape and a portrait image:
        // the reshaped frame must display at exactly the selected ratio and its
        // committed pixel crop must be exactly ratio too.
        val images = listOf(Size(4000f, 3000f), Size(3000f, 4000f))
        for (image in images) {
            val full = Rect(0f, 0f, 1f, 1f)
            for (selector in CropAspectRatio.entries) {
                val ratio = selector.ratio ?: continue
                val target = normalizedAspectRatio(ratio, image)
                var frame = reshapeCropToRatio(full, target)
                // On-screen (letterboxed) aspect is exactly the selected ratio.
                assertEquals(
                    "${selector.label} on $image", ratio, displayAspect(frame, image), 0.001f
                )
                // The committed pixel crop ratio equals the selected ratio.
                assertEquals(
                    "${selector.label} pixel crop on $image",
                    ratio, (frame.width * image.width) / (frame.height * image.height), 0.001f
                )
            }
        }
    }

    @Test
    fun `a selected ratio stays locked while dragging a corner`() {
        val image = Size(4000f, 3000f)
        val ratio = 1f
        val target = normalizedAspectRatio(ratio, image)
        var frame = reshapeCropToRatio(Rect(0f, 0f, 1f, 1f), target)
        frame = dragCropRect(frame, CropDragMode.RESIZE_TOP_LEFT, dx = -0.1f, dy = 0.05f, ratio = target)
        assertEquals("1:1 stays 1:1 while dragging an edge/corner", ratio, displayAspect(frame, image), 0.001f)
        // And the pixel crop stays exactly ratio.
        assertEquals(ratio, (frame.width * image.width) / (frame.height * image.height), 0.001f)
    }

    @Test
    fun `a selected ratio stays locked while pinching`() {
        val image = Size(3000f, 4000f)
        val ratio = 16f / 9f
        val target = normalizedAspectRatio(ratio, image)
        var frame = reshapeCropToRatio(Rect(0.1f, 0.1f, 0.9f, 0.9f), target)
        val pinch = scaleCropRect(frame, 1.3f, ratio = target)
        assertEquals("16:9 stays locked while pinching", ratio, displayAspect(pinch, image), 0.001f)
        assertEquals(ratio, (pinch.width * image.width) / (pinch.height * image.height), 0.001f)
    }

    @Test
    fun `switching from one fixed ratio to ANOTHER takes the new ratio exactly, not a leftover`() {
        val image = Size(4000f, 3000f)
        // Landscape photo, 9:16 first → the frame becomes portrait (normalised
        // 9/16·(H/W) = 0.4219), then 16:9 → the frame must be exactly 16:9 again.
        var frame = reshapeCropToRatio(Rect(0f, 0f, 1f, 1f), normalizedAspectRatio(9f / 16f, image))
        assertTrue("first selection is portrait", frame.width < frame.height)
        frame = reshapeCropToRatio(frame, normalizedAspectRatio(16f / 9f, image))
        assertEquals("9:16 then 16:9 ends exactly 16:9", 16f / 9f, displayAspect(frame, image), 0.001f)
        assertEquals(16f / 9f, (frame.width * image.width) / (frame.height * image.height), 0.001f)
        // And the reverse order: 16:9 first, then 9:16 → exactly 9:16.
        var reverse = reshapeCropToRatio(Rect(0f, 0f, 1f, 1f), normalizedAspectRatio(16f / 9f, image))
        assertTrue("first selection is landscape", reverse.width > reverse.height)
        reverse = reshapeCropToRatio(reverse, normalizedAspectRatio(9f / 16f, image))
        assertEquals("16:9 then 9:16 ends exactly 9:16", 9f / 16f, displayAspect(reverse, image), 0.001f)
        assertEquals(9f / 16f, (reverse.width * image.width) / (reverse.height * image.height), 0.001f)
    }

    @Test
    fun `switching ratios after a manual move resize keeps the selected ratio centred in the box`() {
        val image = Size(4000f, 3000f)
        // Manually moved/resized frame (offset from the full box): 0.68 x 0.55.
        val moved = Rect(0.12f, 0.2f, 0.8f, 0.75f)
        val switched = reshapeCropToRatio(moved, normalizedAspectRatio(4f / 3f, image))
        assertEquals("switched ratio locks exactly 4:3", 4f / 3f, displayAspect(switched, image), 0.001f)
        // The reshape PRESERVES the moved frame's area (0.68 x 0.55 = 0.374) at
        // the new ratio instead of largest-fiting inside the moved bounds — so it
        // may legitimately span wider/taller than the moved frame, and is clipped
        // only to the normalized box, keeping the same centre.
        assertEquals(
            "the switch preserves the moved frame's area",
            moved.width * moved.height, switched.width * switched.height, 0.001f
        )
        assertEquals("switch keeps the frame centred on the moved centre",
            moved.center.x, switched.center.x, 0.001f)
        assertEquals(moved.center.y, switched.center.y, 0.001f)
        assertTrue(switched.left >= 0f && switched.top >= 0f && switched.right <= 1f && switched.bottom <= 1f)
    }

    @Test
    fun `a pinch right after a ratio switch keeps the newly selected ratio`() {
        val image = Size(4000f, 3000f)
        var frame = reshapeCropToRatio(Rect(0f, 0f, 1f, 1f), normalizedAspectRatio(9f / 16f, image))
        frame = reshapeCropToRatio(frame, normalizedAspectRatio(16f / 9f, image))
        val pinched = scaleCropRect(frame, 1.6f, ratio = normalizedAspectRatio(16f / 9f, image))
        assertEquals("pinch after the switch keeps 16:9", 16f / 9f, displayAspect(pinched, image), 0.001f)
        assertEquals(16f / 9f, (pinched.width * image.width) / (pinched.height * image.height), 0.001f)
    }

    @Test
    fun `reshape keeps the frame inside the box and actually reshapes`() {
        val image = Size(4000f, 3000f)
        val frame = reshapeCropToRatio(Rect(0f, 0f, 1f, 1f), normalizedAspectRatio(16f / 9f, image))
        assertTrue(frame.left >= 0f && frame.top >= 0f && frame.right <= 1f && frame.bottom <= 1f)
        assertEquals(16f / 9f, displayAspect(frame, image), 0.001f)
        assertTrue("16:9 must change the full 4:3 frame", frame != Rect(0f, 0f, 1f, 1f))
    }

    // --- Area-preserving reshape (shrink-accumulation fix) -------------------
    // Switching ratios must keep the frame's SCALE, not largest-fit inside the
    // previous (already-reduced) frame — otherwise 1:1 → 4:3 → 1:1 → 4:3 keeps
    // shrinking. Area preservation means returning to a ratio reproduces the
    // very first selection's size exactly (when no axis overflows the box).

    @Test
    fun `alternating 1-1 and 4-3 on a landscape image round-trips without residual shrink`() {
        val image = Size(4000f, 3000f)
        // First 1:1 selection from the full 4:3 image: largest-fit → 0.75 x 1.
        val first = reshapeCropToRatio(Rect(0f, 0f, 1f, 1f), normalizedAspectRatio(1f, image))
        assertEquals(0.75f, first.width, 0.001f)
        assertEquals(1f, first.height, 0.001f)
        // Switch to 4:3 (normalised target on this image = 1) then BACK to 1:1:
        // the area never changes, so the return equals the first selection — the
        // alternating 1:1 → 4:3 → 1:1 loop that used to shrink every switch.
        var frame = reshapeCropToRatio(first, normalizedAspectRatio(4f / 3f, image))
        frame = reshapeCropToRatio(frame, normalizedAspectRatio(1f, image))
        assertEquals("1:1 return equals the first selection's width",
            first.width, frame.width, 0.001f)
        assertEquals("1:1 return equals the first selection's height",
            first.height, frame.height, 0.001f)
        assertEquals("the 1:1 return stays exactly 1:1", 1f, displayAspect(frame, image), 0.001f)
    }

    @Test
    fun `alternating 16-9 and 9-16 on a square image round-trips without residual shrink`() {
        val image = Size(2000f, 2000f)
        // First 16:9 selection on the square image: 1 x 9/16 (largest-fit).
        val first = reshapeCropToRatio(Rect(0f, 0f, 1f, 1f), normalizedAspectRatio(16f / 9f, image))
        assertEquals("16:9 first selection is strictly landscape", 1f, first.width, 0.001f)
        assertEquals(9f / 16f, first.height, 0.001f)
        // 16:9 → 9:16 → 16:9: both orientations FIT the square box at the
        // preserved area, so returning reproduces the first selection exactly.
        var frame = reshapeCropToRatio(first, normalizedAspectRatio(9f / 16f, image))
        assertEquals("9:16 switch flips the frame to portrait", 9f / 16f, frame.width, 0.001f)
        assertEquals(1f, frame.height, 0.001f)
        frame = reshapeCropToRatio(frame, normalizedAspectRatio(16f / 9f, image))
        assertEquals("16:9 return equals the first selection",
            first.width, frame.width, 0.001f)
        assertEquals(first.height, frame.height, 0.001f)
        assertEquals("the 16:9 return stays exactly 16:9",
            16f / 9f, displayAspect(frame, image), 0.001f)
    }

    @Test
    fun `a ratio whose preserved area overflows a tall image clamps in-box keeping the exact ratio`() {
        val image = Size(3000f, 4000f)
        // 4:3 on the tall (3:4) image: normalised target 4/3·(4/3) = 16/9, whose
        // sqrt(area·ratio) width (1.33 from the full box) overflows — the clamp
        // caps width at 1 and derives the height, keeping [ratio] exact in-box.
        val target = normalizedAspectRatio(4f / 3f, image)
        val frame = reshapeCropToRatio(Rect(0f, 0f, 1f, 1f), target)
        assertTrue(
            "clamped frame stays in the normalized box",
            frame.left >= 0f && frame.top >= 0f && frame.right <= 1f && frame.bottom <= 1f
        )
        assertEquals("the width-clamp yields width 1", 1f, frame.width, 0.001f)
        assertEquals("4:3 stays exactly 4:3 on the tall image",
            4f / 3f, displayAspect(frame, image), 0.001f)
        assertEquals(4f / 3f, (frame.width * image.width) / (frame.height * image.height), 0.001f)
    }

    // --- crop-after-merge anchored to the CURRENT fold base ------------------

    @Test
    fun `crop overlay anchors to the merged base extent not the original image dims`() {
        // The pristine photo the user loaded (what the overlay MUST NOT anchor to
        // after a merge).
        val originalImage = Size(1000f, 1000f)
        // mergeLayers repoints the fold base to the merged result — larger here
        // (e.g. after an enlarging merge). The crop overlay must span THIS.
        val mergedBase = cropImageSize(Size(1600f, 1200f), rotationDegrees = 0f)
        assertEquals("resolver reports the merged base dims", Size(1600f, 1200f), mergedBase)

        // Letterbox the merged base into the crop box: 4:3 in a 900x900 box.
        val fit = fitRect(Size(900f, 900f), mergedBase)
        assertEquals(900f, fit.size.width, 0.001f)
        assertEquals(675f, fit.size.height, 0.001f)
        assertEquals(0f, fit.offset.x, 0.001f)
        assertEquals((900f - 675f) / 2f, fit.offset.y, 0.001f)

        // A full-frame crop normalized against the merged base rect spans the
        // ENTIRE merged extent in-bounds — not clamped to a 1000x1000 original.
        val full = Rect(0f, 0f, 1f, 1f)
        val display = Rect(
            left = fit.offset.x + full.left * fit.size.width,
            top = fit.offset.y + full.top * fit.size.height,
            right = fit.offset.x + full.right * fit.size.width,
            bottom = fit.offset.y + full.bottom * fit.size.height
        )
        assertEquals("full-frame crop covers the whole box width", 0f, display.left, 0.001f)
        assertEquals(900f, display.right, 0.001f)
        assertEquals("full-frame crop covers the whole letterbox height", fit.offset.y, display.top, 0.001f)
        assertEquals(fit.offset.y + fit.size.height, display.bottom, 0.001f)

        // Contrast: anchoring to the old original dims would letterbox a 1:1
        // into the same full square and would NOT span the merged (4:3) extent.
        val oldFit = fitRect(Size(900f, 900f), originalImage)
        assertTrue(
            "merged-base letterbox is shorter than the original 1:1 fill",
            fit.size.height < oldFit.size.height
        )
    }
}
