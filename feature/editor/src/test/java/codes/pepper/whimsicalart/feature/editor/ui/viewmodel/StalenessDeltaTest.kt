package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for the derived-stack staleness decision extracted from
 * `EditorViewModel.stalenessForDerivedDelta` (BP-6) — no ViewModel, no
 * Robolectric, plain JUnit. The rules a change at the tail appends the cached
 * tail commit; any change at or before a prefix position marks prev+last stale;
 * a removed last marks lastImage stale unconditionally.
 */
class StalenessDeltaTest {

    private fun delta(before: List<String>, after: List<String>): StalenessDelta =
        deriveStalenessDelta(before, after)

    @Test
    fun `equal sequences report no meaningful change - firstDiff at the tail`() {
        val d = delta(listOf("a", "b", "c"), listOf("a", "b", "c"))
        assertEquals(3, d.firstDiffIndex)
        assertFalse(d.tailAppend)
    }

    @Test
    fun `single pure tail append is flagged as such`() {
        val d = delta(listOf("a", "b"), listOf("a", "b", "c"))
        assertEquals(2, d.firstDiffIndex)
        assertTrue(d.tailAppend)
    }

    @Test
    fun `append onto an empty stack is a tail append at index zero`() {
        val d = delta(emptyList(), listOf("a"))
        assertEquals(0, d.firstDiffIndex)
        assertTrue(d.tailAppend)
    }

    @Test
    fun `insert in the middle is not a tail append`() {
        val d = delta(listOf("a", "b", "c"), listOf("a", "x", "b", "c"))
        assertEquals(1, d.firstDiffIndex)
        assertFalse(d.tailAppend)
    }

    @Test
    fun `replacing the first effect diffs at zero`() {
        val d = delta(listOf("a", "b"), listOf("x", "b"))
        assertEquals(0, d.firstDiffIndex)
        assertFalse(d.tailAppend)
    }

    @Test
    fun `replacing the last effect diffs at the last index without a tail flag`() {
        val d = delta(listOf("a", "b"), listOf("a", "x"))
        assertEquals(1, d.firstDiffIndex)
        assertFalse(d.tailAppend)
    }

    @Test
    fun `removing the last effect diffs past the previous prefix`() {
        val d = delta(listOf("a", "b", "c"), listOf("a", "b"))
        assertEquals(2, d.firstDiffIndex)
        assertFalse(d.tailAppend)
    }

    @Test
    fun `removing middle effects diffs at the first missing index`() {
        val d = delta(listOf("a", "b", "c"), listOf("a"))
        assertEquals(1, d.firstDiffIndex)
        assertFalse(d.tailAppend)
    }

    @Test
    fun `grow by one with a changed prefix is not a tail append`() {
        val d = delta(listOf("a", "b"), listOf("x", "b", "c"))
        assertEquals(0, d.firstDiffIndex)
        assertFalse(d.tailAppend)
    }

    @Test
    fun `removing everything reports a diff at index zero`() {
        val d = delta(listOf("a", "b"), emptyList())
        assertEquals(0, d.firstDiffIndex)
        assertFalse(d.tailAppend)
    }
}