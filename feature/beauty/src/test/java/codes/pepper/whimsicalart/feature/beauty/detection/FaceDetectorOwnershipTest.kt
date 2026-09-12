package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The ML detectors must never hand a CALLER-owned bitmap to native inference:
 * a caller-side recycle (a concurrent fold-cache recycle is the on-device case)
 * could invalidate pixels native still reads — the recycled-bitmap warnings +
 * SIGSEGV seen on device. A recycled input must short-circuit to empty BEFORE
 * any native access (including the lazy task creation), and the owned-copy path
 * never touches the caller's bitmap after the copy.
 */
@RunWith(RobolectricTestRunner::class)
class FaceDetectorOwnershipTest {

    @Test
    fun `face mesh detector returns empty for a recycled bitmap without native access`() {
        val detector = MediaPipeFaceMeshDetector(RuntimeEnvironment.getApplication())
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        // Must return BEFORE the lazy FaceLandmarker is created.
        assertEquals(emptyList<FaceMeshFace>(), detector.detect(bitmap))
    }

    @Test
    fun `hair segmenter returns null for a recycled bitmap`() {
        val detector = MediaPipeHairSegmenter(RuntimeEnvironment.getApplication())
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        assertEquals(null, detector.segment(bitmap))
    }
}