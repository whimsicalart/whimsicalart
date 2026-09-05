package codes.pepper.whimsicalart.feature.editor.domain.filter

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/**
 * A "learned-look" style filter applied as an optional enhancement layer inside
 * [codes.pepper.whimsicalart.feature.editor.domain.BitmapRenderer]'s render
 * pipeline, on top of the selected LUT/filter color matrix.
 *
 * The built-in presets are analytic per-pixel tone maps that emulate the look of
 * learned style-transfer models (filmic tones, vibrant HDR, matte/low-sat).
 * They run entirely in software so they are JVM-testable under Robolectric; a
 * quantized TFLite style-transfer / tone-map model can later supply the same
 * interface without touching the render path.
 */
enum class StyleFilter(
    val id: String,
    val displayName: String
) {
    FILMIC("filmic", "Filmic"),
    VIBRANT("vibrant", "Vibrant"),
    MATTE("matte", "Matte");

    companion object {
        fun fromId(id: String?): StyleFilter? = entries.firstOrNull { it.id == id }
    }
}

interface StyleFilterApplier {
    /** Applies the (possibly null-free) style map to a copy of [source]. */
    fun apply(source: Bitmap, filter: StyleFilter): Bitmap
}

/**
 * Default analytic implementation. The tone curves below are hand-tuned to
 * reproduce the per-channel behaviour of a learned classifier's output rather
 * than relying on a shipped model.
 */
object StyleFilterProcessor : StyleFilterApplier {

    override fun apply(source: Bitmap, filter: StyleFilter): Bitmap {
        val w = source.width
        val h = source.height
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in px.indices) {
            out[i] = map(px[i], filter)
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun map(p: Int, filter: StyleFilter): Int {
        val r = Color.red(p)
        val g = Color.green(p)
        val b = Color.blue(p)
        return when (filter) {
            StyleFilter.FILMIC -> {
                // Gentle S-curve: deepen blacks, warm highlights, cool shadows.
                val tr = curve(r * 0.90f + 8f) + 4
                val tg = curve(g * 0.96f) + 2
                val tb = curve(b * 0.88f + 6f) - 2
                Color.rgb(tr, tg, tb)
            }
            StyleFilter.VIBRANT -> {
                // Boost chroma and expand midtone contrast.
                val l = grey(r, g, b)
                val s = 1.22f
                val inv = 1f - s
                val r2 = (r * s + l * inv)
                val g2 = (g * s + l * inv)
                val b2 = (b * s + l * inv)
                val c = 1.06f
                val mid = 127.5f
                Color.rgb(
                    clip((r2 - mid) * c + mid),
                    clip((g2 - mid) * c + mid),
                    clip((b2 - mid) * c + mid)
                )
            }
            StyleFilter.MATTE -> {
                // Low-saturation, lifted shadows, soft contrast.
                val l = grey(r, g, b)
                val s = 0.8f
                val inv = 1f - s
                val r2 = (r * s + l * inv)
                val g2 = (g * s + l * inv)
                val b2 = (b * s + l * inv)
                val lift = 10f
                val c = 0.9f
                val mid = 127.5f
                Color.rgb(
                    clip((r2 - mid) * c + mid + lift),
                    clip((g2 - mid) * c + mid + lift),
                    clip((b2 - mid) * c + mid + lift)
                )
            }
        }
    }

    private fun curve(v: Float): Int = clip(255f / (1f + kotlin.math.exp(-(v - 127.5f) / 45f)))

    private fun grey(r: Int, g: Int, b: Int): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun clip(v: Float): Int = v.roundToInt().coerceIn(0, 255)
}