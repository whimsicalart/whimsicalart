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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The fine-grained full-size stale-flag propagation (spec → Staleness
 * transitions on structural changes; docs/effect_stack.md): a structure change is
 * a flag-only transition on the full-size fold cache — never a wipe, never a
 * render — and the controller only ever reuses a position whose signature still
 * matches the request, so a preserved-but-actually-invalid value just re-folds.
 *  (i) tail commit: `prevImage` takes `lastImage`'s value, `lastImage` stalls;
 * (ii) removal of the last layer: `lastImage` takes `prevImage`'s value;
 * (iii) removal at/behind the previous layer: `prevImage` + `lastImage` stall;
 * (iv) move at min(from,to) marks exactly the removal's set;
 *  (v) a removal both ≤ N-2 and ≤ currentPosition stalls all three.
 */
@RunWith(RobolectricTestRunner::class)
class StalePropagationTest {

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
        val file = File(context.cacheDir, "stale_propagation.jpg")
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

    private fun scalePass(r: Float, g: Float, b: Float): FilterEffect =
        FilterEffect(filterMatrix = floatArrayOf(
            r, 0f, 0f, 0f, 0f,
            0f, g, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

    private val f1 = scalePass(0.8f, 1f, 1f)
    private val f2 = scalePass(1f, 0.8f, 1f)
    private val vignette = SingleAdjustmentEffect(tool = EditTool.VIGNETTE, value = 0.6f)
    private val three = listOf(f1, f2, vignette)
    private val two = listOf(f1, f2)

    private fun resetCounters(vm: EditorViewModel = viewModel) {
        vm.indexZeroFoldCountForTest = 0
        vm.cacheHitCountForTest = 0
        vm.foldCallsForTest = 0
    }

    @Test
    fun `a tail commit transfers prev from last and stales only last`() {
        val bundle = EditorRenderBundle()
        runBlocking { viewModel.foldToIndex(bundle, two, 2) }
        val cache = viewModel.fullSizeFoldCacheForTest()
        val oldLast = cache.lastImage
        val oldFullSig = cache.stackSignature
        assertNotNull("last primed", oldLast)
        assertTrue("last signature primed", oldFullSig.isNotEmpty())

        resetCounters()
        viewModel.propagateFullSizeStaleness(2, 2, tailAppend = true)
        assertEquals("lastImage marked stale", "", cache.stackSignature)
        assertEquals("prevImage tagged with the old whole-stack key", oldFullSig, cache.prevSig)
        assertTrue("prevImage took the lastImage value", cache.prevImage === oldLast)

        // Whole-stack request for the extended stack re-folds (last was stale).
        val full = runBlocking { viewModel.foldToIndex(bundle, three, 3) }
        assertNotNull(full)
        assertEquals("whole-stack re-folded", 1, viewModel.foldCallsForTest)
        // N-1 request reuses the transferred value (its signature matches).
        val nMinus1 = runBlocking { viewModel.foldToIndex(bundle, three, 2) }
        assertNotNull(nMinus1)
        assertEquals("N-1 hit the transferred prev", 1, viewModel.cacheHitCountForTest)
        assertEquals("N-1 did not re-fold", 1, viewModel.foldCallsForTest)
        val direct = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, viewModel.firstFullForTest()!!, two, scale = 1f)
        }
        assertTrue("transferred prev is the N-1 composite", samePixels(direct!!, nMinus1!!))
    }

