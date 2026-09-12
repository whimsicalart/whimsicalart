package codes.pepper.whimsicalart.feature.beauty.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * MediaPipe Tasks (LiteRT) [ImageSegmenter] over the bundled
 * `skin_segmenter.tflite` (the official SelfieMulticlass model, which classifies
 * pixels as background / hair / body-skin / face-skin / clothes / others). Only
 * the **face-skin** channel is extracted so beauty tools polish real skin while
 * leaving eyes, brows, lips, hair and the neck/hands (body-skin) untouched.
 *
 * The class label index is discovered at runtime from the model metadata and
 * falls back to `3` (the documented face-skin channel) when the metadata cannot
 * be read.
 *
 * The task is pinned to the **CPU** delegate (`BaseOptions.Delegate.CPU`): the
 * bundled model runs segmentation ops the MediaPipe default (GPU) delegate
 * cannot execute, so without an explicit delegate the task fails to init on a
 * real device and silently falls back to the geometric cheek ellipses.
 */
class MediaPipeSkinSegmenter(context: Context) : SkinSegmenter {

    private val segmenter: ImageSegmenter? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            ImageSegmenter.createFromOptions(
                context,
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET)
                            .setDelegate(Delegate.CPU)
                            .build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputConfidenceMasks(true)
                    .build()
            )
        }.getOrNull()
    }

    private val faceSkinLabelIndex: Int by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        segmenter?.getLabels()
            ?.indexOfFirst { label ->
                label.contains("face", ignoreCase = true) &&
                    label.contains("skin", ignoreCase = true)
            }
            ?.takeIf { it >= 0 }
            ?: FACE_SKIN_FALLBACK_INDEX
    }

    override fun segment(source: Bitmap): Bitmap? {
        val task = segmenter
        if (task == null) {
            android.util.Log.e("BeautyGeometryML", "SKIN: segmenter init failed")
            return null
        }
        if (source.isRecycled) return null
        return runCatching {
            val maskW = source.width
            val maskH = source.height
            // Own the pixels handed to native: build the MPImage from a personal
            // ARGB copy and recycle it only after `segment` returns, so a
            // caller-side recycle (BeautyGeometryResolver) or an overlapping
            // resolve can never invalidate a bitmap the native inference still
            // holds (AndroidBitmap_lockPixels -2 → SIGSEGV on-device).
            val owned = source.copy(Bitmap.Config.ARGB_8888, false)
            if (owned == null) return null
            val image: MPImage = BitmapImageBuilder(owned).build()
            try {
                val result = task.segment(image)
                val masks = result.confidenceMasks().orElse(null)
                if (masks == null || masks.isEmpty()) {
                    android.util.Log.e("BeautyGeometryML", "SKIN: confidenceMasks empty labels=${task.getLabels()} idx=$faceSkinLabelIndex")
                    return null
                }
                if (faceSkinLabelIndex >= masks.size) {
                    android.util.Log.e("BeautyGeometryML", "SKIN: idx $faceSkinLabelIndex >= masks ${masks.size}")
                    return null
                }
                val allAlpha = computeAlphaMask(masks[faceSkinLabelIndex], maskW, maskH)
                masks.forEach { m -> m.close() }
                android.util.Log.d(
                    "BeautyGeometryML",
                    "SKIN: mask ${masks[faceSkinLabelIndex].width}x${masks[faceSkinLabelIndex].height} -> alpha ${allAlpha.width}x${allAlpha.height}"
                )
                allAlpha
            } finally {
                image.close()
                if (!owned.isRecycled) owned.recycle()
            }
        }.getOrElse { e ->
            android.util.Log.e("BeautyGeometryML", "SKIN: segment threw", e)
            null
        }
    }

    /**
     * Materialises the model's single-channel float confidence mask into a
     * source-sized ARGB_8888 bitmap whose ALPHA encodes the skin confidence.
     * The masks returned by the MediaPipe Tasks [ImageSegmenter] are
     * `VEC32F1` ByteBuffer-backed [MPImage]s (a CPU float value in [0,1] per
     * pixel in row-major order) — NOT Bitmap-backed images, so
     * [com.google.mediapipe.framework.image.BitmapExtractor] throws on them and
     * the mask must be read through [ByteBufferExtractor] instead. This is the
     * real-device fix: previously the extraction threw
     * (`IllegalArgumentException: Extracting Bitmap from a MPImage created by
     * objects other than Bitmap is not supported`) and `runCatching` in [segment]
     * silently swallowed it → SKIN always fell back to the geometric cheek
     * ellipses.
     */
    private fun computeAlphaMask(mask: MPImage, width: Int, height: Int): Bitmap {
        val confidence = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val floats: java.nio.FloatBuffer = if (mask.width > 0 && mask.height > 0) {
            ByteBufferExtractor.extract(mask).let { raw ->
                // extract() returns a read-only view; duplicate it so we can pin
                // the byte order without mutating the shared image buffer.
                raw.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
            }
        } else {
            return confidence
        }
        val maskW = if (mask.width > 0) mask.width else width
        val maskH = if (mask.height > 0) mask.height else height
        val scaleX = maskW.toFloat() / width
        val scaleY = maskH.toFloat() / height
        val argb = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            val sy = (y * scaleY).toInt().coerceIn(0, maskH - 1)
            val row = sy * maskW
            for (x in 0 until width) {
                val sx = (x * scaleX).toInt().coerceIn(0, maskW - 1)
                val v = if (floats.hasRemaining()) floats.get(row + sx) else 0f
                val a = (v.coerceIn(0f, 1f) * 255f).roundToInt()
                argb[i++] = Color.argb(a, 255, 255, 255)
            }
        }
        confidence.setPixels(argb, 0, width, 0, 0, width, height)
        return confidence
    }

    override fun close() {
        segmenter?.close()
    }

    private companion object {
        const val MODEL_ASSET = "skin_segmenter.tflite"
        const val FACE_SKIN_FALLBACK_INDEX = 3
    }
}