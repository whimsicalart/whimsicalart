package codes.pepper.whimsicalart.feature.filters

import codes.pepper.whimsicalart.feature.filters.domain.FilterCategory
import codes.pepper.whimsicalart.feature.filters.domain.FilterPresets
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterPresetsTest {

    @Test
    fun `filter presets contains expected filters`() {
        assertTrue(FilterPresets.filters.isNotEmpty())
        assertTrue(FilterPresets.filters.size >= 10)
    }

    @Test
    fun `original filter exists`() {
        val original = FilterPresets.getFilterById("original")
        assertNotNull(original)
        assertEquals("Original", original?.name)
        assertEquals(FilterCategory.BASIC, original?.category)
    }

    @Test
    fun `getFilterById returns correct filter`() {
        val sepia = FilterPresets.getFilterById("sepia")
        assertNotNull(sepia)
        assertEquals("Sepia", sepia?.name)
    }

    @Test
    fun `getFilterById returns null for unknown id`() {
        assertNull(FilterPresets.getFilterById("nonexistent"))
    }

    @Test
    fun `getFiltersByCategory returns correct filters`() {
        val basicFilters = FilterPresets.getFiltersByCategory(FilterCategory.BASIC)
        assertTrue(basicFilters.isNotEmpty())
        basicFilters.forEach { filter ->
            assertEquals(FilterCategory.BASIC, filter.category)
        }
    }

    @Test
    fun `getFiltersByCategory returns landscape filters`() {
        val filters = FilterPresets.getFiltersByCategory(FilterCategory.LANDSCAPE)
        assertTrue(filters.isNotEmpty())
        filters.forEach { filter ->
            assertEquals(FilterCategory.LANDSCAPE, filter.category)
        }
    }

    @Test
    fun `every preset filter has a working preview color matrix`() {
        FilterPresets.filters.forEach { filter ->
            val isOriginal = filter.id == "original"
            if (isOriginal) {
                assertNull("Original should have no color matrix", filter.previewColorMatrix)
            } else {
                assertNotNull(
                    "Filter ${filter.id} must have a real previewColorMatrix (broken/no-op presets were removed)",
                    filter.previewColorMatrix
                )
            }
        }
    }

    @Test
    fun `all filters have non-empty id`() {
        FilterPresets.filters.forEach { filter ->
            assertTrue("Filter ${filter.name} has empty id", filter.id.isNotEmpty())
        }
    }

    @Test
    fun `all filters have non-empty name`() {
        FilterPresets.filters.forEach { filter ->
            assertTrue("Filter ${filter.id} has empty name", filter.name.isNotEmpty())
        }
    }

    @Test
    fun `all filters have non-empty shader code`() {
        FilterPresets.filters.forEach { filter ->
            assertTrue("Filter ${filter.id} has empty shader code", filter.shaderCode.isNotEmpty())
        }
    }

    @Test
    fun `filter intensity is within valid range`() {
        FilterPresets.filters.forEach { filter ->
            assertTrue(
                "Filter ${filter.id} intensity out of range: ${filter.intensity}",
                filter.intensity in 0f..1f
            )
        }
    }

    @Test
    fun `concatFilterMatrices returns null when nothing selected`() {
        assertNull(FilterPresets.concatFilterMatrices(null, emptySet()))
        assertNull(FilterPresets.concatFilterMatrices("original", emptySet()))
    }

    @Test
    fun `concatFilterMatrices composes colour and lens filters`() {
        val out = FilterPresets.concatFilterMatrices("vintage", setOf("neutral_density", "uv"))
        assertNotNull(out)
        assertEquals(20, out!!.size)
    }

    @Test
    fun `lens filters are all distinct from colour filters`() {
        val lens = FilterPresets.lensFilters.map { it.id }.toSet()
        val color = FilterPresets.colorFilters.map { it.id }.toSet()
        assertTrue("Lens and colour filter sets must not overlap", lens.intersect(color).isEmpty())
        FilterPresets.lensFilters.forEach { assertTrue(FilterPresets.isLensFilter(it.id)) }
    }

    @Test
    fun `real-pass lens filters are all registered lens filters`() {
        assertTrue(FilterPresets.realPassLensFilterIds.isNotEmpty())
        FilterPresets.realPassLensFilterIds.forEach {
            assertTrue("$it must be a registered lens filter", FilterPresets.isLensFilter(it))
        }
        assertTrue("red-eye must be a real-pass lens filter", FilterPresets.isRealPassLensFilter("red_eye"))
        assertTrue("star must be a real-pass lens filter", FilterPresets.isRealPassLensFilter("star"))
        assertTrue("anamorphic must be a real-pass lens filter", FilterPresets.isRealPassLensFilter("anamorphic"))
        assertTrue("astro must be a real-pass lens filter", FilterPresets.isRealPassLensFilter("astro"))
        assertFalse("clear is a matrix lens filter, not a real pass", FilterPresets.isRealPassLensFilter("clear"))
    }

    @Test
    fun `concatFilterMatrices excludes real-pass lens filters`() {
        // Real-pass filters must not contribute a matrix approximation: the
        // renderer applies the real pixel pass instead.
        assertNull(
            "a bare real-pass lens filter must not produce a matrix",
            FilterPresets.concatFilterMatrices(null, setOf("star"))
        )
        assertEquals(20, FilterPresets.concatFilterMatrices("vintage", setOf("star"))?.size)
        assertNull(
            "a mixed real-pass-only selection produces no matrix",
            FilterPresets.concatFilterMatrices(null, setOf("red_eye", "astro"))
        )
    }
}
