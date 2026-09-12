package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

import codes.pepper.whimsicalart.feature.beauty.ui.BeautyParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [EditorHistoryStore] — no ViewModel / Robolectric needed
 * because the store is a pure state transformer (BP-6 follow-up: the caps and
 * the undo/redo restore became independently testable once extracted).
 */
class EditorHistoryStoreTest {

    private val store = EditorHistoryStore()

    private fun stateWith(marker: Float): EditorUiState =
        EditorUiState().copy(brightness = marker, vignette = marker)

    @Test
    fun `push records a document and advances the cursor`() {
        val s0 = stateWith(1f)
        val s1 = store.push(s0, HistoryKind.FINE_GRAINED)
        assertEquals(1, s1.history.size)
        assertEquals(0, s1.historyIndex)
        assertEquals(1f, s1.history.last().brightness, 0f)
        assertEquals(1f, s1.history.last().vignette, 0f)
        assertNotSame("push must not mutate the previous state", s1, s0)

        val s2 = store.push(s1, HistoryKind.COARSE_GRAINED)
        assertEquals("two pushes → two documents, 0-based cursor at 1", 1, s2.historyIndex)
        assertEquals(2, s2.history.size)
        assertEquals(HistoryKind.COARSE_GRAINED, s2.history.last().kind)
    }

    @Test
    fun `push after undo drops the redo tail`() {
        val s0 = stateWith(1f)
        val s1 = store.push(s0, HistoryKind.FINE_GRAINED) // A
        val s2 = store.push(s0, HistoryKind.FINE_GRAINED).let { store.push(it, HistoryKind.FINE_GRAINED) } // B
        val undoCursor = store.restore(s2, s1.history.last(), s1.historyIndex) // back on A

        val afterNewEdit = store.push(undoCursor, HistoryKind.FINE_GRAINED) // C

        assertEquals("B must be dropped from the redo tail", 2, afterNewEdit.history.size)
        assertEquals(1, afterNewEdit.historyIndex)
        assertEquals(1f, afterNewEdit.history.last().brightness, 0f)
    }

    @Test
    fun `restore applies the document fields, cursor and beauty params`() {
        val s0 = stateWith(1f)
        val sA = store.push(s0, HistoryKind.FINE_GRAINED) // doc(1)
        val sB = store.push(
            sA.copy(brightness = 7f, vignette = 7f, beauty = BeautyParams(teeth = 7f)),
            HistoryKind.FINE_GRAINED
        ) // [doc(1), doc(7, teeth=7)]

        val undone = store.restore(sB, sB.history[0], 0)

        assertEquals(0, undone.historyIndex)
        assertEquals(1f, undone.brightness, 0f)
        assertEquals(1f, undone.vignette, 0f)
        assertEquals(BeautyParams(), undone.beauty)

        val redone = store.restore(sB, sB.history[1], 1)
        assertEquals(1, redone.historyIndex)
        assertEquals(7f, redone.brightness, 0f)
        assertEquals(7f, redone.vignette, 0f)
        assertEquals(7f, redone.beauty.teeth, 0f)
    }

    @Test
    fun `fine-grained cap keeps the newest thirty entries`() {
        var state = stateWith(0f)
        repeat(40) { state = store.push(state, HistoryKind.FINE_GRAINED) }
        assertTrue(state.history.size <= 30)
        assertEquals(state.historyIndex, state.history.size - 1)
    }

    @Test
    fun `coarse-grained commits survive a long fine-grained run`() {
        var state = store.push(stateWith(0f), HistoryKind.COARSE_GRAINED)
        repeat(30) { state = store.push(state, HistoryKind.FINE_GRAINED) }
        assertTrue("the coarse commit is never evicted by fine tweaks",
            state.history.count { it.kind == HistoryKind.COARSE_GRAINED } == 1)
        assertEquals("newest entry is a fine-grained tweak", HistoryKind.FINE_GRAINED, state.history.last().kind)
    }

    @Test
    fun `coarse-grained cap bounds coarse entries and never evicts the newest`() {
        var state = stateWith(0f)
        repeat(100) { state = store.push(state, HistoryKind.COARSE_GRAINED) }
        assertTrue("coarse cap of 100 holds", state.history.count { it.kind == HistoryKind.COARSE_GRAINED } <= 100)
        assertEquals("the newest checkpoint is the very entry just pushed",
            HistoryKind.COARSE_GRAINED, state.history.last().kind)
        assertTrue("cursor always ends on the newest", state.historyIndex == state.history.size - 1)
    }

    @Test
    fun `both caps apply per kind keeping the newest of each kind in order`() {
        val fine = (0..129).map { i ->
            StackDocument(effectStack = emptyList(), kind = HistoryKind.FINE_GRAINED, vignette = i.toFloat())
        }
        val coarse = (0..129).map { i ->
            StackDocument(effectStack = emptyList(), kind = HistoryKind.COARSE_GRAINED, brightness = i.toFloat())
        }
        val kept = store.enforceLimits(fine + coarse)

        assertEquals("30 fine + 100 coarse survive the two caps", 130, kept.size)
        assertEquals(30, kept.count { it.kind == HistoryKind.FINE_GRAINED })
        assertEquals(100, kept.count { it.kind == HistoryKind.COARSE_GRAINED })
        assertEquals("only the OLDEST fine entries are evicted", 100f, kept.first { it.kind == HistoryKind.FINE_GRAINED }.vignette, 0f)
        assertEquals("only the OLDEST coarse entries are evicted", 30f, kept.first { it.kind == HistoryKind.COARSE_GRAINED }.brightness, 0f)
        val fineMarkers = kept.filter { it.kind == HistoryKind.FINE_GRAINED }.map { it.vignette.toInt() }
        val coarseMarkers = kept.filter { it.kind == HistoryKind.COARSE_GRAINED }.map { it.brightness.toInt() }
        assertEquals("fine entries keep their interleaving order", fineMarkers.sorted(), fineMarkers)
        assertEquals("coarse entries keep their interleaving order", coarseMarkers.sorted(), coarseMarkers)
    }
}