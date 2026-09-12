package codes.pepper.whimsicalart.core.designsystem

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure colour-conversion helpers for the shared colour picker (docs/effects_reference.md
 * → *Shared business rules* → *Colour picker*). Well-defined, Robolectric-safe math.
 */
@RunWith(RobolectricTestRunner::class)
class ColourMathTest {

    @Test
    fun `hue 0 gives pure red`() {
        val argb = ColourMath.hsvToArgb(0f, 1f, 1f, 1f)
        assertEquals(255, Color.red(argb))
        assertTrue("green should be 0, got ${Color.green(argb)}", Color.green(argb) < 5)
        assertTrue("blue should be 0, got ${Color.blue(argb)}", Color.blue(argb) < 5)
        assertEquals(255, Color.alpha(argb))
    }

    @Test
    fun `hue 120 gives pure green`() {
        val argb = ColourMath.hsvToArgb(120f, 1f, 1f, 1f)
        assertTrue("green should dominate, got ${Color.green(argb)}", Color.green(argb) > 250)
        assertTrue("blue should be 0, got ${Color.blue(argb)}", Color.blue(argb) < 5)
    }

    @Test
    fun `hue 240 gives pure blue`() {
        val argb = ColourMath.hsvToArgb(240f, 1f, 1f, 1f)
        assertTrue("blue should dominate, got ${Color.blue(argb)}", Color.blue(argb) > 250)
    }

    @Test
    fun `zero saturation gives grey across hues`() {
        listOf(0f, 90f, 200f, 340f).forEach { hue ->
            val argb = ColourMath.hsvToArgb(hue, 0f, 0.5f, 1f)
            val r = Color.red(argb)
            val g = Color.green(argb)
            val b = Color.blue(argb)
            // All channels equal → neutral grey.
            assertTrue("grey at hue=$hue: r=$r g=$g b=$b", kotlin.math.abs(r - g) <= 2 && kotlin.math.abs(r - b) <= 2)
        }
    }

    @Test
    fun `value scales brightness`() {
        val dim = Color.red(ColourMath.hsvToArgb(0f, 1f, 0.3f, 1f))
        val bright = Color.red(ColourMath.hsvToArgb(0f, 1f, 1f, 1f))
        assertEquals(3f, bright.toFloat() / dim.toFloat(), 0.5f)
    }

    @Test
    fun `alpha is preserved`() {
        val argb = ColourMath.hsvToArgb(200f, 0.5f, 0.8f, 0.4f)
        assertEquals(102f, Color.alpha(argb).toFloat(), 2f) // 0.4 × 255 = 102
    }

    @Test
    fun `roundtrip hsv preserves channels`() {
        val original = 0xFF3D84FF.toInt()
        val (h, s, v) = ColourMath.argbToHsv(original)
        val restored = ColourMath.hsvToArgb(h, s, v, 1f)
        assertEquals(Color.red(original).toFloat(), Color.red(restored).toFloat(), 2f)
        assertEquals(Color.green(original).toFloat(), Color.green(restored).toFloat(), 2f)
        assertEquals(Color.blue(original).toFloat(), Color.blue(restored).toFloat(), 2f)
    }

    @Test
    fun `withAlpha keeps rgb and replaces alpha`() {
        val original = 0xFF3D84FF.toInt()
        val result = ColourMath.withAlpha(original, 0.5f)
        assertEquals(Color.red(original), Color.red(result))
        assertEquals(Color.green(original), Color.green(result))
        assertEquals(Color.blue(original), Color.blue(result))
        assertEquals(127f, Color.alpha(result).toFloat(), 2f)
    }

    @Test
    fun `presets span the full rainbow plus neutral greys`() {
        assertEquals(19, ColourPickerPresets.size)
        assertTrue(ColourPickerPresets.contains(0xFF000000.toInt()))
        assertTrue(ColourPickerPresets.contains(0xFFFFFFFF.toInt()))
        // A red, a green and a blue are all present.
        val reds = ColourPickerPresets.count { Color.red(it) > 200 && Color.green(it) < 100 }
        assertTrue("expected a red preset", reds >= 1)
        val blues = ColourPickerPresets.count { Color.blue(it) > 200 && Color.red(it) < 100 }
        assertTrue("expected a blue preset", blues >= 1)
    }

    @Test
    fun `compact presets are the most common opaque colours`() {
        assertTrue(CompactPresetColours.contains(0xFF000000.toInt()))
        assertTrue(CompactPresetColours.contains(0xFFFFFFFF.toInt()))
        assertTrue(
            "compact basics must be fully opaque",
            CompactPresetColours.all { Color.alpha(it) == 255 }
        )
        val reds = CompactPresetColours.count { Color.red(it) > 200 && Color.green(it) < 100 }
        val greens = CompactPresetColours.count { Color.green(it) > 120 && Color.red(it) < 150 }
        val blues = CompactPresetColours.count { Color.blue(it) > 200 && Color.red(it) < 100 }
        assertTrue("expected a red basic, got $reds", reds >= 1)
        assertTrue("expected a green basic, got $greens", greens >= 1)
        assertTrue("expected a blue basic, got $blues", blues >= 1)
        assertTrue(
            "compact basics must be a subset of the wide presets",
            CompactPresetColours.all { it in ColourPickerPresets }
        )
    }
}