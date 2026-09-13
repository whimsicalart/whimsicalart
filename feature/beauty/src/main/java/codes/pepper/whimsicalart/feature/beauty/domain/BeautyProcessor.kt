package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.os.Build
import codes.pepper.whimsicalart.core.domain.UniformFit
import codes.pepper.whimsicalart.feature.beauty.detection.DetectedFace
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

open class BeautyProcessor {

    private fun Paint.applySoftLightBlend() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = android.graphics.BlendMode.SOFT_LIGHT
        }
    }

    fun applyAutoBeauty(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float = 0.7f,
        skinPath: Path? = null
    ): Bitmap {
        if (intensity <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        faceResult.faces.forEach { face ->
            smoothSkin(result, face, intensity * 0.5f, skinPath)
            brightenEyes(result, face, intensity * 0.3f)
            whitenTeeth(result, face, intensity * 0.4f)
        }

        return result
    }

    private fun smoothSkin(
        bitmap: Bitmap,
        face: DetectedFace,
        intensity: Float,
        skinPath: Path? = null
    ) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.argb(
                (intensity * 255).toInt(),
                255, 255, 255
            )
            applySoftLightBlend()
        }

        drawFaceSkinRegion(canvas, face, paint, skinPath = skinPath)
    }

    private fun brightenEyes(bitmap: Bitmap, face: DetectedFace, intensity: Float) {
        val canvas = Canvas(bitmap)
        val color = Color.argb(
            (intensity * 255).toInt().coerceIn(0, 255),
            255, 255, 255
        )
        val xfermode = android.graphics.PorterDuffXfermode(
            android.graphics.PorterDuff.Mode.ADD
        )
        val fallbackRadius = (face.bounds.width() / 8).toFloat()

        listOf(
            face.landmarks.leftEyeContour to face.landmarks.leftEye,
            face.landmarks.rightEyeContour to face.landmarks.rightEye
        ).forEach { (contour, anchor) ->
            FeatureMaskBuilder.eyeMask(contour, anchor, fallbackRadius)
                ?.let { eyePath ->
                    MaskedEffectRenderer.drawFeatheredMask(canvas, eyePath, color, xfermode)
                }
        }
    }

    private fun whitenTeeth(bitmap: Bitmap, face: DetectedFace, intensity: Float) {
        val color = Color.argb(
            (intensity * 200).toInt().coerceIn(0, 255),
            255, 255, 255
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.LIGHTEN
            )
        }

        val mouthLeft = face.landmarks.mouthLeft
        val mouthRight = face.landmarks.mouthRight
        // Per-pixel teeth-segmentation alpha mask: only bright tooth pixels inside
        // the mouth aperture are whitened, so gums/gloss stay put (a solid LIGHTEN
        // over the whole geometric aperture used to lighten them too). The aperture
        // is the Face Mesh inner-lip ring when available, falling back to the lens.
        val polygon = FeatureMaskBuilder.teethPolygon(
            face.landmarks.lipsContour, mouthLeft, mouthRight,
            face.landmarks.mouthApertureContour
        )
        val teethMask = TeethMaskProcessor.toothMask(bitmap, polygon)
        if (teethMask != null) {
            Canvas(bitmap).drawBitmap(teethMask, 0f, 0f, paint)
            teethMask.recycle()
            return
        }
    }

    fun applySkinSmoothing(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        brushRadius: Float,
        intensity: Float,
        skinPath: Path? = null
    ): Bitmap {
        if (intensity <= 0f) return bitmap
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
            drawFaceSkinRegion(canvas, face, paint, skinPath = skinPath)
        }

        return result
    }

    /**
     * Draws the beautifying blend over the face's skin. When [skinPath] (the
     * machine-learnt skin silhouette) is provided it is clipped to a shell
     * around the face so the effect follows true skin pixels (leaving eyes,
     * brows, lips and the mouth untouched) without spilling onto the neck, ears
     * or a neighbouring face in a group photo. Falls back to the geometric face
     * contour/oval when [skinPath] is null.
     */
    private fun drawFaceSkinRegion(
        canvas: Canvas,
        face: DetectedFace,
        paint: Paint,
        insetX: Float = 0.08f,
        insetY: Float = 0.16f,
        skinPath: Path? = null
    ) {
        val region = faceSkinRegion(face, skinPath, insetX, insetY)
        if (region != null) {
            MaskedEffectRenderer.drawFeatheredMask(canvas, region, paint)
        }
    }

    /**
     * Resolves the per-face skin region to render: the machine-learnt [skinPath]
     * clipped to a shell around [face]'s bounds when available, otherwise the
     * geometric face contour/oval.
     */
    private fun faceSkinRegion(
        face: DetectedFace,
        skinPath: Path?,
        insetX: Float = 0.08f,
        insetY: Float = 0.16f
    ): Path? {
        val oval = faceSkinOval(face, insetX, insetY)
        val geometric = FeatureMaskBuilder.faceMask(face.landmarks.faceContour, oval)
            ?: return null
        val skin = skinPath ?: return geometric
        return intersectPath(skin, faceShell(skin, face))
    }

    private fun intersectPath(path: Path, bounds: RectF): Path? {
        val clip = Region(
            bounds.left.toInt().coerceAtLeast(Int.MIN_VALUE),
            bounds.top.toInt().coerceAtLeast(Int.MIN_VALUE),
            bounds.right.toInt(),
            bounds.bottom.toInt()
        )
        if (clip.isEmpty) return null
        val region = Region()
        region.setPath(path, clip)
        return region.boundaryPath
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
        if (intensity <= 0f) return bitmap
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
        if (intensity <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        faceResult.faces.forEach { face ->
            brightenEyes(result, face, intensity)
        }

        return result
    }

    /**
     * The beauty pen's manual brightness/colour brush. Strokes carry NORMALIZED
     * positions (source fractions) and a width fraction, exactly like the
     * standalone pen, so a stroke bakes identically at committed full-res and at
     * preview resolution regardless of the layer's running bitmap dims.
     * Each point stamps a hard circle of the picked [color] added (dodge) to the
     * underlying pixels: dark regions move toward the picked colour and bright
     * regions clip toward white. [brightness] drives the alpha. A per-pixel CPU
     * pass (like the mosaic/blur brush stamps) so it stays unit-testable under
     * Robolectric's software canvas.
     */
    fun applyBrightnessPen(
        bitmap: Bitmap,
        strokes: List<BrushStroke>,
        brightness: Float = 1f,
        color: Int = android.graphics.Color.WHITE
    ): Bitmap {
        if (brightness <= 0f || strokes.isEmpty()) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val fade = (125f * brightness).coerceIn(0f, 255f) / 255f
        val addR = (Color.red(color) * fade).toInt().coerceIn(0, 255)
        val addG = (Color.green(color) * fade).toInt().coerceIn(0, 255)
        val addB = (Color.blue(color) * fade).toInt().coerceIn(0, 255)
        var scratch = IntArray(0)

        for (stroke in strokes) {
            // Anchored strokes re-express through the uniform fit of their
            // reference base into the running bitmap (docs/effect_stack.md →
            // *Positional layers*); an unset reference maps against the bitmap
            // itself (identity), preserving the historical math.
            val refW = stroke.referenceWidth.takeIf { it > 0 } ?: w
            val refH = stroke.referenceHeight.takeIf { it > 0 } ?: h
            val fit = UniformFit.of(w, h, refW, refH)
            val radiusPx = (fit.measureX(stroke.size, refW) / 2f).coerceAtLeast(1f)
            val r = kotlin.math.ceil(radiusPx).toInt()
            val radiusSq = radiusPx * radiusPx
            for (point in stroke.points) {
                val cx = fit.frameX(point.x, refW).roundToInt()
                val cy = fit.frameY(point.y, refH).roundToInt()
                val left = (cx - r).coerceAtLeast(0)
                val top = (cy - r).coerceAtLeast(0)
                val right = (cx + r).coerceAtMost(w - 1)
                val bottom = (cy + r).coerceAtMost(h - 1)
                val bw = right - left + 1
                val bh = bottom - top + 1
                if (bw <= 0 || bh <= 0) continue
                if (scratch.size < bw * bh) scratch = IntArray(bw * bh)
                result.getPixels(scratch, 0, bw, left, top, bw, bh)
                for (yy in top..bottom) {
                    val dy = yy - cy
                    var idx = (yy - top) * bw
                    for (xx in left..right) {
                        val dx = xx - cx
                        if (dx * dx + dy * dy <= radiusSq) {
                            val d = scratch[idx]
                            scratch[idx] = Color.argb(
                                Color.alpha(d),
                                (Color.red(d) + addR).coerceAtMost(255),
                                (Color.green(d) + addG).coerceAtMost(255),
                                (Color.blue(d) + addB).coerceAtMost(255)
                            )
                        }
                        idx++
                    }
                }
                result.setPixels(scratch, 0, bw, left, top, bw, bh)
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
        if (intensity <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val left = face.landmarks.mouthLeft
            val right = face.landmarks.mouthRight
            FeatureMaskBuilder.lipMask(
                face.landmarks.lipsContour, left, right,
                face.landmarks.mouthApertureContour
            )
                ?.let { lipPath ->
                    MaskedEffectRenderer.drawFeatheredMask(
                        canvas, lipPath, paint.color, paint.xfermode
                    )
                }
        }
        return result
    }

    fun applyBlush(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val fallbackRadius = face.bounds.width() * 0.14f
            val landmarks = face.landmarks
            val boundsHeight = face.bounds.height().toFloat()
            FeatureMaskBuilder.cheekMask(
                landmarks.faceContour, landmarks.leftCheek, isLeft = true,
                boundsHeight, fallbackRadius
            )?.let { cheekPath ->
                MaskedEffectRenderer.drawFeatheredMask(canvas, cheekPath, paint.color, paint.xfermode)
            }
            FeatureMaskBuilder.cheekMask(
                landmarks.faceContour, landmarks.rightCheek, isLeft = false,
                boundsHeight, fallbackRadius
            )?.let { cheekPath ->
                MaskedEffectRenderer.drawFeatheredMask(canvas, cheekPath, paint.color, paint.xfermode)
            }
        }
        return result
    }

    fun applyEyeShadow(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val fallbackRadius = face.bounds.width() * 0.15f
            listOf(
                face.landmarks.leftEyeContour to face.landmarks.leftEye,
                face.landmarks.rightEyeContour to face.landmarks.rightEye
            ).forEach { (contour, eye) ->
                FeatureMaskBuilder.eyeMask(contour, eye, fallbackRadius)
                    ?.let { eyePath ->
                        MaskedEffectRenderer.drawFeatheredMask(canvas, eyePath, paint.color, paint.xfermode)
                    }
            }
        }
        return result
    }

    fun applyEyeliner(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val strokeWidth = face.bounds.width() * 0.02f
            val fallbackRadius = face.bounds.width() * 0.12f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.strokeCap = Paint.Cap.ROUND
            listOf(
                face.landmarks.leftEyeContour to face.landmarks.leftEye,
                face.landmarks.rightEyeContour to face.landmarks.rightEye
            ).forEach { (contour, eye) ->
                FeatureMaskBuilder.eyeMask(contour, eye, fallbackRadius)
                    ?.let { canvas.drawPath(it, paint) }
            }
        }
        return result
    }

    fun applyFoundation(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float,
        skinPath: Path? = null
    ): Bitmap {
        if (intensity <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            val foundation = skinPath?.let { intersectPath(it, faceShell(it, face)) }
                ?: run {
                    val fallback = RectF(
                        (face.bounds.left + face.bounds.width() / 6).toFloat(),
                        (face.bounds.top + face.bounds.height() / 6).toFloat(),
                        (face.bounds.right - face.bounds.width() / 6).toFloat(),
                        (face.bounds.bottom - face.bounds.height() / 6).toFloat()
                    )
                    FeatureMaskBuilder.faceMask(face.landmarks.faceContour, fallback)
                }
            if (foundation != null) {
                MaskedEffectRenderer.drawFeatheredMask(canvas, foundation, paint.color, paint.xfermode)
            }
        }
        return result
    }

    private fun faceShell(skinPath: Path, face: DetectedFace): RectF {
        val bounds = face.bounds
        return RectF(
            bounds.left - bounds.width() * 0.15f,
            bounds.top - bounds.height() * 0.10f,
            bounds.right + bounds.width() * 0.15f,
            bounds.bottom + bounds.height() * 0.10f
        )
    }

    fun applyHairColor(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        color: Int,
        intensity: Float,
        hairPath: Path? = null
    ): Bitmap {
        if (intensity <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = overlayPaint(color, intensity)

        faceResult.faces.forEach { face ->
            // Prefer the machine-learnt hair silhouette (dense segmentation path)
            // over the geometric dome pinned to the face bounds. The path already
            // covers the whole hair region, so horizontal wander between faces in
            // a group photo is avoided.
            val hair = hairPath ?: FeatureMaskBuilder.hairMask(face.bounds)
            val falloffPaint = Paint(paint).apply { shader = null }
            val bounds = RectF()
            hair.computeBounds(bounds, true)
            falloffPaint.applyRadialFalloff(
                bounds.centerX(), bounds.top,
                max(bounds.width(), bounds.height()) * 0.6f,
                startFrac = 0.5f
            )
            canvas.drawPath(hair, falloffPaint)
        }
        return result
    }

    fun applySlim(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        faceSlim: Float
    ): Bitmap {
        if (faceSlim == 0f) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val verts = baseMesh(width, height)
        faceResult.faces.forEach { face ->
            val midX = face.bounds.exactCenterX()
            val faceHeight = face.bounds.height().coerceAtLeast(1).toFloat()
            val halfWidth = (face.bounds.width().coerceAtLeast(2) / 2).toFloat()
            var idx = 0
            for (row in 0..FACE_MESH_HEIGHT) {
                for (col in 0..FACE_MESH_WIDTH) {
                    val x = verts[idx]
                    val y = verts[idx + 1]
                    val vy = (y - face.bounds.top) / faceHeight
                    val windowX = localHorizontalWindow(x - midX, halfWidth)
                    val cheek = smoothstep(0.25f, 0.9f, vy)
                    verts[idx] = x - (x - midX) * cheek * windowX * faceSlim * 0.16f
                    idx += 2
                }
            }
        }
        return drawFaceWarp(bitmap, width, height, verts)
    }

    fun applyJaw(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        jawAdjust: Float
    ): Bitmap {
        if (jawAdjust == 0f) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val verts = baseMesh(width, height)
        faceResult.faces.forEach { face ->
            val midX = face.bounds.exactCenterX()
            val faceHeight = face.bounds.height().coerceAtLeast(1).toFloat()
            val halfWidth = (face.bounds.width().coerceAtLeast(2) / 2).toFloat()
            var idx = 0
            for (row in 0..FACE_MESH_HEIGHT) {
                for (col in 0..FACE_MESH_WIDTH) {
                    val x = verts[idx]
                    val y = verts[idx + 1]
                    val vy = (y - face.bounds.top) / faceHeight
                    val windowX = localHorizontalWindow(x - midX, halfWidth)
                    val jawArea = smoothstep(0.55f, 0.9f, vy)
                    verts[idx] = x + (x - midX) * jawArea * windowX * jawAdjust * 0.2f
                    idx += 2
                }
            }
        }
        return drawFaceWarp(bitmap, width, height, verts)
    }

    fun applyEyeEnlarge(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        eyeEnlarge: Float
    ): Bitmap {
        if (eyeEnlarge == 0f) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val verts = baseMesh(width, height)
        faceResult.faces.forEach { face ->
            var idx = 0
            for (row in 0..FACE_MESH_HEIGHT) {
                for (col in 0..FACE_MESH_WIDTH) {
                    val x = verts[idx]
                    val y = verts[idx + 1]
                    listOfNotNull(face.landmarks.leftEye, face.landmarks.rightEye).forEach { eye ->
                        val dist = hypot(x - eye.x, y - eye.y)
                        val radius = face.bounds.width() * 0.16f
                        val falloff = 1f / (1f + (dist / radius) * (dist / radius))
                        val pull = eyeEnlarge * 0.4f * falloff
                        verts[idx] += (x - eye.x) * pull
                        verts[idx + 1] += (y - eye.y) * pull
                    }
                    idx += 2
                }
            }
        }
        return drawFaceWarp(bitmap, width, height, verts)
    }

    fun applyNose(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        noseAdjust: Float
    ): Bitmap {
        if (noseAdjust == 0f) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val verts = baseMesh(width, height)
        faceResult.faces.forEach { face ->
            face.landmarks.nose?.let { nose ->
                var idx = 0
                for (row in 0..FACE_MESH_HEIGHT) {
                    for (col in 0..FACE_MESH_WIDTH) {
                        val x = verts[idx]
                        val y = verts[idx + 1]
                        val dist = hypot(x - nose.x, y - nose.y)
                        val radius = face.bounds.width() * 0.22f
                        val falloff = 1f / (1f + (dist / radius) * (dist / radius))
                        val pull = noseAdjust * 0.3f * falloff
                        verts[idx] += (x - nose.x) * pull
                        verts[idx + 1] += (y - nose.y) * pull
                        idx += 2
                    }
                }
            }
        }
        return drawFaceWarp(bitmap, width, height, verts)
    }

    private companion object {
        const val FACE_MESH_WIDTH = 28
        const val FACE_MESH_HEIGHT = 28
    }

    private fun baseMesh(width: Int, height: Int): FloatArray {
        val columns = FACE_MESH_WIDTH + 1
        val rows = FACE_MESH_HEIGHT + 1
        val verts = FloatArray(columns * rows * 2)
        var idx = 0
        for (row in 0..FACE_MESH_HEIGHT) {
            for (col in 0..FACE_MESH_WIDTH) {
                verts[idx++] = col * width.toFloat() / FACE_MESH_WIDTH
                verts[idx++] = row * height.toFloat() / FACE_MESH_HEIGHT
            }
        }
        return verts
    }

    private fun drawFaceWarp(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        verts: FloatArray
    ): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { isFilterBitmap = true }
        canvas.drawBitmapMesh(bitmap, FACE_MESH_WIDTH, FACE_MESH_HEIGHT, verts, 0, null, 0, paint)
        return result
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Horizontal proximity window for the face-shaping warps: 1.0 at the face
     * centre, smoothly fading to 0 by ~2.4 half-widths from it. Without the
     * gate the cheek/jaw mesh warps act on every vertex inside the face's
     * vertical band, which bleeds onto other faces at a similar height in a
     * group photo.
     */
    internal fun localHorizontalWindow(dx: Float, halfWidth: Float): Float =
        1f - smoothstep(halfWidth, halfWidth * 2.4f, abs(dx))

    private fun hypot(dx: Float, dy: Float): Float {
        return kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }

    /**
     * Pixels the dark-circle silkworm must drop below the eye ANCHOR so its
     * flat top edge starts at/under the LOWER lash instead of overlapping the
     * eye: the eye landmark sits on/near the outer corner (the contour's first
     * point), roughly halfway up the eye, so the drop is the anchor-to-lowest
     * eye-contour distance plus a small margin. Without an eye contour the
     * anchor is already the raw eye centre and the shift is 0 (the silkworm
     * keeps its built-in grazing offset) - same as the pre-bugfix fallback.
     * `internal` for tests.
     */
    internal fun eyeDropToLash(eye: PointF, eyeContour: List<PointF>): Float {
        if (eyeContour.size < 3) return 0f
        val lowest = eyeContour.maxOf { it.y }
        if (lowest <= eye.y) return 0f
        val eyeHeight = lowest - eyeContour.minOf { it.y }
        return lowest - eye.y + eyeHeight * 0.1f
    }

    fun applyDarkCircleRemoval(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap
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
            // Silkworm (under-eye crescent) region rather than a circle: wider
            // than tall, its top edge grazing the lash line and dipping below
            // the eye - the actual dark-circle zone. The crescent is dropped to
            // the lower lash (a fraction of the eye's own height below the eye
            // anchor) so it lands on the hollow instead of overlapping the eye
            // and lower lid.
            val width = face.bounds.width() * 0.42f
            val height = width * 0.6f
            listOf(
                face.landmarks.leftEye to face.landmarks.leftEyeContour,
                face.landmarks.rightEye to face.landmarks.rightEyeContour
            ).forEach { (eye, contour) ->
                if (eye == null) return@forEach
                val drop = eyeDropToLash(eye, contour)
                val path = FeatureMaskBuilder.silkwormPath(eye.x, eye.y + drop, width, height)
                paint.applyRadialFalloff(eye.x, eye.y + drop + height * 0.6f, width / 2f)
                canvas.drawPath(path, paint)
            }
            paint.shader = null
        }
        return result
    }

    /**
     * Dimension-agnostic smoothing reach for [applySpotRemoval]: a fraction of
     * the live image's short edge (≈ the historical `3 + 5·intensity` px at a
     * 500px short edge), so a preview and the full-size fold soften the same
     * relative reach. `internal` for tests.
     */
    internal fun spotRemovalRadius(shortEdge: Int, intensity: Float): Int =
        (shortEdge * (3f + 5f * intensity) / 500f).roundToInt().coerceAtLeast(1)

    /**
     * Dimension-agnostic smoothing reach for [applyWrinkleRemoval] (see
     * [spotRemovalRadius]): ≈ `6 + 10·intensity` px at a 500px short edge.
     * `internal` for tests.
     */
    internal fun wrinkleRemovalRadius(shortEdge: Int, intensity: Float): Int =
        (shortEdge * (6f + 10f * intensity) / 500f).roundToInt().coerceAtLeast(1)

    fun applySpotRemoval(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float,
        skinPath: Path? = null
    ): Bitmap {
        if (intensity <= 0f) return bitmap
        if (faceResult.faces.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        // One effect, two scales: the smoothing band is a fraction of the live
        // image (≈ the historical 3 + 5·intensity px at a 500px short edge), so
        // a preview and the full-size fold soften the same relative reach.
        val radius = spotRemovalRadius(minOf(bitmap.width, bitmap.height), intensity)
        val blurred = boxBlur(result, radius)

        faceResult.faces.forEach { face ->
            // Clip the smoothing blit to the face skin PATH (ML skin silhouette
            // when available, else the geometric contour/oval) so the softened
            // region follows the actual skin instead of a hard rectangle inset
            // from the bounds.
            val region = faceSkinRegion(face, skinPath)
            if (region != null) {
                val canvas = Canvas(result)
                val paint = Paint().apply {
                    isAntiAlias = true
                    alpha = (intensity * 200).toInt().coerceIn(0, 255)
                }
                canvas.save()
                canvas.clipPath(region)
                canvas.drawBitmap(blurred, 0f, 0f, paint)
                canvas.restore()
            }
        }
        return result
    }

    fun applyWrinkleRemoval(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float
    ): Bitmap {
        if (intensity <= 0f) return bitmap
        if (faceResult.faces.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        // One effect, two scales (see applySpotRemoval): the wrinkle band is a
        // fraction of the live image (≈ the historical 6 + 10·intensity px at a
        // 500px short edge).
        val radius = wrinkleRemovalRadius(minOf(bitmap.width, bitmap.height), intensity)
        val blurred = boxBlur(result, radius)

        faceResult.faces.forEach { face ->
            val foreheadRect = RectF(
                face.bounds.left + face.bounds.width() / 5f,
                face.bounds.top + face.bounds.height() / 10f,
                face.bounds.right - face.bounds.width() / 5f,
                face.bounds.top + face.bounds.height() / 3f
            )
            val mouthRect = RectF(
                face.bounds.left + face.bounds.width() / 3f,
                face.bounds.top + face.bounds.height() / 2f,
                face.bounds.right - face.bounds.width() / 3f,
                face.bounds.bottom - face.bounds.height() / 12f
            )
            // Face-silhouette bands rather than two hard rectangles: each region
            // is a trapezoid defined by the face contour at the band's heights.
            val foreheadMask = FeatureMaskBuilder.verticalBand(
                face.landmarks.faceContour, foreheadRect.top, foreheadRect.bottom, foreheadRect
            )
            val mouthMask = FeatureMaskBuilder.verticalBand(
                face.landmarks.faceContour, mouthRect.top, mouthRect.bottom, mouthRect
            )
            listOf(foreheadMask, mouthMask).forEach { region ->
                val canvas = Canvas(result)
                val paint = Paint().apply {
                    isAntiAlias = true
                    alpha = (intensity * 160).toInt().coerceIn(0, 255)
                }
                canvas.save()
                canvas.clipPath(region)
                canvas.drawBitmap(blurred, 0f, 0f, paint)
                canvas.restore()
            }
        }
        return result
    }

    fun applySkinTone(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        intensity: Float,
        skinPath: Path? = null
    ): Bitmap {
        val t = intensity.coerceIn(-1f, 1f)
        if (t == 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val alpha = (Math.abs(t.toDouble()) * 160).toInt().coerceIn(0, 255)
        val red = if (t > 0) 255 else (255 + (185 - 255) * Math.abs(t.toDouble())).toInt()
        val green = (255 - (255 - 205) * Math.abs(t.toDouble())).toInt()
        val blue = if (t < 0) 255 else (255 + (180 - 255) * Math.abs(t.toDouble())).toInt()

        faceResult.faces.forEach { face ->
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
            // Broader than the smoothing region so the whole face gets tinted:
            // the ML skin silhouette when available, else the face contour path
            // or a generous face oval.
            val region = faceSkinRegion(face, skinPath, insetX = 0.06f, insetY = 0.08f)
            if (region != null) {
                MaskedEffectRenderer.drawFeatheredMask(canvas, region, paint)
            }
        }
        return result
    }

    private fun boxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val src = IntArray(width * height)
        bitmap.getPixels(src, 0, width, 0, 0, width, height)
        return Bitmap.createBitmap(
            boxBlurPixels(src, width, height, radius),
            width, height, Bitmap.Config.ARGB_8888
        )
    }

    /**
     * Separable box blur over a row-major ARGB array (horizontal then vertical
     * pass, non-premultiplied — each channel is averaged independently). The
     * sliding-window passes are integer-exact to the prior per-pixel re-sum;
     * `internal` for the parity test (mirrors MaskedEffectRenderer.boxBlurPixels).
     */
    internal fun boxBlurPixels(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val horizontal = IntArray(w * h)
        blurHorizontal(src, horizontal, w, h, radius)
        val result = IntArray(w * h)
        blurVertical(horizontal, result, w, h, radius)
        return result
    }

    private fun blurHorizontal(
        src: IntArray,
        dst: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        // Classic incremental window instead of re-summing the 2r+1 window from
        // scratch per pixel (O(w·h·r); r reaches ~25-64 for full-res
        // spot/wrinkle 8MP folds) — the window slides one column at a time:
        // add the entering pixel, subtract the leaving one. Same clamped
        // left/right edges and the same count ⇒ integer-exact, O(w·h).
        for (y in 0 until height) {
            val rowBase = y * width
            var count = 0L
            var a = 0L
            var r = 0L
            var g = 0L
            var b = 0L
            for (i in 0..minOf(radius, width - 1)) {
                val c = src[rowBase + i]
                a += c ushr 24 and 0xff
                r += c ushr 16 and 0xff
                g += c ushr 8 and 0xff
                b += c and 0xff
                count++
            }
            for (x in 0 until width) {
                dst[rowBase + x] = intColor(a, r, g, b, count)
                val removeIdx = x - radius
                if (removeIdx >= 0) {
                    val c = src[rowBase + removeIdx]
                    a -= c ushr 24 and 0xff
                    r -= c ushr 16 and 0xff
                    g -= c ushr 8 and 0xff
                    b -= c and 0xff
                    count--
                }
                val addIdx = x + radius + 1
                if (addIdx < width) {
                    val c = src[rowBase + addIdx]
                    a += c ushr 24 and 0xff
                    r += c ushr 16 and 0xff
                    g += c ushr 8 and 0xff
                    b += c and 0xff
                    count++
                }
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
        // Same sliding-window trick as blurHorizontal, along the column axis.
        for (x in 0 until width) {
            var count = 0L
            var a = 0L
            var r = 0L
            var g = 0L
            var b = 0L
            for (i in 0..minOf(radius, height - 1)) {
                val c = src[i * width + x]
                a += c ushr 24 and 0xff
                r += c ushr 16 and 0xff
                g += c ushr 8 and 0xff
                b += c and 0xff
                count++
            }
            for (y in 0 until height) {
                dst[y * width + x] = intColor(a, r, g, b, count)
                val removeIdx = y - radius
                if (removeIdx >= 0) {
                    val c = src[removeIdx * width + x]
                    a -= c ushr 24 and 0xff
                    r -= c ushr 16 and 0xff
                    g -= c ushr 8 and 0xff
                    b -= c and 0xff
                    count--
                }
                val addIdx = y + radius + 1
                if (addIdx < height) {
                    val c = src[addIdx * width + x]
                    a += c ushr 24 and 0xff
                    r += c ushr 16 and 0xff
                    g += c ushr 8 and 0xff
                    b += c and 0xff
                    count++
                }
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
