package codes.pepper.whimsicalart.feature.stickers.ui.viewmodel

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import codes.pepper.whimsicalart.feature.stickers.domain.Sticker
import codes.pepper.whimsicalart.feature.stickers.domain.StickerCategory
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPlacement
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class StickersUiState(
    val selectedCategory: StickerCategory = StickerCategory.EMOJI,
    val availableStickers: List<Sticker> = StickerPresets.getStickersByCategory(StickerCategory.EMOJI),
    val placedStickers: List<StickerPlacement> = emptyList(),
    val selectedStickerId: String? = null,
    val isDragging: Boolean = false
)

@HiltViewModel
class StickersViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(StickersUiState())
    val uiState: StateFlow<StickersUiState> = _uiState.asStateFlow()

    fun selectCategory(category: StickerCategory) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            availableStickers = StickerPresets.getStickersByCategory(category)
        )
    }

    fun placeSticker(sticker: Sticker, position: Offset) {
        val placement = StickerPlacement(
            stickerId = sticker.id,
            x = position.x,
            y = position.y
        )
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers + placement,
            selectedStickerId = placement.stickerId
        )
    }

    fun moveSticker(stickerId: String, newPosition: Offset) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.stickerId == stickerId) {
                    placement.copy(x = newPosition.x, y = newPosition.y)
                } else {
                    placement
                }
            }
        )
    }

    fun scaleSticker(stickerId: String, scale: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.stickerId == stickerId) {
                    placement.copy(
                        scaleX = scale,
                        scaleY = scale
                    )
                } else {
                    placement
                }
            }
        )
    }

    fun rotateSticker(stickerId: String, rotation: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.stickerId == stickerId) {
                    placement.copy(rotation = rotation)
                } else {
                    placement
                }
            }
        )
    }

    fun flipSticker(stickerId: String) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.stickerId == stickerId) {
                    placement.copy(isFlipped = !placement.isFlipped)
                } else {
                    placement
                }
            }
        )
    }

    fun setOpacity(stickerId: String, opacity: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.stickerId == stickerId) {
                    placement.copy(opacity = opacity)
                } else {
                    placement
                }
            }
        )
    }

    fun removeSticker(stickerId: String) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.filter {
                it.stickerId != stickerId
            }
        )
    }

    fun selectSticker(stickerId: String?) {
        _uiState.value = _uiState.value.copy(selectedStickerId = stickerId)
    }

    fun clearAllStickers() {
        _uiState.value = _uiState.value.copy(placedStickers = emptyList())
    }
}
