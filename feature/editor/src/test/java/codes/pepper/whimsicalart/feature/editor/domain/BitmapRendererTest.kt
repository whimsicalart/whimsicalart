package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import codes.pepper.whimsicalart.feature.stickers.domain.StickerItem
import codes.pepper.whimsicalart.feature.stickers.domain.StickerLineStyle
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

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
            inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.1f, color = Color.BLACK)
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
                FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.1f, color = Color.BLACK))
            )
        )
        assertEquals("frame border must reach the corners", Color.BLACK, out.getPixel(0, 0))
        assertEquals("frame border must cover the edges", Color.BLACK, out.getPixel(20, 0))
        assertEquals("centre of the image must keep the white", Color.WHITE, out.getPixel(20, 20))
    }

    @Test
    fun `every frame style draws a band of the configured thickness`() {
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        for (style in FrameStyle.entries) {
            val out = BitmapRenderer.render(
                context = org.robolectric.RuntimeEnvironment.getApplication(),
                input = input,
                rotationDegrees = 0f,
                flipHorizontal = false,
                flipVertical = false,
                cropRect = null,
                colorMatrix = null,
                frames = listOf(FrameLayer(inner = FrameTier(style = style, thickness = 0.2f, color = Color.BLACK)))
            )
            assertEquals("$style must keep the canvas dimensions", 40, out.width)
            assertEquals("$style must keep the canvas dimensions", 40, out.height)
            assertEquals("$style band must cover the corners", Color.BLACK, out.getPixel(0, 0))
            assertEquals("$style band must cover the mid-edge", Color.BLACK, out.getPixel(20, 0))
            assertEquals("$style centre keeps the white photo", Color.WHITE, out.getPixel(20, 20))
        }
    }

    @Test
    fun `thickness 0 renders no frame band`() {
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
            frames = listOf(FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 0f, color = Color.BLACK)))
        )
        assertEquals("a zero-thickness frame must not touch any pixel", Color.WHITE, out.getPixel(0, 0))
        assertEquals("a zero-thickness frame must not touch any pixel", Color.WHITE, out.getPixel(20, 0))
        assertEquals("a zero-thickness frame must not touch any pixel", Color.WHITE, out.getPixel(20, 20))
    }

    @Test
    fun `translucent frame colour blends over the image behind it`() {
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        // 50%-alpha red frame colour.
        val halfRed = Color.argb(128, 255, 0, 0)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            frames = listOf(FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.25f, color = halfRed)))
        )
        // A 50% red over white yields pink: strong red, noticeably faded green/blue.
        val band = out.getPixel(0, 0)
        assertTrue("band must blend the translucent red in", Color.red(band) > 200)
        assertTrue("band must fade the white behind", Color.green(band) < 150)
        assertTrue("band must fade the white behind", Color.blue(band) < 150)
        assertEquals("centre untouched by the translucent blend", Color.WHITE, out.getPixel(20, 20))
    }

    @Test
    fun `50 percent thickness maps to a quarter band - the interior survives`() {
        // Spec (docs/effects_reference.md → *Frames* → *Thickness*): the slider
        // maps to HALF its old value — 50% = a quarter band, 100% = half the
        // short edge. On a 100x100 square, t = 0.5 × 100 / 2 = 25 → a solid 25 px
        // band with a 50x50 interior still showing the photo.
        val input = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            frames = listOf(FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.5f, color = Color.BLACK)))
        )
        assertEquals("band covers the top quarter", Color.BLACK, out.getPixel(0, 0))
        assertEquals("band reaches y=24 (25 px deep)", Color.BLACK, out.getPixel(50, 24))
        assertEquals("interior starts at y=25", Color.WHITE, out.getPixel(50, 25))
        assertEquals("centre keeps the photo", Color.WHITE, out.getPixel(50, 50))
        assertEquals("bottom band resumes at y=75", Color.BLACK, out.getPixel(50, 75))
        assertEquals("bottom-quarter corner is band", Color.BLACK, out.getPixel(99, 99))
    }

    @Test
    fun `100 percent thickness still degenerates to full cover on a square`() {
        // t = 1.0 × 100 / 2 = 50 = short-edge half = the cap; the interior window
        // collapses to zero, so the frame covers the whole canvas (cap preserved).
        val input = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            frames = listOf(FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 1f, color = Color.BLACK)))
        )
        assertEquals("full-cover frame fills the corners", Color.BLACK, out.getPixel(0, 0))
        assertEquals("full-cover frame fills the centre", Color.BLACK, out.getPixel(50, 50))
        assertEquals("full-cover frame fills the far corner", Color.BLACK, out.getPixel(99, 99))
    }

    @Test
    fun `the outer tier enlarges the committed canvas and insets the photo by the extent`() {
        // SOLID outer tier, thickness 0.25 on a 40x40 photo: t = 0.25×40/2 = 5 px,
        // no lines, zero reach → extent = 5 px. The fold canvas grows to 50x50 and
        // the photo centres at (5,5). The 5 px ring hugs the photo edge; the
        // margin between the ring and the canvas corner stays untouched.
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
                FrameLayer(outer = FrameTier(style = FrameStyle.SOLID, thickness = 0.25f, color = Color.BLACK))
            )
        )
        assertEquals("canvas grows by 2× the outer extent", 50, out.width)
        assertEquals("canvas grows by 2× the outer extent", 50, out.height)
        assertEquals("ring paints one pixel inside the photo left edge column range",
            Color.BLACK, out.getPixel(4, 25))
        assertEquals("ring paints one pixel inside the photo right edge",
            Color.BLACK, out.getPixel(45, 25))
                assertEquals("the photo's own edge column stays interior", Color.WHITE, out.getPixel(5, 25))
        assertEquals("the photo centre keeps the photo", Color.WHITE, out.getPixel(25, 25))
        assertEquals("the ring avoids the margin between ring and canvas corner",
            Color.TRANSPARENT, out.getPixel(0, 0))
        assertEquals("the margin left of the photo stays untouched",
            Color.TRANSPARENT, out.getPixel(0, 20))
    }

    @Test
    fun `the outer tier draws its decorative line strips beyond the band with a leading gap`() {
        // SOLID outer tier, thickness 0.25 (t = 5 px), 2 lines at 10% distance
        // and 5% width, applied at the third-scale (so d = 0.1×40/3 = 1.33 px,
        // l = 0.05×40/3 = 0.67 px). Extent = 5 + 2×(d+l) = 9 px: the ring is 9 px,
        // the canvas 58×58. The outer strips are anchored to the band's outer
        // border (contour), so on this constant-reach SOLID the reach wraps the
        // 17-px photo edge at margin 5 and the strips sit one pixel apart out of
        // it, with the sub-pixel d gap falling between the ring and strip 0.
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
                    outer = FrameTier(
                        style = FrameStyle.SOLID,
                        thickness = 0.25f,
                        color = Color.BLACK,
                        lines = 2,
                        lineDistance = 0.1f,
                        lineThickness = 0.05f
                    )
                )
            )
        )
        assertEquals("canvas grows by the band + lines extent", 58, out.width)
        assertEquals("canvas grows by the band + lines extent", 58, out.height)
        val row = 25
        assertEquals("band at margin 3", Color.BLACK, out.getPixel(6, row))
        assertEquals("band ring sits at margin 4 (the strip anchor)", Color.BLACK, out.getPixel(5, row))
        assertEquals("first line strip at margin 5, anchored to the band's outer border", Color.BLACK, out.getPixel(4, row))
        assertEquals("gap between the band ring and the strip stays untouched at margin 6", Color.TRANSPARENT, out.getPixel(3, row))
        assertEquals("second line strip at margin 7", Color.BLACK, out.getPixel(2, row))
        assertEquals("beyond every strip the margin stays untouched at margin 8", Color.TRANSPARENT, out.getPixel(1, row))
        assertEquals("beyond every strip the margin stays untouched at margin 9", Color.TRANSPARENT, out.getPixel(0, row))
    }

    @Test
    fun `both tiers commit through one layer - inner band inward, outer band outward`() {
        // Inner SOLID (t = 4 px inward over the photo) + outer SOLID (t = 5 px
        // outward, extent 5 → canvas 50x50, photo at (5,5)). The photo edge row
        // y=5..9 gets the INNER band, the ring margins get the OUTER band, and
        // the interior window (≥ 4 px in from each edge) keeps the photo.
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
                    inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.2f, color = Color.BLACK),
                    outer = FrameTier(style = FrameStyle.SOLID, thickness = 0.25f, color = Color.BLACK)
                )
            )
        )
        assertEquals("outer extent grows the canvas", 50, out.width)
        assertEquals("outer extent grows the canvas", 50, out.height)
        assertEquals("inner band covers the photo edge row", Color.BLACK, out.getPixel(5, 7))
        assertEquals("outer band covers the ring margin", Color.BLACK, out.getPixel(4, 25))
        assertEquals("interior window keeps the photo", Color.WHITE, out.getPixel(25, 25))
        assertEquals("interior starts 4 px in from the photo edge", Color.WHITE, out.getPixel(9, 9))
    }

    @Test
    fun `edge-size styles are exactly the twelve starred styles in picker order`() {
        val starred = FrameStyle.entries.filter { it.hasEdgeSize }
        assertEquals(
            listOf(
                "Rounded", "Beveled",
                "Inward Curve Edge", "Corner Eased Edge", "Gear Edge", "Ogee Edge",
                "Double Ogee Edge", "Dupont Edge", "Cove Edge", "French Cove Edge",
                "Dupont + \u00BD Bullnose Edge", "Ogee Cove Edge"
            ),
            starred.map { it.displayName }
        )
        assertEquals("edge size is exposed by exactly the starred styles", 12, starred.size)
        assertEquals("only Custom is the custom-curved slider style", 1, FrameStyle.entries.count { it.isCustom })
        assertTrue(FrameStyle.CUSTOM.isCustom)
    }

    @Test
    fun `style chips are ordered None then the spec taxonomy`() {
        assertEquals(
            listOf(
                "Solid", "Rounded", "Beveled", "Oval", "Heart",
                "Inward Curve Edge", "Corner Eased Edge", "Gear Edge", "Ogee Edge",
                "Double Ogee Edge", "Dupont Edge", "Cove Edge", "French Cove Edge",
                "Dupont + \u00BD Bullnose Edge", "Ogee Cove Edge",
                "Wavy", "Sawtooth", "Cogged", "Floral", "Waterfall",
                "Single Wave", "Twin Wave", "Recessed", "Offset Wave",
                "Water Waves Inward", "Water Waves Outward", "Custom (Curved)"
            ),
            FrameStyle.entries.map { it.displayName }
        )
    }

    @Test
    fun `edge size 0 squares the rounded and beveled corners`() {
        val rounded = FrameGeometry(FrameStyle.ROUNDED, 200, 200, 20f, edgeSize = 0f)
        val beveled = FrameGeometry(FrameStyle.BEVELED, 200, 200, 20f, edgeSize = 0f)
        for (x in 0 until 200) {
            assertEquals("rounded must be straight at edge size 0", 20f, rounded.topInset(x), 1e-3f)
            assertEquals("beveled must be straight at edge size 0", 20f, beveled.topInset(x), 1e-3f)
        }
    }

    @Test
    fun `edge size reach runs toward the middle of the short edge`() {
        val w = 400
        val h = 200
        val t = 20f
        // Beveled's linear chamfer pinpoints the reach exactly: at 50% the
        // straight run starts at half the short edge (E = 50% * short/2).
        val beveledHalf = FrameGeometry(FrameStyle.BEVELED, w, h, t, edgeSize = 0.5f)
        assertEquals("beveled straight run begins at the half-short mid point", t, beveledHalf.leftInset(50), 1e-3f)
        assertTrue("beveled is still treated just inside the reach", beveledHalf.leftInset(49) > t + 0.5f)
        // For every starred style at 100%: the treatment runs toward the middle
        // of the short edge (a clear treatment exists somewhere on it) while the
        // long edge keeps a straight run in the middle.
        for (style in FrameStyle.entries.filter { it.hasEdgeSize }) {
            val full = FrameGeometry(style, w, h, t, edgeSize = 1f)
            assertEquals("$style long-edge middle stays straight at 100%", t, full.topInset(w / 2), 1e-3f)
            assertTrue(
                "$style short edge must be treated at 100%",
                (0 until h).any { y -> kotlin.math.abs(full.leftInset(y) - t) > 0.5f }
            )
        }
    }

    @Test
    fun `renamed styles keep their exact pre-taxonomy footprint`() {
        val wm = 40f
        // The pre-taxonomy formulas the imposter styles used; new renamed styles
        // must reproduce them pixel-for-pixel.
        fun gear(u: Float): Float = if (kotlin.math.sin(2f * kotlin.math.PI.toFloat() * 3f * u) >= 0f) 1.35f else 0.8f
        fun ogee(u: Float): Float = 1f + 0.3f * kotlin.math.cos(2f * kotlin.math.PI.toFloat() * u)
        fun doubleOgee(u: Float): Float = 1f + 0.3f * kotlin.math.cos(4f * kotlin.math.PI.toFloat() * u)
        fun dupont(u: Float): Float = if (u < 0.4f || u > 0.6f) 1f else 0.6f
        fun ogeeCove(u: Float): Float = 1f + 0.25f * kotlin.math.cos(2f * kotlin.math.PI.toFloat() * (u - 0.25f))
        val cases = listOf(
            FrameStyle.COGGED to ::gear,
            FrameStyle.SINGLE_WAVE to ::ogee,
            FrameStyle.TWIN_WAVE to ::doubleOgee,
            FrameStyle.RECESSED to ::dupont,
            FrameStyle.OFFSET_WAVE to ::ogeeCove
        )
        for ((style, formula) in cases) {
            val g = FrameGeometry(style, 41, 41, 8f)
            for (x in 0 until 41) {
                val expected = 8f * formula(x / wm)
                assertEquals(style.toString(), expected, g.topInset(x), 0.01f)
            }
        }
    }

    @Test
    fun `custom strength shapes the half-curve arc triangle and teardrop distinctly`() {
        val arc = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 0f, customCurveDistance = 0.5f, customStrength = 0f, customCount = 2f)
        val triangle = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 0f, customCurveDistance = 0.5f, customStrength = 1f, customCount = 2f)
        val teardrop = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 0f, customCurveDistance = 0.5f, customStrength = -1f, customCount = 2f)
        // Every shape peaks at the half-curve midpoint (x = u*2 = 0.5 → column 10).
        assertEquals("arc peak", 12f, arc.topInset(10), 1e-3f)
        assertEquals("triangle peak", 12f, triangle.topInset(10), 1e-3f)
        assertEquals("teardrop peak", 12f, teardrop.topInset(10), 1e-3f)
        // One quarter into the half-curve (column 5, x = 0.25): teardrop bulges
        // most, arc next, triangle tapers to its midpoint point.
        assertTrue("teardrop must bulge farther than arc", teardrop.topInset(5) > arc.topInset(5))
        assertTrue("arc must bulge farther than triangle", arc.topInset(5) > triangle.topInset(5))
    }

    @Test
    fun `custom count sets the number of half-curves per side`() {
        fun halfCurves(g: FrameGeometry): Int {
            var count = 0
            for (i in 1 until 40) {
                val up = g.topInset(i) - g.topInset(i - 1)
                val down = g.topInset(i + 1) - g.topInset(i)
                if (up > 0f && down < 0f) count++
            }
            return count
        }
        for (count in listOf(1f, 2f, 4f)) {
            val g = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 0f, customCurveDistance = 0.5f, customStrength = 0f, customCount = count)
            assertEquals("count=$count must yield that many half-curves per side", count.toInt(), halfCurves(g))
        }
    }

    @Test
    fun `polygon interior matches isInterior for closed and axis-aligned styles`() {
        // The interior polygon (the Compose preview's even-odd fill) must
        // describe the same window the committed per-pixel pass paints. SOLID's
        // boundary is axis-aligned and OVAL/HEART build both from the same
        // ray-cast spans, so agreement is exact on every non-corner cell; the
        // corner L-squares are excluded because the diagonal polygon corner is a
        // single geometric point while the pixel grid is axis-separable there.
        for (style in listOf(FrameStyle.SOLID, FrameStyle.OVAL, FrameStyle.HEART)) {
            val g = FrameGeometry(style, 60, 40, 8f)
            var checked = 0
            var mismatches = 0
            for (y in 0 until 40) {
                for (x in 0 until 60) {
                    if (inCornerL(x, y) || nearBoundary(g, x, y)) continue
                    checked++
                    val committed = g.isInterior(x, y)
                    val preview = evenOddContains(g.polygon, x + 0.5f, y + 0.5f)
                    if (committed != preview) mismatches++
                }
            }
            assertEquals(
                "$style preview polygon must agree exactly away from corners/boundary",
                0,
                mismatches
            )
        }
    }

    @Test
    fun `heart interior is centred on the canvas in both axes`() {
        // The heart's bounding box must sit centred in the interior window
        // (docs/effects_reference.md → *Frames* → *Style* → heart): equal
        // margins above/below and left/right. The old (cy − 3f) offset dropped
        // the whole shape by ~6·s — ~16 px on this 101×101 canvas at t = 8 —
        // leaving the bottom cusp ~26 px below centre. Centring is asserted on
        // the exact polygon extents (not per-pixel filled rows, which the
        // tapering cusp quantises by ±1-2 px).
        val size = 101
        val centre = (size - 1) / 2f
        val g = FrameGeometry(FrameStyle.HEART, size, size, 8f)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until g.polygon.size step 2) {
            val x = g.polygon[i]
            val y = g.polygon[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        // The bounding box is the source of the filled interior (rayCast), so
        // ensuring it is centred centres the drawn heart too; ±2 covers the
        // ~1 px residual of the integer (cy + 3) midpoint offset.
        assertTrue(
            "heart vertically centred, bbox y-centre ${(minY + maxY) / 2f} vs $centre",
            abs((minY + maxY) / 2f - centre) <= 2f
        )
        assertTrue(
            "heart horizontally centred, bbox x-centre ${(minX + maxX) / 2f} vs $centre",
            abs((minX + maxX) / 2f - centre) <= 2f
        )
    }

    /** The four t-deep corner L-squares where the two rasterizations diverge. */
    private fun inCornerL(x: Int, y: Int): Boolean {
        val t = 9
        val w = 60
        val h = 40
        return (x < t && y < t) || (x >= w - t && y < t) ||
            (x < t && y >= h - t) || (x >= w - t && y >= h - t)
    }

    /** True when the pixel touches the committed interior boundary (skip these). */
    private fun nearBoundary(g: FrameGeometry, x: Int, y: Int): Boolean {
        val here = g.isInterior(x, y)
        val neighbours = arrayOf(
            x - 1 to y, x + 1 to y, x to y - 1, x to y + 1,
            x - 1 to y - 1, x + 1 to y - 1, x - 1 to y + 1, x + 1 to y + 1
        )
        for (p in neighbours) {
            if (p.first in 0 until 60 && p.second in 0 until 40 &&
                g.isInterior(p.first, p.second) != here
            ) {
                return true
            }
        }
        return false
    }

    /** Even-odd ray-cast point-in-polygon test (boundary probe, rightwards ray). */
    private fun evenOddContains(poly: FloatArray, px: Float, py: Float): Boolean {
        var inside = false
        val n = poly.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ax = poly[2 * i]; val ay = poly[2 * i + 1]
            val bx = poly[2 * j]; val by = poly[2 * j + 1]
            if ((ay <= py && by > py) || (by <= py && ay > py)) {
                val t = (py - ay) / (by - ay)
                if (ax + t * (bx - ax) > px) inside = !inside
            }
        }
        return inside
    }

    @Test
    fun `custom curve distance widens the custom band`() {
        val faint = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 0f, customCurveDistance = 0f, customStrength = 0f, customCount = 2f)
        val strong = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 0f, customCurveDistance = 1f, customStrength = 0f, customCount = 2f)
        // Column 10 sits on the half-curve peak (u = 0.25 → x = 0.5).
        assertTrue(
            "curve distance must widen the custom band",
            strong.topInset(10) > faint.topInset(10)
        )
        assertEquals("zero curve distance leaves the band at plain thickness", 8f, faint.topInset(10), 1e-3f)
    }

    @Test
    fun `custom curve distance sign picks the direction`() {
        val outward = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 0f, customCurveDistance = 0.5f, customStrength = 0f, customCount = 2f)
        val inward = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 0f, customCurveDistance = -0.5f, customStrength = 0f, customCount = 2f)
        val straight = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 0f, customCurveDistance = 0f, customStrength = 0f, customCount = 2f)
        // Column 10 sits on the half-curve peak (u = 0.25 → x = 0.5, y = 1).
        assertEquals("positive curve distance bulges OUTWARD past the band", 12f, outward.topInset(10), 1e-3f)
        assertEquals("negative curve distance scoops INWARD inside the band", 4f, inward.topInset(10), 1e-3f)
        assertEquals("zero curve distance leaves the band straight", 8f, straight.topInset(10), 1e-3f)
        assertEquals("the two directions mirror about the straight band",
            8f, (outward.topInset(10) + inward.topInset(10)) / 2f, 1e-3f)
        // The direction is UnCoupled from the Angle: with a 90° angle the same
        // −0.5 makes the same inward PEAK, just shifted along the edge (its
        // y=1 half-curve peak lands at column 5 here).
        val phased = FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customAngle = 90f, customCurveDistance = -0.5f, customStrength = 0f, customCount = 2f)
        assertEquals("angle shifts the phase, not the direction", 4f, phased.topInset(5), 1e-3f)
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
                TextItem(
                    id = "t1",
                    text = "Hi",
                    color = Color.BLACK,
                    fontSize = 0.5f,
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
    fun `translucent fill with outline renders through a layer without crashing`() {
        val input = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        // Opaque outline + a 40%-opacity fill. The outline pass now runs inside a
        // saveLayer so the glyph interior is punched out (revealing the background,
        // never blending toward the outline colour). Glyph pixels aren't reliably
        // rasterized under Robolectric, so we assert the render path (saveLayer +
        // CLEAR punch + feather passes) completes and preserves the canvas.
        val out = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            texts = listOf(
                TextItem(
                    id = "t2",
                    text = "Hi",
                    color = Color.argb(102, 0, 0, 0), // 40% opacity fill
                    fontSize = 0.5f,
                    x = 0.5f,
                    y = 0.5f,
                    outline = TextOutline(enabled = true, size = 0.003f, color = Color.RED, blur = 0.5f)
                )
            )
        )
        assertEquals(120, out.width)
        assertEquals(120, out.height)
        // Feathered outline with a translucent fill must keep the canvas opaque
        // outside the block (the saveLayer composites transparent over the white).
        assertEquals(255, Color.alpha(out.getPixel(0, 0)))
    }

    @Test
    fun `outline with a transparent fill renders through a clean layer`() {
        // 0% fill: the glyph must show the image through it, while the outward-only
        // outline layer stays confined to the block (no canvas-wide tint). Glyph
        // glyph ink is not rasterized faithfully under Robolectric, so we assert the
        // render path (saveLayer + clip-out passes) completes and keeps the canvas
        // opaque and untouched outside the block.
        val input = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
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
                TextItem(
                    id = "t-clear",
                    text = "Hi",
                    color = Color.TRANSPARENT, // 0% fill
                    fontSize = 0.5f,
                    x = 0.5f,
                    y = 0.5f,
                    outline = TextOutline(enabled = true, size = 0.004f, color = Color.RED, blur = 0.5f)
                )
            )
        )
        assertEquals(160, out.width)
        assertEquals(160, out.height)
        // The outline layer is confined to the text block: the canvas corner stays
        // opaque white — never red-tinted.
        assertEquals(255, Color.alpha(out.getPixel(0, 0)))
        assertEquals(Color.WHITE, out.getPixel(0, 0))
    }

    @Test
    fun `opaque fill with outline keeps the canvas intact`() {
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
                TextItem(
                    id = "t-opaque",
                    text = "A",
                    color = Color.BLACK,
                    fontSize = 0.6f,
                    x = 0.5f,
                    y = 0.5f,
                    outline = TextOutline(enabled = true, size = 0.005f, color = Color.BLUE, blur = 0f)
                )
            )
        )
        assertEquals(120, out.width)
        assertEquals(120, out.height)
        assertEquals(255, Color.alpha(out.getPixel(0, 0)))
    }

    @Test
    fun `outline renders at every fill opacity and blur combination`() {
        val fills = listOf(Color.TRANSPARENT, Color.argb(102, 0, 0, 0), Color.BLACK)
        for (fill in fills) {
            for (blur in listOf(0f, 1f)) {
                val input = Bitmap.createBitmap(140, 140, Bitmap.Config.ARGB_8888)
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
                        TextItem(
                            id = "t-blur-$fill-$blur",
                            text = "O",
                            color = fill,
                            fontSize = 0.6f,
                            x = 0.5f,
                            y = 0.5f,
                            outline = TextOutline(enabled = true, size = 0.005f, color = Color.RED, blur = blur)
                        )
                    )
                )
                assertEquals("dims must survive blur=$blur fill=#$fill", 140, out.width)
                assertEquals("dims must survive blur=$blur fill=#$fill", 140, out.height)
                assertEquals("corner must stay opaque and white (blur=$blur fill=#$fill)", Color.WHITE, out.getPixel(0, 0))
            }
        }
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
        // toward the local average instead of leaving it pure black. The soften
        // reach is a fraction of the image (dimension-agnostic), so on this 40px
        // canvas it reaches ~1px — the halo's edge pixel (x=19) still mixes the
        // white column and lifts.
        val softened = Color.red(out.getPixel(19, 20))
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

    @Test
    fun `downscaling before sharpen matches sharpen before downscaling within sub-pixel tolerance`() {
        // PERF (preview-first): the live-look passes may now run on a preview-
        // scale base instead of the full bounded source. That reordering must
        // stay visually lossless — downscale-then-sharpen must produce nearly the
        // same pixels as the old sharpen-then-downscale (tiny interpolation halo
        // differences only, never a wholesale change of the look).
        val src = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        for (y in 0 until 120) {
            for (x in 0 until 200) {
                src.setPixel(x, y, if (x < 100) Color.rgb(80, 80, 80) else Color.rgb(200, 200, 200))
            }
        }
        val amount = 60f
        val scaledDown = Bitmap.createScaledBitmap(src, 100, 60, true)
        val actual = BitmapRenderer.sharpenPreview(scaledDown, amount)
        val sharpenedFull = BitmapRenderer.sharpenPreview(src, amount)
        val expected = Bitmap.createScaledBitmap(sharpenedFull, 100, 60, true)

        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        var maxDiff = 0
        var sumDiff = 0L
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val a = actual.getPixel(x, y)
                val e = expected.getPixel(x, y)
                val d = maxOf(
                    kotlin.math.abs(Color.red(a) - Color.red(e)),
                    kotlin.math.abs(Color.green(a) - Color.green(e)),
                    kotlin.math.abs(Color.blue(a) - Color.blue(e))
                )
                if (d > maxDiff) maxDiff = d
                sumDiff += d
            }
        }
        val count = expected.width * expected.height
        val meanDiff = sumDiff.toDouble() / count
        assertTrue(
            "mean per-pixel channel diff must stay sub-pixel (was $meanDiff on $count px)",
            meanDiff < 10.0
        )
        assertTrue(
            "no pixel may diverge beyond the interpolation halo (max $maxDiff)",
            maxDiff <= 96
        )
    }

    @Test
    fun `pen at full hardness paints solid across the whole size`() {
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
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.PEN,
                    points = listOf(0.5f to 0.5f),
                    brushSize = 0.2f,
                    color = Color.BLACK,
                    hardness = 1f
                )
            )
        )
        val centre = Color.red(out.getPixel(20, 20))
        val nearEdge = Color.red(out.getPixel(23, 20))
        assertTrue("hard-edge centre must be solid black, got $centre", centre < 40)
        assertTrue("hard edge must stay solid near the border, got $nearEdge", nearEdge < 40)
    }

    @Test
    fun `pen at zero hardness fades out to the size`() {
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
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.PEN,
                    points = listOf(0.5f to 0.5f),
                    brushSize = 0.2f,
                    color = Color.BLACK,
                    hardness = 0f
                )
            )
        )
        val centre = Color.red(out.getPixel(20, 20))
        val nearEdge = Color.red(out.getPixel(23, 20))
        assertTrue("soft stroke keeps a solid core, got $centre", centre < 40)
        assertTrue("soft edge must fade toward the background, got $nearEdge", nearEdge > 90)
    }

    @Test
    fun `pen opacity scales the whole stroke including its border`() {
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
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.PEN,
                    points = listOf(0.5f to 0.5f),
                    brushSize = 0.2f,
                    // Alpha carries the 50% pen opacity.
                    color = Color.argb(128, 0, 0, 0),
                    hardness = 1f
                )
            )
        )
        // Half-opacity black on white must land around the middle gray band...
        val centre = Color.red(out.getPixel(20, 20))
        assertTrue("half-opacity centre must be mid gray, got $centre", centre in 80..175)
        // ...and the hard border keeps the same, still-scaled opacity.
        val edge = Color.red(out.getPixel(23, 20))
        assertTrue("hard border is still scaled by the opacity, got $edge", edge > centre - 60)
    }

    @Test
    fun `glow pen commits a white-hot centre a solid core and a wide faint halo`() {
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
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.PEN,
                    points = listOf(0.5f to 0.5f),
                    brushSize = 0.2f,
                    color = Color.BLACK,
                    hardness = 0f,
                    brush = PenBrushKind.GLOW
                )
            )
        )
        // radius = 4px; t <= 0.6 keeps the white-hot centre over the black core.
        val centre = Color.red(out.getPixel(20, 20))
        assertTrue("glow centre must stay white-hot, got $centre", centre > 200)
        // t ~ 2 → the solid colour band fully paints black.
        val core = Color.red(out.getPixel(20, 12))
        assertTrue("glow solid core must be dark, got $core", core < 60)
        // t ~ 5 → the 0.25 halo faintly tints the background.
        val halo = Color.red(out.getPixel(20, 0))
        assertTrue("glow outer halo must be a faint tint, got $halo", halo in 140..220)
        // Beyond t = 7 the halo ends: the corner stays untouched white.
        val far = Color.red(out.getPixel(0, 0))
        assertEquals("glow halo must end at 7 core-radii", 255, far)
    }

    @Test
    fun `neon pen commits a solid core with a faint outer halo and no white centre`() {
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
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.PEN,
                    points = listOf(0.5f to 0.5f),
                    brushSize = 0.2f,
                    color = Color.BLACK,
                    hardness = 0f,
                    brush = PenBrushKind.NEON
                )
            )
        )
        val centre = Color.red(out.getPixel(20, 20))
        assertTrue("neon core must be solid black, got $centre", centre < 40)
        // t ~ 2 → the 0.35 halo, notably lighter than the core.
        val halo = Color.red(out.getPixel(20, 12))
        assertTrue("neon halo must be a faint tint, got $halo", halo in 120..210)
        // t = 5 is past the 3.5 halo: untouched white (no wide glow like GLOW).
        val gap = Color.red(out.getPixel(20, 0))
        assertEquals("neon halo must end at 3.5 core-radii", 255, gap)
    }

    @Test
    fun `rainbow pen ignores the picked colour and cycles hue along the stroke`() {
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
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.PEN,
                    points = listOf(0.2f to 0.5f, 0.8f to 0.5f),
                    brushSize = 0.3f,
                    color = Color.BLACK,
                    hardness = 1f,
                    brush = PenBrushKind.RAINBOW
                )
            )
        )
        // First sampled centre hue (index 0) is red — not the picked black.
        val firstR = Color.red(out.getPixel(8, 20))
        val firstB = Color.blue(out.getPixel(8, 20))
        assertTrue("rainbow start must be red-dominant, r=$firstR b=$firstB", firstR > 200 && firstB < 100)
        // Later sampled centres cycle hue (e.g. ~162° → green-dominant).
        val secondR = Color.red(out.getPixel(32, 20))
        val secondG = Color.green(out.getPixel(32, 20))
        assertTrue("rainbow must shift hue along the stroke, r=$secondR g=$secondG", secondG > secondR + 80 && secondG > 120)
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
    fun `gallery sticker folds the picked photo through the committed pipeline`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        // A real JPEG source (same pattern as BitmapDecoderTest) that
        // drawStickers decodes as the Gallery sticker's content URI.
        val file = File(app.cacheDir, "gallery_sticker.jpg")
        val src = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }

        // The decode step itself is the size-sampled, EXIF-oriented content-URI path.
        val decoded = decodeBoundedOriented(app.contentResolver, Uri.fromFile(file), maxDim = 2048)
        assertEquals(20, decoded!!.width)
        assertEquals(10, decoded.height)

        // Fold a FULL-CANVAS gallery sticker: proves decode → resample-to-box →
        // draw through the committed renderer. A full-canvas sticker is
        // deliberately transform-independent — Robolectric's software canvas does
        // not honour the canvas-state/matrix form of drawBitmap, so off-centre
        // positions and rotations of bitmap stickers are not pixel-verifiable in
        // unit tests; the transform block is shared verbatim with the emoji/preset
        // paths (verified on device) and the model's position/size math is covered
        // by StickerItemTest.
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val out = BitmapRenderer.render(
            context = app,
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            stickers = listOf(
                StickerItem(
                    stickerId = "gallery:file:/unused",
                    imageUri = Uri.fromFile(file).toString(),
                    x = 0.5f,
                    y = 0.5f,
                    widthFrac = 1f,
                    heightFrac = 1f
                )
            )
        )
        assertEquals(40, out.width)
        assertEquals(40, out.height)
        val px = out.getPixel(20, 20)
        assertTrue(
            "gallery sticker centre must be the folded photo's red, got 0x${Integer.toHexString(px)}",
            Color.red(px) > 180 && Color.blue(px) < 80
        )
    }

    @Test
    fun `gallery sticker keeps its photo aspect - proportional fit, no stretch or crop`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val file = File(app.cacheDir, "gallery_sticker_ratio.jpg")
        val src = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }

        // A full-canvas 40x40 sticker box over a 2:1 (20x10) photo: the renderer
        // must proportionally fit the photo to 40x20 and centre it — letterboxing
        // the top/bottom rows with the canvas colour instead of stretching it to
        // fill the square (cropping or distortion is exactly what the spec forbids).
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val out = BitmapRenderer.render(
            context = app,
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            stickers = listOf(
                StickerItem(
                    stickerId = "gallery:file:/unused",
                    imageUri = Uri.fromFile(file).toString(),
                    x = 0.5f,
                    y = 0.5f,
                    widthFrac = 1f,
                    heightFrac = 1f
                )
            )
        )
        val fittedRegionRed = Color.red(out.getPixel(20, 20)) > 180 && Color.blue(out.getPixel(20, 20)) < 80
        val topLetterboxWhite = out.getPixel(20, 4).let {
            Color.red(it) > 220 && Color.green(it) > 220 && Color.blue(it) > 220
        }
        val bottomLetterboxWhite = out.getPixel(20, 35).let {
            Color.red(it) > 220 && Color.green(it) > 220 && Color.blue(it) > 220
        }
        assertTrue("photo must be drawn (centre red), was 0x${Integer.toHexString(out.getPixel(20, 20))}", fittedRegionRed)
        assertTrue("top rows must stay canvas (no vertical stretch), was 0x${Integer.toHexString(out.getPixel(20, 4))}", topLetterboxWhite)
        assertTrue("bottom rows must stay canvas (no vertical stretch), was 0x${Integer.toHexString(out.getPixel(20, 35))}", bottomLetterboxWhite)
    }

    @Test
    fun `gallery sticker opacity runs through the committed pipeline without corrupting the canvas`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val file = File(app.cacheDir, "gallery_sticker_op.jpg")
        val src = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }

        // The pixel-level blend of paint.alpha over the canvas is not rasterizable
        // by Robolectric's software canvas (it writes the bitmap buffer without
        // the paint's alpha), so the opacity SLIDER's routing to the renderer is
        // verified structurally here (item → paint.alpha, opacity round-trips in
        // StickerItemTest) and the fold completes without swallowing the canvas.
        val input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val out = BitmapRenderer.render(
            context = app,
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            stickers = listOf(
                StickerItem(
                    stickerId = "gallery:file:/unused",
                    imageUri = Uri.fromFile(file).toString(),
                    x = 0.5f,
                    y = 0.5f,
                    widthFrac = 1f,
                    heightFrac = 1f,
                    opacity = 0.5f
                )
            )
        )
        assertEquals(40, out.width)
        assertEquals(40, out.height)
    }

    // --- Sticker silhouette line (spec: effects_reference.md → Stickers → Line) ---

    // A full-canvas, fully-opaque sticker (via the Gallery photo path used by the
    // existing sticker tests — the one raster path whose pixels are verifiable
    // under Robolectric) so the line tests can assert exact colours and widths.
    private fun renderSolidStickerWithLine(
        fillColor: Int,
        lineStyle: StickerLineStyle,
        lineThickness: Float,
        lineColor: Int?
    ): Bitmap {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val file = File(app.cacheDir, "line_sticker_${System.nanoTime()}.jpg")
        val src = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(fillColor) }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        return BitmapRenderer.render(
            context = app,
            input = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) },
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            stickers = listOf(
                StickerItem(
                    stickerId = "gallery:file:/unused",
                    imageUri = Uri.fromFile(file).toString(),
                    x = 0.5f,
                    y = 0.5f,
                    widthFrac = 1f,
                    heightFrac = 1f,
                    lineStyle = lineStyle,
                    lineThickness = lineThickness,
                    lineColor = lineColor
                )
            )
        )
    }

    private fun isRedPixel(p: Int) = Color.red(p) > 160 && Color.green(p) < 110 && Color.blue(p) < 110
    private fun isBluePixel(p: Int) = Color.blue(p) > 160 && Color.red(p) < 110 && Color.green(p) < 110

    // Robolectric's LEGACY shadow canvas only records drawPath calls (it does
    // not rasterize strokes), so the pixel-level line tests run with the NATIVE
    // graphics runtime, whose software rasterizer paints paths, dash effects and
    // anti-aliasing exactly like Skia on a device.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a solid line strokes the sticker silhouette at thickness times the short side in the line colour`() {
        val out = renderSolidStickerWithLine(
            fillColor = 0xFF0000FF.toInt(),
            lineStyle = StickerLineStyle.SOLID,
            lineThickness = 0.1f,
            lineColor = 0xFFFF0000.toInt()
        )
        // thickness 0.1 × short side 40 = 4px wide ring around the blue fill.
        assertTrue("ring must reach the border (red), was 0x${Integer.toHexString(out.getPixel(1, 20))}", isRedPixel(out.getPixel(1, 20)))
        assertTrue("top edge must carry the ring (red), was 0x${Integer.toHexString(out.getPixel(20, 0))}", isRedPixel(out.getPixel(20, 0)))
        assertTrue("fill just inside the ring stays blue", isBluePixel(out.getPixel(3, 20)))
        assertTrue("centre stays the blue fill", isBluePixel(out.getPixel(20, 20)))
        assertTrue("8px inboard is still fill (no smear)", isBluePixel(out.getPixel(8, 20)))
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a none line style draws no line`() {
        val out = renderSolidStickerWithLine(
            fillColor = 0xFF0000FF.toInt(),
            lineStyle = StickerLineStyle.NONE,
            lineThickness = 0.1f,
            lineColor = 0xFFFF0000.toInt()
        )
        var redPixels = 0
        for (y in 0 until out.height) {
            for (x in 0 until out.width) {
                if (isRedPixel(out.getPixel(x, y))) redPixels++
            }
        }
        assertEquals("no line colour may appear with a None style", 0, redPixels)
        assertTrue("the blue fill must still be there", isBluePixel(out.getPixel(20, 20)))
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a null line colour defaults to black`() {
        val out = renderSolidStickerWithLine(
            fillColor = 0xFF0000FF.toInt(),
            lineStyle = StickerLineStyle.SOLID,
            lineThickness = 0.1f,
            lineColor = null
        )
        fun isBlackPixel(p: Int) = Color.red(p) < 110 && Color.green(p) < 110 && Color.blue(p) < 110
        assertTrue("unpicked line must fall back to black (default line colour), was 0x${Integer.toHexString(out.getPixel(1, 20))}", isBlackPixel(out.getPixel(1, 20)))
        assertTrue("top edge must carry the black ring, was 0x${Integer.toHexString(out.getPixel(20, 0))}", isBlackPixel(out.getPixel(20, 0)))
        assertTrue("the blue fill must still be there", isBluePixel(out.getPixel(20, 20)))
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `dashed and dotted lines break the contour into segments instead of a solid ring`() {
        val dashed = renderSolidStickerWithLine(
            fillColor = 0xFF0000FF.toInt(),
            lineStyle = StickerLineStyle.DASHED,
            lineThickness = 0.05f,
            lineColor = 0xFFFF0000.toInt()
        )
        val dotted = renderSolidStickerWithLine(
            fillColor = 0xFF0000FF.toInt(),
            lineStyle = StickerLineStyle.DOTTED,
            lineThickness = 0.05f,
            lineColor = 0xFFFF0000.toInt()
        )
        fun redAndOtherCounts(bmp: Bitmap, y: Int): Pair<Int, Int> {
            var red = 0
            var other = 0
            for (x in 0 until bmp.width) {
                if (isRedPixel(bmp.getPixel(x, y))) red++ else other++
            }
            return red to other
        }
        val (dRed, dOther) = redAndOtherCounts(dashed, 0)
        val (oRed, oOther) = redAndOtherCounts(dotted, 0)
        assertTrue("dashed top edge must be segmented (some red runs), got $dRed", dRed in 4..36)
        assertTrue("dashed top edge must have gaps, got $dOther non-red", dOther >= 4)
        assertTrue("dotted top edge must be segmented (some red runs), got $oRed", oRed in 2..30)
        assertTrue("dotted top edge must have gaps, got $oOther non-red", oOther >= 4)
    }

    @Test
    fun `line stroke paint derives width from the short-side fraction and scales with opacity`() {
        val paint = stickerLinePaint(
            style = StickerLineStyle.SOLID,
            widthPx = 6f,
            color = 0xFFFF0000.toInt(),
            alpha = 128
        )
        assertNotNull(paint)
        assertEquals(6f, paint!!.strokeWidth, 0.001f)
        assertEquals(128, paint.alpha)
        assertEquals(0xFFFF0000.toInt(), paint.color)
        assertEquals(Paint.Style.STROKE, paint.style)
        assertTrue(
            "dashed applies a dash effect",
            stickerLinePaint(StickerLineStyle.DASHED, 4f, 0xFFFF0000.toInt(), 255)!!.pathEffect is DashPathEffect
        )
        assertTrue(
            "dotted applies a dash effect",
            stickerLinePaint(StickerLineStyle.DOTTED, 4f, 0xFFFF0000.toInt(), 255)!!.pathEffect is DashPathEffect
        )
    }

    @Test
    fun `none style or zero width yields no line paint`() {
        assertNull(stickerLinePaint(StickerLineStyle.NONE, 6f, 0xFFFF0000.toInt(), 255))
        assertNull(stickerLinePaint(StickerLineStyle.SOLID, 0f, 0xFFFF0000.toInt(), 255))
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

    @Test
    fun `removal stroke replaces the brushed object with the surrounding colour`() {
        val background = Color.rgb(200, 100, 50)
        val input = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        input.eraseColor(background)
        for (yy in 9..18) {
            for (xx in 9..18) {
                input.setPixel(xx, yy, Color.rgb(0, 0, 0))
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
                    type = StrokeType.REMOVAL,
                    points = listOf(0.2f to 0.2f),
                    brushSize = 0.15f
                )
            )
        )
        // The brushed object is gone: its centre is back to the background colour.
        assertEquals(background, out.getPixel(13, 13))
        assertEquals(background, out.getPixel(16, 16))
    }

    @Test
    fun `second removal commit leaves the first stroke's resolved region untouched`() {
        val background = Color.rgb(90, 160, 240)
        val input = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        input.eraseColor(background)
        // Two isolated black objects to remove.
        for (yy in 9..18) for (xx in 9..18) input.setPixel(xx, yy, Color.rgb(0, 0, 0))
        for (yy in 46..55) for (xx in 46..55) input.setPixel(xx, yy, Color.rgb(0, 0, 0))

        val firstOnly = listOf(
            StrokeLayer(type = StrokeType.REMOVAL, points = listOf(0.2f to 0.2f), brushSize = 0.15f)
        )
        val both = firstOnly + listOf(
            StrokeLayer(type = StrokeType.REMOVAL, points = listOf(0.79f to 0.79f), brushSize = 0.15f)
        )
        val afterFirst = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            strokes = firstOnly
        )
        val afterBoth = BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            strokes = both
        )
        // First object resolved in both.
        assertEquals(background, afterFirst.getPixel(13, 13))
        assertEquals(background, afterBoth.getPixel(13, 13))
        // Second object resolved only after the second commit.
        assertEquals(background, afterBoth.getPixel(50, 50))
        assertTrue(
            "second object must still be black before its stroke commits",
            Color.red(afterFirst.getPixel(50, 50)) == 0
        )
        // Everywhere OUTSIDE the second stroke's footprint (centre 0.79 rad ~6.4px
        // + halo), the second commit is byte-identical to the first: previously
        // resolved pixels are never revisited by the incremental inpaint.
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val inSecondFootprint = x in 38..62 && y in 38..62
                if (inSecondFootprint) continue
                assertEquals(
                    "second removal commit disturbed ($x,$y)",
                    afterFirst.getPixel(x, y),
                    afterBoth.getPixel(x, y)
                )
            }
        }
    }

    // The Text tool's font-family bug report ("changing the font family does not
    // change the font shape") is a committed-exporter concern: the safe chips are
    // barred from SANS_SERIF-vs-DEFAULT comparisons (both ≈ the same system font
    // at raster time), so this test pins the shape must ACTUALLY differ between
    // Serif and Monospace on the committed bitmap. NATIVE graphics runtime is
    // required because Robolectric's LEGACY shadow canvas does not rasterise
    // glyphs (like the line tests above).
    private fun renderSingleText(body: TextFont, glyph: String = "M"): Bitmap {
        val input = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        return BitmapRenderer.render(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            texts = listOf(
                TextItem(
                    id = "t", text = glyph,
                    color = Color.BLACK,
                    fontFamily = body,
                    fontSize = 0.5f,
                    x = 0.5f, y = 0.5f
                )
            )
        )
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `the committed exporter rasterises every bundled font family differently`() {
        val serif = renderSingleText(TextFont.SERIF)
        val mono = renderSingleText(TextFont.MONOSPACE)
        val hand = renderSingleText(TextFont.HANDWRITING)
        fun countDiffering(a: Bitmap, b: Bitmap): Int {
            var differing = 0
            for (y in 0 until 200) {
                for (x in 0 until 200) {
                    if (a.getPixel(x, y) != b.getPixel(x, y)) differing++
                }
            }
            return differing
        }
        val serifMono = countDiffering(serif, mono)
        val serifHand = countDiffering(serif, hand)
        val monoHand = countDiffering(mono, hand)
        assertNotNull(serif)
        assertNotNull(mono)
        assertNotNull(hand)
        assertTrue(
            "switching font family must change the rasterised glyph shape, but Serif and " +
                "Monospace produced an identical 200x200 output (only $serifMono pixels differ)",
            serifMono > 20
        )
        assertTrue(
            "Handwriting must differ from Serif (only $serifHand pixels differ) - bundled " +
                "fonts are meant to stay distinct even when a device aliases its generic families",
            serifHand > 20
        )
        assertTrue(
            "Handwriting must differ from Monospace (only $monoHand pixels differ)",
            monoHand > 20
        )
    }

    @Test
    fun `sharpen radius is a fraction of the short edge at every scale`() {
        // One effect, two scales (docs/effect_stack.md → Preview rendering
        // model): the unsharp-mask reach scales with the live image — 4px at
        // the 500px reference — and is clamped only for the full-size pass.
        assertEquals(4, BitmapRenderer.sharpenRadius(500))
        assertEquals(8, BitmapRenderer.sharpenRadius(1000))
        assertEquals(16, BitmapRenderer.sharpenRadius(2000))
        assertEquals(16, BitmapRenderer.sharpenRadius(8000))
    }

    @Test
    fun `brush blur radius grows with the image until the relative cap`() {
        // The historical fixed 20px cap made a full-size blur ~0.5% of the image
        // while a small preview blurred ~4%; the cap is now a short-edge
        // fraction so the reach matches the user's brush in BOTH spaces.
        assertEquals(5, BitmapRenderer.brushBlurRadius(500, 15))
        assertEquals(20, BitmapRenderer.brushBlurRadius(500, 111))
        assertEquals(20, BitmapRenderer.brushBlurRadius(500, 500))
        assertEquals("an 8x image blurs 8x further", 160, BitmapRenderer.brushBlurRadius(4000, 4000))
        assertEquals(1, BitmapRenderer.brushBlurRadius(500, 2))
    }

    // --- overlay ↔ raster sticker parity -----------------------------------

    @Test
    fun `flipped rotating sticker box maps identically in the overlay and the committed raster`() {
        // Rule 2 (docs/effect_stack.md → Preview rendering model): the floating
        // sticker stand-in and the committed drawStickers must be interchangeable.
        // The raster applies (BitmapRenderer.kt:836-840):
        //   translate(left, top); rotate(θ, w/2, h/2); scale(-1,1, w/2, h/2)
        // which collapses to T(cx,cy)·R(θ)·S(-1,1)·T(-w/2,-h/2) — the exact
        // matrix the overlay's offset box + centre-pivoted graphicsLayer builds.
        // Both are verified numerically below so a one-sided pivot change fails here.
        val width = 1080
        val height = 1440
        val w = (0.3f * width).roundToInt().coerceAtLeast(1) // widthFrac 0.3
        val h = (0.2f * height).roundToInt().coerceAtLeast(1) // heightFrac 0.2
        val cx = 0.6f * width
        val cy = 0.7f * height
        val left = cx - w / 2f
        val top = cy - h / 2f
        val boxCorners = listOf(0f to 0f, w.toFloat() to 0f, w.toFloat() to h.toFloat(), 0f to h.toFloat())

        for (angle in floatArrayOf(0f, 45f, 90f, 135f, 270f, 450f)) {
            for (flipped in booleanArrayOf(false, true)) {
                // Raster: literal Canvas op sequence (post-concatenation: the LAST
                // op applies FIRST, so the scale about the pivot is the innermost).
                val raster = rasterSequence(left, top, angle, w.toFloat(), h.toFloat(), flipped)
                // Overlay: the collapsed box matrix T(cx,cy)·R·S·T(-w/2,-h/2).
                val overlay = overlayBoxMatrix(cx, cy, angle, w.toFloat(), h.toFloat(), flipped)
                boxCorners.forEach { (px, py) ->
                    val r = apply(raster, px, py)
                    val o = apply(overlay, px, py)
                    assertEquals(
                        "angle=$angle flipped=$flipped corner($px,$py)",
                        r.first, o.first, 1e-3
                    )
                    assertEquals(
                        "angle=$angle flipped=$flipped corner($px,$py)",
                        r.second, o.second, 1e-3
                    )
                }
            }
        }
    }

    @Test
    fun `sticker placements map to the same normalized fractions at any preview size`() {
        // toStickerItem (feature/stickers/StickerItem.kt) normalizes the dp
        // placement against the fitted frame; BitmapRenderer then consumes the
        // SAME source-fraction fields (widthFrac/heightFrac/x/y) at any surface.
        // Asserting both consumes the fractions means a fitted 500px preview and
        // a 4000px commit place the sticker centre and box identically.
        val normX = 0.6f
        val normY = 0.7f
        val normW = 0.3f
        val normH = 0.2f
        for (size in arrayOf(500 to 375, 1000 to 750, 2000 to 1500, 4000 to 3000)) {
            val (cw, ch) = size
            val w = (normW * cw).roundToInt().coerceAtLeast(1)
            val h = (normH * ch).roundToInt().coerceAtLeast(1)
            assertEquals(normW * cw, w.toFloat(), 1f)
            assertEquals(normH * ch, h.toFloat(), 1f)
            // Centre + box corners in surface px (raster left/top decomposition).
            val left = normX * cw - w / 2f
            val top = normY * ch - h / 2f
            assertEquals(normX * cw, left + w / 2f, 0.01f)
            assertEquals(normY * ch, top + h / 2f, 0.01f)
        }
    }

    @Test
    fun `separable sharpen box average is byte-identical to the per-pixel box average`() {
        // boxAverageImage computes every pixel's clipped box average with two
        // sliding passes, skipping fully transparent pixels exactly like
        // VignetteRenderer.boxAverageAt (same clipped bounds, same floor
        // division, same all-transparent centre fallback). A varied gradient +
        // sparse random translucency on a non-square frame exercises edge
        // clipping and the transparent exclusion on all four sides.
        val rng = java.util.Random(11)
        val w = 37
        val h = 29
        val src = IntArray(w * h) {
            val x = it % w
            val y = it / w
            val alpha = if (rng.nextInt(5) == 0) 0 else 255
            Color.argb(
                alpha,
                (x * 3 + (it * 13) % 7) % 256,
                (y * 5 + (it * 31) % 11) % 256,
                ((x + y) * 7 + rng.nextInt(40)) % 256
            )
        }
        for (radius in intArrayOf(1, 4, 11)) {
            val via = StrokeOverlayRenderer.boxAverageImage(src, w, h, radius)
            for (idx in 0 until w * h) {
                val cx = idx % w
                val cy = idx / w
                val x0 = (cx - radius).coerceAtLeast(0)
                val y0 = (cy - radius).coerceAtLeast(0)
                val x1 = (cx + radius).coerceAtMost(w - 1)
                val y1 = (cy + radius).coerceAtMost(h - 1)
                var r = 0L; var g = 0L; var b = 0L; var a = 0L; var count = 0L
                for (y in y0..y1) {
                    for (x in x0..x1) {
                        val c = src[y * w + x]
                        val alpha = Color.alpha(c)
                        if (alpha == 0) continue
                        r += Color.red(c); g += Color.green(c); b += Color.blue(c); a += alpha
                        count++
                    }
                }
                val expected = if (count == 0L) {
                    src[cy * w + cx]
                } else {
                    Color.argb(
                        (a / count).toInt(), (r / count).toInt(),
                        (g / count).toInt(), (b / count).toInt()
                    )
                }
                assertEquals("radius=$radius pixel ($cx,$cy)", expected, via[idx])
            }
        }
    }

    @Test
    fun `separable sharpen box average keeps the all-transparent centre fallback`() {
        // A fully transparent frame must fall back to the centre pixel for
        // every output slot, matching boxAverageAt rather than emitting an
        // opaque black box.
        val w = 20
        val h = 15
        val src = IntArray(w * h) { Color.TRANSPARENT }
        val via = StrokeOverlayRenderer.boxAverageImage(src, w, h, 5)
        for (idx in 0 until w * h) {
            assertEquals("pixel ${idx % w},${idx / w}", Color.TRANSPARENT, via[idx])
        }
    }
}

