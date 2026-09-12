package codes.pepper.whimsicalart.feature.stickers.ui.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import codes.pepper.whimsicalart.core.domain.wrapDegrees
import codes.pepper.whimsicalart.feature.stickers.domain.Sticker
import codes.pepper.whimsicalart.feature.stickers.domain.StickerCategory
import codes.pepper.whimsicalart.feature.stickers.domain.StickerLineStyle
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
    val isDragging: Boolean = false,
    val canvasCenter: Offset? = null
)

@HiltViewModel
class StickersViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(StickersUiState())
    val uiState: StateFlow<StickersUiState> = _uiState.asStateFlow()

    companion object {
        /** Vertical step (dp) between auto-placed stickers. */
        const val PLACEMENT_STEP_DP = 56f
    }

    fun selectCategory(category: StickerCategory) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            availableStickers = StickerPresets.getStickersByCategory(category)
        )
    }

    /** Remembers the edit-box centre (dp) so the first sticker lands centred. */
    fun updateCanvasCenter(center: Offset) {
        if (_uiState.value.canvasCenter != center) {
            _uiState.value = _uiState.value.copy(canvasCenter = center)
        }
    }

    fun placeSticker(sticker: Sticker, fallbackCenter: Offset) {
        val position = placementFor(fallbackCenter)
        val placement = StickerPlacement(
            stickerId = sticker.id,
            x = position.x,
            y = position.y,
            // A shape with a default colour starts with that colour (spec:
            // "the shape's default colour is what appears when no tint is picked").
            tintColor = sticker.defaultTintColor
        )
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers + placement,
            selectedStickerId = placement.id
        )
    }

    fun placeEmoji(emoji: String, fallbackCenter: Offset) {
        if (emoji.isBlank()) return
        val position = placementFor(fallbackCenter)
        val placement = StickerPlacement(
            stickerId = "emoji:$emoji",
            x = position.x,
            y = position.y,
            emojiText = emoji
        )
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers + placement,
            selectedStickerId = placement.id
        )
    }

    /**
     * Places the image picked through the Gallery entry ([uri] is its content
     * URI). [aspectRatio] is the image's intrinsic width:height ratio
     * (EXIF-oriented, computed at pick time) — carried on the placement so the
     * on-canvas box and every re-open keep the photo's proportion (spec:
     * "Gallery stickers keep the original image's aspect ratio").
     */
    fun placeGallerySticker(uri: String, fallbackCenter: Offset, aspectRatio: Float = 1f) {
        if (uri.isBlank()) return
        val position = placementFor(fallbackCenter)
        val placement = StickerPlacement(
            // Synthetic id in place of a preset: gallery stickers carry the URI.
            stickerId = "gallery:$uri",
            x = position.x,
            y = position.y,
            imageUri = uri,
            aspectRatio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        )
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers + placement,
            selectedStickerId = placement.id
        )
    }

    fun moveSticker(placementId: String, newPosition: Offset) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(x = newPosition.x, y = newPosition.y)
                } else {
                    placement
                }
            }
        )
    }

    fun scaleSticker(placementId: String, scale: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(scaleX = scale, scaleY = scale)
                } else {
                    placement
                }
            }
        )
    }

    /**
     * Corner/edge resize: a single atomic update sets the new uniform scale AND
     * re-anchors the centre so the opposite corner stays fixed under the finger
     * (one call, no intermediate flash between scale and move).
     */
    fun resizeSticker(placementId: String, scale: Float, position: Offset) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(
                        scaleX = scale,
                        scaleY = scale,
                        x = position.x,
                        y = position.y
                    )
                } else {
                    placement
                }
            }
        )
    }

    fun rotateSticker(placementId: String, rotation: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(rotation = wrapDegrees(rotation))
                } else {
                    placement
                }
            }
        )
    }

    fun flipSticker(placementId: String) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(isFlipped = !placement.isFlipped)
                } else {
                    placement
                }
            }
        )
    }

    fun setTint(placementId: String, color: Int?) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(tintColor = color)
                } else {
                    placement
                }
            }
        )
    }

    fun setLineStyle(placementId: String, style: StickerLineStyle) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(lineStyle = style)
                } else {
                    placement
                }
            }
        )
    }

    fun setLineThickness(placementId: String, thickness: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(lineThickness = thickness)
                } else {
                    placement
                }
            }
        )
    }

    fun setLineColour(placementId: String, color: Int?) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(lineColor = color)
                } else {
                    placement
                }
            }
        )
    }

    fun setOpacity(placementId: String, opacity: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map { placement ->
                if (placement.id == placementId) {
                    placement.copy(opacity = opacity)
                } else {
                    placement
                }
            }
        )
    }

    fun removeSticker(placementId: String) {
        val kept = _uiState.value.placedStickers.filter { it.id != placementId }
        _uiState.value = _uiState.value.copy(
            placedStickers = kept,
            selectedStickerId = if (_uiState.value.selectedStickerId == placementId) null else _uiState.value.selectedStickerId
        )
    }

    fun selectSticker(placementId: String?) {
        _uiState.value = _uiState.value.copy(selectedStickerId = placementId)
    }

    fun clearAllStickers() {
        _uiState.value = _uiState.value.copy(
            placedStickers = emptyList(),
            selectedStickerId = null
        )
    }

    /** Restores committed placements when re-entering the tool (stack is the source of truth). */
    fun restoreOverlays(placements: List<StickerPlacement>) {
        _uiState.value = _uiState.value.copy(
            placedStickers = placements,
            selectedStickerId = null
        )
    }

    /**
     * Auto-placement: first sticker at the canvas CENTER, each subsequent one
     * slightly below the previous (the placement cursor walks down).
     * [fallbackCenter] is used when the remembered canvas centre is not known yet.
     */
    private fun placementFor(fallbackCenter: Offset): Offset {
        val base = _uiState.value.canvasCenter ?: fallbackCenter
        val last = _uiState.value.placedStickers.lastOrNull() ?: return base
        return Offset(last.x, last.y + PLACEMENT_STEP_DP)
    }
}