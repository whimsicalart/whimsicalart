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
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Re-commit of an ALREADY-STACKED layer with changed parameters (spec → Re-commit
 * of a re-edited layer at a non-tail position; docs/effect_stack.md). The commit
 * path applies the SAME position rule as a removal at that position, but flag-only
 * — NO value transfers (`lastImage` ← `prevImage` and its mirror accompany only a
 * real removal/tail-commit), and a no-change re-entry is NOT a staleness event.
 * The preview path follows the same position rule lazily and never carries a stale
 * flag: it just re-folds the affected suffix on the next preview request.
 *  (i) unchanged re-entry → nothing marked stale;
 * (ii) changed re-commit at N-1 → only `lastImage` stale, `prevImage` untouched
 *      (and still valid → the next N-1 request hits it);
 * (iii) changed re-commit at `p ≤ N-2` → `prevImage` + `lastImage` stale;
 * (iv) re-commit at `p ≤ currentPosition` → `currentImage` stale too, no transfers;
 *  (v) the preview path regenerates the re-edited layer and its suffix on the fly.
 */
@RunWith(RobolectricTestRunner::class)
class ReCommitStalenessTest {

    private lateinit var context: Context
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = freshViewModel()
    }

    private fun freshViewModel(): EditorViewModel {
        val vm = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
        vm.indexZeroFoldCountForTest = 0
        vm.cacheHitCountForTest = 0
        vm.foldCallsForTest = 0
        val file = File(context.cacheDir, "recommit.jpg")
        if (!file.exists()) {
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
        }
        vm.setImageUri(Uri.fromFile(file))
        return vm
    }

    private fun adj(tool: EditTool, value: Float): SingleAdjustmentEffect =
        SingleAdjustmentEffect(tool = tool, value = value)

    private fun fpsOf(stack: List<StackEffect>): Map<String, String> =
        stack.associate {
            it.layerKey to "adj:${(it as SingleAdjustmentEffect).tool}:${it.value}"
        }

    private fun resetCounters() {
        viewModel.indexZeroFoldCountForTest = 0
        viewModel.cacheHitCountForTest = 0
        viewModel.foldCallsForTest = 0
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

    @Test
    fun `a no-change re-entry marks no full-size cache stale`() {
        val bundle = EditorRenderBundle()
        viewModel.updateBrightness(0.5f)
        runBlocking { viewModel.foldToIndex(bundle, listOf(adj(EditTool.BRIGHTNESS, 0.5f)), 1) }
        val cache = viewModel.fullSizeFoldCacheForTest()
        val sigBefore = cache.stackSignature
        assertTrue("whole primed", sigBefore.isNotEmpty())

        val committed = listOf(adj(EditTool.BRIGHTNESS, 0.5f))
        viewModel.syncLayerState(committed, fpsOf(committed))
        assertEquals("first commit marks nothing", sigBefore, cache.stackSignature)

        resetCounters()
        viewModel.syncLayerState(committed, fpsOf(committed))
        assertEquals("revisiting the untouched tool marks nothing", sigBefore, cache.stackSignature)
        assertEquals("no fold work on a no-change re-entry", 0, viewModel.foldCallsForTest)
    }

    @Test
    fun `a changed re-commit at the last layer marks only last stale and keeps prev`() {
        val bundle = EditorRenderBundle()
        viewModel.updateBrightness(0.5f)
        viewModel.updateVignette(0.4f)
        val two = listOf(adj(EditTool.BRIGHTNESS, 0.5f), adj(EditTool.VIGNETTE, 0.4f))
        runBlocking { viewModel.foldToIndex(bundle, two, 2) }
        runBlocking { viewModel.foldToIndex(bundle, two, 1) }
        val cache = viewModel.fullSizeFoldCacheForTest()
        val oldPrev = cache.prevImage
        val oldPrevSig = cache.prevSig
        val oldLast = cache.lastImage
        assertNotNull("prev primed", oldPrev)
        assertTrue("prev signature primed", oldPrevSig.isNotEmpty())

        viewModel.syncLayerState(two, fpsOf(two))
        assertEquals("first commit marks nothing", oldPrevSig, cache.prevSig)

        // Re-commit the LAST layer (vignette) with a changed value.
        viewModel.updateVignette(0.7f)
        val newTwo = listOf(adj(EditTool.BRIGHTNESS, 0.5f), adj(EditTool.VIGNETTE, 0.7f))
        viewModel.syncLayerState(newTwo, fpsOf(newTwo))
        assertEquals("lastImage marked stale", "", cache.stackSignature)
        assertEquals("prevImage signature untouched", oldPrevSig, cache.prevSig)
        assertTrue("prevImage value untouched", cache.prevImage === oldPrev)
        assertTrue("lastImage unpicked (no value transfer)", cache.lastImage === oldLast)

        // The whole request above the last layer re-folds, while the N-1 request
        // is still served from the UNTOUCHED prev (its prefix really is unchanged).
        resetCounters()
        val nMinus1 = runBlocking { viewModel.foldToIndex(bundle, newTwo, 1) }
        assertNotNull(nMinus1)
        assertEquals("N-1 reuses the intact prefix", 1, viewModel.cacheHitCountForTest)
        assertEquals("N-1 does not re-fold", 0, viewModel.foldCallsForTest)
        val whole = runBlocking { viewModel.foldToIndex(bundle, newTwo, 2) }
        assertNotNull(whole)
        assertEquals("whole re-folded", 1, viewModel.foldCallsForTest)
        val direct = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, viewModel.firstFullForTest()!!, newTwo, scale = 1f)
        }
        assertTrue("re-committed last is correct", samePixels(direct!!, whole!!))
    }

    @Test
    fun `a changed re-commit at or before the previous layer marks prev and last stale`() {
        val bundle = EditorRenderBundle()
        viewModel.updateBrightness(0.5f)
        viewModel.updateVignette(0.4f)
        val two = listOf(adj(EditTool.BRIGHTNESS, 0.5f), adj(EditTool.VIGNETTE, 0.4f))
        runBlocking { viewModel.foldToIndex(bundle, two, 2) }
        runBlocking { viewModel.foldToIndex(bundle, two, 1) }
        val cache = viewModel.fullSizeFoldCacheForTest()
        val oldPrev = cache.prevImage
        val oldLast = cache.lastImage
        viewModel.syncLayerState(two, fpsOf(two))

        // Re-commit the brightness layer (p=0 ≤ N-2).
        viewModel.updateBrightness(0.6f)
        val reTwo = listOf(adj(EditTool.BRIGHTNESS, 0.6f), adj(EditTool.VIGNETTE, 0.4f))
        viewModel.syncLayerState(reTwo, fpsOf(reTwo))
        assertEquals("prevImage stale", "", cache.prevSig)
        assertEquals("lastImage stale", "", cache.stackSignature)
        assertTrue("no prev value transfer", cache.prevImage === oldPrev)
        assertTrue("no last value transfer", cache.lastImage === oldLast)

        // Neither witty position is reused — both re-fold.
        resetCounters()
        val whole = runBlocking { viewModel.foldToIndex(bundle, reTwo, 2) }
        val nMinus1 = runBlocking { viewModel.foldToIndex(bundle, reTwo, 1) }
        assertNotNull(whole)
        assertNotNull(nMinus1)
        assertEquals("no cache reuse after rule-3 re-commit", 0, viewModel.cacheHitCountForTest)
        assertEquals("whole and prev both re-folded", 2, viewModel.foldCallsForTest)
        val direct = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, viewModel.firstFullForTest()!!, reTwo, scale = 1f)
        }
        assertTrue("re-folded composite is correct", samePixels(direct!!, whole!!))
    }

    @Test
    fun `a re-commit at or before currentPosition also stales currentImage without transfers`() {
        val bundle = EditorRenderBundle()
        // Tool on the LAST effect → the N/2 special case anchors currentImage
        // mid-stack (⌊(N-1)/2⌋ = 1), like the removal gate test.
        viewModel.selectTool(EditTool.VIGNETTE)
        runBlocking { viewModel.foldToIndex(bundle, three(), 3) }
        val cache = viewModel.fullSizeFoldCacheForTest()
        val oldPrev = cache.prevImage
        val oldLast = cache.lastImage
        val oldCurrent = cache.currentImage
        assertNotNull("currentImage primed", oldCurrent)
        assertTrue("currentImage signature primed", cache.currentSig.isNotEmpty())

        viewModel.propagateFullSizeRecommitStaleness(1, 3)
        assertEquals("prevImage stale", "", cache.prevSig)
        assertEquals("lastImage stale", "", cache.stackSignature)
        assertEquals("currentImage stale", "", cache.currentSig)
        assertTrue("no prev value transfer", cache.prevImage === oldPrev)
        assertTrue("no last value transfer", cache.lastImage === oldLast)
        assertTrue("no current value transfer", cache.currentImage === oldCurrent)
    }

    @Test
    fun `the preview path regenerates the re-edited layer and its suffix on the fly`() {
        val bundle = EditorRenderBundle()
        viewModel.updateBrightness(0.5f)
        viewModel.updateVignette(0.4f)
        val two = listOf(adj(EditTool.BRIGHTNESS, 0.5f), adj(EditTool.VIGNETTE, 0.4f))
        viewModel.syncLayerState(two, fpsOf(two))

        resetCounters()
        val prime = runBlocking { viewModel.foldToIndex(bundle, two, 2, scale = 0.5f) }
        assertNotNull(prime)
        assertEquals("preview primed by a fold", 1, viewModel.foldCallsForTest)
        val previewCache = viewModel.previewFoldCacheForTest()
        assertTrue("preview whole fresh", previewCache.stackSignature.isNotEmpty())

        // Re-commit the brightness layer (p=0 ≤ N-2): the FULL-SIZE cache gets the
        // position rule, the PREVIEW path carries no stale flags at all.
        viewModel.updateBrightness(0.6f)
        val reTwo = listOf(adj(EditTool.BRIGHTNESS, 0.6f), adj(EditTool.VIGNETTE, 0.4f))
        viewModel.syncLayerState(reTwo, fpsOf(reTwo))
        val fullSize = viewModel.fullSizeFoldCacheForTest()
        assertEquals("full-size prev stale after re-commit", "", fullSize.prevSig)
        assertEquals("full-size last stale after re-commit", "", fullSize.stackSignature)
        assertTrue("preview cache untouched by the full-size flags",
            previewCache.stackSignature.isNotEmpty())

        // The next preview request regenerates the affected suffix ON THE FLY —
        // no stale preview is ever served (the old signature cannot match the
        // re-committed key) and no flag is persisted.
        resetCounters()
        val out = runBlocking { viewModel.foldToIndex(bundle, reTwo, 2, scale = 0.5f) }
        assertNotNull(out)
        assertEquals("preview re-folded, nothing reused", 1, viewModel.foldCallsForTest)
        assertEquals("no same-position preview hit", 0, viewModel.cacheHitCountForTest)
        assertTrue("preview whole refreshed (value, not flag)",
            viewModel.previewFoldCacheForTest().stackSignature.isNotEmpty())
    }

    private fun three(): List<StackEffect> = listOf(
        FilterEffect(filterMatrix = floatArrayOf(
            0.8f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        FilterEffect(filterMatrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 0.8f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        adj(EditTool.VIGNETTE, 0.6f)
    )
}