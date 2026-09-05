package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A recognized text line prepared for insertion as an editor text overlay: the
 * normalized text plus a normalized anchor (0..1 across the source image) and a
 * font-size fraction of the image width, so imported OCR text is placed close to
 * where it was detected and is proportionate to the surrounding content.
 */
data class OcrOverlayFragment(
    val text: String,
    val normalizedX: Float,
    val normalizedY: Float,
    val fontSizeFraction: Float
)

/**
 * Pure, unit-testable transformation from raw on-device OCR output into
 * overlay-ready text fragments. ML Kit is never touched here, so every rule
 * below can be exercised under Robolectric without a native runtime:
 *  - drops blank / dashes-only / very short fragments (garbage or separators);
 *  - collapses internal whitespace and trims edges;
 *  - caps each fragment's length so an over-detected region can't flood the canvas;
 *  - anchors the overlay at the detected line's bounding-box centre.
 */
object OcrTextExtractor {

    const val MIN_FRAGMENT_LENGTH = 2
    const val MAX_FRAGMENT_LENGTH = 120
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
     * fragments, dropping junk and computing normalized anchors. Result is sorted
     * top-to-bottom (reading order) then left-to-right.
     */
    fun overlayFragments(
        lines: List<OcrLine>,
        sourceWidth: Int,
        sourceHeight: Int
    ): List<OcrOverlayFragment> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return emptyList()

        val fragments = lines.mapNotNull { line ->
            val text = normalizeToken(line.text)
            if (text.isEmpty()) return@mapNotNull null
            val box = line.boundingBox
            if (box.width() <= 0f || box.height() <= 0f) return@mapNotNull null

            val centerX = (box.centerX() / sourceWidth)
                .coerceIn(0f, 1f)
            val centerY = (box.centerY() / sourceHeight)
                .coerceIn(0f, 1f)
            val fontSizeFraction = (box.width() / sourceWidth)
                .coerceIn(0.02f, 0.2f)
            OcrOverlayFragment(text, centerX, centerY, fontSizeFraction)
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
}