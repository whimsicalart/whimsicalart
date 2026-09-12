package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.roundToInt
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * Fully-offline, on-device person segmentation for the background tools, run
 * through the plain LiteRT [Interpreter].
 *
 * The bundled `selfie_segmenter.tflite` is a DeepLabV3 (MobileNetV2 backbone)
 * semantic-segmentation model, the classic one used for person segmentation in
 * TensorFlow's hosted-model catalogue (Apache-2.0). It is a pure CNN — no
 * custom ops — so the stock LiteRT resolver loads it on every supported device
 * (min SDK 26 included), unlike the MediaPipe SelfieSegmentation family whose
 * `Convolution2DTransposeBias` custom op requires the MediaPipe Tasks runtime.
 *
 * It takes a `[257, 257, 3]` image and returns a `[257, 257, 21]` tensor of
 * per-pixel logits over the 21 PASCAL VOC classes; class 15 is `person`. Softmax
 * over the classes yields the per-pixel person likelihood in `[0, 1]`, which
 * [MaskFactory] turns into a source-sized alpha mask matching the
 * [SubjectSegmenter] contract.
 *
 * The bundled model is a **quantized** build (~2.8 MB — a float32 DeepLabV3 of the
 * same architecture would be tens of MB), but this class does not assume a
 * dtype: it **introspects the graph's real input/output signature at load**
 * (`Interpreter.getInputTensor` / `getOutputTensor`) and binds exactly that
 * tensor type and shape, applying the quantization scale/zero-point when the
 * model is quantized. A float32 model uses the plain float buffers. Because the
 * buffers always match the actual graph, `Interpreter.run` never trips a tensor
 * dtype/shape mismatch that would otherwise abort the process — any residual
 * native failure is still caught and reported as the graceful "Subject
 * detection failed" state instead of killing the app.
 *
 * No server and no proprietary runtime are used: the whole segmentation runs
 * on-device, which also keeps the user's photo private.
 */
class SelfieMattingSegmenter(context: Context) : SubjectSegmenter {

