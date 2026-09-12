package codes.pepper.whimsicalart.feature.stickers.domain

import androidx.annotation.DrawableRes
import codes.pepper.whimsicalart.feature.stickers.R
import java.util.UUID

data class Sticker(
    val id: String,
    val name: String,
    val category: StickerCategory,
    @DrawableRes val drawableRes: Int,
    val width: Int = 200,
    val height: Int = 200,
    val customColor: Boolean = false,
    val defaultTintColor: Int? = null
)

enum class StickerCategory {
    EMOJI,
    SHAPES,
    DECORATION,
    NATURE,
    FOOD,
    ANIMALS,
    OBJECTS
}

/**
 * How the sticker's art is outlined (spec: effects_reference.md → *Stickers* →
 * *Line*). Used only by colour-enabled stickers; [NONE] draws nothing while the
 * thickness/colour values stay retained for when a style is picked later.
 */
enum class StickerLineStyle { NONE, SOLID, DASHED, DOTTED }

data class StickerPlacement(
    val id: String = UUID.randomUUID().toString(),
    val stickerId: String,
    val x: Float,
    val y: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val isFlipped: Boolean = false,
    val tintColor: Int? = null,
    val emojiText: String? = null,
    /** The Gallery sticker: the picked image's content URI, in place of a preset id. */
    val imageUri: String? = null,
    /**
     * The picked image's intrinsic width:height ratio (EXIF-oriented) — the
     * Gallery sticker keeps its own aspect ratio (spec: "Gallery stickers keep
     * the original image's aspect ratio"), so the on-canvas box is proportional
     * and the renderer never distorts or centre-crops it. Defaults to 1 (square)
     * for emoji / presets, whose boxes are already their own geometry.
     */
    val aspectRatio: Float = 1f,
    /** The sticker's outline style (colour-enabled stickers), default none. */
    val lineStyle: StickerLineStyle = StickerLineStyle.NONE,
    /**
     * Outline thickness as a fraction of the sticker's shorter side (spec's
     * default 5%).
     */
    val lineThickness: Float = 0.05f,
    /**
     * Outline colour; null resolves to **black** at draw time (the default line
     * colour), so a freshly-enabled line starts black regardless of the sticker's
     * default fill colour.
     */
    val lineColor: Int? = null
)

object StickerPresets {
    val stickers = listOf(
        // --- SHAPES: tintable geometry, each with a default colour ---
        Sticker(
            id = "heart_1",
            name = "Heart",
            category = StickerCategory.SHAPES,
            drawableRes = R.drawable.sticker_heart,
            customColor = true,
            defaultTintColor = 0xFFF44336.toInt()),
        Sticker(
            id = "star_1",
            name = "Star",
            category = StickerCategory.SHAPES,
            drawableRes = R.drawable.sticker_star,
            customColor = true,
            defaultTintColor = 0xFFFFEB3B.toInt()),
        Sticker(
            id = "circle_1",
            name = "Circle",
            category = StickerCategory.SHAPES,
            drawableRes = R.drawable.sticker_circle,
            customColor = true,
            defaultTintColor = 0xFF03A9F4.toInt()),
        Sticker(
            id = "square_1",
            name = "Square",
            category = StickerCategory.SHAPES,
            drawableRes = R.drawable.sticker_square,
            customColor = true,
            defaultTintColor = 0xFF3F51B5.toInt()),
        Sticker(
            id = "triangle_1",
            name = "Triangle",
            category = StickerCategory.SHAPES,
            drawableRes = R.drawable.sticker_triangle,
            customColor = true,
            defaultTintColor = 0xFF4CAF50.toInt()),
        Sticker(
            id = "flower_1",
            name = "Flower",
            category = StickerCategory.SHAPES,
            drawableRes = R.drawable.sticker_flower,
            customColor = true,
            defaultTintColor = 0xFFEC407A.toInt()),
        Sticker(
            id = "drop_1",
            name = "Drop",
            category = StickerCategory.SHAPES,
            drawableRes = R.drawable.sticker_drop,
            customColor = true,
            defaultTintColor = 0xFF00BCD4.toInt()),
        // --- Decoration ---
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
            category = StickerCategory.DECORATION,
            drawableRes = R.drawable.sticker_smiley,
            customColor = true),
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
