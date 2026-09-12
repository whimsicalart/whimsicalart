package codes.pepper.whimsicalart.feature.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import codes.pepper.whimsicalart.feature.gallery.domain.GalleryRepository
import codes.pepper.whimsicalart.feature.gallery.domain.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver
) : GalleryRepository {

    override suspend fun getPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            detailsProjection(),
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val reader = PhotoCursorReader(cursor)
            while (cursor.moveToNext()) {
                val id = reader.readId()
                photos.add(
                    reader.readPhoto()
                        .copy(
                            uri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                        )
                )
            }
        }

        photos
    }

    override suspend fun getPhoto(uri: Uri): Photo? = withContext(Dispatchers.IO) {
        contentResolver.query(
            uri,
            detailsProjection(),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext PhotoCursorReader(cursor).readPhoto().copy(uri = uri)
            }
        }

        null
    }

    private fun detailsProjection(): Array<String> = buildList {
        add(MediaStore.Images.Media._ID)
        add(MediaStore.Images.Media.DISPLAY_NAME)
        add(MediaStore.Images.Media.DATE_ADDED)
        add(MediaStore.Images.Media.WIDTH)
        add(MediaStore.Images.Media.HEIGHT)
        add(MediaStore.Images.Media.DATE_MODIFIED)
        add(MediaStore.Images.Media.MIME_TYPE)
        add(MediaStore.Images.Media.DATE_TAKEN)
        // Every projected column must be a PUBLIC MediaStore column that exists
        // on the API level it is requested from: "_size" and "relative_path" only
        // exist from API 29, and "date_modified_ms" is NOT public on OEM devices
        // at all (the AOSP-only column made the emulator gallery work while a real
        // device throws "Invalid column date_modified_ms").
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Images.Media.SIZE)
            add(RESOLVED_PATH_COLUMN)
        } else {
            add(MediaStore.Images.Media.DATA)
        }
    }.toTypedArray()

    /**
     * Named reader so both queries share the exact same column mapping.
     */
    private class PhotoCursorReader(private val cursor: Cursor) {
        private val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        private val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        private val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        private val width = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        private val height = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        private val dateModified = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
        // "_size" is only present on API 29+ and some providers may still omit
        // it; read it conditionally (never getColumnIndexOrThrow) like path.
        private val size = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
        private val mimeType = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        private val dateTaken = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        private val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(RESOLVED_PATH_COLUMN)
        } else {
            cursor.getColumnIndex(MediaStore.Images.Media.DATA)
        }

        fun readId(): Long = cursor.getLong(id)

fun readPhoto(): Photo {
            val rawAdded = cursor.getLong(dateAdded)
            val rawModified = cursor.getLong(dateModified)
            val rawTaken = cursor.getLong(dateTaken)
            return Photo(
                id = readId(),
                uri = Uri.EMPTY,
                displayName = cursor.getString(name).orEmpty(),
                dateAdded = rawAdded,
                width = cursor.getInt(width),
                height = cursor.getInt(height),
                // A freshly-written row often reports DATE_MODIFIED == 0 (the media
                // scanner finalises the timestamp asynchronously); coalesce to the
                // insertion time so a just-added photo never sorts below its real
                // creation moment. Only the PUBLIC, second-precision column is
                // projection-safe across OEMs; scale to ms for the existing
                // consumers ("Last modified").
                dateModified = (if (rawModified > 0L) rawModified else rawAdded) * 1000L,
                size = if (size >= 0) cursor.getLong(size) else 0L,
                path = readPath(),
                mimeType = cursor.getString(mimeType).orEmpty(),
                // DATE_TAKEN is equally unreliable on freshly-written rows; fall
                // back to the insertion time too.
                dateTaken = if (rawTaken > 0L) rawTaken else rawAdded
            )
        }

        private fun readPath(): String =
            if (pathColumn >= 0) cursor.getString(pathColumn).orEmpty() else ""

        private companion object {
            const val RESOLVED_PATH_COLUMN = "relative_path"
        }
    }

    private companion object {
        const val RESOLVED_PATH_COLUMN = "relative_path"
    }
}