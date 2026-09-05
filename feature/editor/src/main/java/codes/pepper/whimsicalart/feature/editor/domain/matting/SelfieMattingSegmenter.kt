package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter

/**
 * On-device, fully-offline person segmentation backed by the MediaPipe Selfie
 * Segmentation model (Apache-2.0) executed through TensorFlow Lite / LiteRT.
 *
 * The bundled `selfie_segmenter.tflite` takes a `[1, 256, 256, 3]` float32 RGB
 * image normalised to `[0, 1]` and returns a `[1, 256, 256, 1]` float32 tensor
 * holding the per-pixel person confidence. That confidence is turned into a
 * source-sized alpha mask by [MaskFactory].
 *
 * No server and no proprietary runtime are used: the whole segmentation runs
 * on-device, which also keeps the user's photo private. This is the same model
 * family that powers ML Kit / Google Meet background effects.
 */
class SelfieMattingSegmenter(context: Context) : SubjectSegmenter {

    private val interpreter: Interpreter? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            context.assets.open(MODEL_ASSET).use { input ->
                Interpreter(input.readBytes().toByteBuffer())
            }
        }.getOrNull()
    }

    override fun segment(source: Bitmap): Bitmap? {
        val tflite = interpreter ?: return null
        return runCatching {
            val input = Array(1) { Array(INPUT_SIZE) { Array(INPUT_SIZE) { FloatArray(3) } } }
            val small = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    val p = small.getPixel(x, y)
                    input[0][y][x][0] = (p shr 16 and 0xFF) / 255f
                    input[0][y][x][1] = (p shr 8 and 0xFF) / 255f
                    input[0][y][x][2] = (p and 0xFF) / 255f
                }
            }
            if (small !== source) small.recycle()

            val output = Array(1) { Array(INPUT_SIZE) { Array(INPUT_SIZE) { FloatArray(1) } } }
            tflite.run(input, output)

            val confidence = FloatArray(INPUT_SIZE * INPUT_SIZE)
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    confidence[y * INPUT_SIZE + x] = output[0][y][x][0]
                }
            }

            val mask256 = MaskFactory.fromConfidence(
                confidence = confidence,
                width = INPUT_SIZE,
                height = INPUT_SIZE
            )
            val maskSource = MaskFactory.upscale(mask256, source.width, source.height)
            if (mask256 !== maskSource) mask256.recycle()
            maskSource
        }.getOrNull()
    }

    fun close() {
        interpreter?.close()
    }

    private fun ByteArray.toByteBuffer(): java.nio.ByteBuffer =
        java.nio.ByteBuffer.wrap(this)

    companion object {
        private const val MODEL_ASSET = "selfie_segmenter.tflite"
        private const val INPUT_SIZE = 256
    }
}
