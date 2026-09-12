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
 * The task is pinned to the **CPU** delegate (`BaseOptions.Delegate.CPU`): those
 * custom ops have no GPU kernels and the segmentation postprocessing needs the
 * CPU/GLES runtime, so the MediaPipe default (GPU) delegate leaves the task
 * failing to init on device — silently falling back to the hair dome.
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
                            .setDelegate(Delegate.CPU)
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
        if (source.isRecycled) return null
        return runCatching {
            val maskW = source.width
            val maskH = source.height
            // The caller (BeautyGeometryResolver) owns `source` and may recycle it
            // as soon as this returns. MediaPipe wraps the bitmap WITHOUT copying
            // pixels (BitmapImageBuilder keeps a reference), so the pixels handed
            // to native must be OURS: build the MPImage from a personal ARGB copy
            // and recycle that copy only after `segment` returns. Reusing the
            // caller's bitmap would let a caller-side recycle (or an overlapping
            // resolve) invalidate pixels a native inference still holds — the
            // AndroidBitmap_lockPixels -2 / SIGSEGV seen on-device.
            val owned = source.copy(Bitmap.Config.ARGB_8888, false)
            if (owned == null) return null
            val image: MPImage = BitmapImageBuilder(owned).build()
            try {
                val result = task.segment(image)
                val masks = result.confidenceMasks().orElse(null) ?: return null
                if (hairLabelIndex >= masks.size) return null
                val allAlpha = computeAlphaMask(masks[hairLabelIndex], maskW, maskH)
                masks.forEach { m -> m.close() }
                allAlpha
            } finally {
                image.close()
                if (!owned.isRecycled) owned.recycle()
            }
        }.getOrNull()
    }

    /**
     * Turns the model's single-channel hair-confidence MPImage into a source-sized
     * ARGB_8888 bitmap whose alpha encodes hair confidence. The mask output is
     * already the requested size (the model's native output is up-scaled to it).
     * Bulk `getPixels`/`setPixels` keep this O(n) in the array copy rather than
     * pegging the CPU with one `getPixel`/`setPixel` call per pixel (a 48 MP
     * mask loop would take seconds and spike the heap on top of the mask itself).
     */
    private fun computeAlphaMask(mask: MPImage, width: Int, height: Int): Bitmap {
        val confidence = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val floats: java.nio.FloatBuffer = if (mask.width > 0 && mask.height > 0) {
            ByteBufferExtractor.extract(mask)
                .duplicate()
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        } else {
            return confidence
        }
        val maskW = if (mask.width > 0) mask.width else width
        val maskH = if (mask.height > 0) mask.height else height
        val scaleX = maskW.toFloat() / width
        val scaleY = maskH.toFloat() / height
        val dst = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            val sy = (y * scaleY).toInt().coerceIn(0, maskH - 1)
            val row = sy * maskW
            for (x in 0 until width) {
                val sx = (x * scaleX).toInt().coerceIn(0, maskW - 1)
                val v = if (floats.hasRemaining()) floats.get(row + sx) else 0f
                val a = (v.coerceIn(0f, 1f) * 255f).roundToInt()
                dst[i++] = Color.argb(a, 255, 255, 255)
            }
        }
        confidence.setPixels(dst, 0, width, 0, 0, width, height)
        return confidence
    }

    override fun close() {
        segmenter?.close()
    }

    private companion object {
        const val MODEL_ASSET = "hair_segmenter.tflite"
    }
}