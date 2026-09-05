package codes.pepper.whimsicalart.feature.collage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageTemplateTest {

    @Test
    fun should_assign_exactly_one_cell_per_photo() {
        CollageTemplates.all.forEach { template ->
            assertEquals(
                "template ${template.id} photoCount must match cells",
                template.photoCount,
                template.cells.size
            )
        }
    }

    @Test
    fun should_keep_all_cells_inside_unit_square_and_non_degenerate() {
        CollageTemplates.all.forEach { template ->
            template.cells.forEachIndexed { index, cell ->
                assertTrue(
                    "cell $index of ${template.id} must start left of its right edge",
                    cell.left < cell.right
                )
                assertTrue(
                    "cell $index of ${template.id} must start above its bottom edge",
                    cell.top < cell.bottom
                )
                assertTrue(
                    "cell $index of ${template.id} left must be >= 0",
                    cell.left >= 0f
                )
                assertTrue(
                    "cell $index of ${template.id} top must be >= 0",
                    cell.top >= 0f
                )
                assertTrue(
                    "cell $index of ${template.id} right must be <= 1",
                    cell.right <= 1f
                )
                assertTrue(
                    "cell $index of ${template.id} bottom must be <= 1",
                    cell.bottom <= 1f
                )
            }
        }
    }

    @Test
    fun should_tile_the_unit_square_exactly() {
        CollageTemplates.all.forEach { template ->
            val totalArea = template.cells.sumOf { areaOf(it) }
            assertEquals(
                "template ${template.id} cells must cover the whole unit square",
                1.0,
                totalArea,
                1e-4
            )
        }
    }

    @Test
    fun should_not_overlap_cells_within_a_template() {
        CollageTemplates.all.forEach { template ->
            for (i in template.cells.indices) {
                for (j in i + 1 until template.cells.size) {
                    val a = template.cells[i]
                    val b = template.cells[j]
                    val overlap = areaOf(
                        intersectOf(a, b)
                    )
                    assertEquals(
                        "cells $i and $j of ${template.id} must not overlap",
                        0.0,
                        overlap,
                        1e-6
                    )
                }
            }
        }
    }

    @Test
    fun should_assign_unique_ids() {
        val ids = CollageTemplates.all.map { it.id }
        assertEquals("template ids must be unique", ids.size, ids.distinct().size)
    }

    @Test
    fun should_lookup_known_template_by_id() {
        CollageTemplates.all.forEach { template ->
            val found = CollageTemplates.byId(template.id)
            assertEquals("byId should return the matching template", template, found)
        }
    }

    @Test
    fun should_return_null_for_unknown_id() {
        assertNull(CollageTemplates.byId("does-not-exist"))
    }

    private fun areaOf(cell: CollageCell?): Double {
        if (cell == null) return 0.0
        return (cell.right - cell.left) * (cell.bottom - cell.top).toDouble()
    }

    private fun intersectOf(a: CollageCell, b: CollageCell): CollageCell? {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (left >= right || top >= bottom) return null
        return CollageCell(left, top, right, bottom)
    }
}