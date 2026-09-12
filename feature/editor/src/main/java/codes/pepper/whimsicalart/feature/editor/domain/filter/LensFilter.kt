package codes.pepper.whimsicalart.feature.editor.domain.filter

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Real pixel passes for lens filters that cannot be expressed as a colour
 * matrix ([codes.pepper.whimsicalart.feature.filters.domain.FilterPresets.realPassLensFilterIds]):
 * red-eye removal and highlight-streak effects (star, anamorphic, astro).
 *
 * These run entirely in software on the CPU so they are JVM-testable under
 * Robolectric, mirroring the approach of [StyleFilterProcessor]. They are applied
 * through the committed fold ([codes.pepper.whimsicalart.feature.editor.domain.StackEffect])
 * and surface to the user through the composite preview, exactly like style
 * filters; the draw-time preview colour filter cannot represent them.
 *
 * Every pass returns a new bitmap and leaves [source] untouched.
 */
object LensFilterProcessor {

    /** Canonical application order for the real-pass lens filters. */
    val canonicalOrder = listOf("red_eye", "star", "anamorphic", "astro")

    /**
     * Applies every enabled real-pass lens filter in [lensPassIds] in canonical
     * order. Returns a new bitmap; [source] is never recycled or modified.
     */
    fun applyLensPasses(source: Bitmap, lensPassIds: Collection<String>): Bitmap {
        if (lensPassIds.isEmpty()) return source
        var result = source
        for (id in canonicalOrder) {
            if (id !in lensPassIds) continue
            val next = apply(result, id)
            if (next !== result && result !== source && !result.isRecycled) result.recycle()
            result = next
        }
        return result
    }

