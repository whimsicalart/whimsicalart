package codes.pepper.whimsicalart.core.designsystem

import android.graphics.Color
import kotlin.math.roundToInt

/**
 * HSV↔ARGB conversions for the shared colour picker. Kept as pure functions so
 * Robolectric-safe unit tests can cover the math without a composable host.
 * Hues are 0–360, saturation/value 0–1, alpha 0–1 (defaults opaque).
 */
object ColourMath {

    /** Hue (0–360), saturation (0–1), value (0–1) → packed ARGB with [alpha] (0–1). */
    fun hsvToArgb(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Int {
        val h = hue % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val argb = Color.HSVToColor(floatArrayOf(h, s, v))
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (argb and 0x00FFFFFF)
    }

    /** Packed ARGB → hue (0–360), saturation (0–1), value (0–1). */
    fun argbToHsv(argb: Int): Triple<Float, Float, Float> {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        return Triple(hsv[0], hsv[1], hsv[2])
    }

    /** Replaces the alpha channel of [argb] with [alpha] (0–1), keeping RGB. */
    fun withAlpha(argb: Int, alpha: Float): Int {
        val (h, s, v) = argbToHsv(argb)
        return hsvToArgb(h, s, v, alpha)
    }
}

/**
 * The compact row's preset colours — the most common colours shown inline so
 * the control fits inside a controls panel (docs/effects_reference.md → *Shared
 * business rules* → *Colour picker*): black, white plus a handful of saturated
 * basics. The row's last cell is the rainbow disc that opens the full picker
 * popup, so every other colour stays reachable.
 */
val CompactPresetColours: List<Int> = listOf(
    0xFF000000.toInt(),   // black
    0xFFFFFFFF.toInt(),   // white
    0xFF616161.toInt(),   // grey
    0xFFE53935.toInt(),   // red
    0xFFFB8C00.toInt(),   // orange
    0xFFFDD835.toInt(),   // yellow
    0xFF43A047.toInt(),   // green
    0xFF1E88E5.toInt(),   // blue
    0xFF8E24AA.toInt()    // purple
)

/**
 * The wide preset colours offered by the full picker popup: a full rainbow plus
 * neutral black / white / greys. Used wherever a colour is picked (text font /
 * background / shadow / outline, frame, sticker tint, pen).
 */
val ColourPickerPresets: List<Int> = listOf(
    0xFF000000.toInt(),   // black
    0xFFFFFFFF.toInt(),   // white
    0xFF9E9E9E.toInt(),   // grey
    0xFF757575.toInt(),   // dark grey
    0xFF616161.toInt(),   // grey
    0xFFE53935.toInt(),   // red
    0xFFF4511E.toInt(),   // deep orange
    0xFFFB8C00.toInt(),   // orange
    0xFFFDD835.toInt(),   // yellow
    0xFF7CB342.toInt(),   // light green
    0xFF43A047.toInt(),   // green
    0xFF00897B.toInt(),   // teal
    0xFF039BE5.toInt(),   // light blue
    0xFF1E88E5.toInt(),   // blue
    0xFF3949AB.toInt(),   // indigo
    0xFF8E24AA.toInt(),   // purple
    0xFFD81B60.toInt(),   // pink
    0xFFF06292.toInt(),   // light pink
    0xFF6D4C41.toInt()    // brown
)