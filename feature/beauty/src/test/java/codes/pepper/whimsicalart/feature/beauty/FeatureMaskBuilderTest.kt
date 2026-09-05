package codes.pepper.whimsicalart.feature.beauty

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import codes.pepper.whimsicalart.feature.beauty.domain.FeatureMaskBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeatureMaskBuilderTest {

    private fun square(cx: Float, cy: Float, half: Float): List<PointF> =
        listOf(
            PointF(cx - half, cy - half),
            PointF(cx + half, cy - half),
            PointF(cx + half, cy + half),
            PointF(cx - half, cy + half)
        )

    private fun bounds(path: android.graphics.Path): RectF {
        val out = RectF()
        path.computeBounds(out, true)
        return out
    }

    @Test
    fun `eyeMask follows the eye contour`() {
        val contour = square(100f, 100f, 20f)
        val path = FeatureMaskBuilder.eyeMask(contour, PointF(100f, 100f), 40f)
        assertNotNull(path)
        val b = bounds(path!!)
        assertTrue("mask hugs the contour", b.width() <= 44f && b.height() <= 44f)
    }

    @Test
    fun `eyeMask falls back to an almond at the anchor when the contour is too small`() {
        val path = FeatureMaskBuilder.eyeMask(emptyList(), PointF(150f, 150f), 40f)
        assertNotNull(path)
        val b = bounds(path!!)
        assertEquals(80f, b.width(), 1f)
        assertTrue("almond is narrower than tall-rounded", b.height() <= b.width())
    }

    @Test
    fun `eyeMask returns null without contour and without an anchor`() {
        assertNull(FeatureMaskBuilder.eyeMask(emptyList(), null, 40f))
    }

    @Test
    fun `lipMask builds a closed mask from the lips contour`() {
        val lips = square(200f, 300f, 30f)
        val path = FeatureMaskBuilder.lipMask(lips, null, null)
        assertNotNull(path)
        val b = bounds(path!!)
        assertTrue("mouth centre sits inside the mask", b.left <= 200f && b.right >= 200f)
    }

    @Test
    fun `lipMask falls back to a bow-topped lip path from the corners`() {
        val path = FeatureMaskBuilder.lipMask(
            emptyList(),
            PointF(170f, 280f),
            PointF(230f, 280f)
        )
        assertNotNull(path)
        val b = bounds(path!!)
        assertTrue("mask covers the mouth line", b.top <= 280f && b.bottom >= 280f)
        assertTrue("mask width ~ mouth width", b.width() >= 55f && b.width() <= 65f)
        assertTrue("lip shape wider than tall (cupid bow + drop lip, not a fat oval)", b.height() in 20f..30f)
    }

    @Test
    fun `lipMask fallback has a fuller bottom lip than top bow`() {
        val path = FeatureMaskBuilder.lipMask(emptyList(), PointF(200f, 400f), PointF(260f, 400f))
        assertNotNull(path)
        val b = bounds(path!!)
        // rise (0.16 * width) is smaller than the drop (0.26 * width), so the
        // bottom lip extends further below the mouth line than the bow does above.
        val above = 400f - b.top
        val below = b.bottom - 400f
        assertTrue("bottom lip fuller", below > above)
    }

    @Test
    fun `teethMask is a narrow band inside the mouth`() {
        val lips = listOf(
            PointF(170f, 270f), PointF(230f, 270f),
            PointF(230f, 290f), PointF(170f, 290f)
        )
        val path = FeatureMaskBuilder.teethMask(
            lips,
            PointF(170f, 280f),
            PointF(230f, 280f)
        )
        assertNotNull(path)
        val b = bounds(path!!)
        assertTrue("band narrower than the mouth", b.height() < 30f)
        assertTrue("band inside the lip bounds", b.top >= 260f && b.bottom <= 300f)
    }

    @Test
    fun `teethMask works from corners alone when the contour is missing`() {
        val path = FeatureMaskBuilder.teethMask(
            emptyList(),
            PointF(170f, 280f),
            PointF(230f, 280f)
        )
        assertNotNull(path)
        val b = bounds(path!!)
        assertTrue("still a mouth-sized band", b.width() in 20f..60f)
    }

    @Test
    fun `teethMask is the mouth aperture anchored to the corners, not a free ellipse`() {
        val lips = listOf(
            PointF(170f, 270f), PointF(200f, 268f), PointF(230f, 270f),
            PointF(230f, 290f), PointF(200f, 292f), PointF(170f, 290f)
        )
        val left = PointF(170f, 280f)
        val right = PointF(230f, 280f)
        val path = FeatureMaskBuilder.teethMask(lips, left, right)
        assertNotNull(path)
        val b = bounds(path!!)
        // Corners on the mouth line; lens bounded by the lips bands.
        assertEquals(170f, b.left, 1f)
        assertEquals(230f, b.right, 1f)
        assertTrue("vertical extent inside the lip interior", b.top >= 268f && b.bottom <= 292f)
        assertTrue("mouth centre sits inside the aperture", b.top <= 280f && b.bottom >= 280f)
    }

    @Test
    fun `silkworm path is a flat lens under the eye anchor`() {
        val path = FeatureMaskBuilder.silkwormPath(cx = 200f, cy = 200f, width = 100f, height = 50f)
        val b = bounds(path)
        assertEquals(100f, b.width(), 1f)
        assertTrue("starts below the lash line", b.top in 202f..212f)
        assertTrue("dips below the anchor", b.bottom in 246f..254f)
        assertTrue("wider than tall (crescent, not circle)", b.height() < b.width())
    }

    @Test
    fun `silkworm path follows the eye anchor`() {
        val left = bounds(FeatureMaskBuilder.silkwormPath(180f, 200f, 100f, 50f))
        val right = bounds(FeatureMaskBuilder.silkwormPath(240f, 205f, 100f, 50f))
        assertEquals(180f, left.centerX(), 1f)
        assertEquals(240f, right.centerX(), 1f)
        assertTrue("right-eye mask sits lower with its anchor", right.centerY() > left.centerY())
    }

    @Test
    fun `verticalBand follows the face contour as a trapezoid`() {
        val faceContour = listOf(
            PointF(120f, 100f), PointF(115f, 130f), PointF(112f, 160f), PointF(118f, 190f),
            PointF(280f, 110f), PointF(285f, 145f), PointF(282f, 180f)
        )
        // Band between y=120 and y=180: left side crosses around x=112-115, right
        // side around x=282-285, so the band hugs the face sides instead of the
        // fallback rect (x 140..260).
        val fallback = RectF(140f, 120f, 260f, 180f)
        val path = FeatureMaskBuilder.verticalBand(faceContour, 120f, 180f, fallback)
        val b = bounds(path)
        assertTrue("reaches the contour sides", b.left < 125f && b.right > 275f)
        assertTrue("band height follows the requested range", b.top in 120f..140f && b.bottom in 170f..190f)
    }

    @Test
    fun `verticalBand falls back to the rounded fallback rect without a contour`() {
        val fallback = RectF(100f, 120f, 300f, 190f)
        val path = FeatureMaskBuilder.verticalBand(emptyList(), 120f, 190f, fallback)
        val b = bounds(path)
        assertEquals(200f, b.width(), 1f)
        assertEquals(70f, b.height(), 1f)
    }

    @Test
    fun `cheekMask prefers a face-contour leaf over the ellipse`() {
        val faceContour = listOf(
            PointF(100f, 252f), PointF(96f, 258f), PointF(92f, 262f), PointF(97f, 268f),
            PointF(140f, 252f), PointF(146f, 260f), PointF(140f, 270f)
        )
        val cheek = PointF(120f, 260f)
        val path = FeatureMaskBuilder.cheekMask(
            faceContour, cheek, isLeft = true, boundsHeight = 160f, fallbackRadius = 60f
        )
        assertNotNull(path)
        val b = bounds(path!!)
        assertTrue("leaf reaches the face side", b.left < 100f)
        assertTrue("cheek sits inside the leaf", b.top <= 260f && b.bottom >= 260f)
        assertTrue("leaf tapers toward the face edge", b.top >= 252f && b.bottom <= 268f)
    }

    @Test
    fun `cheekMask falls back to the ellipse when the contour is too sparse`() {
        val path = FeatureMaskBuilder.cheekMask(
            emptyList(), PointF(160f, 260f), isLeft = true, boundsHeight = 160f, fallbackRadius = 60f
        )
        assertNotNull(path)
        val b = bounds(path!!)
        assertEquals(120f, b.width(), 2f)
        assertTrue("ellipse is flatter than wide", b.height() < b.width())
    }

    @Test
    fun `cheekMask returns null without a cheek anchor`() {
        assertNull(FeatureMaskBuilder.cheekMask(emptyList(), null, isLeft = true, boundsHeight = 160f, fallbackRadius = 60f))
    }

    @Test
    fun `cheekEdgeRadius reaches the face side on the same side of the cheek`() {
        val faceContour = listOf(
            PointF(90f, 240f), PointF(150f, 300f), PointF(250f, 300f),
            PointF(310f, 240f), PointF(250f, 210f), PointF(150f, 210f)
        )
        // Left cheek at x=130: nearest face-side point on the left is (90, 240).
        val radius = FeatureMaskBuilder.cheekEdgeRadius(
            faceContour, PointF(130f, 246f), isLeft = true, boundsHeight = 160f,
            fallbackRadius = 60f
        )
        assertTrue("radius reaches toward the face edge", radius in 35f..50f)
    }

    @Test
    fun `cheekEdgeRadius falls back when there is no face contour`() {
        val radius = FeatureMaskBuilder.cheekEdgeRadius(
            emptyList(), PointF(200f, 200f), isLeft = true, boundsHeight = 160f,
            fallbackRadius = 45f
        )
        assertEquals(45f, radius, 0f)
    }

    @Test
    fun `faceMask follows the face contour when present`() {
        val contour = square(200f, 200f, 60f)
        val path = FeatureMaskBuilder.faceMask(contour, RectF(150f, 150f, 250f, 250f))
        assertNotNull(path)
        val b = bounds(path!!)
        assertTrue("hugs the contour", b.width() <= 125f && b.height() <= 125f)
    }

    @Test
    fun `faceMask falls back to the oval without a contour`() {
        val path = FeatureMaskBuilder.faceMask(emptyList(), RectF(150f, 150f, 250f, 250f))
        assertNotNull(path)
        val b = bounds(path!!)
        assertEquals(100f, b.width(), 1f)
        assertEquals(100f, b.height(), 1f)
    }

    @Test
    fun `hairMask spans the face width above the brow line`() {
        val path = FeatureMaskBuilder.hairMask(Rect(100, 200, 300, 400))
        val b = bounds(path)
        assertEquals(200f, b.width(), 1f)
        assertTrue("crown sits above the face", b.top < 200f)
        assertTrue("dome bottom stays inside the face top", b.bottom in 200f..450f)
        assertTrue("no horizontal overhang", b.left >= 99f && b.right <= 301f)
    }

    @Test
    fun `hairMask scales with the face size`() {
        val small = bounds(FeatureMaskBuilder.hairMask(Rect(100, 100, 200, 200)))
        val large = bounds(FeatureMaskBuilder.hairMask(Rect(100, 100, 300, 300)))
        assertTrue("wider face -> wider dome", large.width() > small.width())
        assertTrue("taller face -> taller crown", large.top < small.top)
    }
}