    fun apply(source: Bitmap, lensPassId: String): Bitmap {
        return when (lensPassId) {
            "red_eye" -> redEyePass(source)
            "star" -> starStreakPass(source)
            "anamorphic" -> anamorphicStreakPass(source)
            "astro" -> astroGodRaysPass(source)
            else -> source.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * Heuristic red-eye removal: bright reddish pixels (a bright R channel with
     * both G and B far below it, typical of a flash-lit pupil) are desaturated
     * back toward their luminance, leaving the rest of the image untouched.
     * There is no face/eye detection here; the mask is purely colour-based,
     * which keeps it a cheap, deterministic, Robolectric-safe pass.
     */
    fun redEyePass(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            val a = Color.alpha(p)
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val maxGB = max(g, b)
            val redness = r - maxGB
            val isRedEye = r >= 120 && r <= 245 &&
                redness >= 55 &&
                maxGB <= r * 0.55f &&
                g + b <= r * 0.9f
            out[i] = if (isRedEye) {
                val lum = grey(r, g, b)
                val amount = min(1f, redness / 110f)
                val nr = lum + (1f - amount) * (r - lum)
                Color.argb(a, clip(nr), g, b)
            } else {
                p
            }
        }
        return toBitmap(w, h, out)
    }

    /**
     * Star effect: KinoStreak-style streak spikes radiating from luminance
     * highlights in four directions (the two axes plus both diagonals).
     */
    fun starStreakPass(source: Bitmap): Bitmap {
        return streakPass(
            source,
            directions = floatArrayOf(
                1f, 0f, -1f, 0f, 0f, 1f, 0f, -1f,
                0.70710677f, 0.70710677f, -0.70710677f, 0.70710677f,
                0.70710677f, -0.70710677f, -0.70710677f, -0.70710677f
            ),
            steps = 6,
            stepPx = 2f,
            intensity = 0.32f
        )
    }

    /**
     * Anamorphic effect: a longer horizontal slit-scan streak (single axis)
     * that only spreads light horizontally from highlights.
     */
    fun anamorphicStreakPass(source: Bitmap): Bitmap {
        return streakPass(
            source,
            directions = floatArrayOf(1f, 0f, -1f, 0f),
            steps = 12,
            stepPx = 2f,
            intensity = 0.5f
        )
    }

    private fun streakPass(
        source: Bitmap,
        directions: FloatArray,
        steps: Int,
        stepPx: Float,
        intensity: Float
    ): Bitmap {
        val w = source.width
        val h = source.height
        val n = w * h
        val px = IntArray(n)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(n)
        for (i in 0 until n) {
            lum[i] = grey(Color.red(px[i]), Color.green(px[i]), Color.blue(px[i]))
        }
        val highlight = FloatArray(n)
        for (i in 0 until n) {
            // Smoothstep-ish: only near-specular luminance creates streaks.
            val t = ((lum[i] - 180f) / 75f).coerceIn(0f, 1f)
            highlight[i] = t * t * (3f - 2f * t)
        }
        // Precompute the integer stepping offsets and per-step falloff weights
        // once rather than re-deriving them per pixel: the offset is
        // roundToInt(dx·k·stepPx) and floats are IEEE-deterministic, so the
        // accumulated gather is bit-exact to the old per-pixel round.
        val dirCount = directions.size / 2
        val offX = Array(dirCount) { IntArray(steps + 1) }
        val offY = Array(dirCount) { IntArray(steps + 1) }
        for (dir in 0 until dirCount) {
            val dx = directions[2 * dir]
            val dy = directions[2 * dir + 1]
            for (k in 1..steps) {
                offX[dir][k] = (dx * k * stepPx).roundToInt()
                offY[dir][k] = (dy * k * stepPx).roundToInt()
            }
        }
        val falloffW = FloatArray(steps + 1) { k ->
            if (k == 0) 0f
            else {
                val t = k.toFloat() / steps
                val v = 1f - t
                v * v
            }
        }
        val out = px.clone()
        for (i in 0 until n) {
            val strength = streakStrength(i, w, h, highlight, offX, offY, dirCount, falloffW)
            if (strength <= 0f) continue
            val r = Color.red(px[i])
            val g = Color.green(px[i])
            val b = Color.blue(px[i])
            val boost = strength * intensity * 3f
            val nr = clip(r * (1f + boost * r / 255f))
            val ng = clip(g * (1f + boost * g / 255f))
            val nb = clip(b * (1f + boost * b / 255f))
            out[i] = Color.argb(Color.alpha(px[i]), nr, ng, nb)
        }
        return toBitmap(w, h, out)
    }

    private fun streakStrength(
        i: Int,
        w: Int,
        h: Int,
        highlight: FloatArray,
        offX: Array<IntArray>,
        offY: Array<IntArray>,
        dirCount: Int,
        falloffW: FloatArray
    ): Float {
        val x = i % w
        val y = i / w
        var accum = 0f
        for (dir in 0 until dirCount) {
            val ox = offX[dir]
            val oy = offY[dir]
            var k = 1
            while (k < ox.size) {
                val sx = x + ox[k]
                val sy = y + oy[k]
                if (sx in 0 until w && sy in 0 until h) {
                    accum += highlight[sy * w + sx] * falloffW[k]
                }
                k++
            }
        }
        return accum
    }

    /**
     * Astro/god-rays effect: find the brightest highlight, then add scattered
     * light along rays running toward that origin (classic volumetric
     * light-scattering approximation, as seen in glsl-godrays).
     */
    fun astroGodRaysPass(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val n = w * h
        val px = IntArray(n)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(n)
        var maxLum = 0f
        var originIdx = n / 2
        for (i in 0 until n) {
            lum[i] = grey(Color.red(px[i]), Color.green(px[i]), Color.blue(px[i]))
            if (lum[i] > maxLum) {
                maxLum = lum[i]
                originIdx = i
            }
        }
        val originX = originIdx % w
        val originY = originIdx / w
        val out = px.clone()
        val steps = 6
        val stepPx = 3f
        val decay = 0.9f
        val exposure = 0.6f
        // Precompute per-step decay powers and per-pixel excess over the
        // highlight threshold — both copy the old expressions exactly so
        // the gather is bit-equal to the old path.
        val decayPow = FloatArray(steps + 1) { s -> if (s == 0) 0f else decay.pow(s) }
        val excess = FloatArray(n) { i -> max(0f, lum[i] - 120f) }
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
            while (s <= steps) {
                val sx = (x + dirX * s * stepPx).roundToInt()
                val sy = (y + dirY * s * stepPx).roundToInt()
                if (sx in 0 until w && sy in 0 until h) {
                    accumulated += excess[sy * w + sx] * decayPow[s]
                }
                s++
            }
            if (accumulated <= 0f) continue
            val boost = accumulated / 255f * exposure
            val r = Color.red(px[i])
            val g = Color.green(px[i])
            val b = Color.blue(px[i])
            val nr = clip(r + (255f - r) * boost)
            val ng = clip(g + (255f - g) * boost)
            val nb = clip(b + (255f - b) * boost)
            out[i] = Color.argb(Color.alpha(px[i]), nr, ng, nb)
        }
        return toBitmap(w, h, out)
    }

    private fun toBitmap(w: Int, h: Int, out: IntArray): Bitmap {
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun grey(r: Int, g: Int, b: Int): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun clip(v: Float): Int = v.roundToInt().coerceIn(0, 255)
}