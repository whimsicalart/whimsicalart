package codes.pepper.whimsicalart.feature.gallery.domain.tag

/**
 * Pure, unit-testable mapping from raw on-device [SceneLabel]s into the curated
 * [SceneTag] vocabulary. ML Kit is never touched here, so every rule below can
 * be exercised under Robolectric without a native runtime:
 *  - a label contributes to a tag when its (case-insensitive) text contains a
 *    keyword that the tag recognizes;
 *  - a tag is only emitted when its aggregated confidence clears [MIN_CONFIDENCE];
 *  - the highest-confidence contributing label drives the emitted confidence;
 *  - labels that match no keyword are ignored (the image is simply untagged).
 */
object TagTransformer {

    /** ML Kit's default per-label confidence gate. */
    const val MIN_CONFIDENCE = 0.5f

    /**
     * Each tag's keyword list is checked with substring containment so a label
     * like "Human face" or "Skyscraper" maps to the intended curated tag.
     */
    private val KEYWORDS: Map<SceneTag, List<String>> = mapOf(
        SceneTag.PORTRAIT to listOf("face", "portrait", "person", "people"),
        SceneTag.BEAUTY to listOf("beauty", "makeup", "cosmet"),
        SceneTag.INDOOR to listOf("indoors", "interior", "furniture", "room"),
        SceneTag.OUTDOOR to listOf("outdoor", "outdoors", "park", "garden"),
        SceneTag.NATURE to listOf("nature", "forest", "tree", "plant", "flower", "leaf", "mountain"),
        SceneTag.FOOD to listOf("food", "cuisine", "meal", "dish", "fruit", "vegetable", "dessert"),
        SceneTag.DOCUMENT to listOf("text", "document", "paper", "book", "page", "reading"),
        SceneTag.ANIMAL to listOf("animal", "cat", "dog", "bird", "mammal", "pet"),
        SceneTag.NIGHT to listOf("night", "dark", "evening", "moon"),
        SceneTag.WATER to listOf("water", "sea", "ocean", "river", "lake", "beach", "pool"),
        SceneTag.URBAN to listOf("urban", "city", "building", "street", "skyscrap", "traffic"),
        SceneTag.SKY to listOf("sky", "cloud", "sunset", "sunrise", "horizon")
    )

    /**
     * Maps [labels] to the set of [SceneTag]s whose confidence clears
     * [MIN_CONFIDENCE], using each tag's best matching label. Order is stable by
     * tag confidence descending, so callers can pick the dominant tag cheaply.
     */
    fun transform(labels: List<SceneLabel>): List<SceneTagResult> {
        val bestByTag = mutableMapOf<SceneTag, Float>()
        for (label in labels) {
            if (label.confidence < MIN_CONFIDENCE) continue
            val tag = match(label.text) ?: continue
            val current = bestByTag[tag]
            if (current == null || label.confidence > current) {
                bestByTag[tag] = label.confidence
            }
        }
        return bestByTag.entries
            .map { (tag, confidence) -> SceneTagResult(tag, confidence) }
            .sortedByDescending { it.confidence }
    }

    /** Returns the dominant tag for an image ("" -> null when none qualifies). */
    fun dominant(labels: List<SceneLabel>): SceneTagResult? = transform(labels).firstOrNull()

    /**
     * Branches a raw label text to its [SceneTag] by substring keyword match,
     * taking the first tag whose keyword list contains a match in declaration
     * order. Case-insensitive. Returns null when nothing matches.
     */
    fun match(raw: String): SceneTag? {
        val text = raw.lowercase()
        for ((tag, keywords) in KEYWORDS) {
            if (keywords.any { text.contains(it) }) return tag
        }
        return null
    }
}

/** A matched tag together with its driving confidence. */
data class SceneTagResult(
    val tag: SceneTag,
    val confidence: Float
)