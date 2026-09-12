package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.feature.editor.ui.text.TextOverlay
import codes.pepper.whimsicalart.feature.editor.ui.text.toTextItem
import codes.pepper.whimsicalart.feature.editor.ui.mosaic.pixelMosaicCellPx
import codes.pepper.whimsicalart.feature.stickers.domain.StickerItem
import codes.pepper.whimsicalart.feature.stickers.domain.StickerLineStyle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * FOLD CACHING (tracker): per-fold overlay artefacts are cached so an unchanged
 * stack does not re-decode/re-rasterise work on every fold.
 *  - StickerOverlayRenderer: decoded+scaled sticker art, keyed by
 *    (stickerId, box size) — tint/opacity/rotation/flip are draw-time transforms
 *    and therefore NOT part of the key.
 *  - TextOverlayRenderer: the rasterised text block, keyed by every
 *    content-affecting parameter + target size — position/rotation are
 *    composite-time transforms and NOT part of the key.
 *  - StrokeOverlayRenderer: one shared pre-mosaic snapshot for every mosaic
 *    stroke instead of a full-canvas copy per stroke.
 */
@RunWith(RobolectricTestRunner::class)
class EditorOverlayCacheTest {

    private val context get() = org.robolectric.RuntimeEnvironment.getApplication()

    private fun sticker(width: Float, height: Float) = StickerItem(
        stickerId = "emoji:\uD83D\uDE00",
        emojiText = "\uD83D\uDE00",
        x = 0.5f,
        y = 0.5f,
        widthFrac = width,
        heightFrac = height
    )

    private fun text(id: String, textValue: String, color: Int = Color.RED, fontSize: Float = 0.06f) =
        TextItem(
            id = id,
            text = textValue,
            color = color,
            fontSize = fontSize,
            x = 0.5f,
            y = 0.5f
        )

    @Test
    fun `repeated folds reuse the cached sticker art and only a new box re-decodes`() {
        StickerOverlayRenderer.clearStickerCache()
        val s = sticker(0.3f, 0.4f)
        val a = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        StickerOverlayRenderer.drawStickers(context, a, listOf(s))
        val afterFirst = StickerOverlayRenderer.stickerDecodeCount
        assertTrue("first draw must rasterise the sticker once", afterFirst == 1)

        StickerOverlayRenderer.drawStickers(context, a, listOf(s))
        assertEquals("same sticker + same box on the next fold reuses the art", afterFirst, StickerOverlayRenderer.stickerDecodeCount)

        val b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        StickerOverlayRenderer.drawStickers(context, b, listOf(s))
        assertTrue("a different box size is a fresh key and re-decodes", StickerOverlayRenderer.stickerDecodeCount > afterFirst)
    }

    @Test
    fun `tint opacity and rotation of an unchanged sticker do not re-rasterise`() {
        StickerOverlayRenderer.clearStickerCache()
        val s = sticker(0.3f, 0.3f)
        val a = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        StickerOverlayRenderer.drawStickers(context, a, listOf(s))
        val afterFirst = StickerOverlayRenderer.stickerDecodeCount

        val restyled = s.copy(rotationDegrees = 45f, opacity = 0.5f, tintColor = Color.BLUE, isFlipped = true)
        StickerOverlayRenderer.drawStickers(context, a, listOf(restyled))
        assertEquals("draw-time transforms must hit the same cached art", afterFirst, StickerOverlayRenderer.stickerDecodeCount)
    }

    @Test
    fun `clearStickerCache resets the decode counter`() {
        StickerOverlayRenderer.clearStickerCache()
        val s = sticker(0.3f, 0.3f)
        val a = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        StickerOverlayRenderer.drawStickers(context, a, listOf(s))
        assertTrue(StickerOverlayRenderer.stickerDecodeCount >= 1)

        StickerOverlayRenderer.clearStickerCache()
        assertEquals(0, StickerOverlayRenderer.stickerDecodeCount)
    }

    @Test
    fun `line-style sticker folds reuse the cached silhouette instead of re-tracing`() {
        StickerOverlayRenderer.clearStickerCache()
        val s = sticker(0.3f, 0.3f).copy(
            lineStyle = StickerLineStyle.SOLID,
            lineThickness = 0.05f,
            lineColor = Color.RED
        )
        val a = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        StickerOverlayRenderer.drawStickers(context, a, listOf(s))
        val traceCount = StickerOverlayRenderer.stickerContourCount
        assertEquals("first line-style draw must trace the art once", 1, traceCount)

        StickerOverlayRenderer.drawStickers(context, a, listOf(s))
        assertEquals(
            "an unchanged line-style sticker on the next fold reuses the traced contour",
            traceCount,
            StickerOverlayRenderer.stickerContourCount
        )

        val resized = s.copy(widthFrac = 0.5f, heightFrac = 0.5f)
        StickerOverlayRenderer.drawStickers(context, a, listOf(resized))
        assertTrue(
            "a new box size re-decodes the art and must re-trace",
            StickerOverlayRenderer.stickerContourCount > traceCount
        )

        val noLine = s.copy(lineStyle = StickerLineStyle.NONE)
        val before = StickerOverlayRenderer.stickerContourCount
        StickerOverlayRenderer.drawStickers(context, a, listOf(noLine))
        assertEquals("a None line style never traces a contour", before, StickerOverlayRenderer.stickerContourCount)
    }

    private fun assertPixelsEqual(label: String, a: Bitmap, b: Bitmap) {
        assertEquals(label, a.width, b.width)
        assertEquals(label, a.height, b.height)
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        assertArrayEquals(label, pa, pb)
    }

