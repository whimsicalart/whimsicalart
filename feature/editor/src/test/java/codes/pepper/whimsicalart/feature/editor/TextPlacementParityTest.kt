package codes.pepper.whimsicalart.feature.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import codes.pepper.whimsicalart.feature.editor.domain.TextOverlayRenderer
import codes.pepper.whimsicalart.feature.editor.ui.fittedImageSize
import codes.pepper.whimsicalart.feature.editor.ui.imageFrameOf
import codes.pepper.whimsicalart.feature.editor.ui.text.TextOverlay
import codes.pepper.whimsicalart.feature.editor.ui.text.toTextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

/**
 * TEXT PLACEMENT PARITY (tracker: BUGFIX, moved text lands at the wrong position
 * in the eye-ON view / saved photo). The live overlay places a text item in
 * FULL-BOX dp; commit normalizes it against the FITTED IMAGE RECT and the
 * committed pass draws it at `cx = x * canvasW` over the fold canvas. The two
 * agree exactly ONLY when that fitted rect is the one the ACTUAL displayed
 * bitmap occupies — i.e. the COMPOSITE's own aspect. Canvas-reshaping effects
 * (a crop, a frame's outer extent, a grown background placement) change the
 * fold's output aspect while the base photo dims do not, so a frame fixed to
 * the rotated BASE lands every moved item at a shifted pixel. These tests pin
 * the corrected mapping ([fittedImageSize] fed from the displayed composite).
 */
@RunWith(RobolectricTestRunner::class)
class TextPlacementParityTest {

    private val context get() = org.robolectric.RuntimeEnvironment.getApplication()

    @Test
    fun `a cropped composite reshapes the capture frame to the on-screen rect`() {
        val box = Size(900f, 1400f)
        val rotatedBase = Size(600f, 800f)
        // A crop half-width applied: the composite fold is 300x800 (3:8), a
        // different aspect than the 600x800 (3:4) base it was cropped from.
        val composite = Size(300f, 800f)
        val density = Density(1f)

        // The OLD mapping fixed the rect to the rotated base.
        val oldFrame = imageFrameOf(box, fittedImageSize(box, rotatedBase))
        // The NEW mapping fixes it to the canvas that is actually displayed.
        val newFrame = imageFrameOf(box, fittedImageSize(box, composite))

        assertEquals("the reshape must change the on-screen rect", 900f, oldFrame.size.width, 0.01f)
        assertEquals("the reshape must change the on-screen rect", 525f, newFrame.size.width, 0.01f)
        assertTrue(
            "the cropped composite now letterboxes HORIZONTALLY (new offset=${newFrame.offsetX})",
            newFrame.offsetX > 0f
        )

        // User dragged the text (centre) to box px (700, 130).
        val overlay = TextOverlay(id = "t1", text = "Hi", position = Offset(700f, 130f))

        // The TRUE pixel over the composite canvas: the box point projected
        // onto the displayed composite rect, scaled to composite px.
        val expectedX = (700f - newFrame.offsetX) / newFrame.size.width * composite.width
        val expectedY = (130f - newFrame.offsetY) / newFrame.size.height * composite.height

        val newItem = overlay.toTextItem(
            density, newFrame.size.width, newFrame.size.height,
            newFrame.offsetX, newFrame.offsetY
        )
        assertEquals("new mapping must draw at the projected composite px", expectedX, newItem.x * composite.width, 0.01f)
        assertEquals("new mapping must draw at the projected composite px", expectedY, newItem.y * composite.height, 0.01f)

        val oldItem = overlay.toTextItem(
            density, oldFrame.size.width, oldFrame.size.height,
            oldFrame.offsetX, oldFrame.offsetY
        )
        assertTrue(
            "the base-fixed frame must land elsewhere on the composite canvas " +
                "(old=${oldItem.x * composite.width}, expected=$expectedX)",
            abs(oldItem.x * composite.width - expectedX) > 40f
        )
    }

