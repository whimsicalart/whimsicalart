package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.beauty.domain.BrushStroke
import codes.pepper.whimsicalart.feature.beauty.domain.GeometryField
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyLayerSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Each [BeautyStackEffect] declares the geometry fields it renders with
 * ([BeautyStackEffect.neededFields]); the editor threads that set into the
 * shared context's `geometryFor`, so a segment pays only the ML stages its
 * tools actually consume (docs/effect_stack.md → Shared geometry context —
 * Per-field resolution).
 */
@RunWith(RobolectricTestRunner::class)
class BeautyStackEffectsTest {

    @Test
    fun `every beauty effect that renders geometry requests FACE`() {
        // The manual pen is the only beauty effect that renders WITHOUT geometry
        // (it needs no face detection), so it is exempt from the FACE default.
        allEffects().filter { it !is BeautyPenEffect }.forEach { effect ->
            assertEquals("$effect must need FACE", true, GeometryField.FACE in effect.neededFields)
        }
    }

    @Test
    fun `skin-using effects additionally request SKIN`() {
        val skinEffects = setOf(
            BeautyAutoEffect(intensity = 1f),
            BeautySmoothingEffect(intensity = 1f),
            BeautySpotEffect(intensity = 1f),
            BeautySkinToneEffect(intensity = 1f),
            BeautyFoundationEffect(intensity = 1f)
        ).map { it.layerKey }.toSet()
        allEffects().forEach { effect ->
            assertEquals(
                effect.layerKey,
                effect.layerKey in skinEffects,
                GeometryField.SKIN in effect.neededFields
            )
        }
    }

    @Test
    fun `hair color additionally requests HAIR and the pen requests no geometry at all`() {
        assertEquals(setOf(GeometryField.FACE, GeometryField.HAIR), BeautyHairEffect().neededFields)
        assertEquals(emptySet<GeometryField>(), BeautyPenEffect().neededFields)
    }

    @Test
    fun `a beauty pen stroke renders resolution-independently and tints toward the picked colour`() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(80, 80, 80))
        val stroke = BrushStroke(
            points = listOf(PointF(0.5f, 0.5f)),
            size = 0.5f, // 50% of the width → covers the centre at any resolution
            opacity = 1f
        )
        val out = BeautyPenEffect(strokes = listOf(stroke), opacity = 1f, color = 0xFFFF0000.toInt())
            .render(source, ApplicationProvider.getApplicationContext())

        val before = source.getPixel(32, 32)
        val after = out.getPixel(32, 32)
        assertNotEquals("the stroke must change the brushed pixels", before, after)
        // The picked colour is added (brighten dodge), so the red channel must
        // rise above the flat mid-gray base.
        assertTrue("red must increase toward the picked red: ${Color.red(after)}", Color.red(after) > Color.red(before))

        source.recycle()
        out.recycle()
    }

    @Test
    fun `toBeautyEffects carries pen and makeup colours from the specs`() {
        val colour = 0xFFAABBCC.toInt()
        val effects = listOf(
            BeautyLayerSpec("beauty:lipstick", 0.5f, color = colour),
            BeautyLayerSpec(
                "beauty:pen",
                strokes = listOf(BrushStroke(points = listOf(PointF(0.25f, 0.25f)))),
                opacity = 0.4f,
                color = colour
            )
        ).toBeautyEffects()

        assertEquals(colour, (effects[0] as BeautyLipstickEffect).color)
        val pen = effects[1] as BeautyPenEffect
        assertEquals(colour, pen.color)
        assertEquals(0.4f, pen.opacity)
    }

    @Test
    fun `makeup palette keeps pink as default and adds a black swatch`() {
        val colors = codes.pepper.whimsicalart.feature.beauty.ui.MakeupPalette.colors
        assertEquals(0xFFFF4081.toInt(), colors.first())
        assertEquals(0xFF000000.toInt(), colors.last())
    }

    /** One representative instance of every concrete [BeautyStackEffect]. */
    private fun allEffects(): List<BeautyStackEffect> = listOf(
        BeautyAutoEffect(intensity = 1f),
        BeautySmoothingEffect(intensity = 1f),
        BeautyTeethEffect(intensity = 1f),
        BeautyEyeBrightenEffect(intensity = 1f),
        BeautyDarkCircleEffect(intensity = 1f),
        BeautySpotEffect(intensity = 1f),
        BeautyWrinkleEffect(intensity = 1f),
        BeautySkinToneEffect(intensity = 1f),
        BeautySlimEffect(intensity = 1f),
        BeautyEyeEnlargeEffect(intensity = 1f),
        BeautyNoseEffect(intensity = 1f),
        BeautyJawEffect(intensity = 1f),
        BeautyLipstickEffect(intensity = 1f),
        BeautyBlushEffect(intensity = 1f),
        BeautyEyeShadowEffect(intensity = 1f),
        BeautyEyelinerEffect(intensity = 1f),
        BeautyFoundationEffect(intensity = 1f),
        BeautyHairEffect(intensity = 1f),
        BeautyPenEffect()
    )
}