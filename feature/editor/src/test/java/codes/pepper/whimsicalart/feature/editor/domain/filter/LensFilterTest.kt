package codes.pepper.whimsicalart.feature.editor.domain.filter

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LensFilterTest {

    /** Pre-hoist star directions (mirrors LensFilterProcessor.starStreakPass). */
    private val starDirs = floatArrayOf(
        1f, 0f, -1f, 0f, 0f, 1f, 0f, -1f,
        0.70710677f, 0.70710677f, -0.70710677f, 0.70710677f,
        0.70710677f, -0.70710677f, -0.70710677f, -0.70710677f
    )

    /**
     * The pre-hoist star/anamorphic streak pipeline: per-pixel roundToInt
     * sampling offsets and per-pixel falloff re-derivation (as before the
     * offX/offY/falloffW hoisting). Byte-parity control for the batched pass.
     */
    private fun streakRef(source: Bitmap, directions: FloatArray, steps: Int, stepPx: Float, intensity: Float): Bitmap {
        val w = source.width
        val h = source.height
        val n = w * h
        val px = IntArray(n)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(n) { i -> greyRef(Color.red(px[i]), Color.green(px[i]), Color.blue(px[i])) }
        val highlight = FloatArray(n) {
            val t = ((lum[it] - 180f) / 75f).coerceIn(0f, 1f)
            t * t * (3f - 2f * t)
        }
        val out = px.clone()
        for (i in 0 until n) {
            var accum = 0f
            val x = i % w
            val y = i / w
            var d = 0
            while (d < directions.size) {
                val dx = directions[d]
                val dy = directions[d + 1]
                var k = 1
                while (k <= steps) {
                    val sx = (x + dx * k * stepPx).roundToInt()
                    val sy = (y + dy * k * stepPx).roundToInt()
                    if (sx in 0 until w && sy in 0 until h) {
                        val t = k.toFloat() / steps
                        val v = 1f - t
                        accum += highlight[sy * w + sx] * (v * v)
                    }
                    k++
                }
                d += 2
            }
            if (accum <= 0f) continue
            val boost = accum * intensity * 3f
            val r = Color.red(px[i])
            val g = Color.green(px[i])
            val b = Color.blue(px[i])
            out[i] = Color.argb(
                Color.alpha(px[i]),
                clipRef(r * (1f + boost * r / 255f)),
                clipRef(g * (1f + boost * g / 255f)),
                clipRef(b * (1f + boost * b / 255f))
            )
        }
        return toBitmapRef(w, h, out)
    }

    /** The pre-hoist astro pipeline — per-sample max(0f, lum-120) and decay.pow(s). */
    private fun astroRef(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val n = w * h
        val px = IntArray(n)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(n)
        var maxLum = 0f
        var originIdx = n / 2
        for (i in 0 until n) {
            lum[i] = greyRef(Color.red(px[i]), Color.green(px[i]), Color.blue(px[i]))
            if (lum[i] > maxLum) {
                maxLum = lum[i]
                originIdx = i
            }
        }
        val originX = originIdx % w
        val originY = originIdx / w
        val out = px.clone()
        for (i in 0 until n) {
            val x = i % w
            val y = i / w
            val dx = originX - x
            val dy = originY - y
            val distSq = dx * dx + dy * dy
            if (distSq <= 0.001f) continue
            val dist = sqrt(distSq.toFloat())
            val dirX = dx / dist
            val dirY = dy / dist
            var accumulated = 0f
            var s = 1
            while (s <= 6) {
                val sx = (x + dirX * s * 3f).roundToInt()
                val sy = (y + dirY * s * 3f).roundToInt()
                if (sx in 0 until w && sy in 0 until h) {
                    accumulated += max(0f, lum[sy * w + sx] - 120f) * 0.9f.pow(s)
                }
                s++
            }
            if (accumulated <= 0f) continue
            val boost = accumulated / 255f * 0.6f
            val r = Color.red(px[i])
            val g = Color.green(px[i])
            val b = Color.blue(px[i])
            out[i] = Color.argb(
                Color.alpha(px[i]),
                clipRef(r + (255f - r) * boost),
                clipRef(g + (255f - g) * boost),
                clipRef(b + (255f - b) * boost)
            )
        }
        return toBitmapRef(w, h, out)
    }

    private fun greyRef(r: Int, g: Int, b: Int): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun clipRef(v: Float): Int = v.roundToInt().coerceIn(0, 255)

    private fun toBitmapRef(w: Int, h: Int, out: IntArray): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, w, 0, 0, w, h) }

    /** A highlight-rich pattern with scattered sources at many offsets/corners. */
    private fun highlightPattern(w: Int, h: Int): Bitmap {
        val input = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(70, 60, 50))
        val highlights = listOf(
            2 to 2, w - 3 to h - 3, w / 2 to h / 2, w / 4 to 3 * h / 4, 3 * w / 4 to h / 4, w - 2 to 2
        )
        for ((x, y) in highlights) {
            input.setPixel(x, y, Color.WHITE)
            input.setPixel((x + 1).coerceAtMost(w - 1), y, Color.rgb(220, 220, 220))
        }
        return input
    }

    /** Byte-compares two bitmaps, pointing at the first differing pixel. */
    private fun assertBitmapsEqual(expected: Bitmap, actual: Bitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val e = expected.getPixel(x, y)
                val a = actual.getPixel(x, y)
                assertEquals("pixel ($x,$y)", e, a)
            }
        }
    }

    private fun solid(w: Int, h: Int, color: Int): Bitmap {
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }
    }

    @Test
    fun `no lens passes returns the source untouched`() {
        val input = solid(8, 8, Color.rgb(50, 120, 200))
        val out = LensFilterProcessor.applyLensPasses(input, emptySet())
        assertSame("empty pass set must not allocate", input, out)
        assertEquals("source must stay intact", input.getPixel(2, 2), out.getPixel(2, 2))
    }

    @Test
    fun `unknown lens id is an identity copy that leaves the source intact`() {
        val input = solid(6, 6, Color.rgb(90, 140, 30))
        val out = LensFilterProcessor.apply(input, "no_such_lens")
        assertNotEquals("identity pass returns a fresh bitmap", input, out)
        assertEquals(input.getPixel(3, 3), out.getPixel(3, 3))
        assertEquals("source must stay intact", input.getPixel(3, 3), Color.rgb(90, 140, 30))
        out.recycle()
    }

    @Test
    fun `red-eye pass desaturates a bright pupil without touching neutral pixels`() {
        val input = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(128, 128, 128))
        // A flash-lit pupil: bright red channel with both G and B far below.
        for (y in 6..9) for (x in 6..9) input.setPixel(x, y, Color.rgb(220, 40, 30))
        val out = LensFilterProcessor.redEyePass(input)
        val pupil = out.getPixel(7, 7)
        val neutral = out.getPixel(2, 2)
        assertTrue(
            "pupil red channel must drop below 110, got ${Color.red(pupil)}",
            Color.red(pupil) < 110
        )
        assertEquals("neutral pixels must stay unchanged (128,128,128)", Color.rgb(128, 128, 128), neutral)
        assertEquals(
            "green and blue channels of the pupil stay put",
            Color.green(input.getPixel(7, 7)),
            Color.green(pupil)
        )
    }

    @Test
    fun `red-eye pass ignores red pixels whose blue channel disqualifies the mask`() {
        val input = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        // A reddish sunset pixel: R high but B not far enough below R.
        input.eraseColor(Color.rgb(220, 120, 90))
        val out = LensFilterProcessor.redEyePass(input)
        assertEquals(
            "reddish-but-not-pupil pixels must stay unchanged",
            Color.rgb(220, 120, 90),
            out.getPixel(4, 4)
        )
    }

    @Test
    fun `star streak brightens axial neighbours and leaves off-axis pixels alone`() {
        val input = Bitmap.createBitmap(17, 17, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.BLACK)
        input.setPixel(8, 8, Color.WHITE)
        // Axial neighbour two pixels below the highlight, and an off-axis pixel
        // at a similar distance.
        val before = Color.rgb(200, 200, 200)
        input.setPixel(8, 10, before)
        input.setPixel(10, 9, before)
        val out = LensFilterProcessor.starStreakPass(input)
        assertTrue(
            "axial neighbour must brighten (was 200, got ${Color.red(out.getPixel(8, 10))})",
            Color.red(out.getPixel(8, 10)) > 250
        )
        assertEquals(
            "off-axis pixel must stay unchanged",
            before,
            out.getPixel(10, 9)
        )
    }

    @Test
    fun `anamorphic streak spreads light horizontally but not vertically`() {
        val input = Bitmap.createBitmap(17, 17, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.BLACK)
        input.setPixel(8, 8, Color.WHITE)
        val before = Color.rgb(200, 200, 200)
        input.setPixel(10, 8, before) // horizontal neighbour → on the streak.
        input.setPixel(8, 10, before) // vertical neighbour → off the streak.
        val out = LensFilterProcessor.anamorphicStreakPass(input)
        assertTrue(
            "horizontal neighbour must brighten",
            Color.red(out.getPixel(10, 8)) > Color.red(before)
        )
        assertEquals(
            "vertical neighbour must stay unchanged",
            before,
            out.getPixel(8, 10)
        )
    }

    @Test
    fun `astro god-rays brightens pixels on the ray from the brightest source`() {
        val input = Bitmap.createBitmap(17, 17, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.BLACK)
        input.setPixel(8, 8, Color.WHITE)
        val out = LensFilterProcessor.astroGodRaysPass(input)
        val axial = out.getPixel(8, 11) // three pixels below the source.
        val diagonal = out.getPixel(11, 11) // diagonal, similar proximity.
        assertTrue(
            "axial pixel on the ray must gain scattered light, got ${Color.red(axial)}",
            Color.red(axial) > 10
        )
        assertEquals(
            "diagonal off-ray pixel must stay dark",
            Color.BLACK,
            diagonal
        )
    }

    @Test
    fun `a dark uniform image gains no god rays`() {
        val input = solid(12, 12, Color.rgb(30, 30, 30))
        val out = LensFilterProcessor.astroGodRaysPass(input)
        assertEquals("no bright source → no scattered light", input.getPixel(6, 6), out.getPixel(6, 6))
    }

    @Test
    fun `multiple passes apply in sequence and differ from each single pass`() {
        val input = Bitmap.createBitmap(17, 17, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(40, 40, 40))
        input.setPixel(8, 8, Color.WHITE)            // highlight for the streak.
        input.setPixel(13, 4, Color.rgb(220, 40, 30)) // pupil for red-eye.
        val combined = LensFilterProcessor.applyLensPasses(input, setOf("red_eye", "star"))
        val starOnly = LensFilterProcessor.starStreakPass(input)
        assertTrue(
            "red-eye must have desaturated the pupil in the combined pass, " +
                "got ${Color.red(combined.getPixel(13, 4))}",
            Color.red(combined.getPixel(13, 4)) < 110
        )
        assertTrue(
            "star alone must leave the pupil red",
            Color.red(starOnly.getPixel(13, 4)) > 200
        )
    }

    @Test
    fun `alpha is preserved by every pass`() {
        val alpha = 160
        val input = Bitmap.createBitmap(17, 17, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.argb(alpha, 120, 120, 120))
        input.setPixel(8, 8, Color.argb(alpha, 255, 255, 255))
        assertEquals(alpha, Color.alpha(LensFilterProcessor.redEyePass(input).getPixel(8, 8)))
        assertEquals(alpha, Color.alpha(LensFilterProcessor.starStreakPass(input).getPixel(8, 8)))
        assertEquals(alpha, Color.alpha(LensFilterProcessor.anamorphicStreakPass(input).getPixel(8, 8)))
        assertEquals(alpha, Color.alpha(LensFilterProcessor.astroGodRaysPass(input).getPixel(8, 8)))
    }

    @Test
    fun `streak passes are byte-identical to the per-pixel reference`() {
        // The hoisting precomputes integer offsets (roundToInt(dx·k·stepPx)) and
        // falloff weights once; every gather must reproduce the per-pixel round
        // exactly. Corner/triscattered highlights stress many offset/boundary
        // combinations (the star diagonals are the non-integer-offset case).
        val w = 29
        val h = 31
        val input = highlightPattern(w, h)
        assertBitmapsEqual(
            streakRef(input, starDirs, steps = 6, stepPx = 2f, intensity = 0.32f),
            LensFilterProcessor.starStreakPass(input)
        )
        assertBitmapsEqual(
            streakRef(input, floatArrayOf(1f, 0f, -1f, 0f), steps = 12, stepPx = 2f, intensity = 0.5f),
            LensFilterProcessor.anamorphicStreakPass(input)
        )
    }

    @Test
    fun `astro god-rays pass is byte-identical to the per-pixel reference`() {
        // The hoisting precomputes per-step decay powers and per-pixel excess
        // over the highlight threshold; the gather math must stay byte-equal.
        val w = 29
        val h = 31
        val input = highlightPattern(w, h)
        assertBitmapsEqual(astroRef(input), LensFilterProcessor.astroGodRaysPass(input))
    }
}