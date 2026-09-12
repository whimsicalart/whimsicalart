package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.PointF
import android.graphics.Rect
import codes.pepper.whimsicalart.feature.beauty.detection.DetectedFace
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult
import codes.pepper.whimsicalart.feature.beauty.detection.FaceLandmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BeautyGeometryTest {

    private fun faceResult(width: Int, height: Int): FaceDetectionResult = FaceDetectionResult(
        faces = listOf(
            DetectedFace(
                bounds = Rect(10, 20, 30, 40),
                leftEyeOpenProbability = 0f,
                rightEyeOpenProbability = 0f,
                smilingProbability = 0f,
                eulerY = 0f,
                eulerZ = 0f,
                landmarks = FaceLandmarks(
            leftEye = null,
            rightEye = null,
            nose = PointF(20f, 30f),
            mouthLeft = null,
            mouthRight = null,
            leftCheek = null,
            rightCheek = null
        )
            )
        ),
        imageWidth = width,
        imageHeight = height
    )

    @Test
    fun `rescaledTo rescales every coordinate field and re-anchors the base dims`() {
        val geometry = BeautyGeometry(faceResult = faceResult(100, 100), baseWidth = 100, baseHeight = 100)
        val scaled = geometry.rescaledTo(300, 200)
        assertEquals(300, scaled.baseWidth)
        assertEquals(200, scaled.baseHeight)
        val face = scaled.faceResult!!.faces.single()
        assertEquals(30, face.bounds.left)
        assertEquals(40, face.bounds.top)
        assertEquals(90, face.bounds.right)
        assertEquals(80, face.bounds.bottom)
        assertEquals(60f, face.landmarks.nose!!.x, 0f)
        assertEquals(60f, face.landmarks.nose!!.y, 0f)
        assertEquals(300, scaled.faceResult!!.imageWidth)
        assertEquals(200, scaled.faceResult!!.imageHeight)
    }

    @Test
    fun `rescaledTo preserves normalized coordinates within rounding`() {
        // The geometry-track cap contract: a geometry resolved over a 1–4 MP
        // capped base rescales up to the full-size fold with normalized
        // coordinates intact (within rounding), so swapping the capped
        // full-quality solve for the preview solve is seamless.
        val geometry = BeautyGeometry(faceResult = faceResult(1024, 768), baseWidth = 1024, baseHeight = 768)
        val scaled = geometry.rescaledTo(2897, 1448)
        val before = geometry.faceResult!!.faces.single().landmarks.nose!!
        val after = scaled.faceResult!!.faces.single().landmarks.nose!!
        assertEquals(before.x / 1024f, after.x / 2897f, 1e-3f)
        assertEquals(before.y / 768f, after.y / 1448f, 1e-3f)
    }

    @Test
    fun `rescaledTo is a no-op when unanchored or already at the target`() {
        val unanchored = BeautyGeometry(faceResult = faceResult(100, 100))
        assertSame(unanchored, unanchored.rescaledTo(300, 200))
        val anchored = BeautyGeometry(faceResult = faceResult(50, 50), baseWidth = 50, baseHeight = 50)
        assertSame(anchored, anchored.rescaledTo(50, 50))
    }

    @Test
    fun `mergedWith keeps pixel-space base dims from the retained geometry`() {
        val retained = BeautyGeometry(baseWidth = 200, baseHeight = 150)
        val partial = BeautyGeometry(faceResult = faceResult(200, 150))
        val merged = retained.mergedWith(partial)
        assertEquals(200, merged.baseWidth)
        assertEquals(150, merged.baseHeight)
        assertSame(partial.faceResult, merged.faceResult)
    }
}