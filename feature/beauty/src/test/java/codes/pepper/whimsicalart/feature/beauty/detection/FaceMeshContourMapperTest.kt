package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.PointF
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaceMeshContourMapperTest {

    /** Builds a 478-point list; every point defaults to [0,0] except those at [indices]. */
    private fun mesh(indices: Map<Int, PointF>): List<PointF> {
        val points = List(FaceMeshContourMapper.LANDMARK_COUNT) { PointF(0f, 0f) }.toMutableList()
        indices.forEach { (i, p) -> points[i] = p }
        return points
    }

    @Test
    fun `toImagePoints scales normalized coords to pixel space`() {
        val normalized = listOf(
            PointF(0.25f, 0.5f),
            PointF(1f, 0f),
            PointF(0f, 1f)
        )
        val pixels = FaceMeshContourMapper.toImagePoints(normalized, 200, 100)
        assertEquals(PointF(50f, 50f), pixels[0])
        assertEquals(PointF(200f, 0f), pixels[1])
        assertEquals(PointF(0f, 100f), pixels[2])
    }

    @Test
    fun `boundsOf spans the point cloud extremes`() {
        val points = listOf(
            PointF(10.4f, 20.6f),
            PointF(99.2f, 80.1f),
            PointF(3f, 100f)
        )
        val bounds = FaceMeshContourMapper.boundsOf(points)
        assertEquals(Rect(3, 20, 100, 100), bounds)
    }

    @Test
    fun `toContours groups subject-relative eyes to image-space fields`() {
        // FaceMesh "left eye" (362..) is the subject's LEFT eye (image right).
        val points = mesh(
            mapOf(
                362 to PointF(60f, 10f),
                33 to PointF(20f, 10f),
                61 to PointF(10f, 50f),
                291 to PointF(90f, 50f),
                // Nose TIP (1), not the inter-eye bridge (168): the reshape
                // anchor must sit on the nose body, below the eye line.
                1 to PointF(50f, 80f),
                336 to PointF(70f, 5f),
                70 to PointF(30f, 5f)
            )
        )
        val out = FaceMeshContourMapper.toContours(points)
        assertEquals("left eye anchor from subject-left eye group", PointF(60f, 10f), out.leftEye)
        assertEquals("right eye anchor from subject-right eye group", PointF(20f, 10f), out.rightEye)
        assertEquals("nose anchor at the tip, below the eye line", PointF(50f, 80f), out.nose)
        assertEquals("image-left lip corner", PointF(10f, 50f), out.mouthLeft)
        assertEquals("image-right lip corner", PointF(90f, 50f), out.mouthRight)
        assertEquals(PointF(70f, 5f), out.leftEyebrowContour.first())
        assertEquals(PointF(30f, 5f), out.rightEyebrowContour.first())
        assertTrue(out.leftEyeContour.isNotEmpty())
        assertTrue(out.rightEyeContour.isNotEmpty())
        assertTrue(out.lipsContour.isNotEmpty())
        assertTrue(out.faceContour.isNotEmpty())
    }

    @Test
    fun `toContours leaves cheeks null - face mesh has no cheek landmarks`() {
        val out = FaceMeshContourMapper.toContours(mesh(emptyMap()))
        assertNull(out.leftCheek)
        assertNull(out.rightCheek)
    }

    @Test
    fun `toContours maps the inner-lip ring into the mouth aperture contour`() {
        // The inner-lip ring: lipsUpperInner 78..308 + lipsLowerInner 324..95.
        // A couple of representative indices must land inside the aperture ring,
        // and the ring must be returned as an ordered loop (not merged into the
        // outer lips contour).
        val points = mesh(
            mapOf(
                78 to PointF(30f, 40f),
                13 to PointF(50f, 30f),
                308 to PointF(70f, 40f),
                95 to PointF(40f, 60f),
                17 to PointF(50f, 55f),
                0 to PointF(50f, 20f)
            )
        )
        val out = FaceMeshContourMapper.toContours(points)
        assertTrue("inner ring populated", out.mouthApertureContour.isNotEmpty())
        assertEquals("aperture upper point 78 maps through", PointF(30f, 40f), out.mouthApertureContour[0])
        assertEquals("lower inner ring (95) follows the upper ring", PointF(40f, 60f), out.mouthApertureContour[19])
        assertEquals("aperture ring = 11 upper + 9 lower inner points", 20, out.mouthApertureContour.size)
    }

    @Test
    fun `toContours leaves the aperture contour empty for a short mesh without inner-lip points`() {
        // A partial/short mesh (e.g. an older ML Kit contour layout) lacks the
        // 478-face indices, so the inner-lip ring stays empty and callers keep
        // their legacy lens fallback.
        assertTrue(FaceMeshContourMapper.toContours(listOf(PointF(5f, 5f), PointF(9f, 9f))).mouthApertureContour.isEmpty())
    }

    @Test
    fun `toFaceDetectionResult derives bounds, probabilities and pose from the mesh`() {
        val points = mesh(
            mapOf(
                33 to PointF(10f, 50f),
                263 to PointF(50f, 50f),
                1 to PointF(52.5f, 90f)
            )
        )
        val face = FaceMeshFace(
            points = points,
            bounds = Rect(5, 5, 100, 120),
            blendshapes = mapOf(
                "eyeblinkright" to 0.8f,
                "mouthsmileleft" to 0.2f,
                "mouthsmileright" to 0.9f
            )
        )
        val result = FaceMeshContourMapper.toFaceDetectionResult(listOf(face), 200, 300)
        assertEquals(1, result.faces.size)
        assertEquals(Rect(5, 5, 100, 120), result.faces[0].bounds)
        assertEquals("right eye open from the eyebrow blink", 0.2f, result.faces[0].rightEyeOpenProbability, 1e-4f)
        assertEquals("smile from the stronger corner", 0.9f, result.faces[0].smilingProbability, 1e-4f)
        assertEquals("roll from the horizontal eye line", 0f, result.faces[0].eulerZ, 1e-4f)
        assertEquals("yaw from the centred nose", 0f, result.faces[0].eulerY, 1e-4f)
        assertTrue(result.faces[0].landmarks.leftEyeContour.isNotEmpty())
        assertEquals(200, result.imageWidth)
        assertEquals(300, result.imageHeight)
    }

    @Test
    fun `toFaceDetectionResult reports empty faces for no meshes and the image size`() {
        val result = FaceMeshContourMapper.toFaceDetectionResult(emptyList(), 640, 480)
        assertTrue(result.faces.isEmpty())
        assertEquals(640, result.imageWidth)
        assertEquals(480, result.imageHeight)
    }

    @Test
    fun `roll and yaw saturate from the eye-line slope and the nose offset`() {
        val eyes = mesh(
            mapOf(
                33 to PointF(20f, 40f),
                263 to PointF(50f, 55f)
            )
        )
        val noseRight = mesh(mapOf(1 to PointF(200f, 80f)))
        val noseLeft = mesh(mapOf(1 to PointF(0f, 80f)))
        val roll = FaceMeshContourMapper.toFaceDetectionResult(
            listOf(FaceMeshFace(eyes, Rect(5, 5, 100, 120))), 200, 200
        ).faces[0].eulerZ
        val yawRight = FaceMeshContourMapper.toFaceDetectionResult(
            listOf(FaceMeshFace(noseRight, Rect(50, 5, 150, 200))), 200, 200
        ).faces[0].eulerY
        val yawLeft = FaceMeshContourMapper.toFaceDetectionResult(
            listOf(FaceMeshFace(noseLeft, Rect(50, 5, 150, 200))), 200, 200
        ).faces[0].eulerY
        assertTrue("eye line slopes down rightwards", roll > 0f)
        assertTrue("nose beyond the right edge saturates the yaw", yawRight > 0f)
        assertTrue("nose beyond the left edge saturates the yaw", yawLeft < 0f)
        assertEquals(45f, yawRight, 1e-4f)
        assertEquals(-45f, yawLeft, 1e-4f)
    }

    @Test
    fun `missing blendshapes and short meshes degrade the derived fields safely`() {
        val sparse = FaceMeshFace(listOf(PointF(1f, 2f)), Rect(0, 0, 5, 5))
        val result = FaceMeshContourMapper.toFaceDetectionResult(listOf(sparse), 100, 100)
        val face = result.faces[0]
        assertEquals("absent blink blendshape means fully open", 1f, face.leftEyeOpenProbability, 1e-4f)
        assertEquals("absent smile blendshape means not smiling", 0f, face.smilingProbability, 1e-4f)
        assertEquals("no eye corners -> level", 0f, face.eulerZ, 1e-4f)
        assertEquals("no nose -> straight ahead", 0f, face.eulerY, 1e-4f)
        assertTrue(face.landmarks.faceContour.isEmpty())
    }
}