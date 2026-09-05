package codes.pepper.whimsicalart.feature.gallery.domain.tag

/**
 * Curated set of scene tags surfaced in the gallery for filtering/search. Each
 * tag is derived by the pure [TagTransformer] from raw ML Kit label confidences,
 * keeping the module's vocabulary stable and testable without a native runtime.
 */
enum class SceneTag(val displayName: String) {
    PORTRAIT("Portrait"),
    BEAUTY("Beauty"),
    INDOOR("Indoor"),
    OUTDOOR("Outdoor"),
    NATURE("Nature"),
    FOOD("Food"),
    DOCUMENT("Document"),
    ANIMAL("Animal"),
    NIGHT("Night"),
    WATER("Water"),
    URBAN("Urban"),
    SKY("Sky");
}