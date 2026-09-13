package codes.pepper.whimsicalart.feature.editor.ui.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import codes.pepper.whimsicalart.feature.editor.domain.TextAlignment
import codes.pepper.whimsicalart.feature.editor.domain.TextBackgroundShape
import codes.pepper.whimsicalart.feature.editor.domain.TextFont
import codes.pepper.whimsicalart.feature.editor.domain.TextOverlayRenderer
import codes.pepper.whimsicalart.feature.editor.domain.TextStyleFlag
import codes.pepper.whimsicalart.feature.editor.domain.bokeh.BokehProcessor
import codes.pepper.whimsicalart.feature.editor.domain.ocr.OcrOverlayFragment
import codes.pepper.whimsicalart.feature.editor.ui.buildOcrTextOverlay
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs


/**
 * Spec: docs/effects_reference.md → *Text* → *Background blur semantics* — the
 * BLUR background bakes a loose feathered oval of blurred PHOTO content behind
 * the glyphs: fully blurred inside the ellipse, fading back to the sharp photo
 * across a band past the ellipse edge, and (unlike RECT / OVAL / PILL) it is
 * intensity-driven — no colour or opacity control. These tests pin down the
 * pixel behaviour of the shared blur core, the committed-pass rasterisation,
 * the panel round-trip, and the OCR default.
 */
@RunWith(RobolectricTestRunner::class)
class TextBlurTest {

    // ---------------------------------------------------------------------
    // Shared blur core (used by BOTH the committed renderer and the preview).
    // ---------------------------------------------------------------------

    private fun twoToneBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            val px = IntArray(w * h) { i -> if (i % w < w / 2) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
            setPixels(px, 0, w, 0, 0, w, h)
        }

    private fun pixels(b: Bitmap): IntArray =
        IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private fun blurWindow(
        source: Bitmap,
        sx: Int,
        sy: Int,
        rw: Int,
        rh: Int,
        inner: RectF,
        radius: Float,
        feather: Float,
        rotationDegrees: Float = 0f
    ): Bitmap =
        TextOverlayRenderer.blurRegionOval(source, sx, sy, rw, rh, inner, radius, feather, rotationDegrees)

