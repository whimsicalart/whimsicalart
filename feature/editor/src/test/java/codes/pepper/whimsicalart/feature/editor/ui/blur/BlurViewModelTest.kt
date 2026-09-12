package codes.pepper.whimsicalart.feature.editor.ui.blur

import androidx.compose.ui.geometry.Offset
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
 * Debounce contract for the blur-brush live preview (docs/effects_reference.md
 * → Live-applied brush effects): the real blur fires only after a full,
 * uninterrupted 500 ms of stillness/release; any resumed stroking cancels and
 * restarts the countdown. Mirrors RemovalViewModelTest.
 */
@RunWith(RobolectricTestRunner::class)
class BlurViewModelTest {

    private lateinit var vm: BlurViewModel

    @Before
    fun setUp() {
        vm = BlurViewModel()
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
    fun `release applies live blur after a full half second of quiet`() {
        stroke()
        assertFalse(vm.uiState.value.liveBlurApplied)
        idle(499)
        assertFalse(vm.uiState.value.liveBlurApplied)
        idle(1)
        assertTrue(vm.uiState.value.liveBlurApplied)
    }

    @Test
    fun `resuming to stroke within half a second cancels the countdown and restarts it`() {
        stroke()
        idle(300)
        assertFalse(vm.uiState.value.liveBlurApplied)
        vm.onDragStart(Offset(0f, 0f))
        idle(300)
        assertFalse("countdown restarted at the resume", vm.uiState.value.liveBlurApplied)
        idle(201)
        assertTrue(vm.uiState.value.liveBlurApplied)
    }

    @Test
    fun `stillness while pressing applies without lifting the finger`() {
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(40f, 40f))
        idle(500)
        assertTrue(vm.uiState.value.liveBlurApplied)
    }

    @Test
    fun `resuming motion past the debounce drops the live result`() {
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(40f, 40f))
        idle(500)
        assertTrue(vm.uiState.value.liveBlurApplied)
        vm.onDragMove(Offset(80f, 80f))
        assertFalse(vm.uiState.value.liveBlurApplied)
    }

    @Test
    fun `no strokes never applies a live blur`() {
        idle(2000)
        assertFalse(vm.uiState.value.liveBlurApplied)
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
        assertFalse(vm.uiState.value.liveBlurApplied)
    }

    @Test
    fun `clear cancels the pending application`() {
        stroke()
        idle(300)
        vm.clear()
        idle(1000)
        assertFalse(vm.uiState.value.liveBlurApplied)
    }

    @Test
    fun `a live stroke freezes into strokes once the debounce fires mid-press`() {
        vm.onDragStart(Offset(10f, 10f))
        vm.onDragMove(Offset(40f, 40f))
        idle(500)
        assertTrue(vm.uiState.value.liveBlurApplied)
        assertEquals(1, vm.uiState.value.strokes.size)
        assertNull(vm.uiState.value.currentStroke)
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
}
