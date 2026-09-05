package codes.pepper.whimsicalart.feature.stickers

import androidx.compose.ui.geometry.Offset
import codes.pepper.whimsicalart.feature.stickers.domain.StickerCategory
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
    fun `placeSticker stores correct position`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(100f, 200f))
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(100f, placement.x)
        assertEquals(200f, placement.y)
    }

    @Test
    fun `placeSticker selects the newly placed sticker`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(100f, 200f))
        assertEquals(sticker.id, viewModel.uiState.value.selectedStickerId)
    }

    @Test
    fun `moveSticker updates position`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        
        viewModel.moveSticker(sticker.id, Offset(50f, 75f))
        
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(50f, placement.x)
        assertEquals(75f, placement.y)
    }

    @Test
    fun `scaleSticker updates scale`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        
        viewModel.scaleSticker(sticker.id, 2f)
        
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(2f, placement.scaleX)
        assertEquals(2f, placement.scaleY)
    }

    @Test
    fun `rotateSticker updates rotation`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        
        viewModel.rotateSticker(sticker.id, 45f)
        
        val placement = viewModel.uiState.value.placedStickers.first()
        assertEquals(45f, placement.rotation)
    }

    @Test
    fun `flipSticker toggles isFlipped`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        
        viewModel.flipSticker(sticker.id)
        assertTrue(viewModel.uiState.value.placedStickers.first().isFlipped)
        
        viewModel.flipSticker(sticker.id)
        assertFalse(viewModel.uiState.value.placedStickers.first().isFlipped)
    }

    @Test
    fun `setOpacity updates opacity`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        
        viewModel.setOpacity(sticker.id, 0.5f)
        
        assertEquals(0.5f, viewModel.uiState.value.placedStickers.first().opacity)
    }

    @Test
    fun `removeSticker removes from list`() {
        val sticker = StickerPresets.stickers.first()
        viewModel.placeSticker(sticker, Offset(0f, 0f))
        assertEquals(1, viewModel.uiState.value.placedStickers.size)
        
        viewModel.removeSticker(sticker.id)
        assertTrue(viewModel.uiState.value.placedStickers.isEmpty())
    }

    @Test
    fun `clearAllStickers empties list`() {
        StickerPresets.stickers.take(3).forEach { sticker ->
            viewModel.placeSticker(sticker, Offset(0f, 0f))
        }
        assertEquals(3, viewModel.uiState.value.placedStickers.size)
        
        viewModel.clearAllStickers()
        assertTrue(viewModel.uiState.value.placedStickers.isEmpty())
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
