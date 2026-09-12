package codes.pepper.whimsicalart.feature.editor.ui.mosaic

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.mosaic.MediaPipeFaceRectsDetector
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/**
 * Debounce contract for the mosaic live preview (docs/effects_reference.md →
 * Live-applied brush effects): the real pixelation fires only after a full,
 * uninterrupted 500 ms of stillness/release; any resumed stroking cancels and
 * restarts the countdown. Mirrors RemovalViewModelTest / BlurViewModelTest.
 * None of these paths touch MediaPipe inference (only suggestPrivacyRegions does).
 */
@RunWith(RobolectricTestRunner::class)
class MosaicViewModelTest {

    private lateinit var vm: MosaicViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        vm = MosaicViewModel(context, MediaPipeFaceRectsDetector(context))
    }

    private fun idle(millis: Long) {
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
    }

    private fun stroke() {
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(60f, 60f))
        vm.onDragEnd()
    }

    @Test
    fun `release applies live mosaic after a full half second of quiet`() {
        stroke()
        assertFalse(vm.uiState.value.liveMosaicApplied)
        idle(499)
        assertFalse(vm.uiState.value.liveMosaicApplied)
        idle(1)
        assertTrue(vm.uiState.value.liveMosaicApplied)
    }

    @Test
    fun `resuming to stroke within half a second cancels the countdown and restarts it`() {
        stroke()
        idle(300)
        assertFalse(vm.uiState.value.liveMosaicApplied)
        vm.onDragStart(Offset(0f, 0f))
        idle(300)
        assertFalse("countdown restarted at the resume", vm.uiState.value.liveMosaicApplied)
        idle(201)
        assertTrue(vm.uiState.value.liveMosaicApplied)
    }

    @Test
    fun `stillness while pressing applies without lifting the finger`() {
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(40f, 40f))
        idle(500)
        assertTrue(vm.uiState.value.liveMosaicApplied)
    }

    @Test
    fun `resuming motion past the debounce drops the live result`() {
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(40f, 40f))
        idle(500)
        assertTrue(vm.uiState.value.liveMosaicApplied)
        vm.onDragMove(Offset(80f, 80f))
        assertFalse(vm.uiState.value.liveMosaicApplied)
    }

    @Test
    fun `no strokes never applies a live mosaic`() {
        idle(2000)
        assertFalse(vm.uiState.value.liveMosaicApplied)
    }

    @Test
    fun `erasing away every stroke cancels the pending application`() {
        stroke()
        idle(300)
        vm.toggleErasing()
        vm.onDragStart(Offset(15f, 15f))
        vm.onDragEnd()
        assertTrue(vm.uiState.value.strokes.isEmpty())
        idle(1000)
        assertFalse(vm.uiState.value.liveMosaicApplied)
    }

    @Test
    fun `clear cancels the pending application`() {
        stroke()
        idle(300)
        vm.clear()
        idle(1000)
        assertFalse(vm.uiState.value.liveMosaicApplied)
    }

    @Test
    fun `settling a stroke increments the committed-strokes version`() {
        assertEquals(0, vm.uiState.value.strokesVersion)
        stroke()
        assertEquals("one settled stroke bumps the version", 1, vm.uiState.value.strokesVersion)
        vm.onDragStart(Offset(0f, 0f))
        vm.onDragMove(Offset(30f, 30f))
        vm.onDragEnd()
        assertEquals("a second settled stroke bumps it again", 2, vm.uiState.value.strokesVersion)
        vm.clear()
        assertEquals("clearing committed strokes bumps the version", 3, vm.uiState.value.strokesVersion)
    }

    @Test
    fun `mid-drag motion does not bump the committed-strokes version`() {
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(40f, 40f))
        vm.onDragMove(Offset(60f, 60f))
        assertEquals("only a settled stroke advances the version", 0, vm.uiState.value.strokesVersion)
    }

    @Test
    fun `a live stroke freezes into strokes once the debounce fires mid-press`() {
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(40f, 40f))
        idle(500)
        assertTrue(vm.uiState.value.liveMosaicApplied)
        assertEquals(1, vm.uiState.value.strokes.size)
        assertNull(vm.uiState.value.currentStroke)
    }

    @Test
    fun `suggested regions stay independent of the live-mosaic folding`() {
        // Suggestion state is a separate channel: adding/clearing suggestions
        // must not touch the live-apply flag or the stroke list.
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragEnd()
        idle(500)
        assertTrue(vm.uiState.value.liveMosaicApplied)
        vm.clearSuggestions()
        assertTrue(vm.uiState.value.liveMosaicApplied)
        assertEquals(1, vm.uiState.value.strokes.size)
    }

    @Test
    fun `face-detection decode is down-sampled so nv21 stays in the low megabytes`() {
        // The crash (tracker: "Suggest faces (privacy)" OOM) allocated ~241 MB for
        // a full-res NV21; the detector bitmap is now capped at 1280px long side —
        // NV21 of the capped decode is at most ~1280×1280×1.5 ≈ 2.5 MB.
        // The pure helper picks the power-of-two sample that honours the cap.
        assertEquals(1, detectionSampleSize(1280))
        assertEquals(1, detectionSampleSize(1000))
        assertEquals(2, detectionSampleSize(1281))
        assertEquals(2, detectionSampleSize(2000))
        assertEquals(4, detectionSampleSize(4000))
        assertEquals(8, detectionSampleSize(8000))
        assertEquals(16, detectionSampleSize(16000))
        // After sampling, the long side always lands at/below the cap.
        for (long in listOf(1030, 1290, 2500, 4000, 6000, 12000)) {
            val sample = detectionSampleSize(long)
            assertTrue("long=$long sample=$sample", long / sample <= DETECTION_MAX_LONG_SIDE_PX)
            assertTrue("sample must stay a power of two", sample == sample.takeLowestOneBit())
        }
    }

    @Test
    fun `detection sample size is relative to the configured cap`() {
        assertEquals(2, detectionSampleSize(2000, maxLongSidePx = 1024))
        assertEquals(16, detectionSampleSize(2000, maxLongSidePx = 200))
        assertEquals(1, detectionSampleSize(200, maxLongSidePx = 200))
    }
}