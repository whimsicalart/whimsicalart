package codes.pepper.whimsicalart.feature.collage.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollageSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun saveCollage(bitmap: Bitmap, album: String = "WhimsicalArt"): Uri? {
        val filename = "collage_${System.currentTimeMillis()}"
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                saveToMediaStore(bitmap, filename, album)
            }
            else -> {
                saveToExternalStorage(bitmap, filename, album)
            }
        }
    }

    private fun saveToMediaStore(bitmap: Bitmap, filename: String, album: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + album
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(it, values, null, null)
        }
        return uri
    }

    private fun saveToExternalStorage(bitmap: Bitmap, filename: String, album: String): Uri? {
        val picturesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, album)
        if (!appDir.exists()) appDir.mkdirs()
        val file = File(appDir, "$filename.jpg")
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        }
        return Uri.fromFile(file)
    }
}
