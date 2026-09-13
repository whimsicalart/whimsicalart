package codes.pepper.whimsicalart.feature.editor

import android.content.Context
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyParams
import codes.pepper.whimsicalart.feature.editor.domain.BeautyAutoEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyPenEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautySlimEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautySmoothingEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.domain.toBeautyEffects
import codes.pepper.whimsicalart.feature.editor.ui.buildStackSnapshot
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.buildBeautySpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The BEAUTY commit-on-lost-focus contract (docs/effect_stack.md → Beauty
 * commit on lost focus): while the BEAUTY tool is focused the editor is
 * UNSTACKED — the live params are the single source of truth, every fold
 * derives the current beauty layers from them, and the layers are committed to
 * the reversible stack exactly once on lost focus (the screen's
 * buildStackSnapshot → syncLayerState path). One commit = one undo step; slider
 * ticks never flood the history; revisiting the tool with unchanged values
 * records nothing.
 */
@RunWith(RobolectricTestRunner::class)
class BeautyCommitOnLostFocusTest {

    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
    }

    private fun liveBeauty(): List<BeautyStackEffect> =
        viewModel.uiState.value.beauty.buildBeautySpecs().toBeautyEffects()

    /**
     * The semantic content of a derived beauty layer, ignoring instance identity
     * (`id`, `geometry`, the per-call processor instance) — every derive call
     * regenerates those, so object equality is meaningless between a live fold,
     * a lost-focus snapshot and the committed stack. This is the same
     * id/geometry-independent view the commit bookkeeping uses.
     */
    private fun BeautyStackEffect.sig(): Any = when (this) {
        is BeautyAutoEffect -> "auto:${intensity}"
        is BeautySmoothingEffect -> "smoothing:${intensity}"
        is BeautySlimEffect -> "slim:${intensity}"
        is BeautyPenEffect -> "pen:${color}:${opacity}:${strokes}"
        else -> layerKey
    }

    private fun commitCurrentTool() {
        viewModel.syncLayerState(
            buildStackSnapshot(viewModel.uiState.value, EditorRenderBundle()),
            emptyMap()
        )
    }

    @Test
    fun `live params the lost-focus snapshot and the committed stack describe the same beauty layers`() {
        viewModel.selectTool(EditTool.BEAUTY)
        viewModel.updateBeautyParams {
            it.copy(
                auto = 50f, smoothing = 30f, faceSlim = 20f,
                penColor = 0xFF3366CC.toInt(), brushOpacity = 0.6f
            )
        }
        viewModel.startBeautyPenStroke(PointF(0.5f, 0.5f))
        viewModel.endBeautyPenStroke()

        // LIVE (focused fold): derived fresh from the params.
        val live = liveBeauty()
        // COMMIT (buildStackSnapshot): the beauty sub-set is derived from the
        // SAME specs, so "what you see" is exactly "what gets stored".
        val snapshotBeauty = buildStackSnapshot(viewModel.uiState.value, EditorRenderBundle())
            .filterIsInstance<BeautyStackEffect>()
        assertEquals("expected auto + smoothing + slim + pen", 4, live.size)
        assertEquals(
            "live preview must equal the lost-focus commit",
            live.map { it.sig() }, snapshotBeauty.map { it.sig() }
        )

        // While focused the committed stack is untouched (UNSTACKED).
        assertTrue(viewModel.uiState.value.effectStack.filterIsInstance<BeautyStackEffect>().isEmpty())

        // Commit on lost focus; the stored beauty sub-set equals the live one.
        commitCurrentTool()
        val committed = viewModel.uiState.value.effectStack.filterIsInstance<BeautyStackEffect>()
        assertEquals(
            "committed stack must equal the live preview",
            live.map { it.sig() }, committed.map { it.sig() }
        )
    }

    @Test
    fun `a beauty slider change dirties the session only at the lost-focus commit`() {
        viewModel.selectTool(EditTool.BEAUTY)
        val historyBefore = viewModel.uiState.value.history.size
        viewModel.setBeautyParams(BeautyParams(auto = 40f, smoothing = 25f))

        assertFalse(
            "slider streaming must not dirty while the tool is focused",
            viewModel.uiState.value.hasUnsavedChanges
        )
        assertEquals(
            "slider streaming must not add undo entries",
            historyBefore, viewModel.uiState.value.history.size
        )
        assertTrue(viewModel.uiState.value.effectStack.isEmpty())

        commitCurrentTool()

        val state = viewModel.uiState.value
        assertTrue("the lost-focus commit makes the session dirty", state.hasUnsavedChanges)
        assertEquals(
            "auto + smoothing are committed as separate layers",
            2, state.effectStack.count { it is BeautyStackEffect }
        )
    }

    @Test
    fun `a beauty pen stroke dirties the session only at the lost-focus commit`() {
        viewModel.selectTool(EditTool.BEAUTY)
        viewModel.startBeautyPenStroke(PointF(0.5f, 0.5f))
        viewModel.moveBeautyPenStroke(PointF(0.55f, 0.5f))
        viewModel.endBeautyPenStroke()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertTrue("the stroke must stay live in the params while focused", liveBeauty().isNotEmpty())
        assertTrue(viewModel.uiState.value.effectStack.filterIsInstance<BeautyPenEffect>().isEmpty())

        commitCurrentTool()

        val state = viewModel.uiState.value
        assertTrue(state.hasUnsavedChanges)
        assertEquals(1, state.effectStack.filterIsInstance<BeautyPenEffect>().size)
        assertEquals(1, liveBeauty().size)
    }

    @Test
    fun `reverting beauty params to the stored values commits no layer no checkpoint and no new dirty`() {
        viewModel.selectTool(EditTool.BEAUTY)
        viewModel.setBeautyParams(BeautyParams(auto = 50f))
        commitCurrentTool()
        val dirtyBeforeRevert = viewModel.uiState.value.hasUnsavedChanges
        val historyBeforeRevert = viewModel.uiState.value.history.size
        assertTrue(dirtyBeforeRevert)
        assertEquals(1, viewModel.uiState.value.effectStack.count { it is BeautyAutoEffect })

        // Revisit the tool, drift the value away, then bring it BACK to the
        // committed 50 — the drift is scrubbed before the commit.
        viewModel.setBeautyParams(BeautyParams(auto = 70f))
        assertEquals(70f, viewModel.uiState.value.beauty.auto, 1e-4f)
        viewModel.setBeautyParams(BeautyParams(auto = 50f))
        commitCurrentTool()

        val state = viewModel.uiState.value
        assertEquals(1, state.effectStack.count { it is BeautyAutoEffect })
        assertEquals("a revert must not add an undo entry", historyBeforeRevert, state.history.size)
        assertEquals("... and must not produce new dirty", dirtyBeforeRevert, state.hasUnsavedChanges)
    }

    @Test
    fun `revisiting the beauty tool with nothing changed commits nothing`() {
        viewModel.selectTool(EditTool.BEAUTY)
        val historyBefore = viewModel.uiState.value.history.size

        commitCurrentTool()

        val state = viewModel.uiState.value
        assertEquals(historyBefore, state.history.size)
        assertFalse(state.hasUnsavedChanges)
        assertTrue(state.effectStack.isEmpty())
    }

    @Test
    fun `a single undo reverts exactly the whole lost-focus beauty commit`() {
        viewModel.selectTool(EditTool.BEAUTY)
        viewModel.setBeautyParams(BeautyParams(auto = 50f, faceSlim = 20f))
        commitCurrentTool()

        val committed = viewModel.uiState.value
        assertEquals(2, committed.effectStack.count { it is BeautyStackEffect })
        assertTrue(committed.hasUnsavedChanges)
        val committedIndex = committed.historyIndex

        viewModel.undo()

        val reverted = viewModel.uiState.value
        assertTrue(
            "undo must remove the committed beauty layers from the stack",
            reverted.effectStack.none { it is BeautyStackEffect }
        )
        assertEquals("one undo step collapses the pre+post checkpoint pair", committedIndex - 1, reverted.historyIndex)
        // Undo returns to the FOCUSED state: the params stay live and uncommitted
        // until the next lost-focus commit (the same as undoing any tool whose
        // controls hold the current values).
        assertEquals(50f, reverted.beauty.auto, 1e-4f)
        assertEquals(20f, reverted.beauty.faceSlim, 1e-4f)
    }

    @Test
    fun `slider drags never flood the undo history - only the single lost-focus commit is a step`() {
        viewModel.selectTool(EditTool.BEAUTY)
        val baselineHistory = viewModel.uiState.value.history.size

        repeat(30) { i -> viewModel.setBeautyParams(BeautyParams(auto = (i + 1) * 2f)) }

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals(
            "30 param ticks record no undo entries",
            baselineHistory, viewModel.uiState.value.history.size
        )

        commitCurrentTool()
        val committed = viewModel.uiState.value
        assertEquals("one commit = the pre+post checkpoint pair", baselineHistory + 2, committed.history.size)
        assertTrue(committed.hasUnsavedChanges)

        viewModel.undo()
        val reverted = viewModel.uiState.value
        assertTrue(reverted.effectStack.none { it is BeautyStackEffect })
        assertEquals(baselineHistory, reverted.historyIndex)
    }

    @Test
    fun `committed beauty layers stay separate reorderable and re-editable in place`() {
        viewModel.selectTool(EditTool.BEAUTY)
        viewModel.setBeautyParams(BeautyParams(auto = 50f, faceSlim = 20f))
        commitCurrentTool()

        assertEquals(listOf("beauty:auto", "beauty:slim"), viewModel.uiState.value.effectStack.map { it.layerKey })

        // Layers drag: move slim before auto.
        viewModel.moveEffect(1, 0)
        assertEquals(listOf("beauty:slim", "beauty:auto"), viewModel.uiState.value.effectStack.map { it.layerKey })

        // Re-edit the auto slot (re-enter the tool, change the value, lose focus).
        viewModel.setBeautyParams(BeautyParams(auto = 70f, faceSlim = 20f))
        commitCurrentTool()

        val stack = viewModel.uiState.value.effectStack
        assertEquals(
            "the re-committed layer updates IN PLACE, preserving its reordered position",
            listOf("beauty:slim", "beauty:auto"), stack.map { it.layerKey }
        )
        assertEquals(2, stack.size)
        assertEquals(70f, stack.filterIsInstance<BeautyAutoEffect>().single().intensity, 1e-4f)
        assertEquals(20f, stack.filterIsInstance<BeautySlimEffect>().single().intensity, 1e-4f)
    }
}