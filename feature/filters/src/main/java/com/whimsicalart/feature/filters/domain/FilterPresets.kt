package com.whimsicalart.feature.filters.domain

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
    val filters = listOf(
        Filter(
            id = "original",
            name = "Original",
            category = FilterCategory.BASIC,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                void main() {
                    fragColor = texture(uTexture, vTexCoord);
                }
            """.trimIndent()
        ),

        Filter(
            id = "brightness",
            name = "Brightness",
            category = FilterCategory.BASIC,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec4 uFilterParam;
                void main() {
                    vec4 color = texture(uTexture, vTexCoord);
                    color.rgb += uFilterParam.x * 0.01;
                    fragColor = color;
                }
            """.trimIndent(),
            defaultParams = floatArrayOf(0f, 0f, 0f, 1f),
            previewColorMatrix = brightnessMatrix(0f)
        ),

        Filter(
            id = "contrast",
            name = "Contrast",
            category = FilterCategory.BASIC,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec4 uFilterParam;
                void main() {
                    vec4 color = texture(uTexture, vTexCoord);
                    float contrast = uFilterParam.x * 0.01;
                    color.rgb = (color.rgb - 0.5) * (1.0 + contrast) + 0.5;
                    fragColor = color;
                }
            """.trimIndent(),
            defaultParams = floatArrayOf(0f, 0f, 0f, 1f),
            previewColorMatrix = contrastMatrix(0f)
        ),

        Filter(
            id = "saturation",
            name = "Saturation",
            category = FilterCategory.BASIC,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec4 uFilterParam;
                void main() {
                    vec4 color = texture(uTexture, vTexCoord);
                    float saturation = uFilterParam.x * 0.01;
                    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    color.rgb = mix(vec3(gray), color.rgb, 1.0 + saturation);
                    fragColor = color;
                }
            """.trimIndent(),
            defaultParams = floatArrayOf(0f, 0f, 0f, 1f),
            previewColorMatrix = saturationMatrix(0f)
        ),

        Filter(
            id = "warmth",
            name = "Warmth",
            category = FilterCategory.ARTISTIC,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec4 uFilterParam;
                void main() {
                    vec4 color = texture(uTexture, vTexCoord);
                    float warmth = uFilterParam.x * 0.001;
                    color.r += warmth;
                    color.b -= warmth;
                    fragColor = color;
                }
            """.trimIndent(),
            defaultParams = floatArrayOf(0f, 0f, 0f, 1f),
            previewColorMatrix = warmthMatrix(0f)
        ),

        Filter(
            id = "vignette",
            name = "Vignette",
            category = FilterCategory.ARTISTIC,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec4 uFilterParam;
                void main() {
                    vec4 color = texture(uTexture, vTexCoord);
                    vec2 center = vec2(0.5, 0.5);
                    float dist = distance(vTexCoord, center);
                    float vignette = smoothstep(0.8, 0.2, dist * uFilterParam.x);
                    color.rgb *= vignette;
                    fragColor = color;
                }
            """.trimIndent(),
            defaultParams = floatArrayOf(1f, 0f, 0f, 1f)
        ),

        Filter(
            id = "sepia",
            name = "Sepia",
            category = FilterCategory.VINTAGE,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec4 uFilterParam;
                void main() {
                    vec4 color = texture(uTexture, vTexCoord);
                    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    vec3 sepia = vec3(
                        gray * 1.2,
                        gray * 1.0,
                        gray * 0.8
                    );
                    color.rgb = mix(color.rgb, sepia, uFilterParam.x);
                    fragColor = color;
                }
            """.trimIndent(),
            defaultParams = floatArrayOf(1f, 0f, 0f, 1f),
            previewColorMatrix = sepiaMatrix(1f)
        ),

        Filter(
            id = "grayscale",
            name = "Grayscale",
            category = FilterCategory.MONOCHROME,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec4 uFilterParam;
                void main() {
                    vec4 color = texture(uTexture, vTexCoord);
                    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    color.rgb = mix(color.rgb, vec3(gray), uFilterParam.x);
                    fragColor = color;
                }
            """.trimIndent(),
            defaultParams = floatArrayOf(1f, 0f, 0f, 1f),
            previewColorMatrix = grayscaleMatrix(1f)
        ),

        Filter(
            id = "sharpen",
            name = "Sharpen",
            category = FilterCategory.BASIC,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec4 uFilterParam;
                void main() {
                    vec2 texelSize = vec2(1.0 / 1000.0, 1.0 / 800.0);
                    vec4 center = texture(uTexture, vTexCoord);
                    vec4 top = texture(uTexture, vTexCoord + vec2(0.0, texelSize.y));
                    vec4 bottom = texture(uTexture, vTexCoord - vec2(0.0, texelSize.y));
                    vec4 left = texture(uTexture, vTexCoord - vec2(texelSize.x, 0.0));
                    vec4 right = texture(uTexture, vTexCoord + vec2(texelSize.x, 0.0));
                    
                    vec4 sharpen = center * 5.0 - top - bottom - left - right;
                    float intensity = uFilterParam.x * 0.01;
                    fragColor = mix(center, sharpen, intensity);
                }
            """.trimIndent(),
            defaultParams = floatArrayOf(50f, 0f, 0f, 1f)
        ),

        Filter(
            id = "blur",
            name = "Blur",
            category = FilterCategory.BASIC,
            shaderCode = """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec4 uFilterParam;
                void main() {
                    vec2 texelSize = vec2(1.0 / 1000.0, 1.0 / 800.0);
                    vec4 color = vec4(0.0);
                    float blurSize = uFilterParam.x * 0.1;
                    
                    for (int x = -2; x <= 2; x++) {
                        for (int y = -2; y <= 2; y++) {
                            vec2 offset = vec2(float(x), float(y)) * texelSize * blurSize;
                            color += texture(uTexture, vTexCoord + offset);
                        }
                    }
                    
                    fragColor = color / 25.0;
                }
            """.trimIndent(),
            defaultParams = floatArrayOf(50f, 0f, 0f, 1f)
        )
    )

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
