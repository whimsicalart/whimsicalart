package com.whimsicalart.feature.stickers.domain

import androidx.annotation.DrawableRes
import com.whimsicalart.feature.stickers.R

data class Sticker(
    val id: String,
    val name: String,
    val category: StickerCategory,
    @DrawableRes val drawableRes: Int,
    val width: Int = 200,
    val height: Int = 200
)

enum class StickerCategory {
    EMOJI,
    DECORATION,
    TEXT,
    FRAME,
    NATURE,
    FOOD,
    ANIMALS,
    OBJECTS
}

data class StickerPlacement(
    val stickerId: String,
    val x: Float,
    val y: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val isFlipped: Boolean = false
)

object StickerPresets {
    val stickers = listOf(
        Sticker(
            id = "heart_1",
            name = "Heart",
            category = StickerCategory.EMOJI,
            drawableRes = R.drawable.sticker_heart),
        Sticker(
            id = "star_1",
            name = "Star",
            category = StickerCategory.EMOJI,
            drawableRes = R.drawable.sticker_star),
        Sticker(
            id = "flower_1",
            name = "Flower",
            category = StickerCategory.NATURE,
            drawableRes = R.drawable.sticker_flower),
        Sticker(
            id = "sparkle_1",
            name = "Sparkle",
            category = StickerCategory.DECORATION,
            drawableRes = R.drawable.sticker_sparkle),
        Sticker(
            id = "rainbow_1",
            name = "Rainbow",
            category = StickerCategory.NATURE,
            drawableRes = R.drawable.sticker_rainbow),
        Sticker(
            id = "crown_1",
            name = "Crown",
            category = StickerCategory.OBJECTS,
            drawableRes = R.drawable.sticker_crown),
        Sticker(
            id = "smiley_1",
            name = "Smiley",
            category = StickerCategory.EMOJI,
            drawableRes = R.drawable.sticker_smiley),
        Sticker(
            id = "cherry_blossom_1",
            name = "Cherry Blossom",
            category = StickerCategory.NATURE,
            drawableRes = R.drawable.sticker_cherry_blossom),
        Sticker(
            id = "sunflower_1",
            name = "Sunflower",
            category = StickerCategory.NATURE,
            drawableRes = R.drawable.sticker_sunflower),
        Sticker(
            id = "strawberry_1",
            name = "Strawberry",
            category = StickerCategory.FOOD,
            drawableRes = R.drawable.sticker_strawberry),
        Sticker(
            id = "lemon_1",
            name = "Lemon",
            category = StickerCategory.FOOD,
            drawableRes = R.drawable.sticker_lemon),
        Sticker(
            id = "pizza_1",
            name = "Pizza",
            category = StickerCategory.FOOD,
            drawableRes = R.drawable.sticker_pizza),
        Sticker(
            id = "donut_1",
            name = "Donut",
            category = StickerCategory.FOOD,
            drawableRes = R.drawable.sticker_donut),
        Sticker(
            id = "dog_1",
            name = "Dog",
            category = StickerCategory.ANIMALS,
            drawableRes = R.drawable.sticker_dog),
        Sticker(
            id = "cat_1",
            name = "Cat",
            category = StickerCategory.ANIMALS,
            drawableRes = R.drawable.sticker_cat),
        Sticker(
            id = "fox_1",
            name = "Fox",
            category = StickerCategory.ANIMALS,
            drawableRes = R.drawable.sticker_fox),
        Sticker(
            id = "panda_1",
            name = "Panda",
            category = StickerCategory.ANIMALS,
            drawableRes = R.drawable.sticker_panda),
        Sticker(
            id = "unicorn_1",
            name = "Unicorn",
            category = StickerCategory.ANIMALS,
            drawableRes = R.drawable.sticker_unicorn),
        Sticker(
            id = "gem_1",
            name = "Gem",
            category = StickerCategory.OBJECTS,
            drawableRes = R.drawable.sticker_gem),
        Sticker(
            id = "teddy_bear_1",
            name = "Teddy Bear",
            category = StickerCategory.OBJECTS,
            drawableRes = R.drawable.sticker_teddy_bear),
        Sticker(
            id = "balloon_1",
            name = "Balloon",
            category = StickerCategory.OBJECTS,
            drawableRes = R.drawable.sticker_balloon),
        Sticker(
            id = "party_popper_1",
            name = "Party Popper",
            category = StickerCategory.DECORATION,
            drawableRes = R.drawable.sticker_tada),
        Sticker(
            id = "ribbon_1",
            name = "Ribbon",
            category = StickerCategory.DECORATION,
            drawableRes = R.drawable.sticker_ribbon),
    )

    fun getStickersByCategory(category: StickerCategory): List<Sticker> {
        return stickers.filter { it.category == category }
    }

    fun getStickerById(id: String): Sticker? {
        return stickers.find { it.id == id }
    }
}
