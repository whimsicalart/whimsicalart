package codes.pepper.whimsicalart.feature.editor.domain

import codes.pepper.whimsicalart.core.domain.GeometryAnchor
import codes.pepper.whimsicalart.feature.stickers.domain.StickerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ANCHORED POSITIONAL GEOMETRY (tracker): the commit/re-commit reconciliation
 * of sticker / text / brush-stroke families — a positional layer must FROZEN
 * the geometry and reference it was committed with, and only RE-ANCHOR (never
 * silently re-normalise against the latest dims) when the layer's own base
 * actually changes (docs/effect_stack.md → *Positional layers*, rules 1+4).
 */
class PositionalGeometryTest {

    private val refA = GeometryAnchor(1000, 800)
    private val refB = GeometryAnchor(500, 400)

    private fun sticker(
        x: Float = 0.5f,
        y: Float = 0.5f,
        w: Float = 0.4f,
        h: Float = 0.4f,
        ref: GeometryAnchor = refA
    ) = StickerItem(
        stickerId = "s1",
        emojiText = "\uD83D\uDE00",
        x = x,
        y = y,
        widthFrac = w,
        heightFrac = h,
        referenceWidth = ref.width,
        referenceHeight = ref.height
    )

    private fun stroke(
        x: Float = 0.5f,
        y: Float = 0.5f,
        brush: Float = 0.1f,
        ref: GeometryAnchor = refA
    ) = StrokeLayer(
        type = StrokeType.MOSAIC,
        points = listOf(x to y, 0.25f to 0.5f),
        brushSize = brush,
        referenceWidth = ref.width,
        referenceHeight = ref.height
    )

    // ------------------------------------------------------------------ (a)
    // Commit / re-enter / re-commit without moving: the stored list and its
    // reference are carried verbatim — pixel-identical, never refreshed.

    @Test
    fun `first commit adopts the live capture as-is`() {
        val derived = listOf(sticker())
        val out = PositionalReconcilers.stickers(null, GeometryAnchor(), derived, refA)
        assertEquals(derived, out)
        assertSame(derived.first(), out.first())
    }

    @Test
    fun `no-move re-commit freezes the stored list instance and reference`() {
        val stored = listOf(sticker(ref = refA))
        val derived = listOf(sticker(ref = refA))

        // Reference unchanged AND geometry reproduced by the round-trip →
        // verbatim freeze (state refers to the exact same object, so the
        // commit is a true no-op and the carried preview survives).
        val out = PositionalReconcilers.stickers(stored, refA, derived, refA)
        assertSame(stored, out)
        assertSame(stored.first(), out.first())
        assertEquals(1000, out.first().referenceWidth)
        assertEquals(800, out.first().referenceHeight)
    }

    @Test
    fun `re-commit restores the exact originally committed geometry, not the latest base`() {
        val stored = listOf(
            sticker(x = 0.3f, y = 0.2f, w = 0.25f, h = 0.18f, ref = refA)
        )
        // Re-entry re-derives identical fractions (restore → capture round-trip).
        val derived = listOf(
            sticker(x = 0.3f, y = 0.2f, w = 0.25f, h = 0.18f, ref = refA)
        )
        val out = PositionalReconcilers.stickers(stored, refA, derived, refA)
        assertSame(stored, out)
        assertEquals(0.3f, out.first().x, 1e-4f)
        assertEquals(0.25f, out.first().widthFrac, 1e-4f)
    }

    // ------------------------------------------------------------------ (b)
    // A composite change AFTER a layer never re-anchors it (own base unchanged).

    @Test
    fun `composite change after the layer keeps its geometry and reference frozen`() {
        val stored = listOf(sticker(ref = refA))
        val derived = listOf(sticker(ref = refA))
        val out = PositionalReconcilers.stickers(stored, refA, derived, refA)
        assertSame(stored, out)
    }

    @Test
    fun `stored reference is never refreshed when nothing before the layer changed`() {
        // The classic bug: rebuild re-normalised the layer against the LATEST
        // stack dims and stamped them back into the committed reference even
        // though no-move happened. The reconciler must carry the stored ref as
        // long as the own base is unchanged, regardless of re-commits.
        val stored = listOf(sticker(ref = refA))
        repeat(3) {
            val out = PositionalReconcilers.stickers(stored, refA, listOf(sticker(ref = refA)), refA)
            assertSame(stored, out)
        }
    }

    // ------------------------------------------------------------------ (c)
    // Own-base change: re-anchor through the centre-origin uniform fit and
    // record the new base — the object keeps its exact on-image spot and size.

    @Test
    fun `own-base change re-anchors and records the new base`() {
        val stored = listOf(sticker(x = 0.5f, y = 0.5f, w = 0.4f, h = 0.4f, ref = refA))
        val derived = listOf(sticker(ref = refA)) // identical round-trip (no edit)
        val out = PositionalReconcilers.stickers(stored, refA, derived, refB)

        // Same aspect → every fraction unchanged, only the reference re-recorded.
        assertEquals(0.5f, out.first().x, 1e-4f)
        assertEquals(0.5f, out.first().y, 1e-4f)
        assertEquals(0.4f, out.first().widthFrac, 1e-4f)
        assertEquals(0.4f, out.first().heightFrac, 1e-4f)
        assertEquals(500, out.first().referenceWidth)
        assertEquals(400, out.first().referenceHeight)
    }

