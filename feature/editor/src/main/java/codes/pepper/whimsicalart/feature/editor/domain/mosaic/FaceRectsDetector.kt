package codes.pepper.whimsicalart.feature.editor.domain.mosaic

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Detects face rectangles in a bitmap so the Mosaic tool can auto-suggest
 * privacy-mask regions. Kept behind an interface so the native ML Kit runtime can
 * be swapped / faked, matching the codebase's ML isolation pattern (unit tests
 * exercise the pure [PrivacyMaskBuilder], not this detector).
 */
interface FaceRectsDetector {
    /** @return face bounding boxes in source pixels, or empty if none / on failure. */
    fun detectFaces(source: Bitmap): List<Rect>

    /** Releases native resources. No-op by default so fakes are trivially testable. */
    fun close() = Unit
}