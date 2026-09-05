package codes.pepper.whimsicalart.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.CropEffect
import codes.pepper.whimsicalart.feature.editor.domain.BitmapDiffEffect
import codes.pepper.whimsicalart.feature.editor.domain.FrameEffect
import codes.pepper.whimsicalart.feature.editor.domain.FrameLayer
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MlKitOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {

    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MlKitOcrDetector())
    }

    @Test
    fun `initial state has default values`() {
        val state = viewModel.uiState.value
        assertNull(state.imageUri)
        assertFalse(state.isProcessing)
        assertNull(state.error)
        assertNull(state.selectedTool)
        assertEquals(0f, state.brightness)
        assertEquals(0f, state.contrast)
        assertEquals(0f, state.saturation)
        assertEquals(0f, state.sharpness)
        assertEquals(0f, state.rotation)
        assertFalse(state.flipHorizontal)
        assertFalse(state.flipVertical)
        assertNull(state.cropRect)
        assertTrue(state.history.isEmpty())
        assertEquals(-1, state.historyIndex)
    }

    @Test
    fun `setImageUri updates state`() {
        val uri = Uri.parse("content://media/1")
        viewModel.setImageUri(uri)
        assertEquals(uri, viewModel.uiState.value.imageUri)
    }

    @Test
    fun `selectTool updates selectedTool`() {
        viewModel.selectTool(EditTool.BRIGHTNESS)
        assertEquals(EditTool.BRIGHTNESS, viewModel.uiState.value.selectedTool)
    }

    @Test
    fun `selectTool null clears selection`() {
        viewModel.selectTool(EditTool.CROP)
        viewModel.selectTool(null)
        assertNull(viewModel.uiState.value.selectedTool)
    }

    @Test
    fun `updateBrightness updates state`() {
        viewModel.updateBrightness(50f)
        assertEquals(50f, viewModel.uiState.value.brightness)
    }

    @Test
    fun `updateContrast updates state`() {
        viewModel.updateContrast(-30f)
        assertEquals(-30f, viewModel.uiState.value.contrast)
    }

    @Test
    fun `updateSaturation updates state`() {
        viewModel.updateSaturation(75f)
        assertEquals(75f, viewModel.uiState.value.saturation)
    }

    @Test
    fun `updateSharpness updates state`() {
        viewModel.updateSharpness(42f)
        assertEquals(42f, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `rotateLeft wraps to 270 degrees`() {
        viewModel.rotateLeft()
        assertEquals(270f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `rotateRight increments rotation by 90`() {
        viewModel.rotateRight()
        assertEquals(90f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `rotateLeft followed by rotateRight returns to zero`() {
        viewModel.rotateLeft()
        assertEquals(270f, viewModel.uiState.value.rotation)
        viewModel.rotateRight()
        assertEquals(0f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `rotateRight wraps around to 0 after four rotations`() {
        for (i in 1..4) viewModel.rotateRight()
        assertEquals(0f, viewModel.uiState.value.rotation)
    }

    @Test
    fun `flipHorizontal toggles state`() {
        assertFalse(viewModel.uiState.value.flipHorizontal)
        viewModel.flipHorizontal()
        assertTrue(viewModel.uiState.value.flipHorizontal)
        viewModel.flipHorizontal()
        assertFalse(viewModel.uiState.value.flipHorizontal)
    }

    @Test
    fun `flipVertical toggles state`() {
        assertFalse(viewModel.uiState.value.flipVertical)
        viewModel.flipVertical()
        assertTrue(viewModel.uiState.value.flipVertical)
        viewModel.flipVertical()
        assertFalse(viewModel.uiState.value.flipVertical)
    }

    @Test
    fun `updateCrop updates cropRect`() {
        val rect = Rect(10f, 20f, 100f, 200f)
        viewModel.updateCrop(rect)
        assertEquals(rect, viewModel.uiState.value.cropRect)
    }

    @Test
    fun `resetAdjustments clears all adjustment values`() {
        viewModel.updateBrightness(50f)
        viewModel.updateContrast(-30f)
        viewModel.updateSaturation(75f)
        viewModel.updateSharpness(42f)
        
        viewModel.resetAdjustments()
        
        assertEquals(0f, viewModel.uiState.value.brightness)
        assertEquals(0f, viewModel.uiState.value.contrast)
        assertEquals(0f, viewModel.uiState.value.saturation)
        assertEquals(0f, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `rotateRight saves to history`() {
        viewModel.rotateRight()
        assertEquals(1, viewModel.uiState.value.history.size)
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `undo restores previous state`() {
        viewModel.rotateRight()
        viewModel.rotateRight()

        viewModel.undo()

        assertEquals(90f, viewModel.uiState.value.rotation)
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `redo restores next state`() {
        viewModel.rotateRight()
        viewModel.rotateRight()

        viewModel.undo()
        viewModel.redo()

        assertEquals(180f, viewModel.uiState.value.rotation)
        assertEquals(1, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `undo at index 0 does nothing`() {
        viewModel.rotateRight()
        viewModel.undo()
        viewModel.undo()
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `redo at end does nothing`() {
        viewModel.rotateRight()
        viewModel.redo()
        assertEquals(0, viewModel.uiState.value.historyIndex)
    }

    @Test
    fun `setBitmaps stores original and edited bitmaps for the compare overlay`() {
        val original = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        val edited = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        viewModel.setBitmaps(original, edited)
        assertSame(original, viewModel.uiState.value.originalBitmap)
        assertSame(edited, viewModel.uiState.value.editedBitmap)
        assertNull(viewModel.uiState.value.sharpenedPreview)
    }

    @Test
    fun `rapid frenetic sharpness changes settle on the last value without a preview source`() {
        var last = 0f
        for (i in 1..120) {
            last = ((i * 7) % 101).toFloat()
            viewModel.updateSharpness(last)
        }
        assertEquals(last, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `sharpness bounced to zero never leaves a stale preview`() {
        viewModel.updateSharpness(50f)
        viewModel.updateSharpness(0f)
        viewModel.updateSharpness(-40f)
        viewModel.updateSharpness(0f)
        viewModel.updateSharpness(80f)
        viewModel.updateSharpness(0f)
        assertEquals(0f, viewModel.uiState.value.sharpness)
        assertNull(viewModel.uiState.value.sharpenedPreview)
    }

    @Test
    fun `selecting sharpen tool and sliding does not crash when the uri cannot be decoded`() {
        viewModel.setImageUri(Uri.parse("content://codes.pepper.whimsicalart.test/missing"))
        viewModel.selectTool(EditTool.SHARPEN)
        for (i in 1..60) viewModel.updateSharpness((i % 100).toFloat())
        viewModel.updateSharpness(0f)
        viewModel.updateSharpness(1f)
        for (i in 1..40) viewModel.updateSharpness(((i * 13) % 100).toFloat())
        assertEquals(20f, viewModel.uiState.value.sharpness)
    }

    @Test
    fun `startComparing then stopComparing toggles the compare flag`() {
        assertFalse(viewModel.uiState.value.isComparing)
        viewModel.startComparing()
        assertTrue(viewModel.uiState.value.isComparing)
        viewModel.stopComparing()
        assertFalse(viewModel.uiState.value.isComparing)
    }

    @Test
    fun `setEffectStack stores the snapshot with stable ids`() {
        val effect = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 40f
        )
        viewModel.setEffectStack(listOf(effect))
        assertEquals(listOf(effect.id), viewModel.uiState.value.effectStack.map { it.id })
        assertTrue(viewModel.uiState.value.effectStack.isNotEmpty())
    }

    @Test
    fun `selectStackEffect restores remembered parameter and selects the tool`() {
        val effect = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.SATURATION, value = 75f
        )
        viewModel.selectTool(EditTool.TINT)
        viewModel.setEffectStack(listOf(effect))
        viewModel.selectStackEffect(effect.id)
        assertEquals(EditTool.SATURATION, viewModel.uiState.value.selectedTool)
        assertEquals(75f, viewModel.uiState.value.saturation)
    }

    @Test
    fun `selectStackEffect on merged layer does not redirect`() {
        viewModel.selectTool(EditTool.CONTRAST)
        val merged = codes.pepper.whimsicalart.feature.editor.domain.MergedEffect()
        viewModel.setEffectStack(listOf(merged))
        viewModel.selectStackEffect(merged.id)
        assertNotEquals(null, viewModel.uiState.value.selectedTool)
    }

    @Test
    fun `mergeLayers resets adjustments and marks merged but keeps original for compare`() {
        val original = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        viewModel.setBitmaps(original, original)
        viewModel.updateBrightness(50f)
        viewModel.setStyleFilter(codes.pepper.whimsicalart.feature.editor.domain.filter.StyleFilter.FILMIC)
        viewModel.toggleEnhance()
        viewModel.mergeLayers(
            codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle()
        )
        val state = viewModel.uiState.value
        assertTrue(state.hasMerged)
        assertEquals(0f, state.brightness)
        assertNull(state.styleFilter)
        assertFalse(state.enhanceEnabled)
        assertSame(original, state.originalBitmap)
        assertEquals(1, state.effectStack.size)
    }

    @Test
    fun `removeStackEffect drops only the targeted entry`() {
        val a = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 10f
        )
        val b = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.CONTRAST, value = 20f
        )
        viewModel.setEffectStack(listOf(a, b))
        viewModel.removeStackEffect(a.id)
        assertEquals(listOf(b.id), viewModel.uiState.value.effectStack.map { it.id })
    }

    private fun adj(tool: EditTool, v: Float): codes.pepper.whimsicalart.feature.editor.domain.StackEffect =
        codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(tool = tool, value = v)

    private fun keys(): List<String> = viewModel.uiState.value.effectStack.map { it.layerKey }

    @Test
    fun `moveEffect reorders the stack`() {
        viewModel.setEffectStack(listOf(
            adj(EditTool.BRIGHTNESS, 10f),
            adj(EditTool.CONTRAST, 20f),
            adj(EditTool.SATURATION, 30f)
        ))
        assertEquals(
            listOf("layer:BRIGHTNESS", "layer:CONTRAST", "layer:SATURATION"),
            keys()
        )
        viewModel.moveEffect(0, 2)
        assertEquals(
            listOf("layer:CONTRAST", "layer:SATURATION", "layer:BRIGHTNESS"),
            keys()
        )
    }

    @Test
    fun `moveEffect is undoable`() {
        viewModel.setEffectStack(listOf(
            adj(EditTool.BRIGHTNESS, 10f),
            adj(EditTool.CONTRAST, 20f)
        ))
        viewModel.moveEffect(0, 1)
        assertEquals(listOf("layer:CONTRAST", "layer:BRIGHTNESS"), keys())
        viewModel.undo()
        assertEquals(listOf("layer:BRIGHTNESS", "layer:CONTRAST"), keys())
    }

    @Test
    fun `reorder is preserved across a later param sync`() {
        viewModel.setEffectStack(listOf(
            adj(EditTool.BRIGHTNESS, 10f),
            adj(EditTool.CONTRAST, 20f),
            adj(EditTool.SATURATION, 30f)
        ))
        viewModel.moveEffect(0, 2)
        assertEquals(listOf("layer:CONTRAST", "layer:SATURATION", "layer:BRIGHTNESS"), keys())
        // A fresh snapshot in canonical order arrives; the merge must keep the
        // user's ordering (CONTRAST, SATURATION, BRIGHTNESS) while refreshing params.
        val refreshed = listOf(
            adj(EditTool.BRIGHTNESS, 11f),
            adj(EditTool.CONTRAST, 21f),
            adj(EditTool.SATURATION, 31f)
        )
        viewModel.syncLayerState(refreshed, refreshed.associate { it.layerKey to it.layerKey })
        assertEquals(listOf("layer:CONTRAST", "layer:SATURATION", "layer:BRIGHTNESS"), keys())
    }

    @Test
    fun `toggleLayers toggles the layers panel visibility`() {
        assertFalse(viewModel.uiState.value.isLayersVisible)
        viewModel.toggleLayers()
        assertTrue(viewModel.uiState.value.isLayersVisible)
        viewModel.toggleLayers()
        assertFalse(viewModel.uiState.value.isLayersVisible)
    }

    @Test
    fun `clearCrop nulls the crop rect`() {
        viewModel.updateCrop(Rect(10f, 10f, 100f, 100f))
        viewModel.clearCrop()
        assertNull(viewModel.uiState.value.cropRect)
    }

    @Test
    fun `changesGeometry flags the geometry-changing effect set`() {
        // Geometry-changing: crop / transform, occluding overlays, hard brushes.
        assertTrue(codes.pepper.whimsicalart.feature.editor.domain.TransformEffect(rotation = 90f).changesGeometry)
        assertTrue(CropEffect(rect = androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 1f)).changesGeometry)
        assertTrue(codes.pepper.whimsicalart.feature.editor.domain.StickerEffect().changesGeometry)
        assertTrue(codes.pepper.whimsicalart.feature.editor.domain.TextEffect().changesGeometry)
        assertTrue(codes.pepper.whimsicalart.feature.editor.domain.FrameEffect().changesGeometry)
        assertTrue(BitmapDiffEffect(tool = EditTool.PEN, strokes = emptyList()).changesGeometry)
        assertTrue(BitmapDiffEffect(tool = EditTool.MOSAIC, strokes = emptyList()).changesGeometry)
        assertTrue(BitmapDiffEffect(tool = EditTool.OBJECT_REMOVAL, strokes = emptyList()).changesGeometry)
        assertTrue(
            codes.pepper.whimsicalart.feature.editor.domain.BeautySlimEffect().changesGeometry
        )
        assertTrue(
            codes.pepper.whimsicalart.feature.editor.domain.BeautyEyeEnlargeEffect().changesGeometry
        )
        assertTrue(
            codes.pepper.whimsicalart.feature.editor.domain.BeautyNoseEffect().changesGeometry
        )
        assertTrue(
            codes.pepper.whimsicalart.feature.editor.domain.BeautyJawEffect().changesGeometry
        )
        // Colour-only / non-occluding: adjustments, blur brush, makeup brushes.
        assertFalse(adj(EditTool.BRIGHTNESS, 10f).changesGeometry)
        assertFalse(BitmapDiffEffect(tool = EditTool.BLUR_BRUSH, strokes = emptyList()).changesGeometry)
        assertFalse(
            codes.pepper.whimsicalart.feature.editor.domain.BeautySmoothingEffect().changesGeometry
        )
        assertFalse(
            codes.pepper.whimsicalart.feature.editor.domain.BeautyPenEffect().changesGeometry
        )
    }

    @Test
    fun `incremental tail reapply is byte identical to a full from-base fold`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            // Non-uniform content so pixel comparison is meaningful.
            setPixel(5, 5, Color.RED)
            setPixel(30, 10, Color.BLUE)
            setPixel(20, 30, Color.GREEN)
        }
        val effects = listOf(
            SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 40f),
            CropEffect(rect = null),
            FrameEffect(layer = FrameLayer(borderWidth = 0.1f, cornerRadius = 0f, color = Color.BLACK))
        )
        val full = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, base, effects)
        }
        // Compute the prefix composite (effects[0..size-1)) then reapply only the
        // tail (the frame) from it — the incremental 3-image fold path.
        val prefix = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, base, effects.take(effects.size - 1))
        }
        val incremental = runBlocking {
            viewModel.renderStackWithBeautyGeometry(
                context, base, effects, resumeAt = effects.size - 1, resumeBitmap = prefix
            )
        }
        assertEquals(
            "incremental tail reapply must match a full from-base fold byte-for-byte",
            full.width, incremental.width
        )
        assertEquals(full.height, incremental.height)
        for (y in 0 until incremental.height) {
            for (x in 0 until incremental.width) {
                assertEquals("pixel($x,$y)", full.getPixel(x, y), incremental.getPixel(x, y))
            }
        }
        full.recycle()
        prefix.recycle()
        incremental.recycle()
    }

    @Test
    fun `renderStackWithBeautyGeometry returns an independent copy never recycled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        base.eraseColor(Color.WHITE)
        val effects = listOf(
            SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 30f)
        )
        val result = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, base, effects)
        }
        // The returned bitmap is a real, non-recycled, indepently-usable result
        // (the fold never returns the caller-owned base without applying it).
        assertFalse(result.isRecycled)
        assertNotSame("result must not be the caller-owned base", base, result)
        base.recycle()
        result.recycle()
    }

    @Test
    fun `effectsBeforeTool folds only the effects preceding the selected tool`() {
        val brightness = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 40f)
        val frame = FrameEffect(
            layer = FrameLayer(borderWidth = 0.1f, cornerRadius = 0f, color = Color.BLACK)
        )
        val pen = BitmapDiffEffect(tool = EditTool.PEN, strokes = emptyList())

        val noPen = listOf(brightness, frame)
        assertEquals(
            "a tool absent from the stack keeps the whole stack as its base",
            noPen, viewModel.effectsBeforeTool(noPen, EditTool.PEN)
        )
        val withPen = listOf(brightness, pen, frame)
        assertEquals(
            "the brush base must fold only the effects before the brush layer",
            listOf(brightness), viewModel.effectsBeforeTool(withPen, EditTool.PEN)
        )
        assertEquals(
            "a null tool keeps the whole stack",
            withPen, viewModel.effectsBeforeTool(withPen, null)
        )
    }

    @Test
    fun `clearCompositePreview resets the preview field`() {
        // There is no decodable source URI in a unit test, so refresh leaves it
        // null; clearing must not crash nor disturb unrelated state.
        viewModel.updateContrast(30f)
        viewModel.clearCompositePreview()
        assertNull(viewModel.uiState.value.compositePreview)
        assertEquals(
            "clearing the composite preview must not disturb the other state",
            30f, viewModel.uiState.value.contrast
        )
    }

    @Test
    fun `refreshCompositePreview is safe without a decodable source`() {
        // No imageUri → buildBaseAndEffects returns null → refresh must not
        // crash and must leave compositePreview null.
        viewModel.refreshCompositePreview(codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle())
        assertNull(viewModel.uiState.value.compositePreview)
    }

    @Test
    fun `rotation-only transform layer survives a sync into the effect stack`() {
        // Reproduces the "transform layer missing from Layers list" report:
        // after brightness → rotate → another tool, the TransformEffect (rotation,
        // no crop rect) must remain a committed layer in effectStack.
        val brightness = codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect(
            tool = EditTool.BRIGHTNESS, value = 40f
        )
        viewModel.setEffectStack(listOf(brightness))
        viewModel.setRotation(90f)
        viewModel.updateCrop(Rect(10f, 10f, 90f, 90f))

        val snapshot = listOf(
            brightness.copy(id = brightness.id),
            codes.pepper.whimsicalart.feature.editor.domain.TransformEffect(rotation = 90f),
            CropEffect(rect = Rect(10f, 10f, 90f, 90f))
        )
        viewModel.syncLayerState(snapshot, snapshot.associate { it.layerKey to it.layerKey })
        val keys = viewModel.uiState.value.effectStack.map { it.layerKey }
        assertEquals(listOf("layer:BRIGHTNESS", "layer:TRANSFORM", "layer:CROP"), keys)
    }

    @Test
    fun `rotation-only transform renders a differently-sized album through the fold`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val effect = codes.pepper.whimsicalart.feature.editor.domain.TransformEffect(rotation = 90f)
        val result = runBlocking {
            viewModel.renderStackWithBeautyGeometry(context, base, listOf(effect))
        }
        assertEquals(80, result.width)
        assertEquals(40, result.height)
        base.recycle()
        result.recycle()
    }

    @Test
    fun `crop applies over the oriented image when transform precedes it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        // Transform bakes a 80x40 canvas; the crop rect is normalized against
        // THAT oriented bitmap (matches how the renderer applies both).
        val result = runBlocking {
            viewModel.renderStackWithBeautyGeometry(
                context, base,
                listOf(
                    codes.pepper.whimsicalart.feature.editor.domain.TransformEffect(rotation = 90f),
                    CropEffect(rect = Rect(0f, 0f, 0.5f, 1f))
                )
            )
        }
        assertEquals(40, result.width)
        assertEquals(40, result.height)
        base.recycle()
        result.recycle()
    }
}
