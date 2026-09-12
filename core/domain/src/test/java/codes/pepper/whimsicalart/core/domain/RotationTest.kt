package codes.pepper.whimsicalart.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RotationTest {

    private fun assertDegrees(expected: Float, actual: Float) {
        assertEquals(expected, actual, 0.0001f)
    }

    @Test
    fun shouldKeepInRangeValuesUnchanged() {
        assertDegrees(0f, wrapDegrees(0f))
        assertDegrees(90f, wrapDegrees(90f))
        assertDegrees(359f, wrapDegrees(359f))
    }

    @Test
    fun shouldWrapPastThreeSixtyBackToZero() {
        assertDegrees(0f, wrapDegrees(360f))
        assertDegrees(0f, wrapDegrees(720f))
        assertDegrees(90f, wrapDegrees(450f))
    }

    @Test
    fun shouldWrapNegativeIntoPositiveRange() {
        assertDegrees(270f, wrapDegrees(-90f))
        assertDegrees(359.5f, wrapDegrees(-0.5f))
        assertDegrees(0f, wrapDegrees(-360f))
    }

    @Test
    fun shouldReturnHalfOpenRangeNeverReachingThreeSixty() {
        for (degrees in listOf(0f, 359.999f, 400f, -400f, 1000f, -1000f)) {
            val wrapped = wrapDegrees(degrees)
            assert(wrapped >= 0f) { "wrapDegrees($degrees) = $wrapped < 0" }
            assert(wrapped < 360f) { "wrapDegrees($degrees) = $wrapped >= 360" }
        }
    }
}