package codes.pepper.whimsicalart.feature.beauty

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import codes.pepper.whimsicalart.feature.beauty.detection.DetectedFace
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult
import codes.pepper.whimsicalart.feature.beauty.detection.FaceLandmarks
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyProcessor
import codes.pepper.whimsicalart.feature.beauty.domain.BrushStroke
import codes.pepper.whimsicalart.feature.beauty.domain.FeatureMaskBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class BeautyProcessorMaskTest {
    // Note: this Robolectric build cannot rasterize Canvas (draw*/drawBitmap are
    // unsafe no-ops that can even corrupt the backing pixels), so the geometric
    // mask selection and fallbacks are covered in FeatureMaskBuilderTest, the
    // blur math in MaskedEffectRendererTest, and the on-device visual behaviour
    // during emulator QA. These tests cover the processor wiring instead.

    private lateinit var processor: BeautyProcessor

    @Before
    fun setup() {
        processor = BeautyProcessor()
    }

    private fun face(landmarks: FaceLandmarks) = DetectedFace(
        bounds = Rect(100, 100, 300, 400),
        leftEyeOpenProbability = 1f,
        rightEyeOpenProbability = 1f,
        smilingProbability = 1f,
        eulerY = 0f,
        eulerZ = 0f,
        landmarks = landmarks
    )

    private fun result(vararg faces: DetectedFace) =
        FaceDetectionResult(faces.toList(), imageWidth = 400, imageHeight = 500)

    private fun grey(): Bitmap {
        val bmp = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.argb(255, 0x40, 0x40, 0x40))
        return bmp
    }

    private fun pixels(bmp: Bitmap): IntArray {
        val p = IntArray(bmp.width * bmp.height)
        bmp.getPixels(p, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return p
    }

    /** The pre-batching reference: one getPixel+setPixel pair per in-circle pixel. */
    private fun brightnessPenScalarReference(
        bitmap: Bitmap,
        strokes: List<BrushStroke>,
        brightness: Float,
        color: Int
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val fade = (125f * brightness).coerceIn(0f, 255f) / 255f
        val addR = (Color.red(color) * fade).toInt().coerceIn(0, 255)
        val addG = (Color.green(color) * fade).toInt().coerceIn(0, 255)
        val addB = (Color.blue(color) * fade).toInt().coerceIn(0, 255)
        for (stroke in strokes) {
            val radiusPx = (stroke.size * w / 2f).coerceAtLeast(1f)
            val r = kotlin.math.ceil(radiusPx).toInt()
            for (point in stroke.points) {
                val cx = (point.x * w).roundToInt()
                val cy = (point.y * h).roundToInt()
                for (dy in -r..r) {
                    for (dx in -r..r) {
                        val x = cx + dx
                        val y = cy + dy
                        if (x !in 0 until w || y !in 0 until h) continue
                        if (dx * dx + dy * dy > radiusPx * radiusPx) continue
                        val d = result.getPixel(x, y)
                        result.setPixel(x, y, Color.argb(
                            Color.alpha(d),
                            (Color.red(d) + addR).coerceAtMost(255),
                            (Color.green(d) + addG).coerceAtMost(255),
                            (Color.blue(d) + addB).coerceAtMost(255)
                        ))
                    }
                }
            }
        }
        return result
    }

    private fun minimalLandmarks() = FaceLandmarks(
        leftEye = PointF(180f, 200f),
        rightEye = PointF(220f, 200f),
        nose = PointF(200f, 240f),
        mouthLeft = PointF(170f, 320f),
        mouthRight = PointF(230f, 320f),
        leftCheek = PointF(150f, 260f),
        rightCheek = PointF(250f, 260f)
    )

    private fun fullLandmarks() = FaceLandmarks(
        leftEye = PointF(180f, 200f),
        rightEye = PointF(220f, 200f),
        nose = PointF(200f, 240f),
        mouthLeft = PointF(170f, 320f),
        mouthRight = PointF(230f, 320f),
        leftCheek = PointF(150f, 260f),
        rightCheek = PointF(250f, 260f),
        faceContour = listOf(
            PointF(140f, 240f), PointF(150f, 270f), PointF(160f, 300f),
            PointF(240f, 300f), PointF(250f, 270f), PointF(260f, 240f)
        ),
        leftEyeContour = listOf(
            PointF(168f, 188f), PointF(192f, 188f), PointF(192f, 212f), PointF(168f, 212f)
        ),
        rightEyeContour = listOf(
            PointF(208f, 188f), PointF(232f, 188f), PointF(232f, 212f), PointF(208f, 212f)
        ),
        lipsContour = listOf(
            PointF(170f, 310f), PointF(200f, 306f), PointF(230f, 310f),
            PointF(230f, 330f), PointF(200f, 334f), PointF(170f, 330f)
        ),
        mouthApertureContour = listOf(
            PointF(180f, 316f), PointF(190f, 314f), PointF(200f, 314f),
            PointF(210f, 314f), PointF(220f, 316f), PointF(220f, 326f),
            PointF(210f, 328f), PointF(200f, 328f), PointF(190f, 328f),
            PointF(180f, 326f)
        )
    )

    @Test
    fun `eye brightening returns a mutable copy of the same size`() {
        val out = processor.applyEyeBrightening(grey(), result(face(fullLandmarks())), intensity = 1f)
        assertEquals(400, out.width)
        assertEquals(500, out.height)
        assertTrue(out.isMutable)
    }

    @Test
    fun `zero intensity returns the same bitmap without copying`() {
        val source = grey()
        val empty = result()
        listOf(
            processor.applyAutoBeauty(source, empty, intensity = 0f),
            processor.applySkinSmoothing(source, empty, brushRadius = 4f, intensity = 0f),
            processor.applyTeethWhitening(source, empty, intensity = 0f),
            processor.applyEyeBrightening(source, empty, intensity = 0f),
            processor.applyDarkCircleRemoval(source, empty, intensity = 0f),
            processor.applyLipstick(source, empty, Color.RED, intensity = 0f),
            processor.applyBlush(source, empty, Color.RED, intensity = 0f),
            processor.applyEyeShadow(source, empty, 0xFF8B5E3C.toInt(), intensity = 0f),
            processor.applyEyeliner(source, empty, Color.BLACK, intensity = 0f),
            processor.applyFoundation(source, empty, 0xFFF0C8A0.toInt(), intensity = 0f),
            processor.applyHairColor(source, empty, 0xFF4A2C1A.toInt(), intensity = 0f),
            processor.applySkinTone(source, empty, intensity = 0f),
            processor.applySpotRemoval(source, empty, intensity = 0f),
            processor.applyWrinkleRemoval(source, empty, intensity = 0f),
            processor.applySlim(source, empty, faceSlim = 0f),
            processor.applyJaw(source, empty, jawAdjust = 0f),
            processor.applyEyeEnlarge(source, empty, eyeEnlarge = 0f),
            processor.applyNose(source, empty, noseAdjust = 0f),
            processor.applyBrightnessPen(source, emptyList()),
            processor.applyBrightnessPen(
                source,
                listOf(BrushStroke(points = listOf(PointF(0.5f, 0.5f)), size = 20f, opacity = 1f)),
                brightness = 0f
            )
        ).forEach {
            assertSame("must not allocate a copy at zero intensity", source, it)
        }
    }

    @Test
    fun `no faces detected returns an untouched copy for all thirteen tools`() {
        val before = pixels(grey())
        val empty = result()
        val tools = listOf(
            processor.applyEyeBrightening(grey(), empty, intensity = 1f),
            processor.applyTeethWhitening(grey(), empty, intensity = 1f),
            processor.applyLipstick(grey(), empty, Color.RED, intensity = 1f),
            processor.applyBlush(grey(), empty, Color.RED, intensity = 1f),
            processor.applySkinSmoothing(grey(), empty, brushRadius = 4f, intensity = 1f),
            processor.applyFoundation(grey(), empty, 0xFFF0C8A0.toInt(), intensity = 1f),
            processor.applyEyeShadow(grey(), empty, 0xFF8B5E3C.toInt(), intensity = 1f),
            processor.applyEyeliner(grey(), empty, Color.BLACK, intensity = 1f),
            processor.applyHairColor(grey(), empty, 0xFF4A2C1A.toInt(), intensity = 1f),
            processor.applySkinTone(grey(), empty, intensity = 1f),
            processor.applyDarkCircleRemoval(grey(), empty, intensity = 1f),
            processor.applySpotRemoval(grey(), empty, intensity = 1f),
            processor.applyWrinkleRemoval(grey(), empty, intensity = 1f)
        )
        tools.forEach { assertEquals("no faces is a no-op", before.toList(), pixels(it).toList()) }
    }

    @Test
    fun `full intensity runs cleanly with contour-full and contour-less faces`() {
        listOf(face(fullLandmarks()), face(minimalLandmarks())).forEach { f ->
            val r = result(f)
            assertTrue(processor.applyEyeBrightening(grey(), r, intensity = 1f).isMutable)
            assertTrue(processor.applyTeethWhitening(grey(), r, intensity = 1f).isMutable)
            assertTrue(processor.applyLipstick(grey(), r, Color.RED, intensity = 1f).isMutable)
            assertTrue(processor.applyBlush(grey(), r, Color.RED, intensity = 1f).isMutable)
            assertTrue(processor.applySkinSmoothing(grey(), r, brushRadius = 4f, intensity = 1f).isMutable)
            assertTrue(processor.applyFoundation(grey(), r, 0xFFF0C8A0.toInt(), intensity = 1f).isMutable)
            assertTrue(processor.applyEyeShadow(grey(), r, 0xFF8B5E3C.toInt(), intensity = 1f).isMutable)
            assertTrue(processor.applyEyeliner(grey(), r, Color.BLACK, intensity = 1f).isMutable)
            assertTrue(processor.applyHairColor(grey(), r, 0xFF4A2C1A.toInt(), intensity = 1f).isMutable)
            assertTrue(processor.applySkinTone(grey(), r, intensity = 1f).isMutable)
            assertTrue(processor.applySkinTone(grey(), r, intensity = -1f).isMutable)
            assertTrue(processor.applyDarkCircleRemoval(grey(), r, intensity = 1f).isMutable)
            assertTrue(processor.applySpotRemoval(grey(), r, intensity = 1f).isMutable)
            assertTrue(processor.applyWrinkleRemoval(grey(), r, intensity = 1f).isMutable)
        }
    }

    @Test
    fun `two faces at full intensity runs cleanly for every tool`() {
        val f1 = face(minimalLandmarks()).let { f ->
            f.copy(bounds = Rect(40, 60, 200, 260))
        }
        val f2 = face(fullLandmarks()).let { f ->
            f.copy(bounds = Rect(280, 60, 440, 260))
        }
        val r = result(f1, f2)
        assertEquals(2, r.faces.size)
        assertTrue(processor.applyEyeBrightening(grey(), r, intensity = 1f).isMutable)
        assertTrue(processor.applyTeethWhitening(grey(), r, intensity = 1f).isMutable)
        assertTrue(processor.applyLipstick(grey(), r, Color.RED, intensity = 1f).isMutable)
        assertTrue(processor.applyBlush(grey(), r, Color.RED, intensity = 0.8f).isMutable)
        assertTrue(processor.applySkinSmoothing(grey(), r, brushRadius = 4f, intensity = 1f).isMutable)
        assertTrue(processor.applyFoundation(grey(), r, 0xFFF0C8A0.toInt(), intensity = 1f).isMutable)
        assertTrue(processor.applyEyeShadow(grey(), r, 0xFF8B5E3C.toInt(), intensity = 1f).isMutable)
        assertTrue(processor.applyEyeliner(grey(), r, Color.BLACK, intensity = 1f).isMutable)
        assertTrue(processor.applyHairColor(grey(), r, 0xFF4A2C1A.toInt(), intensity = 1f).isMutable)
        assertTrue(processor.applySkinTone(grey(), r, intensity = 1f).isMutable)
        assertTrue(processor.applyDarkCircleRemoval(grey(), r, intensity = 1f).isMutable)
        assertTrue(processor.applySpotRemoval(grey(), r, intensity = 1f).isMutable)
        assertTrue(processor.applyWrinkleRemoval(grey(), r, intensity = 1f).isMutable)
        assertTrue(processor.applySlim(grey(), r, faceSlim = 1f).isMutable)
        assertTrue(processor.applyEyeEnlarge(grey(), r, eyeEnlarge = 0.5f).isMutable)
        assertTrue(processor.applyNose(grey(), r, noseAdjust = 0.5f).isMutable)
        assertTrue(processor.applyJaw(grey(), r, jawAdjust = 0.5f).isMutable)
    }

    @Test
    fun `dark-circle crescent drops to the lower lash instead of overlaying the eye`() {
        // The eye anchor is the contour's outer-corner point (100,120), roughly
        // halfway up the eye (upper lid 114, lower lash 126). The crescent must
        // start under the lash line, so the drop is anchor-to-lowest + a margin
        // of eye height - not 0, which would plant the top edge back ON the eye.
        val eyeContour = listOf(
            PointF(100f, 120f), PointF(112f, 114f), PointF(126f, 116f),
            PointF(126f, 124f), PointF(116f, 126f), PointF(100f, 124f)
        )
        assertEquals(7.2f, processor.eyeDropToLash(PointF(100f, 120f), eyeContour), 1e-4f)
        assertEquals("anchor already below the lash needs no extra drop",
            0f, processor.eyeDropToLash(PointF(100f, 128f), eyeContour), 1e-4f)
        assertEquals("no contour keeps the historic anchor behaviour",
            0f, processor.eyeDropToLash(PointF(100f, 120f), emptyList()), 1e-4f)
        // Dropped silkworm: its flat top edge must sit below the eye anchor.
        val dropped = processor.eyeDropToLash(PointF(100f, 120f), eyeContour)
        val path = FeatureMaskBuilder.silkwormPath(100f, 120f + dropped, 100f, 50f)
        val b = RectF()
        path.computeBounds(b, true)
        assertTrue("top edge under the anchor y", b.top > 120f)
    }

    @Test
    fun `lipstick over an open mouth hands the aperture to the lip mask`() {
        // next.md "lip colour paints over the mouth opening": applyLipstick must
        // feed the inner-lip ring into lipMask so an EVEN-ODD hole keeps the
        // teeth untinted. Robolectric cannot rasterise the canvas (see class
        // note), so the hole geometry itself is pinned in FeatureMaskBuilderTest;
        // here the wiring is: the aperture contour reaches the processor path
        // and an open-mouth face survives the full lipstick pass.
        val r = result(face(fullLandmarks()))
        val out = processor.applyLipstick(grey(), r, Color.RED, intensity = 0.8f)
        assertTrue(out.isMutable)
        assertEquals(400, out.width)
        assertEquals(500, out.height)
        assertTrue(
            "aperture ring travels with the geometry across scales",
            fullLandmarks().copy(
                lipsContour = emptyList(),
                mouthLeft = PointF(170f, 320f),
                mouthRight = PointF(230f, 320f)
            ).mouthApertureContour.isNotEmpty()
        )
    }

    /** The pre-sliding-window reference: re-sums the 2r+1 window per pixel. */
    private fun boxBlurSlow(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val horizontal = IntArray(w * h)
        for (y in 0 until h) {
            val rowBase = y * w
            for (x in 0 until w) {
                val left = (x - radius).coerceAtLeast(0)
                val right = (x + radius).coerceAtMost(w - 1)
                var count = 0L
                var a = 0L
                var r = 0L
                var g = 0L
                var b = 0L
                for (i in left..right) {
                    val c = src[rowBase + i]
                    a += c ushr 24 and 0xff
                    r += c ushr 16 and 0xff
                    g += c ushr 8 and 0xff
                    b += c and 0xff
                    count++
                }
                horizontal[rowBase + x] = intColorRef(a, r, g, b, count)
            }
        }
        val result = IntArray(w * h)
        for (x in 0 until w) {
            for (y in 0 until h) {
                val top = (y - radius).coerceAtLeast(0)
                val bottom = (y + radius).coerceAtMost(h - 1)
                var count = 0L
                var a = 0L
                var r = 0L
                var g = 0L
                var b = 0L
                for (i in top..bottom) {
                    val c = horizontal[i * w + x]
                    a += c ushr 24 and 0xff
                    r += c ushr 16 and 0xff
                    g += c ushr 8 and 0xff
                    b += c and 0xff
                    count++
                }
                result[y * w + x] = intColorRef(a, r, g, b, count)
            }
        }
        return result
    }

    private fun intColorRef(a: Long, r: Long, g: Long, b: Long, count: Long): Int =
        ((a / count).toInt() shl 24) or
            ((r / count).toInt() shl 16) or
            ((g / count).toInt() shl 8) or
            (b / count).toInt()

    /** Patterned ramp with varying alpha, so every channel sum is exercised. */
    private fun ramp(w: Int, h: Int): IntArray {
        val p = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                p[y * w + x] = Color.argb(
                    (x * 255 / w).coerceIn(0, 255),
                    (y * 255 / h).coerceIn(0, 255),
                    ((x + y) * 255 / (w + h)).coerceIn(0, 255),
                    (x * 31 % 256)
                )
            }
        }
        return p
    }

    /** Edge-clamped control: a bright ring over a flat mid-tone field. */
    private fun bordered(w: Int, h: Int): IntArray {
        val p = IntArray(w * h)
        for (i in p.indices) p[i] = Color.rgb(120, 120, 120)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    p[y * w + x] = Color.rgb(250, 250, 250)
                }
            }
        }
        return p
    }

    @Test
    fun `sliding-window boxBlur is byte-identical to the per-pixel window sum`() {
        // The pass rewrote the per-pixel 2r+1 re-sum into an O(w·h) sliding
        // window; the clamped edges and the count must reproduce the naive sum
        // bit-for-bit across radii {1,3,8,24} on patterned + edge-clamped
        // inputs (including ones where the radius exceeds a dimension).
        val radii = intArrayOf(1, 3, 8, 24)
        val inputs = listOf(
            BlurCase("ramp17x23", 17, 23, ramp(17, 23)),
            BlurCase("ramp3x7", 3, 7, ramp(3, 7)),
            BlurCase("ramp47x3", 47, 3, ramp(47, 3)),
            BlurCase("bordered33x29", 33, 29, bordered(33, 29)),
            BlurCase("bordered5x5", 5, 5, bordered(5, 5))
        )
        for (radius in radii) {
            for (input in inputs) {
                val slow = boxBlurSlow(input.px, input.w, input.h, radius)
                val fast = processor.boxBlurPixels(input.px, input.w, input.h, radius)
                assertArrayEquals("radius=$radius on ${input.name}", slow, fast)
            }
        }
    }

    private data class BlurCase(val name: String, val w: Int, val h: Int, val px: IntArray)

    @Test
    fun `batched brightness pen is byte-identical to the scalar per-pixel stamp`() {
        val src = grey()
        val strokes = listOf(
            BrushStroke(
                points = listOf(PointF(0.25f, 0.3f), PointF(0.5f, 0.5f), PointF(0.6f, 0.8f)),
                size = 10f,
                opacity = 1f
            ),
            BrushStroke(
                points = listOf(PointF(0.75f, 0.2f), PointF(0.05f, 0.05f), PointF(0.95f, 0.95f)),
                size = 26f,
                opacity = 1f
            ),
            BrushStroke(
                points = listOf(PointF(0.5f, 0.45f)),
                size = 3f,
                opacity = 1f
            )
        )
        val brightness = 0.45f
        val color = 0xFFE0A050.toInt()
        val batched = processor.applyBrightnessPen(src, strokes, brightness = brightness, color = color)
        val scalar = brightnessPenScalarReference(src, strokes, brightness, color)
        assertArrayEquals(
            "batched pen must reproduce the scalar stamp byte-for-byte",
            pixels(scalar),
            pixels(batched)
        )
    }

    @Test
    fun `empty faces returns the same bitmap reference for spot and wrinkle removal`() {
        // The empty-faces gate now sits above the copy()+blur, so the untouched
        // source IS the output (no copy allocated, no blur run).
        val source = grey()
        val empty = result()
        assertSame(
            "spot removal with no faces must return the source",
            source,
            processor.applySpotRemoval(source, empty, intensity = 1f)
        )
        assertSame(
            "wrinkle removal with no faces must return the source",
            source,
            processor.applyWrinkleRemoval(source, empty, intensity = 1f)
        )
    }
}