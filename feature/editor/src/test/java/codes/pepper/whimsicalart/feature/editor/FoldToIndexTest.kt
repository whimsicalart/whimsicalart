package codes.pepper.whimsicalart.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.FilterEffect
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
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
 * The centralized fold-to-target-index controller (docs/effect_stack.md →
 * Centralized full-size controller and fold-to-index): index-0 returns the base
 * with no fold, a same-position fresh cache returns without re-folding, a
 * mid-stack request folds only the missing suffix, and the whole-stack request
 * refreshes last/prev and anchors currentImage at ⌊N/2⌋.
 */
@RunWith(RobolectricTestRunner::class)
class FoldToIndexTest {

    private lateinit var context: Context
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
        viewModel.indexZeroFoldCountForTest = 0
        viewModel.cacheHitCountForTest = 0
        viewModel.foldCallsForTest = 0
        // Three vertical strips (RED / GREEN / BLUE) so each channel-scale pass
        // below provably changes pixels at its own position in the fold.
        val file = File(context.cacheDir, "fold_to_index.jpg")
        val src = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888)
        for (y in 0 until 640) {
            for (x in 0 until 960) {
                src.setPixel(x, y, when {
                    x < 320 -> Color.RED
                    x < 640 -> Color.GREEN
                    else -> Color.BLUE
                })
            }
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        viewModel.setImageUri(Uri.fromFile(file))
    }

    private fun scalePass(r: Float, g: Float, b: Float): FilterEffect =
        FilterEffect(filterMatrix = floatArrayOf(
            r, 0f, 0f, 0f, 0f,
            0f, g, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

    private val vignette = SingleAdjustmentEffect(tool = EditTool.VIGNETTE, value = 0.6f)
    private val stack = listOf(
        scalePass(0.8f, 1f, 1f),
        scalePass(1f, 0.8f, 1f),
        vignette
    )

    private fun assertPixelsEqual(expected: Bitmap, actual: Bitmap, message: String = "") {
        assertEquals("$message width", expected.width, actual.width)
        assertEquals("$message height", expected.height, actual.height)
        for (x in 0 until expected.width) {
            for (y in 0 until expected.height) {
                assertEquals("$message pixel($x,$y)", expected.getPixel(x, y), actual.getPixel(x, y))
            }
        }
    }

    @Test
    fun `index-0 request returns firstFull with no effect fold`() {
        val firstFull = viewModel.firstFullForTest()
        assertNotNull("firstFull exists", firstFull)
        val beforeFolds = viewModel.foldCallsForTest
        val beforeZero = viewModel.indexZeroFoldCountForTest
        val out = runBlocking {
            viewModel.foldToIndex(EditorRenderBundle(), stack, 0)
        }
        assertNotNull("index-0 produces the base", out)
        assertEquals("index-0 runs at full dims", firstFull!!.width, out!!.width)
        assertEquals("index-0 runs at full dims", firstFull.height, out.height)
        assertPixelsEqual(firstFull, out, "index-0 returns firstFull")
        assertEquals("index-0 fast path fired", 1, viewModel.indexZeroFoldCountForTest - beforeZero)
        assertEquals("no effect fold was performed", 0, viewModel.foldCallsForTest - beforeFolds)
    }

    @Test
    fun `a same-position fresh cache returns without re-folding`() {
        val bundle = EditorRenderBundle()
        // Whole-stack request folds once and stores lastImage.
        val full = runBlocking { viewModel.foldToIndex(bundle, stack, 3) }
        assertNotNull(full)
        assertEquals("the first whole-stack request folds", 1, viewModel.foldCallsForTest)
        // The identical request is now a fresh same-position cache hit.
        val repeated = runBlocking { viewModel.foldToIndex(bundle, stack, 3) }
        assertPixelsEqual(full!!, repeated!!, "repeated whole-stack request")
        assertEquals("no re-fold on the cache hit", 1, viewModel.foldCallsForTest)
        assertEquals("the whole-stack request hit lastImage", 1, viewModel.cacheHitCountForTest)
        // A mid-stack (N-1) request folds once, stores prevImage, then hits it.
        val mid = runBlocking { viewModel.foldToIndex(bundle, stack, 2) }
        assertNotNull(mid)
        val midRepeated = runBlocking { viewModel.foldToIndex(bundle, stack, 2) }
        assertPixelsEqual(mid!!, midRepeated!!, "repeated N-1 request")
        assertEquals("the N-1 refresh folded once", 2, viewModel.foldCallsForTest)
        assertEquals("the repeated N-1 request hit prevImage", 2, viewModel.cacheHitCountForTest)
    }

    @Test
    fun `a mid-stack request folds only the missing suffix`() {
        val bundle = EditorRenderBundle()
        val prefix = runBlocking { viewModel.foldToIndex(bundle, stack, 2) }
        assertNotNull("mid-stack composite exists", prefix)
        // The prefix composite stops at 2 effects: folding ONLY the missing last
        // effect over it reproduces the full-stack fold exactly.
        val full = runBlocking { viewModel.foldToIndex(bundle, stack, 3) }
        val fromPrefix = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, prefix!!, listOf(vignette))
        }
        assertNotNull(fromPrefix)
        assertFalse("the suffix changes the pixels", samePixels(full!!, prefix!!))
        assertPixelsEqual(full, fromPrefix, "mid-stack prefix resumes into the full fold")
    }

    @Test
    fun `whole-stack request refreshes last and prev and anchors currentImage at N-half`() {
        // Tool occupies the LAST effect → the N/2 special case can anchor
        // currentImage mid-stack while folding the whole stack.
        viewModel.selectTool(EditTool.VIGNETTE)
        val bundle = EditorRenderBundle()
        val out = runBlocking { viewModel.foldToIndex(bundle, stack, 3) }
        assertNotNull(out)
        val cache = viewModel.fullSizeFoldCacheForTest()
        // lastImage refreshed (whole-stack request, was stale) — full key.
        assertNotNull("lastImage refreshed", cache.lastImage)
        // prevImage refreshed (the fold rendered the N-1 image and prev was stale).
        assertNotNull("prevImage refreshed", cache.prevImage)
        assertTrue("prevSig recorded", cache.prevSig.isNotEmpty())
        // The N/2 anchor: currentImage sits at ⌊(N-1)/2⌋ = 1 with its signature.
        assertEquals("currentImage anchored at ⌊(N-1)/2⌋", 1, cache.anchorCount)
        assertNotNull("currentImage anchored mid-stack", cache.currentImage)
        assertTrue("currentSig recorded", cache.currentSig.isNotEmpty())
        // The next whole-stack request hits the fresh lastImage without re-folding.
        val repeated = runBlocking { viewModel.foldToIndex(bundle, stack, 3) }
        assertPixelsEqual(out!!, repeated!!, "cached whole-stack request")
        assertEquals("cached whole-stack request did not re-fold", 1, viewModel.foldCallsForTest)
        assertEquals("cached whole-stack request hit lastImage", 1, viewModel.cacheHitCountForTest)
    }

    private fun samePixels(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (x in 0 until a.width) {
            for (y in 0 until a.height) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) return false
            }
        }
        return true
    }
}