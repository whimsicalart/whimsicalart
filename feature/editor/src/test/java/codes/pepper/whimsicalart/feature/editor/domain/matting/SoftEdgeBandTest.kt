package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SoftEdgeBandTest {

    /** 16×16 mask whose left half is subject (white) and right half background. */
    private fun halfMask(): Bitmap {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val px = IntArray(16 * 16)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                px[y * 16 + x] = if (x < 8) Color.WHITE else Color.TRANSPARENT
            }
        }
        bmp.setPixels(px, 0, 16, 0, 0, 16, 16)
        return bmp
    }

    @Test
    fun `repeated depthMap on the same mask returns the cached instance`() {
        val mask = halfMask()

        val first = SoftEdgeBand.depthMap(mask, 110, SoftEdgeBand.DEFAULT_WIDTH)
        val second = SoftEdgeBand.depthMap(mask, 110, SoftEdgeBand.DEFAULT_WIDTH)

        assertSame("same mask + params must hit the cache", first, second)
    }

    @Test
    fun `depthMap recomputes when the mask identity changes`() {
        val mask = halfMask()
        val other = halfMask()

        val first = SoftEdgeBand.depthMap(mask, 110, SoftEdgeBand.DEFAULT_WIDTH)
        val second = SoftEdgeBand.depthMap(other, 110, SoftEdgeBand.DEFAULT_WIDTH)

        assertNotSame("a different mask reference must compute a fresh depth map", first, second)
        // Both are 16×16 half-masks, so the CONTENT must be identical.
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun `depthMap recomputes when the bandWidth changes`() {
        val mask = halfMask()

        val first = SoftEdgeBand.depthMap(mask, 110, 2)
        val second = SoftEdgeBand.depthMap(mask, 110, 4)

        assertNotSame("a different bandWidth must compute a fresh depth map", first, second)
    }

    @Test
    fun `depthMap boundary distance is measured inward from the mask path`() {
        val mask = halfMask()
        val depth = SoftEdgeBand.depthMap(mask, 110, SoftEdgeBand.DEFAULT_WIDTH)

        // Column 8 is outside the mask (alpha below the threshold): depth 0.
        assertEquals(0, depth[8])
        // The mask path itself (column 7, whose right neighbour is background)
        // reads 0 too — the threshold switch and the band ramp share the index.
        assertEquals(0, depth[7])
        // Moving inward, one depth level per column: 6 → 1, 5 → 2, 4 → 3.
        assertEquals(1, depth[6])
        assertEquals(2, depth[5])
        assertEquals(3, depth[4])
        // The core clamps at the band width, exported as the band width itself.
        assertEquals(SoftEdgeBand.DEFAULT_WIDTH, depth[3])
        assertEquals(SoftEdgeBand.DEFAULT_WIDTH, depth[0])
    }
}