    @Test
    fun `blur text cache-miss measures once and later folds reuse the cached measure`() {
        TextOverlayRenderer.clearTextCache()
        val item = text("t1", "Blurred").copy(
            background = TextBackgroundShape.BLUR,
            backgroundBlur = 1f
        )
        val a = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        TextOverlayRenderer.drawTexts(context, a, listOf(item))
        val measures = TextOverlayRenderer.textMeasureCount
        val rasters = TextOverlayRenderer.textRasterCount
        // The BLUR oval used to re-measure inside drawBlurBackground on a cache
        // miss (measure twice for one block); now the raster's measurement is
        // handed to the blur path, so a fresh BLUR item measures exactly once.
        assertEquals("a cache-miss BLUR item measures once, not twice", 1, measures)
        assertEquals("the missing block rasterises once", 1, rasters)

        val b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        TextOverlayRenderer.drawTexts(context, b, listOf(item))
        assertEquals("second fold must not re-measure", measures, TextOverlayRenderer.textMeasureCount)
        assertEquals("second fold must not re-rasterise", rasters, TextOverlayRenderer.textRasterCount)

        val c = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        TextOverlayRenderer.drawTexts(context, c, listOf(item))
        assertEquals("third fold must not re-measure", measures, TextOverlayRenderer.textMeasureCount)

        // The cached measure feeds the BLUR repaint on every fresh canvas, so a
        // fold drawn from the cache must paint the identical photo blur as the
        // original rasterisation fold.
        assertPixelsEqual("fold 2 (cached measure) vs fold 1 (fresh measure)", a, b)
        assertPixelsEqual("fold 3 (cached measure) vs fold 1 (fresh measure)", a, c)
    }

    private fun pixels(bmp: Bitmap): IntArray {
        val p = IntArray(bmp.width * bmp.height)
        bmp.getPixels(p, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return p
    }

    @Test
    fun `unchanged text folds reuse the rasterised block and moves do not re-rasterise`() {
        TextOverlayRenderer.clearTextCache()
        val item = text("t1", "Hello")
        val a = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        TextOverlayRenderer.drawTexts(context, a, listOf(item))
        val afterFirst = TextOverlayRenderer.textRasterCount
        assertEquals("first draw must rasterise the block once", 1, afterFirst)

        TextOverlayRenderer.drawTexts(context, a, listOf(item))
        assertEquals("same item on the next fold recomposites the cached block", afterFirst, TextOverlayRenderer.textRasterCount)

        val moved = item.copy(x = 0.3f, y = 0.6f, rotationDegrees = 30f)
        TextOverlayRenderer.drawTexts(context, a, listOf(moved))
        assertEquals("position and rotation are composite-time transforms, not cache keys", afterFirst, TextOverlayRenderer.textRasterCount)
    }

    @Test
    fun `changed text content re-rasterises and a new target size is a fresh key`() {
        TextOverlayRenderer.clearTextCache()
        val item = text("t1", "Hello")
        val a = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        TextOverlayRenderer.drawTexts(context, a, listOf(item))
        val afterFirst = TextOverlayRenderer.textRasterCount

        val recoloured = item.copy(color = Color.BLUE)
        TextOverlayRenderer.drawTexts(context, a, listOf(recoloured))
        assertTrue("a changed colour changes the pixels and must re-rasterise", TextOverlayRenderer.textRasterCount > afterFirst)

        val b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        TextOverlayRenderer.drawTexts(context, b, listOf(item))
        assertTrue("the block geometry scales with the target size, so a new size re-rasterises", TextOverlayRenderer.textRasterCount > afterFirst)
    }

@Test
    fun `clearTextCache resets the raster counter`() {
        TextOverlayRenderer.clearTextCache()
        TextOverlayRenderer.drawTexts(context, Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888), listOf(text("t1", "Hi")))
        assertTrue(TextOverlayRenderer.textRasterCount >= 1)

        TextOverlayRenderer.clearTextCache()
        assertEquals(0, TextOverlayRenderer.textRasterCount)
    }

    // --- Text pinch-scale parity (tracker → next.md: text/sticker pinch-scale
    //     commit mismatch) ----------------------------------------------

    @Test
    fun `a pinch-scaled text item reuses the scale-free block and never re-rasterises`() {
        // scale² regression guard (tracker → next.md: text pinch-scale commit
        // mismatch): the committed path used to bake t.scale into the measured
        // textSize AND draw under draw.scale — the live overlay scales once, so
        // committed ≠ canvas. The block now measures unscaled and composites
        // under ONE draw.scale: both scales must share the same single raster.
        TextOverlayRenderer.clearTextCache()
        val base = text("t", "Scaled", fontSize = 0.2f)
        val a = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        TextOverlayRenderer.drawTexts(context, a, listOf(base.copy(scale = 1f)))
        val afterFirst = TextOverlayRenderer.textRasterCount
        TextOverlayRenderer.drawTexts(context, b, listOf(base.copy(scale = 1.5f)))
        assertEquals("scale is composite-time, the scaled item must not re-rasterise", afterFirst, TextOverlayRenderer.textRasterCount)
        assertTrue("the scale-1 item must actually draw ink", inkBoundingBox(a).isEmpty.not())
        assertTrue("the scale-1.5 item must actually draw ink", inkBoundingBox(b).isEmpty.not())
    }

    @Test
    fun `pinch-rescaling a text item does not re-rasterise the block`() {
        TextOverlayRenderer.clearTextCache()
        val item = text("t1", "Hello", fontSize = 0.15f)
        val a = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        TextOverlayRenderer.drawTexts(context, a, listOf(item))
        val afterFirst = TextOverlayRenderer.textRasterCount

        TextOverlayRenderer.drawTexts(context, a, listOf(item.copy(scale = 1.7f)))
        assertEquals(
            "scale is a composite-time transform and not part of the block raster",
            afterFirst, TextOverlayRenderer.textRasterCount
        )
    }

