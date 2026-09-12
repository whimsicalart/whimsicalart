package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * Samples the dominant foreground ("ink") colour inside a detected OCR line
 * region so imported text can be tinted like the source (spec
 * `effects_reference.md` → OCR: "detected colour"). Pure bitmap math (no native
 * runtime), so it is exercised under Robolectric with crafted bitmaps.
 *
 * The region is expected to be a fairly clean text-line crop with a bimodal
 * foreground/background distribution. The smaller luma population (dark pixels
 * on a light background, or light pixels on a dark background) is treated as
 * the ink whenever the contrast gap and coverage are decisive; otherwise the
 * region is ambiguous (solid fill, colourful poster, too little text, ...) and
 * `null` is returned so the caller falls back to the text-tool default colour.
 */
object OcrInkSampler {

    private const val MIN_INK_FRACTION = 0.02f
    private const val MAX_INK_FRACTION = 0.5f
    private const val MIN_LUMA_GAP = 40f
    private const val MIN_SAMPLES = 16

    /** Pixel stride; text-line crops are small so 2 keeps this cheap without rejecting antialiased edges. */
    private const val STEP = 2

    /**
     * Returns the dominant ink colour (ARGB, opaque) inside [region] on [bitmap],
     * or `null` when no decisive foreground colour can be established.
     */
    fun sampleInkColor(bitmap: Bitmap, region: RectF): Int? {
        val left = region.left.coerceIn(0f, bitmap.width.toFloat() - 1f).roundToInt()
        val top = region.top.coerceIn(0f, bitmap.height.toFloat() - 1f).roundToInt()
        val right = region.right.coerceIn(0f, bitmap.width.toFloat() - 1f).roundToInt()
        val bottom = region.bottom.coerceIn(0f, bitmap.height.toFloat() - 1f).roundToInt()
        if (right < left || bottom < top) return null
        // One batched read of the region instead of a JNI getPixel per sample.
        val regionW = right - left + 1
        val regionH = bottom - top + 1
        val pixels = IntArray(regionW * regionH)
        bitmap.getPixels(pixels, 0, regionW, left, top, regionW, regionH)

        var darkR = 0
        var darkG = 0
        var darkB = 0
        var lightR = 0
        var lightG = 0
        var lightB = 0
        var darkCount = 0
        var lightCount = 0
        var sampled = 0

        var y = top
        while (y <= bottom) {
            val row = (y - top) * regionW
            var x = left
            while (x <= right) {
                val px = pixels[row + (x - left)]
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                if (luma < 96f) {
                    darkR += r; darkG += g; darkB += b
                    darkCount++
                } else if (luma > 160f) {
                    lightR += r; lightG += g; lightB += b
                    lightCount++
                }
                sampled++
                x += STEP
            }
            y += STEP
        }
        if (sampled < MIN_SAMPLES) return null

        var inkR = 0
        var inkG = 0
        var inkB = 0
        var bgR = 0
        var bgG = 0
        var bgB = 0
        var inkCount: Int
        var bgCount = 0
        if (darkCount <= lightCount) {
            // Foreground = dark ink, background = light surface.
            inkR = darkR; inkG = darkG; inkB = darkB
            inkCount = darkCount
            bgR = lightR; bgG = lightG; bgB = lightB
            bgCount = lightCount
        } else {
            // Inverted: foreground = light ink on a dark surface.
            inkR = lightR; inkG = lightG; inkB = lightB
            inkCount = lightCount
            bgR = darkR; bgG = darkG; bgB = darkB
            bgCount = darkCount
        }

        val classified = (darkCount + lightCount).toFloat()
        if (classified <= 0f) return null
        val inkFraction = inkCount / classified
        if (inkCount == 0 || bgCount == 0) return null
        if (inkFraction < MIN_INK_FRACTION || inkFraction > MAX_INK_FRACTION) return null

        val inkLuma = (0.299f * inkR + 0.587f * inkG + 0.114f * inkB) / inkCount
        val bgLuma = (0.299f * bgR + 0.587f * bgG + 0.114f * bgB) / bgCount
        if (kotlin.math.abs(bgLuma - inkLuma) < MIN_LUMA_GAP) return null

        return Color.rgb(
            (inkR / inkCount).coerceAtMost(255),
            (inkG / inkCount).coerceAtMost(255),
            (inkB / inkCount).coerceAtMost(255)
        )
    }
}