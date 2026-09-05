package codes.pepper.whimsicalart.feature.filters.domain

import android.graphics.ColorMatrix

data class Filter(
    val id: String,
    val name: String,
    val category: FilterCategory,
    val shaderCode: String,
    val defaultParams: FloatArray = floatArrayOf(0f, 0f, 0f, 1f),
    val intensity: Float = 1f,
    val previewColorMatrix: ColorMatrix? = null
)

enum class FilterCategory {
    BASIC,
    ARTISTIC,
    PORTRAIT,
    LANDSCAPE,
    VINTAGE,
    MONOCHROME
}

object FilterPresets {
    private const val IDENTITY_SHADER = """
        #version 300 es
        precision mediump float;
        in vec2 vTexCoord;
        out vec4 fragColor;
        uniform sampler2D uTexture;
        void main() {
            fragColor = texture(uTexture, vTexCoord);
        }
    """

    val filters = listOf(
        Filter(
            id = "original",
            name = "Original",
            category = FilterCategory.BASIC,
            shaderCode = IDENTITY_SHADER
        ),
        Filter(
            id = "sepia",
            name = "Sepia",
            category = FilterCategory.VINTAGE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = sepiaMatrix(0.85f)
        ),
        Filter(
            id = "grayscale",
            name = "B&W",
            category = FilterCategory.MONOCHROME,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = grayscaleMatrix(1f)
        ),
        Filter(
            id = "bw_vivid",
            name = "B&W Vivid",
            category = FilterCategory.MONOCHROME,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.40f, 0.55f, 0.05f, 0f, -18f,
                0.40f, 0.55f, 0.05f, 0f, -18f,
                0.40f, 0.55f, 0.05f, 0f, -18f,
                0f, 0f, 0f, 1f, 0f
            ))
        ),
        Filter(
            id = "vintage",
            name = "Vintage",
            category = FilterCategory.VINTAGE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.9f, 0.12f, 0.08f, 0f, 8f,
                0.05f, 0.9f, 0.05f, 0f, 4f,
                0.02f, 0.08f, 0.75f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f
            ))
        ),
        Filter(
            id = "neutral",
            name = "Neutral",
            category = FilterCategory.BASIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix().apply { setSaturation(0.85f) }
        ),
        Filter(
            id = "romantic",
            name = "Romantic",
            category = FilterCategory.PORTRAIT,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.05f, 0f, 0f, 0f, 14f,
                0f, 0.97f, 0f, 0f, 8f,
                0f, 0f, 0.95f, 0f, 14f,
                0f, 0f, 0f, 1f, 0f
            ))
        ),
        Filter(
            id = "muted",
            name = "Muted",
            category = FilterCategory.ARTISTIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.85f, 0f, 0f, 0f, 4f,
                0f, 0.85f, 0f, 0f, 4f,
                0f, 0f, 0.85f, 0f, 4f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(0.6f) }
        ),
        Filter(
            id = "foodie",
            name = "Foodie",
            category = FilterCategory.PORTRAIT,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.12f, 0f, 0f, 0f, 12f,
                0f, 1.02f, 0f, 0f, 6f,
                0f, 0f, 0.88f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.1f) }
        ),
        Filter(
            id = "refreshing",
            name = "Refreshing",
            category = FilterCategory.LANDSCAPE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.95f, 0f, 0f, 0f, 6f,
                0f, 1.05f, 0f, 0f, 8f,
                0f, 0f, 1.08f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.15f) }
        ),
        Filter(
            id = "cool",
            name = "Cool",
            category = FilterCategory.BASIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.97f, 0f, 0f, 0f, -6f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1.06f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            ))
        ),
        Filter(
            id = "dreamy",
            name = "Dreamy",
            category = FilterCategory.ARTISTIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.02f, 0f, 0f, 0f, 18f,
                0f, 1.0f, 0f, 0f, 16f,
                0f, 0f, 1.04f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(0.8f) }
        ),
        Filter(
            id = "late_autumn",
            name = "Late Autumn",
            category = FilterCategory.VINTAGE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.15f, 0.06f, 0f, 0f, 10f,
                0.05f, 1.05f, 0f, 0f, 4f,
                0f, 0.02f, 0.82f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f
            ))
        ),
        Filter(
            id = "film",
            name = "Film",
            category = FilterCategory.VINTAGE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.92f, 0.04f, 0.02f, 0f, 6f,
                0.02f, 0.92f, 0.04f, 0f, 4f,
                0.01f, 0.05f, 0.9f, 0f, 2f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(0.9f) }
        ),
        Filter(
            id = "youth",
            name = "Youth",
            category = FilterCategory.PORTRAIT,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.06f, 0f, 0f, 0f, 10f,
                0f, 1.04f, 0f, 0f, 8f,
                0f, 0f, 1.02f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.1f) }
        ),
        Filter(
            id = "dark_gold",
            name = "Dark Gold",
            category = FilterCategory.VINTAGE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.05f, 0.08f, 0.02f, 0f, -10f,
                0.03f, 0.95f, 0.03f, 0f, -8f,
                0f, 0.04f, 0.62f, 0f, -14f,
                0f, 0f, 0f, 1f, 0f
            ))
        ),
        Filter(
            id = "cyberpunk",
            name = "Cyberpunk",
            category = FilterCategory.ARTISTIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.0f, 0.1f, 0.15f, 0f, -4f,
                0.0f, 0.85f, 0.1f, 0f, 4f,
                0.1f, 0.1f, 1.05f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.3f) }
        ),
        Filter(
            id = "clear",
            name = "Clear",
            category = FilterCategory.LANDSCAPE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.06f, 0f, 0f, 0f, 10f,
                0f, 1.06f, 0f, 0f, 10f,
                0f, 0f, 1.06f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.1f) }
        ),
        Filter(
            id = "uv",
            name = "Ultra-Violet",
            category = FilterCategory.ARTISTIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.0f, 0f, 0.02f, 0f, 2f,
                0f, 1.0f, 0f, 0f, -2f,
                0.02f, 0f, 1.08f, 0f, 14f,
                0f, 0f, 0f, 1f, 0f
            ))
        ),
        Filter(
            id = "cir_polarizer",
            name = "C. Polarizer",
            category = FilterCategory.LANDSCAPE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.94f, 0f, 0f, 0f, -4f,
                0f, 0.96f, 0f, 0f, -4f,
                0f, 0f, 0.92f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.15f) }
        ),
        Filter(
            id = "lin_polarizer",
            name = "L. Polarizer",
            category = FilterCategory.LANDSCAPE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.96f, 0f, 0f, 0f, 0f,
                0f, 0.97f, 0f, 0f, 0f,
                0f, 0f, 0.94f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.1f) }
        ),
        Filter(
            id = "neutral_density",
            name = "Neutral Density",
            category = FilterCategory.BASIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.6f, 0f, 0f, 0f, 0f,
                0f, 0.6f, 0f, 0f, 0f,
                0f, 0f, 0.6f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        ),
        Filter(
            id = "infrared",
            name = "Infra-red",
            category = FilterCategory.ARTISTIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.0f, 1.2f, 0.0f, 0f, 0f,
                0.0f, 1.05f, 0.0f, 0f, 0f,
                0.0f, 0.85f, 0.0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(0.6f) }
        ),
        Filter(
            id = "soft",
            name = "Soft",
            category = FilterCategory.PORTRAIT,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.96f, 0f, 0f, 0f, 16f,
                0f, 0.96f, 0f, 0f, 16f,
                0f, 0f, 0.96f, 0f, 16f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(0.85f) }
        ),
        Filter(
            id = "star",
            name = "Star",
            category = FilterCategory.ARTISTIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.02f, 0.02f, 0.03f, 0f, 6f,
                0f, 1.0f, 0f, 0f, 0f,
                0.03f, 0f, 1.05f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.2f) }
        ),
        Filter(
            id = "bokeh",
            name = "Bokeh",
            category = FilterCategory.ARTISTIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                1.06f, 0.02f, 0f, 0f, 10f,
                0.01f, 1.0f, 0f, 0f, 6f,
                0f, 0.01f, 0.94f, 0f, 4f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.05f) }
        ),
        Filter(
            id = "anamorphic",
            name = "Anamorphic",
            category = FilterCategory.ARTISTIC,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.88f, 0.03f, 0.02f, 0f, -4f,
                0.02f, 0.94f, 0.04f, 0f, 2f,
                0.02f, 0.08f, 0.98f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.05f) }
        ),
        Filter(
            id = "astro",
            name = "Astro",
            category = FilterCategory.LANDSCAPE,
            shaderCode = IDENTITY_SHADER,
            previewColorMatrix = ColorMatrix(floatArrayOf(
                0.9f, 0f, 0f, 0f, -12f,
                0f, 0.92f, 0f, 0f, -10f,
                0f, 0f, 1.05f, 0f, 4f,
                0f, 0f, 0f, 1f, 0f
            )).apply { setSaturation(1.1f) }
        )
    )

    private val colorFilterIds = linkedSetOf(
        "original", "sepia", "grayscale", "bw_vivid", "vintage", "neutral",
        "romantic", "muted", "foodie", "refreshing", "cool", "dreamy",
        "late_autumn", "film", "youth", "dark_gold", "cyberpunk"
    )

    private val lensFilterIds = linkedSetOf(
        "clear", "uv", "cir_polarizer", "lin_polarizer", "neutral_density",
        "infrared", "soft", "star", "bokeh", "anamorphic", "astro"
    )

    /** Single-select colour-grading presets (linear colour matrices). */
    val colorFilters: List<Filter> = filters.filter { it.id in colorFilterIds }

    /** Multi-selectable simulated lens filters (Clear, UV, polarizers, ND, etc.). */
    val lensFilters: List<Filter> = filters.filter { it.id in lensFilterIds }

    fun isLensFilter(id: String): Boolean = id in lensFilterIds

    /**
     * Composes a single colour matrix from the single-selected colour filter and
     * all enabled (multi-select) lens filters, applied in order. Returns null
     * when no filter matrix is active. Lens filters are independent of the
     * colour filter, so both can apply together.
     */
    fun concatFilterMatrices(
        colorFilterId: String?,
        enabledLensFilterIds: Set<String>
    ): FloatArray? {
        val pieces = ArrayList<FloatArray>(enabledLensFilterIds.size + 1)
        colorFilterId?.let { id ->
            getFilterById(id)?.previewColorMatrix?.array?.let { pieces.add(it) }
        }
        enabledLensFilterIds.forEach { id ->
            getFilterById(id)?.previewColorMatrix?.array?.let { pieces.add(it) }
        }
        if (pieces.isEmpty()) return null
        var result = ColorMatrix(pieces[0])
        for (i in 1 until pieces.size) {
            result.postConcat(ColorMatrix(pieces[i]))
        }
        return result.array
    }

    fun getFilterById(id: String): Filter? {
        return filters.find { it.id == id }
    }

    fun getFiltersByCategory(category: FilterCategory): List<Filter> {
        return filters.filter { it.category == category }
    }

    private fun brightnessMatrix(value: Float): ColorMatrix {
        val offset = value * 2.55f
        return ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun contrastMatrix(value: Float): ColorMatrix {
        val scale = 1f + value * 0.01f
        val translation = 127.5f * (1f - scale)
        return ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translation,
                0f, scale, 0f, 0f, translation,
                0f, 0f, scale, 0f, translation,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun saturationMatrix(value: Float): ColorMatrix {
        return ColorMatrix().apply {
            setSaturation((1f + value * 0.01f).coerceIn(0f, 2f))
        }
    }

    private fun warmthMatrix(value: Float): ColorMatrix {
        val redOffset = value * 0.255f
        val blueOffset = -value * 0.255f
        return ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, redOffset,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, blueOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun sepiaMatrix(intensity: Float): ColorMatrix {
        val t = intensity.coerceIn(0f, 1f)
        val inv = 1f - t
        return ColorMatrix(
            floatArrayOf(
                inv + 0.393f * t, 0.769f * t, 0.189f * t, 0f, 0f,
                0.349f * t, inv + 0.686f * t, 0.168f * t, 0f, 0f,
                0.272f * t, 0.534f * t, inv + 0.131f * t, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun grayscaleMatrix(intensity: Float): ColorMatrix {
        return ColorMatrix().apply {
            setSaturation((1f - intensity).coerceIn(0f, 1f))
        }
    }
}