    @Test
    fun `blur background oval is scale-invariant and centred on the item`() {
        val base = text("t", "Oval", fontSize = 0.2f).copy(
            background = TextBackgroundShape.BLUR,
            backgroundBlur = 1f
        )
        // The oval is measured at the UNSCALED size and composited under the
        // SAME draw.scale frame as the glyph block — so every pinch-scale shares
        // one rect and the painted oval always spans the scaled block (never one
        // scaled and the other not).
        val c1 = TextOverlayRenderer.blurCoverRect(context, base.copy(scale = 1f), 200f, 200f)
        val c15 = TextOverlayRenderer.blurCoverRect(context, base.copy(scale = 1.5f), 200f, 200f)
        assertEquals(c1.left, c15.left, 0.01f)
        assertEquals(c1.top, c15.top, 0.01f)
        assertEquals(c1.right, c15.right, 0.01f)
        assertEquals(c1.bottom, c15.bottom, 0.01f)
        // Centred on the item's (normalized) position, symmetric about it.
        assertEquals(200f * 0.5f, c15.centerX(), 1f)
        assertEquals(200f * 0.5f, c15.centerY(), 1f)
        assertEquals(c15.centerX() - c15.left, c15.right - c15.centerX(), 0.01f)
        assertEquals(c15.centerY() - c15.top, c15.bottom - c15.centerY(), 0.01f)
        assertTrue("the oval must enclose the glyph block: width ${c15.width()}", c15.width() > 2f)
        assertTrue("the oval must enclose the glyph block: height ${c15.height()}", c15.height() > 2f)
    }

    @Test
    fun `scaled blur oval equals blurCoverRect in the item's scale frame`() {
        // Scale parity (tracker → next.md: SCALED text layer commits a WRONG
        // background). The committed oval is [scaledBlurCoverRect]: blurCoverRect
        // transformed by t.scale about the block centre — NEVER the unscaled
        // cover, and never the cover scaled a second time by a second canvas
        // scale. Scale 1 is identity; every other scale grows the rect about the
        // item centre by exactly t.scale.
        val base = text("t", "Oval", fontSize = 0.2f).copy(
            background = TextBackgroundShape.BLUR,
            backgroundBlur = 1f
        )
        val cover = TextOverlayRenderer.blurCoverRect(context, base, 200f, 200f)
        val identity = TextOverlayRenderer.scaledBlurCoverRect(context, base.copy(scale = 1f), 200f, 200f)
        assertEquals("scale 1 scaled cover is the unscaled cover (left)", cover.left, identity.left, 0.01f)
        assertEquals("scale 1 scaled cover is the unscaled cover (top)", cover.top, identity.top, 0.01f)
        assertEquals("scale 1 scaled cover is the unscaled cover (right)", cover.right, identity.right, 0.01f)
        assertEquals("scale 1 scaled cover is the unscaled cover (bottom)", cover.bottom, identity.bottom, 0.01f)

        val scaled = TextOverlayRenderer.scaledBlurCoverRect(context, base.copy(scale = 1.5f), 200f, 200f)
        // Same centre (the item's normalized position), symmetric.
        assertEquals("scaled oval stays centred on the item X", cover.centerX(), scaled.centerX(), 0.01f)
        assertEquals("scaled oval stays centred on the item Y", cover.centerY(), scaled.centerY(), 0.01f)
        assertEquals("scaled half-width is the cover half-width × scale", cover.width() / 2f * 1.5f, scaled.width() / 2f, 0.1f)
        assertEquals("scaled half-height is the cover half-height × scale", cover.height() / 2f * 1.5f, scaled.height() / 2f, 0.1f)
        assertEquals("scaled width = cover width × scale", cover.width() * 1.5f, scaled.width(), 0.1f)
        assertEquals("scaled height = cover height × scale", cover.height() * 1.5f, scaled.height(), 0.1f)
        assertTrue("scaled oval is strictly larger than the unscaled cover", scaled.width() > cover.width())
    }

    @Test
    fun `a scaled blur item commits the oval at its scaled window - never at the unscaled size`() {
        // Pixel-level scale parity: the committed path paints the feathered oval
        // straight from [scaledBlurCoverRect] (scale baked into the geometry —
        // NO canvas scale), so the blurred band on the committed photo spans
        // scaledCover ± feather, exactly tracking the scaled glyph block.
        val w = 600
        val h = 400
        val base = text("p", "Parity", fontSize = 0.09f).copy(
            background = TextBackgroundShape.BLUR,
            backgroundBlur = 0.6f
        )

        fun blurBand(scale: Float): Pair<Int, Int> {
            val item = base.copy(scale = scale)
            TextOverlayRenderer.clearTextCache()
            val src = gradientPhoto(w, h)
            val out = src.copy(Bitmap.Config.ARGB_8888, true)
            TextOverlayRenderer.drawTexts(context, out, listOf(item))
            val cy = 200
            var first = -1
            var last = -1
            for (x in 0 until w) {
                if (out.getPixel(x, cy) != src.getPixel(x, cy)) {
                    if (first < 0) first = x
                    last = x
                }
            }
            return first to last
        }

        for (scale in listOf(1f, 1.5f, 2f)) {
            val item = base.copy(scale = scale)
            val scaled = TextOverlayRenderer.scaledBlurCoverRect(context, item, w.toFloat(), h.toFloat())
            val feather = (scaled.height() * 0.08f).coerceIn(3f, 32f)
            val expectedFirst = (scaled.left - feather).roundToInt()
            val expectedLast = (scaled.right + feather).roundToInt()
            val (first, last) = blurBand(scale)
            assertEquals("scale=$scale oval begins at scaledCover − feather (left)",
                expectedFirst.toDouble(), first.toDouble(), 3.0)
            assertEquals("scale=$scale oval ends at scaledCover + feather (right)",
                expectedLast.toDouble(), last.toDouble(), 3.0)
        }

        // The band GROWS with scale (the whole point) — a doubled scale must span
        // ~double the unscaled band.
        val (f1, l1) = blurBand(1f)
        val (f2, l2) = blurBand(2f)
        assertTrue("scale 2 band is far wider than the scale 1 band: [$f1,$l1] vs [$f2,$l2]", (l2 - f2) > (l1 - f1) * 1.5f)
    }

    private fun gradientPhoto(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            val px = IntArray(w * h) { i ->
                val c = (i % w).toFloat() / (w - 1)
                Color.rgb((c * 255).toInt(), 128, 255 - (c * 255).toInt())
            }
            setPixels(px, 0, w, 0, 0, w, h)
        }

