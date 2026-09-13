package codes.pepper.whimsicalart.feature.editor.domain

import androidx.compose.ui.geometry.Size
import codes.pepper.whimsicalart.feature.editor.ui.familyAnchorFor
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The anchored-reference CAPTURE for a positional family (docs/effect_stack.md
 * → *Positional layers*, rule 1): the stored refWidth×refHeight must be the
 * actual prefix output footprint at the layer's own stack position — a single
 * fold of the committed prefix from the stack's TRUE base. The capture used to
 * start from the ALREADY-ROTATED `rotatedImageSize`, and since the committed
 * prefix ALSO contains the Transform layer, the rotation was applied twice —
 * the stale double-rotated reference no longer matched the base the layer folds
 * over, so the sticker/text/brush re-rendered against the wrong frame and
 * drifted off-centre exactly when a transform was (re)added to the stack.
 */
class FamilyAnchorBaseTest {

    private fun stickerStack(first: StackEffect): List<StackEffect> =
        listOf(first, StickerEffect(layers = emptyList()))

    @Test
    fun `family anchor folds the prefix from the true stack base not the rotated viewport`() {
        // 960×640 base + a committed base 90° rotation: the sticker's prefix
        // output is the single-folded 640×960 — never the double-swapped 960×640
        // that `rotatedImageSize` as the start dims would produce.
        val anchor = familyAnchorFor(
            stickerStack(TransformEffect(rotation = 90f)),
            EditTool.STICKERS,
            Size(960f, 640f),
            Size(1920f, 1080f)
        )
        assertEquals(640, anchor.refWidth)
        assertEquals(960, anchor.refHeight)
    }

    @Test
    fun `family anchor does not double-count a free-angle rotation`() {
        // 960×640 @ 30°: W' = 960·cos30 + 640·sin30 ≈ 1151, H' = 960·sin30 + 640·cos30 ≈ 1034.
        val anchor = familyAnchorFor(
            stickerStack(TransformEffect(rotation = 30f)),
            EditTool.STICKERS,
            Size(960f, 640f),
            Size(1920f, 1080f)
        )
        assertEquals("single-fold width, not the double-counted 1514", 1151, anchor.refWidth)
        assertEquals("single-fold height, not the double-counted 1470", 1034, anchor.refHeight)
    }

    @Test
    fun `family anchor is the single-fold footprint of the committed prefix from the true base`() {
        // The concrete example from docs/effect_stack.md (sticker at (0.75, 0.5)
        // of its 960×640 base with a 45° free-rotate): the reference must be the
        // single-fold (1131×1131) footprint — the actual base the sticker folds
        // over — never the double-counted footprint a pre-rotated start would
        // produce.
        val anchor = familyAnchorFor(
            stickerStack(TransformEffect(rotation = 45f)),
            EditTool.STICKERS,
            Size(960f, 640f),
            Size(1920f, 1080f)
        )
        assertEquals(1131, anchor.refWidth)
        assertEquals(1131, anchor.refHeight)
    }

    @Test
    fun `no-rotation family anchor is the unrotated base`() {
        val anchor = familyAnchorFor(emptyList(), EditTool.STICKERS, Size(960f, 640f), Size(1920f, 1080f))
        assertEquals(960, anchor.refWidth)
        assertEquals(640, anchor.refHeight)
    }
}