    private fun argbPixels(b: Bitmap): IntArray =
        IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private fun luminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r + g + b) / 3f
    }

    @Test
    fun `blur core fully blurs inside the ellipse`() {
        // 100x60 two-tone (vertical white/black boundary at x=50). The ellipse
        // straddles the boundary so the blurred and sharp values differ.
        val src = twoToneBitmap(100, 60)
        val inner = RectF(30f, 15f, 70f, 45f) // centre (50,30), rx=20, ry=15
        val sx = 22
        val sy = 7
        val rw = 56
        val rh = 46
        val radius = 6f
        val feather = 8f

        val out = blurWindow(src, sx, sy, rw, rh, inner, radius, feather)

        val sharp = Bitmap.createBitmap(src, sx, sy, rw, rh)
        val blurred = sharp.copy(Bitmap.Config.ARGB_8888, true)
        BokehProcessor().applySoftwareBlur(blurred, radius.toInt())
        val sharpPx = pixels(sharp)
        val blurredPx = pixels(blurred)
        val outPx = pixels(out)

        // (28,23) local = ellipse centre → weight 1 → equals the pure blurred value.
        assertEquals("centre pixel = blurred", blurredPx[23 * rw + 28], outPx[23 * rw + 28])
        assertNotEquals("sharp must differ at the boundary", sharpPx[23 * rw + 28], blurredPx[23 * rw + 28])
    }

    @Test
    fun `blur core feathers the band - a strict mix, never a binary switch`() {
        val src = twoToneBitmap(100, 60)
        val inner = RectF(30f, 15f, 70f, 45f)
        val sx = 22
        val sy = 7
        val rw = 56
        val rh = 46
        val radius = 6f
        val feather = 8f

        val out = blurWindow(src, sx, sy, rw, rh, inner, radius, feather)

        val sharp = Bitmap.createBitmap(src, sx, sy, rw, rh)
        val blurred = sharp.copy(Bitmap.Config.ARGB_8888, true)
        BokehProcessor().applySoftwareBlur(blurred, radius.toInt())
        val sharpPx = pixels(sharp)
        val blurredPx = pixels(blurred)
        val outPx = pixels(out)

        // (28,42) local sits in the feather band (scaled distance ≈ 1.27 between
        // the 1.0 ellipse edge and the 1 + bandN outer edge): it must be a strict
        // mixture of blurred and sharp.
        val idx = 42 * rw + 28
        assertNotEquals("band pixel is not the blurred value", blurredPx[idx], outPx[idx])
        assertNotEquals("band pixel is not the sharp value", sharpPx[idx], outPx[idx])
        assertNotEquals("sharp and blurred differ at this pixel", sharpPx[idx], blurredPx[idx])
    }

    @Test
    fun `blur core leaves pixels beyond the feather band untouched`() {
        val src = twoToneBitmap(100, 60)
        val inner = RectF(30f, 15f, 70f, 45f)
        val sx = 22
        val sy = 7
        val rw = 56
        val rh = 46
        val radius = 6f
        val feather = 8f

        val out = blurWindow(src, sx, sy, rw, rh, inner, radius, feather)

        val sharp = Bitmap.createBitmap(src, sx, sy, rw, rh)
        val outPx = pixels(out)
        val sharpPx = pixels(sharp)

        // (28,0) local (top of the window, global (50,7)): scaled distance
        // ≈ 1.53 > 1 + bandN → weight 0 → identical to the sharp original.
        assertEquals("beyond the band stays sharp", sharpPx[0 * rw + 28], outPx[0 * rw + 28])
    }

    // ---------------------------------------------------------------------
    // Committed-pass no-op contract. (The committed rasteriser simply feeds the
    // photo window under the block into the shared core — while Robolectric does
    // not rasterise real glyphs, the deterministic pixel contract of that core is
    // fully covered here: a uniform region is mathematically unchanged by a box
    // blur, so the blended result must equal the source in EVERY pixel — inside
    // the ellipse, in the feather band, and beyond it.)
    // ---------------------------------------------------------------------

    @Test
    fun `blur core leaves a uniform photo untouched in every band`() {
        val src = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888).apply {
            val px = IntArray(100 * 60) { 0xFF808080.toInt() }
            setPixels(px, 0, 100, 0, 0, 100, 60)
        }
        val inner = RectF(30f, 15f, 70f, 45f) // centre (50,30), rx=20, ry=15
        val sx = 22
        val sy = 7
        val rw = 56
        val rh = 46

        val out = blurWindow(src, sx, sy, rw, rh, inner, 6f, 8f)

        val srcPx = pixels(src)
        val outPx = pixels(out)
        // Only the window (44..100 x 8..54 local window area) is compared; the
        // rest of the source array is skipped via the window bounds.
        for (y in 0 until rh) {
            for (x in 0 until rw) {
                val windowIdx = y * rw + x
                val srcIdx = (sy + y) * 100 + (sx + x)
                assertEquals("uniform pixel stays exact", srcPx[srcIdx], outPx[windowIdx])
            }
        }
    }

    // ---------------------------------------------------------------------
    // Panel → item → restore round-trip.
    // ---------------------------------------------------------------------

    @Test
    fun `background blur edits round-trip the live draft overlay and the control`() {
        val vm = TextEditorViewModel()
        vm.startAddingText()
        vm.updateCurrentText("Blurred")
        vm.updateBackgroundShape(TextBackgroundShape.BLUR)
        vm.updateBackgroundBlur(0.65f)

        val state = vm.uiState.value
        val edited = state.textOverlays.single()
        assertEquals(TextBackgroundShape.BLUR, edited.backgroundShape)
        assertEquals(0.65f, edited.backgroundBlur, 1e-4f)
        assertEquals(0.65f, state.currentBackgroundBlur, 1e-4f)
    }

    @Test
    fun `selecting an existing BLUR item restores its intensity into the control`() {
        val vm = TextEditorViewModel()
        val overlay = TextOverlay(
            id = "a", text = "Saved", position = Offset(100f, 100f),
            backgroundShape = TextBackgroundShape.BLUR,
            backgroundBlur = 0.4f
        )
        vm.restoreOverlays(listOf(overlay))
        vm.selectOverlay("a")

        assertEquals(TextBackgroundShape.BLUR, vm.uiState.value.currentBackgroundShape)
        assertEquals(0.4f, vm.uiState.value.currentBackgroundBlur, 1e-4f)
    }

    @Test
    fun `background blur survives the commit conversion and back`() {
        val density = Density(density = 2f, fontScale = 1f)
        val overlay = TextOverlay(
            id = "t1",
            text = "Blur",
            position = Offset(250f, 200f),
            fontSize = 40f,
            backgroundShape = TextBackgroundShape.BLUR,
            backgroundBlur = 0.7f
        )

        val item = overlay.toTextItem(density, 1000f, 800f)
        assertEquals(TextBackgroundShape.BLUR, item.background)
        assertEquals(0.7f, item.backgroundBlur, 1e-4f)

        val roundTrip = item.toTextOverlay(density, 1000f, 800f)
        assertEquals(TextBackgroundShape.BLUR, roundTrip.backgroundShape)
        assertEquals(0.7f, roundTrip.backgroundBlur, 1e-4f)
    }

    // ---------------------------------------------------------------------
    // OCR import default.
    // ---------------------------------------------------------------------

    @Test
    fun `OCR-imported text defaults to a BLUR background at 65% intensity`() {
        val density = Density(density = 2f, fontScale = 1f)
        val overlay = buildOcrTextOverlay(
            fragment = OcrOverlayFragment("Hello", 0.5f, 0.25f, 0.05f),
            density = density,
            containerPx = Size(1200f, 1600f),
            imagePx = Size(1000f, 1400f)
        )

        assertEquals(TextBackgroundShape.BLUR, overlay.backgroundShape)
        assertEquals(0.65f, overlay.backgroundBlur, 1e-4f)
    }

    // ---------------------------------------------------------------------
    // LIVE-source resolution (tracker: BUGFIX, the text tool's BLUR BACKGROUND
    // NEVER SHOWS on the live preview canvas while editing). TextOverlayLayer
    // builds the piece only when `sourceBitmap != null`; EditorCanvasArea fed it
    // composite/sharpened previews, and on a PLAIN photo (no stack fold, no look
    // effect) BOTH are null — so the live blur had no source and never rendered.
    // The resolver now falls back to the oriented base decode the canvas shows.
    // ---------------------------------------------------------------------

    @Test
    fun `live blur source resolves in display priority with a base-decode fallback`() {
        val base = twoToneBitmap(100, 50)
        val sharpened = twoToneBitmap(90, 45)
        val composite = twoToneBitmap(80, 40)

        assertEquals("stack fold shown -> the piece reads the composite",
            composite, resolveLiveTextBlurSource(composite, sharpened, base))
        assertEquals("no fold, look pass active -> the sharpened preview",
            sharpened, resolveLiveTextBlurSource(null, sharpened, base))
        assertEquals("PLAIN photo -> falls back to the oriented base decode (never null)",
            base, resolveLiveTextBlurSource(null, null, base))
        assertNull("all-null state stays null (nothing to blur)",
            resolveLiveTextBlurSource(null, null, null))
    }

    // ---------------------------------------------------------------------
    // Live-preview draw sizing (next.md → *Text `blur` background does NOT
    // apply in the live preview*). The piece is cut in SOURCE px, but the
    // preview bitmap is shown inside a larger canvas frame — so each source px
    // must be rendered at `canvasPerSource` canvas px. The old draw skipped
    // that factor and painted `image.width / scale` box-local px (≈ image.width
    // canvas px), i.e. `scaleX` times too small when the preview bitmap is
    // smaller than the fitted frame — the normal live-preview case.
    // ---------------------------------------------------------------------

    @Test
    fun `preview piece carries the canvas-per-source ratio for a smaller preview bitmap`() {
        val source = twoToneBitmap(100, 50)
        val overlay = TextOverlay(
            id = "b", text = "Blur", position = Offset(50f, 25f),
            backgroundShape = TextBackgroundShape.BLUR, backgroundBlur = 0.7f
        )
        val density = Density(density = 1f, fontScale = 1f)
        // Preview bitmap is 100×50 source px shown inside a 200×100 canvas
        // frame (canvasPerSourceX/Y = 2), i.e. scaleX = scaleY = 0.5.
        val frame = TextBlurFrame(offsetXPx = 0f, offsetYPx = 0f, widthPx = 200f, heightPx = 100f)

        val piece = requireNotNull(
            buildTextBlurPiece(
                ApplicationProvider.getApplicationContext(),
                source,
                overlay,
                Size(60f, 30f),
                frame,
                density
            )
        ) { "blur piece must be built for a BLUR item" }

        assertEquals("canvas px per source px (X)", 2f, piece.canvasPerSourceX, 1e-4f)
        assertEquals("canvas px per source px (Y)", 2f, piece.canvasPerSourceY, 1e-4f)
        assertEquals("window width stays rw source px", piece.image.width, piece.src.width())
        assertEquals("window height stays rh source px", piece.image.height, piece.src.height())
    }

    @Test
    fun `regression - the painted oval spans the canvas-not-source window, never scaleX times smaller`() {
        // Direct check of the draw math (blurDstPx = the box-local size the
        // preview paints, before the item's own graphicsLayer scale).
        val piece = TextBlurPiece(
            bitmap = twoToneBitmap(40, 20),
            image = twoToneBitmap(40, 20).asImageBitmap(),
            src = android.graphics.Rect(0, 0, 40, 20),
            canvasPerSourceX = 2f,
            canvasPerSourceY = 2f
        )

        // Source window is 40×20; the canvas frame doubles it → 80×40 box-local
        // px at scale 1 (the OLD draw produced 40×20 — the reported bug).
        assertEquals(
            IntSize(80, 40),
            blurDstPx(piece, overlayScale = 1f)
        )
        // At the item's own scale, the drawn window divides so that applying the
        // graphicsLayer scale lands back on the full canvas window.
        assertEquals(
            IntSize(40, 20),
            blurDstPx(piece, overlayScale = 2f)
        )
    }

    @Test
    fun `painted window follows the canvas-per-source ratio at every preview resolution`() {
        val srcW = 100
        val srcH = 50
        val source = twoToneBitmap(srcW, srcH)
        val overlay = TextOverlay(
            id = "c", text = "Blur", position = Offset(50f, 25f),
            backgroundShape = TextBackgroundShape.BLUR, backgroundBlur = 0.7f
        )
        val density = Density(density = 1f, fontScale = 1f)

        // Frame SMALLER than the preview bitmap (canvasPerSource < 1) — the
        // window must still map down exactly.
        val smallFrame = TextBlurFrame(0f, 0f, widthPx = 50f, heightPx = 25f)
        val piece = requireNotNull(
            buildTextBlurPiece(
                ApplicationProvider.getApplicationContext(),
                source,
                overlay,
                Size(60f, 30f),
                smallFrame,
                density
            )
        ) { "blur piece must be built for a BLUR item" }
        assertEquals(0.5f, piece.canvasPerSourceX, 1e-4f)

        val dst = blurDstPx(piece, overlayScale = 1f)
        // rw × 0.5 = the on-screen window width.
        assertEquals(piece.image.width * 0.5f, dst.width.toFloat(), 0.6f)
        assertEquals(piece.image.height * 0.5f, dst.height.toFloat(), 0.6f)
    }

    // ---------------------------------------------------------------------
    // Live-vs-committed PARITY (next.md → *Text blur does NOT show in the live
    // preview, only on commit*). The live piece must derive its oval window from
    // the SAME shared cover rule the committed rasteriser uses
    // (TextOverlayRenderer.blurCoverRect), never from the live compose box
    // (whose 12/8 dp padding and layout width paint a different — and at small
    // sizes effectively invisible — oval). The piece only carries the feather-
    // inflated SOURCE window, so the parity check is: the piece's centre maps
    // back to the committed cover centre, and its canvas-px extent (window ×
    // canvas-per-source, after the item's own scale) is the committed scaled
    // cover extended by the feather band (bounded 3..32 px).
    // ---------------------------------------------------------------------

    @Test
    fun `live oval centre and size equal the committed cover - the shared single rule`() {
        val density = Density(density = 2f, fontScale = 1f)
        val overlay = TextOverlay(
            id = "p", text = "Blurry headline", position = Offset(250f, 120f),
            fontSize = 40f, backgroundShape = TextBackgroundShape.BLUR,
            backgroundBlur = 0.7f, scale = 1.3f
        )
        val frameW = 1000f
        val frameH = 800f
        // Half-resolution preview shown in a 1000×800 canvas frame.
        val source = twoToneBitmap(500, 400)
        val frame = TextBlurFrame(0f, 0f, frameW, frameH)
        val piece = requireNotNull(
            buildTextBlurPiece(
                ApplicationProvider.getApplicationContext(),
                source,
                overlay,
                Size(340f, 110f),
                frame,
                density
            )
        ) { "blur piece must be built for a BLUR item" }

        val item = overlay.toTextItem(density, frameW, frameH)
        val cover = TextOverlayRenderer.blurCoverRect(
            ApplicationProvider.getApplicationContext(), item, frameW, frameH
        )

        // Centre: source window centre × canvas-per-source == committed cover centre.
        val liveCentreX = (piece.src.left + piece.image.width / 2f) * piece.canvasPerSourceX
        val liveCentreY = (piece.src.top + piece.image.height / 2f) * piece.canvasPerSourceY
        assertEquals("live oval centre X matches the committed cover",
            cover.centerX(), liveCentreX, 2f)
        assertEquals("live oval centre Y matches the committed cover",
            cover.centerY(), liveCentreY, 2f)

        // Size (canvas px, after the item's own scale = the committed draw.scale
        // frame): the scaled cover, extended by the feather band (≤ 32 px + the
        // floor/ceil rounding of the window). A preview oval sized by the compose
        // box instead would overhang this window by the box padding difference
        // (e.g. tens of px) AND still never be SMALLER than the cover — the old
        // measured-derived window did both.
        val liveW = piece.image.width * piece.canvasPerSourceX
        val liveH = piece.image.height * piece.canvasPerSourceY
        assertTrue("live window covers the scaled committed cover (got $liveW)",
            liveW >= cover.width() * overlay.scale)
        assertTrue("live window is the scaled cover + the small feather band (got $liveW)",
            liveW <= cover.width() * overlay.scale + 64f)
        assertTrue("live window covers the scaled committed cover height (got $liveH)",
            liveH >= cover.height() * overlay.scale)
        assertTrue("live window is the scaled cover + the small feather band height (got $liveH)",
            liveH <= cover.height() * overlay.scale + 64f)
    }

    // Scale parity (tracker → next.md: SCALED text layer commits a WRONG
    // background): the live oval IS the committed scaled oval — both are
    // [TextOverlayRenderer.scaledBlurCoverRect] (blurCoverRect in the item's
    // scale frame), so the preview window's centre and extent match the scaled
    // cover exactly, never the unscaled cover.

    @Test
    fun `live preview oval window equals the commit's scaled cover at any pinch-scale`() {
        val density = Density(density = 2f, fontScale = 1f)
        val overlay = TextOverlay(
            id = "p", text = "Blurry headline", position = Offset(250f, 120f),
            fontSize = 40f, backgroundShape = TextBackgroundShape.BLUR,
            backgroundBlur = 0.7f, scale = 1.5f
        )
        val frameW = 1000f
        val frameH = 800f
        val source = twoToneBitmap(500, 400)
        val frame = TextBlurFrame(0f, 0f, frameW, frameH)
        val piece = requireNotNull(
            buildTextBlurPiece(
                ApplicationProvider.getApplicationContext(),
                source,
                overlay,
                Size(340f, 110f),
                frame,
                density
            )
        ) { "blur piece must be built for a BLUR item" }

        val item = overlay.toTextItem(density, frameW, frameH)
        val scaled = TextOverlayRenderer.scaledBlurCoverRect(
            ApplicationProvider.getApplicationContext(), item, frameW, frameH
        )
        val cover = TextOverlayRenderer.blurCoverRect(
            ApplicationProvider.getApplicationContext(), item, frameW, frameH
        )

        val liveCentreX = (piece.src.left + piece.image.width / 2f) * piece.canvasPerSourceX
        val liveCentreY = (piece.src.top + piece.image.height / 2f) * piece.canvasPerSourceY
        assertEquals("live oval centre X is the scaled cover centre (== item centre)",
            scaled.centerX(), liveCentreX, 1f)
        assertEquals("live oval centre Y is the scaled cover centre (== item centre)",
            scaled.centerY(), liveCentreY, 1f)
        assertTrue("the live oval is genuinely scaled — larger than the unscaled cover: " +
            "scaled=${scaled.width()} cover=${cover.width()}", scaled.width() > cover.width())

        // The preview window (source window × canvas-per-source) spans the
        // SCALED cover ± the feather band exactly like the committed oval. The
        // feather is clamped in SOURCE (photo) px, so derive it the way
        // buildTextBlurPiece does: block height = scaled height × scaleY.
        val featherSource = (scaled.height() * (source.height / frameH) * 0.08f).coerceIn(3f, 32f)
        val liveW = piece.image.width * piece.canvasPerSourceX
        val liveH = piece.image.height * piece.canvasPerSourceY
        val windowW = (scaled.width() * (source.width / frameW) + 2f * featherSource) * piece.canvasPerSourceX
        val windowH = (scaled.height() * (source.height / frameH) + 2f * featherSource) * piece.canvasPerSourceY
        assertEquals("live window width = the scaled cover + the feather band",
            windowW, liveW, 4f)
        assertEquals("live window height = the scaled cover + the feather band",
            windowH, liveH, 4f)
    }

    // ---------------------------------------------------------------------
    // Live blur-piece CACHE invalidation (next.md → *BUGFIX, the live TEXT blur
    // background is not invalidated when the text's rendering inputs change*).
    // TextOverlayLayer's `remember` used to key on the compose box's `measured`
    // size — every other change that re-lays-out into the SAME box (font family
    // change, a style flag with equal line counts, a rewrap that keeps the block
    // bounds) left all keys equal and the OLD pre-blurred window stayed on
    // screen until an unrelated key moved. The cache now keys on the ACTUAL
    // single-rule inputs via [textBlurPieceKey] — exactly the fields
    // [TextOverlayRenderer.measureTextBlock] consumes — so a rebuild happens
    // whenever the committed oval would recompute.
    // ---------------------------------------------------------------------

    @Test
    fun `text blur cache key invalidates on any metrics field the old box-size proxy missed`() {
        val base = TextOverlay(
            id = "k",
            text = "Blur",
            position = Offset(50f, 25f),
            backgroundShape = TextBackgroundShape.BLUR,
            backgroundBlur = 0.6f
        )
        val baseKey = textBlurPieceKey(base)

        // A metrics or geometry field the OLD key could not see (it keyed only
        // on scale/position/backgroundBlur + the compose box's measured size).
        // A font family or a style flag that keeps the SAME measured box must
        // STILL invalidate — that is exactly the reported stale-window class.
        val variants = listOf(
            base.copy(fontFamily = TextFont.SERIF),
            base.copy(style = setOf(TextStyleFlag.BOLD)),
            base.copy(style = setOf(TextStyleFlag.UNDERLINE, TextStyleFlag.STRIKETHROUGH)),
            base.copy(alignment = TextAlignment.JUSTIFIED),
            base.copy(text = "Blurry"),
            base.copy(fontSize = 40f),
            base.copy(scale = 1.4f),
            base.copy(position = Offset(60f, 30f)),
            // Rotation changes which photo pixels the oval reads (the shared
            // core re-samples under the rotated footprint) — it MUST rebuild.
            base.copy(rotation = 45f),
            base.copy(backgroundBlur = 0.9f)
        )
        variants.forEach { v ->
            assertTrue(
                "key must change for text=${v.text} family=${v.fontFamily} style=${v.style} " +
                    "alignment=${v.alignment} fontSize=${v.fontSize} scale=${v.scale}",
                baseKey != textBlurPieceKey(v)
            )
        }
    }

    @Test
    fun `text blur cache key ignores edits the cover rule does not consume`() {
        val base = TextOverlay(
            id = "k",
            text = "Blur",
            position = Offset(50f, 25f),
            backgroundShape = TextBackgroundShape.BLUR,
            backgroundBlur = 0.6f
        )
        val baseKey = textBlurPieceKey(base)

        // Shadow, outline and the background fill colour do NOT feed the cover rule
        // (the blur piece only reads the photo behind the block) — these edits
        // must skip the rebuild, mirroring the commit-side [TextItem] block key.
        // Rotation is NOT here: it changes which photo pixels the oval blurs and
        // MUST rebuild (covered above).
        val isometries = listOf(
            base.copy(hasShadow = true, shadowDistance = 8f, shadowAngle = 30f, shadowColor = Color.Red),
            base.copy(hasOutline = true, outlineSize = 3f, outlineBlur = 1f, outlineColor = Color.Blue),
            base.copy(backgroundColor = Color.White)
        )
        isometries.forEach { v -> assertEquals(baseKey, textBlurPieceKey(v)) }
    }

    @Test
    fun `two overlays differing only in a metrics field build different blur windows`() {
        val density = Density(density = 2f, fontScale = 1f)
        val source = twoToneBitmap(500, 400)
        val frame = TextBlurFrame(0f, 0f, 1000f, 800f)
        val base = TextOverlay(
            id = "w",
            text = "Blur",
            position = Offset(250f, 120f),
            fontSize = 40f,
            backgroundShape = TextBackgroundShape.BLUR,
            backgroundBlur = 0.7f
        )
        // ONLY the font size differs — a metrics field the old key missed.
        val edited = base.copy(fontSize = 60f)
        assertTrue(
            "metric-only change must invalidate the cache",
            textBlurPieceKey(base) != textBlurPieceKey(edited)
        )

        // The rebuilt window re-derives from the CURRENT cover rule — the same
        // scaledBlurCoverRect the committed rasteriser paints, so a bigger font
        // covers a strictly larger rect.
        val container: Context = ApplicationProvider.getApplicationContext()
        val baseCover = TextOverlayRenderer.scaledBlurCoverRect(
            container,
            base.toTextItem(density, 1000f, 800f),
            1000f,
            800f
        )
        val editedCover = TextOverlayRenderer.scaledBlurCoverRect(
            container,
            edited.toTextItem(density, 1000f, 800f),
            1000f,
            800f
        )
        assertTrue(
            "larger font covers a larger rect: base=${baseCover.width()} edited=${editedCover.width()}",
            editedCover.width() > baseCover.width()
        )

        // A rebuild freshens, never reuses: the two pieces carry DIFFERENT
        // source windows (the pre-blurred region scales with the cover).
        val basePiece = requireNotNull(
            buildTextBlurPiece(container, source, base, Size(340f, 110f), frame, density)
        )
        val editedPiece = requireNotNull(
            buildTextBlurPiece(container, source, edited, Size(340f, 110f), frame, density)
        )
        assertTrue(
            "rebuilt piece must differ from the stale one: base=${basePiece.src} edited=${editedPiece.src}",
            basePiece.src != editedPiece.src
        )
    }

    // ---------------------------------------------------------------------
    // ROTATED-footprint re-sampling (next.md → *Text blur background keeps a
    // stale cached sample when the item's footprint changes*). A rotated BLUR
    // item must defocus the photo content under the ROTATED oval, never a
    // rotated slice of the axis-aligned (pre-rotation) region. The shared core
    // re-reads the source at the rotated coordinates whenever rotationDegrees is
    // non-zero; these tests pin that down directly, and the cache-key tests
    // above prove a rotation delta rebuilds the live piece.
    // ---------------------------------------------------------------------

    @Test
    fun `blur core re-samples the photo under a rotated oval instead of spinning the window`() {
        // Vertical two-tone boundary at x=50; the ellipse (center (50,30),
        // rx=20, ry=15) straddles it. At 0° the patch holds the axis-aligned
        // window (left/right halves split by the boundary). At 90° the same
        // patch pixels must hold the photo content above/below the rectangle —
        // the ellipse's TOP tip reads the BLACK zone and its BOTTOM tip the
        // WHITE zone — i.e. the content follows the rotated footprint rather
        // than a spun copy of the pre-rotation window.
        val src = twoToneBitmap(100, 60)
        val inner = RectF(30f, 15f, 70f, 45f)
        val sx = 22
        val sy = 7
        val rw = 56
        val rh = 46
        val radius = 6f
        val feather = 8f

        val flat = blurWindow(src, sx, sy, rw, rh, inner, radius, feather, rotationDegrees = 0f)
        val spun = blurWindow(src, sx, sy, rw, rh, inner, radius, feather, rotationDegrees = 90f)

        // Patch position of the ellipse's top and bottom tips (world (50,15)
        // and (50,45)).
        val topX = 50 - sx
        val topY = 15 - sy
        val bottomX = 50 - sx
        val bottomY = 45 - sy
        val flatGap = abs(luminance(flat.getPixel(bottomX, bottomY)) - luminance(flat.getPixel(topX, topY)))
        val spunTop = luminance(spun.getPixel(topX, topY))
        val spunBottom = luminance(spun.getPixel(bottomX, bottomY))

        assertTrue("90° top must be dark (black zone), got $spunTop", spunTop < 110)
        assertTrue("90° bottom must be light (white zone), got $spunBottom", spunBottom > 150)
        assertTrue("90° top/bottom diverge along the rotated content: top=$spunTop bottom=$spunBottom",
            spunBottom - spunTop > 60f)
        // At 0° both tips sit on the same boundary column, so they stay similar.
        assertTrue("0° top/bottom both straddle the boundary column (gap=$flatGap)", flatGap < 40f)
        // The 90° top tip re-read the far black zone, so it genuinely differs
        // from the 0° boundary-grey sample at the same patch pixel.
        assertTrue("90° content must differ from 0° at the top tip",
            abs(spunTop - luminance(flat.getPixel(topX, topY))) > 20f)
    }

    @Test
    fun `a rotation change rebuilds the live piece against the rotated footprint`() {
        val density = Density(1f)
        // Half-resolution preview (frame 1000×800 -> source 500×400). The item
        // sits centred on the two-tone boundary at x=250, so rotating 90° flips
        // which photo zones the oval reads.
        val source = twoToneBitmap(500, 400)
        val frame = TextBlurFrame(0f, 0f, 1000f, 800f)
        val base = TextOverlay(
            id = "r", text = "Blur", position = Offset(500f, 400f),
            fontSize = 60f, backgroundShape = TextBackgroundShape.BLUR, backgroundBlur = 0.7f
        )
        val container: Context = ApplicationProvider.getApplicationContext()
        val flat = requireNotNull(
            buildTextBlurPiece(container, source, base, Size(340f, 110f), frame, density)
        )
        val rotated = requireNotNull(
            buildTextBlurPiece(container, source, base.copy(rotation = 90f), Size(340f, 110f), frame, density)
        )

        // The window bounds are the UNROTATED scaled cover (same src rect); the
        // rotation lives in the sampled CONTENT.
        assertEquals(flat.src, rotated.src)
        assertTrue("rotation must invalidate the live cache key",
            textBlurPieceKey(base) != textBlurPieceKey(base.copy(rotation = 90f)))
        assertFalse("rotated piece content must rebuild against the rotated footprint",
            argbPixels(flat.bitmap).contentEquals(argbPixels(rotated.bitmap)))
    }
}