    private fun inkBoundingBox(bmp: Bitmap): Rect {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                if (bmp.getPixel(x, y) != Color.WHITE) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        check(maxX >= minX && maxY >= minY) { "no ink found" }
        return Rect(minX, minY, maxX, maxY)
    }

    @Test
    fun `mosaic cells sample the single shared pre-mosaic snapshot`() {
        // 40x40 horizontal grey ramp: colour at column x is proportional to x, so
        // every cell of a different window has a different average — this is the
        // regime that distinguishes the pre-mosaic snapshot from a cumulative copy.
        val w = 40
        val input = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
        for (y in 0 until w) {
            for (x in 0 until w) {
                input.setPixel(x, y, Color.rgb(x * 255 / w, x * 255 / w, x * 255 / w))
            }
        }
        // Two mosaic strokes on the same row. The second brush's cell (x 10..22
        // after half-cell offseting) overlaps the first's painted cell (x 2..14):
        // under a cumulative copy it would read the first stroke's uniform fill,
        // under the shared snapshot it averages the ORIGINAL ramp (x 10..21).
        val out = BitmapRenderer.render(
            context = context,
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            strokes = listOf(
                StrokeLayer(type = StrokeType.MOSAIC, points = listOf(0.2f to 0.5f), brushSize = 0.3f),
                StrokeLayer(type = StrokeType.MOSAIC, points = listOf(0.4f to 0.5f), brushSize = 0.3f)
            )
        )
        // NOTE on assertion site: Robolectric's software-canvas readback only
        // reliably reports cells along the drawn rect's top/bottom band rows
        // (interior rows come back stale); the top band y=14 is the full cell
        // width on every stroke, so the snapshot signal is read there.
        val snapshotAverage = (10 + 11 + 12 + 13 + 14 + 15 + 16 + 17 + 18 + 19 + 20 + 21) * 255 / w / 12
        val snapshotBandColor = out.getPixel(16, 14)
        assertEquals(
            "the second stroke averages the pre-mosaic snapshot (colour=$snapshotAverage)",
            Color.rgb(snapshotAverage, snapshotAverage, snapshotAverage),
            snapshotBandColor
        )
        // The old cumulative path read stroke1's already-painted cell, so it
        // would have produced a DIFFERENT (blended) colour at this spot — proving
        // the shared snapshot really is the pre-mosaic source, not the baked one.
        val cumulativeColour = oldPathControl(input, w).getPixel(16, 14)
        assertTrue(
            "a cumulative copy must differ from the snapshot fill (cumulative=$cumulativeColour, snapshot=$snapshotBandColor)",
            Color.red(cumulativeColour) != Color.red(snapshotBandColor)
        )
    }

    @Test
    fun `mosaic integral-image averages are byte-identical to the per-window blockAverage control`() {
        // PERF tracker (mosaic block-average via integral image): the O(1)
        // per-cell average read from the per-stroke R/G/B prefix-sum must
        // reproduce StrokeOverlayRenderer.blockAverage's exact colour for every
        // window, including the clipped right/bottom edge cells that sweep off
        // the frame (floor division + integer sums are grouping-exact, so the
        // swap must never change a pixel).
        val w = 40
        val src = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
        for (y in 0 until w) {
            for (x in 0 until w) {
                src.setPixel(x, y, Color.rgb(x * 255 / w, y * 255 / w, (x * 3 + y * 5) % 256))
            }
        }
        val pixels = IntArray(w * w)
        src.getPixels(pixels, 0, w, 0, 0, w, w)
        src.recycle()
        val integral = MosaicIntegral(pixels, w, w)
        for (cell in intArrayOf(1, 4, 12, 20, 40)) {
            for (x0 in intArrayOf(0, 5, w - cell, w - cell + 1, w - 1)) {
                for (y0 in intArrayOf(0, 7, w - cell, w - cell + 1, w - 1)) {
                    val fast = StrokeOverlayRenderer.integralAverage(integral, w, w, x0, y0, cell, cell)
                    val control = StrokeOverlayRenderer.blockAverage(pixels, w, w, x0, y0, cell, cell)
                    assertEquals(
                        "cell=$cell window ($x0,$y0) must match the per-window scan",
                        control, fast
                    )
                }
            }
        }
    }

    @Test
    fun `mosaic integral prepass triggers only for a frame-filling stamp footprint`() {
        // Footprint gating: a tiny tap must NOT pay the O(w·h) prepass (the few
        // per-window scans are cheaper), while a long fat-trail stroke that would
        // re-scan the whole frame should switch to the integral image.
        val small = listOf(0.5f to 0.5f)
        assertEquals(
            "a one-tap tiny-stamp stroke keeps the per-window scan",
            false, StrokeOverlayRenderer.useMosaicIntegral(400, 300, 12, small)
        )
        val longTrail = listOf(0f to 0.5f, 1f to 0.5f, 0f to 0.5f, 1f to 0.5f)
        assertEquals(
            "a long trail with a fat brush pays for the prepass",
            true, StrokeOverlayRenderer.useMosaicIntegral(400, 300, 60, longTrail)
        )
        val fullFrameCell = listOf(0.5f to 0.5f)
        assertEquals(
            "a single full-frame cell is a cost wash - it stays on the scan",
            false, StrokeOverlayRenderer.useMosaicIntegral(400, 300, 400, fullFrameCell)
        )
        // Beyond the memory cap even a frame-filling trail falls back.
        assertEquals(
            "a 4000px-wide frame exceeds the integral footprint budget",
            false, StrokeOverlayRenderer.useMosaicIntegral(4000, 3000, 200, longTrail)
        )
    }

