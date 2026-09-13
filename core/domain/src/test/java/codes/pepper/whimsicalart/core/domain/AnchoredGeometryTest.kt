package codes.pepper.whimsicalart.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchoredGeometryTest {

    private fun assertF(expected: Float, actual: Float, eps: Float = 0.0001f) {
        assertEquals(expected, actual, eps)
    }

    @Test
    fun `uniform fit maps a reference into a smaller same-aspect destination at scale one over both axes`() {
        val fit = UniformFit.of(destinationWidth = 600, destinationHeight = 400, referenceWidth = 1200, referenceHeight = 800)
        assertF(0.5f, fit.scale)
        assertF(0f, fit.offsetX)
        assertF(0f, fit.offsetY)
        // A centre fraction maps to the centre of the destination.
        assertF(300f, fit.frameX(0.5f, 1200))
        assertF(200f, fit.frameY(0.5f, 800))
        // A width fraction scales uniformly.
        assertF(150f, fit.measureX(0.25f, 1200))
        assertF(100f, fit.measureY(0.25f, 800))
    }

    @Test
    fun `uniform fit centres a side-flipped reference inside a rotated destination`() {
        // Reference 1200x800 (landscape) into a 800x1200 (portrait) destination:
        // the long sides both scale to the short side, leaving vertical letterbox.
        val fit = UniformFit.of(destinationWidth = 800, destinationHeight = 1200, referenceWidth = 1200, referenceHeight = 800)
        assertF(0.6666667f, fit.scale)
        assertF(0f, fit.offsetX)
        assertF(333.3333f, fit.offsetY)
        // The reference's horizontal centre lands in the middle; its vertical
        // middle lands in the middle of the destination image.
        assertF(400f, fit.frameX(0.5f, 1200))
        assertF(600f, fit.frameY(0.5f, 800))
        // A half-width measure of the reference keeps its pixel size under fit.
        assertF(400f, fit.measureX(0.5f, 1200))
    }

    @Test
    fun `uniform fit into a non-proportional destination never distorts`() {
        val fit = UniformFit.of(destinationWidth = 1000, destinationHeight = 500, referenceWidth = 800, referenceHeight = 800)
        assertF(0.625f, fit.scale)
        // 800*0.625 = 500 wide, centred horizontally.
        assertF(250f, fit.offsetX)
        assertF(0f, fit.offsetY)
        assertF(250f + 800f * 0.625f, fit.frameX(1f, 800))
    }

    @Test
    fun `uniform fit is the identity when destination equals the reference`() {
        val fit = UniformFit.of(destinationWidth = 1200, destinationHeight = 800, referenceWidth = 1200, referenceHeight = 800)
        assertF(1f, fit.scale)
        assertF(0f, fit.offsetX)
        assertF(0f, fit.offsetY)
    }

    @Test
    fun `uniform fit is the identity when the reference is unset`() {
        val fit = UniformFit.of(destinationWidth = 900, destinationHeight = 600, referenceWidth = 0, referenceHeight = 0)
        assertEquals(UniformFit.IDENTITY, fit)
        // Identity resolves the fraction against the destination dims, preserving legacy math.
        assertF(450f, fit.frameX(0.5f, 900))
        assertF(225f, fit.measureX(0.25f, 900))
    }

    @Test
    fun `reAnchor re-expresses centred geometry against a side-flipped base without moving it`() {
        // A square-ish object anchored to a landscape (1200x800) frame stays put
        // when its base becomes portrait (800x1200): centre-in/centre-out.
        val r = reAnchor(
            x = 0.5f, y = 0.5f,
            widthFraction = 0.25f, heightFraction = 0.4f,
            oldAnchor = GeometryAnchor(1200, 800),
            newWidth = 800, newHeight = 1200
        )
        assertF(0.5f, r.x)
        assertF(0.5f, r.y)
        // On-image sizes: 0.25*1200=300px wide / 0.4*800=320px tall → uniform
        // fit 0.6667 into the swapped base → 200px / 213.33px.
        assertF(200f / 800f, r.widthFraction)
        assertF(320f * (0.6666667f) / 1200f, r.heightFraction)
        assertEquals(GeometryAnchor(800, 1200), r.anchor)
    }

    @Test
    fun `reAnchor moved off-centre geometry keeps its exact on-image pixel position`() {
        // Anchor at the top-left quadrant of a 1200x800 frame.
        val r = reAnchor(
            x = 0.25f, y = 0.25f,
            widthFraction = 0.1f, heightFraction = 0.1f,
            oldAnchor = GeometryAnchor(1200, 800),
            newWidth = 600, newHeight = 400
        )
        // Same-aspect downscale: fractions are unchanged, anchor adopts the new base.
        assertF(0.25f, r.x)
        assertF(0.25f, r.y)
        assertF(0.1f, r.widthFraction)
        assertF(0.1f, r.heightFraction)
        assertEquals(GeometryAnchor(600, 400), r.anchor)
    }

    @Test
    fun `reAnchor with an unset anchor keeps fractions and adopts the new base`() {
        val r = reAnchor(
            x = 0.6f, y = 0.7f,
            widthFraction = 0.2f, heightFraction = 0.3f,
            oldAnchor = GeometryAnchor(),
            newWidth = 640, newHeight = 480
        )
        assertF(0.6f, r.x)
        assertF(0.7f, r.y)
        assertF(0.2f, r.widthFraction)
        assertF(0.3f, r.heightFraction)
        assertEquals(GeometryAnchor(640, 480), r.anchor)
    }

    @Test
    fun `geometry anchor reports set only for positive dims`() {
        assertFalse(GeometryAnchor().isSet)
        assertFalse(GeometryAnchor(0, 800).isSet)
        assertFalse(GeometryAnchor(1200, 0).isSet)
        assertTrue(GeometryAnchor(1200, 800).isSet)
    }
}