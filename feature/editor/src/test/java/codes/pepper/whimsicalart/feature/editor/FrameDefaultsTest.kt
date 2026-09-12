package codes.pepper.whimsicalart.feature.editor

import android.graphics.Bitmap
import android.graphics.Color
import codes.pepper.whimsicalart.feature.editor.domain.BitmapRenderer
import codes.pepper.whimsicalart.feature.editor.domain.FrameGeometry
import codes.pepper.whimsicalart.feature.editor.domain.FrameLayer
import codes.pepper.whimsicalart.feature.editor.domain.FrameStyle
import codes.pepper.whimsicalart.feature.editor.domain.FrameTier
import codes.pepper.whimsicalart.feature.editor.ui.frames.CUSTOM_CURVE_DISTANCE_RANGE
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorUiState
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.StackDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec → Frames, "Edge size" + "Custom (Curved) sliders". */
@RunWith(RobolectricTestRunner::class)
class FrameDefaultsTest {

    @Test
    fun `a fresh editor state defaults frame colours to black`() {
        assertEquals("EditorUiState innerFrameColor", Color.BLACK, EditorUiState().innerFrameColor)
        assertEquals("EditorUiState outerFrameColor", Color.BLACK, EditorUiState().outerFrameColor)
        assertEquals("StackDocument innerFrameColor", Color.BLACK,
            StackDocument(effectStack = emptyList()).innerFrameColor)
        assertEquals("StackDocument outerFrameColor", Color.BLACK,
            StackDocument(effectStack = emptyList()).outerFrameColor)
    }

    @Test
    fun `a fresh editor state defaults the line distance to the line thickness`() {
        // Spec: Distance defaults EQUAL to the default Line Thickness (2%).
        assertEquals("EditorUiState innerFrameLineDistance", 0.02f,
            EditorUiState().innerFrameLineDistance, 1e-6f)
        assertEquals("EditorUiState outerFrameLineDistance", 0.02f,
            EditorUiState().outerFrameLineDistance, 1e-6f)
        assertEquals("StackDocument innerFrameLineDistance", 0.02f,
            StackDocument(effectStack = emptyList()).innerFrameLineDistance, 1e-6f)
        assertEquals("StackDocument outerFrameLineDistance", 0.02f,
            StackDocument(effectStack = emptyList()).outerFrameLineDistance, 1e-6f)
        assertEquals("distance equals the default thickness",
            EditorUiState().innerFrameLineDistance, EditorUiState().innerFrameLineThickness, 1e-6f)
    }

    @Test
    fun `a fresh editor state defaults the edge-size slider to 20 percent`() {
        assertEquals("EditorUiState innerFrameEdgeSize", 0.2f, EditorUiState().innerFrameEdgeSize, 1e-6f)
        assertEquals("EditorUiState outerFrameEdgeSize", 0.2f, EditorUiState().outerFrameEdgeSize, 1e-6f)
        assertEquals("StackDocument innerFrameEdgeSize", 0.2f,
            StackDocument(effectStack = emptyList()).innerFrameEdgeSize, 1e-6f)
        assertEquals("StackDocument outerFrameEdgeSize", 0.2f,
            StackDocument(effectStack = emptyList()).outerFrameEdgeSize, 1e-6f)
    }

