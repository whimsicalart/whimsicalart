package codes.pepper.whimsicalart.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.domain.decodeBoundedOriented
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * VERIFY, the full-size fold lifecycle (tracker planned task, directive
 * 2026-09-12): with the TEMPORARY `FullSizeFoldVerify` instrumentation (fold
 * START/END + trigger + source-image identity + elapsed, zoom in/out + effect
 * actions), the four device scenarios are pinned down deterministically here
 * first — the full-size fold fired only by a preview zoom demand
 * (`displayScale > 1`), never as work-only; each committed effect under zoom
 * folds exactly once seeded from the previous full-size image; the preview-sized
 * composite stays in place until the full-size fold lands and swaps atomically
 * (no effect-less full-size frame).
 *
 * Viewport math: Robolectric's default preview ceiling is 320 px, so a 1920x1280
 * source folds a preview composite with max side ~384; in a 160x120 viewport the
 * letterbox fit is ~0.417 → `displayScale` at zoom 1.0 is ≈0.42 (no full fold,
 * scenario 1) while zoom 3.0 gives ≈1.25 (> 1 → a full fold, scenarios 2-4).
 */
@RunWith(RobolectricTestRunner::class)
class FullSizeFoldLifecycleTest {

    private companion object {
        const val VIEW_W = 160
        const val VIEW_H = 120
    }