    /** In-test replication of the OLD copy-per-stroke mosaic path, as a control. */
    private fun oldPathControl(input: Bitmap, w: Int): Bitmap {
        val result = Bitmap.createBitmap(input)
        for ((px, py) in listOf(0.2f to 0.5f, 0.4f to 0.5f)) {
            val src = result.copy(Bitmap.Config.ARGB_8888, false)
            val cell = 12
            val x0 = (px * w - cell / 2f).roundToInt()
            val y0 = (py * w - cell / 2f).roundToInt()
            val fill = blockAverageControl(src, x0, y0, cell, cell)
            android.graphics.Canvas(result).drawRect(
                x0.toFloat(), y0.toFloat(), (x0 + cell).toFloat(), (y0 + cell).toFloat(),
                android.graphics.Paint().apply {
                    color = fill
                    alpha = 255
                }
            )
            src.recycle()
        }
        return result
    }

    private fun blockAverageControl(src: Bitmap, x0: Int, y0: Int, w: Int, h: Int): Int {
        var r = 0L
        var count = 0L
        for (y in y0 until (y0 + h).coerceAtMost(src.height)) {
            for (x in x0 until (x0 + w).coerceAtMost(src.width)) {
                r += Color.red(src.getPixel(x, y))
                count++
            }
        }
        return Color.rgb((r / count).toInt(), (r / count).toInt(), (r / count).toInt())
    }

    @Test
    fun `ROUNDED corner radius is identical live and committed at any font size`() {
        // The live preview clips with (fontSize * ROUNDED_BACKGROUND_CORNER_FACTOR).dp,
        // i.e. px = fontSizePx * FACTOR; toTextItem normalizes fontSize to a fraction
        // of the frame and the committed rasteriser takes textSize = fontSizeFrac *
        // frameW, again * FACTOR. Both must reduce to the same px value, so preview
        // curvature and committed curvature can never drift apart.
        for (density in floatArrayOf(1f, 1.75f, 3f)) {
            for (fontDp in floatArrayOf(12f, 24f, 48f, 96f)) {
                val d = Density(density)
                val liveRadiusPx = with(d) { (fontDp * ROUNDED_BACKGROUND_CORNER_FACTOR).dp.toPx() }

                val overlay = TextOverlay(
                    id = "r",
                    text = "Rounded",
                    position = Offset(0.5f, 0.5f),
                    fontSize = fontDp,
                    backgroundShape = TextBackgroundShape.ROUNDED
                )
                val item = overlay.toTextItem(d, frameWidthPx = 1000f, frameHeightPx = 800f)
                val committedRadiusPx = (item.fontSize * 1000f) * ROUNDED_BACKGROUND_CORNER_FACTOR

                assertEquals(
                    "density=$density fontSize=${fontDp}dp must agree on the corner radius",
                    liveRadiusPx.toDouble(),
                    committedRadiusPx.toDouble(),
                    0.5
                )
            }
        }
    }

    @Test
    fun `ROUNDED corner radius follows the shared 25-percent-of-text-size rule`() {
        // Robolectric's software canvas cannot rasterise drawRoundRect (and its
        // Paint.measureText returns zero-width lines), so like the blur geometry
        // this pins the committed corner rule on its single source of truth:
        // the renderer's radius expression must literally be
        // textSize * ROUNDED_BACKGROUND_CORNER_FACTOR, which the live preview
        // shares in dp (file-level test `ROUNDED corner radius is identical
        // live and committed at any font size` proves the dp and px sides of
        // that constant stay equal).
        for (textSizePx in floatArrayOf(40f, 100f, 250f, 400f)) {
            assertEquals(
                "radius is 25% of the text size",
                textSizePx * 0.25f,
                roundedBackgroundRadiusPx(textSizePx),
                0.001f
            )
            assertEquals(
                "radius follows the shared constant",
                textSizePx * ROUNDED_BACKGROUND_CORNER_FACTOR,
                roundedBackgroundRadiusPx(textSizePx),
                0.001f
            )
        }
    }