    @Test
    fun `a fresh editor state defaults the custom curve distance to minus 50 percent`() {
        assertEquals("EditorUiState innerFrameCustomCurveDistance",
            -0.5f, EditorUiState().innerFrameCustomCurveDistance, 1e-6f)
        assertEquals("EditorUiState outerFrameCustomCurveDistance",
            -0.5f, EditorUiState().outerFrameCustomCurveDistance, 1e-6f)
        assertEquals("StackDocument innerFrameCustomCurveDistance",
            -0.5f, StackDocument(effectStack = emptyList()).innerFrameCustomCurveDistance, 1e-6f)
        assertEquals("StackDocument outerFrameCustomCurveDistance",
            -0.5f, StackDocument(effectStack = emptyList()).outerFrameCustomCurveDistance, 1e-6f)
        assertEquals("FrameGeometry frameCustomCurveDistance",
            -0.5f, FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f).customCurveDistance, 1e-6f)
        assertEquals("FrameLayer inner-tier customCurveDistance",
            -0.5f, FrameLayer(inner = FrameTier(style = FrameStyle.CUSTOM, thickness = 0.2f)).inner.customCurveDistance, 1e-6f)
        assertEquals("FrameLayer outer-tier customCurveDistance",
            -0.5f, FrameLayer(outer = FrameTier(style = FrameStyle.CUSTOM, thickness = 0.2f)).outer.customCurveDistance, 1e-6f)
    }

    @Test
    fun `the curve-distance slider spans minus 100 through plus 100 percent`() {
        assertEquals("slider lower bound", -1f, CUSTOM_CURVE_DISTANCE_RANGE.start, 1e-6f)
        assertEquals("slider upper bound", 1f, CUSTOM_CURVE_DISTANCE_RANGE.endInclusive, 1e-6f)
    }

    @Test
    fun `a default-created custom frame geometry scoops inward at 50 percent`() {
        // Default customCurveDistance −0.5: the half-curve PEAK (column 10,
        // u·count = 0.5) cuts the interior inset to t·(1 − 0.5·1) = 4 of the
        // straight 8 — an inward scoop; 0 distance keeps the straight band.
        val geometry = FrameGeometry(style = FrameStyle.CUSTOM, width = 41, height = 41, thickness = 8f)
        assertEquals("default −50% scoops the interior BELOW the straight band",
            4f, geometry.topInset(10), 1e-3f)
        assertEquals("a straight distance would keep the full band", 8f,
            FrameGeometry(FrameStyle.CUSTOM, 41, 41, 8f, customCurveDistance = 0f).topInset(10), 1e-3f)
    }

    @Test
    fun `a FrameGeometry created without an edgeSize reaches a 0-2 band of the short-edge half`() {
        // BEVELED chamfers the corners for the first `reach` px of each edge and is
        // straight (band depth = thickness) beyond. Default edgeSize 0.2f on a
        // 400x300 canvas → reach = 0.2 * 150 = 30 px.
        val thickness = 60f
        val geometry = FrameGeometry(
            style = FrameStyle.BEVELED,
            width = 400,
            height = 300,
            thickness = thickness
        )
        assertEquals("mid-edge inset is the straight band", thickness, geometry.topInset(200), 1e-6f)
        assertEquals("at the reach boundary the straight band resumes", thickness, geometry.topInset(30), 1e-6f)
        assertEquals("past the reach nothing changes", thickness, geometry.topInset(31), 1e-6f)
        assertTrue("inside the reach the corner chamfer is deeper", geometry.topInset(29) > thickness)
    }

    @Test
    fun `a FrameLayer created without an edgeSize renders a sane band with interior`() {
        // thickness 0.25 → half of a quarter band: t = 0.25 × 300 / 2 = 37.5 px
        // (spec: the slider value is HALF the short-edge fraction it used to be,
        // so 50% is a quarter band and 100% is half the short edge).
        // The exact 0.2-reach corner shape is pinned by the FrameGeometry test;
        // here the DEFAULT edgeSize must render a complete frame, not a broken/
        // oversized one — corners, full top edge and the interior all correct.
        val input = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.WHITE)
        val out = BitmapRenderer.render(
            context = RuntimeEnvironment.getApplication(),
            input = input,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            cropRect = null,
            colorMatrix = null,
            frames = listOf(
                FrameLayer(inner = FrameTier(style = FrameStyle.BEVELED, thickness = 0.25f, color = Color.BLACK))
            )
        )
        assertEquals("corner band", Color.BLACK, out.getPixel(0, 0))
        assertEquals("top mid-edge band", Color.BLACK, out.getPixel(200, 0))
        assertEquals("band covers every row above the interior start",
            Color.BLACK, out.getPixel(200, 37))
        assertEquals("interior starts exactly at the band depth",
            Color.WHITE, out.getPixel(200, 38))
        assertEquals("the centre keeps the photo", Color.WHITE, out.getPixel(200, 200))
    }
}