    private val interpreter: Interpreter? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            context.assets.open(MODEL_ASSET).use { input ->
                val bytes = input.readBytes()
                // XNNPACK is measurably faster for this CNN where it can prepare;
                // some older / low-power devices cannot, so the prepare failure
                // falls back to the plain float CPU kernels (deterministic and
                // universally supported) instead of killing segmentation.
                runCatching {
                    Interpreter(modelBuffer(bytes), Interpreter.Options().apply { setUseXNNPACK(true) })
                }.getOrElse { t ->
                    android.util.Log.w(TAG, "XNNPACK prepare failed; using plain CPU kernels", t)
                    Interpreter(modelBuffer(bytes), Interpreter.Options().apply { setUseXNNPACK(false) })
                }
            }
        }.getOrElse { t ->
            android.util.Log.e(TAG, "interpreter load FAILED", t)
            null
        }
    }

    private fun modelBuffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }

    /** The model's real I/O contract, introspected once at load. */
    private data class ModelIo(
        val inputType: DataType,
        val outputType: DataType,
        val inputShape: IntArray,
        val outputShape: IntArray,
        val inputScale: Float,
        val inputZeroPoint: Int,
        val outputScale: Float,
        val outputZeroPoint: Int
    ) {
        val inputQuantized: Boolean = inputType == DataType.UINT8 || inputType == DataType.INT8
        val outputQuantized: Boolean = outputType == DataType.UINT8 || outputType == DataType.INT8
    }

    private val io: ModelIo? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val tflite = interpreter ?: run {
            android.util.Log.e(TAG, "I/O introspection skipped: no interpreter")
            return@lazy null
        }
        runCatching {
            val input = tflite.getInputTensor(0)
            val output = tflite.getOutputTensor(0)
            android.util.Log.i(
                TAG,
                "model I/O: input ${input.dataType()} ${input.shape().contentToString()} " +
                    "(q scale=${input.quantizationParams().scale} " +
                    "zp=${input.quantizationParams().zeroPoint}) | " +
                    "output ${output.dataType()} ${output.shape().contentToString()} " +
                    "(q scale=${output.quantizationParams().scale} " +
                    "zp=${output.quantizationParams().zeroPoint})"
            )
            ModelIo(
                inputType = input.dataType(),
                outputType = output.dataType(),
                inputShape = input.shape(),
                outputShape = output.shape(),
                inputScale = normalizationScale(input.quantizationParams().scale),
                inputZeroPoint = input.quantizationParams().zeroPoint,
                outputScale = normalizationScale(output.quantizationParams().scale),
                outputZeroPoint = output.quantizationParams().zeroPoint
            )
        }.getOrElse { t ->
            android.util.Log.e(TAG, "model I/O introspection FAILED", t)
            null
        }
    }

    /** Serializes overlapping `set` calls: `Interpreter.run` is not thread-safe. */
    private val runLock = Any()

    override fun segment(source: Bitmap): Bitmap? {
        val tflite = interpreter ?: run {
            android.util.Log.e(TAG, "segment called with uninitialised interpreter")
            return null
        }
        val contract = io ?: run {
            android.util.Log.e(TAG, "segment called without a model I/O contract")
            return null
        }
        return runCatching {
            synchronized(runLock) {
                segmentLocked(tflite, contract, source)
            }
        }.getOrElse { t ->
            android.util.Log.e(TAG, "segment FAILED", t)
            null
        }
    }

    private fun segmentLocked(tflite: Interpreter, contract: ModelIo, source: Bitmap): Bitmap? {
        // Guard against a model variant we do not understand: fail gracefully
        // (log + null) instead of letting a tensor-shape mismatch blow up inside
        // the native runtime.
        val inShp = contract.inputShape
        val outShp = contract.outputShape
        val expectedInput =
            inShp.size == 4 && inShp[1] == INPUT_SIZE && inShp[2] == INPUT_SIZE && inShp[3] == 3
        val expectedOutput =
            outShp.size == 4 && outShp[1] == INPUT_SIZE && outShp[2] == INPUT_SIZE &&
                outShp[3] == NUM_CLASSES
        if (!expectedInput || !expectedOutput) {
            android.util.Log.e(
                TAG,
                "unexpected model shape: input ${inShp.contentToString()} " +
                    "output ${outShp.contentToString()}"
            )
            return null
        }

        val small = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
        try {
            val output = allocateOutput(contract)
            tflite.run(buildInput(contract, small), output)

            val confidence = FloatArray(INPUT_SIZE * INPUT_SIZE)
            if (contract.outputQuantized) {
                output.rewind()
                val logits = FloatArray(NUM_CLASSES)
                val scale = contract.outputScale
                val zero = contract.outputZeroPoint
                for (idx in confidence.indices) {
                    for (c in 0 until NUM_CLASSES) {
                        logits[c] = ((output.get().toInt() and 0xFF) - zero) * scale
                    }
                    confidence[idx] = personConfidence(logits)
                }
            } else {
                output.rewind()
                val logits = output.asFloatBuffer()
                val row = FloatArray(NUM_CLASSES)
                for (idx in confidence.indices) {
                    logits.get(row)
                    confidence[idx] = personConfidence(row)
                }
            }

            val mask257 = MaskFactory.fromConfidence(confidence, INPUT_SIZE, INPUT_SIZE)
            val maskSource = MaskFactory.upscale(mask257, source.width, source.height)
            if (mask257 !== maskSource) mask257.recycle()
            return maskSource
        } finally {
            if (small !== source) small.recycle()
        }
    }

    /** Builds the exact input the graph declares — float32 or quantized bytes. */
    private fun buildInput(contract: ModelIo, small: Bitmap): Any {
        // One batched read of the 257x257 tensor input: both branches index
        // `y*w+x` instead of calling getPixel ~66k times (identical channel math).
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        small.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (contract.inputQuantized) {
            val scale = contract.inputScale
            val zero = contract.inputZeroPoint
            val signed = contract.inputType == DataType.INT8
            val bytes = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
                .order(ByteOrder.nativeOrder())
            for (p in pixels) {
                bytes.put(quantize((p shr 16 and 0xFF) / 255f, scale, zero, signed))
                bytes.put(quantize((p shr 8 and 0xFF) / 255f, scale, zero, signed))
                bytes.put(quantize((p and 0xFF) / 255f, scale, zero, signed))
            }
            bytes.rewind()
            return bytes
        }
        return Array(1) {
            Array(INPUT_SIZE) {
                Array(INPUT_SIZE) { FloatArray(3) }
            }
        }.also { input ->
            for (y in 0 until INPUT_SIZE) {
                val row = y * INPUT_SIZE
                for (x in 0 until INPUT_SIZE) {
                    val p = pixels[row + x]
                    input[0][y][x][0] = (p shr 16 and 0xFF) / 255f
                    input[0][y][x][1] = (p shr 8 and 0xFF) / 255f
                    input[0][y][x][2] = (p and 0xFF) / 255f
                }
            }
        }
    }

    /** Allocates the graph's declared output — float32 logits or quantized bytes. */
    private fun allocateOutput(contract: ModelIo): ByteBuffer {
        val elementBytes = if (contract.outputQuantized) 1 else 4
        return ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * NUM_CLASSES * elementBytes)
            .order(ByteOrder.nativeOrder())
    }

    private fun personConfidence(logits: FloatArray): Float {
        var maxLogit = logits[0]
        for (c in 1 until NUM_CLASSES) {
            if (logits[c] > maxLogit) maxLogit = logits[c]
        }
        var expSum = 0f
        for (c in 0 until NUM_CLASSES) {
            expSum += exp(logits[c] - maxLogit)
        }
        return exp(logits[PERSON_CLASS] - maxLogit) / expSum
    }

    private fun quantize(normalized: Float, scale: Float, zeroPoint: Int, signed: Boolean): Byte {
        val s = if (scale > 0f) scale else 1f / 255f
        val q = (normalized / s + zeroPoint).roundToInt()
        return if (signed) q.coerceIn(-128, 127).toByte() else q.coerceIn(0, 255).toByte()
    }

    private fun normalizationScale(scale: Float): Float = if (scale > 0f) scale else 1f

    fun close() {
        interpreter?.close()
    }

    private companion object {
        const val TAG = "SelfieMatting"
        const val MODEL_ASSET = "selfie_segmenter.tflite"
        const val INPUT_SIZE = 257
        const val NUM_CLASSES = 21
        // PASCAL VOC label list index for `person`.
        const val PERSON_CLASS = 15
    }
}