package codes.pepper.whimsicalart.feature.editor.domain.bokeh

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: on real devices API ≥ 31 the old RenderNode blur path threw
 * `IllegalArgumentException: Software rendering doesn't support
 * drawRenderNode` because `applyBackgroundBlur` was called into a software
 * bitmap canvas. After the fix (RenderNode path removed; always software
 * blur), this test — running under Robolectric SDK 34 — proves the full
 * BLUR preview frame completes without throwing and produces a non-blank
 * result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BokehProcessorApi34Test {

    @Test
    fun applyBackgroundBlur_succeeds_on_api_34_without_throwing() {
        val w = 32
        val h = 32
        val original = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(100, 180, 240))
        }
        // Mask: left half opaque (foreground), right half transparent (background).
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val maskPx = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                maskPx[y * w + x] = if (x < w / 2) Color.WHITE else Color.TRANSPARENT
            }
        }
        mask.setPixels(maskPx, 0, w, 0, 0, w, h)

        val result = BokehProcessor().applyBackgroundBlur(original, mask, blurRadius = 3f)

        assertTrue("result is non-null", result != null)
        assertTrue("result is not recycled", !result.isRecycled)
        assertEquals("result dimensions match", w, result.width)
        assertEquals("result dimensions match", h, result.height)

        // The result should not be entirely blank (all zeros) — at least some
        // foreground pixels must remain the original colour.
        var nonZero = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (result.getPixel(x, y) != 0) nonZero++
            }
        }
        assertTrue("result has non-zero pixels", nonZero > 0)
    }
}
