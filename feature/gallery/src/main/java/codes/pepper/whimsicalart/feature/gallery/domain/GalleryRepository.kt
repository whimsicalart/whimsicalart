package codes.pepper.whimsicalart.feature.gallery.domain

import android.net.Uri

interface GalleryRepository {
    suspend fun getPhotos(): List<Photo>
    suspend fun getPhoto(uri: Uri): Photo?
}
