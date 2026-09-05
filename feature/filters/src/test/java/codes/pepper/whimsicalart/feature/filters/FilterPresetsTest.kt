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
}
