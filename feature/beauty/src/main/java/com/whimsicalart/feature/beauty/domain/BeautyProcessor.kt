package com.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.whimsicalart.feature.beauty.detection.DetectedFace
import com.whimsicalart.feature.beauty.detection.FaceDetectionResult
import kotlin.math.hypot
import kotlin.math.max

open class BeautyProcessor {

    private fun Paint.applySoftLightBlend() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = android.graphics.BlendMode.SOFT_LIGHT
        }
    }

    fun applyAutoBeauty(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float = 0.7f
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        faceResult.faces.forEach { face ->
            smoothSkin(result, face, intensity * 0.5f)
            brightenEyes(result, face, intensity * 0.3f)
            whitenTeeth(result, face, intensity * 0.4f)
        }

        return result
    }

    private fun smoothSkin(bitmap: Bitmap, face: DetectedFace, intensity: Float) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.argb(
                (intensity * 255).toInt(),
                255, 255, 255
            )
            applySoftLightBlend()
        }

        drawFaceSkinRegion(canvas, face, paint)
    }

    private fun brightenEyes(bitmap: Bitmap, face: DetectedFace, intensity: Float) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.argb(
                (intensity * 255).toInt(),
                255, 255, 255
            )
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.ADD
            )
        }

        face.landmarks.leftEye?.let { eye ->
            val eyeRadius = face.bounds.width() / 8
            paint.applyRadialFalloff(eye.x, eye.y, eyeRadius.toFloat())
            canvas.drawCircle(
                eye.x,
                eye.y,
                eyeRadius.toFloat(),
                paint
            )
            paint.shader = null
        }

        face.landmarks.rightEye?.let { eye ->
            val eyeRadius = face.bounds.width() / 8
            paint.applyRadialFalloff(eye.x, eye.y, eyeRadius.toFloat())
            canvas.drawCircle(
                eye.x,
                eye.y,
                eyeRadius.toFloat(),
                paint
            )
            paint.shader = null
        }
    }

    private fun whitenTeeth(bitmap: Bitmap, face: DetectedFace, intensity: Float) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.argb(
                (intensity * 200).toInt(),
                255, 255, 255
            )
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.LIGHTEN
            )
        }

        val mouthLeft = face.landmarks.mouthLeft
        val mouthRight = face.landmarks.mouthRight

        if (mouthLeft != null && mouthRight != null) {
            val mouthWidth = mouthRight.x - mouthLeft.x
            val mouthCenter = PointF(
                (mouthLeft.x + mouthRight.x) / 2,
                (mouthLeft.y + mouthRight.y) / 2
            )

            val teethArea = Rect(
                (mouthCenter.x - mouthWidth / 3).toInt(),
                (mouthCenter.y - mouthWidth / 6).toInt(),
                (mouthCenter.x + mouthWidth / 3).toInt(),
                (mouthCenter.y + mouthWidth / 6).toInt()
            )

            canvas.drawRect(teethArea, paint)
        }
    }

    fun applySkinSmoothing(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        brushRadius: Float,
        intensity: Float
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = Color.argb(
                (intensity * 255).toInt(),
                255, 255, 255
            )
            applySoftLightBlend()
        }

        faceResult.faces.forEach { face ->
            drawFaceSkinRegion(canvas, face, paint)
        }

        return result
    }

    private fun drawFaceSkinRegion(
        canvas: Canvas,
        face: DetectedFace,
        paint: Paint,
        insetX: Float = 0.08f,
        insetY: Float = 0.16f
    ) {
        val oval = faceSkinOval(face, insetX, insetY)

        val color = paint.color
        paint.shader = RadialGradient(
            oval.centerX(), oval.centerY(),
            maxOf(oval.width(), oval.height()) / 2f,
            intArrayOf(
                color,
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
            ),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(oval, paint)
        paint.shader = null
    }

    internal fun faceSkinOval(
        face: DetectedFace,
        insetX: Float = 0.08f,
        insetY: Float = 0.16f
    ): RectF {
        val b = face.bounds
        val h = b.height().toFloat()

        val cheekLeft = face.landmarks.leftCheek
        val cheekRight = face.landmarks.rightCheek
        val cx: Float
        val halfW: Float
        if (cheekLeft != null && cheekRight != null) {
            cx = (cheekLeft.x + cheekRight.x) / 2f
            halfW = (cheekRight.x - cheekLeft.x) / 2f
        } else {
            cx = b.exactCenterX()
            halfW = b.width() * (0.42f)
        }

        val top = b.top + h * insetY
        val bottom = b.bottom - h * insetY
        val cy = (top + bottom) / 2f
        val halfH = (bottom - top) / 2f

        return RectF(cx - halfW, top, cx + halfW, bottom)
    }

    fun applyTeethWhitening(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        faceResult.faces.forEach { face ->
            whitenTeeth(result, face, intensity)
        }

        return result
    }

    fun applyEyeBrightening(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        faceResult.faces.forEach { face ->
            brightenEyes(result, face, intensity)
        }

        return result
    }

    fun applyBrightnessPen(
        bitmap: Bitmap,
        strokes: List<BrushStroke>,
        brightness: Float = 1f
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (strokes.isEmpty()) return result
        val canvas = Canvas(result)
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(
                (125f * brightness).toInt().coerceIn(0, 255),
                255, 255, 255
            )
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.ADD
            )
        }

        strokes.forEach { stroke ->
            stroke.points.forEach { point ->
                canvas.drawCircle(point.x, point.y, stroke.size, paint)
            }
        }

        return result
    }

    private fun overlayPaint(color: Int, intensity: Float): Paint {
        val alpha = (intensity * 200).toInt().coerceIn(0, 255)
        val argb = Color.argb(
            alpha,
            color ushr 16 and 0xff,
            color ushr 8 and 0xff,
            color and 0xff
        )
        val paint = Paint()
        paint.isAntiAlias = true
        paint.color = argb
        paint.xfermode = android.graphics.PorterDuffXfermode(
            android.graphics.PorterDuff.Mode.SRC_OVER
        )
        return paint
    }

    /**
     * Points the paint's shader at a radial falloff (solid until [startFrac] of
     * the radius, then fading to fully transparent at the edge) so makeup and
     * eye masks blend into the skin instead of leaving a hard circle. The
     * caller must reset `paint.shader = null` after drawing.
     */
    private fun Paint.applyRadialFalloff(
        centerX: Float,
        centerY: Float,
        radius: Float,
        startFrac: Float = 0.5f
    ) {
        shader = RadialGradient(
            centerX, centerY,
            radius.coerceAtLeast(1f),
            intArrayOf(color, Color.TRANSPARENT),
            floatArrayOf(startFrac, 1f),
            Shader.TileMode.CLAMP
        )
    }

    open fun applyLipstick(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val left = face.landmarks.mouthLeft
            val right = face.landmarks.mouthRight
            if (left != null && right != null) {
                val cx = (left.x + right.x) / 2f
                val cy = (left.y + right.y) / 2f
                val width = Math.abs(right.x - left.x)
                val height = width * 0.6f
                val oval = RectF(
                    cx - width / 2f,
                    cy - height / 2f,
                    cx + width / 2f,
                    cy + height / 2f
                )
                paint.applyRadialFalloff(cx, cy, max(width, height) * 0.6f, startFrac = 0.45f)
                canvas.drawOval(oval, paint)
            }
            paint.shader = null
        }
        return result
    }

    fun applyBlush(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val radius = face.bounds.width() * 0.14f
            face.landmarks.leftCheek?.let { cheek ->
                paint.applyRadialFalloff(cheek.x, cheek.y, radius)
                canvas.drawCircle(cheek.x, cheek.y, radius, paint)
            }
            face.landmarks.rightCheek?.let { cheek ->
                paint.applyRadialFalloff(cheek.x, cheek.y, radius)
                canvas.drawCircle(cheek.x, cheek.y, radius, paint)
            }
            paint.shader = null
        }
        return result
    }

    fun applyEyeShadow(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val width = face.bounds.width() * 0.3f
            val height = face.bounds.width() * 0.18f
            listOfNotNull(face.landmarks.leftEye, face.landmarks.rightEye)
                .forEach { eye ->
                    val oval = RectF(
                        eye.x - width / 2f,
                        eye.y - height,
                        eye.x + width / 2f,
                        eye.y
                    )
                    paint.applyRadialFalloff(
                        oval.centerX(), oval.centerY(),
                        max(width, height) * 0.6f
                    )
                    canvas.drawOval(oval, paint)
                }
            paint.shader = null
        }
        return result
    }

    fun applyEyeliner(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity).apply {
            style = Paint.Style.STROKE
            strokeWidth = faceResult.faces.firstOrNull()?.bounds?.width()?.times(0.02f) ?: 4f
        }

        faceResult.faces.forEach { face ->
            val width = face.bounds.width() * 0.26f
            listOfNotNull(face.landmarks.leftEye, face.landmarks.rightEye)
                .forEach { eye ->
                    val oval = RectF(
                        eye.x - width / 2f,
                        eye.y - width * 0.15f,
                        eye.x + width / 2f,
                        eye.y + width * 0.15f
                    )
                    canvas.drawOval(oval, paint)
                }
        }
        return result
    }

    fun applyFoundation(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val skin = Rect(
                face.bounds.left + face.bounds.width() / 6,
                face.bounds.top + face.bounds.height() / 6,
                face.bounds.right - face.bounds.width() / 6,
                face.bounds.bottom - face.bounds.height() / 6
            )
            val radius = hypot(skin.width().toFloat(), skin.height().toFloat()) * 0.6f
            paint.applyRadialFalloff(skin.exactCenterX(), skin.exactCenterY(), radius, startFrac = 0.5f)
            canvas.drawRect(skin, paint)
            paint.shader = null
        }
        return result
    }

    fun applyHairColor(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val width = face.bounds.width() * 0.8f
            val height = face.bounds.height() * 0.5f
            val top = face.bounds.top
            val cx = face.bounds.exactCenterX()
            val oval = RectF(
                cx - width / 2f,
                top - height * 0.7f,
                cx + width / 2f,
                top + height * 0.3f
            )
            paint.applyRadialFalloff(oval.centerX(), oval.centerY(), max(width, height) * 0.6f, startFrac = 0.5f)
            canvas.drawOval(oval, paint)
            paint.shader = null
        }
        return result
    }

    fun applyFaceReshape(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        faceSlim: Float,
        eyeEnlarge: Float,
        noseAdjust: Float,
        jawAdjust: Float
    ): Bitmap {
        if (faceSlim == 0f && eyeEnlarge == 0f && noseAdjust == 0f && jawAdjust == 0f) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        val width = bitmap.width
        val height = bitmap.height
        val meshWidth = 28
        val meshHeight = 28
        val columns = meshWidth + 1
        val rows = meshHeight + 1
        val verts = FloatArray(columns * rows * 2)
        var idx = 0
        for (row in 0..meshHeight) {
            for (col in 0..meshWidth) {
                verts[idx++] = col * width.toFloat() / meshWidth
                verts[idx++] = row * height.toFloat() / meshHeight
            }
        }

        faceResult.faces.forEach { face ->
            val midX = face.bounds.exactCenterX()
            val faceHeight = face.bounds.height().coerceAtLeast(1).toFloat()
            idx = 0
            for (row in 0..meshHeight) {
                for (col in 0..meshWidth) {
                    val x = verts[idx]
                    val y = verts[idx + 1]
                    val vy = (y - face.bounds.top) / faceHeight

                    if (faceSlim != 0f) {
                        val cheek = smoothstep(0.25f, 0.9f, vy)
                        verts[idx] = x - (x - midX) * cheek * faceSlim * 0.16f
                    }
                    if (jawAdjust != 0f) {
                        val jawArea = smoothstep(0.55f, 0.9f, vy)
                        verts[idx] = x + (x - midX) * jawArea * jawAdjust * 0.2f
                    }
                    if (eyeEnlarge != 0f) {
                        listOfNotNull(face.landmarks.leftEye, face.landmarks.rightEye)
                            .forEach { eye ->
                                val dist = hypot(x - eye.x, y - eye.y)
                                val radius = face.bounds.width() * 0.16f
                                val falloff = 1f / (1f + (dist / radius) * (dist / radius))
                                val pull = eyeEnlarge * 0.4f * falloff
                                verts[idx] += (x - eye.x) * pull
                                verts[idx + 1] += (y - eye.y) * pull
                            }
                    }
                    if (noseAdjust != 0f) {
                        face.landmarks.nose?.let { nose ->
                            val dist = hypot(x - nose.x, y - nose.y)
                            val radius = face.bounds.width() * 0.22f
                            val falloff = 1f / (1f + (dist / radius) * (dist / radius))
                            val pull = noseAdjust * 0.3f * falloff
                            verts[idx] += (x - nose.x) * pull
                            verts[idx + 1] += (y - nose.y) * pull
                        }
                    }
                    idx += 2
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { isFilterBitmap = true }
        canvas.drawBitmapMesh(bitmap, meshWidth, meshHeight, verts, 0, null, 0, paint)
        return result
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun hypot(dx: Float, dy: Float): Float {
        return kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }

    fun applyDarkCircleRemoval(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        faceResult.faces.forEach { face ->
            val canvas = Canvas(result)
            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.argb(
                    (intensity * 140).toInt().coerceIn(0, 255),
                    255, 255, 255
                )
                xfermode = android.graphics.PorterDuffXfermode(
                    android.graphics.PorterDuff.Mode.ADD
                )
            }
            val radius = face.bounds.width() / 7f
            val offsetY = face.bounds.height() * 0.06f
            face.landmarks.leftEye?.let {
                paint.applyRadialFalloff(it.x, it.y + offsetY, radius)
                canvas.drawCircle(it.x, it.y + offsetY, radius, paint)
            }
            face.landmarks.rightEye?.let {
                paint.applyRadialFalloff(it.x, it.y + offsetY, radius)
                canvas.drawCircle(it.x, it.y + offsetY, radius, paint)
            }
            paint.shader = null
        }
        return result
    }

    fun applySpotRemoval(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val radius = (3 + 5 * intensity).toInt().coerceIn(1, 12)
        val blurred = boxBlur(result, radius)

        faceResult.faces.forEach { face ->
            val skinRect = Rect(
                face.bounds.left + face.bounds.width() / 6,
                face.bounds.top + face.bounds.height() / 6,
                face.bounds.right - face.bounds.width() / 6,
                face.bounds.bottom - face.bounds.height() / 6
            )
            val canvas = Canvas(result)
            val paint = Paint().apply {
                isAntiAlias = true
                alpha = (intensity * 200).toInt().coerceIn(0, 255)
            }
            canvas.save()
            canvas.clipRect(skinRect)
            canvas.drawBitmap(blurred, 0f, 0f, paint)
            canvas.restore()
        }
        return result
    }

    fun applyWrinkleRemoval(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val radius = (6 + 10 * intensity).toInt().coerceIn(2, 20)
        val blurred = boxBlur(result, radius)

        faceResult.faces.forEach { face ->
            val foreheadRect = Rect(
                face.bounds.left + face.bounds.width() / 5,
                face.bounds.top + face.bounds.height() / 10,
                face.bounds.right - face.bounds.width() / 5,
                face.bounds.top + face.bounds.height() / 3
            )
            val mouthRect = Rect(
                face.bounds.left + face.bounds.width() / 3,
                face.bounds.top + face.bounds.height() / 2,
                face.bounds.right - face.bounds.width() / 3,
                face.bounds.bottom - face.bounds.height() / 12
            )
            listOf(foreheadRect, mouthRect).forEach { region ->
                val canvas = Canvas(result)
                val paint = Paint().apply {
                    isAntiAlias = true
                    alpha = (intensity * 160).toInt().coerceIn(0, 255)
                }
                canvas.save()
                canvas.clipRect(region)
                canvas.drawBitmap(blurred, 0f, 0f, paint)
                canvas.restore()
            }
        }
        return result
    }

    fun applySkinTone(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float
    ): Bitmap {
        val t = intensity.coerceIn(-1f, 1f)
        if (t == 0f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val alpha = (Math.abs(t.toDouble()) * 160).toInt().coerceIn(0, 255)
        val red = if (t > 0) 255 else (255 + (185 - 255) * Math.abs(t.toDouble())).toInt()
        val green = (255 - (255 - 205) * Math.abs(t.toDouble())).toInt()
        val blue = if (t < 0) 255 else (255 + (180 - 255) * Math.abs(t.toDouble())).toInt()

        faceResult.faces.forEach { face ->
            val skinRect = Rect(
                face.bounds.left + face.bounds.width() / 4,
                face.bounds.top + face.bounds.height() / 4,
                face.bounds.right - face.bounds.width() / 4,
                face.bounds.bottom - face.bounds.height() / 4
            )
            val canvas = Canvas(result)
            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.argb(alpha, red, green, blue)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    blendMode = android.graphics.BlendMode.SOFT_LIGHT
                } else {
                    xfermode = android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.OVERLAY
                    )
                }
            }
            canvas.drawRect(skinRect, paint)
        }
        return result
    }

    private fun boxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val src = IntArray(width * height)
        val tmp = IntArray(width * height)
        bitmap.getPixels(tmp, 0, width, 0, 0, width, height)

        blurHorizontal(tmp, src, width, height, radius)
        blurVertical(src, tmp, width, height, radius)

        return Bitmap.createBitmap(tmp, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blurHorizontal(
        src: IntArray,
        dst: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        for (y in 0 until height) {
            val rowBase = y * width
            for (x in 0 until width) {
                val left = (x - radius).coerceAtLeast(0)
                val right = (x + radius).coerceAtMost(width - 1)
                val count = (right - left + 1).toLong()
                var a = 0L
                var r = 0L
                var g = 0L
                var b = 0L
                for (i in left..right) {
                    val c = src[rowBase + i]
                    a += c ushr 24 and 0xff
                    r += c ushr 16 and 0xff
                    g += c ushr 8 and 0xff
                    b += c and 0xff
                }
                dst[rowBase + x] = intColor(a, r, g, b, count)
            }
        }
    }

    private fun blurVertical(
        src: IntArray,
        dst: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        for (x in 0 until width) {
            for (y in 0 until height) {
                val top = (y - radius).coerceAtLeast(0)
                val bottom = (y + radius).coerceAtMost(height - 1)
                val count = (bottom - top + 1).toLong()
                var a = 0L
                var r = 0L
                var g = 0L
                var b = 0L
                for (i in top..bottom) {
                    val c = src[i * width + x]
                    a += c ushr 24 and 0xff
                    r += c ushr 16 and 0xff
                    g += c ushr 8 and 0xff
                    b += c and 0xff
                }
                dst[y * width + x] = intColor(a, r, g, b, count)
            }
        }
    }

    private fun intColor(a: Long, r: Long, g: Long, b: Long, count: Long): Int {
        return ((a / count).toInt() shl 24) or
            ((r / count).toInt() shl 16) or
            ((g / count).toInt() shl 8) or
            (b / count).toInt()
    }
}
