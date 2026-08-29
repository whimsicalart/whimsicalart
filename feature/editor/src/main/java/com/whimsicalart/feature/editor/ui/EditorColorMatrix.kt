package com.whimsicalart.feature.editor.ui

import androidx.compose.ui.graphics.ColorMatrix

/**
 * Builds a combined [ColorMatrix] from the editor's adjustment values so the
 * preview reflects brightness, contrast, saturation, exposure, temperature,
 * tint and the selected filter.
 *
 * A 4x5 color matrix is represented as a [FloatArray] of 20 elements, laid out
 * row-major (4 rows x 5 columns). Post-concatenation composes the new matrix
 * after the accumulated one.
 */
object EditorColorMatrix {

    fun build(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        exposure: Float,
        temperature: Float,
        tint: Float,
        shadows: Float,
        highlights: Float,
        filterMatrix: FloatArray?
    ): ColorMatrix = ColorMatrix(buildValues(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        exposure = exposure,
        temperature = temperature,
        tint = tint,
        shadows = shadows,
        highlights = highlights,
        filterMatrix = filterMatrix
    ))

    /** Returns the raw 20-element (4x5) combined matrix. Public for testing. */
    fun buildValues(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        exposure: Float,
        temperature: Float,
        tint: Float,
        shadows: Float,
        highlights: Float,
        filterMatrix: FloatArray?
    ): FloatArray {
        var matrix = identity()

        // Exposure is a linear gain on RGB.
        val exposureScale = 1f + exposure * 0.02f
        matrix = postConcat(matrix, scale(exposureScale, exposureScale, exposureScale))

        // Contrast pivots around mid-grey (0.5): scale then re-add 0.5.
        val contrastScale = 1f + contrast * 0.01f
        matrix = postConcat(matrix, scaleTranslate(
            contrastScale, contrastScale, contrastScale,
            0.5f * (1f - contrastScale), 0.5f * (1f - contrastScale), 0.5f * (1f - contrastScale)
        ))

        // Brightness adds a constant to RGB (slider -100..100 -> offset around -0.8..0.8).
        val brightnessOffset = brightness * 0.008f
        matrix = postConcat(matrix, offset(brightnessOffset))

        // Shadows (-100..100) lift/bloom the dark tones: flatten the range a
        // little and add a dark-favouring positive offset. Highlights (-100..100)
        // push the bright tones up or down with a smaller offset.
        val shadowScale = 1f - 0.3f * shadows * 0.008f
        val shadowOffset = shadows * 0.012f
        val highlightOffset = highlights * 0.010f
        matrix = postConcat(
            matrix,
            scaleTranslate(
                shadowScale, shadowScale, shadowScale,
                shadowOffset + highlightOffset,
                shadowOffset + highlightOffset,
                shadowOffset + highlightOffset
            )
        )

        // Temperature (warm/cool) and tint (green/magenta) channel balances.
        val red = 1f + temperature * 0.010f + tint * 0.006f
        val green = 1f - tint * 0.004f
        val blue = 1f - temperature * 0.010f + tint * 0.002f
        matrix = postConcat(matrix, scale(red, green, blue))

        // Saturation.
        matrix = postConcat(matrix, saturation(saturation))

        // Apply the filter's own color matrix on top (filter is applied last).
        if (filterMatrix != null) {
            matrix = postConcat(filterMatrix, matrix)
        }

        return matrix
    }

    private fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    private fun scale(r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
        r, 0f, 0f, 0f, 0f,
        0f, g, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    private fun offset(amount: Float): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, amount,
        0f, 1f, 0f, 0f, amount,
        0f, 0f, 1f, 0f, amount,
        0f, 0f, 0f, 1f, 0f
    )

    private fun scaleTranslate(
        r: Float, g: Float, b: Float,
        tr: Float, tg: Float, tb: Float
    ): FloatArray = floatArrayOf(
        r, 0f, 0f, 0f, tr,
        0f, g, 0f, 0f, tg,
        0f, 0f, b, 0f, tb,
        0f, 0f, 0f, 1f, 0f
    )

    private fun saturation(saturation: Float): FloatArray {
        val sat = 1f + saturation * 0.01f
        val inv = 1f - sat
        val r = 0.213f * inv
        val g = 0.715f * inv
        val b = 0.072f * inv
        return floatArrayOf(
            r + sat, g, b, 0f, 0f,
            r, g + sat, b, 0f, 0f,
            r, g, b + sat, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    /**
     * Right-multiplies [a] (the accumulated matrix) by [b] (a new operation):
     * result = b * a. Both are 20-element arrays.
     */
    private fun postConcat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = 0f
                if (col < 4) {
                    for (k in 0 until 4) {
                        sum += b[row * 5 + k] * a[k * 5 + col]
                    }
                } else {
                    // translation column: b[row][4] + sum_k b[row][k]*a[k][4]
                    sum = b[row * 5 + 4]
                    for (k in 0 until 4) {
                        sum += b[row * 5 + k] * a[k * 5 + 4]
                    }
                }
                out[row * 5 + col] = sum
            }
        }
        return out
    }
}
