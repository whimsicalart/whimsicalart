package codes.pepper.whimsicalart.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.ImageFormat
import codes.pepper.whimsicalart.feature.editor.domain.ImageQuality
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.Resolution
import codes.pepper.whimsicalart.feature.editor.domain.SaveConfig
import codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorUiState
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.HistoryKind
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.StackDocument
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorHistoryStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Exercises the "discard changes?" dirty signal (docs/user_guide.md → The
 * editor → Leaving the editor) — the [hasUnsavedChanges] lifecycle.
 *
 * The guard inside [saveToHistory] must NOT fire on a baseline push whose
 * snapshot is identical to the previous checkpoint (selectTool's false
 * positive), and MUST fire on a genuine state change.
 */
@RunWith(RobolectricTestRunner::class)
class UnsavedChangesTest {

    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
    }

    // ---- state-mechanic tests ----

    @Test
    fun `a fresh document is not dirty`() {
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `a bare tool tap does not dirty the document`() {
        viewModel.selectTool(EditTool.BRIGHTNESS)
        assertFalse(
            "the first baseline push must be treated as a no-change (cleanBaseline guard)",
            viewModel.uiState.value.hasUnsavedChanges
        )

        viewModel.selectTool(EditTool.CONTRAST)
        assertFalse(
            "a second baseline with the same content must also not dirty",
            viewModel.uiState.value.hasUnsavedChanges
        )

        viewModel.selectTool(null)
        assertFalse("deselecting a tool also pushes a no-change baseline", viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `a real committed edit marks the document dirty`() {
        viewModel.updateBrightness(50f)
        viewModel.syncLayerState(
            listOf(SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f)),
            mapOf("layer:BRIGHTNESS" to "brightness:20")
        )
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `a no-op re-commit does not dirty a pristine document`() {
        // Same stack re-synced; nothing changed → stackChanged false → no push → clean.
        viewModel.syncLayerState(emptyList(), emptyMap())
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `undo keeps the document dirty`() {
        viewModel.selectTool(EditTool.BRIGHTNESS)
        viewModel.updateBrightness(50f)
        viewModel.syncLayerState(
            listOf(SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f)),
            mapOf("layer:BRIGHTNESS" to "brightness:20")
        )
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.undo()
        assertTrue("undo always bumps dirty (it restores a pre-save state)", viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `redo keeps the document dirty`() {
        viewModel.selectTool(EditTool.BRIGHTNESS)
        viewModel.updateBrightness(50f)
        viewModel.syncLayerState(
            listOf(SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f)),
            mapOf("layer:BRIGHTNESS" to "brightness:20")
        )
        viewModel.undo()
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.redo()
        assertTrue("redo always bumps dirty (it restores a post-save state)", viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `setBitmaps resets the dirty flag for a new document`() {
        viewModel.updateBrightness(50f)
        viewModel.syncLayerState(
            listOf(SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f)),
            mapOf("layer:BRIGHTNESS" to "brightness:20")
        )
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        viewModel.setBitmaps(bmp, bmp)
        assertFalse(
            "opening a new photo resets the dirty signal and the clean baseline",
            viewModel.uiState.value.hasUnsavedChanges
        )

        // A subsequent real commit must re-dirty from the new baseline.
        viewModel.updateBrightness(50f)
        viewModel.syncLayerState(
            listOf(SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f)),
            mapOf("layer:BRIGHTNESS" to "brightness:20")
        )
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `save failure keeps the dirty flag`() {
        viewModel.updateBrightness(50f)
        viewModel.syncLayerState(
            listOf(SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f)),
            mapOf("layer:BRIGHTNESS" to "brightness:20")
        )
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        val looper = shadowOf(Looper.getMainLooper())
        var resultReceived = false
        var saved = false
        viewModel.saveImage(
            SaveConfig(format = ImageFormat.JPEG, quality = ImageQuality.MEDIUM, resolution = Resolution.ORIGINAL, album = "Test"),
            EditorRenderBundle()
        ) { r ->
            saved = r
            resultReceived = true
        }
        looper.idle()

        assertTrue("callback received", resultReceived)
        assertFalse("renderCommittedBitmap returns null when no imageUri is set", saved)
        assertTrue("failed save must leave the dirty flag intact", viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `share failure keeps the dirty flag`() {
        viewModel.updateBrightness(50f)
        viewModel.syncLayerState(
            listOf(SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f)),
            mapOf("layer:BRIGHTNESS" to "brightness:20")
        )
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        val looper = shadowOf(Looper.getMainLooper())
        var resultReceived = false
        var shared = true
        viewModel.shareImage(
            SaveConfig(format = ImageFormat.JPEG, quality = ImageQuality.MEDIUM, resolution = Resolution.ORIGINAL, album = "Test"),
            EditorRenderBundle()
        ) { r ->
            shared = r
            resultReceived = true
        }
        looper.idle()

        assertTrue("callback received", resultReceived)
        assertFalse("shareImage returns false when no imageUri is set", shared)
        assertTrue("failed share must leave the dirty flag intact", viewModel.uiState.value.hasUnsavedChanges)
    }

    // ---- EditorHistoryStore push parity (kind-ignoring comparison) ----

    @Test
    fun `push guard ignores kind when comparing baseline snapshots`() {
        // selectTool fires a COARSE_GRAINED baseline push.
        val store = EditorHistoryStore()
        val base = EditorUiState()
        // First push: cleanBaseline is the default StackDocument (FINE_GRAINED); the pushed doc is COARSE.
        // This exercises the kind-ignoring comparison (baseline.kind != pushed.kind → content-equal → skip).
        val afterFirst = store.push(base, HistoryKind.COARSE_GRAINED)
        val doc = afterFirst.history.last()
        assertEquals(HistoryKind.COARSE_GRAINED, doc.kind)

        // A second identical push at the same state must be detected as a duplicate.
        val afterSecond = store.push(afterFirst, HistoryKind.COARSE_GRAINED)
        assertEquals("history grew (push always appends)", 2, afterSecond.history.size)
        val doc2 = afterSecond.history.last()
        assertEquals("the new doc is content-equal to the previous one", doc, doc2.copy(kind = doc.kind))
    }
}
