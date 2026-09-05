package codes.pepper.whimsicalart.feature.beauty.ui

import codes.pepper.whimsicalart.feature.beauty.domain.BrushStroke

/**
 * The full set of per-sub-tool beauty parameters. The EDITOR is the single
 * owner of this state (`EditorUiState.beauty`) and derives the beauty sub-set of
 * its effect stack from it; the beauty panel UI is a stateless control surface
 * that renders these values and reports changes back. This shared value type is
 * what keeps `feature:beauty` (the panel UI) decoupled from the editor while both
 * speak the same parameter model.
 */
data class BeautyParams(
    val auto: Float = 0f,
    val smoothing: Float = 0f,
    val teeth: Float = 0f,
    val eyeBrighten: Float = 0f,
    val skinTone: Float = 0f,
    val darkCircle: Float = 0f,
    val spots: Float = 0f,
    val wrinkles: Float = 0f,
    val brushSize: Float = 30f,
    val brushOpacity: Float = 0.2f,
    val brushStrokes: List<BrushStroke> = emptyList(),
    val activeStroke: BrushStroke? = null,
    val faceSlim: Float = 0f,
    val eyeEnlarge: Float = 0f,
    val nose: Float = 0f,
    val jaw: Float = 0f,
    val makeupColor: Int = MakeupPalette.colors.first(),
    val lipstick: Float = 0f,
    val blush: Float = 0f,
    val eyeShadow: Float = 0f,
    val eyeliner: Float = 0f,
    val foundation: Float = 0f,
    val hair: Float = 0f
)

/** The active beauty sub-tool; null when no beauty sub-tool is selected. */
enum class BeautyTool {
    AUTO_BEAUTY,
    SKIN_SMOOTHING,
    TEETH_WHITENING,
    EYE_BRIGHTENING,
    BRIGHTNESS_PEN,
    DARK_CIRCLE_REMOVAL,
    SPOT_REMOVAL,
    WRINKLE_REMOVAL,
    SKIN_TONE,
    FACE_SLIM,
    EYE_ENLARGE,
    NOSE_ADJUST,
    JAW_ADJUST,
    LIPSTICK,
    BLUSH,
    EYE_SHADOW,
    EYELINER,
    FOUNDATION,
    HAIR_COLOR
}

/** The finite set of makeup colors offered by the color picker. */
object MakeupPalette {
    val colors = listOf(
        0xFFFF4081.toInt(),
        0xFFE91E63.toInt(),
        0xFFF44336.toInt(),
        0xFFFF9800.toInt(),
        0xFFAD1457.toInt(),
        0xFF8D6E63.toInt(),
        0xFF66BB6A.toInt(),
        0xFFFFD600.toInt()
    )
}