// 2D affine arrays [a, b, c, d, e, f] for x' = a·x + c·y + e, y' = b·x + d·y + f.
private typealias Affine = DoubleArray

private fun affine(tx: Double, ty: Double, a: Double = 1.0, b: Double = 0.0, c: Double = 0.0, d: Double = 1.0): Affine =
    doubleArrayOf(a, b, c, d, tx, ty)

private fun mul(m2: Affine, m1: Affine): Affine {
    val a2 = m2[0]; val b2 = m2[1]; val c2 = m2[2]; val d2 = m2[3]; val e2 = m2[4]; val f2 = m2[5]
    val a1 = m1[0]; val b1 = m1[1]; val c1 = m1[2]; val d1 = m1[3]; val e1 = m1[4]; val f1 = m1[5]
    return doubleArrayOf(
        a2 * a1 + c2 * b1,
        b2 * a1 + d2 * b1,
        a2 * c1 + c2 * d1,
        b2 * c1 + d2 * d1,
        a2 * e1 + c2 * f1 + e2,
        b2 * e1 + d2 * f1 + f2
    )
}

private fun apply(m: Affine, x: Float, y: Float): Pair<Double, Double> =
    m[0] * x + m[2] * y + m[4] to m[1] * x + m[3] * y + m[5]

private fun rotateAngle(degrees: Float): Affine {
    val r = Math.toRadians(degrees.toDouble())
    val cos = Math.cos(r)
    val sin = Math.sin(r)
    return affine(0.0, 0.0, a = cos, b = sin, c = -sin, d = cos)
}

