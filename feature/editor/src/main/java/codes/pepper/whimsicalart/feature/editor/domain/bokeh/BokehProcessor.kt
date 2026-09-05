package codes.pepper.whimsicalart.feature.editor.domain.bokeh

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
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

class BokehProcessor {

    /**
     * Portrait-style background defocus that keeps the subject sharp.
     *
     * [foregroundMask] is white where the subject should stay in focus (any pixel
     * whose alpha >= [maskThreshold] is kept sharp). Everything outside the mask
     * is replaced with a blurred copy of [original]; where [bokehShape] is applied
     * the blurred background's bright highlights are re-stamped as shaped discs to
     * give the bokeh its characteristic defocused-light look.
     *
     * Purely pixel-level compositing so it rasterizes deterministically on every
     * backend (including the Robolectric software canvas used by unit tests).
     */
    fun applyBackgroundBlur(
        original: Bitmap,
        foregroundMask: Bitmap,
        blurRadius: Float,
        bokehShape: BokehShape = BokehShape.CIRCLE,
        maskThreshold: Int = 128
    ): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val blurred = blurBitmap(original, blurRadius)

        val w = original.width
        val h = original.height
        val maskW = foregroundMask.width.coerceAtLeast(1)
        val maskH = foregroundMask.height.coerceAtLeast(1)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val mx = (x * maskW / w).coerceIn(0, maskW - 1)
                val my = (y * maskH / h).coerceIn(0, maskH - 1)
                val maskAlpha = Color.alpha(foregroundMask.getPixel(mx, my))
                if (maskAlpha < maskThreshold) {
                    result.setPixel(x, y, blurred.getPixel(x, y))
                }
            }
        }

        if (bokehShape != BokehShape.CIRCLE) {
            applyShapeHighlights(result, blurred, bokehShape)
        }

        return result
    }

    /**
     * Re-stamps the bright, already-blurred highlights of the background
     * (pixels with high luma and strong alpha contribution) as the selected
     * bokeh disc shape. This is the subtle "shaped bokeh" look; intensity is
     * driven by pixel luma so it only affects actual highlights.
     */
    private fun applyShapeHighlights(
        result: Bitmap,
        blurred: Bitmap,
        shape: BokehShape
    ) {
        val w = result.width
        val h = result.height
        val canvas = Canvas(result)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = result.getPixel(x, y)
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                // A highlight: bright and relatively desaturated core.
                val luma = (r * 0.3f + g * 0.59f + b * 0.11f)
                if (luma > 200f && (max - min) < 70) {
                    val radius = 2f
                    drawBokehDisc(canvas, x.toFloat(), y.toFloat(), radius, shape, Color.argb(120, r, g, b))
                }
            }
        }
    }

    private fun drawBokehDisc(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        shape: BokehShape,
        color: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        when (shape) {
            BokehShape.HEXAGON -> {
                val path = Path()
                val n = 6
                for (i in 0 until n) {
                    val angle = Math.toRadians((60 * i - 30).toDouble())
                    val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
                    val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            BokehShape.HEART -> {
                val path = Path()
                path.moveTo(cx, cy + radius * 0.35f)
                path.cubicTo(
                    cx - radius, cy - radius * 0.4f,
                    cx - radius * 0.9f, cy - radius,
                    cx - radius * 0.2f, cy - radius * 0.6f
                )
                path.lineTo(cx, cy - radius * 0.35f)
                path.cubicTo(
                    cx + radius * 0.2f, cy - radius * 0.6f,
                    cx + radius * 0.9f, cy - radius,
                    cx + radius, cy - radius * 0.4f
                )
                path.moveTo(cx, cy + radius * 0.35f)
                path.cubicTo(
                    cx + radius, cy - radius * 0.4f,
                    cx + radius * 0.9f, cy - radius,
                    cx + radius * 0.2f, cy - radius * 0.6f
                )
                path.close()
                canvas.drawPath(path, paint)
            }
            BokehShape.CIRCLE -> {
                canvas.drawCircle(cx, cy, radius, paint)
            }
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurWithRenderEffect(result, bitmap, radius)
        } else {
            applySoftwareBlur(result, radius.roundToInt().coerceAtLeast(1))
        }
        return result
    }

    /**
     * RenderNode backend only exists on Android 12+ (RenderEffect via
     * RenderNode.setRenderEffect needs API 31; RenderNode itself needs 29), so
     * anything below 12 falls back to [applySoftwareBlur].
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun blurWithRenderEffect(result: Bitmap, source: Bitmap, radius: Float) {
        val canvas = Canvas(result)
        val renderNode = RenderNode("blur")
        renderNode.setPosition(0, 0, source.width, source.height)
        renderNode.setRenderEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        )
        val renderCanvas = renderNode.beginRecording()
        renderCanvas.drawBitmap(source, 0f, 0f, null)
        renderNode.endRecording()
        canvas.drawRenderNode(renderNode)
    }

    /**
     * Software separable box blur for API < 31. Two passes (horizontal then
     * vertical) repeated twice approximate a gaussian kernel closely enough for
     * a defocus background while staying cheap on CPU.
     */
    internal fun applySoftwareBlur(bitmap: Bitmap, radiusPx: Int) {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 1 || h < 1) return
        val source = IntArray(w * h)
        val target = IntArray(w * h)
        bitmap.getPixels(source, 0, w, 0, 0, w, h)
        repeat(2) {
            boxBlurPass(source, target, w, h, radiusPx, horizontal = true)
            boxBlurPass(target, source, w, h, radiusPx, horizontal = false)
        }
        bitmap.setPixels(source, 0, w, 0, 0, w, h)
    }

    internal fun boxBlurPass(
        input: IntArray,
        output: IntArray,
        w: Int,
        h: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        val length = if (horizontal) w else h
        val stride = if (horizontal) 1 else w
        val count = 2 * radius + 1
        for (outer in 0 until (if (horizontal) h else w)) {
            val base = if (horizontal) outer * w else outer

            fun index(pos: Int): Int = base + pos.coerceIn(0, length - 1) * stride

            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (k in -radius..radius) {
                val c = input[index(k)]
                sumR += Color.red(c)
                sumG += Color.green(c)
                sumB += Color.blue(c)
            }
            for (pos in 0 until length) {
                output[index(pos)] = Color.rgb(
                    (sumR / count).toInt(),
                    (sumG / count).toInt(),
                    (sumB / count).toInt()
                )
                val leaving = input[index(pos - radius)]
                val entering = input[index(pos + radius + 1)]
                sumR += Color.red(entering) - Color.red(leaving)
                sumG += Color.green(entering) - Color.green(leaving)
                sumB += Color.blue(entering) - Color.blue(leaving)
            }
        }
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
