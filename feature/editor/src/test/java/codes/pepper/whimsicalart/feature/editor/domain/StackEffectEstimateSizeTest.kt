package codes.pepper.whimsicalart.feature.editor.domain

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import codes.pepper.whimsicalart.feature.editor.domain.enhance.EnhanceSettings
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StackEffectEstimateSizeTest {

    // --- CropEffect ---------------------------------------------------------

    @Test
    fun `crop estimate is identity for null rect`() {
        assertEquals(100 to 50, CropEffect(rect = null).estimateOutputSize(100, 50))
    }

    @Test
    fun `crop estimate full frame keeps dimensions`() {
        assertEquals(
            100 to 50,
            CropEffect(rect = Rect(0f, 0f, 1f, 1f)).estimateOutputSize(100, 50)
        )
    }

    @Test
    fun `crop estimate centered half halves dimensions`() {
        assertEquals(
            50 to 25,
            CropEffect(rect = Rect(0.25f, 0.25f, 0.75f, 0.75f)).estimateOutputSize(100, 50)
        )
    }

    @Test
    fun `crop estimate with offset clips at integer boundaries`() {
        assertEquals(
            25 to 100,
            CropEffect(rect = Rect(0.25f, 0f, 0.75f, 1f)).estimateOutputSize(50, 100)
        )
    }

    @Test
    fun `crop estimate clamps to at least one pixel`() {
        assertEquals(
            1 to 1,
            CropEffect(rect = Rect(0f, 0f, 0.001f, 0.001f)).estimateOutputSize(100, 50)
        )
    }

    // --- TransformEffect ----------------------------------------------------

    @Test
    fun `transform estimate identity at zero degrees`() {
        assertEquals(100 to 50, TransformEffect(rotation = 0f).estimateOutputSize(100, 50))
    }

    @Test
    fun `transform estimate swaps dimensions at 90 and 270 degrees`() {
        assertEquals(50 to 100, TransformEffect(rotation = 90f).estimateOutputSize(100, 50))
        assertEquals(50 to 100, TransformEffect(rotation = 270f).estimateOutputSize(100, 50))
    }

    @Test
    fun `transform estimate keeps dimensions at 180 degrees`() {
        assertEquals(100 to 50, TransformEffect(rotation = 180f).estimateOutputSize(100, 50))
    }

    @Test
    fun `transform estimate ignores flips for dimensions`() {
        val flipH = TransformEffect(rotation = 0f, flipHorizontal = true)
        val flipV = TransformEffect(rotation = 0f, flipVertical = true)
        assertEquals(100 to 50, flipH.estimateOutputSize(100, 50))
        assertEquals(100 to 50, flipV.estimateOutputSize(100, 50))
        assertEquals(
            50 to 100,
            TransformEffect(rotation = 90f, flipHorizontal = true).estimateOutputSize(100, 50)
        )
    }

    @Test
    fun `transform estimate keeps dimensions for near zero angle`() {
        // Mirrors BitmapRenderer.transforms' exact-90 fast path.
        assertEquals(100 to 80, TransformEffect(rotation = 0.1f).estimateOutputSize(100, 80))
    }

    @Test
    fun `transform estimate grows to the rotated bounding box for free angle`() {
        // Bounding box of a 20×10 rect rotated 45°: (W+H)/√2 on both axes → 21×21.
        assertEquals(21 to 21, TransformEffect(rotation = 45f).estimateOutputSize(20, 10))
        // 30×10 at 30°: width = 30·cos30 + 10·sin30 ≈ 30.98 → 31,
        // height = 30·sin30 + 10·cos30 ≈ 23.66 → 24.
        assertEquals(31 to 24, TransformEffect(rotation = 30f).estimateOutputSize(30, 10))
    }

    // --- Draw-in-place effects stay the same size ---------------------------

    @Test
    fun `draw in place effects estimate identity dimensions`() {
        val effects = listOf(
            SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f),
            FilterEffect(filterMatrix = null),
            EnhanceEffect(enabled = true, settings = EnhanceSettings()),
            StyleEffect(filter = null),
            BackgroundEffect(mode = BackgroundMode.BLUR),
            BitmapDiffEffect(tool = EditTool.PEN),
            StickerEffect(layers = emptyList()),
            TextEffect(layers = emptyList()),
            FrameEffect(layer = null),
            SkinDenoiseEffect(intensity = 0.5f)
        )
        for (effect in effects) {
            assertEquals(
                "100x50 for $effect",
                100 to 50,
                effect.estimateOutputSize(100, 50)
            )
        }
    }

    @Test
    fun `background replace estimate stays put within fit size`() {
        val bg = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        assertEquals(
            100 to 50,
            BackgroundEffect(
                mode = BackgroundMode.REPLACE,
                backgroundImage = bg,
                placementScale = 1f,
                placementRotationDegrees = 0f
            ).estimateOutputSize(100, 50)
        )
        assertEquals(
            100 to 50,
            BackgroundEffect(
                mode = BackgroundMode.REPLACE,
                backgroundImage = bg,
                placementScale = 1.5f,
                freeScaleUp = false
            ).estimateOutputSize(100, 50)
        )
    }

    @Test
    fun `background replace estimate grows with a scaled up placement`() {
        val bg = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        assertEquals(
            200 to 100,
            BackgroundEffect(
                mode = BackgroundMode.REPLACE,
                backgroundImage = bg,
                placementScale = 2f,
                freeScaleUp = true
            ).estimateOutputSize(100, 50)
        )
    }

    @Test
    fun `background replace estimate grows with rotation and stays for no image`() {
        val bg = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        // 100×50 rotated 45° has a bounding box of (100+50)/√2 ≈ 106.07 per side;
        // unioned with the source (0..100, 0..50) and ceiling-rounded → 108×108.
        assertEquals(
            108 to 108,
            BackgroundEffect(
                mode = BackgroundMode.REPLACE,
                backgroundImage = bg,
                placementRotationDegrees = 45f
            ).estimateOutputSize(100, 50)
        )
        // Without a placement image the effect is in place even with placement params.
        assertEquals(
            100 to 50,
            BackgroundEffect(
                mode = BackgroundMode.REPLACE,
                backgroundImage = null,
                placementScale = 2f,
                freeScaleUp = true
            ).estimateOutputSize(100, 50)
        )
    }

    @Test
    fun `merged and root effects estimate from their baked image`() {
        // No baked image → identity of the running image.
        assertEquals(100 to 50, StackRoot(image = null).estimateOutputSize(100, 50))
        assertEquals(100 to 50, MergedEffect(image = null).estimateOutputSize(100, 50))
    }

    // --- Prefix fold: crop normalized against the previous layer's dims -----

    @Test
    fun `prefix fold normalizes crop against the transformed composite`() {
        // 100×50, rotate 90° → 50×100, then crop the left half of the rotated
        // canvas → 25×100. Mirrors the fold renderer's "crop over the oriented
        // image" behaviour (docs/effect_stack.md → The fold renderer).
        val effects = listOf(
            TransformEffect(rotation = 90f),
            CropEffect(rect = Rect(0f, 0f, 0.5f, 1f))
        )
        assertEquals(25 to 100, estimateStackOutputSize(effects, startWidth = 100, startHeight = 50))
    }

    @Test
    fun `prefix fold keeps dimensions for draw in place effects`() {
        val effects = listOf(
            SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 20f),
            FilterEffect(filterMatrix = null),
            StickerEffect(layers = emptyList())
        )
        assertEquals(100 to 50, estimateStackOutputSize(effects, startWidth = 100, startHeight = 50))
    }

    @Test
    fun `prefix fold projects each prefix before the final output`() {
        val transform = TransformEffect(rotation = 90f)
        val crop = CropEffect(rect = Rect(0f, 0f, 0.5f, 1f))
        val identity = FilterEffect(filterMatrix = null)
        // Prefix before the crop = the rotated composite dims.
        assertEquals(50 to 100, estimateStackOutputSize(listOf(transform), 100, 50))
        assertEquals(
            25 to 100,
            estimateStackOutputSize(listOf(transform, crop, identity), 100, 50)
        )
    }
}