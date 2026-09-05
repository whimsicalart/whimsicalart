package codes.pepper.whimsicalart.feature.collage.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import codes.pepper.whimsicalart.core.common.BitmapPool
import codes.pepper.whimsicalart.feature.collage.domain.CollageTemplate
import codes.pepper.whimsicalart.feature.collage.domain.FaceAwareCropper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CollageRenderer {

    const val OUTPUT_SIZE = 1600

    suspend fun render(
        resolver: ContentResolver,
        template: CollageTemplate,
        slotUris: List<Uri?>,
        slotRects: Map<Int, RectF>,
        faceCenters: Map<Int, PointF> = emptyMap(),
        isFreeForm: Boolean,
        borderWidthRatio: Float,
        borderColorArgb: Int,
        backgroundColorArgb: Int,
        outputSize: Int = OUTPUT_SIZE
    ): Bitmap? = withContext(Dispatchers.Default) {
        val bitmap = BitmapPool.get(outputSize, outputSize, Bitmap.Config.ARGB_8888)
            ?: Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColorArgb)

        val gapPx = (outputSize * borderWidthRatio).coerceAtLeast(0f)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        template.cells.forEachIndexed { index, cell ->
            val cellRect: RectF =
                if (isFreeForm) slotRects[index] ?: cell.toRectF() else cell.toRectF()
            val inset = gapPx / 2f
            val rect = RectF(
                cellRect.left * outputSize + inset,
                cellRect.top * outputSize + inset,
                cellRect.right * outputSize - inset,
                cellRect.bottom * outputSize - inset
            )
            if (rect.width() <= 0f || rect.height() <= 0f) return@forEachIndexed

            val uri = slotUris.getOrNull(index)
            val sourceBitmap = uri?.let { decodeScaled(resolver, it, rect) }

            if (sourceBitmap != null) {
                drawCover(canvas, sourceBitmap, rect, faceCenters[index])
                sourceBitmap.recycle()
            } else if (!isFreeForm) {
                canvas.drawColor(Color.DKGRAY)
            }
        }

        if (!isFreeForm) {
            // Draw the border seams on top with the border color.
            val borderPaint = Paint().apply {
                isAntiAlias = true
                color = borderColorArgb
                style = Paint.Style.FILL
            }
            template.cells.forEach { cell ->
                val rect = RectF(
                    cell.left * outputSize,
                    cell.top * outputSize,
                    cell.right * outputSize,
                    cell.bottom * outputSize
                )
                canvas.drawRect(
                    rect.left,
                    rect.top,
                    rect.left + gapPx,
                    rect.bottom,
                    borderPaint
                )
                canvas.drawRect(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.top + gapPx,
                    borderPaint
                )
            }
        }

        bitmap
    }

    private fun decodeScaled(
        resolver: ContentResolver,
        uri: Uri,
        rect: RectF
    ): Bitmap? {
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun drawCover(
        canvas: Canvas,
        source: Bitmap,
        target: RectF,
        faceCenter: PointF?
    ) {
        val targetAspect = target.width() / target.height()
        val srcRect = FaceAwareCropper.cropWindow(
            sourceW = source.width,
            sourceH = source.height,
            targetAspect = targetAspect,
            faceCenter = faceCenter
        )
        val dstRect = Rect(
            target.left.toInt(),
            target.top.toInt(),
            target.right.toInt(),
            target.bottom.toInt()
        )
        canvas.drawBitmap(source, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
