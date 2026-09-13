package codes.pepper.whimsicalart.feature.editor.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The sticker + transform-rotation renderer contract (docs/effect_stack.md →
 * *Positional layers — anchored reference base, centre-origin, uniform fit* →
 * *Sticker/text + transform rotation*). A positional layer rasterises against
 * its stored reference base and is composited flat; a Transform ABOVE rotates
 * that whole composite around the centre, so the sticker keeps its offset from
 * centre and is never re-rendered against the post-rotation canvas. With the
 * transform BELOW, the stored reference must be the layer's ACTUAL own-position
 * base footprint — a stale (double-counted) reference re-places the object
 * off-centre.
 *
 * The sticker is a SOLID-BLUE photo (content URI) on a solid-red base: the only
 * non-red opaque pixels in the composite are the sticker's, so their centroid is
 * the sticker's rendered centre.
 *
 * Runs under NATIVE graphics (like [CropMountRendererTest]): Robolectric's
 * LEGACY-graphics Matrix.mapRect maps only two corners, which corrupts the
 * free-angle bounding-box sizing in [CropMountRenderer]; the real Android
 * graphics stack renders the free-angle path faithfully.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StickerTransformRotationTest {

    private lateinit var context: Context
    private lateinit var blueUri: Uri

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val art = File(context.cacheDir, "sticker_transform_art.png")
        val solid = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        FileOutputStream(art).use { out -> solid.compress(Bitmap.CompressFormat.PNG, 100, out) }
        solid.recycle()
        blueUri = Uri.fromFile(art)
    }

    private fun base(): Bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.RED)
    }

    private fun sticker(
        x: Float, y: Float,
        refW: Int, refH: Int
    ) = codes.pepper.whimsicalart.feature.stickers.domain.StickerItem(
        stickerId = "s1",
        imageUri = blueUri.toString(),
        x = x, y = y,
        widthFrac = 0.2f, heightFrac = 0.2f,
        referenceWidth = refW, referenceHeight = refH
    )

    private fun stickerCentre(out: Bitmap): Pair<Float, Float> {
        var sumX = 0.0
        var sumY = 0.0
        var count = 0
        for (y in 0 until out.height) {
            for (x in 0 until out.width) {
                val px = out.getPixel(x, y)
                if (Color.alpha(px) > 0 && Color.red(px) < 0x80) {
                    sumX += x
                    sumY += y
                    count++
                }
            }
        }
        assertTrue("sticker pixels must be rendered (found $count)", count > 0)
        return (sumX / count).toFloat() to (sumY / count).toFloat()
    }

    @Test
    fun `sticker keeps its offset from centre when a free-rotate sits above it`() {
        // The concrete example from docs/effect_stack.md: a sticker at (0.75, 0.5)
        // of its 960×640 reference base with a 45° free-rotate ABOVE. The fold
        // rasterises the sticker onto the pre-rotation base and rotates the whole
        // composite around the (565.5, 565.5) canvas centre, so the sticker lands
        // at centre + rotated offset ((720-480)·cos45, (720-480)·sin45) = (735, 735)
        // — NOT at the re-rendered fraction-of-the-post-rotation-canvas spot
        // (0.75·1131, 0.5·1131) = (848, 565).
        val layered = base()
        // StickerOverlayRenderer.drawStickers draws onto the given bitmap.
        StickerOverlayRenderer.drawStickers(
            context,
            layered,
            listOf(sticker(x = 0.75f, y = 0.5f, refW = 960, refH = 640))
        )
        val out = CropMountRenderer.transforms(layered, 45f, false, false)
        assertEquals("rotated footprint W", 1131, out.width)
        assertEquals("rotated footprint H", 1131, out.height)
        val (cx, cy) = stickerCentre(out)
        assertEquals("sticker centre x", 735f, cx, 20f)
        assertEquals("sticker centre y", 735f, cy, 20f)
    }

    @Test
    fun `sticker glued to its placed spot when the transform sits below with its true base reference`() {
        // The [Transform(90), Sticker] fold order: the 90° rotation first produces
        // the sticker's own 640×960 base; with the TRUE reference the placement
        // (0.75, 0.25) of that base renders at (0.75·640, 0.25·960) = (480, 240).
        val rotated = CropMountRenderer.transforms(base(), 90f, false, false)
        StickerOverlayRenderer.drawStickers(
            context,
            rotated,
            listOf(sticker(x = 0.75f, y = 0.25f, refW = 640, refH = 960))
        )
        val (cx, cy) = stickerCentre(rotated)
        assertEquals("sticker centre x", 480f, cx, 20f)
        assertEquals("sticker centre y", 240f, cy, 20f)
    }

    @Test
    fun `a stale double-swapped reference re-places the sticker off-centre`() {
        // The capture defect the fix removes: starting the reference fold from the
        // already-rotated footprint double-swaps the 90° base rotation, so the
        // stored 960×640 reference no longer matches the 640×960 base the layer
        // folds over. The uniform fit then re-places the same (0.75, 0.25)
        // placement at (480, 373) instead of (480, 240) — the off-centre sticker.
        val rotated = CropMountRenderer.transforms(base(), 90f, false, false)
        StickerOverlayRenderer.drawStickers(
            context,
            rotated,
            listOf(sticker(x = 0.75f, y = 0.25f, refW = 960, refH = 640))
        )
        val (cx, cy) = stickerCentre(rotated)
        assertEquals("sticker centre x", 480f, cx, 20f)
        assertEquals("stale reference re-places the sticker off-centre", 373f, cy, 20f)
    }
}