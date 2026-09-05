package codes.pepper.whimsicalart.feature.editor.domain

import android.net.Uri

data class Image(
    val uri: Uri,
    val width: Int,
    val height: Int
)