    @Test
    fun `a same-aspect composite keeps the base capture frame unchanged`() {
        // Plain / letterboxed / rotated photos with no canvas-reshaper: the
        // composite shares the base aspect, so the composite-driven rect is
        // BYTE-EQUAL to the old base-derived one — no regression on the common
        // path (and the composite-null fallback reproduces it exactly too).
        val box = Size(900f, 1200f)
        val rotatedBase = Size(600f, 400f)
        val compositeSameAspect = Size(480f, 320f)
        val fromComposite = imageFrameOf(box, fittedImageSize(box, compositeSameAspect))
        val fromBase = imageFrameOf(box, fittedImageSize(box, rotatedBase))
        assertEquals("same aspect -> same on-screen rect", fromBase, fromComposite)
        assertEquals(0f, fromBase.offsetX, 0.01f)
        assertEquals(300f, fromBase.offsetY, 0.01f)
        assertEquals("the fitted 3:2 canvas fills the wide box", 900f, fromBase.size.width, 0.01f)
        assertEquals("the fitted 3:2 canvas letterboxes the tall box", 600f, fromBase.size.height, 0.01f)
    }

    @Test
    fun `letterboxed box uses fitted margins and keeps the fraction mapping`() {
        // Portrait box, landscape composite: the composite drives a real
        // vertical letterbox, and a box point normalizes against that rect 1:1.
        val box = Size(1000f, 1200f)
        val composite = Size(800f, 600f)
        val frame = imageFrameOf(box, fittedImageSize(box, composite))
        assertEquals(0f, frame.offsetX, 0.01f)
        assertEquals(225f, frame.offsetY, 0.01f)
        assertEquals(1000f, frame.size.width, 0.01f)
        assertEquals(750f, frame.size.height, 0.01f)

        val density = Density(1f)
        val placement = Offset(900f, 60f)
        val item = TextOverlay(id = "t", text = "Hi", position = placement)
            .toTextItem(density, frame.size.width, frame.size.height, frame.offsetX, frame.offsetY)
        val canvasX = item.x * composite.width
        val canvasY = item.y * composite.height
        val projectedX = (900f - frame.offsetX) / frame.size.width * composite.width
        val projectedY = (60f - frame.offsetY) / frame.size.height * composite.height
        assertEquals(projectedX, canvasX, 0.01f)
        assertEquals(projectedY, canvasY, 0.01f)
    }

    @Test
    fun `committed text raster lands on the projected composite pixel after a crop`() {
        // End-to-end committed path: normalize against the COMPOSITE-correct
        // frame, then run the same drawTexts the committed pass runs on the
        // composite-aspect canvas. The glyph ink must sit at the projected
        // pixel, while the old base-fixed normalization visibly misses it.
        TextOverlayRenderer.clearTextCache()
        val box = Size(900f, 1400f)
        val composite = Size(300f, 800f)
        val frame = imageFrameOf(box, fittedImageSize(box, composite))
        val density = Density(1f)

        fun itemPosition(position: Offset) = TextOverlay(
            id = "t",
            text = "Hello",
            position = position,
            fontSize = 24f
        ).toTextItem(density, frame.size.width, frame.size.height, frame.offsetX, frame.offsetY)

        val placement = Offset(700f, 130f)
        val projected = itemPosition(placement)

        val canvas = Bitmap.createBitmap(300, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        TextOverlayRenderer.drawTexts(context, canvas, listOf(projected))
        val ink = inkBoundingBox(canvas)
        val projectedCenter = ink.let { (it.left + it.right) / 2f to (it.top + it.bottom) / 2f }

        val expectedX = (700f - frame.offsetX) / frame.size.width * composite.width
        val expectedY = (130f - frame.offsetY) / frame.size.height * composite.height
        assertEquals("text ink x must sit on the projected composite pixel", expectedX, projectedCenter.first, 6f)
        assertEquals("text ink y must sit on the projected composite pixel", expectedY, projectedCenter.second, 6f)
    }

    private fun inkBoundingBox(bmp: Bitmap): Rect {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                if (bmp.getPixel(x, y) != Color.BLACK) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return Rect(minX, minY, maxX, maxY)
    }
}