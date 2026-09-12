package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class MaskedEffectRendererTest {

    @Before
    fun setup() {
        MaskedEffectRenderer.clearMaskCache()
    }

    private fun squarePath(): Path = Path().apply {
        moveTo(10f, 10f)
        lineTo(40f, 10f)
        lineTo(40f, 40f)
        lineTo(10f, 40f)
        close()
    }

    @Test
    fun `identical geometry reuses the cached feathered mask across alpha ticks`() {
        val canvas = Canvas(Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888))

        MaskedEffectRenderer.drawFeatheredMask(
            canvas, squarePath(),
            Paint().apply { color = Color.argb(128, 0xE0, 0x20, 0x20) }
        )
        assertEquals(1, MaskedEffectRenderer.maskRenderCount)

        // A slider-drag tick: the path is REBUILT from the same geometry (so it
        // is a different Path instance) and only the alpha (intensity) changes.
        // The key is RGB geometry, so this must be a cache HIT.
        val dragTickPath = squarePath()
        MaskedEffectRenderer.drawFeatheredMask(
            canvas, dragTickPath,
            Paint().apply { color = Color.argb(64, 0xE0, 0x20, 0x20) }
        )
        assertEquals("alpha-only changes recomposite the cached mask", 1, MaskedEffectRenderer.maskRenderCount)

        // A genuinely different RGB colour is a different cached artefact.
        MaskedEffectRenderer.drawFeatheredMask(
            canvas, dragTickPath,
            Paint().apply { color = Color.argb(128, 0x10, 0x80, 0x30) }
        )
        assertEquals(2, MaskedEffectRenderer.maskRenderCount)
    }

    @Test
    fun `feathered edge is a fraction of the mask short edge at every scale`() {
        // One effect, two scales: the same relative feather must reach the same
        // soft area at preview and full size — an 8x bigger mask feathers 8x.
        val preview = MaskedEffectRenderer.featheredEdge(200f)
        val full = MaskedEffectRenderer.featheredEdge(1600f)
        assertEquals("18% of a 200px preview mask", 36, preview)
        assertEquals("full-size feathers proportionally", preview * 8, full)
    }

    @Test
    fun `feathered edge keeps the historical reach at the reference size`() {
        // The old fixed cap kept the feather at 24px on a 200px mask — the
        // 18% derivation stays in that neighbourhood at preview scale.
        assertEquals(36, MaskedEffectRenderer.featheredEdge(200f))
        assertTrue(MaskedEffectRenderer.featheredEdge(50f) >= 2)
    }

    @Test
    fun `feathered edge ceiling is a fraction of the mask, never a fixed px cap`() {
        // A 4000px full-size face mask must feather several hundred px (the same
        // ~18% the preview gets), not the historical fixed 24px ceiling.
        assertEquals(720, MaskedEffectRenderer.featheredEdge(4000f))
    }
}