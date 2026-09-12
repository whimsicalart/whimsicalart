package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for the full-size composite freshness contract extracted from
 * `EditorViewModel.isFullCompositeFresh` (BP-6) — plain JUnit, no Android.
 * A cached full-size composite is usable only when the tail is visible AND a
 * valid cached image is present AND the stored stack signature matches the
 * current controls.
 */
class CompositeCacheFreshnessTest {

    @Test
    fun `fresh only when tail shown with a valid signature-matching image`() {
        assertTrue(compositeCacheIsFresh(includeTail = true, hasValidCachedImage = true, signatureMatches = true))
    }

    @Test
    fun `not fresh while the tail is hidden`() {
        assertFalse(compositeCacheIsFresh(includeTail = false, hasValidCachedImage = true, signatureMatches = true))
    }

    @Test
    fun `not fresh when no valid cached image exists`() {
        assertFalse(compositeCacheIsFresh(includeTail = true, hasValidCachedImage = false, signatureMatches = true))
    }

    @Test
    fun `not fresh when the signature no longer matches the controls`() {
        assertFalse(compositeCacheIsFresh(includeTail = true, hasValidCachedImage = true, signatureMatches = false))
    }

    @Test
    fun `any missing condition is not fresh`() {
        assertFalse(compositeCacheIsFresh(includeTail = false, hasValidCachedImage = false, signatureMatches = true))
        assertFalse(compositeCacheIsFresh(includeTail = true, hasValidCachedImage = false, signatureMatches = false))
        assertFalse(compositeCacheIsFresh(includeTail = false, hasValidCachedImage = true, signatureMatches = false))
        assertFalse(compositeCacheIsFresh(includeTail = false, hasValidCachedImage = false, signatureMatches = false))
    }
}