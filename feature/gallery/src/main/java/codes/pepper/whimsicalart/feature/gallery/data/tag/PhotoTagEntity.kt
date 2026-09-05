package codes.pepper.whimsicalart.feature.gallery.data.tag

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import codes.pepper.whimsicalart.feature.gallery.domain.tag.PhotoTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneTag

/**
 * Durable Room row mapping a [SceneTag] to a photo. Composite PK keeps the same
 * photo/tag pair idempotent (re-tagging overwrites rather than duplicates).
 */
@Entity(
    tableName = "photo_tags",
    primaryKeys = ["uri", "tag"],
    indices = [Index(value = ["tag"])]
)
data class PhotoTagEntity(
    val uri: String,
    val tag: String,
    val confidence: Float,
    val taggedAt: Long
) {
    fun toDomain(): PhotoTag = PhotoTag(
        uri = Uri.parse(uri),
        tag = SceneTag.valueOf(tag),
        confidence = confidence,
        taggedAt = taggedAt
    )

    companion object {
        fun fromDomain(tag: PhotoTag): PhotoTagEntity = PhotoTagEntity(
            uri = tag.uri.toString(),
            tag = tag.tag.name,
            confidence = tag.confidence,
            taggedAt = tag.taggedAt
        )
    }
}