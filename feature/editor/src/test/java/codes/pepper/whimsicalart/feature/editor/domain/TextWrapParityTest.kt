package codes.pepper.whimsicalart.feature.editor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live-vs-committed text wrap parity (next.md:101). The committed text block is
 * wrapped by [TextOverlayRenderer.wrapLines] — pure `Paint.measureText` math,
 * deliberately deterministic on every backend. It must mirror the live Compose
 * `Text` layout on the common line-breaking cases: single-space greedy word
 * wrap (identical to the legacy simple wrapper), **verbatim whitespace runs**
 * (consecutive spaces survive), **CJK no-space runs break between ideographs**
 * instead of clipping past the block edge, and an overlong single word is
 * **hard-broken by character** (Compose's default overflow wrap) instead of
 * being pushed off the raster.
 *
 * Widths come from an injected `measure` stub (`text.length`), because
 * Robolectric's `Paint.measureText` always returns 0 — the wrap DECISIONS are
 * what this pins, not glyph metrics.
 */
class TextWrapParityTest {

    private fun wrap(text: String, maxWidth: Int) =
        TextOverlayRenderer.wrapLines(text, maxWidth.toFloat()) { it.length.toFloat() }

    @Test
    fun `single-space latin text wraps greedily exactly like the previous wrapper`() {
        // Same line breaks the old split-on-space greedy wrapper produced.
        val text = "the quick brown fox jumps over the lazy dog"
        assertEquals(
            listOf("the quick brown fox", "jumps over the lazy", "dog"),
            wrap(text, 20)
        )
    }

    @Test
    fun `consecutive spaces are preserved verbatim instead of collapsed`() {
        assertEquals(listOf("a  b  c"), wrap("a  b  c", 8))
        // Where the double-space run itself forces a wrap, the break still keeps
        // the original spacing inside each line ("a  b", not "a b").
        assertEquals(listOf("a  b", "c"), wrap("a  b  c", 4))
    }

    @Test
    fun `leading and trailing whitespace collapse at the wrapped edges`() {
        assertEquals(listOf("hello world"), wrap("  hello world  ", 100))
    }

    @Test
    fun `a long no-space CJK string wraps between ideographs instead of clipping`() {
        // 11 ideographs at one char per width unit → greedy 4-char lines.
        assertEquals(
            listOf("日本語の", "テストテ", "キスト"),
            wrap("日本語のテストテキスト", 4)
        )
        assertEquals(
            listOf("こんにち", "は世界"),
            wrap("こんにちは世界", 4)
        )
    }

    @Test
    fun `mixed latin and CJK text breaks at the script boundary too`() {
        assertEquals(listOf("hello", "世界", "again"), wrap("hello世界again", 5))
        // "hello" was too wide WITH the CJK run glued on; a tighter width must
        // hard-break each overlong unit by character at the same boundaries.
        assertEquals(listOf("hell", "o", "世界", "agai", "n"), wrap("hello世界again", 4))
    }

    @Test
    fun `an overlong single word hard-breaks by character instead of clipping`() {
        assertEquals(
            listOf("abcde", "fghij", "klmno", "pqrst", "uvwxy", "z"),
            wrap("abcdefghijklmnopqrstuvwxyz", 5)
        )
    }

    @Test
    fun `explicit newlines and blank lines behave as before`() {
        assertEquals(listOf(""), TextOverlayRenderer.wrapLines("", 100f) { 0f })
        assertEquals(listOf("ab", "", "cd"), wrap("ab\n  \ncd", 100))
    }

    @Test
    fun `ideograph and CJK punctuation characters open line-break opportunities`() {
        assertTrue(TextOverlayRenderer.isCjkClass('日'))
        assertTrue(TextOverlayRenderer.isCjkClass('テ'))
        assertTrue(TextOverlayRenderer.isCjkClass('。')) // U+3002 CJK full stop
        assertTrue(TextOverlayRenderer.isCjkClass('ｱ')) // U+FF71 halfwidth katakana
        assertTrue(!TextOverlayRenderer.isCjkClass('a'))
        assertTrue(!TextOverlayRenderer.isCjkClass(' '))
        assertTrue(!TextOverlayRenderer.isCjkClass('é'))
    }
}