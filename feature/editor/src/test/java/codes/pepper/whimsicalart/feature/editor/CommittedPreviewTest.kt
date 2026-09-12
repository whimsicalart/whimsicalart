package codes.pepper.whimsicalart.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundEffect
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundLayer
import codes.pepper.whimsicalart.feature.editor.domain.BackgroundMode
import codes.pepper.whimsicalart.feature.editor.domain.ImageSaver
import codes.pepper.whimsicalart.feature.editor.domain.EditorRenderBundle
import codes.pepper.whimsicalart.feature.editor.domain.FrameEffect
import codes.pepper.whimsicalart.feature.editor.domain.FrameLayer
import codes.pepper.whimsicalart.feature.editor.domain.FrameStyle
import codes.pepper.whimsicalart.feature.editor.domain.FrameTier
import codes.pepper.whimsicalart.feature.editor.domain.SingleAdjustmentEffect
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.domain.StrokeLayer
import codes.pepper.whimsicalart.feature.editor.domain.StrokeType
import codes.pepper.whimsicalart.feature.editor.domain.decodeBoundedOriented
import codes.pepper.whimsicalart.feature.editor.domain.matting.BackgroundReplacer
import codes.pepper.whimsicalart.feature.editor.domain.matting.SoftEdgeBand
import codes.pepper.whimsicalart.feature.editor.domain.ocr.MnnOcrDetector
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorViewModel
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * P8 — per-layer committed preview bitmaps carried on the [StackEffect] layer
 * itself (docs/effect_stack.md → Performance / preview cache → *Per-layer
 * preview cache*): each committed layer carries its preview, the next layer
 * folds from it, re-editing only re-renders the changed layer + tail, and the
 * interactive preview fold resumes from the nearest carried preview. Driven
 * through the real production pass ([EditorViewModel.rebuildCommittedPreviews])
 * with deterministic runBlocking so no coroutine hopping skews the assertions.
 */
@RunWith(RobolectricTestRunner::class)
class CommittedPreviewTest {

