package codes.pepper.whimsicalart.feature.editor.domain.removal

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiffusionInpainterTest {

    private fun solidBitmap(color: Int): Bitmap {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return b
    }

    private fun mask(x: Int, y: Int, size: Int = 6): Bitmap {
        val m = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        m.eraseColor(Color.TRANSPARENT)
        for (yy in y until y + size) {
            for (xx in x until x + size) {
                m.setPixel(xx, yy, Color.argb(255, 255, 255, 255))
            }
        }
        return m
    }

    private fun maskArray(w: Int, h: Int, x: Int, y: Int, size: Int): IntArray {
        val m = IntArray(w * h)
        for (yy in y until y + size) {
            var row = yy * w
            for (xx in x until x + size) {
                m[row + xx] = Color.WHITE
            }
        }
        return m
    }

    @Test
    fun `removed region is replaced with surrounding colour`() {
        val background = Color.rgb(200, 100, 50)
        // A uniform background with a black "object" in the centre to remove.
        val scarred = solidBitmap(background)
        for (yy in 13..18) {
            for (xx in 13..18) {
                scarred.setPixel(xx, yy, Color.rgb(0, 0, 0))
            }
        }
        val out = DiffusionInpainter.inpaint(scarred, mask(13, 13, 6))

        val centre = out!!.getPixel(16, 16)
        assertNotEquals("centre is no longer the black object", 0, centre)
        // On a uniform field every known neighbour is (200, 100, 50), so the
        // fill converges exactly on the background colour.
        assertEquals(background, centre)
    }

    @Test
    fun `empty mask leaves the image effectively unchanged`() {
        val source = solidBitmap(Color.rgb(10, 20, 30))
        val emptyMask = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        emptyMask.eraseColor(Color.TRANSPARENT)
        val out = DiffusionInpainter.inpaint(source, emptyMask)
        // No mask pixels -> no fill, so the bitmap is a copy of the source.
        assertEquals(Color.rgb(10, 20, 30), out!!.getPixel(5, 5))
        assertNotEquals("inpaint returns a fresh bitmap", source, out)
    }

    @Test
    fun `non 8888 source is coerced to ARGB 8888`() {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.RGB_565)
        source.eraseColor(Color.rgb(120, 120, 120))
        val out = DiffusionInpainter.inpaint(source, mask(4, 4))
        assertEquals(Bitmap.Config.ARGB_8888, out!!.config)
        assertNotEquals("content remains after fill", 0, out!!.getPixel(8, 8))
        source.recycle()
    }

    @Test
    fun `region bounded fill is pixel-identical to the whole-image inpaint`() {
        val background = Color.rgb(200, 100, 50)
        val scarred = solidBitmap(background)
        for (yy in 13..18) {
            for (xx in 13..18) {
                scarred.setPixel(xx, yy, Color.rgb(0, 0, 0))
            }
        }
        val m = mask(13, 13, 6)
        // Reference: the whole-image inpaint (its scan already defaults to the
        // mask's own bounding box).
        val whole = DiffusionInpainter.inpaint(scarred, m)!!
        val w = scarred.width
        val h = scarred.height
        val pixels = IntArray(w * h)
        scarred.getPixels(pixels, 0, w, 0, 0, w, h)
        // Region fill restricted to the hole's rectangle must match, pixel by pixel.
        DiffusionInpainter.fillRegion(pixels, maskArray(w, h, 13, 13, 6), w, h, 13, 13, 19, 19)
        val region = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                assertEquals(
                    "region-bound fill diverged from whole inpaint at ($x,$y)",
                    whole.getPixel(x, y),
                    region.getPixel(x, y)
                )
            }
        }
    }

    @Test
    fun `two disjoint regions filled incrementally equal a single union inpaint`() {
        val background = Color.rgb(90, 160, 240)
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.eraseColor(background)
        // Two isolated black "objects" to remove.
        val objA = 6..11
        val objB = 44..49
        for (yy in objA) for (xx in objA) source.setPixel(xx, yy, Color.rgb(0, 0, 0))
        for (yy in objB) for (xx in objB) source.setPixel(xx, yy, Color.rgb(0, 0, 0))

        val union = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        union.eraseColor(Color.TRANSPARENT)
        for (yy in objA) for (xx in objA) union.setPixel(xx, yy, Color.WHITE)
        for (yy in objB) for (xx in objB) union.setPixel(xx, yy, Color.WHITE)

        val expected = DiffusionInpainter.inpaint(source, union)!!
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val unionMask = maskArray(w, h, objA.first, objA.first, objA.last - objA.first + 1)
        for (yy in objB) {
            var row = yy * w
            for (xx in objB) {
                unionMask[row + xx] = Color.WHITE
            }
        }

        // First commit resolves A, then the second commit resolves B against the
        // canvas that already has A baked in — the old union re-inpaint collapsed.
        DiffusionInpainter.fillRegion(pixels, unionMask, w, h, objA.first, objA.first, objA.last + 1, objA.last + 1)
        val afterFirst = pixels.copyOf()
        val afterSecond = DiffusionInpainter.fillRegion(
            pixels, unionMask, w, h,
            objB.first, objB.first, objB.last + 1, objB.last + 1
        )

        // The whole canvas matches the one-pass union inpaint...
        for (y in 0 until h) {
            for (x in 0 until w) {
                assertEquals(
                    "incremental 2-region fill diverged from union at ($x,$y)",
                    expected.getPixel(x, y),
                    pixels[y * w + x]
                )
            }
        }
        // ...and B's resolution never touched a pixel outside its own footprint.
        for (y in 0 until h) {
            for (x in 0 until w) {
                val inB = x in objB && y in objB
                if (!inB) {
                    assertEquals("second region touched a pixel outside itself", afterFirst[y * w + x], pixels[y * w + x])
                }
            }
        }
        // A legitimately fills: B's fill wrote exactly B's hole area.
        with(afterSecond) {
            assertTrue("second stroke filled something", filledPixels > 0)
            assertTrue("second stroke fill is bounded by its region", filledPixels <= (objB.last - objB.first + 1) * (objB.last - objB.first + 1))
        }
    }

    @Test
    fun `second region scan is bounded by its own footprint, not the canvas`() {
        val w = 64
        val h = 64
        val source = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(10, 60, 120))
        // A WORST-CASE large first mask: a 60x30 band covering most of the canvas.
        val bigMask = maskArray(w, h, 2, 2, 60)
        for (yy in 2 until 32) for (xx in 2 until 62) bigMask[yy * w + xx] = Color.WHITE
        val smallMask = maskArray(w, h, 50, 50, 8)

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        DiffusionInpainter.fillRegion(pixels, bigMask, w, h, 2, 2, 62, 32)
        val afterBig = pixels.copyOf()
        val scan = DiffusionInpainter.fillRegion(pixels, smallMask, w, h, 50, 50, 58, 58)

        // The second stroke re-visits only its own 8x8 region (its few onion-peel
        // layers), nowhere near the whole 64x64 canvas, and never the big mask.
        assertTrue(
            "second stroke scanned ${scan.scannedPixels} pixels — the whole canvas?",
            scan.scannedPixels < w * h
        )
        assertEquals("region fill writes exactly the hole area", 64, scan.filledPixels)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!(x in 50 until 58 && y in 50 until 58)) {
                    assertEquals("big mask region must stay untouched", afterBig[y * w + x], pixels[y * w + x])
                }
            }
        }
    }
}