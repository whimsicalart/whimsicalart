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
import org.robolectric.shadows.ShadowLooper

/**
 * The fold-to-index controller is the SINGLE point of entrance for every
 * colour/composite fold (docs/effect_stack.md → Centralized full-size
 * controller and fold-to-index). These tests probe that claim:
 *  (i) a routed whole-stack request is byte-identical to the legacy direct fold;
 * (ii) the entry-call probe fires exactly once per routed requester through
 *      [EditorViewModel.renderCommittedBitmap] (the pass save/merge/share/zoom>100%
 *      all funnel through) — index-0, cold fold and cache hit each count once;
 * (iii) the geometry track is a SEPARATE process: a deforming merge resolves its
 *      geometry outside the colour controller, never entering its caches.
 */
@RunWith(RobolectricTestRunner::class)
class SinglePointEntranceTest {

    private lateinit var context: Context
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
        viewModel.indexZeroFoldCountForTest = 0
        viewModel.cacheHitCountForTest = 0
        viewModel.foldCallsForTest = 0
        val file = File(context.cacheDir, "single_point_entrance.jpg")
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

    private fun brightness(value: Float) =
        SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = value)

    private fun scalePass(r: Float, g: Float, b: Float): FilterEffect =
        FilterEffect(filterMatrix = floatArrayOf(
            r, 0f, 0f, 0f, 0f,
            0f, g, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

    @Test
    fun `a routed whole-stack fold is byte-identical to the legacy direct fold`() {
        val pristine = viewModel.firstFullForTest()
        assertNotNull("firstFull exists", pristine)
        // The legacy path folded the whole list from the base in one pass.
        val stack = listOf(
            scalePass(0.8f, 1f, 1f),
            scalePass(1f, 0.8f, 1f),
            brightness(0.25f)
        )
        val legacy = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, pristine!!, stack, scale = 1f)
        }
        // The routed path: one recordable fold-to-index call, same bytes.
        val routed = runBlocking {
            viewModel.foldToIndex(EditorRenderBundle(), stack, stack.size, true, scale = 1f)
        }
        assertNotNull(legacy)
        assertNotNull(routed)
        assertTrue("routed fold reproduces the direct fold", samePixels(legacy!!, routed!!))
    }

    @Test
    fun `every whole-stack requester funnels through exactly one fold-to-index call`() {
        val bundle = EditorRenderBundle()
        // Requester without effects → the index-0 rule (base, no fold).
        val baseOnly = runBlocking { viewModel.renderCommittedBitmap(bundle) }
        assertNotNull(baseOnly)
        assertEquals("index-0 request enters once", 1, viewModel.foldControllerEntryCallsForTest())
        assertEquals("index-0 rule fired", 1, viewModel.indexZeroFoldCountForTest)
        viewModel.indexZeroFoldCountForTest = 0

        // Committed brightness → cold fold, then a same-position fresh cache hit.
        viewModel.updateBrightness(0.45f)
        val legacy = runBlocking {
            viewModel.renderStackWithBeautyGeometry(
                context, viewModel.firstFullForTest()!!, listOf(brightness(0.45f)), scale = 1f
            )
        }
        val routed1 = runBlocking { viewModel.renderCommittedBitmap(bundle) }
        val routed2 = runBlocking { viewModel.renderCommittedBitmap(bundle) }
        assertNotNull(routed1)
        assertNotNull(routed2)
        assertPixelsEqual(routed1!!, routed2!!, "repeated routed whole-stack requests")
        assertTrue(
            "routed requester output matches the direct fold",
            samePixels(legacy!!, routed2)
        )
        assertEquals(
            "two routed requests = two entry calls total",
            2, viewModel.foldControllerEntryCallsForTest()
        )
        assertEquals("the cold request folded once", 1, viewModel.foldCallsForTest)
        assertEquals("the repeat hit the cache once", 1, viewModel.cacheHitCountForTest)
    }

    @Test
    fun `a deforming merge resolves geometry on its own track, bypassing the colour controller`() {
        viewModel.geometryTrackBaseCallsForTest = 0
        viewModel.setRotation(90f)
        viewModel.mergeLayers(EditorRenderBundle())
        ShadowLooper.idleMainLooper()
        // The deforming fold ran on the geometry track (its own process) ...
        assertTrue(
            "geometry resolved via its own track",
            viewModel.geometryTrackBaseCallsForTest > 0
        )
        // ... and the merge's colour composite entered the controller exactly once.
        assertEquals(
            "the merge's colour fold is the only controller entry",
            1, viewModel.foldControllerEntryCallsForTest()
        )
    }

    private fun assertPixelsEqual(expected: Bitmap, actual: Bitmap, message: String = "") {
        assertEquals("$message width", expected.width, actual.width)
        assertEquals("$message height", expected.height, actual.height)
        for (x in 0 until expected.width) {
            for (y in 0 until expected.height) {
                assertEquals("$message pixel($x,$y)", expected.getPixel(x, y), actual.getPixel(x, y))
            }
        }
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