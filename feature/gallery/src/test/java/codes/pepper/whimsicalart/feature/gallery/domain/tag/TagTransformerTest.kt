package codes.pepper.whimsicalart.feature.gallery.domain.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagTransformerTest {

    private fun label(text: String, confidence: Float) = SceneLabel(text, confidence)

    @Test
    fun `food labels map to FOOD`() {
        val results = TagTransformer.transform(
            listOf(label("Food", 0.91f), label("Cuisine", 0.8f))
        )
        assertEquals(1, results.size)
        assertEquals(SceneTag.FOOD, results[0].tag)
        assertEquals(0.91f, results[0].confidence, 0.001f)
    }

    @Test
    fun `human face maps to PORTRAIT via substring`() {
        assertEquals(SceneTag.PORTRAIT, TagTransformer.match("Human face"))
    }

    @Test
    fun `beauty and makeup map to BEAUTY case-insensitively`() {
        assertEquals(SceneTag.BEAUTY, TagTransformer.match("Beauty"))
        assertEquals(SceneTag.BEAUTY, TagTransformer.match("makeup"))
    }

    @Test
    fun `confidence below threshold is ignored`() {
        val results = TagTransformer.transform(
            listOf(label("Food", 0.3f))
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `results are ordered by confidence descending and dominant is the top match`() {
        val results = TagTransformer.transform(
            listOf(label("Outdoors", 0.6f), label("Food", 0.95f))
        )
        assertEquals(2, results.size)
        assertEquals(SceneTag.FOOD, results[0].tag) // 0.95 sorts first
        assertEquals(SceneTag.OUTDOOR, results[1].tag)
        assertEquals(SceneTag.FOOD, TagTransformer.dominant(
            listOf(label("Outdoors", 0.6f), label("Food", 0.95f))
        )?.tag)
    }

    @Test
    fun `unrecognized labels are ignored`() {
        val results = TagTransformer.transform(
            listOf(label("Synthetic material", 0.99f), label("Logo", 0.88f))
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `water keywords map to WATER`() {
        assertEquals(SceneTag.WATER, TagTransformer.match("Water"))
        assertEquals(SceneTag.WATER, TagTransformer.match("ocean waves"))
        assertEquals(SceneTag.WATER, TagTransformer.match("Beach"))
    }

    @Test
    fun `empty labels produce no tags`() {
        assertTrue(TagTransformer.transform(emptyList()).isEmpty())
        assertNull(TagTransformer.dominant(emptyList()))
    }
}