    @Test
    fun `mosaic live stand-in cell size equals the committed pixelation cell at preview resolution`() {
        // The live overlay previews pixelation with drawPixelMosaic sized by
        // pixelMosaicCellPx (MosaicOverlay.kt); the committed raster sizes its
        // cells the same way from the SAME brush size (normalized back against
        // the frame width in EditorScreen), so a painted stroke must produce a
        // committed cell the width of the live stand-in's cell (±1 raster edge
        // rounding). Bigger brushes must scale the committed cell proportionally.
        val w = 200
        for (brushPx in floatArrayOf(30f, 60f)) {
            val input = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
            for (y in 0 until w) {
                for (x in 0 until w) {
                    input.setPixel(x, y, Color.rgb(x * 255 / w, x * 255 / w, x * 255 / w))
                }
            }
            val out = BitmapRenderer.render(
                context = context,
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
                        brushSize = brushPx / w,
                        opacity = 1f
                    )
                )
            )
            val expectedCell = pixelMosaicCellPx(brushPx, w.toFloat(), w.toFloat())
            // Cell centred on (100,100); read the top band row y=y0 (the reliable
            // readback band) and measure the contiguous fill run across it.
            val x0 = (100 - expectedCell / 2f).roundToInt()
            val fill = out.getPixel(x0, x0)
            var run = 0
            while (x0 + run < w && out.getPixel(x0 + run, x0) == fill) run++
            assertEquals(
                "committed cell (brush=$brushPx) must match the live stand-in cell (±1 edge px)",
                expectedCell.toFloat(),
                run.toFloat(),
                1f
            )
        }
    }

    @Test
    fun `self-crossing soft SOLID stroke never darkens the overlap beyond a single stamp`() {
        // A soft (hardness 0.4) 50%-opacity red X-cross on a 100px frame with
        // 5px-radius stamps. Where the two diagonal arms cross at (50,50) every
        // pixel is painted by both arms' nearest stamps; SRC_OVER compounding
        // would reach ~0.66 alpha there, the committed/live max-alpha model keeps
        // the maximum of a single arm. The single-arm value is re-derived from the
        // SAME sample pacing the raster uses (sampleAlong: spacing radius/2, steps
        // roundToInt, distances from the FLOAT sample point, not the rounded
        // stamp centre).
        val w = 100
        val baseAlpha = 0.5f
        val hardness = 0.4f
        val stroke = StrokeLayer(
            type = StrokeType.PEN,
            points = listOf(0.3f to 0.3f, 0.7f to 0.7f, 0.3f to 0.7f, 0.7f to 0.3f),
            brushSize = 0.1f,
            color = Color.argb((baseAlpha * 255).roundToInt(), 255, 0, 0),
            hardness = hardness,
            brush = PenBrushKind.SOLID
        )
        val raster = StrokeOverlayRenderer.penStrokeRaster(w, w, stroke)!!
        val crossing = Color.alpha(raster.bitmap.getPixel(50 - raster.offsetX, 50 - raster.offsetY)) / 255f

        val radius = (0.1f * w / 2f)
        val spacing = radius * 0.5f
        val diag = kotlin.math.hypot(40f, 40f)
        val steps = (diag / spacing).roundToInt().coerceAtLeast(1)
        var singleArm = 0f
        for (s in 0..steps) {
            val a = 30f + 40f * s / steps
            for ((cx, cy) in listOf(a to a, a to 70f - 40f * s / steps)) {
                val d = sqrt((50f - cx) * (50f - cx) + (50f - cy) * (50f - cy))
                val t = d / radius
                singleArm = maxOf(singleArm, baseAlpha * (1f - (1f - hardness) * t.coerceAtMost(1f)))
            }
        }
        assertEquals(
            "the crossing keeps max-of-one-arm coverage, not the SRC_OVER sum",
            singleArm, crossing, 1f / 255f
        )
        var maxAlpha = 0f
        for (y in 0 until raster.bitmap.height) {
            for (x in 0 until raster.bitmap.width) {
                maxAlpha = maxOf(maxAlpha, Color.alpha(raster.bitmap.getPixel(x, y)) / 255f)
            }
        }
        assertEquals("no pixel may exceed the stamp's hardest core alpha", baseAlpha, maxAlpha, 1f / 255f)
        assertTrue("the crossing pixel must genuinely sit under both arms", crossing > 0.3f)
    }

    @Test
    fun `GLOW sharp bend stays closed and never doubles the halo alpha`() {
        // A 90° V-bend (live drawLine Round caps once "closed" the corner but
        // blended its GLOW halos brighter at the vertex; the committed rasteriser
        // — which the live overlay now draws — keeps the corner closed through
        // the overlapping stamps AND takes the max single-stamp alpha, so a slow
        // drag or a bend can never brighten the halo).
        val w = 100
        val alpha = 0.4f
        val stroke = StrokeLayer(
            type = StrokeType.PEN,
            points = listOf(0.3f to 0.5f, 0.5f to 0.5f, 0.5f to 0.9f),
            brushSize = 0.1f, // radius 5px
            color = Color.argb((alpha * 255).roundToInt(), 0, 180, 90),
            hardness = 0f, // GLOW ignores hardness
            brush = PenBrushKind.GLOW
        )
        val raster = StrokeOverlayRenderer.penStrokeRaster(w, w, stroke)!!
        val a = { x: Int, y: Int ->
            Color.alpha(raster.bitmap.getPixel(x - raster.offsetX, y - raster.offsetY)) / 255f
        }
        // The vertex (50,50) is a stamp centre, but its neighbours (spaced
        // radius/2) are all within their GLOW base band (t ≤ 4) there, so the
        // max-alpha model keeps the FULL band alpha and the band's colour — never
        // the old live overlay's compounded white-heat stripe.
        assertEquals("the corner closes at the base band's full alpha (no gap)", alpha, a(50, 50), 1f / 255f)
        val corner = raster.bitmap.getPixel(50 - raster.offsetX, 50 - raster.offsetY)
        assertEquals("the bend keeps the stroke colour, not a white-heat stripe", 90.0, Color.blue(corner).toDouble(), 2.0)
        // Points in the base band along and beside the arms prove the bend keeps
        // the ribbon continuous (no stamp gap at the corner).
        assertTrue("diagonal to the corner stays in the base band: ${a(53, 53)}", a(53, 53) >= alpha * 0.9f)
        assertTrue("along the arm at halo distance: ${a(58, 50)}", a(58, 50) >= alpha * 0.9f)
        var maxAlpha = 0f
        for (y in 0 until raster.bitmap.height) {
            for (x in 0 until raster.bitmap.width) {
                maxAlpha = maxOf(maxAlpha, Color.alpha(raster.bitmap.getPixel(x, y)) / 255f)
            }
        }
        assertEquals("two overlapping halos must never exceed one stamp's max", alpha, maxAlpha, 1f / 255f)
    }

    @Test
    fun `rainbow raster advances hue per sampled stamp along the ribbon`() {
        // RAINBOW only exists in the committed rasteriser now — the live overlay
        // draws that same raster through drawPenRasterStroke. A 160px-long stroke
        // at a 20px-radius brush samples every 10px (ceil(160/10)+1 = 17 stamps).
        // Hardness 0 makes each stamp's alpha fall off with distance, so every
        // stamp centre keeps its own colour; reading the centres back must show
        // one (index*18)%360 step per sample.
        val w = 200
        val stroke = StrokeLayer(
            type = StrokeType.PEN,
            points = listOf(0.1f to 0.5f, 0.9f to 0.5f),
            brushSize = 0.2f,
            color = Color.BLACK,
            hardness = 0f,
            brush = PenBrushKind.RAINBOW
        )
        val raster = StrokeOverlayRenderer.penStrokeRaster(w, w, stroke)!!
        val hsv = FloatArray(3)
        for (s in 0..16) {
            val col = raster.bitmap.getPixel(20 + 10 * s - raster.offsetX, 100 - raster.offsetY)
            assertEquals("opaque stamp core at s=$s", 255, Color.alpha(col))
            Color.colorToHSV(col, hsv)
            val expected = (s * 18) % 360f
            assertEquals("hue at stamp s=$s advances 18° per sample", expected, hsv[0], 2f)
        }
    }

    @Test
    fun `separable blur-brush box pass is byte-identical to the per-pixel box average`() {
        // boxBlurImage computes every pixel's clipped (2r+1)^2 box average with
        // two sliding passes; it must reproduce the old per-pixel scanning box
        // average EXACTLY (same clipped bounds, same floor division) so the blur
        // brush output never changes. A varied gradient + sparse random pixels
        // and a non-square frame exercise edge clipping on all four sides.
        val rng = java.util.Random(7)
        val w = 37
        val h = 29
        val src = IntArray(w * h) {
            val x = it % w
            val y = it / w
            Color.rgb(
                (x * 3 + (it * 13) % 7) % 256,
                (y * 5 + (it * 31) % 11) % 256,
                ((x + y) * 7 + rng.nextInt(40)) % 256
            )
        }
        for (radius in intArrayOf(1, 4, 11)) {
            val via = StrokeOverlayRenderer.boxBlurImage(src, w, h, radius)
            for (idx in 0 until w * h) {
                val cx = idx % w
                val cy = idx / w
                val x0 = (cx - radius).coerceAtLeast(0)
                val y0 = (cy - radius).coerceAtLeast(0)
                val x1 = (cx + radius).coerceAtMost(w - 1)
                val y1 = (cy + radius).coerceAtMost(h - 1)
                var r = 0L; var g = 0L; var b = 0L; var count = 0L
                for (y in y0..y1) {
                    for (x in x0..x1) {
                        val c = src[y * w + x]
                        r += Color.red(c); g += Color.green(c); b += Color.blue(c)
                        count++
                    }
                }
                val expected = Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
                assertEquals("radius=$radius pixel ($cx,$cy)", expected, via[idx])
            }
        }
    }

    @Test
    fun `batched pen composite is byte-identical to the per-pixel source-over loop`() {
        // compositePenRaster batching swapped 3 JNI calls/pixel (src getPixel +
        // dst getPixel + dst setPixel) for one getPixels per source + one region
        // setPixels; the float source-over chain is UNCHANGED (same skip gates,
        // same float order). An overlapping sprite over a varied canvas exercises
        // the skip gates and soft/dark overlap where both source and destination
        // contribute.
        val w = 24
        val h = 20
        val base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                base.setPixel(x, y, Color.rgb((x * 9) % 256, (y * 11) % 256, ((x + y) * 5) % 256))
            }
        }
        val stroke = StrokeLayer(
            type = StrokeType.PEN,
            points = listOf(0.2f to 0.3f, 0.8f to 0.6f, 0.2f to 0.8f),
            brushSize = 0.5f,
            color = Color.argb(200, 255, 40, 90),
            hardness = 0.3f,
            brush = PenBrushKind.SOLID
        )
        val batched = base.copy(Bitmap.Config.ARGB_8888, true)
        StrokeOverlayRenderer.drawStrokes(batched, listOf(stroke))

        // Control: the previous per-pixel loop compositing the same raster.
        val raster = StrokeOverlayRenderer.penStrokeRaster(w, h, stroke)!!
        val control = base.copy(Bitmap.Config.ARGB_8888, true)
        val bmp = raster.bitmap
        for (yy in 0 until bmp.height) {
            for (xx in 0 until bmp.width) {
                val src = bmp.getPixel(xx, yy)
                val sa = Color.alpha(src) / 255f
                if (sa <= 0.001f) continue
                val dst = control.getPixel(raster.offsetX + xx, raster.offsetY + yy)
                val da = Color.alpha(dst) / 255f
                val outA = sa + da * (1f - sa)
                if (outA <= 0f) continue
                control.setPixel(
                    raster.offsetX + xx, raster.offsetY + yy,
                    Color.argb(
                        (outA * 255f).roundToInt().coerceIn(0, 255),
                        ((Color.red(src) * sa + Color.red(dst) * da * (1f - sa)) / outA).roundToInt().coerceIn(0, 255),
                        ((Color.green(src) * sa + Color.green(dst) * da * (1f - sa)) / outA).roundToInt().coerceIn(0, 255),
                        ((Color.blue(src) * sa + Color.blue(dst) * da * (1f - sa)) / outA).roundToInt().coerceIn(0, 255)
                    )
                )
            }
        }
        val a = IntArray(w * h)
        val c = IntArray(w * h)
        batched.getPixels(a, 0, w, 0, 0, w, h)
        control.getPixels(c, 0, w, 0, 0, w, h)
        for (i in 0 until w * h) {
            assertEquals("pixel ${i % w},${i / w}", c[i], a[i])
        }
    }

    @Test
    fun `pen raster write-out is byte-identical to the per-pixel stamp profile`() {
        // The raster write-out (one setPixel per pixel → one batched setPixels)
        // must produce exactly the stamp profile: the brush's radial alpha
        // falloff of the stroke colour, and zeros (TRANSPARENT) wherever the
        // profile falls at/below the 0.001 skip gate. A single-point SOLID
        // stroke makes the control a closed-form radial profile. Even canvas
        // dims keep the stamp centre pixel-exact (no toInt/round ambiguity).
        val w = 48
        val h = 48
        val brushSize = 0.3f
        val radiusPx = (brushSize * w / 2f).coerceAtLeast(1f)
        val spreadPx = radiusPx
        val spread2 = spreadPx * spreadPx
        val ri = kotlin.math.ceil(spreadPx).toInt()
        val colour = Color.argb(178, 120, 60, 240)
        val baseAlpha = Color.alpha(colour) / 255f
        val hardness = 0.5f
        val stroke = StrokeLayer(
            type = StrokeType.PEN,
            points = listOf(0.5f to 0.5f),
            brushSize = brushSize,
            color = colour,
            hardness = hardness,
            brush = PenBrushKind.SOLID
        )
        val raster = StrokeOverlayRenderer.penStrokeRaster(w, h, stroke)!!
        // Stamp centre = the single 0.5/0.5 sample (px = py = 24.0, integer).
        val px = w / 2f
        val py = h / 2f
        val cx = px.roundToInt()
        val cy = py.roundToInt()
        val pxs = px.toInt()
        val pys = py.toInt()
        // Offset/bounds from penStrokeRaster for the same centre.
        val x0 = (cx - ri).coerceAtLeast(0)
        val y0 = (cy - ri).coerceAtLeast(0)
        val x1 = (cx + ri).coerceAtMost(w - 1)
        val y1 = (cy + ri).coerceAtMost(h - 1)
        assertEquals(x1 - x0 + 1, raster.bitmap.width)
        assertEquals(y1 - y0 + 1, raster.bitmap.height)
        assertEquals(x0, raster.offsetX)
        assertEquals(y0, raster.offsetY)
        for (row in 0 until raster.bitmap.height) {
            for (col in 0 until raster.bitmap.width) {
                val canvasX = x0 + col
                val canvasY = y0 + row
                val dy = canvasY - pys
                val dx = canvasX - pxs
                val d2 = dx * dx + dy * dy
                val expected = if (d2 > spread2) {
                    Color.TRANSPARENT
                } else {
                    val t = kotlin.math.sqrt(d2.toFloat()) / radiusPx
                    val a = baseAlpha * (1f - (1f - hardness) * t.coerceAtMost(1f))
                    if (a <= 0f) {
                        Color.TRANSPARENT
                    } else {
                        Color.argb(
                            (a * 255f).roundToInt().coerceIn(0, 255),
                            Color.red(colour), Color.green(colour), Color.blue(colour)
                        )
                    }
                }
                val actual = raster.bitmap.getPixel(col, row)
                assertEquals("pixel ($col,$row) canvas ($canvasX,$canvasY)", expected, actual)
            }
        }
    }

    @Test
    fun `privacy region pixelation reads only the union bbox, byte-identical to the whole-canvas snapshot`() {
        // drawPrivacyRegions dropped the full-bitmap copy() in favour of one
        // getPixels limited to the union bbox of the privacy rects. The cell
        // averages must be identical to a control that snapshots the WHOLE
        // canvas first (the old path) — the bbox array can only differ if a
        // cell would be clipped differently, which the bbox prevents.
        val w = 64
        val h = 64
        val canvas = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Horizontal + vertical ramps so every cell average is distinct.
        for (y in 0 until h) {
            for (x in 0 until w) {
                canvas.setPixel(x, y, Color.rgb(x * 255 / w, y * 255 / h, ((x + y) * 255 / (w + h))))
            }
        }
        val regions = listOf(
            RectF(0.1f, 0.15f, 0.45f, 0.4f),
            RectF(0.35f, 0.3f, 0.7f, 0.6f),
            RectF(0.6f, 0.55f, 0.9f, 0.85f)
        )
        val batched = canvas.copy(Bitmap.Config.ARGB_8888, true)
        StrokeOverlayRenderer.drawPrivacyRegions(batched, regions)

        // Control: old path snapshot (whole-canvas getPixels + blockAverage).
        val control = canvas.copy(Bitmap.Config.ARGB_8888, true)
        val src = control.copy(Bitmap.Config.ARGB_8888, false)
        val srcPixels = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val draw = android.graphics.Canvas(control)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        for (region in regions) {
            val left = (region.left * w).roundToInt().coerceIn(0, w - 1)
            val top = (region.top * h).roundToInt().coerceIn(0, h - 1)
            val right = (region.right * w).roundToInt().coerceIn(left + 1, w)
            val bottom = (region.bottom * h).roundToInt().coerceIn(top + 1, h)
            val cell = ((right - left) / 3f).roundToInt().coerceAtLeast((minOf(w, h) / 140f).coerceAtLeast(1f).roundToInt())
            var y = top
            while (y < bottom) {
                var x = left
                while (x < right) {
                    val cw = cell.coerceAtMost(right - x)
                    val ch = cell.coerceAtMost(bottom - y)
                    var r = 0L
                    var g = 0L
                    var b = 0L
                    var count = 0L
                    val width = (x + cw).coerceAtMost(src.width)
                    val height = (y + ch).coerceAtMost(src.height)
                    for (yy in y until height) {
                        var i = yy * src.width + x
                        val end = i + (width - x)
                        while (i < end) {
                            val cc = srcPixels[i]
                            r += Color.red(cc); g += Color.green(cc); b += Color.blue(cc)
                            count++
                            i++
                        }
                    }
                    paint.color = if (count == 0L) srcPixels[x.coerceAtMost(src.width - 1) + y.coerceAtMost(src.height - 1) * src.width]
                    else Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
                    draw.drawRect(x.toFloat(), y.toFloat(), (x + cw).toFloat(), (y + ch).toFloat(), paint)
                    x += cw
                }
                y += cell
            }
        }
        src.recycle()

        // Compare the union bbox region + a guard scan line outside it.
        val bx0 = (0.1f * w).roundToInt()
        val by0 = (0.15f * h).roundToInt()
        val bx1 = (0.9f * w).roundToInt()
        val by1 = (0.85f * h).roundToInt()
        val scanW = bx1 - bx0 + 1
        val scanH = by1 - by0 + 1
        val ba = IntArray(scanW * scanH)
        val cb = IntArray(scanW * scanH)
        batched.getPixels(ba, 0, scanW, bx0, by0, scanW, scanH)
        control.getPixels(cb, 0, scanW, bx0, by0, scanW, scanH)
        for (i in 0 until scanW * scanH) {
            assertEquals("bbox pixel (${bx0 + i % scanW},${by0 + i / scanW})", cb[i], ba[i])
        }
        // Row 0 lies above every privacy rect, so it must be byte-identical too
        // (guards against an accidental whole-canvas overwrite).
        val ra = IntArray(w).also { batched.getPixels(it, 0, w, 0, 0, w, 1) }
        val rc = IntArray(w).also { control.getPixels(it, 0, w, 0, 0, w, 1) }
        for (i in 0 until w) {
            assertEquals("row-0 pixel $i", rc[i], ra[i])
        }
    }
}