package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
/** Pure PP-OCR pipeline math: resizing rules, NCHW packing, DB geometry, CTC. */
class PpOcrTest {

    // ------------------------------------------------------------------ resize

    @Test
    fun `det resize keeps aspect and caps the longest side at 960 rounded to 32`() {
        val spec = PpOcr.detResize(4000, 3000)
        assertEquals(960, spec.width)
        assertEquals(704, spec.height) // 3000 * (960/4000) = 720 -> 720/32 floor *32 = 704
    }

    @Test
    fun `det resize keeps the ratio for a small image but still rounds to 32`() {
        val spec = PpOcr.detResize(320, 240)
        assertEquals(320, spec.width)
        assertEquals(224, spec.height) // 240/32 floor *32 = 224
    }

    @Test
    fun `rec resize fixes height 48 and pads width to a multiple of 4 capped at 320`() {
        val spec = PpOcr.recResize(400, 100)
        assertEquals(48, spec.height)
        assertEquals(192, spec.width) // 400*48/100=192
    }

    @Test
    fun `rec width never exceeds the 320 cap`() {
        val spec = PpOcr.recResize(2000, 40)
        assertEquals(48, spec.height)
        assertEquals(320, spec.width)
    }

    // -------------------------------------------------------------- NCHW pack

    @Test
    fun `nchw packs rgb planes in order and normalises to zero one`() {
        // 2x1 image: pure red, pure green.
        val px = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val out = PpOcr.toNchwRgb(px, 2, 1, 2, 1)
        assertEquals(6, out.size)
        assertEquals(1f, out[0], 1e-5f)   // red plane px0
        assertEquals(0f, out[1], 1e-5f)   // red plane px1
        assertEquals(0f, out[2], 1e-5f)   // green plane px0
        assertEquals(1f, out[3], 1e-5f)   // green plane px1
        assertEquals(0f, out[4], 1e-5f)   // blue plane px0
        assertEquals(0f, out[5], 1e-5f)   // blue plane px1
    }

    @Test
    fun `nchw downscale averages bilinearly`() {
        // 2x2 image: top 255, bottom 0 → 1x1 average is 0.5.
        val px = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt())
        val out = PpOcr.toNchwRgb(px, 2, 2, 1, 1)
        assertEquals(0.5f, out[0], 2e-2f)
    }

    // --------------------------------------------------------------------- DB

    @Test
    fun `db finds one axis-aligned box above threshold and inflates it`() {
        val w = 32
        val h = 32
        val probs = FloatArray(w * h)
        for (y in 10 until 22) for (x in 8 until 24) probs[y * w + x] = 0.8f
        val boxes = PpOcr.findTextBoxes(probs, w, h)
        assertEquals(1, boxes.size)
        val bb = boxes[0].boundingBox()
        assertTrue("inflated above the mask", bb.width() > 16f)
        assertTrue("inflated above the mask", bb.height() > 12f)
    }

    @Test
    fun `db skips tiny noise components`() {
        val w = 32
        val h = 32
        val probs = FloatArray(w * h)
        probs[5 * w + 5] = 0.9f // single pixel noise
        probs[6 * w + 5] = 0.9f
        assertEquals(0, PpOcr.findTextBoxes(probs, w, h).size)
    }

    @Test
    fun `db separates two distinct boxes`() {
        val w = 48
        val h = 24
        val probs = FloatArray(w * h)
        for (x in 2 until 18) { probs[4 * w + x] = 0.9f; probs[5 * w + x] = 0.9f }
        for (x in 30 until 46) { probs[18 * w + x] = 0.9f; probs[19 * w + x] = 0.9f }
        assertEquals(2, PpOcr.findTextBoxes(probs, w, h).size)
    }

    @Test
    fun `min area rect and box corners give a centred axis-aligned box for a rectangle`() {
        val rect = PpOcr.minAreaRect(
            listOf(
                PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 6f), PointF(0f, 6f)
            )
        )
        assertEquals(10f, rect.width, 1e-3f)
        assertEquals(6f, rect.height, 1e-3f)
        assertEquals(5f, rect.centerX, 1e-3f)
        assertEquals(3f, rect.centerY, 1e-3f)
        val corners = PpOcr.boxCorners(rect).corners
        assertEquals(4, corners.size)
        assertTrue("box is cyclic/all four corners present", corners.distinctBy { p -> (p.x).toInt() to (p.y).toInt() }.size == 4)
    }

    // --------------------------------------------------------------------- CTC

    @Test
    fun `ctc collapses repeats and drops blanks`() {
        val vocab = PpOcr.vocabWithBlank(listOf("a", "b", "c"))
        val probs = floatArrayOf(
            // t0 blank, t1 'a', t2 'a' (repeat), t3 'b', t4 blank
            0f, 0f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
            1f, 0f, 0f
        )
        val decoded = PpOcr.ctcDecode(vocab, probs, timesteps = 5, classes = 3)
        assertEquals("ab", decoded.text)
    }

    @Test
    fun `ctc confidence is the mean top probability`() {
        val vocab = PpOcr.vocabWithBlank(listOf("a"))
        val probs = floatArrayOf(
            0f, 1f,
            0.5f, 0.5f
        )
        val decoded = PpOcr.ctcDecode(vocab, probs, timesteps = 2, classes = 2)
        assertEquals("a", decoded.text)
        assertEquals(0.75f, decoded.confidence, 1e-4f)
    }

    @Test
    fun `cls rotation maps class index one to 180 degrees`() {
        assertEquals(0, PpOcr.rotationDegreesFromCls(floatArrayOf(0.9f, 0.1f), 2))
        assertEquals(180, PpOcr.rotationDegreesFromCls(floatArrayOf(0.2f, 0.8f), 2))
    }
}