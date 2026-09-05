package codes.pepper.whimsicalart.feature.gallery.domain.tag

import android.graphics.Bitmap

/**
 * A raw on-device image-labeling result. Pure data; produced by an on-device
 * classifier and consumed by the pure [TagTransformer].
 */
data class SceneLabel(
    val text: String,
    val confidence: Float
)

/**
 * On-device scene labeler. The concrete implementation wraps ML Kit Image
 * Labeling and can only run on a real device/emulator (native runtime), so it is
 * isolated behind this interface and unit tests target the pure [TagTransformer].
 */
interface SceneClassifier {
    suspend fun label(bitmap: Bitmap): List<SceneLabel>
}