    private lateinit var context: Context
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context, ImageSaver(context), MnnOcrDetector(context))
    }

    // --- helpers -----------------------------------------------------------

    private fun redGreenOriginal(): Bitmap {
        // 12x8 two-tone: left RED, right GREEN. Built with eraseColor/setPixel
        // only — Robolectric's graphics shadows do not rasterize Canvas draw ops.
        val bmp = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.RED)
        for (x in 6 until 12) {
            for (y in 0 until 8) {
                bmp.setPixel(x, y, Color.GREEN)
            }
        }
        return bmp
    }

    private fun scaledBase(original: Bitmap, scale: Float = 0.5f): Bitmap =
        Bitmap.createScaledBitmap(
            original,
            (original.width * scale).roundToInt().coerceAtLeast(1),
            (original.height * scale).roundToInt().coerceAtLeast(1),
            true
        )

    private fun fp(effect: SingleAdjustmentEffect): String =
        "adj:${effect.tool}:${effect.value}"

    private fun commit(vararg effects: StackEffect) {
        viewModel.syncLayerState(
            effects.toList(),
            effects.associate {
                it.layerKey to if (it is SingleAdjustmentEffect) fp(it) else it.layerKey
            }
        )
    }

    private fun rebuild(scale: Float = 0.5f, base: Bitmap = scaledBase(redGreenOriginal())) {
        // Mirror refreshLayerPreviews: rebuildCommittedPreviews computes the chain
        // AND the production caller publishes the thumbnails/fingerprints used by
        // syncLayerState's eviction check.
        val (thumbs, fingerprints) = runBlocking {
            viewModel.rebuildCommittedPreviews(viewModel.uiState.value.effectStack, scale, base)
        }
        viewModel.publishLayerPreviews(thumbs, fingerprints)
    }

    private fun foldFromBase(bundle: EditorViewModel, base: Bitmap, effects: List<StackEffect>, scale: Float = 0.5f): Bitmap =
        runBlocking {
            bundle.renderStackWithBeautyGeometry(
                context, base, effects,
                adaptBackgroundArtifacts = true,
                scale = scale
            )
        }

    private fun assertPixelsEqual(expected: Bitmap, actual: Bitmap, message: String = "") {
        assertEquals(message, expected.width, actual.width)
        assertEquals(message, expected.height, actual.height)
        for (x in 0 until expected.width) {
            for (y in 0 until expected.height) {
                assertEquals("${message} pixel($x,$y)", expected.getPixel(x, y), actual.getPixel(x, y))
            }
        }
    }

    // --- tests -------------------------------------------------------------

    @Test
    fun `a committed layer carries its preview matching a from-base fold of one effect`() {
        val original = redGreenOriginal()
        val base = scaledBase(original)
        val brightness = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f)
        commit(brightness)
        rebuild(base = base)

        val carried = viewModel.uiState.value.effectStack[0].committedPreview
        assertNotNull("the committed layer carries its preview bitmap", carried)
        assertEquals(6, carried!!.width)
        assertEquals(4, carried.height)
        assertPixelsEqual(
            foldFromBase(viewModel, base, listOf(brightness)),
            carried,
            "carried preview byte-matches a from-base fold of the layer"
        )
        // A real preview, never an alias of the pristine fold base.
        assertNotSame(base, carried)
    }

    @Test
    fun `a second layer's committed preview equals folding the FIRST layer's carried preview with one effect`() {
        val original = redGreenOriginal()
        val base = scaledBase(original)
        val e0 = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f)
        val e1 = SingleAdjustmentEffect(tool = EditTool.CONTRAST, value = 0.3f)
        commit(e0, e1)
        rebuild(base = base)

        val stack = viewModel.uiState.value.effectStack
        val p0 = stack[0].committedPreview
        val p1 = stack[1].committedPreview
        assertNotNull(p0)
        assertNotNull(p1)
        // p1 == full from-base fold AND == folding p0 with ONE more effect.
        assertPixelsEqual(foldFromBase(viewModel, base, listOf(e0, e1)), p1!!, "tail preview")
        val chained = runBlocking {
            viewModel.renderStackWithBeautyGeometry(
                context, base, listOf(stack[0], stack[1]),
                resumeAt = 1, resumeBitmap = p0,
                adaptBackgroundArtifacts = true,
                scale = 0.5f
            )
        }
        assertPixelsEqual(chained, p1, "second layer folds from the first layer's carried preview")
    }

    @Test
    fun `re-committing unchanged values leaves the preview untouched (identity no re-render no eviction)`() {
        val original = redGreenOriginal()
        val base = scaledBase(original)
        val e0 = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f)
        commit(e0)
        rebuild(base = base)

        val first = viewModel.uiState.value.effectStack[0].committedPreview
        assertNotNull(first)

        // Re-commit the SAME value (fresh instance, identical fingerprint).
        commit(SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f))
        // No eviction: the thumbnail for the unchanged layer is still published.
        assertTrue(
            "unchanged re-commit must not evict the layer's cached preview",
            viewModel.uiState.value.layerThumbnails.containsKey("layer:BRIGHTNESS")
        )
        rebuild(base = base)
        val revived = viewModel.uiState.value.effectStack[0].committedPreview
        assertNotNull("unchanged re-commit keeps the carried preview", revived)
        assertSame(
            "unchanged re-commit reuses the SAME preview bitmap — no re-render, no new bitmap",
            first,
            revived
        )
    }

    @Test
    fun `changing a value then restoring it before commit resumes from the previous layer's carried preview`() {
        val original = redGreenOriginal()
        val base = scaledBase(original)
        val e0 = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f)
        val e1 = SingleAdjustmentEffect(tool = EditTool.CONTRAST, value = 0.3f)
        commit(e0, e1)
        rebuild(base = base)
        val stack = viewModel.uiState.value.effectStack
        val p0 = stack[0].committedPreview
        assertNotNull(p0)

        // Live-edit: the tool's value moves AWAY then BACK before any second
        // commit. The committed stack under the tool is unchanged, so the
        // interactive fold anchors on the last carried preview either way.
        val moved = SingleAdjustmentEffect(tool = EditTool.CONTRAST, value = 0.8f)
        val restored = SingleAdjustmentEffect(tool = EditTool.CONTRAST, value = 0.3f)

        assertEquals(
            "a different live value still anchors on the previous carried preview",
            0,
            viewModel.committedPreviewAnchor(listOf(e0, moved), 1, base.width, base.height)
        )
        assertEquals(
            "restoring the committed value anchors identically",
            0,
            viewModel.committedPreviewAnchor(listOf(e0, restored), 1, base.width, base.height)
        )
        // Both fold from the SAME carried preview — the tail (one effect) is the
        // only thing re-rendered, and the output stays byte-identical to a
        // from-base fold of the same list.
        for (effects in listOf(listOf(e0, moved), listOf(e0, restored))) {
            val resume = runBlocking {
                viewModel.renderStackWithBeautyGeometry(
                    context, base, effects,
                    resumeAt = 1, resumeBitmap = p0,
                    adaptBackgroundArtifacts = true,
                    scale = 0.5f
                )
            }
            assertPixelsEqual(foldFromBase(viewModel, base, effects), resume, "anchored fold")
        }
    }

    @Test
    fun `appending a layer keeps every prior carried preview so the preview fold resumes from the tail`() {
        val original = redGreenOriginal()
        val base = scaledBase(original)
        val e0 = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f)
        val e1 = SingleAdjustmentEffect(tool = EditTool.CONTRAST, value = 0.3f)
        val e2 = SingleAdjustmentEffect(tool = EditTool.SATURATION, value = 0.4f)
        commit(e0, e1, e2)
        rebuild(base = base)
        val prefixTail = viewModel.uiState.value.effectStack[2].committedPreview
        assertNotNull(prefixTail)

        // Append filter N+1 over a stack of N=3: every prior layer must keep its
        // carried preview ACROSS the ordered merge (the merged instances are
        // fresh derivations whose carried field is null otherwise), so the next
        // interactive preview fold resumes from the tail instead of re-folding it.
        val e3 = SingleAdjustmentEffect(tool = EditTool.VIGNETTE, value = 0.5f)
        commit(e0, e1, e2, e3)
        val after = viewModel.uiState.value.effectStack
        assertEquals(4, after.size)
        assertSame(
            "the previous last layer keeps its carried preview through a tail-append commit",
            prefixTail,
            after[2].committedPreview
        )
        assertNull(
            "the appended layer carries nothing until its preview is rebuilt",
            after[3].committedPreview
        )

        // The preview composite then anchors on layer N-1 and folds EXACTLY the
        // one appended effect (the O(1) tail append), with the fresh derived
        // instances exactly as committedEffects would supply them.
        val derived = listOf(e0, e1, e2, e3)
        assertEquals(
            "anchor lands on the previous last carried layer, not the controller fallback",
            2,
            viewModel.committedPreviewAnchor(derived, 3, base.width, base.height)
        )

        // Rebuild the previews: the appended layer then carries a preview equal
        // to its own fold over the previous layer's carried preview
        // (byte-identical to a from-base fold of the whole stack).
        rebuild(base = base)
        val carried = viewModel.uiState.value.effectStack[3].committedPreview
        assertNotNull("the appended layer carries its rebuilt preview", carried)
        assertPixelsEqual(foldFromBase(viewModel, base, derived), carried!!)
    }

    @Test
    fun `moving a layer regenerates the moved layer and tail and keeps the prefix preview untouched`() {
        val original = redGreenOriginal()
        val base = scaledBase(original)
        val e0 = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f)
        val e1 = SingleAdjustmentEffect(tool = EditTool.CONTRAST, value = 0.3f)
        val e2 = SingleAdjustmentEffect(tool = EditTool.SATURATION, value = 0.4f)
        commit(e0, e1, e2)
        rebuild(base = base)
        val prefixPreview = viewModel.uiState.value.effectStack[0].committedPreview
        assertNotNull(prefixPreview)

        viewModel.moveEffect(1, 2)
        val moved = viewModel.uiState.value.effectStack
        assertEquals(3, moved.size)
        assertSame("prefix before the move keeps its carried preview untouched", prefixPreview, moved[0].committedPreview)
        assertNull("the moved layer's stale preview is dropped", moved[1].committedPreview)
        assertNull("the shifted tail's stale preview is dropped", moved[2].committedPreview)

        rebuild(base = base)
        val movedTail = moved[2].committedPreview
        assertNotNull(moved[1].committedPreview)
        assertNotNull(movedTail)
        // Regenerated content == a from-base fold of the NEW order.
        assertPixelsEqual(
            foldFromBase(viewModel, base, listOf(moved[0], moved[1], moved[2])),
            movedTail!!,
            "moved tail regenerated to the new ordering"
        )
    }

    @Test
    fun `removing a layer regenerates the tail and keeps the prefix preview untouched (same instances)`() {
        val original = redGreenOriginal()
        val base = scaledBase(original)
        val e0 = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f)
        val e1 = SingleAdjustmentEffect(tool = EditTool.CONTRAST, value = 0.3f)
        val e2 = SingleAdjustmentEffect(tool = EditTool.SATURATION, value = 0.4f)
        commit(e0, e1, e2)
        rebuild(base = base)
        val prefixPreview = viewModel.uiState.value.effectStack[0].committedPreview
        assertNotNull(prefixPreview)

        // Delete the middle layer: commit the stack without it (the app
        // re-snapshots FRESH instances of the surviving layers).
        commit(
            SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f),
            SingleAdjustmentEffect(tool = EditTool.SATURATION, value = 0.4f)
        )
        val remaining = viewModel.uiState.value.effectStack
        assertEquals(2, remaining.size)
        assertSame(
            "the untouched prefix KEEPS its carried preview across the merge (its fold input never changed)",
            prefixPreview,
            remaining[0].committedPreview
        )
        assertNull(
            "the surviving tail's carried preview is evicted — its fold input changed with the removal",
            remaining[1].committedPreview
        )

        rebuild(base = base)
        assertSame(
            "the untouched prefix reuses its SAME cached preview — no re-render",
            prefixPreview,
            remaining[0].committedPreview
        )
        val remainingTail = remaining[1].committedPreview
        assertNotNull(remainingTail)
        assertPixelsEqual(
            foldFromBase(viewModel, base, listOf(remaining[0], remaining[1])),
            remainingTail!!,
            "tail regenerated against the new (shortened) stack"
        )
    }

    @Test
    fun `a recycled or scaled-away carried preview is never resumed from`() {
        val original = redGreenOriginal()
        val base = scaledBase(original)
        val e0 = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f)
        val e1 = SingleAdjustmentEffect(tool = EditTool.CONTRAST, value = 0.3f)
        commit(e0, e1)
        rebuild(base = base)
        val stack = viewModel.uiState.value.effectStack

        // Resolution change (a different viewport): the carried preview no longer
        // matches the requested fold base.
        assertEquals(
            "a resolutions-mismatched carried preview is skipped",
            -1,
            viewModel.committedPreviewAnchor(listOf(e0, e1), 1, 12, 8) ?: -1
        )
        assertEquals(
            0,
            viewModel.committedPreviewAnchor(listOf(e0, e1), 1, base.width, base.height) ?: -1
        )

        // Eviction recycling: a recycled carried preview is skipped too.
        stack[0].committedPreview?.recycle()
        stack[0].committedPreview = null
        assertNull(
            "a recycled carried preview is never resumed from",
            viewModel.committedPreviewAnchor(listOf(e0, e1), 1, base.width, base.height)
        )
    }

    @Test
    fun `a frame committed as the sole first layer carries a preview folded from the pristine base`() {
        val base = redGreenOriginal() // 12x8: the preview-sized pristine original (scale 1)
        val frame = FrameEffect(
            layer = FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.4f, color = Color.BLACK))
        )
        commit(frame)
        rebuild(base = base, scale = 1f)

        val carried = viewModel.uiState.value.effectStack[0].committedPreview
        assertNotNull("the frame layer carries its committed preview", carried)
        assertEquals(12, carried!!.width)
        assertEquals(8, carried.height)
        assertPixelsEqual(
            foldFromBase(viewModel, base, listOf(frame), scale = 1f),
            carried,
            "first-layer frame preview byte-matches a from-base fold (unconditional pristine base)"
        )
        // Correct band: opaque black over the outer 1.6px of the 12x8 canvas
        // (thickness 0.4 → half fraction = 0.2 × short edge 8 = 1.6 px; the band
        // must stay >= 1 px to draw, and just below it (0.8 px) the renderer
        // intentionally skips). The interior window (x/y in 1.6..10.4 / 1.6..6.4)
        // keeps the original pixels.
        assertEquals(Color.BLACK, carried.getPixel(0, 0))
        assertEquals(Color.BLACK, carried.getPixel(11, 7))
        assertEquals(Color.RED, carried.getPixel(4, 3))
        assertEquals(Color.GREEN, carried.getPixel(9, 3))
    }

    @Test
    fun `a recycled resume bitmap never seeds the fold (first-layer frame safety)`() {
        val original = redGreenOriginal()
        val base = scaledBase(original)
        val brightness = SingleAdjustmentEffect(tool = EditTool.BRIGHTNESS, value = 0.2f)
        val frame = FrameEffect(
            layer = FrameLayer(inner = FrameTier(style = FrameStyle.SOLID, thickness = 0.3f, color = Color.BLACK))
        )
        val recycled = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        recycled.recycle()
        // Tail resumed at index 1 over a RECYCLED composite — the racing-cache
        // scenario that used to hand the recycled bitmap to FrameEffect.render
        // (reading `current.width` on a recycled bitmap crashes the app). The
        // fold must drop the dead resume and restart from the pristine base.
        val folded = runBlocking {
            viewModel.renderStackWithBeautyGeometry(
                context, base, listOf(brightness, frame),
                resumeAt = 1, resumeBitmap = recycled,
                adaptBackgroundArtifacts = true, scale = 0.5f
            )
        }
        assertTrue(!folded.isRecycled)
        assertPixelsEqual(
            foldFromBase(viewModel, base, listOf(brightness, frame)),
            folded,
            "a recycled resume bitmap folds the whole stack from the pristine base"
        )
    }

    @Test
    fun `eye-off composite fold with zero committed effects does not crash`() {
        val original = redGreenOriginal()
        viewModel.setBitmaps(original, original)
        viewModel.selectTool(EditTool.FRAMES)
        // Pre-fix this threw "Cannot coerce value to an empty range" inside
        // foldedEffects; now the empty stack falls back gracefully.
        viewModel.refreshCompositePreview(
            EditorRenderBundle(),
            includeTail = false,
            visibleWidthPx = 200,
            visibleHeightPx = 200,
            zoom = 1f
        )
        // Nothing committed → no composite published, and no crash.
        assertNull(viewModel.uiState.value.compositePreview)
    }

    @Test
    fun `a replace background layer renders the exact placed canvas of its placement transform`() {
        val original = redGreenOriginal()
        val mask = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (x in 2 until 10) {
                for (y in 2 until 6) {
                    setPixel(x, y, Color.BLACK)
                }
            }
        }
        // Exact-match footprint (12x8, the fold base size) so the preview-fold
        // artifact adaptation is a no-op and the fold equals a raw place() call.
        val bg = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val transforms = listOf(
            BackgroundReplacer.PlacementTransform(),
            BackgroundReplacer.PlacementTransform(
                scale = 1.5f, panX = 0.2f, panY = -0.15f, rotationDegrees = 30f, freeScaleUp = true
            )
        )
        for (t in transforms) {
            val effect = BackgroundEffect(
                mode = BackgroundMode.REPLACE,
                subjectMask = mask,
                backgroundImage = bg,
                placementScale = t.scale,
                placementPanX = t.panX,
                placementPanY = t.panY,
                placementRotationDegrees = t.rotationDegrees,
                freeScaleUp = t.freeScaleUp
            )
            val expected = BackgroundReplacer.place(original, mask, bg, t, softEdgeWidth = SoftEdgeBand.DEFAULT_WIDTH)
            val folded = foldFromBase(viewModel, original, listOf(effect), scale = 1f)
            assertPixelsEqual(expected, folded, "replace fold == place($t) — pre-fix the fold returned the un-placed copy")
            expected.recycle()
            folded.recycle()
        }
        // The two placements produce genuinely different canvases — panning /
        // scaling / rotating the placement CHANGES the fold output (the
        // "image follows the grid" invariant at the fold level).
        val zero = BackgroundReplacer.place(original, mask, bg, transforms[0])
        val moved = BackgroundReplacer.place(original, mask, bg, transforms[1])
        assertTrue(
            "a moved placement grows the composite canvas",
            zero.width != moved.width || zero.height != moved.height || zero.getPixel(0, 0) != moved.getPixel(0, 0)
        )
        zero.recycle()
        moved.recycle()
    }

    @Test
    fun `a blur background layer defocuses only the region outside the mask`() {
        // Left half = subject (opaque mask -> kept sharp); right half =
        // background (transparent -> blurred). Exact 12x8 footprint + scale 1
        // so the fold applies the mask as-is.
        val original = redGreenOriginal()
        val mask = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (x in 0 until 6) {
                for (y in 0 until 8) {
                    setPixel(x, y, Color.WHITE)
                }
            }
        }
        val effect = BackgroundEffect(
            mode = BackgroundMode.BLUR,
            subjectMask = mask,
            blurRadius = 10f
        )
        val folded = foldFromBase(viewModel, original, listOf(effect), scale = 1f)
        try {
            // The subject CORE (deeper than the 4px soft edge band) stays
            // byte-identical (mask alpha keeps it sharp)...
            for (y in 0 until 8) {
                for (x in 0 until 2) {
                    assertEquals("subject core pixel($x,$y)", original.getPixel(x, y), folded.getPixel(x, y))
                }
            }
            // ...the boundary-adjacent pixel (depth 0 of the band) is faded all
            // the way into the background, so the soft fringe visibly cuts in...
            var mixed = 0
            for (y in 0 until 8) {
                for (x in 2 until 6) {
                    if (original.getPixel(x, y) != folded.getPixel(x, y)) mixed++
                }
            }
            assertTrue("soft band mixes the subject edge, got $mixed/32 band px changed", mixed > 0)
            // ...while the background half was defocused (the box blur averaged
            // neighbouring RED/GREEN), so it is NOT the unblurred working copy —
            // pre-fix the fold discarded the blurred result and returned the
            // unmodified copy ("blur visibly does nothing").
            var changed = 0
            for (y in 0 until 8) {
                for (x in 6 until 12) {
                    if (original.getPixel(x, y) != folded.getPixel(x, y)) changed++
                }
            }
            assertTrue(
                "background must be defocused by the blur fold, got $changed/48 background px changed",
                changed > 0
            )
        } finally {
            folded.recycle()
        }
    }

    @Test
    fun `blur intensity is a percent of canvas width so the slider varies the strength`() {
        // 32x24 two-tone: left RED / right GREEN, subject = left half.
        val original = redGreenOriginal()
        val base = Bitmap.createBitmap(original.width * 2, original.height * 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            for (x in original.width until width) setPixelColorStrip(this, x, Color.GREEN)
        }
        val mask = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (x in 0 until base.width / 2) setPixelColorStrip(this, x, Color.WHITE)
        }
        val soft = backgroundBlur(base, mask, blurRadius = 10f)
        val strong = backgroundBlur(base, mask, blurRadius = 40f)
        try {
            var softChanged = 0
            var strongChanged = 0
            for (y in 0 until base.height) {
                for (x in base.width / 2 until base.width) {
                    if (base.getPixel(x, y) != soft.getPixel(x, y)) softChanged++
                    if (base.getPixel(x, y) != strong.getPixel(x, y)) strongChanged++
                }
            }
            assertTrue(
                "radius must scale with intensity: got soft=$softChanged vs strong=$strongChanged changed bg px",
                strongChanged > softChanged
            )
        } finally {
            base.recycle()
            mask.recycle()
            soft.recycle()
            strong.recycle()
        }
    }

    private fun setPixelColorStrip(bmp: Bitmap, x: Int, color: Int) {
        for (y in 0 until bmp.height) bmp.setPixel(x, y, color)
    }

    private fun backgroundBlur(base: Bitmap, mask: Bitmap, blurRadius: Float): Bitmap =
        foldFromBase(
            viewModel,
            base,
            listOf(BackgroundEffect(mode = BackgroundMode.BLUR, subjectMask = mask, blurRadius = blurRadius)),
            scale = 1f
        )

    @Test
    fun `a blur mask oriented to a rotated fold base defocuses the right region without manual transform`() {
        // The fold base already carries prior rotation/crop (the sheet segment
        // must run against the running base at the layer position — never the
        // pristine decode). Simulate a 90°-rotated base: subject = top half
        // (RED), background = bottom half (GREEN), with the mask in the SAME
        // orientation. No mask mapping may be needed.
        val base = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            for (y in 6 until 12) setRow(this, y, Color.GREEN)
        }
        val mask = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (y in 0 until 6) setRow(this, y, Color.WHITE)
        }
        val folded = foldFromBase(
            viewModel,
            base,
            listOf(BackgroundEffect(mode = BackgroundMode.BLUR, subjectMask = mask, blurRadius = 10f)),
            scale = 1f
        )
        try {
            for (y in 0 until 2) {
                for (x in 0 until 8) {
                    assertEquals("rotated subject core row $y", base.getPixel(x, y), folded.getPixel(x, y))
                }
            }
            var mixed = 0
            for (y in 2 until 6) {
                for (x in 0 until 8) {
                    if (base.getPixel(x, y) != folded.getPixel(x, y)) mixed++
                }
            }
            assertTrue("rotated soft band mixes the subject edge, got $mixed/32 band px changed", mixed > 0)
            var changed = 0
            for (y in 6 until 12) {
                for (x in 0 until 8) {
                    if (base.getPixel(x, y) != folded.getPixel(x, y)) changed++
                }
            }
            assertTrue("rotated background must be defocused, got $changed/48 px changed", changed > 0)
        } finally {
            base.recycle()
            mask.recycle()
            folded.recycle()
        }
    }

    @Test
    fun `replace with a null background image keeps the fold byte-identical and the state empty`() {
        val original = redGreenOriginal()
        val mask = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (x in 0 until 6) setPixelColorStrip(this, x, Color.WHITE)
        }
        val effect = BackgroundEffect(
            mode = BackgroundMode.REPLACE,
            subjectMask = mask,
            backgroundImage = null
        )
        val folded = foldFromBase(viewModel, original, listOf(effect), scale = 1f)
        try {
            assertNotSame("REPLACE must never alias the fold base", original, folded)
            assertPixelsEqual(original, folded, "REPLACE with no image is a byte-identical no-op, not a crash")
        } finally {
            folded.recycle()
            mask.recycle()
        }
        viewModel.updateBackgroundMode(BackgroundMode.REPLACE)
        assertNull(
            "switching to Replace must keep the state explicitly image-less until a preset/image is loaded",
            viewModel.uiState.value.backgroundImage
        )
    }

    private fun setRow(bmp: Bitmap, y: Int, color: Int) {
        for (x in 0 until bmp.width) bmp.setPixel(x, y, color)
    }

    @Test
    fun `a rapid placement gesture stream converges the composite to the LAST transform`() {
        val file = File(context.cacheDir, "placement_gesture_stream.jpg")
        val src = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(800, 600)

        val mask = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (x in 16 until 32) {
                for (y in 10 until 22) {
                    setPixel(x, y, Color.BLACK)
                }
            }
        }
        val bg = Bitmap.createBitmap(64, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val transforms = listOf(
            BackgroundReplacer.PlacementTransform(),
            BackgroundReplacer.PlacementTransform(scale = 1.2f, panX = 0.05f, panY = 0f, freeScaleUp = true),
            BackgroundReplacer.PlacementTransform(
                scale = 1.3f, panX = 0.12f, panY = -0.08f, rotationDegrees = 35f, freeScaleUp = true
            )
        )

        fun bundleFor(t: BackgroundReplacer.PlacementTransform): EditorRenderBundle =
            EditorRenderBundle(
                background = BackgroundLayer(
                    mode = BackgroundMode.REPLACE,
                    subjectMask = mask,
                    backgroundImage = bg,
                    placementScale = t.scale,
                    placementPanX = t.panX,
                    placementPanY = t.panY,
                    placementRotationDegrees = t.rotationDegrees,
                    freeScaleUp = t.freeScaleUp
                )
            )

        // Emulate a fast gesture stream: 3 placement updates land with NO main-
        // looper idle between them, so each new request only marks the running
        // fold dirty. Pre-fix the coalescing loop captured the FIRST request and
        // re-folded it forever → the published composite ignored the LAST
        // transform (the "replacement does not follow the grid" device report).
        viewModel.refreshCompositePreview(
            bundleFor(transforms[0]), includeTail = true,
            visibleWidthPx = 800, visibleHeightPx = 600, zoom = 1f
        )
        viewModel.refreshCompositePreview(
            bundleFor(transforms[1]), includeTail = true,
            visibleWidthPx = 800, visibleHeightPx = 600, zoom = 1f
        )
        viewModel.refreshCompositePreview(
            bundleFor(transforms[2]), includeTail = true,
            visibleWidthPx = 800, visibleHeightPx = 600, zoom = 1f
        )

        val composite = waitForComposite(
            BackgroundReplacer.placedCanvasSize(48, 32, transforms[2].scale, transforms[2].rotationDegrees).first,
            BackgroundReplacer.placedCanvasSize(48, 32, transforms[2].scale, transforms[2].rotationDegrees).second
        )
        assertNotNull("composite converges to the final (grown) placement", composite)

        val base = decodeBoundedOriented(context.contentResolver, Uri.fromFile(file))!!
        val expected = BackgroundReplacer.place(base, mask, bg, transforms[2], softEdgeWidth = SoftEdgeBand.DEFAULT_WIDTH)
        assertPixelsEqual(expected, composite!!, "composite reflects the LAST placement")
        expected.recycle()
        base.recycle()
    }

    @Test
    fun `a published composite records the strokes version it was folded with`() {
        // UI/UX "Brush tools — no dead gap": the brush overlay may hide its
        // committed strokes only once the published composite actually includes
        // them. The publisher therefore records the strokes-version it folded —
        // here the composite carries mosaicStrokesVersion=7, so the state must
        // reflect exactly that once it lands.
        val file = File(context.cacheDir, "mosaic_version_composite.jpg")
        val src = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(800, 600)

        val bundle = EditorRenderBundle(
            strokes = listOf(
                StrokeLayer(
                    type = StrokeType.MOSAIC,
                    points = listOf(0.5f to 0.5f),
                    brushSize = 0.3f,
                    opacity = 1f
                )
            )
        )
        viewModel.refreshCompositePreview(
            bundle, includeTail = true,
            visibleWidthPx = 800, visibleHeightPx = 600, zoom = 1f,
            mosaicStrokesVersion = 7
        )

        val looper = shadowOf(Looper.getMainLooper())
        var published = false
        repeat(4000) {
            looper.idle()
            val state = viewModel.uiState.value
            if (state.compositePreview != null && state.publishedMosaicStrokesVersion == 7) {
                published = true
                return@repeat
            }
            Thread.sleep(5)
        }
        assertTrue("composite published carrying mosaic strokes version 7", published)
    }

    private fun waitForComposite(width: Int, height: Int): Bitmap? {
        // The coalescing fold publishes the FIRST intermediate composite (the
        // pre-grid-latest placement) the moment its first Default-thread fold
        // lands, then converges on the NEWEST request. Sampling the first
        // non-null would assert against a stale mid-gesture placement, so wait
        // until the published composite has the LAST transform's exact (grown)
        // canvas size — pre-fix the loop re-folded request #1 forever and never
        // reached it.
        val looper = shadowOf(Looper.getMainLooper())
        repeat(4000) {
            looper.idle()
            val c = viewModel.uiState.value.compositePreview
            if (c != null && c.width == width && c.height == height) return c
            Thread.sleep(5)
        }
        return null
    }

    // --- blur full-fold publish (BUG: Full-size brush blur vanishes on pinch-zoom) --

    /** An 1920x1280 JPEG whose left half is RED and right half GREEN (sharp edge at x=960). */
    private fun twoToneBlurSource(name: String): File {
        val file = File(context.cacheDir, name)
        val src = Bitmap.createBitmap(1920, 1280, Bitmap.Config.ARGB_8888)
        for (y in 0 until 1280) {
            for (x in 0 until 1920) {
                src.setPixel(x, y, if (x < 960) Color.RED else Color.GREEN)
            }
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        src.recycle()
        return file
    }

    /**
     * Waits until a FULL-SIZE (composite == bounded source dims) composite is
     * published. Full folds run ONLY past 100% zoom and are debounced, so this
     * advances the Robolectric clock across [EditorViewModel.FULL_FOLD_DEBOUNCE_MS]
     * while yielding real time for the Dispatchers.Default render threads.
     */
    private fun waitForFullComposite(pristine: Bitmap): Bitmap? {
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS)
        val looper = shadowOf(Looper.getMainLooper())
        // Bounded REAL-time budget instead of a fixed iteration count: the full
        // fold runs on Dispatchers.Default, so under host CPU contention the fold
        // can take far longer than the old 9000x5ms (~45s) fixed cap the test
        // used to allow. The assertion below is unchanged (a full-size composite
        // MUST eventually be published past 100% zoom) — only the time bound is
        // generous enough to be immune to a slow CI container.
        val deadline = System.nanoTime() + 120_000_000_000L
        while (System.nanoTime() < deadline) {
            looper.idle()
            val c = viewModel.uiState.value.compositePreview
            if (c != null && c.width == pristine.width && c.height == pristine.height) return c
            Thread.sleep(5)
        }
        return null
    }

    private fun blurBundle(): EditorRenderBundle = EditorRenderBundle(
        strokes = listOf(
            StrokeLayer(
                type = StrokeType.BLUR,
                points = listOf(0.5f to 0.5f),
                brushSize = 0.4f,
                opacity = 1f
            )
        )
    )

    @Test
    fun `pinching past 100 percent after a blur commit republishes a full composite with the blur`() {
        // BUG (Full-size brush blur vanishes on pinch-zoom): everything below
        // happens past 100% zoom. Refresh #1 starts the preview fold and also
        // schedules a full fold WITH the tool EXCLUDED (the composite base under
        // the live blur overlay). The live-apply flip (excludeCurrentTool
        // true→false) arrives as refresh #2/#3 while that preview fold is still
        // running, so pre-fix #2/#3 early-returned and never re-issued the full
        // fold — the stale NO-blur full fold then published itself as current
        // AND stamped publishedBlurStrokesVersion (turning the overlay's
        // showLiveResult on), so the committed blur vanished from the preview.
        val file = twoToneBlurSource("blur_pinch_zoom.jpg")
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(600, 400)
        viewModel.selectTool(EditTool.BLUR_BRUSH)

        val bundle = blurBundle()
        // Three refreshes with NO looper idle between them: each subsequent one
        // lands while the fold from #1 is still running (compositeRunning).
        viewModel.refreshCompositePreview(
            bundle, includeTail = true, excludeCurrentTool = true,
            visibleWidthPx = 600, visibleHeightPx = 400, zoom = 1.2f, blurStrokesVersion = 3
        )
        viewModel.refreshCompositePreview(
            bundle, includeTail = true, excludeCurrentTool = true,
            visibleWidthPx = 600, visibleHeightPx = 400, zoom = 1.2f, blurStrokesVersion = 3
        )
        viewModel.refreshCompositePreview(
            bundle, includeTail = true, excludeCurrentTool = false,
            visibleWidthPx = 600, visibleHeightPx = 400, zoom = 1.2f, blurStrokesVersion = 3
        )

        val pristine = decodeBoundedOriented(context.contentResolver, Uri.fromFile(file))!!
        val composite = waitForFullComposite(pristine)
        assertNotNull("a full-size composite is published past 100% zoom", composite)
        // The published blur version equals the live strokes version here — the
        // value the overlay compares to decide showLiveResult — so the composite
        // MUST actually contain the strokes it claims to have folded.
        assertEquals(3, viewModel.uiState.value.publishedBlurStrokesVersion)
        assertTrue(
            "published full composite must contain the committed blur (pre-fix the " +
                "stale tool-excluded fold published the pristine base at the edge)",
            composite!!.getPixel(955, 640) != pristine.getPixel(955, 640)
        )
        pristine.recycle()
    }

    @Test
    fun `the full composite published past 100 percent records the strokes version it folded`() {
        // Contract guard for requestFullFold's publish: the full-size composite
        // folded with blurStrokesVersion=5 must be published with exactly that
        // version — the value the overlay compares against the live strokes
        // version before hiding the committed strokes.
        val file = twoToneBlurSource("blur_full_version.jpg")
        viewModel.setImageUri(Uri.fromFile(file))
        viewModel.setViewBox(600, 400)
        viewModel.selectTool(EditTool.BLUR_BRUSH)

        viewModel.refreshCompositePreview(
            blurBundle(), includeTail = true, excludeCurrentTool = false,
            visibleWidthPx = 600, visibleHeightPx = 400, zoom = 1.2f, blurStrokesVersion = 5
        )

        val pristine = decodeBoundedOriented(context.contentResolver, Uri.fromFile(file))!!
        val composite = waitForFullComposite(pristine)
        assertNotNull("a full-size composite is published past 100% zoom", composite)
        assertEquals(5, viewModel.uiState.value.publishedBlurStrokesVersion)
        assertTrue(
            "full composite folded the blur strokes",
            composite!!.getPixel(955, 640) != pristine.getPixel(955, 640)
        )
        pristine.recycle()
    }
}