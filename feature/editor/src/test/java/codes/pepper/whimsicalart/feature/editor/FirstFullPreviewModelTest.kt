package codes.pepper.whimsicalart.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The named full-image-model entities (docs/effect_stack.md → The full-size image
 * model + Choosing preview resolution): `previewSizeMaxSide`, `firstPreview` (the
 * always-present lohalo-downscaled preview base) and `firstFull` work exactly as
 * specified — fixed at load, orientation-invariant, regenerated from the merged
 * result after a merge, and feeding the first-effect (index-0) preview base.
 */
@RunWith(RobolectricTestRunner::class)
class FirstFullPreviewModelTest {

    private lateinit var context: Context
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
    }

    private fun largeSource(name: String): File {
        // 1920x1280 two-tone (left RED / right GREEN) — larger than the Robolectric
        // preview ceiling (the default device's 640 px ceiling, floored at the short
        // side) so firstPreview is a genuine downscale whose max side ==
        // previewSizeMaxSide.
        val file = File(context.cacheDir, name)
        val src = Bitmap.createBitmap(1920, 1280, Bitmap.Config.ARGB_8888)
        for (y in 0 until 1280) {
            for (x in 0 until 1920) {
                src.setPixel(x, y, if (x < 960) Color.RED else Color.GREEN)
            }
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        return file
    }

    private fun twoTone(name: String, width: Int, height: Int): File {
        // Rect-drawn two-tone (left RED / right GREEN) — same pixels as the pixel
        // loop in [largeSource] but cheap enough for large sources (e.g. 4000x3000).
        val file = File(context.cacheDir, name)
        val src = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(src).apply {
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.GREEN })
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        return file
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

    @Test
    fun `the always-present firstPreview has max side equal to previewSizeMaxSide and the original ratio`() {
        val file = largeSource("first_preview_ceiling.jpg")
        viewModel.setImageUri(Uri.fromFile(file))

        // Robolectric default device (320x470 dp at density 2.0 → 640x940 px):
        // 600 dp = 1200 px trims the wide side below zero, so the ceiling is
        // floored at the shorter side (640).
        val dm = context.resources.displayMetrics
        val expectedCeiling = maxOf(
            minOf(dm.widthPixels, dm.heightPixels),
            maxOf(dm.widthPixels, dm.heightPixels) - (600f * dm.density).roundToInt()
        )
        val ceiling = viewModel.previewCeilingPx()
        assertEquals("the preview ceiling never goes below the device short side", expectedCeiling, ceiling)

        val firstPreview = viewModel.firstPreviewForTest()
        assertNotNull("firstPreview is always present", firstPreview)
        val maxSide = maxOf(firstPreview!!.width, firstPreview.height)
        assertEquals("max side == previewSizeMaxSide", ceiling, maxSide)
        assertEquals(
            "aspect ratio preserved vs the source",
            firstPreview.width.toDouble() / firstPreview.height,
            1920.0 / 1280,
            // ≤ 1px per-side rounding on a sub-700px preview keeps the ratio within
            // ~0.003 of the source; 0.005 is comfortably above that rounding noise.
            0.005
        )
        // A real independent preview bitmap, never the full-size base itself.
        assertNotSame("firstPreview is not an alias of firstFull", viewModel.firstFullForTest(), firstPreview)
    }

    @Test
    @Config(qualifiers = "w1080dp-h2340dp-xhdpi")
    fun `a wide portrait device trims the max side by the 600dp control panel`() {
        // 2160x4680 px, density 2.0 → 600 dp = 1200 px → ceiling = max(2160, 4680 − 1200) = 3480.
        assertEquals(3480, viewModel.previewCeilingPx())
    }

    @Test
    @Config(qualifiers = "w1080dp-h1080dp-xhdpi")
    fun `a near-square device floors the ceiling at the shorter side`() {
        // 2160x2160 px → ceiling = max(2160, 2160 − 1200) = 2160 = the shorter side.
        assertEquals(2160, viewModel.previewCeilingPx())
    }

    @Test
    @Config(qualifiers = "w700dp-h1400dp-xhdpi")
    fun `a 4000x3000 source at a 1600 ceiling lands at 1600x1200 - long side at the ceiling`() {
        // 1400x2800 px, density 2.0 → 600 dp = 1200 px → ceiling = max(1400, 2800 − 1200) = 1600.
        assertEquals(1600, viewModel.previewCeilingPx())
        // The fold base decodes at ≤ 2048 px max side (SEGMENT_SOURCE_MAX_DIM), so the
        // 4:3 source arrives as a capped 2048x1536 — still above the 1600 ceiling.
        val file = twoTone("max_side_ceiling.jpg", 4000, 3000)
        viewModel.setImageUri(Uri.fromFile(file))

        val firstPreview = viewModel.firstPreviewForTest()
        assertNotNull("firstPreview is always present", firstPreview)
        assertEquals("long side lands exactly at the ceiling", 1600, maxOf(firstPreview!!.width, firstPreview.height))
        assertEquals("short side follows the source ratio", 1200, minOf(firstPreview.width, firstPreview.height))
        assertNotSame("firstPreview is not an alias of firstFull", viewModel.firstFullForTest(), firstPreview)
    }

    @Test
    @Config(qualifiers = "w500dp-h1100dp-xhdpi")
    fun `a source whose max side is already under the ceiling is used as-is - never upscaled`() {
        // 1000x2200 px, density 2.0 → 600 dp = 1200 px → ceiling = max(1000, 2200 − 1200) = 1000.
        // A 200x150 source is below it, so the preview IS the pristine base — no upscale.
        assertEquals(1000, viewModel.previewCeilingPx())
        val file = twoTone("under_ceiling.jpg", 200, 150)
        viewModel.setImageUri(Uri.fromFile(file))

        val firstPreview = viewModel.firstPreviewForTest()
        assertNotNull("firstPreview is always present", firstPreview)
        assertEquals("dims untouched", 200, firstPreview!!.width)
        assertEquals("dims untouched", 150, firstPreview.height)
        assertSame("under-ceiling preview IS the full base", viewModel.firstFullForTest(), firstPreview)
    }

    @Test
    fun `rotation and zoom never regenerate previewSizeMaxSide`() {
        val file = largeSource("first_preview_rotation.jpg")
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(600, 400)

        val baseline = viewModel.previewCeilingPx()
        // Rotating the app swaps device sides; the ceiling is the MAX side so it
        // must be stable. Zoom/viewport changes must not re-derive it either.
        viewModel.rotateLeft()
        viewModel.setZoom(2f)
        viewModel.setViewBox(1200, 800)
        assertEquals("rotation must not regenerate the ceiling", baseline, viewModel.previewCeilingPx())
        assertEquals("zoom must not regenerate the ceiling", baseline, viewModel.previewCeilingPx())
        assertEquals("viewport must not regenerate the ceiling", baseline, viewModel.previewCeilingPx())
    }

    @Test
    fun `a small split window is served by a smaller previewScale from the same fixed ceiling`() {
        val file = largeSource("first_preview_small_window.jpg")
        viewModel.setImageUri(Uri.fromFile(file))
        // Spec (docs/effect_stack.md → Choosing preview resolution): the ceiling
        // never follows the window — the layout does. A split-screen shrink must
        // NOT lower the ceiling (that would re-dimension every preview-sized image
        // on resize and drop the memoized base); the reduced on-screen footprint is
        // served by the per-frame previewScale from the SAME device-derived base.
        viewModel.setViewBox(1200, 800)
        val ceiling = viewModel.previewCeilingPx()
        val fullWindowScale = viewModel.previewScaleFor(1920, 1280)
        val firstPreview = viewModel.firstPreviewForTest()!!
        assertTrue("test premise: the full window scale reaches the footprint",
            fullWindowScale * 1920f >= firstPreview.width)

        viewModel.setViewBox(300, 200)
        assertEquals("a window shrink never re-derives the ceiling", ceiling, viewModel.previewCeilingPx())
        val splitWindowScale = viewModel.previewScaleFor(1920, 1280)
        assertTrue("the split window is served by a smaller previewScale", splitWindowScale < fullWindowScale)
        assertSame("firstPreview is NOT re-generated on a window resize",
            firstPreview, viewModel.firstPreviewForTest())
    }

    @Test
    fun `after merge firstPreview is regenerated from the merged firstFull`() {
        val file = largeSource("first_preview_merge.jpg")
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(600, 400)
        // Commit a brightness layer so the merged result actually differs from the
        // pristine photo (proving the regeneration source is the merge output).
        val brightness = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.45f)
        viewModel.syncLayerState(
            listOf(brightness),
            mapOf(brightness.layerKey to "adj:${brightness.tool}:${brightness.value}")
        )

        val before = viewModel.firstPreviewForTest()
        assertNotNull("firstPreview exists pre-merge", before)
        val pristine = viewModel.firstFullForTest()
        assertNotNull("firstFull exists pre-merge", pristine)

        viewModel.mergeLayers(bundle = codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle())

        val after = viewModel.firstPreviewForTest()
        val merged = viewModel.firstFullForTest()
        assertNotNull("firstFull is the merged result after merge", merged)
        assertNotNull("firstPreview regenerated after merge", after)
        assertNotSame("firstFull is repointed to the merged result", pristine, merged)
        assertNotSame("firstPreview is a fresh instance after merge", before, after)
        // The regenerated firstPreview is byte-derived from the merged firstFull.
        assertPixelsEqual(
            Bitmap.createScaledBitmap(
                merged!!, after!!.width, after.height, false
            ),
            after,
            "post-merge firstPreview is regenerated from the merged firstFull"
        )
    }

    @Test
    fun `the first effect base below the preview ceiling is a lohalo downscale of firstPreview not the full source`() {
        val file = largeSource("first_preview_base_below.jpg")
        viewModel.setImageUri(Uri.fromFile(file))
        // Small viewport → preview scale below firstPreview's own footprint
        // (640x426 on the default device): the base must be a downscale of
        // firstPreview — the SAME pixels as createScaledBitmap(firstPreview, ...)
        // — never a fresh bilinear decode of the full source.
        viewModel.setViewBox(200, 120)
        val scale = viewModel.previewScaleFor(1920, 1280)
        assertTrue("scale is below the firstPreview footprint", scale * 1920f < viewModel.firstPreviewForTest()!!.width)
        val expected = Bitmap.createScaledBitmap(
            viewModel.firstPreviewForTest()!!,
            (1920f * scale).roundToInt(),
            (1280f * scale).roundToInt(),
            false
        )
        val bundle = codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle()
        val composite = runBlocking { viewModel.renderCommittedBitmap(bundle, scale = scale) }
        assertNotNull("an empty-effect composite folds the base", composite)
        assertPixelsEqual(expected, composite!!, "index-0 base is firstPreview-derived")
    }

    @Test
    fun `the first effect base at or above the preview ceiling IS firstPreview`() {
        val file = largeSource("first_preview_base_ceiling.jpg")
        viewModel.setImageUri(Uri.fromFile(file))
        // Large viewport → preview scale at/above firstPreview's footprint
        // (640x426 on the default device): the base IS firstPreview itself, so
        // the empty-effect composite matches its dims exactly.
        viewModel.setViewBox(1000, 750)
        val scale = viewModel.previewScaleFor(1920, 1280)
        assertTrue("scale reaches the firstPreview footprint", scale * 1920f >= viewModel.firstPreviewForTest()!!.width)
        val firstPreview = viewModel.firstPreviewForTest()!!
        val bundle = codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle()
        val composite = runBlocking { viewModel.renderCommittedBitmap(bundle, scale = scale) }
        assertNotNull("an empty-effect composite folds the base", composite)
        assertEquals("base at the ceiling is firstPreview (width)", firstPreview.width, composite!!.width)
        assertEquals("base at the ceiling is firstPreview (height)", firstPreview.height, composite.height)
    }

    @Test
    fun `a full-size fold uses firstFullBitmap as its base`() {
        val file = largeSource("first_full_base.jpg")
        viewModel.setImageUri(Uri.fromFile(file))
        val firstFull = viewModel.firstFullForTest()
        assertNotNull("firstFull is present pre-merge", firstFull)
        assertNotSame("firstFull is a fold base, never the resident editedBitmap",
            viewModel.uiState.value.editedBitmap, firstFull)
        val composite = runBlocking {
            viewModel.renderCommittedBitmap(
                codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle()
            )
        }
        assertNotNull("a full-size empty stack folds the base", composite)
        assertEquals("full-size fold runs at full dims", firstFull!!.width, composite!!.width)
        assertEquals("full-size fold runs at full dims", firstFull.height, composite.height)
        // No effects ⇒ the full-size composite IS the base: byte-identical to
        // firstFull, yet an independent instance — firstFull is the fold base.
        assertPixelsEqual(firstFull, composite!!, "full-size base is firstFull")
        assertNotSame("the composite is a copy, never the resident base", firstFull, composite)
    }

    @Test
    fun `after merge firstFullBitmap is the merged result`() {
        val file = largeSource("first_full_merge.jpg")
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(600, 400)
        val brightness = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.45f)
        viewModel.syncLayerState(
            listOf(brightness),
            mapOf(brightness.layerKey to "adj:${brightness.tool}:${brightness.value}")
        )
        val pristine = viewModel.firstFullForTest()!!
        viewModel.mergeLayers(bundle = codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle())
        val merged = viewModel.firstFullForTest()
        assertNotNull("firstFull repointed after merge", merged)
        assertSame("firstFull IS the merged full-size result in state", viewModel.uiState.value.editedBitmap, merged)
        assertNotSame("the merged firstFull is not the pristine decode", pristine, merged)
        assertNotSame("hold-to-compare still owns the pristine original",
            viewModel.uiState.value.originalBitmap, merged)
    }
}