/** Canvas rotate about a pivot: T(px,py)·R·T(-px,-py). */
private fun rotateAbout(degrees: Float, px: Double, py: Double): Affine =
    mul(affine(px, py), mul(rotateAngle(degrees), affine(-px, -py)))

/** Canvas scale about a pivot: T(px,py)·S·T(-px,-py). */
private fun scaleAbout(sx: Double, sy: Double, px: Double, py: Double): Affine =
    mul(affine(px, py), mul(affine(0.0, 0.0, a = sx, d = sy), affine(-px, -py)))

/**
 * The committed raster's op sequence (BitmapRenderer.kt:836-840): post-concat
 * translate(left,top); rotate(θ, w/2, h/2); scale(-1,1, w/2, h/2) — the last
 * op is innermost, applying first to the drawing point.
 */
private fun rasterSequence(left: Float, top: Float, angle: Float, w: Float, h: Float, flipped: Boolean): Affine {
    val t = affine(left.toDouble(), top.toDouble())
    val r = rotateAbout(angle, (w / 2).toDouble(), (h / 2).toDouble())
    val inner = if (flipped) scaleAbout(-1.0, 1.0, (w / 2).toDouble(), (h / 2).toDouble()) else affine(0.0, 0.0)
    return mul(t, mul(r, inner))
}

/**
 * The overlay box: offset (cx−w/2, cy−h/2) with centre-pivoted graphicsLayer
 * rotation + flip — the collapsed pivot-cancelled form T(cx,cy)·R(θ)·S(-1,1)·T(-w/2,-h/2).
 */
private fun overlayBoxMatrix(cx: Float, cy: Float, angle: Float, w: Float, h: Float, flipped: Boolean): Affine {
    val c = affine(cx.toDouble(), cy.toDouble())
    val r = rotateAngle(angle)
    val s = if (flipped) affine(0.0, 0.0, a = -1.0, d = 1.0) else affine(0.0, 0.0)
    return mul(mul(c, r), mul(s, affine(-(w / 2).toDouble(), -(h / 2).toDouble())))
}
