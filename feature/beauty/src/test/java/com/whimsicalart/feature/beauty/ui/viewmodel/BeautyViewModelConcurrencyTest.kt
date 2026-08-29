package com.whimsicalart.feature.beauty.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import com.whimsicalart.feature.beauty.detection.DetectedFace
import com.whimsicalart.feature.beauty.detection.FaceDetectionResult
import com.whimsicalart.feature.beauty.detection.FaceDetectorManager
import com.whimsicalart.feature.beauty.detection.FaceLandmarks
import com.whimsicalart.feature.beauty.domain.BeautyProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BeautyViewModelConcurrencyTest {

    private class CountingProcessor : BeautyProcessor() {
        private var internalCalls = 0

        val renderCalls: Int
            get() = internalCalls

        override fun applyLipstick(
            bitmap: Bitmap,
            faceResult: FaceDetectionResult,
            color: Int,
            intensity: Float
        ): Bitmap {
            internalCalls++
            return super.applyLipstick(bitmap, faceResult, color, intensity)
        }
    }

    private class FakeFaceDetectorManager(
        private val result: FaceDetectionResult
    ) : FaceDetectorManager() {
        override suspend fun detectFaces(bitmap: Bitmap): FaceDetectionResult = result
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
    fun `rapid slider updates run exactly one render - the latest`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val processor = CountingProcessor()
            val viewModel = BeautyViewModel(
                faceDetectorManager = FakeFaceDetectorManager(faceResult(64, 64)),
                beautyProcessor = processor
            )
            viewModel.processingDispatcher = dispatcher

            viewModel.setImageUri(Uri.EMPTY, Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.faceResult)

            viewModel.updateLipstickIntensity(0.2f)
            viewModel.updateLipstickIntensity(0.5f)
            viewModel.updateLipstickIntensity(0.9f)

            testScheduler.advanceTimeBy(119)
            testScheduler.runCurrent()
            assertEquals("stale renders must be debounced away", 0, processor.renderCalls)

            testScheduler.advanceTimeBy(1)
            testScheduler.advanceUntilIdle()

            assertEquals("only the trailing render should execute", 1, processor.renderCalls)
            assertFalse(viewModel.uiState.value.isProcessing)
            assertEquals(0.9f, viewModel.uiState.value.lipstickIntensity, 0.001f)
            assertNotNull(viewModel.uiState.value.processedBitmap)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `superseded processed bitmap is recycled on refresh`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val processor = CountingProcessor()
            val viewModel = BeautyViewModel(
                faceDetectorManager = FakeFaceDetectorManager(faceResult(64, 64)),
                beautyProcessor = processor
            )
            viewModel.processingDispatcher = dispatcher

            viewModel.setImageUri(Uri.EMPTY, Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
            testScheduler.advanceUntilIdle()

            viewModel.updateLipstickIntensity(0.5f)
            testScheduler.advanceUntilIdle()

            val firstRender = viewModel.uiState.value.processedBitmap
            assertNotNull(firstRender)

            viewModel.updateLipstickIntensity(0.8f)
            testScheduler.advanceUntilIdle()

            assertTrue("old preview must be recycled when replaced", firstRender!!.isRecycled)
            assertFalse("latest preview must stay alive", viewModel.uiState.value.processedBitmap!!.isRecycled)
            assertFalse(viewModel.uiState.value.isProcessing)
        } finally {
            Dispatchers.resetMain()
        }
    }
}