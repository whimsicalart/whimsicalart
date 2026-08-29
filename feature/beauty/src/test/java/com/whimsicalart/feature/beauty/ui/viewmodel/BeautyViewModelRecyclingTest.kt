package com.whimsicalart.feature.beauty.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.whimsicalart.feature.beauty.detection.DetectedFace
import com.whimsicalart.feature.beauty.detection.FaceDetectionResult
import com.whimsicalart.feature.beauty.detection.FaceDetectorManager
import com.whimsicalart.feature.beauty.detection.FaceLandmarks
import com.whimsicalart.feature.beauty.domain.BeautyProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BeautyViewModelRecyclingTest {

    private class FakeFaceDetectorManager : FaceDetectorManager() {
        override suspend fun detectFaces(bitmap: Bitmap): FaceDetectionResult {
            return FaceDetectionResult(emptyList(), bitmap.width, bitmap.height)
        }
    }

    private fun newViewModel(): BeautyViewModel {
        return BeautyViewModel(
            faceDetectorManager = FakeFaceDetectorManager(),
            beautyProcessor = BeautyProcessor()
        )
    }

    private fun originalBitmap(): Bitmap {
        return Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    }

    private fun faceResult(width: Int, height: Int): FaceDetectionResult {
        val face = DetectedFace(
            bounds = Rect(4, 4, width - 4, height - 4),
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            smilingProbability = 0.5f,
            eulerY = 0f,
            eulerZ = 0f,
            landmarks = FaceLandmarks(
                leftEye = PointF(width * 0.35f, height * 0.35f),
                rightEye = PointF(width * 0.65f, height * 0.35f),
                nose = PointF(width * 0.5f, height * 0.45f),
                mouthLeft = PointF(width * 0.42f, height * 0.6f),
                mouthRight = PointF(width * 0.58f, height * 0.6f),
                leftCheek = PointF(width * 0.3f, height * 0.5f),
                rightCheek = PointF(width * 0.7f, height * 0.5f)
            )
        )
        return FaceDetectionResult(listOf(face), width, height)
    }

    @Test
    fun `stage recycles superseded intermediate but keeps final and original`() = runBlocking {
        val viewModel = newViewModel()
        val original = originalBitmap()
        val intermediate = originalBitmap()

        val final = viewModel.stage(intermediate, original) {
            it.copy(Bitmap.Config.ARGB_8888, true)
        }

        assertTrue("superseded intermediate must be recycled", intermediate.isRecycled)
        assertFalse("the returned bitmap must stay alive", final.isRecycled)
        assertFalse("the original must never be recycled", original.isRecycled)
    }

    @Test
    fun `stage keeps input alive when the transform reuses the same bitmap`() = runBlocking {
        val viewModel = newViewModel()
        val original = originalBitmap()
        val input = originalBitmap()

        val output = viewModel.stage(input, original) { it }

        assertSame(input, output)
        assertFalse("same-object output must not be recycled", input.isRecycled)
        assertFalse("original must not be recycled", original.isRecycled)
    }

    @Test
    fun `stage never recycles the original even when a new bitmap supersedes it`() = runBlocking {
        val viewModel = newViewModel()
        val original = originalBitmap()

        val output = viewModel.stage(original, original) {
            it.copy(Bitmap.Config.ARGB_8888, true)
        }

        assertFalse("original input must survive", original.isRecycled)
        assertFalse("output must survive", output.isRecycled)
    }

    @Test
    fun `render recycles superseded stage outputs and keeps the returned bitmap`() = runBlocking {
        val capturedLipstick = mutableListOf<Bitmap>()
        val spyProcessor = object : BeautyProcessor() {
            override fun applyLipstick(
                bitmap: Bitmap,
                faceResult: FaceDetectionResult,
                color: Int,
                intensity: Float
            ): Bitmap {
                val out = super.applyLipstick(bitmap, faceResult, color, intensity)
                capturedLipstick += out
                return out
            }
        }

        val viewModel = BeautyViewModel(
            faceDetectorManager = FakeFaceDetectorManager(),
            beautyProcessor = spyProcessor
        )
        val original = originalBitmap()
        val state = BeautyUiState(
            lipstickIntensity = 0.5f,
            hairColorIntensity = 0.4f,
            skinDenoiseIntensity = 0.3f
        )

        val final = viewModel.render(state, original, faceResult(48, 48))

        val lipstickIntermediate = capturedLipstick.single()
        assertTrue(
            "a stage output superseded later in the pipeline must be recycled",
            lipstickIntermediate.isRecycled
        )
        assertFalse("the pipeline result must stay alive", final.isRecycled)
        assertFalse("the original must never be recycled", original.isRecycled)
    }
}