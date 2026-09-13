package codes.pepper.whimsicalart.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.BeautyAutoEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyLipstickEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyPenEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautySmoothingEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.beauty.domain.ML_MAX_PIXELS
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyParams
import codes.pepper.whimsicalart.feature.beauty.ui.BeautyTool
import codes.pepper.whimsicalart.feature.editor.domain.CropEffect
import codes.pepper.whimsicalart.feature.editor.domain.BitmapDiffEffect
import codes.pepper.whimsicalart.feature.editor.domain.FrameEffect
import codes.pepper.whimsicalart.feature.editor.domain.FrameLayer
import codes.pepper.whimsicalart.feature.editor.domain.FrameStyle
import codes.pepper.whimsicalart.feature.editor.domain.FrameTier
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.backgroundBlurRadiusPx
import codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.GeometryTracker
import codes.pepper.whimsicalart.feature.editor.ui.buildStackSnapshot
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.HistoryKind
import codes.pepper.whimsicalart.feature.editor.ui.background.BackgroundPresets
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {

    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
    }

    @Test
    fun `clearBackground resets the subject-detection-failed flag`() {
        // Reproduces the Background-tools no-op report path: when segmentation
        // fails the state must carry a persistent failure flag (surfaced by the
        // panel) and clearing the background must reset it so Blur/Replace can
        // retry cleanly.
        assertFalse(viewModel.uiState.value.subjectDetectionFailed)
        viewModel.clearSubjectMask()
        assertFalse(viewModel.uiState.value.subjectDetectionFailed)
        viewModel.clearBackground()
        assertFalse(viewModel.uiState.value.subjectDetectionFailed)
    }

    @Test
    fun `clearSubjectMask clears the subject mask`() {
        // No real segmentation can run under Robolectric (the tflite interpreter
        // is not available), so we only assert the clear path doesn't crash and
        // respects the flag reset.
        viewModel.clearSubjectMask()
        assertNull(viewModel.uiState.value.subjectMask)
        assertFalse(viewModel.uiState.value.subjectDetectionFailed)
    }

    // --- Background subject mask × shared geometry context (P5) -------------

    private fun redGreenOriginal(): Bitmap {
        // 12x8: left half RED, right half GREEN — lets each assertion tell the
        // pristine fold base apart from a downscaled resident copy by shape and
        // by colour. Built with eraseColor/setPixel only: Robolectric's graphics
        // shadows do NOT reliably rasterize Canvas draw ops.
        val bmp = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.RED)
        for (x in 6 until 12) {
            for (y in 0 until 8) {
                bmp.setPixel(x, y, Color.GREEN)
            }
        }
        return bmp
    }

    @Test
    fun `backgroundGeometryEffects keeps only the geometry-changing effects before the background layer`() {
        // The shared geometry context folds the stack UP TO the background
        // layer's position — effects after (or at) the layer must not reshape
        // the base the mask is segmented over.
        val original = redGreenOriginal()
        viewModel.setBitmaps(original, original)
        val cropBefore = CropEffect(rect = Rect(0f, 0f, 0.5f, 1f))
        val bg = codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect(
            mode = codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.BLUR
        )
        val cropAfter = CropEffect(rect = Rect(0.25f, 0.25f, 0.75f, 0.75f))
        viewModel.setEffectStack(listOf(cropBefore, bg, cropAfter))
        assertEquals(listOf(cropBefore), viewModel.backgroundGeometryEffects())
    }

    @Test
    fun `backgroundGeometryEffects is empty when nothing geometry-changing precedes the background`() {
        // A background FIRST in the stack (nothing before it) must produce an
        // empty fold base — effects AFTER the layer cannot reshape the mask's
        // pixel space.
        val original = redGreenOriginal()
        viewModel.setBitmaps(original, original)
        viewModel.setEffectStack(
            listOf(
                codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect(
                    mode = codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.BLUR
                ),
                CropEffect(rect = Rect(0.25f, 0.25f, 0.75f, 0.75f))
            )
        )
        assertEquals(
            "stack=" + viewModel.uiState.value.effectStack,
            emptyList<codes.pepper.whimsicalart.feature.editor.domain.StackEffect>(),
            viewModel.backgroundGeometryEffects()
        )
    }

    @Test
    fun `subject mask base folds the geometry up to the background position, never the resident bitmap`() {
        // The fold base = pristine original (12x8) cropped in half horizontally
        // → a 6x8 all-RED base, even though the resident editedBitmap is a
        // downscaled 6x4 two-tone copy. A mask taken from the resident (the old
        // segmentSubject source) would have the wrong geometry AND the wrong
        // pixel space once any deformer precedes the background layer.
        val original = redGreenOriginal()
        val residentCopy = Bitmap.createScaledBitmap(original, 6, 4, true)
        viewModel.setBitmaps(original, residentCopy)
        viewModel.setEffectStack(
            listOf(
                CropEffect(rect = Rect(0f, 0f, 0.5f, 1f)),
                codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect(
                    mode = codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.BLUR
                )
            )
        )
        val base = runBlocking { viewModel.subjectMaskGeometryBase(1f) }
        assertNotNull(base)
        assertEquals(6, base!!.width)
        assertEquals(8, base.height)
        assertEquals("the fold keeps the pre-crop half RED", Color.RED, base.getPixel(0, 0))
        assertEquals("the fold base is 8px tall, unlike the 6x4 resident", 8, base.height)
    }

    @Test
    fun `subject mask base is the pristine original when nothing geometry-changing precedes the background`() {
        val original = redGreenOriginal()
        val residentCopy = Bitmap.createScaledBitmap(original, 6, 4, true)
        viewModel.setBitmaps(original, residentCopy)
        // A background FIRST in the stack (or alone) resolves against the
        // original geometry image — still 12x8, still two-tone.
        viewModel.setEffectStack(
            listOf(
                codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect(
                    mode = codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.BLUR
                ),
                CropEffect(rect = Rect(0f, 0f, 0.5f, 1f))
            )
        )
        val base = runBlocking { viewModel.subjectMaskGeometryBase(1f) }
        assertNotNull(base)
        assertEquals(12, base!!.width)
        assertEquals(8, base.height)
        assertEquals(Color.RED, base.getPixel(0, 0))
        assertEquals(Color.GREEN, base.getPixel(11, 0))
    }

    @Test
    fun `geometry track caps a multi-MP footprint and the subject mask base lands inside the ML budget`() {
        // A 4096x2048 (8 MP) source: the geometry track folds at ~0.707 —
        // inside the adaptive 1–4 MP ML budget — and the full-quality
        // subject-mask geometry-track base is at/under ML_MAX_PIXELS, so no
        // full-size deformation intermediate is ever folded (docs/effect_stack.md
        // → Lazy beauty geometry).
        val large = Bitmap.createBitmap(4096, 2048, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        viewModel.setBitmaps(large, large)
        viewModel.setEffectStack(
            listOf(
                codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect(
                    mode = codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.BLUR
                )
            )
        )
        val geometryScale = viewModel.geometryTrackScaleFor(emptyList())
        assertTrue("expected a real fold downscale for an 8 MP source: $geometryScale", geometryScale < 1f)
        val base = runBlocking { viewModel.subjectMaskGeometryBase(1f) }
        assertNotNull(base)
        assertTrue(
            "subject-mask geometry-track base (${base!!.width}x${base.height}) exceeds ML_MAX_PIXELS",
            base.width * base.height <= ML_MAX_PIXELS
        )
        assertTrue("the capped base is never upscaled beyond the original", base.width <= large.width)
        large.recycle()
        base.recycle()
    }

    // --- Background survives geometry-changing commits (next.md bugfix) ------

    @Test
    fun `buildStackSnapshot keeps the committed background when the live mask was invalidated`() {
        // A geometry-changing commit (pen) nulls the LIVE subjectMask via
        // invalidateSubjectMask; the next snapshot's bundle then carries no
        // background layer. The committed BackgroundEffect is self-sufficient
        // and must be re-emitted from the effectStack, or orderedMerge would
        // delete it from the document and the background removal would vanish.
        val original = redGreenOriginal()
        viewModel.setBitmaps(original, original)
        val mask = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        viewModel.setEffectStack(
            listOf(
                BackgroundEffect(
                    mode = BackgroundMode.BLUR,
                    subjectMask = mask,
                    blurRadius = 12f
                )
            )
        )
        val snapshot = buildStackSnapshot(viewModel.uiState.value, EditorRenderBundle())
        val kept = snapshot.filterIsInstance<BackgroundEffect>()
        assertEquals("exactly one background layer survives the invalidated-mask snapshot", 1, kept.size)
        assertEquals("the committed mask is carried", mask, kept.single().subjectMask)
    }

    @Test
    fun `a pen commit after a background keeps the background in the committed stack and the render`() {
        // Full reported flow: background removal committed, then a pen commit
        // (geometry-changing → invalidateSubjectMask nulls the LIVE mask) must
        // NOT remove the background from the committed document, and the
        // committed render must keep applying it.
        val original = redGreenOriginal()
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "bg_pen_commit.jpg")
        FileOutputStream(file).use { out -> original.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        original.recycle()
        viewModel.setImageUri(Uri.fromFile(file))
        val mask = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val bg = BackgroundEffect(
            mode = BackgroundMode.BLUR,
            subjectMask = mask,
            blurRadius = 12f
        )
        viewModel.setEffectStack(listOf(bg))
        val before = runBlocking { viewModel.renderCommittedBitmap(EditorRenderBundle(), scale = 1f) }
        assertNotNull(before)

        // commitCurrentTool flow: snapshot from the CURRENT (live-mask-less)
        // state + the fresh pen layer.
        val snapshot = buildStackSnapshot(viewModel.uiState.value, EditorRenderBundle())
        val pen = BitmapDiffEffect(tool = EditTool.PEN, strokes = emptyList())
        viewModel.syncLayerState(
            snapshot + pen,
            (snapshot + pen).associate { it.layerKey to it.layerKey }
        )

        val stack = viewModel.uiState.value.effectStack
        assertTrue("background survives the pen commit", stack.any { it is BackgroundEffect })
        assertEquals("live subject mask is invalidated by the pen commit", null, viewModel.uiState.value.subjectMask)

        val after = runBlocking { viewModel.renderCommittedBitmap(EditorRenderBundle(), scale = 1f) }
        assertNotNull(after)
        assertTrue(
            "the committed render must still apply the background after the pen commit",
            samePixels(before!!, after!!)
        )
    }

    private fun samePixels(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val pa = IntArray(a.width * a.height)
        val pb = IntArray(b.width * b.height)
        a.getPixels(pa, 0, a.width, 0, 0, a.width, a.height)
        b.getPixels(pb, 0, b.width, 0, 0, b.width, b.height)
        return pa.contentEquals(pb)
    }

    @Test
    fun `a REPLACE background replays the placement through the deforming fold`() {
        // 32x32 so the 4px feathered edge (SoftEdgeBand.DEFAULT_WIDTH) never
        // reaches the centre 16x16 subject blob (distance-to-edge 8).
        val context: Context = ApplicationProvider.getApplicationContext()
        val source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val mask = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (y in 8 until 24) for (x in 8 until 24) setPixel(x, y, Color.WHITE)
        }
        val placed = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val stack = listOf(
            BackgroundEffect(
                mode = BackgroundMode.REPLACE,
                subjectMask = mask,
                backgroundImage = placed
            )
        )
        val out = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, source, stack, scale = 1f)
        }
        assertEquals(32, out.width)
        assertEquals(32, out.height)
        val outPixels = IntArray(32 * 32)
        out.getPixels(outPixels, 0, 32, 0, 0, 32, 32)
        assertTrue("backdrop pixels are replaced with the placement (blue visible)",
            outPixels.any { it == Color.BLUE })
        val centre = outPixels[16 * 32 + 16]
        assertTrue(
            "the subject blob keeps its own pixels (centre red over any feathered blend); got #" +
                Integer.toHexString(centre),
            Color.red(centre) > 200 && Color.blue(centre) < 100
        )
        out.recycle()
        mask.recycle()
        placed.recycle()
        source.recycle()
    }

    // --- Background two-stage mask: provisional preview-res pass first --------

    @Test
    fun `provisional mask scale folds only sources above the 2K budget`() {
        assertEquals(1f, viewModel.provisionalMaskScaleFor(1000, 800), 1e-4f)
        assertEquals(1f, viewModel.provisionalMaskScaleFor(0, 0), 1e-4f)
        val w4000 = viewModel.provisionalMaskScaleFor(4000, 3000)
        assertTrue("4000x3000 must fold below the 2048 budget: $w4000", w4000 < 1f)
        assertEquals(2048, (4000 * w4000).roundToInt())
        val w8000 = viewModel.provisionalMaskScaleFor(8000, 6000)
        assertEquals(0.256f, w8000, 1e-4f)
    }

    @Test
    fun `provisional mask base for a huge source is inside the 2K preview budget`() {
        val large = Bitmap.createBitmap(4096, 2048, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        viewModel.setBitmaps(large, large)
        viewModel.setEffectStack(
            listOf(
                codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect(
                    mode = BackgroundMode.BLUR
                )
            )
        )
        val scale = viewModel.provisionalMaskScaleFor(4096, 2048)
        val base = runBlocking { viewModel.subjectMaskGeometryBase(scale) }
        assertNotNull(base)
        assertTrue(
            "provisional base ${base!!.width}x${base.height} exceeds the 2K preview budget",
            base.width <= 2048 && base.height <= 2048
        )
        base.recycle()
        large.recycle()
    }

    @Test
    fun `two-stage subject detection settles cleanly when segmentation is unavailable`() {
        // Under Robolectric no real tflite model loads, so BOTH the
        // provisional preview-res stage and the full-quality geometry-track
        // stage resolve null masks — the flow must still settle (no stuck
        // spinner, no crash) and report the failure, exactly like the
        // single-pass version did.
        viewModel.segmentSubject()
        assertTrue(viewModel.uiState.value.isSegmenting)
        val looper = shadowOf(Looper.getMainLooper())
        repeat(2000) {
            looper.idle()
            if (!viewModel.uiState.value.isSegmenting) return@repeat
            Thread.sleep(5)
        }
        assertFalse(viewModel.uiState.value.isSegmenting)
        assertNull(viewModel.uiState.value.subjectMask)
        assertTrue(viewModel.uiState.value.subjectDetectionFailed)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `full-size bind folds beauty geometry at the capped scale and stamps the base dims on the geometry`() {
        // [crop 0.8x0.8, beauty] on a 8 MP source: the FULL-SIZE (committed /
        // zoom>100% / save) bind must ask the geometry track for the CAPPED
        // base — never a full-size deformation intermediate — and record that
        // base's dims on the resolved geometry so the render loop rescales it up
        // to the full-size colour fold it renders over.
        val large = Bitmap.createBitmap(4096, 2048, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        viewModel.setBitmaps(large, large)
        val crop = CropEffect(rect = Rect(0.1f, 0.1f, 0.9f, 0.9f))
        val beauty = BeautySmoothingEffect(intensity = 30f)
        viewModel.setEffectStack(listOf(crop, beauty))
        // The crop keeps the footprint multi-MP (≈5.4 MP > 4 MP), so the track
        // must fold the full-size bind below 1.
        val expectedScale = viewModel.geometryTrackScaleFor(listOf(crop))
        assertTrue("expected the crop footprint to exceed the ML cap: $expectedScale", expectedScale < 1f)
        val filler = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val requested = mutableListOf<Float>()
        val resolved = runBlocking {
            viewModel.bindBeautyGeometries(listOf(crop, beauty), scale = 1f) { _, s ->
                requested += s
                filler
            }
        }
        val bound = resolved[1] as BeautyStackEffect
        assertNotNull(bound.geometry)
        assertEquals(
            "the full-size bind must fold at the capped geometry-track scale",
            expectedScale, requested.single(), 1e-4f
        )
        assertEquals("the geometry is anchored to the capped base width", filler.width, bound.geometry!!.baseWidth)
        assertEquals("the geometry is anchored to the capped base height", filler.height, bound.geometry!!.baseHeight)
        large.recycle()
        filler.recycle()
    }

    @Test
    fun `initial state has default values`() {
        val state = viewModel.uiState.value
        assertNull(state.imageUri)
        assertFalse(state.isProcessing)
        assertNull(state.error)
        assertNull(state.selectedTool)
        assertEquals(0f, state.brightness)
        assertEquals(0f, state.contrast)
        assertEquals(0f, state.saturation)
        assertEquals(0f, state.sharpness)
        assertEquals(0f, state.rotation)
        assertFalse(state.flipHorizontal)
        assertFalse(state.flipVertical)
        assertNull(state.cropRect)
        assertTrue(state.history.isEmpty())
        assertEquals(-1, state.historyIndex)
    }

    @Test
    fun `setImageUri updates state`() {
        val uri = Uri.parse("content://media/1")
        viewModel.setImageUri(uri)
        assertEquals(uri, viewModel.uiState.value.imageUri)
    }

    @Test
    fun `selectTool updates selectedTool`() {
        viewModel.selectTool(EditTool.BRIGHTNESS)
        assertEquals(EditTool.BRIGHTNESS, viewModel.uiState.value.selectedTool)
    }

    @Test
    fun `selectTool null clears selection`() {
        viewModel.selectTool(EditTool.CROP)
        viewModel.selectTool(null)
        assertNull(viewModel.uiState.value.selectedTool)
    }

    @Test
    fun `updateBrightness updates state`() {
        viewModel.updateBrightness(50f)
        assertEquals(50f, viewModel.uiState.value.brightness)
    }

    @Test
    fun `updateContrast updates state`() {
        viewModel.updateContrast(-30f)
        assertEquals(-30f, viewModel.uiState.value.contrast)
    }

    @Test
    fun `updateSaturation updates state`() {
        viewModel.updateSaturation(75f)
        assertEquals(75f, viewModel.uiState.value.saturation)
    }

    @Test
    fun `updateSharpness updates state`() {
        viewModel.updateSharpness(42f)
        assertEquals(42f, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `rotateLeft wraps to 270 degrees`() {
        viewModel.rotateLeft()
        assertEquals(270f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `rotateRight increments rotation by 90`() {
        viewModel.rotateRight()
        assertEquals(90f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `rotateLeft followed by rotateRight returns to zero`() {
        viewModel.rotateLeft()
        assertEquals(270f, viewModel.uiState.value.rotation)
        viewModel.rotateRight()
        assertEquals(0f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `rotateRight wraps around to 0 after four rotations`() {
        for (i in 1..4) viewModel.rotateRight()
        assertEquals(0f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `flipHorizontal toggles state`() {
        assertFalse(viewModel.uiState.value.flipHorizontal)
        viewModel.flipHorizontal()
        assertTrue(viewModel.uiState.value.flipHorizontal)
        viewModel.flipHorizontal()
        assertFalse(viewModel.uiState.value.flipHorizontal)
    }

    @Test
    fun `flipVertical toggles state`() {
        assertFalse(viewModel.uiState.value.flipVertical)
        viewModel.flipVertical()
        assertTrue(viewModel.uiState.value.flipVertical)
        viewModel.flipVertical()
        assertFalse(viewModel.uiState.value.flipVertical)
    }

    @Test
    fun `updateCrop updates cropRect`() {
        val rect = Rect(10f, 20f, 100f, 200f)
        viewModel.updateCrop(rect)
        assertEquals(rect, viewModel.uiState.value.cropRect)
    }

    @Test
    fun `applyCrop commits one-shot without clearing the rect`() {
        val rect = Rect(0.1f, 0.2f, 0.9f, 0.8f)
        viewModel.updateCrop(rect)
        viewModel.applyCrop()

        val state = viewModel.uiState.value
        // Undo history recorded exactly once (the live adjustments do not).
        assertEquals(1, state.history.size)
        // The committed crop rect persists (it is baked into every render).
        assertEquals(rect, state.cropRect)
    }

    @Test
    fun `resetAdjustments clears all adjustment values`() {
        viewModel.updateBrightness(50f)
        viewModel.updateContrast(-30f)
        viewModel.updateSaturation(75f)
        viewModel.updateSharpness(42f)
        
        viewModel.resetAdjustments()
        
        assertEquals(0f, viewModel.uiState.value.brightness)
        assertEquals(0f, viewModel.uiState.value.contrast)
        assertEquals(0f, viewModel.uiState.value.saturation)
        assertEquals(0f, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `rotateRight saves to history`() {
        viewModel.rotateRight()
        assertEquals(1, viewModel.uiState.value.history.size)
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `undo restores previous state`() {
        viewModel.rotateRight()
        viewModel.rotateRight()

        viewModel.undo()

        assertEquals(90f, viewModel.uiState.value.rotation)
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `redo restores next state`() {
        viewModel.rotateRight()
        viewModel.rotateRight()

        viewModel.undo()
        viewModel.redo()

        assertEquals(180f, viewModel.uiState.value.rotation)
        assertEquals(1, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `undo at index 0 does nothing`() {
        viewModel.rotateRight()
        viewModel.undo()
        viewModel.undo()
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `redo at end does nothing`() {
        viewModel.rotateRight()
        viewModel.redo()
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    // --- Undo / redo kinds + per-kind history caps (docs/effect_stack.md) ----

    @Test
    fun `a changed syncLayerState commit creates COARSE_GRAINED checkpoints and undo reverts the commit`() {
        // The layer-commit-on-lost-focus boundary is ITSELF an undo checkpoint:
        // pre + post COARSE_GRAINED entries, so one Undo reverts exactly the commit
        // (restoring the pre-commit stack) and Redo restores the committed layer.
        val adjEffect = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f)
        viewModel.syncLayerState(
            listOf(adjEffect),
            mapOf(adjEffect.layerKey to "brightness:20")
        )
        assertEquals(2, viewModel.uiState.value.history.size)
        assertTrue(viewModel.uiState.value.history.all { it.kind == HistoryKind.COARSE_GRAINED })
        assertEquals(listOf("layer:BRIGHTNESS"), viewModel.uiState.value.effectStack.map { it.layerKey })

        viewModel.undo()
        assertTrue("pre-commit stack restored", viewModel.uiState.value.effectStack.isEmpty())
        assertEquals(0, viewModel.uiState.value.historyIndex)

        viewModel.redo()
        assertEquals(listOf("layer:BRIGHTNESS"), viewModel.uiState.value.effectStack.map { it.layerKey })
    }

    @Test
    fun `an unchanged syncLayerState commit records no checkpoint`() {
        // Re-entering an untouched tool (same merged stack + fingerprints) is the
        // common lost-focus case and must NOT flood the history with no-op commits.
        viewModel.syncLayerState(emptyList(), emptyMap())
        assertEquals(0, viewModel.uiState.value.history.size)
    }

    @Test
    fun `history caps keep only the newest FINE_GRAINED entries while COARSE_GRAINED entries survive`() {
        // selectTool records the coarse_grained pre-session baseline; 35
        // FINE_GRAINED toggles then stay capped at FINE_GRAINED_LIMIT (30) — only
        // the OLDEST FINE_GRAINED entries are evicted — and the older
        // COARSE_GRAINED baseline is never dropped. The newest checkpoint (the
        // just-recorded entry, always the current index) survives.
        viewModel.selectTool(EditTool.FILTERS)
        assertEquals(1, viewModel.uiState.value.history.count { it.kind == HistoryKind.COARSE_GRAINED })
        repeat(35) { viewModel.toggleEnhance() }
        val history = viewModel.uiState.value.history
        assertEquals(1, history.count { it.kind == HistoryKind.COARSE_GRAINED })
        assertEquals(30, history.count { it.kind == HistoryKind.FINE_GRAINED })
        assertEquals(31, history.size)
        assertEquals(HistoryKind.COARSE_GRAINED, history.first().kind)
        assertEquals(HistoryKind.FINE_GRAINED, history.last().kind)
        assertEquals("current index is the newest, never evicted", 30, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `interleaved COARSE_GRAINED and FINE_GRAINED checkpoints undo and redo in exact event order`() {
        viewModel.selectTool(EditTool.FILTERS) // E0 COARSE_GRAINED baseline
        viewModel.toggleEnhance() // E1 FINE_GRAINED (pre-toggle state: enhance=false)
        val adjEffect = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 10f)
        viewModel.syncLayerState( // E2 pre-commit + E3 post-commit, both COARSE_GRAINED
            listOf(adjEffect),
            mapOf(adjEffect.layerKey to "brightness:10")
        )
        viewModel.toggleEnhance() // E4 FINE_GRAINED (pre-toggle state: enhance=true)
        assertEquals(
            listOf(
                HistoryKind.COARSE_GRAINED, HistoryKind.FINE_GRAINED,
                HistoryKind.COARSE_GRAINED, HistoryKind.COARSE_GRAINED, HistoryKind.FINE_GRAINED
            ),
            viewModel.uiState.value.history.map { it.kind }
        )
        assertEquals(listOf("layer:BRIGHTNESS"), viewModel.uiState.value.effectStack.map { it.layerKey })

        // Undo walks the checkpoints in event order.
        viewModel.undo() // E3: the last one-shot (toggle) reverted
        assertTrue(viewModel.uiState.value.enhanceEnabled)
        assertEquals(listOf("layer:BRIGHTNESS"), viewModel.uiState.value.effectStack.map { it.layerKey })
        viewModel.undo() // E2: the layer commit reverted (pre-commit stack)
        assertTrue(viewModel.uiState.value.effectStack.isEmpty())
        assertTrue(viewModel.uiState.value.enhanceEnabled)
        viewModel.undo() // E1
        assertFalse(viewModel.uiState.value.enhanceEnabled)
        viewModel.undo() // E0
        assertFalse(viewModel.uiState.value.enhanceEnabled)

        // Redo walks back up in the exact same event order.
        viewModel.redo() // E1
        viewModel.redo() // E2
        viewModel.redo() // E3
        assertTrue(viewModel.uiState.value.enhanceEnabled)
        assertEquals(listOf("layer:BRIGHTNESS"), viewModel.uiState.value.effectStack.map { it.layerKey })
        viewModel.redo() // E4 (pre-toggle of the last flip)
        assertTrue(viewModel.uiState.value.enhanceEnabled)
        assertEquals(4, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `mergeLayers records coarse_grained checkpoints so undo restores the pre-merge stack`() {
        val original = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        viewModel.setBitmaps(original, original)
        viewModel.updateBrightness(50f) // live only — never committed to the stack
        viewModel.mergeLayers(codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle())
        val state = viewModel.uiState.value
        assertTrue(state.hasMerged)
        assertEquals(1, state.effectStack.size)
        assertEquals(2, state.history.count { it.kind == HistoryKind.COARSE_GRAINED })

        viewModel.undo()
        assertTrue("pre-merge stack restored", viewModel.uiState.value.effectStack.isEmpty())
        assertEquals(50f, viewModel.uiState.value.brightness)

        viewModel.redo()
        assertEquals(1, viewModel.uiState.value.effectStack.size)
    }

    @Test
    fun `setBitmaps stores original and edited bitmaps for the compare overlay`() {
        val original = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        val edited = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        viewModel.setBitmaps(original, edited)
        assertSame(original, viewModel.uiState.value.originalBitmap)
        assertSame(edited, viewModel.uiState.value.editedBitmap)
        assertNull(viewModel.uiState.value.sharpenedPreview)
    }

    @Test
    fun `baseImageSize mirrors the fold base - edited after setBitmaps and merged after mergeLayers`() {
        val original = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        val edited = android.graphics.Bitmap.createBitmap(8, 6, android.graphics.Bitmap.Config.ARGB_8888)
        viewModel.setBitmaps(original, edited)
        // The test hook sets the fold base to the `edited` bitmap.
        assertEquals(
            "fold base dims after setBitmaps come from the edited bitmap",
            Size(8f, 6f), viewModel.uiState.value.baseImageSize
        )

        // A real merge repoints the fold base to the flattened merged result.
        viewModel.updateBrightness(50f) // live adjustment, committed at merge
        viewModel.mergeLayers(codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle())
        val state = viewModel.uiState.value
        assertTrue(state.hasMerged)
        assertNotNull("mergeLayers sets a new fold base", state.baseImageSize)
        assertEquals(
            "post-merge fold base dims match the merged edited bitmap",
            Size(state.editedBitmap!!.width.toFloat(), state.editedBitmap!!.height.toFloat()),
            state.baseImageSize
        )
    }

    @Test
    fun `loadImageBitmap decodes the bounded source off the caller thread and publishes both bitmaps`() {
        // PERF (editor jank): the editor's first photo decode used to run INLINE
        // on the main thread via the screen's LaunchedEffect, skipping ~39 frames
        // during init (HWUI decode drops on the main thread before "Skipped 39
        // frames" in log.7.txt). The fix moves the decode onto Dispatchers.IO and
        // publishes asynchronously — so right after the call returns the working
        // bitmaps must STILL be null (nothing decoded synchronously on the caller
        // thread), then land with correct bounded geometry + colours.
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "editor_load_image.jpg")
        // 2400x1600, left half BLACK right half WHITE — large enough that the
        // 2048-bound decode must downscale (sampleSize 2 → 1200x800). Luma-only
        // regions survive JPEG losslessly at quality 100 (chroma-subsampled
        // red/green halves decode with bleed, so avoid them). Built with
        // eraseColor + setPixel only (Robolectric rasterizes drawn pixels
        // unreliably).
        val src = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 0 until height) {
                for (x in 0 until width / 2) {
                    setPixel(x, y, Color.BLACK)
                }
            }
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        val uri = Uri.fromFile(file)

        viewModel.loadImageBitmap(uri)
        // If the decode ran synchronously on the caller (main) thread like the
        // old inline decode, the bitmap would ALREADY be published here.
        assertNull("decode must not run synchronously on the caller thread", viewModel.uiState.value.originalBitmap)

        val looper = shadowOf(Looper.getMainLooper())
        var published: Bitmap? = null
        repeat(2000) {
            looper.idle()
            viewModel.uiState.value.originalBitmap?.let {
                published = it
                return@repeat
            }
            Thread.sleep(5)
        }
        assertNotNull("bounded decode must eventually be published", published)
        assertSame(published, viewModel.uiState.value.editedBitmap)
        assertEquals(1200, published!!.width)
        assertEquals(800, published!!.height)
        // Robolectric's native JPEG decode has a known artifact: the right half
        // of an inSampleSize-sampled decode decodes dark (green and white right
        // halves both came out black), so only the faithful left half is pinned.
        // Luma-only source + tolerance keeps this pass across decoders.
        val blackPx = published!!.getPixel(400, 400)
        assertTrue("left half should stay black, got ${Color.red(blackPx)}", Color.red(blackPx) < 20)
        assertFalse(viewModel.uiState.value.isImageLoading)
    }

    @Test
    fun `loadImageBitmap publishes a load error instead of a silent blank when the source cannot be decoded`() {
        // A shared uri whose decode fails (source gone / permission denied) must
        // surface an explicit error the screen can retry from — never an empty
        // canvas. Cold share-to-open lands here when the sender's transient
        // grant is revoked.
        val missing = Uri.fromFile(
            File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "missing.jpg")
        )
        viewModel.setImageUri(missing)
        viewModel.loadImageBitmap(missing)

        val looper = shadowOf(Looper.getMainLooper())
        var settled = false
        repeat(2000) {
            looper.idle()
            if (!viewModel.uiState.value.isImageLoading) {
                settled = true
                return@repeat
            }
            Thread.sleep(5)
        }
        assertTrue("the load must settle (not hang)", settled)
        assertNull("no bitmap for an undecodable source", viewModel.uiState.value.originalBitmap)
        assertNotNull(
            "a failed load publishes an error state for the retry UI",
            viewModel.uiState.value.error
        )
        // Retry re-attempts the decode (same failure, message re-surfaced, no crash).
        viewModel.retryLoadImage()
        repeat(2000) {
            looper.idle()
            if (!viewModel.uiState.value.isImageLoading) {
                return@repeat
            }
            Thread.sleep(5)
        }
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `rapid frenetic sharpness changes settle on the last value without a preview source`() {
        var last = 0f
        for (i in 1..120) {
            last = ((i * 7) % 101).toFloat()
            viewModel.updateSharpness(last)
        }
        assertEquals(last, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `sharpness bounced to zero never leaves a stale preview`() {
        viewModel.updateSharpness(50f)
        viewModel.updateSharpness(0f)
        viewModel.updateSharpness(-40f)
        viewModel.updateSharpness(0f)
        viewModel.updateSharpness(80f)
        viewModel.updateSharpness(0f)
        assertEquals(0f, viewModel.uiState.value.sharpness)
        assertNull(viewModel.uiState.value.sharpenedPreview)
    }

    @Test
    fun `selecting sharpen tool and sliding does not crash when the uri cannot be decoded`() {
        viewModel.setImageUri(Uri.parse("content://codes.pepper.whimsicalart.test/missing"))
        viewModel.selectTool(EditTool.SHARPEN)
        for (i in 1..60) viewModel.updateSharpness((i % 100).toFloat())
        viewModel.updateSharpness(0f)
        viewModel.updateSharpness(1f)
        for (i in 1..40) viewModel.updateSharpness(((i * 13) % 100).toFloat())
        assertEquals(20f, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `startComparing then stopComparing toggles the compare flag`() {
        assertFalse(viewModel.uiState.value.isComparing)
        viewModel.startComparing()
        assertTrue(viewModel.uiState.value.isComparing)
        viewModel.stopComparing()
        assertFalse(viewModel.uiState.value.isComparing)
    }

    @Test
    fun `setEffectStack stores the snapshot with stable ids`() {
        val effect = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 40f
        )
        viewModel.setEffectStack(listOf(effect))
        assertEquals(listOf(effect.id), viewModel.uiState.value.effectStack.map { it.id })
        assertTrue(viewModel.uiState.value.effectStack.isNotEmpty())
    }

    @Test
    fun `selectStackEffect restores remembered parameter and selects the tool`() {
        val effect = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.SATURATION, value = 75f
        )
        viewModel.selectTool(EditTool.TINT)
        viewModel.setEffectStack(listOf(effect))
        viewModel.selectStackEffect(effect.id)
        assertEquals(EditTool.SATURATION, viewModel.uiState.value.selectedTool)
        assertEquals(75f, viewModel.uiState.value.saturation)
    }

    @Test
    fun `selectStackEffect on merged layer does not redirect`() {
        viewModel.selectTool(EditTool.CONTRAST)
        val merged = codes.pepper.whimsicalart.feature.editor.domain.MergedEffect()
        viewModel.setEffectStack(listOf(merged))
        viewModel.selectStackEffect(merged.id)
        assertNotEquals(null, viewModel.uiState.value.selectedTool)
    }

    @Test
    fun `mergeLayers resets adjustments and marks merged but keeps original for compare`() {
        val original = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        viewModel.setBitmaps(original, original)
        viewModel.updateBrightness(50f)
        viewModel.setStyleFilter(codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter.FILMIC)
        viewModel.toggleEnhance()
        viewModel.mergeLayers(
            codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle()
        )
        val state = viewModel.uiState.value
        assertTrue(state.hasMerged)
        assertEquals(0f, state.brightness)
        assertNull(state.styleFilter)
        assertFalse(state.enhanceEnabled)
        assertSame(original, state.originalBitmap)
        assertEquals(1, state.effectStack.size)
        // The merged layer's thumbnail is the preview-resolution stand-in, never
        // the full merged bitmap pinned at full size (Layers-window invariant).
        state.layerThumbnails[state.effectStack.single().layerKey]?.let { thumb ->
            assertTrue(maxOf(thumb.width, thumb.height) <= 100)
            assertTrue(thumb.width <= original.width && thumb.height <= original.height)
        } ?: fail("merged layer is missing its layer thumbnail")
    }

    @Test
    fun `mergeLayers resets EVERY tool control including transform crop frames filters and background`() {
        val original = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        viewModel.setBitmaps(original, original)
        // adjustments
        viewModel.updateBrightness(50f)
        viewModel.toggleEnhance()
        // transform
        viewModel.setRotation(90f)
        viewModel.flipHorizontal()
        viewModel.flipVertical()
        // crop
        viewModel.updateCrop(Rect(0f, 0f, 10f, 10f))
        // frames (style + sliders)
        viewModel.updateInnerFrameStyle(FrameStyle.SOLID)
        viewModel.updateOuterFrameStyle(FrameStyle.ROUNDED)
        viewModel.updateInnerFrameThickness(0.3f)
        viewModel.updateOuterFrameColor(Color.GRAY)
        // filters / lens
        viewModel.setSelectedFilterId("filmic")
        viewModel.toggleLensFilter("lens_blur")
        // background
        viewModel.updateBackgroundMode(BackgroundMode.REPLACE)
        viewModel.updateBackgroundPlacementScale(1.5f)
        viewModel.updateBackgroundPlacementPan(0.2f, -0.3f)
        viewModel.updateBackgroundPlacementRotation(45f)
        // beauty
        viewModel.setBeautyParams(BeautyParams(auto = 0.5f, smoothing = 0.7f))

        viewModel.mergeLayers(
            codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle()
        )
        val state = viewModel.uiState.value
        assertTrue(state.hasMerged)
        // adjustments
        assertEquals(0f, state.brightness)
        assertFalse(state.enhanceEnabled)
        // transform
        assertEquals(0f, state.rotation)
        assertFalse(state.flipHorizontal)
        assertFalse(state.flipVertical)
        // crop
        assertNull(state.cropRect)
        // frames
        assertNull(state.innerFrameStyle)
        assertNull(state.outerFrameStyle)
        assertEquals(0.06f, state.innerFrameThickness, 1e-6f)
        assertEquals(Color.BLACK, state.outerFrameColor)
        // filters / lens
        assertNull(state.selectedFilterId)
        assertTrue(state.enabledLensFilterIds.isEmpty())
        assertNull(state.styleFilter)
        // background
        assertEquals(BackgroundMode.BLUR, state.backgroundMode)
        assertEquals(50f, state.backgroundBlurRadius)
        assertEquals(1f, state.backgroundPlacementScale)
        assertEquals(0f, state.backgroundPlacementPanX)
        assertEquals(0f, state.backgroundPlacementPanY)
        assertEquals(0f, state.backgroundPlacementRotationDegrees)
        assertFalse(state.backgroundFreeScaleUp)
        assertNull(state.selectedBackgroundRes)
        // beauty
        assertEquals(BeautyParams(), state.beauty)
    }

    @Test
    fun `frame lines toggle gates the effective committed line count`() {
        // Fresh state: lines toggle OFF → 0 lines reach the committed effect.
        assertFalse(viewModel.uiState.value.innerFrameLinesEnabled)
        assertFalse(viewModel.uiState.value.outerFrameLinesEnabled)
        assertEquals(0, viewModel.uiState.value.effectiveInnerFrameLines)
        assertEquals(0, viewModel.uiState.value.effectiveOuterFrameLines)

        // Turning the toggle ON defaults the count to 1 (spec: Lines slider 1–10).
        viewModel.updateInnerFrameLinesEnabled(true)
        assertTrue(viewModel.uiState.value.innerFrameLinesEnabled)
        assertEquals(1, viewModel.uiState.value.innerFrameLines)
        assertEquals(1, viewModel.uiState.value.effectiveInnerFrameLines)

        viewModel.updateInnerFrameLines(5)
        assertEquals(5, viewModel.uiState.value.effectiveInnerFrameLines)

        // The count can never drop below 1 while the toggle is on.
        viewModel.updateInnerFrameLines(0)
        assertEquals(1, viewModel.uiState.value.innerFrameLines)
        viewModel.updateInnerFrameLines(5)

        // Turning it OFF keeps the count but forces 0 lines downstream.
        viewModel.updateInnerFrameLinesEnabled(false)
        assertFalse(viewModel.uiState.value.innerFrameLinesEnabled)
        assertEquals(5, viewModel.uiState.value.innerFrameLines)
        assertEquals(0, viewModel.uiState.value.effectiveInnerFrameLines)

        // The outer tier mirrors the same behaviour.
        viewModel.updateOuterFrameLinesEnabled(true)
        viewModel.updateOuterFrameLines(3)
        assertEquals(3, viewModel.uiState.value.effectiveOuterFrameLines)
        viewModel.updateOuterFrameLinesEnabled(false)
        assertEquals(0, viewModel.uiState.value.effectiveOuterFrameLines)
    }

    @Test
    fun `background blur maps the percent slider so 100 percent equals the old 20 radius`() {
        // 100% ≡ the former value 20 → 0.20 × canvas width; 0% = no blur.
        // Both the preview (EditorViewModel) and the committed fold
        // (StackEffect/BitmapRenderer) compute through this single mapping.
        assertEquals("100% mask radius", 200f, backgroundBlurRadiusPx(100f, 1000), 1e-4f)
        assertEquals("0% -> no blur", 0f, backgroundBlurRadiusPx(0f, 1000), 1e-4f)
        assertEquals("50% default keeps the old 10 look", 100f, backgroundBlurRadiusPx(50f, 1000), 1e-4f)
    }

    @Test
    fun `removeStackEffect drops only the targeted entry`() {
        val a = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 10f
        )
        val b = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.CONTRAST, value = 20f
        )
        viewModel.setEffectStack(listOf(a, b))
        viewModel.removeStackEffect(a.id)
        assertEquals(listOf(b.id), viewModel.uiState.value.effectStack.map { it.id })
    }

    @Test
    fun `removeStackEffect removes a non-active layer while another tool remains selected`() {
        val a = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 10f
        )
        val b = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.CONTRAST, value = 20f
        )
        viewModel.setEffectStack(listOf(a, b))
        viewModel.selectTool(EditTool.CONTRAST)
        viewModel.removeStackEffect(a.id)
        assertEquals(listOf(b.id), viewModel.uiState.value.effectStack.map { it.id })
    }

    @Test
    fun `removeStackEffect is undoable and restores the removed entry`() {
        val a = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 10f
        )
        val b = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.CONTRAST, value = 20f
        )
        viewModel.setEffectStack(listOf(a, b))
        viewModel.removeStackEffect(a.id)
        assertEquals(listOf(b.id), viewModel.uiState.value.effectStack.map { it.id })
        viewModel.undo()
        assertEquals(listOf(a.id, b.id), viewModel.uiState.value.effectStack.map { it.id })
    }

    @Test
    fun `removeStackEffect neutralises a beauty param and entry so re-entry does not resurrect it`() {
        viewModel.updateBeautyParams { it.copy(auto = 50f) }
        // The editor is UNSTACKED while BEAUTY is focused: updateBeautyParams
        // keeps the value live in the params only, so the committed beauty layer
        // needs the same explicit setEffectStack the adjustment remove-test uses
        // below (a real lost-focus commit flows through buildStackSnapshot).
        viewModel.setEffectStack(listOf(BeautyAutoEffect(intensity = 50f)))
        val existing = viewModel.uiState.value.effectStack
            .firstOrNull { it is codes.pepper.whimsicalart.feature.editor.domain.BeautyAutoEffect }
        assertNotNull(existing)
        viewModel.removeStackEffect(existing!!.id)
        val state = viewModel.uiState.value
        assertEquals(0f, state.beauty.auto)
        assertFalse(state.effectStack.any { it is codes.pepper.whimsicalart.feature.editor.domain.BeautyAutoEffect })
        viewModel.undo()
        assertEquals(50f, viewModel.uiState.value.beauty.auto)
        assertTrue(viewModel.uiState.value.effectStack.any { it is codes.pepper.whimsicalart.feature.editor.domain.BeautyAutoEffect })
    }

    @Test
    fun `removeStackEffect neutralises the adjustment param so the next commit does not resurrect the layer`() {
        viewModel.updateBrightness(50f)
        val effect = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 50f
        )
        viewModel.setEffectStack(listOf(effect))
        viewModel.removeStackEffect(effect.id)
        val state = viewModel.uiState.value
        assertEquals(0f, state.brightness)
        assertFalse(state.effectStack.any { it is codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect })
        viewModel.undo()
        assertEquals(50f, viewModel.uiState.value.brightness)
        assertTrue(viewModel.uiState.value.effectStack.any { it is codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect })
    }

    @Test
    fun `removeStackEffect neutralises transform params so the deleted layer is not resurrected`() {
        viewModel.rotateRight()
        viewModel.flipHorizontal()
        assertTrue(viewModel.uiState.value.rotation != 0f)
        assertTrue(viewModel.uiState.value.flipHorizontal)
        val effect = codes.pepper.whimsicalart.feature.editor.domain.TransformEffect(
            rotation = 90f, flipHorizontal = true, flipVertical = false
        )
        viewModel.setEffectStack(listOf(effect))
        viewModel.removeStackEffect(effect.id)
        val state = viewModel.uiState.value
        assertEquals(0f, state.rotation)
        assertFalse(state.flipHorizontal)
        assertFalse(state.flipVertical)
        viewModel.undo()
        assertEquals(90f, viewModel.uiState.value.rotation)
        assertTrue(viewModel.uiState.value.flipHorizontal)
    }

    @Test
    fun `removeStackEffect neutralises the selected filter and style so the deleted layer is not resurrected`() {
        viewModel.setStyleFilter(codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter.FILMIC)
        val effect = codes.pepper.whimsicalart.feature.editor.domain.StyleEffect(
            filter = codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter.FILMIC
        )
        viewModel.setEffectStack(listOf(effect))
        viewModel.removeStackEffect(effect.id)
        val state = viewModel.uiState.value
        assertNull(state.styleFilter)
        assertNull(state.selectedFilterId)
        assertTrue(state.enabledLensFilterIds.isEmpty())
        viewModel.undo()
        assertEquals(
            codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter.FILMIC,
            viewModel.uiState.value.styleFilter
        )
    }

    @Test
    fun `removeStackEffect neutralises enhance and denoise params so the next commit does not resurrect them`() {
        viewModel.toggleEnhance()
        viewModel.updateSkinDenoise(30f)
        val enhance = codes.pepper.whimsicalart.feature.editor.domain.EnhanceEffect(enabled = true)
        val denoise = codes.pepper.whimsicalart.feature.editor.domain.SkinDenoiseEffect(intensity = 30f)
        viewModel.setEffectStack(listOf(enhance, denoise))
        viewModel.removeStackEffect(enhance.id)
        val mid = viewModel.uiState.value
        assertFalse(mid.enhanceEnabled)
        assertEquals(30f, mid.skinDenoise)
        viewModel.removeStackEffect(denoise.id)
        val state = viewModel.uiState.value
        assertEquals(0f, state.skinDenoise)
        assertEquals(0, state.effectStack.size)
    }

    @Test
    fun `removeStackEffect is a no-op for a merged layer`() {
        val merged = codes.pepper.whimsicalart.feature.editor.domain.MergedEffect()
        viewModel.setEffectStack(listOf(merged))
        viewModel.removeStackEffect(merged.id)
        assertEquals(1, viewModel.uiState.value.effectStack.size)
    }

    private fun adj(tool: EditTool, v: Float): codes.pepper.whimsicalart.feature.editor.domain.StackEffect =
        codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(tool = tool, value = v)

    private fun keys(): List<String> = viewModel.uiState.value.effectStack.map { it.layerKey }

    @Test
    fun `moveEffect reorders the stack`() {
        viewModel.setEffectStack(listOf(
            adj(EditTool.BRIGHTNESS, 10f),
            adj(EditTool.CONTRAST, 20f),
            adj(EditTool.SATURATION, 30f)
        ))
        assertEquals(
            listOf("layer:BRIGHTNESS", "layer:CONTRAST", "layer:SATURATION"),
            keys()
        )
        viewModel.moveEffect(0, 2)
        assertEquals(
            listOf("layer:CONTRAST", "layer:SATURATION", "layer:BRIGHTNESS"),
            keys()
        )
    }

    @Test
    fun `moveEffect is undoable`() {
        viewModel.setEffectStack(listOf(
            adj(EditTool.BRIGHTNESS, 10f),
            adj(EditTool.CONTRAST, 20f)
        ))
        viewModel.moveEffect(0, 1)
        assertEquals(listOf("layer:CONTRAST", "layer:BRIGHTNESS"), keys())
        viewModel.undo()
        assertEquals(listOf("layer:BRIGHTNESS", "layer:CONTRAST"), keys())
    }

    @Test
    fun `reorder is preserved across a later param sync`() {
        viewModel.setEffectStack(listOf(
            adj(EditTool.BRIGHTNESS, 10f),
            adj(EditTool.CONTRAST, 20f),
            adj(EditTool.SATURATION, 30f)
        ))
        viewModel.moveEffect(0, 2)
        assertEquals(listOf("layer:CONTRAST", "layer:SATURATION", "layer:BRIGHTNESS"), keys())
        // A fresh snapshot in canonical order arrives; the merge must keep the
        // user's ordering (CONTRAST, SATURATION, BRIGHTNESS) while refreshing params.
        val refreshed = listOf(
            adj(EditTool.BRIGHTNESS, 11f),
            adj(EditTool.CONTRAST, 21f),
            adj(EditTool.SATURATION, 31f)
        )
        viewModel.syncLayerState(refreshed, refreshed.associate { it.layerKey to it.layerKey })
        assertEquals(listOf("layer:CONTRAST", "layer:SATURATION", "layer:BRIGHTNESS"), keys())
    }

    @Test
    fun `toggleLayers toggles the layers panel visibility`() {
        assertFalse(viewModel.uiState.value.isLayersVisible)
        viewModel.toggleLayers()
        assertTrue(viewModel.uiState.value.isLayersVisible)
        viewModel.toggleLayers()
        assertFalse(viewModel.uiState.value.isLayersVisible)
    }

    @Test
    fun `clearCrop nulls the crop rect`() {
        viewModel.updateCrop(Rect(10f, 10f, 100f, 100f))
        viewModel.clearCrop()
        assertNull(viewModel.uiState.value.cropRect)
    }

    @Test
    fun `changesGeometry flags the geometry-changing effect set`() {
        // Geometry-changing: crop / transform, occluding overlays, hard brushes.
        assertTrue(codes.pepper.whimsicalart.feature.editor.domain.TransformEffect(rotation = 90f).changesGeometry)
        assertTrue(CropEffect(rect = androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 1f)).changesGeometry)
        assertTrue(codes.pepper.whimsicalart.feature.editor.domain.StickerEffect().changesGeometry)
        assertTrue(codes.pepper.whimsicalart.feature.editor.domain.TextEffect().changesGeometry)
        assertTrue(codes.pepper.whimsicalart.feature.editor.domain.FrameEffect().changesGeometry)
        assertTrue(BitmapDiffEffect(tool = EditTool.PEN, strokes = emptyList()).changesGeometry)
        assertTrue(BitmapDiffEffect(tool = EditTool.MOSAIC, strokes = emptyList()).changesGeometry)
        assertTrue(BitmapDiffEffect(tool = EditTool.OBJECT_REMOVAL, strokes = emptyList()).changesGeometry)
        assertTrue(
            codes.pepper.whimsicalart.feature.editor.domain.BeautySlimEffect().changesGeometry
        )
        assertTrue(
            codes.pepper.whimsicalart.feature.editor.domain.BeautyEyeEnlargeEffect().changesGeometry
        )
        assertTrue(
            codes.pepper.whimsicalart.feature.editor.domain.BeautyNoseEffect().changesGeometry
        )
        assertTrue(
            codes.pepper.whimsicalart.feature.editor.domain.BeautyJawEffect().changesGeometry
        )
        // Background REPLACE WITH a placement image transforms the cut-out
        // SUBJECT (move/scale/rotate), so it re-locates face space like
        // crop/transform; BLUR and a REPLACE with no placement stay colour-only.
        val placement = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        assertTrue(BackgroundEffect(mode = BackgroundMode.REPLACE, backgroundImage = placement).changesGeometry)
        assertFalse(BackgroundEffect(mode = BackgroundMode.REPLACE, backgroundImage = null).changesGeometry)
        assertFalse(BackgroundEffect(mode = BackgroundMode.BLUR, backgroundImage = placement).changesGeometry)
        // Colour-only / non-occluding: adjustments, blur brush, makeup brushes.
        assertFalse(adj(EditTool.BRIGHTNESS, 10f).changesGeometry)
        assertFalse(BitmapDiffEffect(tool = EditTool.BLUR_BRUSH, strokes = emptyList()).changesGeometry)
        assertFalse(
            codes.pepper.whimsicalart.feature.editor.domain.BeautySmoothingEffect().changesGeometry
        )
        assertFalse(
            codes.pepper.whimsicalart.feature.editor.domain.BeautyPenEffect().changesGeometry
        )
    }

    @Test
    fun `a null geometry track base at the second beauty layer degrades only that layer`() {
        // [filter, beauty, crop, beauty]: the track base is VALID at the first
        // beauty index (1) and NULL at the second (3) — a mid-list failure.
        // The layer BEFORE the failure must keep its bound geometry and the fold
        // must not silently unbind the whole group.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val effects = listOf(
            SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 10f),
            BeautyAutoEffect(intensity = 50f),
            CropEffect(rect = Rect(0f, 0f, 0.5f, 0.5f)),
            BeautySmoothingEffect(intensity = 30f)
        )
        val resolved = runBlocking {
            viewModel.bindBeautyGeometries(
                effects, scale = 1f,
                base = { index, _ -> if (index == 1) base else null }
            )
        }
        assertEquals(4, resolved.size)
        assertSame("non-beauty effects pass through unchanged", effects[0], resolved[0])
        assertSame(effects[2], resolved[2])
        val first = resolved[1] as codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
        assertNotSame(
            "the layer BEFORE the failing base must be a freshly bound instance",
            effects[1], first
        )
        assertNotNull("the earlier beauty layer must keep its resolved geometry", first.geometry)
        val second = resolved[3] as codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
        assertSame(
            "the failing layer degrades to identity (same unbound instance)",
            effects[3], second
        )
        assertNull("the degraded layer carries no geometry", second.geometry)
    }

    @Test
    fun `a null geometry track base at the first beauty layer still binds later layers`() {
        // Mirrors the above: a failure LATER must not cascade into earlier layers
        // either — a null base at index 1 leaves the second beauty (index 3) free
        // to bind instead of aborting the whole list.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val effects = listOf(
            SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 10f),
            BeautyAutoEffect(intensity = 50f),
            CropEffect(rect = Rect(0f, 0f, 0.5f, 0.5f)),
            BeautySmoothingEffect(intensity = 30f)
        )
        val resolved = runBlocking {
            viewModel.bindBeautyGeometries(
                effects, scale = 1f,
                base = { index, _ -> if (index == 1) null else base }
            )
        }
        val first = resolved[1] as codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
        assertSame("the failing first layer degrades to identity", effects[1], first)
        assertNull(first.geometry)
        val second = resolved[3] as codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
        assertNotSame("the later beauty layer must still bind", effects[3], second)
        assertNotNull("the later beauty layer receives its geometry", second.geometry)
    }

    @Test
    fun `color and style filters share a single filter layer key`() {
        // Filters is ONE effect (docs/effects_reference.md → *Filters*): the
        // color, style and lens options are controls of the same layer, so the
        // style layer must fold under layer:FILTER like the color filter.
        val color = codes.pepper.whimsicalart.feature.editor.domain.FilterEffect(
            filterMatrix = null
        )
        val style = codes.pepper.whimsicalart.feature.editor.domain.StyleEffect(filter = null)
        assertEquals("layer:FILTER", color.layerKey)
        assertEquals("layer:FILTER", style.layerKey)
    }

    @Test
    fun `incremental tail reapply is byte identical to a full from-base fold`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            // Non-uniform content so pixel comparison is meaningful.
            setPixel(5, 5, Color.RED)
            setPixel(30, 10, Color.BLUE)
            setPixel(20, 30, Color.GREEN)
        }
        val effects = listOf(
            SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 40f),
            CropEffect(rect = null),
            FrameEffect(layer = FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.1f, color = Color.BLACK)))
        )
        val full = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, base, effects)
        }
        // Compute the prefix composite (effects[0..size-1)) then reapply only the
        // tail (the frame) from it — the incremental 3-image fold path.
        val prefix = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, base, effects.take(effects.size - 1))
        }
        val incremental = runBlocking {
            viewModel.renderStackWithBeautyGeometry(
                context, base, effects, resumeAt = effects.size - 1, resumeBitmap = prefix
            )
        }
        assertEquals(
            "incremental tail reapply must match a full from-base fold byte-for-byte",
            full.width, incremental.width
        )
        assertEquals(full.height, incremental.height)
        for (y in 0 until incremental.height) {
            for (x in 0 until incremental.width) {
                assertEquals("pixel($x,$y)", full.getPixel(x, y), incremental.getPixel(x, y))
            }
        }
        full.recycle()
        prefix.recycle()
        incremental.recycle()
    }

    @Test
    fun `renderStackWithBeautyGeometry returns an independent copy never recycled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        base.eraseColor(Color.WHITE)
        val effects = listOf(
            SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 30f)
        )
        val result = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, base, effects)
        }
        // The returned bitmap is a real, non-recycled, indepently-usable result
        // (the fold never returns the caller-owned base without applying it).
        assertFalse(result.isRecycled)
        assertNotSame("result must not be the caller-owned base", base, result)
        base.recycle()
        result.recycle()
    }

    @Test
    fun `effectsBeforeTool folds only the effects preceding the selected tool`() {
        val brightness = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 40f)
        val frame = FrameEffect(
            layer = FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.1f, color = Color.BLACK))
        )
        val pen = BitmapDiffEffect(tool = EditTool.PEN, strokes = emptyList())

        val noPen = listOf(brightness, frame)
        assertEquals(
            "a tool absent from the stack keeps the whole stack as its base",
            noPen, viewModel.effectsBeforeTool(noPen, EditTool.PEN)
        )
        val withPen = listOf(brightness, pen, frame)
        assertEquals(
            "the brush base must fold only the effects before the brush layer",
            listOf(brightness), viewModel.effectsBeforeTool(withPen, EditTool.PEN)
        )
        assertEquals(
            "a null tool keeps the whole stack",
            withPen, viewModel.effectsBeforeTool(withPen, null)
        )
    }

    @Test
    fun `effectsBeforeTool keeps preceding beauty layers until the pen layer`() {
        val auto = BeautyAutoEffect(intensity = 40f)
        val smoothing = BeautySmoothingEffect(intensity = 20f)
        val frame = FrameEffect(
            layer = FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.1f, color = Color.BLACK))
        )
        val pen = BeautyPenEffect(strokes = emptyList())

        val stack = listOf(auto, smoothing, pen, frame)
        // Only the PEN sub-tool cuts at its own `beauty:pen` layer: the other
        // beauty layers that precede it stay in the composite base.
        val penBase = viewModel.effectsBeforeTool(
            stack, EditTool.BEAUTY, BeautyTool.BRIGHTNESS_PEN
        )
        assertEquals(
            "the pen's base folds everything before the pen, other beauty layers included",
            listOf(auto, smoothing), penBase
        )

        // A NON-pen beauty sub-tool still cuts at the first beauty layer (the
        // BEAUTY group's own layer is the first `beauty:auto`).
        val autoBase = viewModel.effectsBeforeTool(
            stack, EditTool.BEAUTY, BeautyTool.AUTO_BEAUTY
        )
        assertEquals(
            "auto-beauty's base drops the whole beauty group and the tail",
            emptyList<codes.pepper.whimsicalart.feature.editor.domain.StackEffect>(), autoBase
        )

        // A non-beauty tool cuts at its own first layer as before.
        val frameBase = viewModel.effectsBeforeTool(
            stack, EditTool.FRAMES, null
        )
        assertEquals(
            "the frame tool's base folds everything before the first frame",
            listOf(auto, smoothing, pen), frameBase
        )
    }

    @Test
    fun `effectsBeforeTool beauty pen with no pen layer keeps the whole stack`() {
        val auto = BeautyAutoEffect(intensity = 40f)
        val stack = listOf(auto)
        assertEquals(
            "no pen layer yet — the whole folded stack is the pen's base",
            stack,
            viewModel.effectsBeforeTool(stack, EditTool.BEAUTY, BeautyTool.BRIGHTNESS_PEN)
        )
    }

    @Test
    fun `currentToolIndex lands on the beauty pen layer not the first beauty layer`() {
        val auto = BeautyAutoEffect(intensity = 40f)
        val smoothing = BeautySmoothingEffect(intensity = 20f)
        val pen = BeautyPenEffect(strokes = emptyList())
        val stack = listOf(auto, smoothing, pen)

        val tracker = GeometryTracker(
            originalGeometryImage = { null },
            selectedTool = { codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool.BEAUTY },
            selectedBeautyTool = { BeautyTool.BRIGHTNESS_PEN },
            effectStack = { stack }
        )
        assertEquals("the pen layer index", 2, tracker.currentToolIndex(stack))

        val autoTracker = GeometryTracker(
            originalGeometryImage = { null },
            selectedTool = { codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool.BEAUTY },
            selectedBeautyTool = { BeautyTool.AUTO_BEAUTY },
            effectStack = { stack }
        )
        assertEquals(
            "a non-pen beauty sub-tool still anchors at the first beauty layer",
            0, autoTracker.currentToolIndex(stack)
        )
    }

    @Test
    fun `clearCompositePreview resets the preview field`() {
        // There is no decodable source URI in a unit test, so refresh leaves it
        // null; clearing must not crash nor disturb unrelated state.
        viewModel.updateContrast(30f)
        viewModel.clearCompositePreview()
        assertNull(viewModel.uiState.value.compositePreview)
        assertEquals(
            "clearing the composite preview must not disturb the other state",
            30f, viewModel.uiState.value.contrast
        )
    }

    @Test
    fun `refreshCompositePreview is safe without a decodable source`() {
        // No imageUri → buildBaseAndEffects returns null → refresh must not
        // crash and must leave compositePreview null.
        viewModel.refreshCompositePreview(codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle())
        assertNull(viewModel.uiState.value.compositePreview)
    }

    @Test
    fun `unknown viewport never stores a coerced stand-in viewport nor renders`() {
        // Fresh viewModel = canvas not measured yet (viewport 0x0). The old code
        // coerced the default viewport to 1x1 and folded a ~5%-of-source bitmap
        // that nothing re-fired to replace (the blurry-preview bug). The fix:
        // an unknown viewport renders nothing — the stored viewport must stay
        // (0,0) and no composite is published.
        assertEquals(Pair(0, 0), viewModel.currentViewportPx())
        viewModel.refreshCompositePreview(
            codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle()
        )
        assertNull(viewModel.uiState.value.compositePreview)
        assertEquals(
            "an unknown viewport must not be coerced into a renderable stand-in",
            Pair(0, 0), viewModel.currentViewportPx()
        )
    }

    @Test
    fun `an explicit measured viewport overrides the stored default viewport`() {
        // The screen now passes the measured canvas sizes (editBoxSize) and zoom
        // explicitly instead of relying on the VM's stored-default viewport, so
        // the preview resolution never depends on a stale stored value.
        viewModel.setViewBox(600, 800)
        assertEquals(Pair(600, 800), viewModel.currentViewportPx())
        viewModel.refreshCompositePreview(
            codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle(),
            visibleWidthPx = 900,
            visibleHeightPx = 500
        )
        assertEquals(Pair(900, 500), viewModel.currentViewportPx())
    }

    @Test
    fun `preview resolution always keeps the preview larger than the displayed footprint`() {
        // Spec (docs/effect_stack.md → Choosing preview resolution): the preview
        // bitmap always exceeds the displayed footprint so the display downscales
        // it ("looks sharp while panning"). The ceiling rounding of quantizePreviewScale
        // guarantees previewMax ≥ viewportMax × effectiveZoom across every 5%-bucket
        // boundary (bounded by the source: you cannot preview above source res).
        viewModel.setViewBox(1080, 900)
        val literals = listOf(0.5f, 0.8f, 1f)
        for (sourceMax in listOf(800, 1200, 4000, 10000, 20000)) {
            for (canvasZoomVal in literals) {
                viewModel.setCanvasZoom(canvasZoomVal)
                val previewScale = viewModel.previewScaleFor(sourceMax, sourceMax)
                val previewMax = (sourceMax * previewScale).toInt()
                val displayed = minOf(((1080 * canvasZoomVal).toInt()), sourceMax)
                assertTrue(
                    "source=$sourceMax @ canvasZoom=$canvasZoomVal previewScale=$previewScale preview=$previewMax must cover displayed=$displayed",
                    previewMax >= displayed
                )
                assertTrue(previewScale in 0.05f..1f)
            }
        }
    }

    @Test
    fun `sharpenedPreview renders at the preview scale, never the full bounded source`() {
        // PERF (preview-first): the live-look sharpen/enhance passes used to run
        // on the FULL 2048-bounded source decode every slider tick, at up to ~6x
        // the displayed footprint. The fix runs them on a preview-scale base —
        // the SAME quantized 5%-bucket the composite fold uses (previewScaleFor)
        // — so the sharpened stand-in never exceeds the fold's display resolution.
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "sharp_preview_scale.jpg")
        val src = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(600, 400)
        viewModel.setCanvasZoom(1f)
        viewModel.updateSharpness(30f)

        // The VM's source decode (sharpnessDecodedSource) is the bounded EXIF-
        // oriented decode of the same URI — mirror its geometry exactly.
        val bounded = codes.pepper.whimsicalart.feature.editor.domain.decodeBoundedOriented(
            context.contentResolver, Uri.fromFile(file)
        )!!
        val scale = viewModel.previewScaleFor(bounded.width, bounded.height)
        val expectedW = (bounded.width * scale).roundToInt().coerceAtLeast(1)
        val expectedH = (bounded.height * scale).roundToInt().coerceAtLeast(1)
        assertTrue(
            "test premise: the bounded source must be larger than the preview bucket",
            bounded.width > expectedW && expectedW < bounded.width
        )

        val looper = shadowOf(Looper.getMainLooper())
        var published: Bitmap? = null
        repeat(2000) {
            looper.idle()
            viewModel.uiState.value.sharpenedPreview?.let {
                published = it
                return@repeat
            }
            Thread.sleep(5)
        }
        assertNotNull("sharpenedPreview must be published", published)
        assertEquals("preview width scales with previewScaleFor", expectedW, published!!.width)
        assertEquals("preview height scales with previewScaleFor", expectedH, published!!.height)
    }

    @Test
    fun `crop eye-on composite shows the cropped region and eye-off keeps the full frame`() {
        // PERF (crop preview): the composite fold (eye on, excludeCurrentTool =
        // false) IS the crop preview — the removed `cropPreviewBitmap` remember
        // rendered the SAME region full-size on the main thread but was never
        // displayed (the composite always landed first). Lock the invariant: eye
        // ON publishes a composite that is entirely the cropped region; eye OFF
        // (grid mode) keeps the full photo in the fold.
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "crop_eye_composite.jpg")
        val src = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            for (x in 24 until 48) {
                for (y in 0 until 32) {
                    setPixel(x, y, Color.GREEN)
                }
            }
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(800, 600)
        // The crop tool must be SELECTED for the eye-off fold to exclude its layer
        // (effectsBeforeTool drops everything from the active tool onward).
        viewModel.selectTool(codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool.CROP)
        // Crop to an interior GREEN-only band — far from the red/green seam so
        // JPEG chroma subsampling cannot bleed the assertion.
        val rect = Rect(0.6f, 0.3f, 0.9f, 0.7f)
        viewModel.updateCrop(rect)

        val looper = shadowOf(Looper.getMainLooper())
        var composite: Bitmap? = null
        viewModel.refreshCompositePreview(
            codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle(),
            includeTail = true,
            excludeCurrentTool = false,
            visibleWidthPx = 800,
            visibleHeightPx = 600,
            canvasZoom = 1f
        )
        repeat(2000) {
            looper.idle()
            viewModel.uiState.value.compositePreview?.let {
                composite = it
                return@repeat
            }
            Thread.sleep(5)
        }
        assertNotNull("eye-on crop composite must be published", composite)

        val base = codes.pepper.whimsicalart.feature.editor.domain.decodeBoundedOriented(
            context.contentResolver, Uri.fromFile(file)
        )!!
        val expected = codes.pepper.whimsicalart.feature.editor.domain.BitmapRenderer.cropPreview(
            base, 0f, flipHorizontal = false, flipVertical = false, cropRect = rect
        )!!
        assertEquals("crop composite matches the crop footprint width", expected.width, composite!!.width)
        assertEquals("crop composite matches the crop footprint height", expected.height, composite!!.height)
        for (x in 0 until composite!!.width) {
            for (y in 0 until composite!!.height) {
                val px = composite!!.getPixel(x, y)
                assertTrue(
                    "pixel($x,$y) must be inside the cropped green region (was #${Integer.toHexString(px)})",
                    Color.green(px) > Color.red(px) + 40
                )
            }
        }

        composite = null
        var issued = 0
        val observed = StringBuilder()
        repeat(2000) {
            if (composite == null && issued < 40) {
                // The VM's composite coalescing can swallow a request that lands
                // just after the running fold's final dirty-check (it leaves
                // `compositeDirty` true with no job to consume it); production
                // recovers on the next state-change re-fire — mirror that here.
                viewModel.refreshCompositePreview(
                    codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle(),
                    includeTail = true,
                    excludeCurrentTool = true,
                    visibleWidthPx = 800,
                    visibleHeightPx = 600,
                    canvasZoom = 1f
                )
                issued++
            }
            looper.idle()
            val c = viewModel.uiState.value.compositePreview
            if (c != null) {
                observed.append("${c.width}x${c.height} ")
                if (c.width == base.width && c.height == base.height) {
                    composite = c
                    return@repeat
                }
            }
            Thread.sleep(5)
        }
        assertNotNull(
            "eye-off composite must be published (observed: $observed)",
            composite
        )
        var sawRed = 0
        var sawGreen = 0
        for (x in 0 until composite!!.width) {
            for (y in 0 until composite!!.height) {
                val px = composite!!.getPixel(x, y)
                if (Color.red(px) > Color.green(px) + 40) sawRed++
                if (Color.green(px) > Color.red(px) + 40) sawGreen++
            }
        }
        assertTrue("eye-off composite keeps the full two-tone frame (red=$sawRed green=$sawGreen)", sawRed > 0 && sawGreen > 0)
    }

    @Test
    fun `full-size zoom fold publishes the full-size composite including committed text and sticker layers`() {
        // The report: after committing text/stickers the canvas stayed on the
        // small preview image — displayScale > 1 never swapped to the ready
        // full-size fold — because the full-size fold used to throw and
        // requestFullFold swallowed it inside runCatching. Publish semantics:
        // with displayScale > 1 a committed text+sticker stack must land a
        // composite AT FULL SOURCE SIZE (never parked at preview size), and a
        // second unrelated refresh must keep serving that full-size fold.
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "fullfold_textsticker.jpg")
        // Large enough that the quantized preview bucket is BELOW the full
        // source — so the published composite must be the FULL-size fold, not a
        // re-served preview.
        val src = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(800, 600)

        val textItem = codes.pepper.whimsicalart.feature.editor.domain.TextItem(
            id = "t1", text = "Q", color = 0xFFFFFFFF.toInt(),
            fontSize = 0.5f, x = 0.5f, y = 0.5f
        )
        val sticker = codes.pepper.whimsicalart.feature.stickers.domain.StickerItem(
            stickerId = "e1", emojiText = "\uD83D\uDE00",
            x = 0.3f, y = 0.4f, widthFrac = 0.4f, heightFrac = 0.4f
        )
        val snapshot = listOf(
            codes.pepper.whimsicalart.feature.editor.domain.TextEffect(layers = listOf(textItem)),
            codes.pepper.whimsicalart.feature.editor.domain.StickerEffect(layers = listOf(sticker))
        )
        viewModel.syncLayerState(snapshot, snapshot.associate { it.layerKey to it.layerKey })

        val bounded = codes.pepper.whimsicalart.feature.editor.domain.decodeBoundedOriented(
            context.contentResolver, Uri.fromFile(file)
        )!!
        // Zoom past the preview footprint → displayScale > 1 → the full-size fold.
        viewModel.setCanvasZoom(2f)

        val looper = shadowOf(Looper.getMainLooper())
        var full: Bitmap? = null
        val observed = StringBuilder()
        var previewSeen = false
        var issued = 0
        repeat(3000) {
            looper.idle()
            val c = viewModel.uiState.value.compositePreview
            if (c != null) {
                observed.append("${c.width}x${c.height} ")
                if (c.width == bounded.width && c.height == bounded.height) {
                    full = c
                }
                if (!previewSeen) {
                    previewSeen = true
                    // Re-issue ONCE after the planner has recomputed the fitted
                    // preview size, so the displayScale>1 gate keys on the real
                    // ratio. After that, stop re-issuing: every refresh cancels
                    // and restarts the debounced full-size request.
                    viewModel.refreshCompositePreview(
                        codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle(),
                        includeTail = true,
                        excludeCurrentTool = false,
                        visibleWidthPx = 800,
                        visibleHeightPx = 600,
                        canvasZoom = 2f
                    )
                }
            } else if (!previewSeen && issued < 100) {
                // The first fold can break while the source decode is still
                // loading; re-kick (like the screen's measure-pass re-fire) until
                // the preview actually publishes.
                viewModel.refreshCompositePreview(
                    codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle(),
                    includeTail = true,
                    excludeCurrentTool = false,
                    visibleWidthPx = 800,
                    visibleHeightPx = 600,
                    canvasZoom = 2f
                )
                issued++
            }
            if (full != null) return@repeat
            Thread.sleep(5)
        }
        if (full == null) {
            // The full-size fold is debounced (FULL_FOLD_DEBOUNCE_MS); advance the
            // Robolectric scheduler past the delay so it actually runs, then poll
            // for the Default-dispatcher completion to land back on the main queue.
            shadowOf(Looper.getMainLooper()).idleFor(1000, TimeUnit.MILLISECONDS)
        }
        repeat(6000) {
            looper.idle()
            viewModel.uiState.value.compositePreview?.let {
                observed.append("${it.width}x${it.height} ")
                if (it.width == bounded.width && it.height == bounded.height) {
                    full = it
                }
            }
            if (full != null) return@repeat
            Thread.sleep(5)
        }
        assertNotNull(
            "full-size fold must publish AT FULL SOURCE SIZE (observed: $observed)",
            full
        )
        // The full-size composite must actually contain the committed layers (a
        // pure-RED base means the fold fell back to the merged base and dropped
        // the text/sticker layers).
        var nonRed = 0
        for (x in 0 until bounded.width) {
            for (y in 0 until bounded.height) {
                if (full!!.getPixel(x, y) != 0xFFFF0000.toInt()) nonRed++
            }
        }
        assertTrue(
            "the published full-size composite must contain the text/sticker layers (nonRED=$nonRed)",
            nonRed > 0
        )

        // An unrelated refresh keeps serving the published full-size composite —
        // no bounce back to the small preview.
        viewModel.refreshCompositePreview(
            codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle(),
            includeTail = true,
            excludeCurrentTool = false,
            visibleWidthPx = 800,
            visibleHeightPx = 600,
            canvasZoom = 2f
        )
        repeat(1000) {
            looper.idle()
            val c = viewModel.uiState.value.compositePreview
            if (c != null && c.width == bounded.width && c.height == bounded.height) {
                return@repeat
            }
            Thread.sleep(5)
        }
        val cache = viewModel.fullSizeFoldCacheForTest()
        assertNotNull(
            "the full-size fold must be CACHED (a real layered fold, not the " +
                "empty-effects base short-circuit that never populates the cache)",
            cache.lastImage
        )
        val cached = viewModel.uiState.value.compositePreview
        assertEquals(1600, cached!!.width)
        assertEquals(1000, cached!!.height)
    }

    @Test
    fun `rotation-only transform layer survives a sync into the effect stack`() {
        // Reproduces the "transform layer missing from Layers list" report:
        // after brightness → rotate → another tool, the TransformEffect (rotation,
        // no crop rect) must remain a committed layer in effectStack.
        val brightness = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 40f
        )
        viewModel.setEffectStack(listOf(brightness))
        viewModel.setRotation(90f)
        viewModel.updateCrop(Rect(10f, 10f, 90f, 90f))

        val snapshot = listOf(
            brightness.copy(id = brightness.id),
            codes.pepper.whimsicalart.feature.editor.domain.TransformEffect(rotation = 90f),
            CropEffect(rect = Rect(10f, 10f, 90f, 90f))
        )
        viewModel.syncLayerState(snapshot, snapshot.associate { it.layerKey to it.layerKey })
        val keys = viewModel.uiState.value.effectStack.map { it.layerKey }
        assertEquals(listOf("layer:BRIGHTNESS", "layer:TRANSFORM", "layer:CROP"), keys)
    }

    @Test
    fun `syncLayerState drops a removed layer and keeps the survivors in order`() {
        // The Layers delete → clearTool → lost-focus commit path: the snapshot
        // no longer contains the removed layer, so the merged stack must drop it
        // while the remaining layers keep their positions (Removal
        // recomputation).
        val a = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 40f
        )
        val b = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.CONTRAST, value = -20f
        )
        viewModel.setEffectStack(listOf(a, b))

        val retained = listOf(a.copy(id = a.id))
        viewModel.syncLayerState(retained, retained.associate { it.layerKey to it.layerKey })

        val keys = viewModel.uiState.value.effectStack.map { it.layerKey }
        assertEquals(listOf("layer:BRIGHTNESS"), keys)
    }

    @Test
    fun `rotation-only transform renders a differently-sized album through the fold`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val effect = codes.pepper.whimsicalart.feature.editor.domain.TransformEffect(rotation = 90f)
        val result = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, base, listOf(effect))
        }
        assertEquals(80, result.width)
        assertEquals(40, result.height)
        base.recycle()
        result.recycle()
    }

    @Test
    fun `crop applies over the oriented image when transform precedes it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        // Transform bakes a 80x40 canvas; the crop rect is normalized against
        // THAT oriented bitmap (matches how the renderer applies both).
        val result = runBlocking {
            viewModel.renderStackWithBeautyGeometry(
                context, base,
                listOf(
                    codes.pepper.whimsicalart.feature.editor.domain.TransformEffect(rotation = 90f),
                    CropEffect(rect = Rect(0f, 0f, 0.5f, 1f))
                )
            )
        }
        assertEquals(40, result.width)
        assertEquals(40, result.height)
        base.recycle()
        result.recycle()
    }

    // --- Beauty pen (BEAUTY + BRIGHTNESS_PEN, Category B brush) --------------

    @Test
    fun `beauty pen drag sequence keeps the stroke live and commits it to the stack on lost focus`() {
        viewModel.startBeautyPenStroke(PointF(0.5f, 0.5f))
        viewModel.moveBeautyPenStroke(PointF(0.55f, 0.5f))
        viewModel.endBeautyPenStroke()

        val dragged = viewModel.uiState.value
        assertNull(dragged.beauty.activeStroke)
        assertEquals(1, dragged.beauty.brushStrokes.size)
        assertEquals(2, dragged.beauty.brushStrokes[0].points.size)
        // The brush size is carried as a width fraction (resolution-independent,
        // like the standalone pen) — normalized against the source width.
        assertEquals(30f / 2000f, dragged.beauty.brushStrokes[0].size, 1e-4f)

        // While the tool is focused the editor is UNSTACKED: the stroke lives in
        // the live params only, NOT in the committed stack yet — it lands there
        // exactly once on lost focus (commit-on-lost-focus, same as the scaling
        // slider / standalone pen).
        assertTrue(dragged.effectStack.filterIsInstance<BeautyPenEffect>().isEmpty())

        // Lost focus: the screen's commit path — snapshot derived from the live
        // params → syncLayerState — stores the stroke layer.
        val snapshot = buildStackSnapshot(viewModel.uiState.value, EditorRenderBundle())
        viewModel.syncLayerState(snapshot, emptyMap())

        val penLayers = viewModel.uiState.value.effectStack.filterIsInstance<BeautyPenEffect>()
        assertEquals(1, penLayers.size)
        assertEquals(1, penLayers[0].strokes.size)
        assertEquals(2, penLayers[0].strokes[0].points.size)
        assertEquals(0.2f, penLayers[0].opacity)
    }

    @Test
    fun `beauty pen move drops sample points closer than half the brush radius`() {
        viewModel.startBeautyPenStroke(PointF(0.5f, 0.5f))
        viewModel.moveBeautyPenStroke(PointF(0.5f, 0.51f))
        viewModel.moveBeautyPenStroke(PointF(0.5f, 0.5100005f))
        viewModel.endBeautyPenStroke()

        val stroke = viewModel.uiState.value.beauty.brushStrokes.single()
        // minStep = size/2 = (30/2000)/2 = 0.0075; 0.01 away appends, the
        // sub-epsilon follow-up is dropped.
        assertEquals(2, stroke.points.size)
    }

    @Test
    fun `beauty pen colour and opacity travel into the live pen layer and the committed stack`() {
        val colour = 0xFF3366CC.toInt()
        viewModel.updateBeautyParams { it.copy(penColor = colour, brushOpacity = 0.7f) }
        viewModel.startBeautyPenStroke(PointF(0.5f, 0.5f))
        viewModel.endBeautyPenStroke()

        // While focused the values are LIVE in the params (single source of
        // truth); the committed pen layer gets them once on lost focus.
        val focused = viewModel.uiState.value
        assertEquals(colour, focused.beauty.penColor)
        assertEquals(0.7f, focused.beauty.brushOpacity)

        val snapshot = buildStackSnapshot(viewModel.uiState.value, EditorRenderBundle())
        viewModel.syncLayerState(snapshot, emptyMap())

        val penLayers = viewModel.uiState.value.effectStack.filterIsInstance<BeautyPenEffect>()
        assertEquals(1, penLayers.size)
        assertEquals(colour, penLayers[0].color)
        assertEquals(0.7f, penLayers[0].opacity)
    }

    @Test
    fun `each colour-bearing sub-tool keeps a dedicated colour that changes alone`() {
        val lipColour = 0xFF112233.toInt()
        val penColour = 0xFF445566.toInt()
        viewModel.updateBeautyParams {
            it.copy(lipstickColor = lipColour, lipstick = 50f, penColor = penColour)
        }
        viewModel.startBeautyPenStroke(PointF(0.5f, 0.5f))
        viewModel.endBeautyPenStroke()

        // The live params are the single source of truth while focused.
        assertEquals(penColour, viewModel.uiState.value.beauty.penColor)
        assertEquals(lipColour, viewModel.uiState.value.beauty.lipstickColor)

        val first = buildStackSnapshot(viewModel.uiState.value, EditorRenderBundle())
        viewModel.syncLayerState(first, emptyMap())
        val stack = viewModel.uiState.value.effectStack
        val pen = stack.filterIsInstance<BeautyPenEffect>().single()
        val lipstick = stack.filterIsInstance<BeautyLipstickEffect>().single()
        assertEquals(penColour, pen.color)
        assertEquals(lipColour, lipstick.color)

        // Mutate ONLY the lipstick slot: the pen layer's colour and every other
        // sub-tool's slot must stay untouched (the previous single shared
        // makeupColor leaked one pick into every other colour-bearing tool).
        viewModel.updateBeautyParams { it.copy(lipstickColor = 0xFF00FF00.toInt()) }
        val state = viewModel.uiState.value
        assertEquals(0xFF00FF00.toInt(), state.beauty.lipstickColor)
        assertEquals(penColour, state.beauty.penColor)
        assertEquals(codes.pepper.whimsicalart.feature.beauty.ui.MakeupPalette.colors.first(), state.beauty.blushColor)
        assertEquals(codes.pepper.whimsicalart.feature.beauty.ui.MakeupPalette.colors.first(), state.beauty.eyeShadowColor)
        // Eyeliner's never-picked default is black, unlike the other makeup tools.
        assertEquals(android.graphics.Color.BLACK, state.beauty.eyelinerColor)
        assertEquals(codes.pepper.whimsicalart.feature.beauty.ui.MakeupPalette.colors.first(), state.beauty.foundationColor)
        assertEquals(codes.pepper.whimsicalart.feature.beauty.ui.MakeupPalette.colors.first(), state.beauty.hairColor)
        assertEquals(penColour, state.beauty.penColor)

        // The re-commit refreshes the lipstick layer IN PLACE; the pen layer
        // keeps its own dedicated colour untouched.
        val second = buildStackSnapshot(viewModel.uiState.value, EditorRenderBundle())
        viewModel.syncLayerState(second, emptyMap())
        val after = viewModel.uiState.value.effectStack
        assertEquals(0xFF00FF00.toInt(), after.filterIsInstance<BeautyLipstickEffect>().single().color)
        assertEquals(penColour, after.filterIsInstance<BeautyPenEffect>().single().color)
    }

    // --- Background placement -------------------------------------------------

    @Test
    fun `background placement scale clamps below 1 without free scale up`() {
        viewModel.updateBackgroundPlacementScale(1.5f)
        assertEquals(1f, viewModel.uiState.value.backgroundPlacementScale)
        viewModel.updateBackgroundPlacementScale(0.1f)
        assertEquals(0.2f, viewModel.uiState.value.backgroundPlacementScale)
    }

    @Test
    fun `background placement scale can exceed 1 with free scale up`() {
        viewModel.toggleBackgroundFreeScaleUp()
        viewModel.updateBackgroundPlacementScale(1.5f)
        assertEquals(1.5f, viewModel.uiState.value.backgroundPlacementScale)
    }

    @Test
    fun `disabling free scale up clamps a scaled-up placement back to fit size`() {
        viewModel.toggleBackgroundFreeScaleUp()
        viewModel.updateBackgroundPlacementScale(1.5f)
        viewModel.toggleBackgroundFreeScaleUp()
        assertEquals(1f, viewModel.uiState.value.backgroundPlacementScale)
    }

    @Test
    fun `background placement pan clamps to half a canvas`() {
        viewModel.updateBackgroundPlacementPan(10f, -10f)
        assertEquals(0.5f, viewModel.uiState.value.backgroundPlacementPanX)
        assertEquals(-0.5f, viewModel.uiState.value.backgroundPlacementPanY)
    }

    @Test
    fun `background placement rotation wraps into zero to three sixty`() {
        viewModel.updateBackgroundPlacementRotation(190f)
        assertEquals(190f, viewModel.uiState.value.backgroundPlacementRotationDegrees)
        viewModel.updateBackgroundPlacementRotation(720f)
        assertEquals(0f, viewModel.uiState.value.backgroundPlacementRotationDegrees)
        viewModel.updateBackgroundPlacementRotation(-90f)
        assertEquals(270f, viewModel.uiState.value.backgroundPlacementRotationDegrees)
    }

    // --- Background mode switching (Replace/Blur, tracker "mode-switch") ------

    private fun waitForBackgroundDecode() {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(2000) {
            looper.idle()
            if (viewModel.uiState.value.backgroundImage != null) return
            Thread.sleep(5)
        }
        assertNotNull(
            "auto-picked preset decode must eventually publish a background",
            viewModel.uiState.value.backgroundImage
        )
    }

    private fun waitForBackgroundCondition(message: String, predicate: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(2000) {
            looper.idle()
            if (predicate()) return
            Thread.sleep(5)
        }
        assertTrue(message, predicate())
    }

    @Test
    fun `first selection of Replace with no chosen background auto-picks the first preset`() {
        // Tracker "mode-switch": REPLACE auto-picks the first preset so the tool
        // enters the placement state immediately instead of showing the plain
        // subject with nothing placed.
        assertNull(viewModel.uiState.value.backgroundImage)
        assertNull(viewModel.uiState.value.selectedBackgroundRes)
        viewModel.updateBackgroundMode(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.REPLACE)

        assertEquals(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.REPLACE, viewModel.uiState.value.backgroundMode)
        waitForBackgroundDecode()
        assertEquals(BackgroundPresets.presets.first().drawableRes, viewModel.uiState.value.selectedBackgroundRes)
    }

    @Test
    fun `choosing Replace after a background is already chosen keeps that same background`() {
        // The auto-pick is a FIRST-TIME fallback only; it must never silently
        // swap a user-chosen preset (here the SECOND preset) back to the first.
        val chosen = BackgroundPresets.presets[1]
        viewModel.loadBackground(chosen.drawableRes)
        waitForBackgroundDecode()
        viewModel.updateBackgroundMode(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.REPLACE)

        assertEquals(chosen.drawableRes, viewModel.uiState.value.selectedBackgroundRes)
        assertNotNull(viewModel.uiState.value.backgroundImage)
    }

    @Test
    fun `switching from Replace to Blur undoes the replacement on canvas but preserves placement`() {
        // Tracker "mode-switch": REPLACE->BLUR returns the canvas to the subject
        // (the render keys on backgroundMode), while the placement VALUES and the
        // chosen background stay untouched for re-entry — resetBackgroundPlacement
        // must not fire on the mode transition.
        viewModel.updateBackgroundMode(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.REPLACE)
        waitForBackgroundDecode()
        viewModel.toggleBackgroundFreeScaleUp()
        viewModel.updateBackgroundPlacementScale(1.5f)
        viewModel.updateBackgroundPlacementPan(0.3f, -0.2f)
        viewModel.updateBackgroundPlacementRotation(75f)
        val chosenRes = viewModel.uiState.value.selectedBackgroundRes
        val chosenImage = viewModel.uiState.value.backgroundImage

        viewModel.updateBackgroundMode(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.BLUR)

        val state = viewModel.uiState.value
        assertEquals(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.BLUR, state.backgroundMode)
        assertEquals(chosenRes, state.selectedBackgroundRes)
        assertSame(chosenImage, state.backgroundImage)
        assertEquals(1.5f, state.backgroundPlacementScale)
        assertEquals(0.3f, state.backgroundPlacementPanX)
        assertEquals(-0.2f, state.backgroundPlacementPanY)
        assertEquals(75f, state.backgroundPlacementRotationDegrees)
        assertTrue(state.backgroundFreeScaleUp)
    }

    @Test
    fun `switching from Blur back to Replace restores the exact placement state`() {
        // Tracker "mode-switch": a Replace->Blur->Replace round-trip must restore
        // the exact custom placement — no reset, no auto-pick swap of a chosen bg.
        viewModel.updateBackgroundMode(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.REPLACE)
        waitForBackgroundDecode()
        viewModel.toggleBackgroundFreeScaleUp()
        viewModel.updateBackgroundPlacementScale(1.5f)
        viewModel.updateBackgroundPlacementPan(0.3f, -0.2f)
        viewModel.updateBackgroundPlacementRotation(75f)
        val chosenRes = viewModel.uiState.value.selectedBackgroundRes
        val chosenImage = viewModel.uiState.value.backgroundImage
        viewModel.updateBackgroundMode(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.BLUR)

        viewModel.updateBackgroundMode(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.REPLACE)

        val state = viewModel.uiState.value
        assertEquals(codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode.REPLACE, state.backgroundMode)
        assertEquals(chosenRes, state.selectedBackgroundRes)
        assertSame(chosenImage, state.backgroundImage)
        assertEquals(1.5f, state.backgroundPlacementScale)
        assertEquals(0.3f, state.backgroundPlacementPanX)
        assertEquals(-0.2f, state.backgroundPlacementPanY)
        assertEquals(75f, state.backgroundPlacementRotationDegrees)
        assertTrue(state.backgroundFreeScaleUp)
    }

    @Test
    fun `swapping to a different preset keeps the exact placement`() {
        // Tracker "placement-preserve": choosing a DIFFERENT preset while in
        // Replace must keep scale / pan / rotation (and freeScaleUp) as set —
        // only the image itself changes. (clearBackground still resets.)
        viewModel.loadBackground(BackgroundPresets.presets[0].drawableRes)
        waitForBackgroundDecode()
        viewModel.toggleBackgroundFreeScaleUp()
        viewModel.updateBackgroundPlacementScale(1.5f)
        viewModel.updateBackgroundPlacementPan(0.3f, -0.2f)
        viewModel.updateBackgroundPlacementRotation(75f)
        val firstImage = viewModel.uiState.value.backgroundImage

        viewModel.loadBackground(BackgroundPresets.presets[2].drawableRes)
        waitForBackgroundCondition("the second preset must swap in") {
            viewModel.uiState.value.selectedBackgroundRes == BackgroundPresets.presets[2].drawableRes
        }

        val state = viewModel.uiState.value
        assertEquals(BackgroundPresets.presets[2].drawableRes, state.selectedBackgroundRes)
        assertNotSame("the preset image itself must swap", firstImage, state.backgroundImage)
        assertEquals(1.5f, state.backgroundPlacementScale)
        assertEquals(0.3f, state.backgroundPlacementPanX)
        assertEquals(-0.2f, state.backgroundPlacementPanY)
        assertEquals(75f, state.backgroundPlacementRotationDegrees)
        assertTrue(state.backgroundFreeScaleUp)
    }

    @Test
    fun `swapping to a custom gallery background keeps the exact placement`() {
        // Tracker "placement-preserve": the same contract for the gallery path —
        // setCustomBackground swaps the image but must leave the grid state alone.
        viewModel.loadBackground(BackgroundPresets.presets[0].drawableRes)
        waitForBackgroundDecode()
        viewModel.updateBackgroundPlacementScale(0.8f)
        viewModel.updateBackgroundPlacementPan(-0.1f, 0.4f)
        viewModel.updateBackgroundPlacementRotation(120f)
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "custom_bg_swap.jpg")
        val image = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        FileOutputStream(file).use { out -> image.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        image.recycle()

        viewModel.setCustomBackground(Uri.fromFile(file))
        waitForBackgroundCondition("the gallery image must land") {
            viewModel.uiState.value.selectedBackgroundRes == null && viewModel.uiState.value.backgroundImage != null
        }

        val state = viewModel.uiState.value
        assertEquals(null, state.selectedBackgroundRes)
        assertNotNull(state.backgroundImage)
        assertEquals(0.8f, state.backgroundPlacementScale)
        assertEquals(-0.1f, state.backgroundPlacementPanX)
        assertEquals(0.4f, state.backgroundPlacementPanY)
        assertEquals(120f, state.backgroundPlacementRotationDegrees)
    }

    @Test
    fun `eye-off live-apply fold stops at the current tool and drops the tail`() {
        // docs/effect_stack.md → *Eye toggle and the current tool*: with the eye
        // OFF and a brush tool's live-apply fired the fold keeps the current
        // tool's own layer but must NOT fold layers ABOVE it. The screen maps
        // {eye OFF, liveApply} to {includeTail=false, excludeCurrentTool=false};
        // here we hold that fold shape on a concrete stack with a tail layer.
        val original = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "eye_off_live_apply.jpg")
        FileOutputStream(file).use { out -> original.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        original.recycle()
        viewModel.setImageUri(Uri.fromFile(file))
        val mosaicStroke = codes.pepper.whimsicalart.feature.editor.domain.StrokeLayer(
            type = codes.pepper.whimsicalart.feature.editor.domain.StrokeType.MOSAIC,
            points = listOf(0.3f to 0.5f, 0.7f to 0.5f),
            brushSize = 0.08f
        )
        val currentToolLayer = BitmapDiffEffect(
            tool = EditTool.MOSAIC,
            strokes = listOf(mosaicStroke)
        )
        val tailLayer = CropEffect(rect = Rect(0f, 0.25f, 1f, 0.75f))
        val bundle = codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle(
            strokes = listOf(mosaicStroke)
        )
        // The crop TAIL sits ABOVE the mosaic current-tool layer: with the eye
        // off, folding [includeTail=false, excludeCurrentTool=false] (the
        // live-apply shape) must stop at the mosaic layer (full 48x32, no crop)
        // and never fold the crop above it (which halves the height to 16).
        viewModel.selectTool(EditTool.MOSAIC)
        viewModel.setEffectStack(listOf(currentToolLayer, tailLayer))
        viewModel.updateCrop(Rect(0f, 0.25f, 1f, 0.75f))
        viewModel.setViewBox(800, 600)

        val looper = shadowOf(Looper.getMainLooper())

        fun awaitComposite(
            includeTail: Boolean,
            excludeCurrentTool: Boolean,
            previous: Bitmap?
        ): Bitmap {
            var issued = 0
            repeat(2000) {
                looper.idle()
                val c = viewModel.uiState.value.compositePreview
                if (c != null && c !== previous) return c
                if ((c == null || c === previous) && issued < 40) {
                    // The VM's composite coalescing can swallow a request that lands
                    // just after the running fold's final dirty-check (it leaves
                    // `compositeDirty` true with no job to consume it); production
                    // recovers on the next state-change re-fire — mirror that here,
                    // re-issuing while the previous phase's bitmap is still current.
                    viewModel.refreshCompositePreview(
                        bundle = bundle,
                        includeTail = includeTail,
                        excludeCurrentTool = excludeCurrentTool,
                        visibleWidthPx = 800,
                        visibleHeightPx = 600,
                        canvasZoom = 1f
                    )
                    issued++
                }
                Thread.sleep(5)
            }
            fail(
                "no composite published (includeTail=$includeTail, excludeCurrentTool=$excludeCurrentTool); " +
                    "current=${viewModel.uiState.value.compositePreview?.let { it.width to it.height }} " +
                    "hasMerged=${viewModel.uiState.value.hasMerged} " +
                    "selectedTool=${viewModel.uiState.value.selectedTool}"
            )
            return viewModel.uiState.value.compositePreview!!
        }

        val tailOff = awaitComposite(includeTail = false, excludeCurrentTool = false, previous = null)
        assertEquals(
            "eye-off live-apply fold is the current tool's layer only — the crop tail " +
                "above MOSAIC must be dropped (no height halving)",
            32, tailOff.height
        )

        val full = awaitComposite(includeTail = true, excludeCurrentTool = false, previous = tailOff)
        assertEquals(
            "eye-on fold includes the tail: the crop above MOSAIC halve the canvas height",
            16, full.height
        )
        for (y in 0 until full.height) {
            for (x in 0 until full.width) {
                assertTrue(
                    "eye-on crop tail must stay inside the cropped band (pixel " +
                        "($x,$y)=#${Integer.toHexString(full.getPixel(x, y))})",
                    Color.red(full.getPixel(x, y)) >= 250
                )
            }
        }
    }
}
