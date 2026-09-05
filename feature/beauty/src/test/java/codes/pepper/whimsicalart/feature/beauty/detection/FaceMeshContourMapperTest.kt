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
                168 to PointF(50f, 40f),
                336 to PointF(70f, 5f),
                70 to PointF(30f, 5f)
            )
        )
        val out = FaceMeshContourMapper.toContours(points)
        assertEquals("left eye anchor from subject-left eye group", PointF(60f, 10f), out.leftEye)
        assertEquals("right eye anchor from subject-right eye group", PointF(20f, 10f), out.rightEye)
        assertEquals(PointF(50f, 40f), out.nose)
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
    fun `enrich keeps kit cheeks and anchors, replaces dense contours`() {
        val kit = FaceLandmarks(
            leftEye = PointF(5f, 5f),
            rightEye = PointF(10f, 5f),
            nose = PointF(8f, 8f),
            mouthLeft = PointF(6f, 10f),
            mouthRight = PointF(9f, 10f),
            leftCheek = PointF(2f, 8f),
            rightCheek = PointF(13f, 8f),
            faceContour = listOf(PointF(0f, 3f), PointF(15f, 9f)),
            leftEyeContour = listOf(PointF(4f, 5f)),
            rightEyeContour = listOf(PointF(11f, 5f)),
            leftEyebrowContour = listOf(PointF(4f, 3f)),
            rightEyebrowContour = listOf(PointF(11f, 3f)),
            lipsContour = listOf(PointF(6f, 10f), PointF(9f, 10f))
        )
        val meshPoints = mesh(
            mapOf(
                362 to PointF(60f, 10f),
                33 to PointF(20f, 10f),
                168 to PointF(50f, 40f),
                61 to PointF(10f, 50f),
                291 to PointF(90f, 50f)
            )
        )
        val merged = FaceMeshContourMapper.enrich(kit, meshPoints)
        // Cheeks survive from Kit (blush must keep working).
        assertEquals(kit.leftCheek, merged.leftCheek)
        assertEquals(kit.rightCheek, merged.rightCheek)
        // Dense contours and anchors come from the mesh (full mesh has all points).
        assertEquals(PointF(60f, 10f), merged.leftEye)
        assertEquals(PointF(20f, 10f), merged.rightEye)
        assertEquals(PointF(50f, 40f), merged.nose)
        assertEquals(PointF(10f, 50f), merged.mouthLeft)
        assertEquals(PointF(90f, 50f), merged.mouthRight)
        assertTrue(merged.leftEyeContour.isNotEmpty())
        assertTrue(merged.rightEyeContour.isNotEmpty())
    }

    @Test
    fun `enrich returns kit unchanged when mesh is too short`() {
        val kit = FaceLandmarks(
            leftEye = PointF(1f, 1f), rightEye = PointF(2f, 1f),
            nose = PointF(1.5f, 2f), mouthLeft = PointF(1f, 3f),
            mouthRight = PointF(2f, 3f), leftCheek = PointF(0f, 2f),
            rightCheek = PointF(3f, 2f), lipsContour = listOf(PointF(1f, 3f), PointF(2f, 3f))
        )
        val sparse = listOf(PointF(5f, 5f), PointF(6f, 6f))
        assertEquals(kit, FaceMeshContourMapper.enrich(kit, sparse))
    }

    @Test
    fun `nearestMesh picks the mesh face closest to the kit centre`() {
        val near = FaceMeshFace(
            points = emptyList(),
            bounds = Rect(40, 40, 60, 60)
        )
        val far = FaceMeshFace(
            points = emptyList(),
            bounds = Rect(200, 200, 220, 220)
        )
        val kitCenter = PointF(50f, 50f)
        assertEquals(near, FaceMeshContourMapper.nearestMesh(kitCenter, listOf(far, near)))
    }

    @Test
    fun `nearestMesh returns null for no mesh faces`() {
        assertNull(FaceMeshContourMapper.nearestMesh(PointF(1f, 1f), emptyList()))
    }
}