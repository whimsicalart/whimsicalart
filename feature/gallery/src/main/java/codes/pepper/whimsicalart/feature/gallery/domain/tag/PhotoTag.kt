package codes.pepper.whimsicalart.feature.gallery.domain.tag

import android.net.Uri

/**
 * A persisted resolution that a [SceneTag] applies to a photo. [uri] identifies
 * the MediaStore image and [confidence] is the classifier's confidence in the
 * mapping (stored so a low-confidence tag can be de-emphasized or dropped).
 */
data class PhotoTag(
    val uri: Uri,
    val tag: SceneTag,
    val confidence: Float,
    val taggedAt: Long
)