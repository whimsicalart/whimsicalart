package codes.pepper.whimsicalart.feature.stickers

import codes.pepper.whimsicalart.feature.stickers.domain.StickerCategory
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StickerPresetsTest {

    @Test
    fun `presets have unique non-blank ids`() {
        val ids = StickerPresets.stickers.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.none { it.isBlank() })
    }

    @Test
    fun `presets all carry a name and a valid drawable resource`() {
        StickerPresets.stickers.forEach { sticker ->
            assertTrue(sticker.name.isNotBlank())
            assertTrue("sticker ${sticker.id} should have a drawable resource", sticker.drawableRes != 0)
        }
    }

    @Test
    fun `presets include the newly added entries`() {
        val ids = StickerPresets.stickers.map { it.id }
        listOf(
            "smiley_1",
            "cherry_blossom_1",
            "sunflower_1",
            "strawberry_1",
            "lemon_1",
            "pizza_1",
            "donut_1",
            "dog_1",
            "cat_1",
            "fox_1",
            "panda_1",
            "unicorn_1",
            "gem_1",
            "teddy_bear_1",
            "balloon_1",
            "party_popper_1",
            "ribbon_1"
        ).forEach { expected ->
            assertTrue("Missing preset $expected", ids.contains(expected))
        }
    }

    @Test
    fun `presets exist in several categories`() {
        val categories = StickerPresets.stickers.map { it.category }.distinct()
        listOf(
            StickerCategory.EMOJI,
            StickerCategory.NATURE,
            StickerCategory.FOOD,
            StickerCategory.ANIMALS,
            StickerCategory.OBJECTS,
            StickerCategory.DECORATION
        ).forEach { category ->
            assertTrue("No sticker in category $category", categories.contains(category))
        }
    }

    @Test
    fun `getStickersByCategory returns only matching stickers`() {
        val food = StickerPresets.getStickersByCategory(StickerCategory.FOOD)
        assertTrue(food.isNotEmpty())
        assertTrue(food.all { it.category == StickerCategory.FOOD })
    }

    @Test
    fun `getStickerById returns matching sticker`() {
        val sticker = StickerPresets.getStickerById("panda_1")
        assertNotNull(sticker)
        assertEquals("panda_1", sticker!!.id)
        assertEquals(StickerCategory.ANIMALS, sticker.category)
    }

    @Test
    fun `getStickerById returns null for unknown id`() {
        assertNull(StickerPresets.getStickerById("does_not_exist"))
    }
}
