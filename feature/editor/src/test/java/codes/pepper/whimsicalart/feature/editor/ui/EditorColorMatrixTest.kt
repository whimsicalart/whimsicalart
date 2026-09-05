package codes.pepper.whimsicalart.feature.editor.ui

import codes.pepper.whimsicalart.feature.editor.domain.EditorColorMatrix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorColorMatrixTest {

    @Test
    fun `neutral adjustments with no filter return identity`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 0f, contrast = 0f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 0f, highlights = 0f,
            filterMatrix = null
        )

        // Applying identity to an input must reproduce it.
        assertArrayEquals(
            floatArrayOf(0.5f, 0.5f, 0.5f, 1f),
            apply(matrix, 0.5f, 0.5f, 0.5f, 1f),
            0.001f
        )
    }

    @Test
    fun `positive brightness raises output`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 50f, contrast = 0f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 0f, highlights = 0f,
            filterMatrix = null
        )
        val out = apply(matrix, 0f, 0f, 0f, 1f)
        assert(out[0] > 0f)
        assert(out[1] > 0f)
        assert(out[2] > 0f)
    }

    @Test
    fun `negative brightness lowers output`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = -50f, contrast = 0f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 0f, highlights = 0f,
            filterMatrix = null
        )
        val out = apply(matrix, 0.2f, 0.2f, 0.2f, 1f)
        assert(out[0] < 0.2f)
    }

    @Test
    fun `full negative saturation produces luminance grey`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 0f, contrast = 0f, saturation = -100f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 0f, highlights = 0f,
            filterMatrix = null
        )
        val out = apply(matrix, 1f, 0f, 0f, 1f)
        val expected = 0.213f
        assertEquals(expected, out[0], 0.001f)
        assertEquals(expected, out[1], 0.001f)
        assertEquals(expected, out[2], 0.001f)
    }

    @Test
    fun `contrast around mid-grey pivot keeps the centre unchanged`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 0f, contrast = 50f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 0f, highlights = 0f,
            filterMatrix = null
        )
        val out = apply(matrix, 0.5f, 0.5f, 0.5f, 1f)
        assertEquals(0.5f, out[0], 0.001f)
        assertEquals(0.5f, out[1], 0.001f)
        assertEquals(0.5f, out[2], 0.001f)
    }

    @Test
    fun `positive shadows lifts dark tones`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 0f, contrast = 0f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 100f, highlights = 0f,
            filterMatrix = null
        )
        val out = apply(matrix, 0.2f, 0.2f, 0.2f, 1f)
        assertTrue("shadow lift must raise dark outputs", out[0] > 0.2f)
    }

    @Test
    fun `negative highlights darkens bright tones`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 0f, contrast = 0f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 0f, highlights = -100f,
            filterMatrix = null
        )
        val out = apply(matrix, 0.8f, 0.8f, 0.8f, 1f)
        assertTrue("pulled-down highlights must lower bright outputs", out[0] < 0.8f)
    }

    @Test
    fun `positive highlights brightens bright tones`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 0f, contrast = 0f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 0f, highlights = 100f,
            filterMatrix = null
        )
        val out = apply(matrix, 0.8f, 0.8f, 0.8f, 1f)
        assertTrue("lifted highlights must raise bright outputs", out[0] > 0.8f)
    }

    @Test
    fun `brightness at max is clearly visible`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 100f, contrast = 0f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 0f, highlights = 0f,
            filterMatrix = null
        )
        val out = apply(matrix, 0.5f, 0.5f, 0.5f, 1f)
        assertTrue("max brightness must lift mid-grey well beyond noise", out[0] > 0.6f)
    }

    @Test
    fun `positive shadows lifts darks more than brights`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 0f, contrast = 0f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 100f, highlights = 0f,
            filterMatrix = null
        )
        val darkLift = apply(matrix, 0.2f, 0.2f, 0.2f, 1f)[0] - 0.2f
        val brightLift = apply(matrix, 0.8f, 0.8f, 0.8f, 1f)[0] - 0.8f
        assertTrue("shadows must favour the dark end", darkLift > brightLift)
    }

    @Test
    fun `positive highlights targets the bright end`() {
        val matrix = EditorColorMatrix.buildValues(
            brightness = 0f, contrast = 0f, saturation = 0f,
            exposure = 0f, temperature = 0f, tint = 0f,
            shadows = 0f, highlights = 100f,
            filterMatrix = null
        )
        val dark = apply(matrix, 0.2f, 0.2f, 0.2f, 1f)[0]
        val bright = apply(matrix, 0.8f, 0.8f, 0.8f, 1f)[0]
        assertTrue("highlights must push brights up hard", bright > 0.95f)
        assertTrue("highlights must leave darks roughly alone", kotlin.math.abs(dark - 0.2f) < 0.05f)
    }

    private fun apply(
        matrix: FloatArray,
        r: Float, g: Float, b: Float, a: Float
    ): FloatArray {
        // ColorMatrix offsets live in the 0..255 domain; normalize the
        // translation column for the normalized (0..1) inputs used here.
        return floatArrayOf(
            matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4] / 255f,
            matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9] / 255f,
            matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14] / 255f,
            matrix[15] * r + matrix[16] * g + matrix[17] * b + matrix[18] * a + matrix[19] / 255f
        )
    }
}
