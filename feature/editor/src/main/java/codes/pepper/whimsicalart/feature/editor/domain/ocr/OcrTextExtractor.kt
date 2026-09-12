package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A recognized text line prepared for insertion as an editor text overlay:
 * the normalized text, a normalized anchor (0..1 across the source image), the
 * detected baseline rotation, a font-size fraction derived from the detected
 * line's *height* proportion of the image (spec: "not merely its width") and
 * the sampled ink colour when reliably detectable (`null` → caller uses the
 * text-tool default).
 */
data class OcrOverlayFragment(
    val text: String,
    val normalizedX: Float,
    val normalizedY: Float,
    val fontSizeFraction: Float,
    val rotationDegrees: Float = 0f,
    val colorArgb: Int? = null
)

/**
 * Pure, unit-testable transformation from raw on-device OCR output into
 * overlay-ready text fragments. No OCR engine is touched here, so every rule
 * below can be exercised under Robolectric without a native runtime:
 *  - drops blank / dashes-only / very short fragments (garbage or separators);
 *  - collapses internal whitespace and trims edges;
 *  - caps each fragment's length so an over-detected region can't flood the canvas;
 *  - anchors the overlay at the detected line's centre (the quad's centroid when
 *    a rotated quad is available, otherwise the axis-aligned box centre);
 *  - sizes the glyphs from the line's height (perpendicular to the text
 *    baseline for tilted quads) as a fraction of the source height;
 *  - carries the detected baseline rotation and, when the source bitmap is
 *    provided, the sampled ink colour (falling back to `null` when ambiguous).
 */
object OcrTextExtractor {

    const val MIN_FRAGMENT_LENGTH = 2
    const val MAX_FRAGMENT_LENGTH = 120
    private const val MIN_FONT_FRACTION = 0.02f
    private const val MAX_FONT_FRACTION = 0.2f
    private val DASHES_ONLY = Regex("^[\\s\\-—_|•*]+$")

    /** Normalizes a raw OCR token into insertion-friendly text ("" if junk). */
    fun normalizeToken(raw: String): String {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return ""
        if (trimmed.length < MIN_FRAGMENT_LENGTH) return ""
        if (DASHES_ONLY.matches(trimmed)) return ""
        if (trimmed.count { it.isLetterOrDigit() } < 1) return ""
        return trimmed.take(MAX_FRAGMENT_LENGTH)
    }

    /**
     * Converts recognized [lines] from [sourceWidth]x[sourceHeight] into overlay
     * fragments, dropping junk and computing normalized anchors, rotation, size
     * and colour (colour requires [sourceBitmap]; sampled only when non-null).
     * Result is sorted top-to-bottom (reading order) then left-to-right.
     */
    fun overlayFragments(
        lines: List<OcrLine>,
        sourceWidth: Int,
        sourceHeight: Int,
        sourceBitmap: Bitmap? = null
    ): List<OcrOverlayFragment> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return emptyList()

        val fragments = lines.mapNotNull { line ->
            val text = normalizeToken(line.text)
            if (text.isEmpty()) return@mapNotNull null

            val quad = line.quad?.takeIf { it.size >= 4 }
            val anchor: PointF
            val region: RectF
            val sizePx: Float
            val rotation: Float
            if (quad != null) {
                anchor = quadCentroid(quad)
                region = quadBounds(quad)
                sizePx = quadHeight(quad)
                rotation = quadBaselineAngleDegrees(quad)
            } else {
                val box = line.boundingBox
                if (box.width() <= 0f || box.height() <= 0f) return@mapNotNull null
                anchor = PointF(box.centerX(), box.centerY())
                region = box
                sizePx = box.height()
                rotation = line.rotationDegrees
            }
            if (sizePx <= 0f) return@mapNotNull null

            val centerX = (anchor.x / sourceWidth).coerceIn(0f, 1f)
            val centerY = (anchor.y / sourceHeight).coerceIn(0f, 1f)
            val fontSizeFraction = (sizePx / sourceHeight).coerceIn(MIN_FONT_FRACTION, MAX_FONT_FRACTION)
            val colorArgb = sourceBitmap?.let { OcrInkSampler.sampleInkColor(it, region) }
            OcrOverlayFragment(text, centerX, centerY, fontSizeFraction, rotation, colorArgb)
        }

        return fragments.sortedWith(
            compareBy(
                { (it.normalizedY * 1000).roundToInt() },
                { (it.normalizedX * 1000).roundToInt() }
            )
        )
    }

    /** Convenience total count (handy for UI messaging). */
    fun recognizedCount(lines: List<OcrLine>, sourceWidth: Int, sourceHeight: Int): Int =
        overlayFragments(lines, sourceWidth, sourceHeight).size

    /** Clamps a fraction, guarding against a zero/negative denominator. */
    fun clampFraction(value: Float, from: Float, to: Float): Float =
        value.coerceIn(min(from, to), maxOf(from, to))

    /** Angle (degrees, wrapped to (-90, 90]) of the quad's longest edge vs horizontal. */
    internal fun quadBaselineAngleDegrees(quad: List<PointF>): Float {
        var longest = -1f
        var angle = 0f
        for (i in quad.indices) {
            val a = quad[i]
            val b = quad[(i + 1) % quad.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = dx * dx + dy * dy
            if (len > longest) {
                longest = len
                angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            }
        }
        var wrapped = angle
        while (wrapped <= -90f) wrapped += 180f
        while (wrapped > 90f) wrapped -= 180f
        return wrapped
    }

    /** Perpendicular height of the quad — mean distance of the two non-baseline corners to the baseline line. */
    internal fun quadHeight(quad: List<PointF>): Float {
        // Locate the longest edge: it defines the text baseline.
        var baselineA = quad[0]
        var baselineB = quad[1]
        var longest = -1f
        for (i in quad.indices) {
            val a = quad[i]
            val b = quad[(i + 1) % quad.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = dx * dx + dy * dy
            if (len > longest) {
                longest = len
                baselineA = a
                baselineB = b
            }
        }
        var height = 0f
        var samples = 0
        for (p in quad) {
            if (p == baselineA || p == baselineB) continue
            height += distanceToLine(p, baselineA, baselineB)
            samples++
        }
        return if (samples == 0) 0f else height / samples
    }

    /** Centroid of the quad's four corners. */
    internal fun quadCentroid(quad: List<PointF>): PointF {
        var x = 0f
        var y = 0f
        for (p in quad) {
            x += p.x
            y += p.y
        }
        return PointF(x / quad.size, y / quad.size)
    }

    /** Axis-aligned bounds of the quad (used as the ink-sampling window). */
    internal fun quadBounds(quad: List<PointF>): RectF {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (p in quad) {
            left = minOf(left, p.x)
            top = minOf(top, p.y)
            right = maxOf(right, p.x)
            bottom = maxOf(bottom, p.y)
        }
        return RectF(left, top, right, bottom)
    }

    private fun distanceToLine(p: PointF, a: PointF, b: PointF): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len = sqrt(abx * abx + aby * aby)
        if (len <= 0f) return 0f
        return kotlin.math.abs(abx * (a.y - p.y) - aby * (a.x - p.x)) / len
    }
}