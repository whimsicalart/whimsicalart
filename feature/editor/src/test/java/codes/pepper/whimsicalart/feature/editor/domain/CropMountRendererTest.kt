package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropMountRendererTest {

    /** Colour encodes its coordinate (red = x, green = y) so flips/rotations are unambiguous. */
    private fun coordinateBitmap(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bmp.setPixel(x, y, Color.rgb(x * 40, y * 60, 0))
            }
        }
        return bmp
    }

    private fun samePixels(expected: Bitmap, actual: Bitmap): Boolean {
        if (expected.width != actual.width || expected.height != actual.height) return false
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                if (expected.getPixel(x, y) != actual.getPixel(x, y)) return false
            }
        }
        return true
    }

    /** Unambiguous reference for a horizontal flip, applied by hand (pixel loop). */
    private fun flipHorizontal(input: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until input.height) {
            for (x in 0 until input.width) {
                out.setPixel(x, y, input.getPixel(input.width - 1 - x, y))
            }
        }
        return out
    }

    /** Unambiguous reference for a vertical flip, applied by hand (pixel loop). */
    private fun flipVertical(input: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until input.height) {
            for (x in 0 until input.width) {
                out.setPixel(x, y, input.getPixel(x, input.height - 1 - y))
            }
        }
        return out
    }

    @Test
    fun `flip-h then 90-degree rotation matches the preview ordering flip-first`() {
        val input = coordinateBitmap(4, 2)

        // Preview ordering: flip first, then rotate (same 90° createBitmap
        // primitive `transforms` uses internally) — what a Flip-H + 90° must be.
        val rotateOnly = Matrix().apply { postRotate(90f) }
        val flipFirstThenRotate = Bitmap.createBitmap(
            flipHorizontal(input), 0, 0, input.width, input.height, rotateOnly, true
        )

        val actual = CropMountRenderer.transforms(
            input, rotationDegrees = 90f, flipHorizontal = true, flipVertical = false
        )

        assertTrue(
            "Flip-H + 90° must equal rotate-of-flipped, not flip-of-rotated",
            samePixels(flipFirstThenRotate, actual)
        )
    }

    @Test
    fun `flip-h followed by 90-degree rotation must not equal rotate-then-flip`() {
        val input = coordinateBitmap(4, 2)

        // The old (wrong) committed ordering: rotate the source first, then flip.
        val rotateFirstThenFlip = flipHorizontal(
            Bitmap.createBitmap(
                input, 0, 0, input.width, input.height,
                Matrix().apply { postRotate(90f) }, true
            )
        )

        val actual = CropMountRenderer.transforms(
            input, rotationDegrees = 90f, flipHorizontal = true, flipVertical = false
        )

        assertFalse(
            "flip-then-rotate must differ from the old rotate-then-flip ordering",
            samePixels(rotateFirstThenFlip, actual)
        )
    }

    @Test
    fun `flip-v then 270-degree rotation matches the preview ordering flip-first`() {
        val input = coordinateBitmap(3, 5)

        val rotateOnly = Matrix().apply { postRotate(270f) }
        val flipFirstThenRotate = Bitmap.createBitmap(
            flipVertical(input), 0, 0, input.width, input.height, rotateOnly, true
        )

        val actual = CropMountRenderer.transforms(
            input, rotationDegrees = 270f, flipHorizontal = false, flipVertical = true
        )

        assertTrue(
            "Flip-V + 270° must equal rotate-of-flipped",
            samePixels(flipFirstThenRotate, actual)
        )
    }

    @Test
    fun `free-angle flip keeps the rotated bounding-box extents`() {
        val input = coordinateBitmap(6, 4)

        val flipped = CropMountRenderer.transforms(
            input, rotationDegrees = 25f, flipHorizontal = true, flipVertical = false
        )
        val plain = CropMountRenderer.transforms(
            input, rotationDegrees = 25f, flipHorizontal = false, flipVertical = false
        )
        val expected = TransformEffect(rotation = 25f).estimateOutputSize(input.width, input.height)

        assertEquals("estimate vs flipped", expected.first, flipped.width)
        assertEquals("estimate vs flipped", expected.second, flipped.height)
        assertEquals("flip does not change extents", plain.width, flipped.width)
        assertEquals("flip does not change extents", plain.height, flipped.height)
    }
}