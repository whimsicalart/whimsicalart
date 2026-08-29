package com.whimsicalart.feature.editor.domain.bokeh

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader

class BokehProcessor {

    fun applyBackgroundBlur(
        original: Bitmap,
        mask: Bitmap,
        blurRadius: Float,
        bokehShape: BokehShape = BokehShape.CIRCLE
    ): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val blurredBitmap = blurBitmap(original, blurRadius)

        val paint = Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.DST_IN
            )
        }

        canvas.drawBitmap(mask, 0f, 0f, null)
        canvas.drawBitmap(blurredBitmap, 0f, 0f, paint)

        return result
    }

    fun createForegroundMask(
        bitmap: Bitmap,
        foregroundRect: RectF,
        featherRadius: Float = 20f
    ): Bitmap {
        val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)

        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }

        val path = Path().apply {
            addRoundRect(
                foregroundRect,
                featherRadius,
                featherRadius,
                Path.Direction.CW
            )
        }

        canvas.drawPath(path, paint)
        return mask
    }

    fun applyBokehEffect(
        bitmap: Bitmap,
        mask: Bitmap,
        intensity: Float,
        bokehShape: BokehShape
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val paint = Paint().apply {
            alpha = (intensity * 255).toInt()
        }

        when (bokehShape) {
            BokehShape.CIRCLE -> {
                drawCircleBokeh(canvas, mask, paint)
            }
            BokehShape.HEXAGON -> {
                drawHexagonBokeh(canvas, mask, paint)
            }
            BokehShape.HEART -> {
                drawHeartBokeh(canvas, mask, paint)
            }
        }

        return result
    }

    private fun blurBitmap(bitmap: Bitmap, radius: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val renderNode = RenderNode("blur")
        renderNode.setPosition(0, 0, bitmap.width, bitmap.height)

        val blurEffect = RenderEffect.createBlurEffect(
            radius,
            radius,
            Shader.TileMode.CLAMP
        )
        renderNode.setRenderEffect(blurEffect)

        val renderCanvas = renderNode.beginRecording()
        renderCanvas.drawBitmap(bitmap, 0f, 0f, null)
        renderNode.endRecording()

        canvas.drawRenderNode(renderNode)

        return result
    }

    private fun drawCircleBokeh(canvas: Canvas, mask: Bitmap, paint: Paint) {
        val path = Path()
        val centerX = mask.width / 2f
        val centerY = mask.height / 2f
        val radius = minOf(mask.width, mask.height) / 2f

        path.addCircle(centerX, centerY, radius, Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawBitmap(mask, 0f, 0f, paint)
    }

    private fun drawHexagonBokeh(canvas: Canvas, mask: Bitmap, paint: Paint) {
        val path = Path()
        val centerX = mask.width / 2f
        val centerY = mask.height / 2f
        val radius = minOf(mask.width, mask.height) / 2f

        for (i in 0..5) {
            val angle = Math.toRadians((60 * i - 30).toDouble())
            val x = centerX + (radius * kotlin.math.cos(angle)).toFloat()
            val y = centerY + (radius * kotlin.math.sin(angle)).toFloat()

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        canvas.clipPath(path)
        canvas.drawBitmap(mask, 0f, 0f, paint)
    }

    private fun drawHeartBokeh(canvas: Canvas, mask: Bitmap, paint: Paint) {
        val path = Path()
        val centerX = mask.width / 2f
        val centerY = mask.height / 2f
        val size = minOf(mask.width, mask.height) / 2f

        path.moveTo(centerX, centerY + size / 4)
        path.cubicTo(
            centerX + size / 2, centerY - size / 2,
            centerX + size, centerY + size / 4,
            centerX, centerY + size
        )
        path.moveTo(centerX, centerY + size / 4)
        path.cubicTo(
            centerX - size / 2, centerY - size / 2,
            centerX - size, centerY + size / 4,
            centerX, centerY + size
        )
        path.close()

        canvas.clipPath(path)
        canvas.drawBitmap(mask, 0f, 0f, paint)
    }
}

enum class BokehShape {
    CIRCLE,
    HEXAGON,
    HEART
}

data class BokehConfig(
    val intensity: Float = 0.5f,
    val blurRadius: Float = 10f,
    val shape: BokehShape = BokehShape.CIRCLE,
    val foregroundRect: RectF? = null
)
