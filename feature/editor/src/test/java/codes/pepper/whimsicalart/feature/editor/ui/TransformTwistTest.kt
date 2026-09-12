package codes.pepper.whimsicalart.feature.editor.ui

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * next.md "Transform twist-rotate has NO live feedback": a two-finger twist must
 * rotate the photo in real time. The ViewModel accumulates the gesture's RAW
 * deltas into `liveRotationDegrees` (no per-event wrap, so crossing 0°/360°
 * never snaps), applies them to the preview immediately, and wraps the value
 * into the stored `rotation` exactly ONCE when the gesture settles — a single
 * history entry per gesture. The free-rotate slider mirrors the live angle via
 * [rotatedByDelta] (wraps for DISPLAY only), and a committed slider/button value
 * cancels any pending settle so the two never double-commit.
 */
@RunWith(RobolectricTestRunner::class)
class TransformTwistTest {

    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
    }

    /** Pumps the main looper past the settle window so a pending commit fires. */
    private fun settleTwist(extraMillis: Long = 150L) {
        shadowOf(Looper.getMainLooper()).idleFor(250L + extraMillis, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `twist deltas accumulate raw without touching the committed rotation`() {
        viewModel.applyTwistDelta(12f)
        viewModel.applyTwistDelta(-3f)
        viewModel.applyTwistDelta(30f)
        assertEquals(39f, viewModel.uiState.value.liveRotationDegrees, 1e-4f)
        assertEquals(0f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `live accumulation crosses the 0-360 seam without snapping back`() {
        // A 355°+15° twist keeps growing to 370° raw — the photo turns the short
        // way through zero continuously instead of snapping to 10° mid-gesture.
        viewModel.applyTwistDelta(355f)
        viewModel.applyTwistDelta(15f)
        assertEquals(370f, viewModel.uiState.value.liveRotationDegrees, 1e-4f)
        assertEquals(0f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `settle commits the wrapped angle once with a single history entry`() {
        viewModel.setRotation(30f)
        val historyBeforeGesture = viewModel.uiState.value.history.size

        viewModel.applyTwistDelta(5f)
        // Still inside the gesture: nothing committed yet.
        assertEquals(30f, viewModel.uiState.value.rotation)
        assertEquals(5f, viewModel.uiState.value.liveRotationDegrees, 1e-4f)

        settleTwist()
        assertEquals(35f, viewModel.uiState.value.rotation)
        assertEquals(0f, viewModel.uiState.value.liveRotationDegrees)
        assertEquals(historyBeforeGesture + 1, viewModel.uiState.value.history.size)
    }

    @Test
    fun `several deltas in one gesture still commit a single wrapped angle`() {
        viewModel.setRotation(30f)
        val historyBeforeGesture = viewModel.uiState.value.history.size

        viewModel.applyTwistDelta(4f)
        viewModel.applyTwistDelta(-9f)
        viewModel.applyTwistDelta(355f)
        // Raw live deltas: 4 - 9 + 355 = 350 (unwrapped); at commit 30 + 350 = 380 → 20°.
        assertEquals(350f, viewModel.uiState.value.liveRotationDegrees, 1e-4f)

        settleTwist()
        assertEquals(20f, viewModel.uiState.value.rotation)
        assertEquals(0f, viewModel.uiState.value.liveRotationDegrees)
        assertEquals(historyBeforeGesture + 1, viewModel.uiState.value.history.size)
    }

    @Test
    fun `a full free turn wraps the stored angle without wrapping the live one`() {
        viewModel.applyTwistDelta(361f)
        assertEquals(361f, viewModel.uiState.value.liveRotationDegrees, 1e-4f)

        settleTwist()
        assertEquals(1f, viewModel.uiState.value.rotation)
        assertEquals(0f, viewModel.uiState.value.liveRotationDegrees)
    }

    @Test
    fun `slider mirror wraps the sum of committed and live rotation`() {
        viewModel.setRotation(350f)
        viewModel.applyTwistDelta(15f)
        // The transform toolbar shows rotatedByDelta(rotation, live): 350+15=365 → 5°.
        assertEquals(5f, rotatedByDelta(viewModel.uiState.value.rotation, viewModel.uiState.value.liveRotationDegrees), 1e-4f)
    }

    @Test
    fun `a storyboard rotation cancels the pending settle instead of double-committing`() {
        viewModel.setRotation(30f)
        viewModel.applyTwistDelta(5f)
        viewModel.rotateRight()

        // rotateRight committed 120° and cancelled the settle; the settle window
        // must not then re-commit the stale live twist.
        settleTwist()
        assertEquals(120f, viewModel.uiState.value.rotation)
        assertEquals(0f, viewModel.uiState.value.liveRotationDegrees)
    }

    @Test
    fun `transform twist is wired to the selected tool only`() {
        // Classification contract the canvas wires on: the twist gesture exists
        // while TRANSFORM is selected (Category B), unlike pure Category A tools.
        // The screen gates it on `selectedTool == TRANSFORM && !eyeEnabled`; the
        // classify bit that makes the eye visible/required is the one below.
        assertTrue(isNonPanZoomCapable(EditTool.TRANSFORM, null))
    }

    @Test
    fun `over a composite the display restores only the unbaked rotation remainder`() {
        // next.md "transform toolbar controls don't work": the free-rotate slider
        // feeds COMMITTED rotation, which the old canvas knob ignored over a
        // composite (it read only the live twist). The new display bridge shows
        // `rotation − compositeBakedRotation` so a slider tick rotates instantly
        // while the re-fold chews; once the fold publishes the remainder is zero.
        assertEquals(
            "fresh state: committed == baked → nothing to restore",
            0f, displayRotation(useCompositePreview = true, committedRotation = 45f, bakedRotation = 45f, liveRotationDegrees = 0f, eyeEnabled = false), 1e-4f
        )
        assertEquals(
            "slider just moved 0→45, old composite baked 0 → the full 45 shows live",
            45f, displayRotation(useCompositePreview = true, committedRotation = 45f, bakedRotation = 0f, liveRotationDegrees = 0f, eyeEnabled = false), 1e-4f
        )
        assertEquals(
            "mid-gesture twist over the not-yet-rebased composite shows the live accumulation raw",
            45f, displayRotation(useCompositePreview = true, committedRotation = 0f, bakedRotation = 0f, liveRotationDegrees = 45f, eyeEnabled = false), 1e-4f
        )
        assertEquals(
            "twist over a fully-baked composite adds just the live frame",
            30f, displayRotation(useCompositePreview = true, committedRotation = 45f, bakedRotation = 45f, liveRotationDegrees = 30f, eyeEnabled = false), 1e-4f
        )
        assertEquals(
            "undo of a baked 90 (composite still shows it) regresses the remainder to upright",
            -90f, displayRotation(useCompositePreview = true, committedRotation = 0f, bakedRotation = 90f, liveRotationDegrees = 0f, eyeEnabled = false), 1e-4f
        )
        assertEquals(
            "short-way wrap: 0 target over a 359-baked composite steps one degree the short way",
            -359f, displayRotation(useCompositePreview = true, committedRotation = 0f, bakedRotation = 359f, liveRotationDegrees = 0f, eyeEnabled = false), 1e-4f
        )
    }

    @Test
    fun `over a composite the display mirrors only the unbaked flips`() {
        // Flips get the same bridge as rotation: the flip buttons commit
        // immediately while the composite still holds the old bake, so the layer
        // mirrors exactly the flips the pixels have NOT yet absorbed.
        assertEquals(false, displayFlip(useCompositePreview = true, committed = false, baked = false, eyeEnabled = false))
        assertEquals("flip just committed over the 0-baked composite mirrors live",
            true, displayFlip(useCompositePreview = true, committed = true, baked = false, eyeEnabled = false))
        assertEquals("fold published the flip → the composite already shows it",
            false, displayFlip(useCompositePreview = true, committed = true, baked = true, eyeEnabled = false))
        assertEquals("double-flip back to none over the 1-baked composite",
            true, displayFlip(useCompositePreview = true, committed = false, baked = true, eyeEnabled = false))
    }

    @Test
    fun `outside a composite the committed transform applies directly`() {
        // No composite (or eye-ON inspection) keeps the classic pass-through.
        assertEquals(57f, displayRotation(useCompositePreview = false, committedRotation = 45f, bakedRotation = 90f, liveRotationDegrees = 12f, eyeEnabled = false), 1e-4f)
        assertEquals(true, displayFlip(useCompositePreview = false, committed = true, baked = true, eyeEnabled = false))
        assertEquals("eye inspection withholds the transform",
            0f, displayRotation(useCompositePreview = false, committedRotation = 45f, bakedRotation = 0f, liveRotationDegrees = 0f, eyeEnabled = true), 1e-4f)
    }

    @Test
    fun `state copy stamps the baked transform for the composite`() {
        // The publish path stamps the CURRENT committed transform so the display
        // bridge zeroes the moment the fold lands (no pending-clear bookkeeping).
        val state = viewModel.uiState.value.copy(
            rotation = 45f,
            flipHorizontal = true,
            liveRotationDegrees = 30f
        ).copy(
            compositeBakedRotation = 45f,
            compositeBakedFlipHorizontal = true,
            compositeBakedFlipVertical = false
        )
        assertEquals(45f, state.compositeBakedRotation, 1e-4f)
        assertEquals(true, state.compositeBakedFlipHorizontal)
        assertEquals(false, state.compositeBakedFlipVertical)
    }
}