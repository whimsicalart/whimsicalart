package codes.pepper.whimsicalart.feature.beauty.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter

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
        val task = segmenter ?: return null
        return runCatching {
            val maskW = source.width
            val maskH = source.height
            val image: MPImage = BitmapImageBuilder(source).build()
            try {
                val result = task.segment(image)
                val masks = result.confidenceMasks().orElse(null) ?: return null
                if (faceSkinLabelIndex >= masks.size) return null
                val allAlpha = computeAlphaMask(masks[faceSkinLabelIndex], maskW, maskH)
                masks.forEach { m -> m.close() }
                allAlpha
            } finally {
                image.close()
            }
        }.getOrNull()
    }

    private fun computeAlphaMask(mask: MPImage, width: Int, height: Int): Bitmap {
        val confidence = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val extracted = BitmapExtractor.extract(mask)
        val scaleX = extracted.width.toFloat() / width
        val scaleY = extracted.height.toFloat() / height
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = extracted.getPixel((x * scaleX).toInt(), (y * scaleY).toInt())
                val a = Color.red(c)
                confidence.setPixel(x, y, Color.argb(a, 255, 255, 255))
            }
        }
        if (confidence !== extracted) extracted.recycle()
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