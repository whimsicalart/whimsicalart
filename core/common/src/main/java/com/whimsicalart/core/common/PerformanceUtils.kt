package com.whimsicalart.core.common

import android.graphics.Bitmap
import android.os.Trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PerformanceUtils {

    suspend fun <T> measurePerformance(
        traceName: String,
        block: suspend () -> T
    ): T {
        return withContext(Dispatchers.Default) {
            Trace.beginSection(traceName)
            try {
                block()
            } finally {
                Trace.endSection()
            }
        }
    }

    fun optimizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val ratio = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
                sampleSize *= 2
            }
        }

        return sampleSize
    }
}