    @Test
    fun `own-base change with a different aspect re-expresses through the fit`() {
        val stored = listOf(sticker(x = 0.5f, y = 0.5f, w = 0.4f, h = 0.4f, ref = refA))
        val out = PositionalReconcilers.stickers(
            stored, refA, listOf(sticker(ref = refA)), GeometryAnchor(400, 800)
        )
        // fit = min(400/1000, 800/800) = 0.4, centred vertically (offsetY 240).
        assertEquals(0.5f, out.first().x, 1e-4f)     // centre stays centre
        assertEquals(0.5f, out.first().y, 1e-4f)     // centre stays centre
        assertEquals(160f, out.first().widthFrac * 400f, 1e-3f)   // 0.4·1000·0.4 px
        assertEquals(128f, out.first().heightFrac * 800f, 1e-3f)  // 0.4·800·0.4 px
        assertEquals(400, out.first().referenceWidth)
        assertEquals(800, out.first().referenceHeight)
    }

    // ------------------------------------------------------------------ (d)
    // Reorder / multi-family: only the family whose own base changed re-anchors.

    @Test
    fun `unrelated family re-commit leaves the other family frozen`() {
        val stickerStored = listOf(sticker(ref = refA))
        val textStored = listOf(
            TextItem(
                id = "t1", text = "hi", color = 0xFF000000.toInt(),
                fontSize = 0.06f, x = 0.3f, y = 0.4f,
                referenceWidth = refA.width, referenceHeight = refA.height
            )
        )
        // A change BEFORE stickers (its own base → refB) re-anchors stickers…
        val stickerOut = PositionalReconcilers.stickers(
            stickerStored, refA, listOf(sticker(ref = refB)), refB
        )
        assertEquals(500, stickerOut.first().referenceWidth)
        assertEquals(400, stickerOut.first().referenceHeight)
        // …while text (base unchanged) stays byte-for-byte frozen.
        val textOut = PositionalReconcilers.texts(
            textStored, refA, textStored, refA
        )
        assertSame(textStored, textOut)
    }

    // ---------------------------------------------------------- strokes too

    @Test
    fun `strokes freeze on no-move re-commit`() {
        val stored = listOf(stroke(ref = refA))
        val out = PositionalReconcilers.strokes(stored, refA, listOf(stroke(ref = refA)), refA)
        assertSame(stored, out)
    }

    @Test
    fun `strokes re-anchor every point and brush size when the base changes`() {
        val stored = listOf(stroke(x = 0.5f, y = 0.5f, brush = 0.1f, ref = refA))
        val out = PositionalReconcilers.strokes(stored, refA, listOf(stroke(ref = refA)), refB)
        val s = out.first()
        assertEquals(0.5f, s.points[0].first, 1e-4f)
        assertEquals(0.5f, s.points[0].second, 1e-4f)
        assertEquals(0.25f, s.points[1].first, 1e-4f)
        assertEquals(0.1f, s.brushSize, 1e-4f)
        assertEquals(500, s.referenceWidth)
        assertEquals(400, s.referenceHeight)
    }

    @Test
    fun `actively edited strokes win over the stored family`() {
        val stored = listOf(stroke(ref = refA))
        val edited = listOf(
            StrokeLayer(
                type = StrokeType.MOSAIC,
                points = listOf(0.51f to 0.5f),
                brushSize = 0.05f,
                referenceWidth = refA.width,
                referenceHeight = refA.height
            )
        )
        val out = PositionalReconcilers.strokes(stored, refA, edited, refA)
        assertSame(edited, out)
    }

    @Test
    fun `emptied family adopts the empty derivation`() {
        val stored = listOf(stroke(ref = refA))
        val out = PositionalReconcilers.strokes(stored, refA, emptyList(), refA)
        assertTrue(out.isEmpty())
    }

    // --------------------------------------------- re-entry restore parity
    // toFramePixels maps a committed stroke back into box pixels EXACTLY as
    // StrokeOverlayRenderer maps it into any destination (same uniform fit).

    @Test
    fun `re-entry restore maps committed strokes through the same fit as the renderer`() {
        val committed = stroke(x = 0.5f, y = 0.5f, brush = 0.1f, ref = refA)
        val (pts, size) = committed.toFramePixels(
            frameOffsetX = 10f, frameOffsetY = 20f,
            frameWidthPx = 1000f, frameHeightPx = 800f
        )
        // UniformFit.of(1000, 800, 1000, 800) is the identity → offset only.
        assertEquals(510f, pts[0].first, 1e-3f)   // 10 + 0.5·1000
        assertEquals(420f, pts[0].second, 1e-3f)  // 20 + 0.5·800
        assertEquals(100f, size, 1e-3f)           // 0.1·1000
    }

    @Test
    fun `re-entry restore re-anchors to compact base via the fit`() {
        // Committed against 1000x800, now the family's base is 500x400 →
        // the restored point still lands on the image centre (250,200).
        val committed = stroke(x = 0.5f, y = 0.5f, brush = 0.1f, ref = refA)
        val (pts, size) = committed.toFramePixels(
            frameOffsetX = 0f, frameOffsetY = 0f,
            frameWidthPx = 500f, frameHeightPx = 400f
        )
        assertEquals(250f, pts[0].first, 1e-3f)
        assertEquals(200f, pts[0].second, 1e-3f)
        assertEquals(50f, size, 1e-3f) // 0.1·1000·0.5
    }

    @Test
    fun `unset reference keeps the legacy identity mapping`() {
        val legacy = StrokeLayer(
            type = StrokeType.BLUR,
            points = listOf(0.25f to 0.75f),
            brushSize = 0.1f
        )
        val (pts, size) = legacy.toFramePixels(
            frameOffsetX = 4f, frameOffsetY = 8f, frameWidthPx = 200f, frameHeightPx = 100f
        )
        assertEquals(54f, pts[0].first, 1e-3f)   // 4 + 0.25·200
        assertEquals(83f, pts[0].second, 1e-3f)  // 8 + 0.75·100
        assertEquals(20f, size, 1e-3f)           // 0.1·200
    }
}