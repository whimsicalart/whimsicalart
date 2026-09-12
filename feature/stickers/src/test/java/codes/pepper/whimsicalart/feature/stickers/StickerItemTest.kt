package codes.pepper.whimsicalart.feature.stickers

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.feature.stickers.domain.EMOJI_STICKER_BASE_SIZE_DP
import codes.pepper.whimsicalart.feature.stickers.domain.StickerItem
import codes.pepper.whimsicalart.feature.stickers.domain.StickerLineStyle
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPlacement
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPresets
import codes.pepper.whimsicalart.feature.stickers.domain.baseSizeDp
import codes.pepper.whimsicalart.feature.stickers.domain.toStickerItem
import codes.pepper.whimsicalart.feature.stickers.domain.toStickerPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickerItemTest {

    private val density = Density(2f)
    private val boxW = 800f
    private val boxH = 600f

    @Test
    fun `line settings round-trip losslessly through the stack entry`() {
        val sticker = StickerPresets.getStickerById("heart_1")!!
        val placement = StickerPlacement(
            stickerId = sticker.id,
            x = 120f,
            y = 80f,
            scaleX = 1.2f,
            scaleY = 1.2f,
            lineStyle = StickerLineStyle.DASHED,
            lineThickness = 0.12f,
            lineColor = 0xFF00FF00.toInt()
        )

        // Placement → stack item keeps every line field...
        val item = placement.toStickerItem(sticker, density, boxW, boxH)!!
        assertEquals(StickerLineStyle.DASHED, item.lineStyle)
        assertEquals(0.12f, item.lineThickness, 0.0001f)
        assertEquals(0xFF00FF00.toInt(), item.lineColor)

        // ...and restoring re-opens the exact same line settings (re-commit path
        // EditorScreen.kt builds the placement list from the stack entry).
        val restored = item.toStickerPlacement(
            density = density,
            frameWidthPx = boxW,
            frameHeightPx = boxH,
            baseSize = Size(sticker.width.toFloat(), sticker.height.toFloat())
        )
        assertEquals(placement.lineStyle, restored.lineStyle)
        assertEquals(placement.lineThickness, restored.lineThickness, 0.001f)
        assertEquals(placement.lineColor, restored.lineColor)
    }

    @Test
    fun `line settings default to no line for a fresh placement`() {
        val placement = StickerPlacement(
            stickerId = "gallery:content://media/picker/fresh",
            x = 0f,
            y = 0f,
            imageUri = "content://media/picker/fresh"
        )
        assertEquals(StickerLineStyle.NONE, placement.lineStyle)
        assertEquals(0.05f, placement.lineThickness, 0.001f)
        assertNull(placement.lineColor)

        val item = placement.toStickerItem(null, density, boxW, boxH)!!
        assertEquals(StickerLineStyle.NONE, item.lineStyle)
        assertNull(item.lineColor)

        val restored = item.toStickerPlacement(
            density = density,
            frameWidthPx = boxW,
            frameHeightPx = boxH,
            baseSize = Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP)
        )
        assertNull(restored.lineColor)
    }

    @Test
    fun `preset placement round-trips losslessly`() {
        val sticker = StickerPresets.getStickerById("star_1")!!
        val placement = StickerPlacement(
            stickerId = sticker.id,
            x = 120f,
            y = 80f,
            scaleX = 1.5f,
            scaleY = 1.5f,
            rotation = 30f,
            opacity = 0.6f,
            isFlipped = true,
            tintColor = 0xFFFF0000.toInt()
        )

        val item = placement.toStickerItem(
            sticker = sticker,
            density = density,
            frameWidthPx = boxW,
            frameHeightPx = boxH
        )!!

        assertEquals(placement.x, item.x * boxW.toFloat() / density.density, 0.001f)
        assertEquals(placement.y, item.y * boxH.toFloat() / density.density, 0.001f)
        assertEquals(1.5f, item.widthFrac * boxW / (sticker.width * density.density), 0.001f)
        assertEquals(30f, item.rotationDegrees, 0.001f)
        assertEquals(0.6f, item.opacity, 0.001f)
        assertEquals(0xFFFF0000.toInt(), item.tintColor)
        assertEquals(true, item.isFlipped)
        assertEquals(sticker.drawableRes, item.drawableRes)

        val restored = item.toStickerPlacement(
            density = density,
            frameWidthPx = boxW,
            frameHeightPx = boxH,
            baseSize = Size(sticker.width.toFloat(), sticker.height.toFloat())
        )

        assertEquals(placement.x, restored.x, 0.001f)
        assertEquals(placement.y, restored.y, 0.001f)
        assertEquals(placement.scaleX, restored.scaleX, 0.001f)
        assertEquals(placement.scaleY, restored.scaleY, 0.001f)
        assertEquals(placement.rotation, restored.rotation, 0.001f)
        assertEquals(placement.opacity, restored.opacity, 0.001f)
        assertEquals(placement.isFlipped, restored.isFlipped)
        assertEquals(placement.tintColor, restored.tintColor)
        assertEquals(placement.emojiText, restored.emojiText)
    }

    @Test
    fun `emoji placement round-trips losslessly`() {
        val placement = StickerPlacement(
            stickerId = "emoji:😀",
            x = 200f,
            y = 100f,
            scaleX = 2f,
            scaleY = 2f,
            rotation = 10f,
            emojiText = "😀"
        )

        val item = placement.toStickerItem(
            sticker = null,
            density = density,
            frameWidthPx = boxW,
            frameHeightPx = boxH
        )!!

        assertNull(item.drawableRes)
        assertEquals("😀", item.emojiText)

        val restored = item.toStickerPlacement(
            density = density,
            frameWidthPx = boxW,
            frameHeightPx = boxH,
            baseSize = Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP)
        )

        assertEquals(placement.x, restored.x, 0.001f)
        assertEquals(placement.y, restored.y, 0.001f)
        assertEquals(placement.scaleX, restored.scaleX, 0.001f)
        assertEquals(placement.rotation, restored.rotation, 0.001f)
        assertEquals("😀", restored.emojiText)
    }

    @Test
    fun `unknown sticker id without emoji text maps to null`() {
        val placement = StickerPlacement(stickerId = "ghost", x = 0f, y = 0f)
        val item = placement.toStickerItem(null, density, boxW, boxH)
        assertEquals(null, item)
    }

    @Test
    fun `zero box size maps to null`() {
        val sticker = StickerPresets.stickers.first()
        val placement = StickerPlacement(stickerId = sticker.id, x = 0f, y = 0f)
        val item = placement.toStickerItem(sticker, density, 0f, 600f)
        assertEquals(null, item)
    }

    @Test
    fun `gallery placement round-trips without a preset`() {
        val uri = "content://media/picker/123"
        val placement = StickerPlacement(
            stickerId = "gallery:$uri",
            x = 150f,
            y = 90f,
            scaleX = 2f,
            scaleY = 2f,
            rotation = 15f,
            opacity = 0.7f,
            imageUri = uri
        )

        val item = placement.toStickerItem(
            sticker = null,
            density = density,
            frameWidthPx = boxW,
            frameHeightPx = boxH
        )!!

        assertNull(item.drawableRes)
        assertNull(item.emojiText)
        assertEquals(uri, item.imageUri)
        // Base falls back to the emoji default ((150dp * 2) * density 2 = 600px).
        assertEquals(0.75f, item.widthFrac, 0.001f)
        assertEquals(1.0f, item.heightFrac, 0.001f)
        assertEquals(0.7f, item.opacity, 0.001f)

        val restored = item.toStickerPlacement(
            density = density,
            frameWidthPx = boxW,
            frameHeightPx = boxH,
            baseSize = Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP)
        )

        assertEquals(uri, restored.imageUri)
        assertEquals("gallery:$uri", restored.stickerId)
        assertEquals(2f, restored.scaleX, 0.001f)
        assertEquals(0.7f, restored.opacity, 0.001f)
        assertEquals(15f, restored.rotation, 0.001f)
    }

    @Test
    fun `gallery item base size is the emoji default`() {
        val placement = StickerPlacement(
            stickerId = "gallery:x",
            x = 0f,
            y = 0f,
            imageUri = "content://media/picker/x"
        )
        val item = placement.toStickerItem(null, density, boxW, boxH)!!
        assertEquals(Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP), item.baseSizeDp())
    }

    @Test
    fun `a 2-1 landscape gallery photo yields a wide twice-as-wide box`() {
        val placement = StickerPlacement(
            stickerId = "gallery:wide",
            x = 0f,
            y = 0f,
            imageUri = "content://media/picker/wide",
            aspectRatio = 2f
        )
        val item = placement.toStickerItem(null, density, boxW, boxH)!!
        val base = item.baseSizeDp()!!
        assertEquals(2f, base.width / base.height, 0.001f)
        assertEquals(EMOJI_STICKER_BASE_SIZE_DP, base.width, 0.001f)
        assertEquals(EMOJI_STICKER_BASE_SIZE_DP / 2f, base.height, 0.001f)
        // In a square frame the normalized fractions mirror the ratio exactly.
        val itemSquare = placement.toStickerItem(null, density, 800f, 800f)!!
        assertEquals(2f, itemSquare.widthFrac / itemSquare.heightFrac, 0.001f)
    }

    @Test
    fun `a 1-2 portrait gallery photo yields a tall twice-as-tall box`() {
        val placement = StickerPlacement(
            stickerId = "gallery:tall",
            x = 0f,
            y = 0f,
            imageUri = "content://media/picker/tall",
            aspectRatio = 0.5f
        )
        val item = placement.toStickerItem(null, density, boxW, boxH)!!
        val base = item.baseSizeDp()!!
        assertEquals(0.5f, base.width / base.height, 0.001f)
        assertEquals(EMOJI_STICKER_BASE_SIZE_DP, base.width, 0.001f)
        assertEquals(EMOJI_STICKER_BASE_SIZE_DP / 0.5f, base.height, 0.001f)
        val itemSquare = placement.toStickerItem(null, density, 800f, 800f)!!
        assertEquals(0.5f, itemSquare.widthFrac / itemSquare.heightFrac, 0.001f)
    }

    @Test
    fun `gallery pick commit re-open restores the same ratio box`() {
        val uri = "content://media/picker/123"
        val placement = StickerPlacement(
            stickerId = "gallery:$uri",
            x = 120f,
            y = 80f,
            scaleX = 1.2f,
            scaleY = 1.2f,
            imageUri = uri,
            aspectRatio = 2f
        )
        // Pick → commit (normalized stack entry).
        val item = placement.toStickerItem(null, density, boxW, boxH)!!
        assertEquals(2f, item.aspectRatio, 0.001f)
        val boxRatio = item.widthFrac / item.heightFrac
        // Re-open: restore from the stack entry, then re-commit the restored
        // placement — the ratio box and scale survive the round trip.
        val restored = item.toStickerPlacement(
            density = density,
            frameWidthPx = boxW,
            frameHeightPx = boxH,
            baseSize = item.baseSizeDp()!!
        )
        assertEquals(placement.aspectRatio, restored.aspectRatio, 0.001f)
        assertEquals(placement.scaleX, restored.scaleX, 0.001f)

        val recommitted = restored.toStickerItem(null, density, boxW, boxH)!!
        assertEquals(boxRatio, recommitted.widthFrac / recommitted.heightFrac, 0.001f)
        assertEquals(item.widthFrac, recommitted.widthFrac, 0.001f)
        assertEquals(item.heightFrac, recommitted.heightFrac, 0.001f)
    }

    @Test
    fun `degenerate or non-finite gallery ratios fall back to square`() {
        for (bad in listOf(0f, -2f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val placement = StickerPlacement(
                stickerId = "gallery:bad",
                x = 0f,
                y = 0f,
                imageUri = "content://media/picker/bad",
                aspectRatio = bad
            )
            val item = placement.toStickerItem(null, density, boxW, boxH)!!
            assertEquals(Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP), item.baseSizeDp())
            assertEquals(1f, item.aspectRatio, 0.001f)
        }
    }

    @Test
    fun `placement base size reflects the gallery photo aspect ratio on the live sprite`() {
        // Portrait (ratio > 1): taller than wide, anchored on the emoji width.
        val portrait = StickerPlacement(
            stickerId = "gallery:p", x = 0f, y = 0f,
            imageUri = "content://media/picker/p", aspectRatio = 2f
        )
        val pBase = portrait.baseSizeDp()!!
        assertEquals(2f, pBase.width / pBase.height, 0.001f)
        assertEquals(EMOJI_STICKER_BASE_SIZE_DP, pBase.width, 0.001f)
        assertEquals(EMOJI_STICKER_BASE_SIZE_DP / 2f, pBase.height, 0.001f)

        // Landscape (ratio < 1): wider than tall.
        val landscape = StickerPlacement(
            stickerId = "gallery:l", x = 0f, y = 0f,
            imageUri = "content://media/picker/l", aspectRatio = 0.5f
        )
        val lBase = landscape.baseSizeDp()!!
        assertEquals(0.5f, lBase.width / lBase.height, 0.001f)
        assertEquals(EMOJI_STICKER_BASE_SIZE_DP, lBase.width, 0.001f)
        assertEquals(EMOJI_STICKER_BASE_SIZE_DP / 0.5f, lBase.height, 0.001f)

        // Emoji keeps the full square; a degenerately-ratioed gallery falls back square.
        val emoji = StickerPlacement(stickerId = "emoji:x", x = 0f, y = 0f, emojiText = "😀")
        assertEquals(Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP), emoji.baseSizeDp())
        val bad = StickerPlacement(
            stickerId = "gallery:b", x = 0f, y = 0f,
            imageUri = "content://media/picker/b", aspectRatio = Float.NaN
        )
        assertEquals(Size(EMOJI_STICKER_BASE_SIZE_DP, EMOJI_STICKER_BASE_SIZE_DP), bad.baseSizeDp())
    }

    @Test
    fun `restored gallery placement keeps the same rectangular box as the original`() {
        // Placing → committing → re-opening a Gallery photo must yield the same
        // proportional box (this is what drives the live sprite and fold).
        val uri = "content://media/picker/rt"
        val placed = StickerPlacement(
            stickerId = "gallery:$uri", x = 200f, y = 150f,
            scaleX = 1.5f, scaleY = 1.5f,
            imageUri = uri, aspectRatio = 2f
        )
        val item = placed.toStickerItem(null, density, boxW, boxH)!!
        val restored = item.toStickerPlacement(
            density = density, frameWidthPx = boxW, frameHeightPx = boxH,
            baseSize = item.baseSizeDp()!!
        )
        val origBox = placed.baseSizeDp()!!
        val restoredBox = restored.baseSizeDp()!!
        assertEquals(origBox.width, restoredBox.width, 0.001f)
        assertEquals(origBox.height, restoredBox.height, 0.001f)
        assertEquals(2f, restoredBox.width / restoredBox.height, 0.001f)
    }

    @Test
    fun `letterboxed frame normalizes to source fractions and round trips`() {
        // P2: box 1000x1200 px (density 2 → 500x600 dp), image frame 640x960 px
        // at offset (180, 120). A sticker dropped at the box position over the
        // image must bake to the same image fraction, and restoring must land
        // back on the same box position.
        val sticker = StickerPresets.stickers.first()
        // Image centre in box px = (180+320, 120+480) = (500, 600) → 250dp, 300dp.
        val placement = StickerPlacement(
            stickerId = sticker.id,
            x = 250f,
            y = 300f
        )
        val item = placement.toStickerItem(sticker, density, 640f, 960f, 180f, 120f)!!
        assertEquals(0.5f, item.x, 0.0001f)
        assertEquals(0.5f, item.y, 0.0001f)

        val restored = item.toStickerPlacement(
            density = density, frameWidthPx = 640f, frameHeightPx = 960f,
            baseSize = Size(sticker.width.toFloat(), sticker.height.toFloat()),
            frameOffsetX = 180f, frameOffsetY = 120f
        )
        assertEquals(placement.x, restored.x, 0.001f)
        assertEquals(placement.y, restored.y, 0.001f)
    }

    @Test
    fun `the fold keeps an already-placed sticker across a zoom-rescaled frame`() {
        // "add a sticker -> zoom -> sticker disappears": the sticker must never
        // be dropped by a frame rescale. The normalized FRACTIONS change (they
        // are relative to the fitted rect), but the pixel-space centre and on-canvas
        // footprint stay constant, so the sticker stays exactly where the user
        // placed it regardless of the frame.
        val sticker = StickerPresets.getStickerById("heart_1")!!
        val placement = StickerPlacement(
            stickerId = sticker.id,
            x = 200f,
            y = 150f,
            scaleX = 1.5f,
            scaleY = 1.5f,
            rotation = 25f
        )
        val preZoom = placement.toStickerItem(sticker, density, 800f, 600f)!!
        val zoomed = placement.toStickerItem(sticker, density, 1200f, 900f)!!
        assertEquals(sticker.id, zoomed.stickerId)
        assertEquals(25f, zoomed.rotationDegrees, 0.001f)
        // Same on-screen centre (400px) and footprint (600px) in both frames.
        assertEquals(preZoom.x * 800f, zoomed.x * 1200f, 1f)
        assertEquals(preZoom.y * 600f, zoomed.y * 900f, 1f)
        assertEquals(preZoom.widthFrac * 800f, zoomed.widthFrac * 1200f, 1f)
        assertEquals(preZoom.heightFrac * 600f, zoomed.heightFrac * 900f, 1f)
    }

    // --- Live-vs-committed parity (tracker → next.md: sticker pinch-scale) ---

    /**
     * The committed fold recomposes `widthFrac × bitmapW` to the same pixel size
     * the live sprite computes as `baseSizeDp × scaleX → dp → px`.  This test
     * pins that equality across a range of scales for every sticker kind.
     */
    private fun assertWidthFracMatchesLive(
        item: StickerItem,
        liveBaseDp: Float,
        scale: Float,
        frameW: Float,
        label: String
    ) {
        val liveWpx = with(density) { (liveBaseDp * scale).dp.toPx() }
        val committedW = item.widthFrac * frameW
        assertEquals(
            "$label scale=$scale: committed pixel width must match live sprite",
            liveWpx, committedW, 1f
        )
    }

    private fun assertHeightFracMatchesLive(
        item: StickerItem,
        liveBaseDp: Float,
        scale: Float,
        frameH: Float,
        label: String
    ) {
        val liveHpx = with(density) { (liveBaseDp * scale).dp.toPx() }
        val committedH = item.heightFrac * frameH
        assertEquals(
            "$label scale=$scale: committed pixel height must match live sprite",
            liveHpx, committedH, 1f
        )
    }

    @Test
    fun `emoji sticker width matches the live sprite at every scale`() {
        for (scale in listOf(0.8f, 1f, 1.5f, 2.5f, 4f)) {
            val placement = StickerPlacement(
                stickerId = "emoji:🚀", x = 0.5f, y = 0.5f,
                scaleX = scale, scaleY = scale, emojiText = "🚀"
            )
            val item = placement.toStickerItem(null, density, boxW, boxH)!!
            assertWidthFracMatchesLive(item, EMOJI_STICKER_BASE_SIZE_DP, scale, boxW, "emoji")
            assertHeightFracMatchesLive(item, EMOJI_STICKER_BASE_SIZE_DP, scale, boxH, "emoji")
        }
    }

    @Test
    fun `preset sticker width matches the live sprite at every scale`() {
        val sticker = StickerPresets.getStickerById("heart_1")!!
        for (scale in listOf(0.8f, 1f, 1.5f, 2.5f)) {
            val placement = StickerPlacement(
                stickerId = sticker.id, x = 0.5f, y = 0.5f,
                scaleX = scale, scaleY = scale
            )
            val item = placement.toStickerItem(sticker, density, boxW, boxH)!!
            assertWidthFracMatchesLive(item, sticker.width.toFloat(), scale, boxW, "preset")
            assertHeightFracMatchesLive(item, sticker.height.toFloat(), scale, boxH, "preset")
        }
    }

    @Test
    fun `gallery sticker width matches the live sprite at every scale`() {
        val uri = "content://media/picker/gallery_parity"
        val galleryRatio = 2f
        val galleryBase = EMOJI_STICKER_BASE_SIZE_DP
        val galleryBaseH = EMOJI_STICKER_BASE_SIZE_DP / galleryRatio
        for (scale in listOf(0.8f, 1f, 1.5f)) {
            val placement = StickerPlacement(
                stickerId = "gallery:$uri", x = 0.5f, y = 0.5f,
                scaleX = scale, scaleY = scale,
                imageUri = uri, aspectRatio = galleryRatio
            )
            val item = placement.toStickerItem(null, density, boxW, boxH)!!
            assertWidthFracMatchesLive(item, galleryBase, scale, boxW, "gallery")
            assertHeightFracMatchesLive(item, galleryBaseH, scale, boxH, "gallery")
        }
    }

    @Test
    fun `sticker fraction is preserved across different fold target resolutions`() {
        val placement = StickerPlacement(
            stickerId = "emoji:🚀", x = 0.5f, y = 0.5f,
            scaleX = 1.5f, scaleY = 1.5f, emojiText = "🚀"
        )
        val item = placement.toStickerItem(null, density, boxW, boxH)!!
        val liveWpx = with(density) { (EMOJI_STICKER_BASE_SIZE_DP * 1.5f).dp.toPx() }
        // A different fold-target bitmap width must scale proportionally.
        for (bitmapW in listOf(200f, 500f, 1000f, 2000f, 4000f)) {
            val committedW = item.widthFrac * bitmapW
            val expectedW = liveWpx * bitmapW / boxW
            assertEquals(
                "bitmapW=$bitmapW: fraction must map to proportional pixel size",
                expectedW, committedW, 1f
            )
        }
    }
}