    private lateinit var context: Context
    private lateinit var viewModel: EditorViewModel
    private lateinit var source: File
    private lateinit var pristine: Bitmap

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
        source = twoToneSource("full_fold_lifecycle_${System.nanoTime()}.jpg")
        viewModel.setImageUri(Uri.fromFile(source))
        viewModel.setViewBox(VIEW_W, VIEW_H)
        pristine = decodeBoundedOriented(context.contentResolver, Uri.fromFile(source))!!
    }

    // --- helpers -----------------------------------------------------------

    private fun twoToneSource(name: String): File {
        val file = File(context.cacheDir, name)
        val src = Bitmap.createBitmap(1920, 1280, Bitmap.Config.ARGB_8888)
        for (y in 0 until 1280) {
            for (x in 0 until 1920) {
                src.setPixel(x, y, if (x < 960) Color.rgb(200, 60, 40) else Color.rgb(40, 120, 200))
            }
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        return file
    }

    // Adjustment effects are committed by WRITING the scalar control state the
    // fold derives them from (buildStackEffects) — the same boundary the app's
    // lost-focus commit uses. Laying instances into effectStack only (as a naive
    // harness would) silently drops them from the fold, which makes effect-less
    // frames and stale full-size caches look indistinguishable from correct
    // behavior.
    private fun commitAdjustment(tool: EditTool, value: Float) {
        when (tool) {
            EditTool.BRIGHTNESS -> viewModel.updateBrightness(value)
            EditTool.CONTRAST -> viewModel.updateContrast(value)
            EditTool.SATURATION -> viewModel.updateSaturation(value)
            EditTool.VIGNETTE -> viewModel.updateVignette(value)
            else -> throw IllegalArgumentException("unmapped adjustment tool $tool")
        }
    }

    private fun adjustment(tool: EditTool, value: Float): SingleAdjustmentEffect =
        SingleAdjustmentEffect(tool = tool, value = value)

    private fun refresh(zoom: Float, excludeCurrentTool: Boolean = false) {
        viewModel.refreshCompositePreview(
            EditorRenderBundle(), includeTail = true, excludeCurrentTool = excludeCurrentTool,
            visibleWidthPx = VIEW_W, visibleHeightPx = VIEW_H, canvasZoom = zoom
        )
    }

    private fun fullSizeOf(effects: List<StackEffect>): Bitmap = runBlocking {
        viewModel.renderStackWithBeautyGeometry(
            context, pristine, effects,
            adaptBackgroundArtifacts = true, scale = 1f
        )
    }

    /** Idles the main looper until [predicate] holds on the published composite or [budgetMs] elapses. */
    private fun waitForComposite(budgetMs: Long = 90_000L, predicate: (Bitmap?) -> Boolean): Bitmap? {
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS)
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + budgetMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            looper.idle()
            val c = viewModel.uiState.value.compositePreview
            if (predicate(c)) return c
            Thread.sleep(5)
        }
        return null
    }

    private fun waitForPreviewComposite(): Bitmap = waitForComposite { c ->
        c != null && c.width < pristine.width && !c.isRecycled
    }!!

    private fun waitForFullComposite(): Bitmap = waitForComposite { c ->
        c != null && c.width == pristine.width && !c.isRecycled
    }!!

    private fun assertPixelsEqual(expected: Bitmap, actual: Bitmap, message: String = "") {
        assertEquals(message, expected.width, actual.width)
        assertEquals(message, expected.height, actual.height)
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                assertEquals("${message} pixel($x,$y)", expected.getPixel(x, y), actual.getPixel(x, y))
            }
        }
    }

    // --- scenarios ---------------------------------------------------------

    @Test
    fun `scenario 1 no preview zoom generates zero full-size images`() {
        commitAdjustment(EditTool.BRIGHTNESS, 0.2f)
        commitAdjustment(EditTool.CONTRAST, -0.3f)

        refresh(zoom = 1f)
        val preview = waitForPreviewComposite()
        // A second action-style refresh (the app's layer-commit pass) at zoom 1
        // must still never demand a full-size fold.
        refresh(zoom = 1f)
        waitForComposite(predicate = { c -> c != null && c.width < pristine.width })
        Thread.sleep(200)

        assertNotNull("a preview-sized composite is published", preview)
        assertTrue(
            "published composite is preview-sized (smaller than the source)",
            preview.width < pristine.width
        )
        val cache = viewModel.fullSizeFoldCacheForTest()
        assertTrue(
            "no full-size composite was ever stamped or demanded: full-size cache untouched " +
                "(lastImage=${if (cache.lastImage == null) "null" else "alive"}, signature=[${cache.stackSignature}])",
            cache.lastImage == null && cache.stackSignature.isEmpty()
        )
    }

    @Test
    fun `scenario 2 zooming only after the last effect lands folds the full-size composite exactly once`() {
        commitAdjustment(EditTool.BRIGHTNESS, 0.2f)
        commitAdjustment(EditTool.CONTRAST, -0.3f)

        refresh(zoom = 1f)
        waitForPreviewComposite()
        assertTrue(
            "before the zoom demand the full-size cache is still unstamped",
            viewModel.fullSizeFoldCacheForTest().stackSignature.isEmpty()
        )

        refresh(zoom = 3f)
        val full = waitForFullComposite()
        assertNotNull("a full-size composite is published past the displayScale threshold", full)

        val e0 = adjustment(EditTool.BRIGHTNESS, 0.2f)
        val e1 = adjustment(EditTool.CONTRAST, -0.3f)
        val expected = fullSizeOf(listOf(e0, e1))
        assertPixelsEqual(expected, full!!, "full composite byte-matches a from-base fold of both effects")
        expected.recycle()
        val cache1 = viewModel.fullSizeFoldCacheForTest()
        val sigAfterFold1 = cache1.stackSignature
        assertTrue(
            "the single zoom demand stamped the full-size fold cache (signature=[$sigAfterFold1])",
            sigAfterFold1.isNotEmpty() && !cache1.lastImage!!.isRecycled
        )

        // A subsequent zoom CHANGE on the same committed stack reuses the cached
        // full-size image — no new fold, no invalidation, no re-stamp.
        refresh(zoom = 4f)
        waitForComposite(predicate = { c -> c != null && c.width == pristine.width })
        Thread.sleep(200)
        val cache2 = viewModel.fullSizeFoldCacheForTest()
        assertEquals(
            "the zoom-only change re-stamps nothing and leaves the full-size image cached",
            sigAfterFold1,
            cache2.stackSignature
        )
        assertTrue(
            "the zoom-only re-use still serves the SAME live full-size image",
            !cache2.lastImage!!.isRecycled
        )
    }

    @Test
    fun `scenario 3 each committed effect under zoom folds exactly once seeded from the previous full-size image`() {
        commitAdjustment(EditTool.BRIGHTNESS, 0.2f)
        commitAdjustment(EditTool.CONTRAST, -0.3f)

        refresh(zoom = 1f)
        waitForPreviewComposite()

        // First zoom demand → the initial full-size fold (over the never-folded
        // full-size predecessor, i.e. a re-ground prefix, never a re-decode).
        refresh(zoom = 3f)
        val full1 = waitForFullComposite()
        assertNotNull(full1)
        assertPixelsEqual(
            fullSizeOf(listOf(adjustment(EditTool.BRIGHTNESS, 0.2f), adjustment(EditTool.CONTRAST, -0.3f))),
            full1!!,
            "first zoomed fold"
        )
        val stamps = mutableListOf(viewModel.fullSizeFoldCacheForTest().stackSignature)
        assertTrue("the first zoom demand stamped a real (effect-ful) full-size fold", stamps.first().isNotEmpty())
        val firstFull = viewModel.firstFullForTest()
        assertNotNull("the initial zoom demand materialized the memoized full-size base", firstFull)

        // The committed stacks to append under zoom: each commits, the effect
        // RESETS the zoom (refresh at 1), then the SAME zoom is re-applied.
        val stacks = listOf(
            listOf(
                adjustment(EditTool.BRIGHTNESS, 0.2f),
                adjustment(EditTool.CONTRAST, -0.3f),
                adjustment(EditTool.VIGNETTE, 0.4f)
            ),
            listOf(
                adjustment(EditTool.BRIGHTNESS, 0.2f),
                adjustment(EditTool.CONTRAST, -0.3f),
                adjustment(EditTool.VIGNETTE, 0.4f),
                adjustment(EditTool.SATURATION, 0.3f)
            )
        )

        for (stack in stacks) {
            commitAdjustment(stack.last().tool, (stack.last() as SingleAdjustmentEffect).value)
            val expected = fullSizeOf(stack)

            // Effect action resets the zoom → refresh at 1: displayScale ≤ 1, so
            // the reset itself must NOT fold; the previous full-size image stays
            // untouched while the preview-sized composite is refreshed.
            val sigBeforeReset = viewModel.fullSizeFoldCacheForTest().stackSignature
            refresh(zoom = 1f)
            val preview = waitForPreviewComposite()
            assertNotNull("the zoom reset republishes the preview-sized composite", preview)
            Thread.sleep(200)
            assertEquals(
                "an effect action that RESETS the zoom never touches the full-size cache",
                sigBeforeReset,
                viewModel.fullSizeFoldCacheForTest().stackSignature
            )

            // Re-apply the SAME zoom: exactly one new full-size fold for this
            // committed effect — the cache is re-stamped with the NEW stack.
            refresh(zoom = 3f)
            val full = waitForFullComposite()
            assertNotNull("one new fold per committed effect", full)
            assertPixelsEqual(expected, full!!, "the fold for this commit contains the just-committed effect")
            val newStamp = viewModel.fullSizeFoldCacheForTest().stackSignature
            assertNotEquals(
                "exactly one new full-size fold per committed effect (a freshly stamped signature)",
                sigBeforeReset,
                newStamp
            )
            stamps += newStamp
            expected.recycle()
        }

        // Identity contract: every full-size fold worked from the memoized
        // `firstFull` base — the SAME instance across all folds, never a
        // re-decode/re-ground of an earlier stage — and the three zoom demands
        // stamped exactly three distinct full-size composites (an effect-less /
        // index-0 rig fold would have left the cache empty instead).
        assertSame(
            "every full-size fold is grounded in the SAME memoized firstFull base (never re-decoded)",
            firstFull,
            viewModel.firstFullForTest()
        )
        assertEquals(
            "the lifecycle produced exactly one effect-ful full-size fold per zoom demand (3 stamps, all non-empty)",
            3,
            stamps.distinct().size
        )
    }

    @Test
    fun `scenario 4 no effect-less full-size frame and no transient full frame during the swap`() {
        commitAdjustment(EditTool.BRIGHTNESS, 0.2f)
        commitAdjustment(EditTool.CONTRAST, -0.3f)
        refresh(zoom = 1f)
        waitForPreviewComposite()

        refresh(zoom = 3f)
        waitForFullComposite()

        commitAdjustment(EditTool.VIGNETTE, 0.4f)
        val expected = fullSizeOf(
            listOf(
                adjustment(EditTool.BRIGHTNESS, 0.2f),
                adjustment(EditTool.CONTRAST, -0.3f),
                adjustment(EditTool.VIGNETTE, 0.4f)
            )
        )

        // Commit under zoom: the preview-sized composite must stay on screen (the
        // committed effect is already folded into it) until the full-size fold
        // lands; the first full-size composite observed must already contain the
        // committed effect — a full-size frame MISSING the effect is a failure.
        refresh(zoom = 1f)
        waitForPreviewComposite()
        val sawPreviewAfterReset = waitForComposite(predicate = { c -> c != null && c.width < pristine.width })
        assertNotNull("the preview-sized composite is still on screen after the commit reset", sawPreviewAfterReset)

        refresh(zoom = 3f)
        var fullSeen = false
        waitForComposite(predicate = { c ->
            c != null && c.width == pristine.width
        })?.let {
            fullSeen = true
            assertPixelsEqual(
                expected,
                it,
                "every observed full-size composite contains the last committed effect (no effect-less frame)"
            )
        }
        assertTrue("the full-size composite eventually lands", fullSeen)
        expected.recycle()
    }
}