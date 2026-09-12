package codes.pepper.whimsicalart.feature.editor.ui.viewmodel

/**
 * The pure decision extracted from `EditorViewModel.stalenessForDerivedDelta`
 * (BP-6): given two effect-stack *fingerprint sequences*, where does the first
 * meaningful change sit, and was the change strictly an appended tail? Both
 * values are derived from signatures only, so the derived-stack staleness rules
 * are unit-testable with plain JUnit — the fold-cache mutation itself stays in
 * the ViewModel.
 */
internal data class StalenessDelta(
    /** Index of the first entry that differs; = `before.size` when the change is an append. */
    val firstDiffIndex: Int,
    /** True when the change is a pure tail append (identical prefix + exactly one extra entry). */
    val tailAppend: Boolean
)

/**
 * Pure signature-diff: compares the fingerprint sequences of two effect lists
 * and returns where they first differ plus whether the change is a strict tail
 * append.
 *
 * The caller keeps `count = before.size` — the rules index into the *derived*
 * stack, so the length is passed along with the decision rather than folded
 * into this result. An empty `before` with a single extra `after` entry is a
 * tail append at index 0.
 */
internal fun deriveStalenessDelta(before: List<String>, after: List<String>): StalenessDelta {
    val count = before.size
    val tailAppend = after.size == count + 1 &&
        (0 until count).all { before[it] == after[it] }
    val firstDiff = (0 until count).firstOrNull { i ->
        i >= after.size || before[i] != after[i]
    } ?: count
    return StalenessDelta(firstDiff, tailAppend)
}

/**
 * The pure freshness contract for the full-size composite cache (BP-6,
 * `EditorViewModel.isFullCompositeFresh`): a cached full-size composite is
 * usable only when the tail is shown (eye OFF folds just the prefix — serving
 * the last full-stack image would leak the hidden tail), a valid cached image
 * is present, and the stored stack signature matches the current controls.
 */
internal fun compositeCacheIsFresh(
    includeTail: Boolean,
    hasValidCachedImage: Boolean,
    signatureMatches: Boolean
): Boolean = includeTail && hasValidCachedImage && signatureMatches