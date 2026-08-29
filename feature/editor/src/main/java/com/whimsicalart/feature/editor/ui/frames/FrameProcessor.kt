package com.whimsicalart.feature.editor.ui.frames

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

data class Frame(
    val id: String,
    val name: String,
    val category: FrameCategory,
    val borderWidth: Float = 20f,
    val cornerRadius: Float = 0f,
    val borderColor: Int = Color.WHITE,
    val hasShadow: Boolean = false,
    val shadowColor: Int = Color.BLACK,
    val shadowRadius: Float = 10f
)

enum class FrameCategory {
    CLASSIC,
    MODERN,
    DECORATIVE,
    MINIMAL
}

class FrameProcessor {

    fun applyFrame(
        bitmap: Bitmap,
        frame: Frame
    ): Bitmap {
        val result = Bitmap.createBitmap(
            bitmap.width + (frame.borderWidth * 2).toInt(),
            bitmap.height + (frame.borderWidth * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)

        if (frame.hasShadow) {
            val shadowPaint = Paint().apply {
                color = frame.shadowColor
                maskFilter = android.graphics.BlurMaskFilter(
                    frame.shadowRadius,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.drawRect(
                frame.borderWidth + frame.shadowRadius,
                frame.borderWidth + frame.shadowRadius,
                result.width - frame.borderWidth + frame.shadowRadius,
                result.height - frame.borderWidth + frame.shadowRadius,
                shadowPaint
            )
        }

        val borderPaint = Paint().apply {
            color = frame.borderColor
            style = Paint.Style.FILL
        }

        if (frame.cornerRadius > 0) {
            canvas.drawRoundRect(
                RectF(0f, 0f, result.width.toFloat(), result.height.toFloat()),
                frame.cornerRadius,
                frame.cornerRadius,
                borderPaint
            )
        } else {
            canvas.drawRect(
                0f, 0f, result.width.toFloat(), result.height.toFloat(),
                borderPaint
            )
        }

        canvas.drawBitmap(bitmap, frame.borderWidth, frame.borderWidth, null)

        return result
    }

    fun applyCircleFrame(bitmap: Bitmap, borderWidth: Float): Bitmap {
        val size = maxOf(bitmap.width, bitmap.height)
        val result = Bitmap.createBitmap(size + (borderWidth * 2).toInt(), size + (borderWidth * 2).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(result.width / 2f, result.height / 2f, result.width / 2f, borderPaint)

        val imagePaint = Paint().apply {
            isAntiAlias = true
        }
        val left = (result.width - bitmap.width) / 2f
        val top = (result.height - bitmap.height) / 2f
        canvas.drawBitmap(bitmap, left, top, imagePaint)

        return result
    }

    fun applyRoundedFrame(bitmap: Bitmap, borderWidth: Float, cornerRadius: Float): Bitmap {
        val result = Bitmap.createBitmap(
            bitmap.width + (borderWidth * 2).toInt(),
            bitmap.height + (borderWidth * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)

        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, result.width.toFloat(), result.height.toFloat()),
            cornerRadius,
            cornerRadius,
            borderPaint
        )

        val path = Path().apply {
            addRoundRect(
                RectF(borderWidth, borderWidth, result.width - borderWidth, result.height - borderWidth),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )
        }
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, borderWidth, borderWidth, null)

        return result
    }
}

object FramePresets {
    val frames = listOf(
        Frame(
            id = "classic_white",
            name = "Classic White",
            category = FrameCategory.CLASSIC,
            borderWidth = 20f,
            cornerRadius = 0f,
            borderColor = Color.WHITE
        ),
        Frame(
            id = "classic_black",
            name = "Classic Black",
            category = FrameCategory.CLASSIC,
            borderWidth = 20f,
            cornerRadius = 0f,
            borderColor = Color.BLACK
        ),
        Frame(
            id = "modern_shadow",
            name = "Modern Shadow",
            category = FrameCategory.MODERN,
            borderWidth = 15f,
            cornerRadius = 8f,
            borderColor = Color.WHITE,
            hasShadow = true,
            shadowRadius = 8f
        ),
        Frame(
            id = "minimal_thin",
            name = "Minimal Thin",
            category = FrameCategory.MINIMAL,
            borderWidth = 8f,
            cornerRadius = 0f,
            borderColor = Color.WHITE
        ),
        Frame(
            id = "decorative_gold",
            name = "Decorative Gold",
            category = FrameCategory.DECORATIVE,
            borderWidth = 25f,
            cornerRadius = 4f,
            borderColor = Color.parseColor("#FFD700")
        )
    )

    fun getFramesByCategory(category: FrameCategory): List<Frame> {
        return frames.filter { it.category == category }
    }

    fun getFrameById(id: String): Frame? {
        return frames.find { it.id == id }
    }
}
