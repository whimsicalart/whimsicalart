package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec: docs/effects_reference.md → Stickers/Text "Actionable area" — the touch
 * targets span the real bounds inflated outward by 50% of the size + 48 dp, and
 * overlapping items resolve to the already-selected (grab) or the closest centre
 * (tap / no selection), with exact-equal centres broken by insertion order.
 *
 * Pure geometry (no Android), so it runs in plain JUnit even where
 * Robolectric artifacts are unavailable.
 */
class OverlayGesturesTest {

    private val insetPx = 48f * 2f // 48 dp at density 2

    private fun target(id: String, cx: Float, cy: Float, w: Float = 100f, h: Float = 60f, order: Int) =
        OverlayHitTarget(id, Offset(cx, cy), Size(w, h), order)

    @Test
    fun `tap inside the real bounds resolves that item`() {
        val t = listOf(target("a", 100f, 100f, order = 0))
        assertEquals("a", resolveOverlayTarget(Offset(100f, 100f), t, insetPx, null, OverlayGestureKind.TAP))
    }

    @Test
    fun `tap in the expanded ring but outside the real bounds still resolves`() {
        val t = listOf(target("a", 100f, 100f, w = 20f, h = 20f, order = 0))
        // Real bounds are 90..110; the +50% + 48dp ring reaches far beyond.
        assertEquals("a", resolveOverlayTarget(Offset(140f, 100f), t, insetPx, null, OverlayGestureKind.TAP))
    }

    @Test
    fun `tap beyond the actionable area resolves nothing`() {
        val t = listOf(target("a", 100f, 100f, w = 20f, h = 20f, order = 0))
        assertNull(resolveOverlayTarget(Offset(10000f, 10000f), t, insetPx, null, OverlayGestureKind.TAP))
    }

    @Test
    fun `overlapping tap picks the closest centre regardless of order`() {
        // 'a' is added first, but 'b' sits physically closer to the tap.
        val t = listOf(
            target("a", 100f, 100f, order = 0),
            target("b", 160f, 100f, order = 1)
        )
        assertEquals("b", resolveOverlayTarget(Offset(150f, 100f), t, insetPx, null, OverlayGestureKind.TAP))
    }

    @Test
    fun `grab applies to the already-selected item among overlapping candidates`() {
        val t = listOf(
            target("a", 100f, 100f, order = 0),
            target("b", 160f, 100f, order = 1)
        )
        // 'a' is selected although 'b' is closer to the touch.
        assertEquals("a", resolveOverlayTarget(Offset(150f, 100f), t, insetPx, "a", OverlayGestureKind.GRAB))
    }

    @Test
    fun `grab with no candidate selected falls back to the closest centre`() {
        val t = listOf(
            target("a", 100f, 100f, order = 0),
            target("b", 160f, 100f, order = 1)
        )
        assertEquals("b", resolveOverlayTarget(Offset(150f, 100f), t, insetPx, null, OverlayGestureKind.GRAB))
    }

    @Test
    fun `selected item outside the candidates does not win`() {
        val t = listOf(
            target("a", 100f, 100f, order = 0),
            target("b", 160f, 100f, order = 1)
        )
        // 'c' is selected but not touchable here; a/b resolve between themselves.
        assertEquals("b", resolveOverlayTarget(Offset(150f, 100f), t, insetPx, "c", OverlayGestureKind.GRAB))
    }

    @Test
    fun `exactly equal centres fall back to list insertion order`() {
        val t = listOf(
            target("first", 100f, 100f, order = 0),
            target("second", 100f, 100f, order = 1)
        )
        assertEquals("first", resolveOverlayTarget(Offset(100f, 100f), t, insetPx, null, OverlayGestureKind.TAP))
        assertEquals("first", resolveOverlayTarget(Offset(100f, 100f), t, insetPx, null, OverlayGestureKind.GRAB))
    }

    @Test
    fun `expanded area grows with the item size`() {
        val small = target("s", 100f, 100f, w = 10f, h = 10f, order = 0)
        val large = target("l", 100f, 100f, w = 200f, h = 100f, order = 1)
        // Both centered at the same point: the larger one reaches further out.
        val far = Offset(300f, 100f)
        assertNull(resolveOverlayTarget(far, listOf(small), insetPx, null, OverlayGestureKind.TAP))
        assertEquals("l", resolveOverlayTarget(far, listOf(large), insetPx, null, OverlayGestureKind.TAP))
    }

    // ---- Corner/edge resize grab band (spec: effects_reference.md → Stickers/Text → *Resize grab band*)

    private fun resizeTarget(cx: Float, cy: Float, w: Float, h: Float, rotation: Float = 0f, scale: Float = 1f) =
        OverlayResizeTarget("sel", Offset(cx, cy), Size(w, h), rotation, scale)

