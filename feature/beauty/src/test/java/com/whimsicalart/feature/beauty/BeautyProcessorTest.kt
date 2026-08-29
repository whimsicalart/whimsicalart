package com.whimsicalart.feature.beauty

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.whimsicalart.feature.beauty.detection.DetectedFace
import com.whimsicalart.feature.beauty.detection.FaceDetectionResult
import com.whimsicalart.feature.beauty.detection.FaceLandmarks
import com.whimsicalart.feature.beauty.domain.BeautyProcessor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BeautyProcessorTest {

    private lateinit var processor: BeautyProcessor

    @Before
    fun setup() {
        processor = BeautyProcessor()
    }

    @Test
    fun `createDetectedFace preserves detection values`() {
        val face = DetectedFace(
            bounds = Rect(100, 100, 300, 400),
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.85f,
            smilingProbability = 0.7f,
            eulerY = 5f,
            eulerZ = -2f,
            landmarks = FaceLandmarks(
                leftEye = PointF(150f, 180f),
                rightEye = PointF(250f, 180f),
                nose = PointF(200f, 220f),
                mouthLeft = PointF(170f, 280f),
                mouthRight = PointF(230f, 280f),
                leftCheek = PointF(130f, 240f),
                rightCheek = PointF(270f, 240f)
            )
        )

        assertEquals(0.9f, face.leftEyeOpenProbability)
        assertEquals(0.85f, face.rightEyeOpenProbability)
        assertEquals(0.7f, face.smilingProbability)
        assertEquals(5f, face.eulerY)
        assertEquals(-2f, face.eulerZ)
        assertNotNull(face.landmarks.leftEye)
        assertNotNull(face.landmarks.rightEye)
        assertNotNull(face.landmarks.nose)
        assertNotNull(face.landmarks.mouthLeft)
        assertNotNull(face.landmarks.mouthRight)
        assertNotNull(face.landmarks.leftCheek)
        assertNotNull(face.landmarks.rightCheek)
    }

    @Test
    fun `faceDetectionResult stores image dimensions`() {
        val result = FaceDetectionResult(
            faces = emptyList(),
            imageWidth = 1920,
            imageHeight = 1080
        )

        assertEquals(1920, result.imageWidth)
        assertEquals(1080, result.imageHeight)
        assertTrue(result.faces.isEmpty())
    }

    @Test
    fun `faceDetectionResult with multiple faces`() {
        val face1 = DetectedFace(
            bounds = Rect(100, 100, 300, 400),
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.85f,
            smilingProbability = 0.7f,
            eulerY = 5f,
            eulerZ = -2f,
            landmarks = FaceLandmarks(null, null, null, null, null, null, null)
        )

        val face2 = DetectedFace(
            bounds = Rect(500, 100, 700, 400),
            leftEyeOpenProbability = 0.8f,
            rightEyeOpenProbability = 0.8f,
            smilingProbability = 0.5f,
            eulerY = -3f,
            eulerZ = 1f,
            landmarks = FaceLandmarks(null, null, null, null, null, null, null)
        )

        val result = FaceDetectionResult(
            faces = listOf(face1, face2),
            imageWidth = 1920,
            imageHeight = 1080
        )

        assertEquals(2, result.faces.size)
    }

    @Test
    fun `faceLandmarks with null values`() {
        val landmarks = FaceLandmarks(
            leftEye = null,
            rightEye = null,
            nose = null,
            mouthLeft = null,
            mouthRight = null,
            leftCheek = null,
            rightCheek = null
        )

        assertNull(landmarks.leftEye)
        assertNull(landmarks.rightEye)
        assertNull(landmarks.nose)
        assertNull(landmarks.mouthLeft)
        assertNull(landmarks.mouthRight)
        assertNull(landmarks.leftCheek)
        assertNull(landmarks.rightCheek)
    }
}
