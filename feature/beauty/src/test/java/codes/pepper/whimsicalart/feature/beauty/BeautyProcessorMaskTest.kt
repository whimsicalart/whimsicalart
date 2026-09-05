package codes.pepper.whimsicalart.feature.beauty

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import codes.pepper.whimsicalart.feature.beauty.detection.DetectedFace
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult
import codes.pepper.whimsicalart.feature.beauty.detection.FaceLandmarks
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}