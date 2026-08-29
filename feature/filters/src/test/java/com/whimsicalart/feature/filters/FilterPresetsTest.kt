package com.whimsicalart.feature.filters

import com.whimsicalart.feature.filters.domain.FilterCategory
import com.whimsicalart.feature.filters.domain.FilterPresets
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
        val brightness = FilterPresets.getFilterById("brightness")
        assertNotNull(brightness)
        assertEquals("Brightness", brightness?.name)
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
    fun `getFiltersByCategory returns empty for unused category`() {
        val filters = FilterPresets.getFiltersByCategory(FilterCategory.LANDSCAPE)
        assertTrue(filters.isEmpty())
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
}
