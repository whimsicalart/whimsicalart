package codes.pepper.whimsicalart.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.BitmapDiffEffect
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.StrokeLayer
import codes.pepper.whimsicalart.feature.editor.domain.StrokeType
import codes.pepper.whimsicalart.feature.editor.domain.decodeBoundedOriented
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.buildStackSnapshot
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * VERIFY, the brush-strokes-on-full-size fold (tracker planned task): a
 * COMMITTED brush layer is self-sufficient — it must survive the full-size fold
 * (pinch-zoom / save / eye-ON) even when the live `bundle.strokes` capture is
 * momentarily empty for its family (cold sub-VM on tool re-entry, an
 * unreconciled mid-zoom recompute), exactly like the BackgroundEffect fallback.
 * The PREVIEW hides the gap because it resumes from the layer's CARRIED
 * committedPreview baked at commit time while the full-size fold re-folds from
 * the base — so without the fallback the full-size composite shows the ORIGINAL
 * image. These tests pin the committed layer through (a) full-size [render] at
 * several resolutions/aspects and (b) the fold + snapshot lifecycle with an
 * EMPTY strokes bundle.
 */
@RunWith(RobolectricTestRunner::class)
class BrushFullSizeFoldTest {

    private lateinit var context: Context
    private lateinit var viewModel: EditorViewModel
    private lateinit var source: File
    private lateinit var pristine: Bitmap

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
        source = twoToneSource("brush_full_fold_${System.nanoTime()}.jpg")
        viewModel.setImageUri(Uri.fromFile(source))
        pristine = decodeBoundedOriented(context.contentResolver, Uri.fromFile(source))!!
    }

    private fun twoToneSource(name: String): File {
        // 24x16 two-tone: left half RED, right half GREEN — lets assertions tell
        // the pristine fold base apart by colour, at any fold resolution.
        // Built with eraseColor/setPixel only: Robolectric's graphics shadows do
        // NOT reliably rasterize Canvas draw ops.
        val file = File(context.cacheDir, name)
        val src = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.RED)
        for (x in 12 until 24) {
            for (y in 0 until 16) {
                src.setPixel(x, y, Color.GREEN)
            }
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        return file
    }

    // A solid-black centre pen splash, normalized (0.5, 0.5) of its base.
    private fun centrePenSplash() = StrokeLayer(
        type = StrokeType.PEN,
        points = listOf(0.5f to 0.5f),
        brushSize = 0.2f,
        color = Color.BLACK,
        hardness = 1f,
        referenceWidth = 0,
        referenceHeight = 0
    )

    private fun samePixels(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val pa = IntArray(a.width * a.height)
        val pb = IntArray(b.width * b.height)
        a.getPixels(pa, 0, a.width, 0, 0, a.width, a.height)
        b.getPixels(pb, 0, b.width, 0, 0, b.width, b.height)
        return pa.contentEquals(pb)
    }

    // --- Full-size render() at several resolutions / aspects -----------------

    @Test
    fun `full-size render draws the same normalized stroke at the drawn spot across three resolutions and aspects`() {
        val effect = BitmapDiffEffect(tool = EditTool.PEN, strokes = listOf(centrePenSplash()))
        // The stroke is unanchored (reference 0×0): points are plain fractions of
        // the running image, so the identical splash must land at the same
        // relative spot (the centre) on every base, at any resolution or aspect.
        val bases = listOf(100 to 100, 200 to 100, 150 to 300)
        for ((w, h) in bases) {
            val base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
            }
            val painted = effect.render(base, context)
            val cx = w / 2
            val cy = h / 2
            assertEquals(
                "brush splash lands at the normalized centre of ${w}x${h}",
                Color.BLACK, painted.getPixel(cx, cy)
            )
            assertEquals(
                "an untouched corner stays the base colour at ${w}x${h}",
                Color.RED, painted.getPixel(0, 0)
            )
            painted.recycle()
            base.recycle()
        }
    }

    @Test
    fun `full-size render of an anchored stroke maps through the reference fit after a base change`() {
        // Committed strokes are stored against a reference base; folding them at
        // FULL size after the base changed must re-express them through the same
        // centre-origin uniform fit the preview uses — the splash stays centred.
        val anchored = centrePenSplash().copy(referenceWidth = 100, referenceHeight = 100)
        val effect = BitmapDiffEffect(tool = EditTool.PEN, strokes = listOf(anchored))
        val base = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val painted = effect.render(base, context)
        assertEquals(Color.BLACK, painted.getPixel(100, 50))
        assertEquals(Color.RED, painted.getPixel(0, 0))
        painted.recycle()
        base.recycle()
    }

    // --- Fold lifecycle: committed brush survives an EMPTY live strokes bundle

    @Test
    fun `a committed brush layer is folded at full size even when the live strokes bundle is empty`() {
        // Reproduces the report ("after committing, the full-size composite —
        // pinch-zoom / save / eye-ON — shows the ORIGINAL image"): the full-size
        // fold re-folds from the base using effects derived from bundle.strokes.
        // With an empty live capture the committed BitmapDiffEffect MUST still be
        // folded (the layer is the document state), or the composite silently
        // loses the brush while the preview (resuming from the carried committed
        // preview) keeps showing it.
        val withoutBrush = runBlocking {
            viewModel.renderCommittedBitmap(EditorRenderBundle(), scale = 1f)
        }
        assertNotNull(withoutBrush)
        val committed = BitmapDiffEffect(tool = EditTool.PEN, strokes = listOf(centrePenSplash()))
        viewModel.setEffectStack(listOf(committed))

        val withBrush = runBlocking {
            viewModel.renderCommittedBitmap(EditorRenderBundle(), scale = 1f)
        }
        assertNotNull(withBrush)
        assertFalse(
            "the full-size composite must keep the committed brush even when the fold's live strokes bundle is empty",
            samePixels(withoutBrush!!, withBrush!!)
        )
        // The centre pixel is the splash's drawn spot: it must have darkened.
        val cx = withBrush.width / 2
        val cy = withBrush.height / 2
        assertTrue(
            "the brush splash must land at the normalized centre of the full-size composite",
            withBrush.getPixel(cx, cy) != pristine.getPixel(cx, cy)
        )
        withBrush.recycle()
        withoutBrush.recycle()
    }

    @Test
    fun `the committed brush layer is not evicted from the stack by an empty-strokes fold or snapshot`() {
        // orderedMerge DROPS any committed layer whose key is absent from the
        // freshly-derived list. Both the fold (buildStackEffects) and the commit
        // snapshot (buildStackSnapshot) must fall back to the committed layer, or
        // the empty-strokes state would delete the brush from effectStack itself.
        val committed = BitmapDiffEffect(tool = EditTool.PEN, strokes = listOf(centrePenSplash()))
        viewModel.setEffectStack(listOf(committed))

        runBlocking {
            viewModel.renderCommittedBitmap(EditorRenderBundle(), scale = 1f)
        }
        val stillInStack = viewModel.uiState.value.effectStack
            .filterIsInstance<BitmapDiffEffect>()
            .any { it.tool == EditTool.PEN && it.strokes.isNotEmpty() }
        assertTrue("the committed PEN layer must survive the empty-strokes full-size fold", stillInStack)

        val snapshot = buildStackSnapshot(viewModel.uiState.value, EditorRenderBundle())
        val inSnapshot = snapshot.filterIsInstance<BitmapDiffEffect>()
            .any { it.tool == EditTool.PEN && it.strokes.isNotEmpty() }
        assertTrue("an empty-strokes commit snapshot must keep the committed PEN layer", inSnapshot)
    }

    @Test
    fun `an empty strokes bundle with no committed brush stays empty`() {
        // Guard the fallback: without a committed layer there is nothing to fall
        // back to, so the fold must stay unchanged (no spurious brush injected).
        val folded = runBlocking {
            viewModel.renderCommittedBitmap(EditorRenderBundle(), scale = 1f)
        }
        assertNotNull(folded)
        assertTrue(
            "no committed brush exists: the fold is byte-identical to the base",
            samePixels(pristine, folded!!)
        )
        folded.recycle()
    }

    @Test
    fun `a different brush family folds independently of an empty sibling family`() {
        // Families are independent: an empty PEN capture must not drop a committed
        // MOSAIC layer (and vice versa) — the fallback is per-family.
        val withoutMosaic = runBlocking {
            viewModel.renderCommittedBitmap(
                EditorRenderBundle(strokes = listOf(centrePenSplash())),
                scale = 1f
            )
        }
        assertNotNull(withoutMosaic)
        val mosaic = BitmapDiffEffect(
            tool = EditTool.MOSAIC,
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.MOSAIC,
                    points = listOf(0.5f to 0.5f),
                    brushSize = 0.2f,
                    referenceWidth = 0,
                    referenceHeight = 0
                )
            )
        )
        viewModel.setEffectStack(listOf(mosaic))
        val withMosaic = runBlocking {
            viewModel.renderCommittedBitmap(
                EditorRenderBundle(strokes = listOf(centrePenSplash())),
                scale = 1f
            )
        }
        assertNotNull(withMosaic)
        assertFalse(
            "a committed MOSAIC survives a live bundle that only carries PEN strokes",
            samePixels(withoutMosaic!!, withMosaic!!)
        )
        withMosaic.recycle()
        withoutMosaic.recycle()
    }
}