package com.whimsicalart.feature.editor.ui.removal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

class ObjectRemover {

    fun removeObject(
        original: Bitmap,
        mask: Bitmap,
        brushSize: Float = 20f
    ): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val paint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        canvas.drawBitmap(mask, 0f, 0f, paint)

        fillArea(result, mask)

        return result
    }

    private fun fillArea(bitmap: Bitmap, mask: Bitmap) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (mask.getPixel(x, y) == Color.WHITE) {
                    val surroundingColor = getAverageSurroundingColor(bitmap, x, y, 10)
                    bitmap.setPixel(x, y, surroundingColor)
                }
            }
        }
    }

    private fun getAverageSurroundingColor(bitmap: Bitmap, x: Int, y: Int, radius: Int): Int {
        var totalR = 0
        var totalG = 0
        var totalB = 0
        var count = 0

        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                if (dx == 0 && dy == 0) continue

                val nx = x + dx
                val ny = y + dy

                if (nx in 0 until bitmap.width && ny in 0 until bitmap.height) {
                    val pixel = bitmap.getPixel(nx, ny)
                    totalR += Color.red(pixel)
                    totalG += Color.green(pixel)
                    totalB += Color.blue(pixel)
                    count++
                }
            }
        }

        return if (count > 0) {
            Color.rgb(totalR / count, totalG / count, totalB / count)
        } else {
            Color.TRANSPARENT
        }
    }

    fun createRemovalMask(
        bitmap: Bitmap,
        selectionPath: Path,
        featherRadius: Float = 10f
    ): Bitmap {
        val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)

        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(
                featherRadius,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }

        canvas.drawPath(selectionPath, paint)
        return mask
    }

    fun healArea(
        original: Bitmap,
        sourceRect: RectF,
        targetRect: RectF
    ): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val sourceBitmap = Bitmap.createBitmap(
            original,
            sourceRect.left.toInt(),
            sourceRect.top.toInt(),
            sourceRect.width().toInt(),
            sourceRect.height().toInt()
        )

        canvas.drawBitmap(
            sourceBitmap,
            null,
            targetRect,
            null
        )

        return result
    }
}

data class RemovalConfig(
    val brushSize: Float = 20f,
    val featherRadius: Float = 10f,
    val mode: RemovalMode = RemovalMode.REMOVE
)

enum class RemovalMode {
    REMOVE,
    HEAL,
    CLONE
}
