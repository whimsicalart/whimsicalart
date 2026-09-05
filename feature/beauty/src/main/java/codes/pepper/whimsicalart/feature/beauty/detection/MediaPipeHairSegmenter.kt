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
 * On-device, fully-offline hair segmentation backed by MediaPipe Tasks (LiteRT)
 * `ImageSegmenter`, running the bundled `hair_segmenter.tflite`.
 *
 * That model is a 2-class (background / hair) network using custom ops
 * (`MaxPoolingWithArgmax2D`, `MaxUnpooling2D`, `Convolution2DTransposeBias`) that
 * a standard LitERT interpreter cannot load - which is exactly why it is executed
 * through MediaPipe's native runtime, whose op resolver registers them. This is
 * the machine-learnt replacement for the geometric hair dome, spotting the true
 * hair silhouette (crown, side drifts) instead of a shape pinned to the face box.
 *
 * The hair label is selected from [ImageSegmenter.getLabels] at runtime (falling
 * back to index 1), and the confidence mask is up-scaled to the source size so
 * [HairMaskProcessor.toPath] can map it directly onto the full-resolution image.
 * Native inference cannot run under Robolectric, so this class is intentionally
 * thin and returns `null` on any failure for graceful fallback to the dome.
 */
class MediaPipeHairSegmenter(context: Context) : HairSegmenter {

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

    private val hairLabelIndex: Int by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        segmenter?.getLabels()
            ?.indexOfFirst { label -> label.contains("hair", ignoreCase = true) }
            ?.takeIf { it >= 0 }
            ?: 1
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
                if (hairLabelIndex >= masks.size) return null
                val allAlpha = computeAlphaMask(masks[hairLabelIndex], maskW, maskH)
                masks.forEach { m -> m.close() }
                allAlpha
            } finally {
                image.close()
            }
        }.getOrNull()
    }

    /**
     * Turns the model's single-channel hair-confidence MPImage into a source-sized
     * ARGB_8888 bitmap whose alpha encodes hair confidence. The mask output is
     * already the requested size (the model's native output is up-scaled to it).
     */
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
        const val MODEL_ASSET = "hair_segmenter.tflite"
    }
}