package com.whimsicalart.feature.editor.domain

import android.content.ContentValues
import android.content.Context
import android.content.Intent
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

enum class ImageFormat {
    JPEG,
    PNG
}

enum class ImageQuality(val value: Int) {
    ORIGINAL(100),
    HIGH(85),
    MEDIUM(60),
    LOW(30)
}

enum class Resolution(val maxDimension: Int) {
    ORIGINAL(0),
    HIGH(2560),
    MEDIUM(1920),
    LOW(1280)
}

data class SaveConfig(
    val format: ImageFormat = ImageFormat.JPEG,
    val quality: ImageQuality = ImageQuality.HIGH,
    val resolution: Resolution = Resolution.ORIGINAL,
    val album: String = "WhimsicalArt"
)

@Singleton
class ImageSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun saveImage(
        bitmap: Bitmap,
        config: SaveConfig,
        filename: String? = null
    ): Uri? {
        val actualFilename = filename ?: "whimsicalart_${System.currentTimeMillis()}"
        val toSave = scaleForResolution(bitmap, config.resolution)

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                saveToMediaStore(toSave, config, actualFilename)
            }
            else -> {
                saveToExternalStorage(toSave, config, actualFilename)
            }
        }
    }

    private fun scaleForResolution(bitmap: Bitmap, resolution: Resolution): Bitmap {
        val maxDimension = resolution.maxDimension
        if (maxDimension <= 0) return bitmap
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / largest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun saveToMediaStore(
        bitmap: Bitmap,
        config: SaveConfig,
        filename: String
    ): Uri? {
        val mimeType = when (config.format) {
            ImageFormat.JPEG -> "image/jpeg"
            ImageFormat.PNG -> "image/png"
        }
        
        val extension = when (config.format) {
            ImageFormat.JPEG -> ".jpg"
            ImageFormat.PNG -> ".png"
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$filename$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + config.album)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                when (config.format) {
                    ImageFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, config.quality.value, outputStream)
                    ImageFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(it, values, null, null)
        }

        return uri
    }

    private fun saveToExternalStorage(
        bitmap: Bitmap,
        config: SaveConfig,
        filename: String
    ): Uri? {
        val extension = when (config.format) {
            ImageFormat.JPEG -> ".jpg"
            ImageFormat.PNG -> ".png"
        }

        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, config.album)
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val file = File(appDir, "$filename$extension")
        
        FileOutputStream(file).use { outputStream ->
            when (config.format) {
                ImageFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, config.quality.value, outputStream)
                ImageFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        }

        return Uri.fromFile(file)
    }

    fun shareImage(bitmap: Bitmap, config: SaveConfig, context: Context) {
        val scaled = scaleForResolution(bitmap, config.resolution)
        val file = File(context.cacheDir, "share_image.jpg")
        FileOutputStream(file).use { outputStream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, config.quality.value, outputStream)
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
    }
}
