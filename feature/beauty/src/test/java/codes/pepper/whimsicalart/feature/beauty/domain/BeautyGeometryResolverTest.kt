package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult
import codes.pepper.whimsicalart.feature.beauty.detection.FaceMeshDetector
import codes.pepper.whimsicalart.feature.beauty.detection.FaceMeshFace
import codes.pepper.whimsicalart.feature.beauty.detection.HairSegmenter
import codes.pepper.whimsicalart.feature.beauty.detection.SkinSegmenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.roundToInt
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class BeautyGeometryResolverTest {

    // --- Adaptive cap derivation ---------------------------------------------

    @Test
    fun `cap clamps to the 1 MP quality floor on a tiny budget`() {
        assertEquals(ML_MIN_PIXELS, adaptivePixelCountCap(16.0 * 1024 * 1024))
    }

    @Test
    fun `cap clamps to the 4 MP CPU ceiling on a huge budget`() {
        assertEquals(ML_MAX_PIXELS, adaptivePixelCountCap(1024.0 * 1024 * 1024))
    }

    @Test
    fun `cap is the budget divided by the per-pixel working set, in clamps`() {
        val budget = 64.0 * 1024 * 1024
        val expected = (budget / (BYTES_PER_ARGB_PIXEL * WORST_CASE_PIXEL_PLANES)).roundToInt()
        assertEquals(expected, adaptivePixelCountCap(budget))
        assertTrue(expected in ML_MIN_PIXELS..ML_MAX_PIXELS)
    }

    // --- Area-based uniform scale factor --------------------------------------

    @Test
    fun `scale factor is 1 below the cap`() {
        assertEquals(1f, adaptiveInputScaleFactor(2_000_000, ML_MAX_PIXELS))
        assertEquals(1f, adaptiveInputScaleFactor(ML_MIN_PIXELS, ML_MIN_PIXELS))
    }

    @Test
    fun `scale factor downscales by root of the pixel-count ratio`() {
        val factor = adaptiveInputScaleFactor(12_000_000, ML_MAX_PIXELS)
        assertEquals(sqrt(ML_MAX_PIXELS.toDouble() / 12_000_000).toFloat(), factor, 1e-4f)
    }

    @Test
    fun `uniform downscale preserves the aspect and lands on the budget for a wide photo`() {
        val w = 6000
        val h = 4000
        val factor = adaptiveInputScaleFactor(w * h, ML_MAX_PIXELS).toDouble()
        val sw = (w * factor).roundToInt()
        val sh = (h * factor).roundToInt()
        assertEquals(w.toFloat() / h, sw.toFloat() / sh, 0.02f)
        assertTrue(sw * sh <= ML_MAX_PIXELS)
        // A 6000x4000 plan now spends the full square budget (2:3 crop of it),
        // not a linear fraction — the whole area-based point of this change.
        assertTrue(sw * sh > (w * h).toDouble() * factor * factor - 4096)
    }

    // --- Recycled-input guard -------------------------------------------------

    @Test
    fun `resolve returns a graceful empty geometry for a recycled image without touching the ML`() {
        val (resolver, mesh, hair, skin) = newResolver()
        val image = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        image.recycle()
        val geometry = runBlocking { resolver.resolve(image, 800 * 600) }
        // None of the pipeline steps are reached; the result degrades gracefully.
        assertEquals(null, mesh.last)
        assertEquals(null, hair.last)
        assertEquals(null, skin.last)
        assertTrue(geometry.faceResult?.faces.orEmpty().isEmpty())
        assertTrue("hair recovers to null path (dome fallback)", geometry.hairPath == null)
    }

    // --- Resolver end-to-end (working input size + pixel-exact rescale) -------

    @Test
    fun `small photo is never downscaled and the working input is the image`() {
        val (resolver, mesh, hair, skin) = newResolver()
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        // 0.96 MP sits under the 1 MP quality floor — no downscale regardless of
        // the device budget, so the ML run happens on the image itself.
        val geometry = runBlocking { resolver.resolve(image, 960_000) }
        assertSame(image, mesh.last)
        assertSame(image, hair.last)
        assertSame(image, skin.last)
        assertEquals(1200, geometry.faceResult!!.imageWidth)
        assertEquals(800, geometry.faceResult!!.imageHeight)
        image.recycle()
    }

    @Test
    fun `12 MP input is downscaled uniformly to the device pixel budget and rescale is pixel-exact`() {
        val (resolver, mesh, hair, skin) = newResolver()
        val image = Bitmap.createBitmap(6000, 4000, Bitmap.Config.ARGB_8888)
        val cap = adaptivePixelCountCap(
            Runtime.getRuntime().maxMemory().toDouble() * MEMORY_BUDGET_FRACTION
        )
        val factor = adaptiveInputScaleFactor(24_000_000, cap).toDouble()
        val geometry = runBlocking { resolver.resolve(image, 24_000_000) }
        val expectedW = (6000 * factor).roundToInt()
        val expectedH = (4000 * factor).roundToInt()
        assertEquals(expectedW, mesh.last!!.width)
        assertEquals(expectedH, mesh.last!!.height)
        assertSame(mesh.last, hair.last)
        assertSame(mesh.last, skin.last)
        assertTrue(mesh.last!!.width * mesh.last!!.height <= cap)
        // All coordinates are scaled back to the source pixel space.
        assertEquals(6000, geometry.faceResult!!.imageWidth)
        assertEquals(4000, geometry.faceResult!!.imageHeight)
        image.recycle()
    }

    @Test
    fun `prefix-fold pixel-count hint steers the cap even for a small running base`() {
        val (resolver, mesh, hair, skin) = newResolver()
        // A preview-resolution base (0.67 MP) whose FULL-SIZE content footprint
        // is 24 MP: the hint (the geometry track's prefix-fold pixel count) is
        // what the adaptive budget is evaluated against, so this small base is
        // still downscaled for the ML run.
        val image = Bitmap.createBitmap(1000, 667, Bitmap.Config.ARGB_8888)
        val cap = adaptivePixelCountCap(
            Runtime.getRuntime().maxMemory().toDouble() * MEMORY_BUDGET_FRACTION
        )
        val factor = adaptiveInputScaleFactor(24_000_000, cap).toDouble()
        runBlocking { resolver.resolve(image, 24_000_000) }
        assertEquals((1000 * factor).roundToInt(), mesh.last!!.width)
        assertEquals((667 * factor).roundToInt(), mesh.last!!.height)
        assertTrue(mesh.last!!.width < 1000)
        assertTrue(mesh.last!!.height < 667)
        image.recycle()
    }

    // --- Resolver per-stage graceful fallback ---------------------------------

    @Test
    fun `a throwing mesh detector degrades the face result while hair and skin still run`() {
        val mesh = ThrowingFaceMesh()
        val hair = RecordingHairSegmenter()
        val skin = RecordingSkinSegmenter()
        val resolver = DefaultBeautyGeometryResolver(mesh, hair, skin)
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val geometry = runBlocking { resolver.resolve(image, 960_000) }
        // The failing stage alone degrades; hair/skin still receive the working
        // input and the exception never propagates.
        assertSame(image, hair.last)
        assertSame(image, skin.last)
        assertTrue(geometry.faceResult?.faces.orEmpty().isEmpty())
        // Empty degradation never touches the caller's bitmap.
        assertTrue(!image.isRecycled)
        image.recycle()
    }

    @Test
    fun `every ML stage throwing still returns an empty geometry instead of aborting`() {
        val resolver = DefaultBeautyGeometryResolver(
            ThrowingFaceMesh(), ThrowingHairSegmenter(), ThrowingSkinSegmenter()
        )
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val geometry = runBlocking { resolver.resolve(image, 960_000) }
        assertTrue(geometry.faceResult?.faces.orEmpty().isEmpty())
        assertEquals(null, geometry.hairPath)
        assertEquals(null, geometry.skinPath)
        assertTrue(!image.isRecycled)
        image.recycle()
    }

    // --- Per-field lazy resolution (never run unneeded ML stages) -------------

    @Test
    fun `FACE only runs the mesh stage, never hair or skin`() {
        val (resolver, mesh, hair, skin) = newResolver()
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val geometry = runBlocking { resolver.resolve(image, 960_000, setOf(GeometryField.FACE)) }
        assertSame(image, mesh.last)
        assertEquals(null, hair.last)
        assertEquals(null, skin.last)
        assertEquals(1200, geometry.faceResult!!.imageWidth)
        image.recycle()
    }

    @Test
    fun `SKIN only runs the skin segmenter and leaves face and hair empty`() {
        val (resolver, mesh, hair, skin) = newResolver()
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val geometry = runBlocking { resolver.resolve(image, 960_000, setOf(GeometryField.SKIN)) }
        assertEquals(null, mesh.last)
        assertEquals(null, hair.last)
        assertSame(image, skin.last)
        assertEquals(null, geometry.faceResult)
        assertEquals(null, geometry.hairPath)
        image.recycle()
    }

    @Test
    fun `HAIR only runs the hair segmenter alone`() {
        val (resolver, mesh, hair, skin) = newResolver()
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val geometry = runBlocking { resolver.resolve(image, 960_000, setOf(GeometryField.HAIR)) }
        assertEquals(null, mesh.last)
        assertSame(image, hair.last)
        assertEquals(null, skin.last)
        assertEquals(null, geometry.faceResult)
        assertEquals(null, geometry.skinPath)
        image.recycle()
    }

    @Test
    fun `SUBJECT_MASK alone runs no beauty ML stages and the resolver yields no mask`() {
        val (resolver, mesh, hair, skin) = newResolver()
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val geometry = runBlocking { resolver.resolve(image, 960_000, setOf(GeometryField.SUBJECT_MASK)) }
        // The subject mask is editor-supplied through the same context; the
        // beauty resolver itself never computes it (and runs no beauty stages).
        assertEquals(null, mesh.last)
        assertEquals(null, hair.last)
        assertEquals(null, skin.last)
        assertEquals(null, geometry.subjectMask)
        assertEquals(null, geometry.faceResult)
        image.recycle()
    }

    @Test
    fun `empty needed fields run no ML stages at all`() {
        val (resolver, mesh, hair, skin) = newResolver()
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val geometry = runBlocking { resolver.resolve(image, 960_000, emptySet()) }
        assertEquals(null, mesh.last)
        assertEquals(null, hair.last)
        assertEquals(null, skin.last)
        assertEquals(null, geometry.faceResult)
        image.recycle()
    }

    private fun newResolver(): ResolverHarness {
        val mesh = RecordingFaceMesh()
        val hair = RecordingHairSegmenter()
        val skin = RecordingSkinSegmenter()
        return ResolverHarness(
            DefaultBeautyGeometryResolver(mesh, hair, skin),
            mesh, hair, skin
        )
    }

    private data class ResolverHarness(
        val resolver: DefaultBeautyGeometryResolver,
        val mesh: RecordingFaceMesh,
        val hair: RecordingHairSegmenter,
        val skin: RecordingSkinSegmenter
    )

    private class RecordingFaceMesh : FaceMeshDetector {
        var last: Bitmap? = null
        override fun detect(source: Bitmap): List<FaceMeshFace> {
            last = source
            return emptyList()
        }
    }

    private class RecordingHairSegmenter : HairSegmenter {
        var last: Bitmap? = null
        override fun segment(source: Bitmap): Bitmap? {
            last = source
            return null
        }
    }

    private class RecordingSkinSegmenter : SkinSegmenter {
        var last: Bitmap? = null
        override fun segment(source: Bitmap): Bitmap? {
            last = source
            return null
        }
    }

    private class ThrowingFaceMesh : FaceMeshDetector {
        override fun detect(source: Bitmap): List<FaceMeshFace> {
            throw IllegalStateException("native mesh unavailable")
        }
    }

    private class ThrowingHairSegmenter : HairSegmenter {
        override fun segment(source: Bitmap): Bitmap? {
            throw IllegalStateException("native hair unavailable")
        }
    }

    private class ThrowingSkinSegmenter : SkinSegmenter {
        override fun segment(source: Bitmap): Bitmap? {
            throw IllegalStateException("native skin unavailable")
        }
    }
}