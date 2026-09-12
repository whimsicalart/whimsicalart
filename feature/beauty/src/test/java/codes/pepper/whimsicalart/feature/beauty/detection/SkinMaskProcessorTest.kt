package codes.pepper.whimsicalart.feature.beauty.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkinMaskProcessorTest {

    private fun emptyMask(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    @Test
    fun `toPath returns null when there is no skin`() {
        val mask = emptyMask(32, 32)
        assertNull(SkinMaskProcessor.toPath(mask))
    }

    @Test
    fun `toPath returns null when the mask is too sparse`() {
        val mask = emptyMask(16, 16)
        mask.setPixel(2, 2, Color.argb(255, 255, 255, 255))
        assertNull(SkinMaskProcessor.toPath(mask))
    }

    @Test
    fun `toPath outlines a rectangular skin region`() {
        val mask = emptyMask(40, 40)
        for (y in 8..31) {
            for (x in 12..27) {
                mask.setPixel(x, y, Color.argb(255, 255, 255, 255))
            }
        }
        val path = SkinMaskProcessor.toPath(mask)
        assertNotNull(path)
        val bounds = RectF()
        path?.computeBounds(bounds, true)
        assertTrue("left hugs the blob (12)", bounds.left in 10f..14f)
        assertTrue("right hugs the blob (27)", bounds.right in 25f..29f)
        assertTrue("top hugs the blob (8)", bounds.top in 6f..10f)
        assertTrue("bottom hugs the blob (31)", bounds.bottom in 29f..33f)
    }

    @Test
    fun `toPath ignores low-alpha pixels below the threshold`() {
        val mask = emptyMask(20, 20)
        for (y in 2..11) {
            for (x in 5..14) {
                mask.setPixel(x, y, Color.argb(200, 255, 255, 255))
            }
        }
        mask.setPixel(10, 18, Color.argb(60, 255, 255, 255))
        val path = SkinMaskProcessor.toPath(mask, threshold = 128)
        assertNotNull(path)
        val bounds = RectF()
        path?.computeBounds(bounds, true)
        assertTrue("sampled rows give a solid outline", bounds.height() >= 8f)
    }

    @Test
    fun `toPath skips rows where the skin is broken so a gap is bridged`() {
        val mask = emptyMask(30, 24)
        // A skin band interrupted mid-way (e.g. a mouth/nose gap): the sweep
        // still captures the outer left/right extents per row.
        for (y in 4..19) {
            mask.setPixel(6, y, Color.argb(255, 255, 255, 255))
            mask.setPixel(23, y, Color.argb(255, 255, 255, 255))
        }
        val path = SkinMaskProcessor.toPath(mask)
        assertNotNull(path)
        val bounds = RectF()
        path?.computeBounds(bounds, true)
        assertTrue("crosses the broken band", bounds.width() >= 14f && bounds.height() >= 14f)
    }

    /** The pre-batching reference: per-pixel getPixel sweep, identical math. */
    private fun toPathScalar(mask: Bitmap, threshold: Int, minRows: Int = 4, stride: Int = 1): Path? {
        val width = mask.width
        val height = mask.height
        val leftEdge = ArrayList<android.graphics.PointF>(height / stride)
        val rightEdge = ArrayList<android.graphics.PointF>(height / stride)
        for (y in 0 until height step stride) {
            var left = -1
            var right = -1
            for (x in 0 until width) {
                if (Color.alpha(mask.getPixel(x, y)) >= threshold) {
                    if (left < 0) left = x
                    right = x
                }
            }
            if (left >= 0 && right >= left) {
                leftEdge.add(android.graphics.PointF(left.toFloat(), y.toFloat()))
                rightEdge.add(android.graphics.PointF(right.toFloat(), y.toFloat()))
            }
        }
        if (leftEdge.size < minRows) return null
        val path = Path()
        path.moveTo(leftEdge[0].x, leftEdge[0].y)
        for (i in 1 until leftEdge.size) path.lineTo(leftEdge[i].x, leftEdge[i].y)
        for (i in rightEdge.indices.reversed()) path.lineTo(rightEdge[i].x, rightEdge[i].y)
        path.close()
        return path
    }

    private fun boundsOf(path: Path?): RectF =
        RectF().also { path?.computeBounds(it, true) }

    @Test
    fun `batched toPath matches the scalar reference on threshold-edge and transparent-heavy masks`() {
        // (a) Threshold-edge: alphas straddle the 128 line so every boundary
        // decision is exercised; (b) transparent-heavy: sparse opaque pixels on
        // a fully transparent field; (c) a mid-field blob that clips every axis.
        val thresholdEdge = emptyMask(40, 40).also { m ->
            for (y in 5 until 35 step 2) {
                for (x in 8 until 32) {
                    m.setPixel(x, y, Color.argb(if (x < 20) 128 else 127, 255, 255, 255))
                }
            }
            m.setPixel(31, 39, Color.argb(200, 255, 255, 255))
        }
        val transparentHeavy = emptyMask(40, 40).also { m ->
            m.setPixel(5, 5, Color.argb(255, 255, 255, 255))
            m.setPixel(5, 6, Color.argb(129, 255, 255, 255))
            m.setPixel(35, 34, Color.argb(128, 255, 255, 255))
            m.setPixel(35, 35, Color.argb(127, 255, 255, 255))
        }
        val clippedBlob = emptyMask(24, 24).also { m ->
            for (y in 0 until 6) for (x in 18 until 24) {
                m.setPixel(x, y, Color.argb(255, 255, 255, 255))
            }
        }
        for (mask in listOf(thresholdEdge, transparentHeavy, clippedBlob)) {
            val batched = boundsOf(SkinMaskProcessor.toPath(mask, threshold = 128))
            val scalar = boundsOf(toPathScalar(mask, threshold = 128))
            assertEquals("left", scalar.left, batched.left, 0.01f)
            assertEquals("right", scalar.right, batched.right, 0.01f)
            assertEquals("top", scalar.top, batched.top, 0.01f)
            assertEquals("bottom", scalar.bottom, batched.bottom, 0.01f)
        }
    }
}