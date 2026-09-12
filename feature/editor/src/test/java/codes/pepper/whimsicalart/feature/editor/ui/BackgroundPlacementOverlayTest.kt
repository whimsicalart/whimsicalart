package codes.pepper.whimsicalart.feature.editor.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import codes.pepper.whimsicalart.feature.editor.domain.matting.BackgroundReplacer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The placement grid must ALWAYS be the replacement image's true on-screen
 * bounds (the "image follows the grid" invariant) — specs:
 *   - grid = centred canvas box at the default placement;
 *   - pan/scale/rotation shift exactly `place()`'s projection (same centre and
 *     half-extents), so the grid can never drift from the displayed pixels;
 *   - `placedCanvasSize` (which sizes the grid's fit box) is EXACTLY the actual
 *     `BackgroundReplacer.place` output bitmap dimensions — the linchpin that
 *     ties the grid maths to the committed renderer's real canvas growth.
 */
@RunWith(RobolectricTestRunner::class)
class BackgroundPlacementOverlayTest {

    private companion object {
        private val CANVAS = Size(400f, 300f)
        private val FIT = Size(800f, 600f)
    }

    private fun gridCorners(grid: PlacementGridGeom): List<Offset> {
        val rad = grid.rotationDegrees * PI.toFloat() / 180f
        val cosA = cos(rad)
        val sinA = sin(rad)
        fun corner(hw: Float, hh: Float) = Offset(
            grid.center.x + hw * cosA - hh * sinA,
            grid.center.y + hw * sinA + hh * cosA
        )
        return listOf(
            corner(-grid.halfW, -grid.halfH),
            corner(grid.halfW, -grid.halfH),
            corner(grid.halfW, grid.halfH),
            corner(-grid.halfW, grid.halfH)
        )
    }

    private fun canvasRectCorners(
        canvas: Size,
        canvasLeft: Float,
        canvasTop: Float,
        fit: Float,
        scale: Float,
        panX: Float,
        panY: Float,
        rotationDegrees: Float
    ): List<Offset> {
        val cw = canvas.width
        val ch = canvas.height
        val cx = cw / 2f + panX * cw
        val cy = ch / 2f + panY * ch
        val rad = rotationDegrees * PI.toFloat() / 180f
        val cosA = cos(rad)
        val sinA = sin(rad)
        fun corner(w: Float, h: Float) = Offset(
            canvasLeft + (cx + w * cosA - h * sinA) * fit,
            canvasTop + (cy + w * sinA + h * cosA) * fit
        )
        return listOf(
            corner(-cw * scale / 2f, -ch * scale / 2f),
            corner(cw * scale / 2f, -ch * scale / 2f),
            corner(cw * scale / 2f, ch * scale / 2f),
            corner(-cw * scale / 2f, ch * scale / 2f)
        )
    }

    private fun assertGridEqualsPlacement(
        canvas: Size = CANVAS,
        fit: Size = FIT,
        transform: BackgroundReplacer.PlacementTransform
    ) {
        val grid = placementGridGeom(
            canvas, fit,
            transform.scale, transform.panX, transform.panY, transform.rotationDegrees
        )
        assertNotNull(grid)
        grid!!
        // Re-derive the SAME projection the overlay uses for its fit box (it sizes
        // the box from placedCanvasSize, letterboxing the grown output).
        val (outW, outH) = BackgroundReplacer.placedCanvasSize(
            canvas.width.toInt(), canvas.height.toInt(), transform.scale, transform.rotationDegrees
        )
        val outWf = outW.toFloat()
        val outHf = outH.toFloat()
        val pxScale = min(fit.width / outWf, fit.height / outHf)
        val outLeft = (fit.width - outWf * pxScale) / 2f
        val outTop = (fit.height - outHf * pxScale) / 2f
        val canvasLeft = outLeft + (outWf - canvas.width) / 2f * pxScale
        val canvasTop = outTop + (outHf - canvas.height) / 2f * pxScale

        val expectedCenter = Offset(
            canvasLeft + canvas.width * pxScale / 2f + transform.panX * canvas.width * pxScale,
            canvasTop + canvas.height * pxScale / 2f + transform.panY * canvas.height * pxScale
        )
        assertEquals("grid centre X", expectedCenter.x, grid.center.x, 0.01f)
        assertEquals("grid centre Y", expectedCenter.y, grid.center.y, 0.01f)

        // Corners: the scaled/rotated placement rect around that centre.
        val actual = gridCorners(grid)
        val expected = canvasRectCorners(
            canvas, canvasLeft, canvasTop, pxScale,
            transform.scale, transform.panX, transform.panY, transform.rotationDegrees
        )
        for (i in 0 until 4) {
            assertEquals("corner $i x", expected[i].x, actual[i].x, 0.01f)
            assertEquals("corner $i y", expected[i].y, actual[i].y, 0.01f)
        }
    }

    @Test
    fun `default placement shows a centred full-canvas grid`() {
        val grid = placementGridGeom(CANVAS, FIT, 1f, 0f, 0f, 0f)
        assertNotNull(grid)
        grid!!
        // Box 800x600 fits 400x300 exactly → fit = 2, canvas covers the box.
        assertEquals(400f, grid.center.x, 0.001f)
        assertEquals(300f, grid.center.y, 0.001f)
        assertEquals(400f, grid.halfW, 0.001f)
        assertEquals(300f, grid.halfH, 0.001f)
        assertEquals(0f, grid.rotationDegrees, 0.001f)
    }

    @Test
    fun `pan scale and rotation move and grow the grid exactly like the placement`() {
        for (t in listOf(
            BackgroundReplacer.PlacementTransform(scale = 1.4f, panX = 0.2f, panY = -0.15f, rotationDegrees = 40f, freeScaleUp = true),
            BackgroundReplacer.PlacementTransform(scale = 1f, panX = -0.3f, panY = 0.25f, rotationDegrees = 0f),
            BackgroundReplacer.PlacementTransform(scale = 0.6f, panX = 0f, panY = 0f, rotationDegrees = 15f),
            BackgroundReplacer.PlacementTransform(scale = 1.2f, panX = -0.5f, panY = -0.5f, rotationDegrees = 90f)
        )) {
            assertGridEqualsPlacement(transform = t)
        }
    }

    @Test
    fun `portrait canvas inside a landscape box letterboxes the grid on the placed bounds`() {
        assertGridEqualsPlacement(
            canvas = Size(300f, 400f),
            fit = Size(900f, 500f),
            transform = BackgroundReplacer.PlacementTransform(
                scale = 1.25f, rotationDegrees = 20f, freeScaleUp = true
            )
        )
    }

    @Test
    fun `placedCanvasSize predicts the exact place output bitmap for every transform`() {
        val source = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val mask = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (x in 12 until 28) for (y in 8 until 22) setPixel(x, y, Color.BLACK)
        }
        val bg = Bitmap.createBitmap(55, 35, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val transforms = listOf(
            BackgroundReplacer.PlacementTransform(),
            BackgroundReplacer.PlacementTransform(scale = 1f, rotationDegrees = 45f, freeScaleUp = true),
            BackgroundReplacer.PlacementTransform(scale = 1.6f, panX = 0.25f, panY = -0.2f, rotationDegrees = 37f, freeScaleUp = true),
            BackgroundReplacer.PlacementTransform(scale = 0.75f, rotationDegrees = 0f)
        )
        for (t in transforms) {
            val out = BackgroundReplacer.place(source, mask, bg, t)
            val predicted = BackgroundReplacer.placedCanvasSize(source.width, source.height, t.scale, t.rotationDegrees)
            assertEquals("OUT width for $t", predicted.first, out.width)
            assertEquals("OUT height for $t", predicted.second, out.height)
            out.recycle()
        }
    }

    @Test
    fun `grid is null until the canvas box is measured`() {
        assertNull(placementGridGeom(Size(0f, 0f), FIT, 1f, 0f, 0f, 0f))
        assertNull(placementGridGeom(CANVAS, Size(0f, 0f), 1f, 0f, 0f, 0f))
    }

    /**
     * Regression: when the image is letterboxed the overlay grid must sit over the
     * fitted photo rect, not the full canvas box. The origin offset shifts the
     * grid by exactly the fitted rect's top-left inside the box.
     */
    @Test
    fun `letterboxed grid is anchored at the frame offset`() {
        // fit = min(1200/400, 900/300) = 3 → the photo fills the whole 1200×900
        // fit box exactly (a non-letterboxed canvas): origin = (0, 0).
        val canvas = Size(400f, 300f)
        val fit = Size(1200f, 900f)
        val noOffset = placementGridGeom(canvas, fit, 1f, 0f, 0f, 0f)
        assertNotNull(noOffset)
        val withOffset = placementGridGeom(canvas, fit, 1f, 0f, 0f, 0f, originX = 100f, originY = 200f)
        assertNotNull(withOffset)
        // The entire grid (centre and half-extents) shifts by exactly the origin.
        assertEquals(noOffset!!.center.x + 100f, withOffset!!.center.x, 0.001f)
        assertEquals(noOffset.center.y + 200f, withOffset.center.y, 0.001f)
        assertEquals(noOffset.halfW, withOffset.halfW, 0.001f)
        assertEquals(noOffset.halfH, withOffset.halfH, 0.001f)
        assertEquals(noOffset.pxPerCanvasX, withOffset.pxPerCanvasX, 0.001f)
        assertEquals(noOffset.pxPerCanvasY, withOffset.pxPerCanvasY, 0.001f)
    }

    /**
     * The portrait-in-landscape letterbox case (the exact report scenario):
     * a portrait canvas in a wide-landscape fit box — the offset shifts the grid
     * into the photo rect position.
     */
    @Test
    fun `portrait canvas in landscape box with origin places grid on the photo rect`() {
        val canvas = Size(300f, 400f)
        val fit = Size(600f, 300f) // landscape box
        // fit for placement = min(600/300, 300/400) = min(2, 0.75) = 0.75
        // outW = 300*0.75 = 225, outH = 400*0.75 = 300
        // outLeft = (600-225)/2 = 187.5, outTop = (300-300)/2 = 0
        // canvasLeft = 187.5 + (300-300)/2*0.75 = 187.5
        // canvasTop = 0 + (400-400)/2*0.75 = 0
        // centre = (187.5 + 300*0.75/2, 0 + 400*0.75/2) = (300, 150)
        val noOrigin = placementGridGeom(canvas, fit, 1f, 0f, 0f, 0f)!!
        assertEquals(300f, noOrigin.center.x, 0.001f)
        assertEquals(150f, noOrigin.center.y, 0.001f)

        // Origin = (187.5, 0) shifts the grid to fill the photo rect exactly.
        val withOrigin = placementGridGeom(
            canvas, fit, 1f, 0f, 0f, 0f, originX = 187.5f, originY = 0f
        )!!
        assertEquals(300f + 187.5f, withOrigin.center.x, 0.001f)
        assertEquals(150f + 0f, withOrigin.center.y, 0.001f)
        // Half-extents and per-canvas mapping are unchanged by the origin shift.
        assertEquals(noOrigin.halfW, withOrigin.halfW, 0.001f)
        assertEquals(noOrigin.halfH, withOrigin.halfH, 0.001f)
    }
}