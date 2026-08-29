package com.whimsicalart.feature.beauty.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.whimsicalart.feature.beauty.detection.DetectedFace
import com.whimsicalart.feature.beauty.detection.FaceDetectionResult
import com.whimsicalart.feature.beauty.detection.FaceDetectorManager
import com.whimsicalart.feature.beauty.detection.FaceLandmarks
import com.whimsicalart.feature.beauty.domain.BeautyProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BeautyViewModelDenoiseDirectionTest {

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

    @Test
    fun `denoise slider leftmost no effect maps to fully permissive softness`() {
        assertEquals(1f, newViewModel().denoiseSoftness(0f), 0.001f)
    }

    @Test
    fun `denoise slider rightmost strongest maps to fully aggressive softness`() {
        assertEquals(0f, newViewModel().denoiseSoftness(1f), 0.001f)
    }

    @Test
    fun `denoise slider midpoint maps to half softness`() {
        assertEquals(0.5f, newViewModel().denoiseSoftness(0.5f), 0.001f)
    }

    @Test
    fun `denoise slider value is clamped to the unit range`() {
        val viewModel = newViewModel()
        assertEquals(0f, viewModel.denoiseSoftness(2f), 0.001f)
        assertEquals(1f, viewModel.denoiseSoftness(-1f), 0.001f)
    }

    @Test
    fun `denoise effect strengthens as the slider moves right`() {
        val viewModel = newViewModel()
        val effectRight = 1f - viewModel.denoiseSoftness(0.75f)
        val effectLeft = 1f - viewModel.denoiseSoftness(0.25f)
        assertEquals(
            "a rightward slider position must denoise more than a leftward one",
            true,
            effectRight > effectLeft
        )
    }
}