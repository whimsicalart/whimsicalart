package com.whimsicalart.feature.beauty

import android.graphics.PointF
import android.graphics.Rect
import com.whimsicalart.feature.beauty.detection.DetectedFace
import com.whimsicalart.feature.beauty.detection.FaceLandmarks
import com.whimsicalart.feature.beauty.domain.BeautyProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BeautyProcessorSkinRegionTest {

    private lateinit var processor: BeautyProcessor

    @Before
    fun setup() {
        processor = BeautyProcessor()
    }

    @Test
    fun `face skin oval is face-shaped, not the full bounding square`() {
        val face = DetectedFace(
            bounds = Rect(150, 150, 350, 450),
            leftEyeOpenProbability = 1f,
            rightEyeOpenProbability = 1f,
            smilingProbability = 1f,
            eulerY = 0f,
            eulerZ = 0f,
            landmarks = FaceLandmarks(
                leftEye = PointF(200f, 220f),
                rightEye = PointF(300f, 220f),
                nose = PointF(250f, 260f),
                mouthLeft = PointF(220f, 330f),
                mouthRight = PointF(280f, 330f),
                leftCheek = PointF(180f, 280f),
                rightCheek = PointF(320f, 280f)
            )
        )

        val oval = processor.faceSkinOval(face)

        // Cheek-anchored centre (leftCheek=180, rightCheek=320 -> cx=250).
        assertEquals(250f, oval.centerX())
        // The oval must be narrower than the bounding square (halfW = 70).
        assertTrue(
            "oval must be narrower than the bounding square",
            oval.width() < face.bounds.width()
        )
        // Vertical insets mean the oval does not touch the bounds' top/bottom.
        assertTrue("oval top must be below bounds top", oval.top > face.bounds.top.toFloat())
        assertTrue(
            "oval bottom must be above bounds bottom",
            oval.bottom < face.bounds.bottom.toFloat()
        )
        // Regression for the 'small square' bug: the point just left of the
        // cheek-anchored oval but inside the bounds rect must NOT be covered.
        assertTrue(
            "point at x=160 must sit left of the oval (oval.left=${oval.left})",
            oval.left > 160f
        )
    }
}
