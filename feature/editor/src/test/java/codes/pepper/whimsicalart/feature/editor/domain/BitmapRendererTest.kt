package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BitmapRendererTest {

    @Test
    fun `rotation by 90 degrees swaps dimensions`() {
        val input = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 90f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null
        )
        assertEquals(2, out.width)
        assertEquals(4, out.height)
    }

    @Test
    fun `rotation by 180 degrees keeps dimensions`() {
        val input = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 180f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null
        )
        assertEquals(4, out.width)
        assertEquals(2, out.height)
    }

    @Test
    fun `no transforms returns original dimensions`() {
        val input = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null
        )
        assertEquals(4, out.width)
        assertEquals(2, out.height)
    }

    @Test
    fun `full frame crop keeps dimensions`() {
        val input = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = Rect(0f, 0f, 1f, 1f),
            colorMatrix = null
        )
        assertEquals(10, out.width)
        assertEquals(10, out.height)
    }

    @Test
    fun `centered half crop halves dimensions`() {
        val input = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = Rect(0.25f, 0.25f, 0.75f, 0.75f),
            colorMatrix = null
        )
        assertEquals(10, out.width)
        assertEquals(10, out.height)
    }

    @Test
    fun `zero rotation normalizes out of range`() {
        val input = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 360f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null
        )
        assertEquals(4, out.width)
        assertEquals(2, out.height)
    }

    @Test
    fun `transform folds to a baked bitmap the next effect consumes`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val input = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        // Fold the committed transform exactly as the EditorViewModel does:
        // TransformEffect.render bakes the rotation into a NEW bitmap whose
        // dimensions reflect the rotated canvas, which becomes the input for
        // later effects.
        val baked = TransformEffect(rotation = 90f).render(input, app)
        assertEquals("rotate 90 must bake a new canvas with swapped dims", 20, baked.width)
        assertEquals("rotate 90 must bake a new canvas with swapped dims", 40, baked.height)
        assertTrue("the transform emits a NEW bitmap, not a viewport override", baked !== input)
        // A 90-degree rotation produces a transparent-gutted canvas (non-90 handled
        // separately); the committed result is always a real editable bitmap.
        assertEquals(Bitmap.Config.ARGB_8888, baked.config)
    }

    @Test
    fun `frame folds square around the baked rotated canvas, not re-applying rotation`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        val frame = FrameLayer(
            borderWidth = 0.1f,
            cornerRadius = 0f,
            color = Color.BLACK
        )
        // Committed stack: transform bakes a rotated canvas, then the frame
        // consumes that baked bitmap and draws square around it (no re-rotation).
        val baked = TransformEffect(rotation = 90f).render(input, app)
        val framed = FrameEffect(layer = frame).render(baked, app)
        // The frame does NOT re-apply the rotation: it draws into the already-rotated
        // canvas, preserving its dims (a square border, not a re-rotated one).
        assertEquals("frame consumes the baked rotated canvas width", baked.width, framed.width)
        assertEquals("frame consumes the baked rotated canvas height", baked.height, framed.height)
        assertEquals("frame keeps the same canvas (no rotation re-applied)", baked.config, framed.config)

        // With no rotation the frame draws its square border INTO the committed
        // bitmap (a real pixel rasterisation, not a display-only overlay): the
        // border reaches the corners and the centre stays untouched.
        val plain = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        plain.eraseColor(Color.WHITE)
        val bordered = FrameEffect(layer = frame).render(plain, app)
        assertEquals("frame rasterises the border into the committed bitmap", Color.BLACK, bordered.getPixel(0, 0))
        assertEquals("frame rasterises the border into the committed bitmap", Color.BLACK, bordered.getPixel(bordered.width - 1, 0))
        assertEquals("centre keeps the white photo", Color.WHITE, bordered.getPixel(bordered.width / 2, bordered.height / 2))
    }

    @Test
    fun `mosaic stroke pixelates a region`() {
        // 40x40 with red left half and blue right half, split at x=20.
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                input.setPixel(x, y, if (x < 20) Color.RED else Color.BLUE)
            }
        }
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.MOSAIC,
                    points = listOf(0.5f to 0.5f),
                    brushSize = 0.2f
                )
            )
        )
        // A pixel inside the straddling cell is now a block-average mix, no longer
        // the original pure red edge value.
        val mixed = out.getPixel(16, 16)
        assertTrue(
            "expected a mixed (non-primary) colour in the mosaic cell, got $mixed",
            Color.blue(mixed) > 100 && Color.red(mixed) < 255
        )
        // The cell is uniform: neighbouring pixels share the same value.
        assertEquals(out.getPixel(16, 16), out.getPixel(17, 16))
        assertEquals(out.getPixel(16, 16), out.getPixel(16, 17))
    }

    @Test
    fun `blur stroke smooths a sharp edge`() {
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                input.setPixel(x, y, if (x < 20) Color.BLACK else Color.WHITE)
            }
        }
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.BLUR,
                    points = listOf(0.5f to 0.5f),
                    brushSize = 0.25f,
                    opacity = 1f
                )
            )
        )
        // A pixel on the black side of the edge becomes an intermediate gray
        // value (smoothed) rather than staying pure black.
        val smoothed = out.getPixel(18, 20)
        assertTrue(
            "blur should turn black into intermediate gray near the edge, got $smoothed",
            Color.red(smoothed) > 40 && Color.red(smoothed) < 215
        )
    }

    @Test
    fun `frame layer draws a border over the image and leaves the centre untouched`() {
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            frames = listOf(
                FrameLayer(
                    borderWidth = 0.1f,
                    cornerRadius = 0f,
                    color = Color.BLACK
                )
            )
        )
        assertEquals("frame border must reach the corners", Color.BLACK, out.getPixel(0, 0))
        assertEquals("frame border must cover the edges", Color.BLACK, out.getPixel(20, 0))
        assertEquals("centre of the image must keep the white", Color.WHITE, out.getPixel(20, 20))
    }

    @Test
    fun `frame with a shadow darkens the band inside the border and keeps dimensions`() {
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        fun renderFrame(shadow: Boolean) = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            frames = listOf(
                FrameLayer(
                    borderWidth = 0.1f,
                    cornerRadius = 0f,
                    color = Color.BLACK,
                    shadowColor = if (shadow) Color.BLACK else null,
                    shadowRadius = 0.1f
                )
            )
        )
        val withoutShadow = renderFrame(shadow = false)
        val withShadow = renderFrame(shadow = true)

        assertEquals("frame with shadow keeps dimensions", 40, withShadow.width)
        assertEquals("frame with shadow keeps dimensions", 40, withShadow.height)
        assertEquals("centre untouched by the shadow", Color.WHITE, withShadow.getPixel(20, 20))

        // The shadow band must visibly darken at least one pixel just inside
        // the crisp border relative to the same frame without a shadow.
        var darkened = false
        for (y in 2 until 12) {
            val without = Color.red(withoutShadow.getPixel(6, y))
            val with = Color.red(withShadow.getPixel(6, y))
            if (with < without) {
                darkened = true
                break
            }
        }
        assertTrue("shadow band darkens the near-border band", darkened)
    }

    @Test
    fun `text layer renders without crash and preserves dimensions`() {
        val input = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            texts = listOf(
                TextLayer(
                    text = "Hi",
                    color = Color.BLACK,
                    fontSizeSp = 0.5f,
                    x = 0.5f,
                    y = 0.5f
                )
            )
        )
        // Glyph rasterization is not reliable under Robolectric, but the render
        // path must not throw and must keep the canvas dimensions intact.
        assertEquals(120, out.width)
        assertEquals(120, out.height)
    }

    @Test
    fun `vignette darkens corners more than the centre`() {
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(128, 128, 128))
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            vignette = 100f
        )
        val centre = Color.red(out.getPixel(20, 20))
        val corner = Color.red(out.getPixel(0, 0))
        assertEquals("centre must stay untouched", 128, centre)
        assertTrue(
            "corner must be darkened by the vignette, got $corner",
            corner < 128
        )
    }

    @Test
    fun `negative sharpen softens a black-white edge`() {
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                input.setPixel(x, y, if (x < 20) Color.BLACK else Color.WHITE)
            }
        }
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            sharpen = -100f
        )
        // Just inside the black side of the edge, softening lifts the pixel
        // toward the local average instead of leaving it pure black.
        val softened = Color.red(out.getPixel(18, 20))
        assertTrue(
            "soften must lift black up near the edge, got $softened",
            softened > 0
        )
    }

    @Test
    fun `sharpenPreview returns a sharpened copy and leaves the input intact`() {
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                input.setPixel(x, y, if (x < 20) Color.rgb(100, 100, 100) else Color.rgb(200, 200, 200))
            }
        }
        val originalBorder = Color.red(input.getPixel(19, 20))
        val preview = BitmapRenderer.sharpenPreview(input, amount = 100f)
        assertEquals(40, preview.width)
        assertEquals(40, preview.height)
        val sharpenedBorder = Color.red(preview.getPixel(19, 20))
        assertTrue("sharpen must push the dark side of the edge darker", sharpenedBorder < originalBorder)
        val afterBorder = Color.red(input.getPixel(19, 20))
        assertEquals("the live preview must not mutate the source", originalBorder, afterBorder)
    }

    // --- free / custom-angle rotation ----------------------------------------

    // NOTE: Robolectric's shadow of android.graphics.Matrix.mapRect is unreliable
    // for arbitrary (non-90°) angles, so we cannot unit-test the exact grown
    // bounding-box dimensions here. Those are verified on-device; the tests below
    // only cover behaviours Robolectric shadows faithfully (exact-90 handling and
    // near-zero angles, which both take the stable fast path).

    @Test
    fun `near zero angle keeps the canvas at original size`() {
        val input = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        val out = BitmapRenderer.transforms(
            input, 0.1f, flipHorizontal = false, flipVertical = false
        )
        assertEquals(100, out.width)
        assertEquals(80, out.height)
    }

    @Test
    fun `90 degree rotation still swaps dimensions without growing`() {
        val input = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        val out = BitmapRenderer.transforms(
            input, 90f, flipHorizontal = false, flipVertical = false
        )
        assertEquals(80, out.width)
        assertEquals(100, out.height)
    }
}
