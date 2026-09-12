package codes.pepper.whimsicalart.feature.editor.ui

import codes.pepper.whimsicalart.feature.beauty.ui.BeautyTool
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P2: the beauty pen (EditTool.BEAUTY + BeautyTool.BRIGHTNESS_PEN) is a Category B
 * brush, exactly like the standalone pen — its classification keys off the
 * SUB-TOOL, so every OTHER beauty sub-tool stays Category A (pan/zoom always,
 * hold-to-compare always, no eye-dependent behaviour change).
 *
 * - with BEAUTY + PEN selected the tool is non-pan/zoom-capable: the eye button
 *   is shown, pan/zoom/hold-to-compare are OFF while the eye is off (the single
 *   drag paints), and the eye ON suspends painting and re-enables pan/zoom +
 *   hold-to-compare;
 * - the other beauty sub-tools must verify NONE of that changed: canPanZoom true,
 *   compare always, composite preview (NOT the raw source overlay).
 */
class BeautyPenClassificationTest {

    @Test
    fun `beauty pen is a beauty-pen sub-tool and nothing else is`() {
        assertEquals(true, isBeautyPenTool(EditTool.BEAUTY, BeautyTool.BRIGHTNESS_PEN))
        assertEquals(false, isBeautyPenTool(EditTool.BEAUTY, BeautyTool.LIPSTICK))
        assertEquals(false, isBeautyPenTool(EditTool.BEAUTY, null))
        assertEquals(false, isBeautyPenTool(EditTool.PEN, null))
    }

    @Test
    fun `beauty pen is non-pan-zoom-capable (Category B) while other beauty sub-tools are not`() {
        assertEquals(true, isNonPanZoomCapable(EditTool.BEAUTY, BeautyTool.BRIGHTNESS_PEN))
        assertEquals(false, isNonPanZoomCapable(EditTool.BEAUTY, BeautyTool.LIPSTICK))
        assertEquals(false, isNonPanZoomCapable(EditTool.BEAUTY, BeautyTool.AUTO_BEAUTY))
        assertEquals(false, isNonPanZoomCapable(EditTool.BEAUTY, null))
        assertEquals(true, isNonPanZoomCapable(EditTool.PEN, null))
    }

    @Test
    fun `transform is a non-pan-zoom-capable tool like crop (Category B)`() {
        // next.md "TRANSFORM ... promote to Category B": the classify must key
        // TRANSFORM exactly like CROP — eye button on entry, pan/zoom +
        // hold-to-compare only while the eye is ON.
        assertEquals(true, isNonPanZoomCapable(EditTool.TRANSFORM, null))
        assertEquals(true, isNonPanZoomCapable(EditTool.CROP, null))
        assertEquals(false, isCompareTool(EditTool.TRANSFORM, null, eyeEnabled = false))
        assertEquals(true, isCompareTool(EditTool.TRANSFORM, null, eyeEnabled = true))
    }

    @Test
    fun `with the beauty pen eye off the single drag paints instead of comparing (eye on re-enables it)`() {
        // Category B brush: hold-to-compare is unavailable while painting.
        assertEquals(false, isCompareTool(EditTool.BEAUTY, BeautyTool.BRIGHTNESS_PEN, eyeEnabled = false))
        assertEquals(true, isCompareTool(EditTool.BEAUTY, BeautyTool.BRIGHTNESS_PEN, eyeEnabled = true))
    }

    @Test
    fun `other beauty sub-tools keep hold-to-compare always available`() {
        assertEquals(true, isCompareTool(EditTool.BEAUTY, BeautyTool.LIPSTICK, eyeEnabled = false))
        assertEquals(true, isCompareTool(EditTool.BEAUTY, BeautyTool.BLUSH, eyeEnabled = false))
        assertEquals(true, isCompareTool(EditTool.BEAUTY, null, eyeEnabled = false))
    }

    @Test
    fun `beauty pen overlays in the raw source space while other beauty sub-tools fold the composite`() {
        assertEquals(true, isSourceSpaceTool(EditTool.BEAUTY, BeautyTool.BRIGHTNESS_PEN))
        assertEquals(false, isSourceSpaceTool(EditTool.BEAUTY, BeautyTool.LIPSTICK))
        assertEquals(false, isSourceSpaceTool(EditTool.BEAUTY, null))
    }

    @Test
    fun `source-space fold flags stop the tail at the current tool with the eye off`() {
        // docs/effect_stack.md → *Eye toggle and the current tool*: eye ON folds
        // the whole stack; eye OFF + live-apply keeps the current tool's own
        // layer in the fold but drops everything ABOVE it (includeTail=false).
        // Eye OFF without live-apply excludes the tool layer too (the overlay
        // draws the interactive stroke), still without the tail.
        assertEquals(true to false, liveFoldFlags(eyeEnabled = true, liveApply = false))
        assertEquals(true to false, liveFoldFlags(eyeEnabled = true, liveApply = true))
        assertEquals(false to false, liveFoldFlags(eyeEnabled = false, liveApply = true))
        assertEquals(false to true, liveFoldFlags(eyeEnabled = false, liveApply = false))
    }
}