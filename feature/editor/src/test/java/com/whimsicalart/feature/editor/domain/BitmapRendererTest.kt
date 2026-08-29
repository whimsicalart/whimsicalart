package com.whimsicalart.feature.editor.domain

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
}
