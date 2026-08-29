package com.whimsicalart.feature.gallery.domain

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val width: Int,
    val height: Int
)
