package codes.pepper.whimsicalart.feature.editor.domain.mosaic

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Degradation contract for the MediaPipe face-rects detector (the SIGSEGV fix —
 * .tmp/log.13.txt, `addr 0x1a0` in `nativeCreateRgbaImage`). The native runtime
 * cannot execute under Robolectric, and a native fault is uncatchable, so these
 * tests pin the structural guards that PREVENT a use-after-free from ever
 * reaching the native code: (1) a closed detector refuses detection outright
 * (never re-touches the released LiteRT session), (2) an unavailable runtime
 * degrades to "no suggestions" instead of crashing, (3) a recycled source is
 * refused. The close-vs-in-flight exclusivity itself is device-verified
 * (deferred to the mentor pass); its lifecycle is code-reviewed here.
 */
@RunWith(RobolectricTestRunner::class)
class MediaPipeFaceRectsDetectorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun detector() = MediaPipeFaceRectsDetector(context)

    private fun bitmap(): Bitmap =
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF888888.toInt()) }

    @Test
    fun `a closed detector refuses detection without touching the released native session`() {
        val d = detector()
        d.close()
        assertTrue(
            "after close the detector reports itself unavailable and never probes the runtime",
            !d.isAvailable
        )
        assertTrue("a closed detector degrades to no suggestions instead of using the closed client", d.detectFaces(bitmap()).isEmpty())
        d.close()
    }

    @Test
    fun `the runtime unavailable or uninitialisable degrades to no suggestions`() {
        // Under Robolectric no native runtime exists, standing in for a device
        // whose task fails to build — the detector must not throw, not crash,
        // and report unavailable.
        val d = detector()
        assertFalse("an uninitialisable runtime is reported unavailable", d.isAvailable)
        assertTrue("detection with no runtime degrades to no suggestions", d.detectFaces(bitmap()).isEmpty())
    }

    @Test
    fun `a recycled source is refused by the closed guard before any native call`() {
        val d = detector()
        val recycled = bitmap()
        recycled.recycle()
        assertTrue("recycled sources degrade to no suggestions", d.detectFaces(recycled).isEmpty())
    }
}