    @Test
    fun `removal of the last layer reuses prev as the new last`() {
        val bundle = EditorRenderBundle()
        runBlocking { viewModel.foldToIndex(bundle, three, 3) }
        runBlocking { viewModel.foldToIndex(bundle, three, 2) }
        val cache = viewModel.fullSizeFoldCacheForTest()
        val oldPrevSig = cache.prevSig
        val oldPrev = cache.prevImage
        assertTrue("prev primed", oldPrevSig.isNotEmpty())
        assertNotNull("prev primed", oldPrev)

        resetCounters()
        viewModel.propagateFullSizeStaleness(2, 3)
        assertEquals("prevImage marked stale", "", cache.prevSig)
        assertEquals("lastImage tagged with the old prev key", oldPrevSig, cache.stackSignature)
        assertTrue("lastImage took the prevImage value", cache.lastImage === oldPrev)

        // The shortened whole-stack request is served from the transferred value.
        val out = runBlocking { viewModel.foldToIndex(bundle, two, 2) }
        assertNotNull(out)
        assertEquals("whole-stack hit the transferred last", 1, viewModel.cacheHitCountForTest)
        assertEquals("whole-stack did not re-fold", 0, viewModel.foldCallsForTest)
        val direct = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, viewModel.firstFullForTest()!!, two, scale = 1f)
        }
        assertTrue("transferred last is the shortened composite", samePixels(direct!!, out!!))

        // The N-1 slot is stale → the next N-1 request re-folds.
        val nMinus1 = runBlocking { viewModel.foldToIndex(bundle, two, 1) }
        assertNotNull(nMinus1)
        assertEquals("N-1 re-folded", 1, viewModel.foldCallsForTest)
    }

    @Test
    fun `removal at or before the previous layer marks prev and last stale`() {
        val bundle = EditorRenderBundle()
        runBlocking { viewModel.foldToIndex(bundle, three, 3) }
        runBlocking { viewModel.foldToIndex(bundle, three, 2) }
        val cache = viewModel.fullSizeFoldCacheForTest()
        assertTrue("prev primed", cache.prevSig.isNotEmpty())
        assertTrue("last primed", cache.stackSignature.isNotEmpty())

        resetCounters()
        viewModel.propagateFullSizeStaleness(1, 3)
        assertEquals("prevImage stale", "", cache.prevSig)
        assertEquals("lastImage stale", "", cache.stackSignature)

        // Neither the whole-stack nor the N-1 request reuses a stale value.
        val removedStack = listOf(f1, vignette)
        val whole = runBlocking { viewModel.foldToIndex(bundle, removedStack, 2) }
        val nMinus1 = runBlocking { viewModel.foldToIndex(bundle, removedStack, 1) }
        assertNotNull(whole)
        assertNotNull(nMinus1)
        assertEquals("no cache reuse after rule-3", 0, viewModel.cacheHitCountForTest)
        assertEquals("both requests re-folded", 2, viewModel.foldCallsForTest)
        val direct = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, viewModel.firstFullForTest()!!, removedStack, scale = 1f)
        }
        assertTrue("re-folded composite is correct", samePixels(direct!!, whole!!))
    }

    @Test
    fun `move at min from-to marks exactly the set a removal at that index marks`() {
        val moveState = primedTransitionOf(1, 3)
        val removalState = primedTransitionOf(1, 3)
        assertEquals("move == removal: prevSig", moveState.prevSig, removalState.prevSig)
        assertEquals("move == removal: stackSignature", moveState.stackSignature, removalState.stackSignature)
        assertEquals("move == removal: currentSig", moveState.currentSig, removalState.currentSig)
        assertEquals("move == removal: anchorCount", moveState.anchorCount, removalState.anchorCount)
    }

    @Test
    fun `a removal both at or before prev AND at or before currentPosition stales all three`() {
        val bundle = EditorRenderBundle()
        // Tool on the LAST effect → the N/2 special case anchors currentImage
        // mid-stack (⌊(N-1)/2⌋ = 1) while priming the whole stack.
        viewModel.selectTool(EditTool.VIGNETTE)
        runBlocking { viewModel.foldToIndex(bundle, three, 3) }
        val cache = viewModel.fullSizeFoldCacheForTest()
        assertTrue("currentImage primed at ⌊N/2⌋", cache.currentSig.isNotEmpty())
        assertTrue("prev primed", cache.prevSig.isNotEmpty())
        assertTrue("last primed", cache.stackSignature.isNotEmpty())

        resetCounters()
        viewModel.propagateFullSizeStaleness(1, 3)
        assertEquals("prevImage stale", "", cache.prevSig)
        assertEquals("lastImage stale", "", cache.stackSignature)
        assertEquals("currentImage stale", "", cache.currentSig)

        // The first whole-stack request after rule-3+4 gets no same-position
        // reuse — it re-folds and refreshes the caches (a later request MAY then
        // legitimately hit the freshly refreshed value; that is not stale reuse).
        val whole = runBlocking { viewModel.foldToIndex(bundle, listOf(f1, vignette), 2) }
        assertNotNull(whole)
        assertEquals("no stale reuse after rule-3+4", 0, viewModel.cacheHitCountForTest)
        assertEquals("whole re-folded", 1, viewModel.foldCallsForTest)
        val direct = runBlocking {
            viewModel.renderStackWithBeautyGeometry(
                context, viewModel.firstFullForTest()!!, listOf(f1, vignette), scale = 1f
            )
        }
        assertTrue("re-folded composite is correct", samePixels(direct!!, whole!!))
    }

    private data class CacheState(
        val prevSig: String,
        val stackSignature: String,
        val currentSig: String,
        val anchorCount: Int
    )

    private fun primedTransitionOf(derivedIndex: Int, derivedCount: Int): CacheState {
        val vm = freshViewModel()
        val bundle = EditorRenderBundle()
        runBlocking { vm.foldToIndex(bundle, three, 3) }
        runBlocking { vm.foldToIndex(bundle, three, 2) }
        vm.propagateFullSizeStaleness(derivedIndex, derivedCount)
        val cache = vm.fullSizeFoldCacheForTest()
        return CacheState(cache.prevSig, cache.stackSignature, cache.currentSig, cache.anchorCount)
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