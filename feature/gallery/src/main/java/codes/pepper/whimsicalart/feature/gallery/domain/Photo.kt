package codes.pepper.whimsicalart.feature.gallery.domain

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val dateModified: Long = 0L,
    val size: Long = 0L,
    val path: String = "",
    val mimeType: String = "",
    val dateTaken: Long = 0L
)
