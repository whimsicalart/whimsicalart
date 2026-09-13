package codes.pepper.whimsicalart.feature.editor.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The text-shadow negative-mask contract (docs/effects_reference.md → *Text* →
 * *The shadow is hollowed out of the glyph like the outline*): a font colour
 * with opacity below 100% must never show its shadow THROUGH the glyph — the
 * glyph fill shape is cleared out of the shadow layer, then the translucent fill
 * re-applies on top and reveals the image behind it. An opaque fill is
 * unchanged (its glyph hides whatever is drawn beneath it).
 *
 * Test strategy: render the SAME item (fill) three ways — opaque/no-shadow
 * (locates the exact glyph STROKE SHAPE, valleys excluded), translucent/no-shadow
 * (control), translucent/with-shadow (the fixed output). The glyph shape from
 * the opaque render is eroded a few pixels so anti-aliased edge pixels (where a
 * shadow overlap is ambiguous) are excluded. Invariants:
 *  - INSIDE the eroded glyph shape, translucent-with-shadow == translucent-no-shadow
 *    (the shadow is hollowed out; it never bleeds through the fill), and the
 *    glyph interior shows the GREEN backdrop through the fill (green dominates).
 *  - OUTSIDE the glyph shape the outputs differ: the shadow must still render.
 *  - For an OPAQUE fill, with-shadow == no-shadow inside the glyph (opaque path
 *    unchanged).
 * On the broken renderer the shadow attaches to the fill paint, so the
 * translucent-with-shadow interior darkens (gray) and differs from the no-shadow
 * output — both invariants fail.
 *
 * Runs under NATIVE graphics (like [StickerTransformRotationTest]): the LEGACY
 * stack does not rasterise drawText/shadows faithfully.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextShadowHollowTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TextOverlayRenderer.clearTextCache()
    }

    /** Solid GREEN backdrop — the colour we expect to see through a translucent glyph. */
    private fun backdrop(): Bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.GREEN)
    }

    private fun textItem(color: Int, shadow: TextShadow) = TextItem(
        id = "t1",
        text = "M",
        color = color,
        fontSize = 0.5f,
        x = 0.5f,
        y = 0.5f,
        shadow = shadow
    )

    private fun render(item: TextItem): Bitmap {
        TextOverlayRenderer.clearTextCache()
        return backdrop().also { TextOverlayRenderer.drawTexts(context, it, listOf(item)) }
    }

    /**
     * The exact opaque glyph shape: every pixel an OPAQUE render paints over the
     * flat backdrop. The opaque fill covers only the true stroke shape (the "M"
     * interior valleys stay GREEN — they are backdrop, not glyph, and must NOT be
     * counted as glyph-ink), so this shapes each glyph's coverage precisely.
     * The shape is then eroded by [erodePx] so anti-aliased edge pixels (where
     * an offset shadow overlap is ambiguous) are excluded from the
     * "must be identical" interior region.
     */
    private fun glyphShape(opaque: Bitmap, erodePx: Int = 5): Set<Pair<Int, Int>> {
        val raw = HashSet<Pair<Int, Int>>()
        for (y in 0 until opaque.height) {
            for (x in 0 until opaque.width) {
                if (opaque.getPixel(x, y) != Color.GREEN) raw.add(x to y)
            }
        }
        assertTrue("opaque render must produce glyph ink", raw.isNotEmpty())
        // Erode: a pixel survives only if every pixel in the (2*erode+1) window
        // around it is also ink.
        return raw.filterTo(LinkedHashSet()) { (x, y) ->
            (-erodePx..erodePx).all { dy ->
                (-erodePx..erodePx).all { dx ->
                    (x + dx to y + dy) in raw
                }
            }
        }.also { assertTrue("eroded glyph interior must not be empty", it.isNotEmpty()) }
    }

    @Test
    fun `translucent fill hides its shadow inside the glyph`() {
        // A translucent WHITE fill over a green backdrop: with the shadow hollowed
        // out, the glyph interior = white × 0.5 + GREEN × 0.5 → (128, 255, 128),
        // identical to the NO-shadow render of the same fill. The old code
        // attached the shadow to the fill paint, so the black shadow bled through
        // the translucent glyph and the interior turned gray — visibly different
        // from the no-shadow output.
        val fill = Color.argb(128, 255, 255, 255)
        val shadow = TextShadow(enabled = true, angleDegrees = 0f, distance = 0.02f, color = Color.BLACK)

        val withShadow = render(textItem(fill, shadow))
        val noShadow = render(textItem(fill, TextShadow()))
        val opaque = render(textItem(Color.RED, TextShadow()))
        val shape = glyphShape(opaque)

        var insideDiffs = 0
        for ((x, y) in shape) {
            if (withShadow.getPixel(x, y) != noShadow.getPixel(x, y)) insideDiffs++
        }
        assertEquals("shadow must be hollowed out of the glyph ink", 0, insideDiffs)

        // The shadow must still render somewhere OUTSIDE the glyph shape.
        val shadowDiffs = (0 until withShadow.height).sumOf { y ->
            (0 until withShadow.width).count { x ->
                (x to y) !in shape && withShadow.getPixel(x, y) != noShadow.getPixel(x, y)
            }
        }
        assertTrue("the shadow must still render OUTSIDE the glyph", shadowDiffs > 0)

        // And the glyph interior must actually be the translucent-fill blend over
        // the GREEN backdrop (not the darkened shadow-through-fill mix): averaged
        // over the glyph shape, green dominates red.
        var sumR = 0L
        var sumG = 0L
        for ((x, y) in shape) {
            sumR += Color.red(withShadow.getPixel(x, y))
            sumG += Color.green(withShadow.getPixel(x, y))
        }
        assertTrue(
            "glyph interior must show the green backdrop through the fill (avgR=${sumR / shape.size}, avgG=${sumG / shape.size})",
            sumG / shape.size > sumR / shape.size
        )
    }

    @Test
    fun `opaque fill keeps its shadow inside the glyph unchanged`() {
        // An opaque fill hides the shadow beneath it, so the fixed path must NOT
        // change opaque rendering: with-shadow and no-shadow opaque outputs are
        // identical INSIDE the glyph (the shadow only pokes out around it).
        val shadow = TextShadow(enabled = true, angleDegrees = 0f, distance = 0.02f, color = Color.BLACK)

        val withShadow = render(textItem(Color.RED, shadow))
        val noShadow = render(textItem(Color.RED, TextShadow()))
        val shape = glyphShape(noShadow)

        var insideDiffs = 0
        for ((x, y) in shape) {
            if (withShadow.getPixel(x, y) != noShadow.getPixel(x, y)) insideDiffs++
        }
        assertEquals("opaque glyph must keep its fill over the shadow", 0, insideDiffs)
    }
}