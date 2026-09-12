package codes.pepper.whimsicalart.feature.stickers

import androidx.compose.ui.geometry.Offset
import codes.pepper.whimsicalart.feature.stickers.domain.StickerCategory
import codes.pepper.whimsicalart.feature.stickers.domain.StickerLineStyle
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPresets
import codes.pepper.whimsicalart.feature.stickers.ui.viewmodel.StickersViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StickersViewModelTest {

    private lateinit var viewModel: StickersViewModel

    @Before
    fun setup() {
        viewModel = StickersViewModel()
    }

    @Test
    fun `initial state has default values`() {
        val state = viewModel.uiState.value
        assertEquals(StickerCategory.EMOJI, state.selectedCategory)
        assertTrue(state.placedStickers.isEmpty())
        assertNull(state.selectedStickerId)
        assertFalse(state.isDragging)
    }

    @Test
    fun `selectCategory updates available stickers`() {
        viewModel.selectCategory(StickerCategory.NATURE)
        assertEquals(StickerCategory.NATURE, viewModel.uiState.value.selectedCategory)
        val stickers = StickerPresets.getStickersByCategory(StickerCategory.NATURE)
        assertEquals(stickers.size, viewModel.uiState.value.availableStickers.size)
    }

    @Test
    fun `placeSticker adds sticker to placed list`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(100f, 200f))
        assertEquals(1, viewModel.uiState.value.placedStickers.size)
    }

    @Test
    fun `first placed sticker lands at canvas center`() {
        viewModel.updateCanvasCenter(Offset(300f, 400f))
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(300f, placement.x)
        assertEquals(400f, placement.y)
    }

    @Test
    fun `subsequent placed stickers walk down from the previous one`() {
        viewModel.updateCanvasCenter(Offset(300f, 400f))
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val placements = viewModel.uiState.value.placedStickers
        assertEquals(2, placements.size)
        assertEquals(placements[0].x, placements[1].x)
        assertEquals(
            placements[0].y + StickersViewModel.PLACEMENT_STEP_DP,
            placements[1].y
        )
    }

    @Test
    fun `placeSticker selects the newly placed sticker`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(100f, 200f))
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(placement.id, viewModel.uiState.value.selectedStickerId)
    }

    @Test
    fun `moveSticker updates only the target placement`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val first = viewModel.uiState.value.placedStickers[0]
        val second = viewModel.uiState.value.placedStickers[1]

        viewModel.moveSticker(first.id, Offset(50f, 75f))

        val placements = viewModel.uiState.value.placedStickers
        assertEquals(50f, placements[0].x)
        assertEquals(75f, placements[0].y)
        assertEquals(second.x, placements[1].x)
        assertEquals(second.y, placements[1].y)
    }

    @Test
    fun `scaleSticker updates scale`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val id = viewModel.uiState.value.placedStickers.first().id

        viewModel.scaleSticker(id, 2f)

        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(2f, placement.scaleX)
        assertEquals(2f, placement.scaleY)
    }

    @Test
    fun `rotateSticker updates rotation`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val id = viewModel.uiState.value.placedStickers.first().id

        viewModel.rotateSticker(id, 45f)

        assertEquals(45f, viewModel.uiState.value.placedStickers.first().rotation)
    }

    @Test
    fun `flipSticker toggles isFlipped`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val id = viewModel.uiState.value.placedStickers.first().id

        viewModel.flipSticker(id)
        assertTrue(viewModel.uiState.value.placedStickers.first().isFlipped)

        viewModel.flipSticker(id)
        assertFalse(viewModel.uiState.value.placedStickers.first().isFlipped)
    }

    @Test
    fun `setOpacity updates opacity`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val id = viewModel.uiState.value.placedStickers.first().id

        viewModel.setOpacity(id, 0.5f)

        assertEquals(0.5f, viewModel.uiState.value.placedStickers.first().opacity)
    }

    @Test
    fun `setTint updates and clears tint`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val id = viewModel.uiState.value.placedStickers.first().id

        viewModel.setTint(id, 0xFFFF0000.toInt())
        assertEquals(0xFFFF0000.toInt(), viewModel.uiState.value.placedStickers.first().tintColor)

        viewModel.setTint(id, null)
        assertNull(viewModel.uiState.value.placedStickers.first().tintColor)
    }

    @Test
    fun `setLineStyle updates only the target placement`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val placements = viewModel.uiState.value.placedStickers
        val first = placements[0]
        val second = placements[1]

        viewModel.setLineStyle(first.id, StickerLineStyle.DASHED)

        assertEquals(StickerLineStyle.DASHED, viewModel.uiState.value.placedStickers[0].lineStyle)
        assertEquals(StickerLineStyle.NONE, viewModel.uiState.value.placedStickers[1].lineStyle)
    }

    @Test
    fun `setLineThickness updates thickness`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val id = viewModel.uiState.value.placedStickers.first().id

        viewModel.setLineThickness(id, 0.12f)

        assertEquals(0.12f, viewModel.uiState.value.placedStickers.first().lineThickness, 0.001f)
    }

    @Test
    fun `setLineColour updates and clears the line colour`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val id = viewModel.uiState.value.placedStickers.first().id

        viewModel.setLineColour(id, 0xFF00FF00.toInt())
        assertEquals(0xFF00FF00.toInt(), viewModel.uiState.value.placedStickers.first().lineColor)

        viewModel.setLineColour(id, null)
        assertNull(viewModel.uiState.value.placedStickers.first().lineColor)
    }

    @Test
    fun `placeEmoji adds an emoji placement`() {
        viewModel.placeEmoji("😀", Offset(100f, 200f))
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals("😀", placement.emojiText)
        assertEquals("emoji:😀", placement.stickerId)
    }

    @Test
    fun `blank emoji is ignored`() {
        viewModel.placeEmoji("   ", Offset(100f, 200f))
        assertTrue(viewModel.uiState.value.placedStickers.isEmpty())
    }

    @Test
    fun `placeGallerySticker adds a gallery placement carrying the uri`() {
        viewModel.placeGallerySticker("content://media/picker/7", Offset(100f, 200f))
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals("gallery:content://media/picker/7", placement.stickerId)
        assertEquals("content://media/picker/7", placement.imageUri)
        assertEquals(1f, placement.aspectRatio, 0.001f)
        assertEquals(placement.id, viewModel.uiState.value.selectedStickerId)
    }

    @Test
    fun `placeGallerySticker carries the picked image's aspect ratio`() {
        viewModel.placeGallerySticker("content://media/picker/7", Offset(100f, 200f), aspectRatio = 2f)
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(2f, placement.aspectRatio, 0.001f)
    }

    @Test
    fun `placeGallerySticker sanitizes a degenerate aspect ratio to square`() {
        viewModel.placeGallerySticker("content://media/picker/9", Offset(100f, 200f), aspectRatio = Float.NaN)
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(1f, placement.aspectRatio, 0.001f)
    }

    @Test
    fun `blank gallery uri is ignored`() {
        viewModel.placeGallerySticker("   ", Offset(100f, 200f))
        assertTrue(viewModel.uiState.value.placedStickers.isEmpty())
    }

    @Test
    fun `gallery placement lands at canvas center`() {
        viewModel.updateCanvasCenter(Offset(300f, 400f))
        viewModel.placeGallerySticker("content://media/picker/8", Offset(0f, 0f))
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(300f, placement.x)
        assertEquals(400f, placement.y)
    }

    @Test
    fun `removeSticker removes only the target placement and clears its selection`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val first = viewModel.uiState.value.placedStickers[0]
        val second = viewModel.uiState.value.placedStickers[1]
        viewModel.selectSticker(second.id)
        assertEquals(2, viewModel.uiState.value.placedStickers.size)

        viewModel.removeSticker(first.id)

        val placements = viewModel.uiState.value.placedStickers
        assertEquals(1, placements.size)
        assertEquals(second.id, placements[0].id)
        assertEquals(second.id, viewModel.uiState.value.selectedStickerId)
    }

    @Test
    fun `clearAllStickers empties list and clears selection`() {
        StickerPresets.stickers.take(3).forEach { sticker ->
            viewModel.placeSticker(sticker, Offset(0f, 0f))
        }
        assertEquals(3, viewModel.uiState.value.placedStickers.size)
        assertNotNull(viewModel.uiState.value.selectedStickerId)

        viewModel.clearAllStickers()

        assertTrue(viewModel.uiState.value.placedStickers.isEmpty())
        assertNull(viewModel.uiState.value.selectedStickerId)
    }

    @Test
    fun `restoreOverlays replaces placed stickers`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        val original = viewModel.uiState.value.placedStickers.first()

        viewModel.restoreOverlays(emptyList())
        assertTrue(viewModel.uiState.value.placedStickers.isEmpty())

        viewModel.restoreOverlays(listOf(original))
        assertEquals(1, viewModel.uiState.value.placedStickers.size)
        assertNull(viewModel.uiState.value.selectedStickerId)
    }

    @Test
    fun `selectSticker updates selectedStickerId`() {
        viewModel.selectSticker("test_id")
        assertEquals("test_id", viewModel.uiState.value.selectedStickerId)
    }

    @Test
    fun `selectSticker null clears selection`() {
        viewModel.selectSticker("test_id")
        viewModel.selectSticker(null)
        assertNull(viewModel.uiState.value.selectedStickerId)
    }
}