    @Test
    fun `touch exactly on the selection corner grabs the corner handle`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f)
        assertEquals(
            OverlayResizeHandle.BOTTOM_RIGHT,
            hitTestResizeBand(t, Offset(150f, 130f), insetPx)
        )
        assertEquals(
            OverlayResizeHandle.TOP_LEFT,
            hitTestResizeBand(t, Offset(50f, 70f), insetPx)
        )
    }

    @Test
    fun `touch in the expanded ring around the top edge grabs the top edge`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f)
        // 30 px above the real top edge — still inside the expanded band.
        assertEquals(OverlayResizeHandle.TOP, hitTestResizeBand(t, Offset(100f, 40f), insetPx))
        assertEquals(OverlayResizeHandle.BOTTOM, hitTestResizeBand(t, Offset(100f, 160f), insetPx))
        assertEquals(OverlayResizeHandle.LEFT, hitTestResizeBand(t, Offset(40f, 100f), insetPx))
        assertEquals(OverlayResizeHandle.RIGHT, hitTestResizeBand(t, Offset(160f, 100f), insetPx))
    }

    @Test
    fun `corner region wins over the edges`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f)
        // local (35, 30): inside BOTH the right and bottom bands → corner.
        assertEquals(OverlayResizeHandle.BOTTOM_RIGHT, hitTestResizeBand(t, Offset(135f, 130f), insetPx))
        // local (35, 10): right band only → right edge.
        assertEquals(OverlayResizeHandle.RIGHT, hitTestResizeBand(t, Offset(135f, 110f), insetPx))
    }

    @Test
    fun `touch in the real-edge inner band resizes instead of moving`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f)
        // local (35, 0): between the 20% line (x=30) and the real right edge (x=50).
        assertEquals(OverlayResizeHandle.RIGHT, hitTestResizeBand(t, Offset(135f, 100f), insetPx))
    }

    @Test
    fun `touch deeper inside the item moves instead of resizing`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f)
        // local (20, 10): inside the 20% lines on both axes → MOVE (null).
        assertNull(hitTestResizeBand(t, Offset(120f, 110f), insetPx))
    }

    @Test
    fun `touch beyond the expanded band resolves nothing`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f)
        // edgeX = 50 + 50 + 96 = 196; beyond that is outside.
        assertNull(hitTestResizeBand(t, Offset(400f, 100f), insetPx))
        assertNull(hitTestResizeBand(t, Offset(100f, 400f), insetPx))
    }

    @Test
    fun `rotated item grab band follows the rotated corner handles`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f, rotation = 90f)
        // The bottom-right corner rotated 90° lands to the LEFT of the centre.
        assertEquals(
            OverlayResizeHandle.BOTTOM_RIGHT,
            hitTestResizeBand(t, Offset(70f, 150f), insetPx)
        )
        // Deep inside still moves.
        assertNull(hitTestResizeBand(t, Offset(100f, 100f), insetPx))
    }

    // ---- Resize math: uniform scale from the opposite corner

    @Test
    fun `corner drag scales uniformly and keeps the opposite corner fixed`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f, scale = 1f)
        val r = resizeFromHandle(t, OverlayResizeHandle.BOTTOM_RIGHT, Offset(180f, 150f), 0.3f, 5f)
        // Distances from the fixed top-left anchor (50,70):
        // original corner 116.62 px, new finger 152.64 px → scale ~1.309.
        assertEquals(1.309f, r.scale, 0.01f)
        assertEquals(115.45f, r.center.x, 0.01f)
        assertEquals(109.27f, r.center.y, 0.01f)
        // Aspect ratio is preserved (both axes grew by the same factor).
        val grown = Size(100f * r.scale, 60f * r.scale)
        assertEquals(100f / 60f, grown.width / grown.height, 1e-3f)
        // The OPPOSITE corner did not move: new centre − grown half-offset = anchor.
        val oppositeCorner = Offset(
            r.center.x - rotateVector(Offset(50f * r.scale, 30f * r.scale), 0f).x,
            r.center.y - rotateVector(Offset(50f * r.scale, 30f * r.scale), 0f).y
        )
        assertEquals(50f, oppositeCorner.x, 0.01f)
        assertEquals(70f, oppositeCorner.y, 0.01f)
    }

    @Test
    fun `edge drag scales along its axis and the orthogonal axis follows`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f, scale = 1f)
        val r = resizeFromHandle(t, OverlayResizeHandle.RIGHT, Offset(300f, 100f), 0.3f, 5f)
        // Anchor (50,100) → original right edge 100 px away, finger 250 px away.
        assertEquals(2.5f, r.scale, 0.001f)
        assertEquals(175f, r.center.x, 0.001f)
        assertEquals(100f, r.center.y, 0.001f)
    }

    @Test
    fun `resize is clamped to the allowed scale range`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f, scale = 1f)
        val r = resizeFromHandle(t, OverlayResizeHandle.RIGHT, Offset(5000f, 100f), 0.3f, 5f)
        assertEquals(5f, r.scale, 1e-3f)
    }

    @Test
    fun `resize at the resting corner keeps scale and centre unchanged`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f, scale = 1f)
        val r = resizeFromHandle(t, OverlayResizeHandle.BOTTOM_RIGHT, Offset(150f, 130f), 0.3f, 5f)
        assertEquals(1f, r.scale, 1e-5f)
        assertEquals(100f, r.center.x, 1e-4f)
        assertEquals(100f, r.center.y, 1e-4f)
    }

    @Test
    fun `rotated item anchor stays fixed in world space`() {
        val t = resizeTarget(100f, 100f, w = 100f, h = 60f, rotation = 90f, scale = 1f)
        val r = resizeFromHandle(t, OverlayResizeHandle.BOTTOM_RIGHT, Offset(15f, 230f), 0.3f, 5f)
        // World-space anchor = center - rotate(BR offset, 90°) = center - (-30, 50) = (130, 50).
        assertTrue(r.scale > 1f)
        assertEquals(130f, r.center.x - rotateVector(Offset(50f * r.scale, 30f * r.scale), 90f).x, 0.01f)
        assertEquals(50f, r.center.y - rotateVector(Offset(50f * r.scale, 30f * r.scale), 90f).y, 0.01f)
    }

    // ---- Resize follows the SAME resolution rule as grab, on the RESOLVED
    // target's band (spec: effects_reference.md → Stickers/Text → "Resize
    // follows the same target resolution as grab"). These tests model the
    // gesture-start decision exactly as the gesture code performs it:
    // resolve the target ONCE with GRAB, then band-test that resolved target's
    // geometry — so an UNSELECTED item's corner resizes on the first drag,
    // while a touch inside the selected item's expanded area stays on it.

    private fun resolveResizeAtStart(
        touch: Offset,
        targets: List<OverlayHitTarget>,
        selectedId: String?,
        geometry: (id: String) -> OverlayResizeTarget?,
        insetPx: Float
    ): Pair<String?, OverlayResizeHandle?> {
        val id = resolveOverlayTarget(touch, targets, insetPx, selectedId, OverlayGestureKind.GRAB) ?: return null to null
        return id to (geometry(id)?.let { hitTestResizeBand(it, touch, insetPx) })
    }

    @Test
    fun `drag on the selected items corner resizes the selected item`() {
        val touch = Offset(150f, 130f) // bottom-right corner of 'a'
        val targets = listOf(
            target("a", 100f, 100f, w = 100f, h = 60f, order = 0),
            target("b", 400f, 400f, w = 100f, h = 60f, order = 1)
        )
        val geometry: (id: String) -> OverlayResizeTarget? = { id ->
            if (id == "a") resizeTarget(100f, 100f, w = 100f, h = 60f) else resizeTarget(400f, 400f, w = 100f, h = 60f)
        }
        val (id, handle) = resolveResizeAtStart(touch, targets, selectedId = "a", geometry, insetPx)
        assertEquals("a", id)
        assertEquals(OverlayResizeHandle.BOTTOM_RIGHT, handle)
    }

    @Test
    fun `drag on an unselected items corner resizes it on the first drag`() {
        // No selected item covers the touch: GRAB resolves to the nearest centre
        // ('b'), and the band is evaluated on B's geometry → first-drag resize.
        val touch = Offset(410f, 130f) // bottom-right corner of 'b'
        val targets = listOf(
            target("a", 100f, 100f, w = 100f, h = 60f, order = 0),
            target("b", 360f, 100f, w = 100f, h = 60f, order = 1)
        )
        val geometry: (id: String) -> OverlayResizeTarget? = { id ->
            if (id == "a") resizeTarget(100f, 100f, w = 100f, h = 60f) else resizeTarget(360f, 100f, w = 100f, h = 60f)
        }
        val (id, handle) = resolveResizeAtStart(touch, targets, selectedId = "a", geometry, insetPx)
        assertEquals("b", id)
        assertEquals(OverlayResizeHandle.BOTTOM_RIGHT, handle)
    }

    @Test
    fun `drag in the selected expanded area over a neighbours band stays on the selected`() {
        // Touch (140,80) sits over 'b''s left-top grab band AND inside 'a''s
        // expanded actionable area. The selected item wins (GRAB) and only its
        // own band is evaluated — the gesture stays on 'a', never leaks to 'b'.
        val touch = Offset(140f, 80f)
        val targets = listOf(
            target("a", 100f, 100f, w = 100f, h = 60f, order = 0),
            target("b", 170f, 100f, w = 60f, h = 60f, order = 1)
        )
        val geometry: (id: String) -> OverlayResizeTarget? = { id ->
            if (id == "a") resizeTarget(100f, 100f, w = 100f, h = 60f) else resizeTarget(170f, 100f, w = 60f, h = 60f)
        }
        val (id, handle) = resolveResizeAtStart(touch, targets, selectedId = "a", geometry, insetPx)
        assertEquals("a", id)
        // The band test ran on 'a''s geometry: (140,80) is a's right-top ring.
        assertEquals(OverlayResizeHandle.TOP_RIGHT, handle)
        // Sanity: without the selection the same touch WOULD target 'b'.
        val (idNoSel, _) = resolveResizeAtStart(touch, targets, selectedId = null, geometry, insetPx)
        assertEquals("b", idNoSel)
    }
}