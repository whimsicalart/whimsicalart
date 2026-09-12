package codes.pepper.whimsicalart.core.designsystem

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The full-picker dialog's working state ([ColourPickerController]) and the
 * compact-row presets ([CompactPresetColours]) behind
 * [ColourPicker]/[ColourPresetRow]/[ColourPickerDialog]
 * (docs/effects_reference.md → *Shared business rules* → *Colour picker*).
 */
@RunWith(RobolectricTestRunner::class)
class ColourPickerTest {

    @Test
    fun `controller opens on the initial colour`() {
        val original = 0xFF3D84FF.toInt()
        val controller = ColourPickerController(original)
        assertEquals(Color.red(original).toFloat(), Color.red(controller.toArgb()).toFloat(), 2f)
        assertEquals(Color.green(original).toFloat(), Color.green(controller.toArgb()).toFloat(), 2f)
        assertEquals(Color.blue(original).toFloat(), Color.blue(controller.toArgb()).toFloat(), 2f)
    }

    @Test
    fun `controller carries the initial colour's alpha`() {
        val controller = ColourPickerController(0x803D84FF.toInt())
        assertEquals(128f, Color.alpha(controller.toArgb()).toFloat(), 2f)
    }

    @Test
    fun `a preset pick adopts its hue while keeping the alpha`() {
        val controller = ColourPickerController(0x803D84FF.toInt())
        controller.selectPreset(0xFFE53935.toInt())
        val picked = controller.toArgb()
        assertEquals(Color.red(0xFFE53935.toInt()), Color.red(picked))
        assertEquals(Color.green(0xFFE53935.toInt()), Color.green(picked))
        assertEquals(Color.blue(0xFFE53935.toInt()), Color.blue(picked))
        assertEquals(128f, Color.alpha(picked).toFloat(), 2f)
    }

    @Test
    fun `a hue-saturation drag updates the working colour live`() {
        val controller = ColourPickerController(0xFF000000.toInt())
        controller.hue = 240f
        controller.saturation = 1f
        controller.value = 1f
        val picked = controller.toArgb()
        assertTrue("blue should dominate, got ${Color.blue(picked)}", Color.blue(picked) > 250)
        assertTrue("red should be near 0, got ${Color.red(picked)}", Color.red(picked) < 5)
    }

    @Test
    fun `the brightness slider scales the picked colour`() {
        val controller = ColourPickerController(0xFFE53935.toInt())
        controller.value = 0.5f
        assertTrue(Color.red(controller.toArgb()) < Color.red(0xFFE53935.toInt()))
    }

    @Test
    fun `the alpha slider rounds into the packed argb`() {
        val controller = ColourPickerController(0xFFE53935.toInt())
        controller.alpha = 0.5f
        assertEquals(127f, Color.alpha(controller.toArgb()).toFloat(), 2f)
        assertEquals(Color.red(0xFFE53935.toInt()), Color.red(controller.toArgb()))
    }
}