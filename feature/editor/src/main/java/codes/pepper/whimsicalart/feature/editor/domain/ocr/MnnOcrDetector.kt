package codes.pepper.whimsicalart.feature.editor.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import codes.pepper.whimsicalart.feature.editor.domain.ocr.PpOcr.DecodedText
import dagger.hilt.android.qualifiers.ApplicationContext
import com.taobao.android.mnn.MNNForwardType
import com.taobao.android.mnn.MNNNetInstance
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PP-OCR (PaddleOCR) detector, recognizer and textline-orientation classifier
 * running on the MNN runtime — the open-source replacement for the ML Kit text
 * recognizer. The MNN Android AAR is vendored as prebuilt `jniLibs`
 * (`libMNN.so` + `libmnncore.so`) plus the Apache-2.0 MNN Java API under
 * `com/taobao/android/mnn`; the models/char sets live in `assets/ocr`.
 *
 * The whole engine is initialized lazily and probed defensively: [isAvailable]
 * loads the four models, creates their sessions and resolves the model tensor
 * names from a candidate list, caching the working pair for the current binary.
 * If any model fails to load OR no input/output tensor name resolves (unknown
 * ABI, format mismatch, probe miss), the probe reports unavailable and — per the
 * spec — the UI hides Recognize Text for a still-usable text tool. The pure
 * pipeline math lives in [PpOcr] and is unit-tested under Robolectric (the
 * libMNN.so path itself is exercised on a real device by the mentor's round).
 */
@Singleton
class MnnOcrDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : OcrDetector {

    private val pending = AtomicInteger(0)

    /** Lazily built on first [isAvailable] / [recognize]; cached for the app lifetime. */
    @Volatile private var engine: Engine? = null
    private val availability = AtomicBoolean(false)
    private val probed = AtomicBoolean(false)

    @Synchronized
    private fun probeEngine(): Engine? {
        if (engine != null) return engine
        val built = runCatching { buildEngine() }.getOrNull()
        engine = built
        probed.set(true)
        availability.set(built != null)
        return built
    }

    /** Whether the four models + the MNN runtime loaded and all tensors resolved. */
    override val isAvailable: Boolean
        get() {
            if (!probed.get()) probeEngine()
            return availability.get()
        }

    override suspend fun recognize(bitmap: Bitmap): List<OcrLine> {
        if (bitmap.isRecycled) return emptyList()
        if (pending.get() > 0) return emptyList() // avoid concurrent calls on the same sessions
        pending.incrementAndGet()
        return try {
            probeEngine()?.recognize(bitmap) ?: emptyList()
        } catch (t: Throwable) {
            emptyList()
        } finally {
            pending.decrementAndGet()
        }
    }

    // ------------------------------------------------------------------ engine

    private fun buildEngine(): Engine? {
        val built = ArrayList<ProbedSession>()
        fun stage(name: String, inCands: List<String>, outCands: List<String>): ProbedSession? {
            val s = modelSession(name, inCands, outCands)
            if (s == null) {
                built.forEach { it.close() }
                return null
            }
            built.add(s)
            return s
        }
        val det = stage("det.mnn", INPUT_CANDIDATES, DET_OUTPUT_CANDIDATES) ?: return null
        val cls = stage("cls.mnn", INPUT_CANDIDATES, PROB_OUTPUT_CANDIDATES) ?: return null
        val recLatin = stage("rec_latin.mnn", INPUT_CANDIDATES, PROB_OUTPUT_CANDIDATES) ?: return null
        val recCj = stage("rec_cj.mnn", INPUT_CANDIDATES, PROB_OUTPUT_CANDIDATES) ?: return null
        val latinVocab = readVocab("keys_latin.txt")
        val cjVocab = readVocab("keys_cj.txt")
        if (latinVocab == null || cjVocab == null) {
            built.forEach { it.close() }
            return null
        }
        return Engine(det, cls, recLatin, recCj, latinVocab, cjVocab)
    }

    private fun modelSession(assetName: String, inCands: List<String>, outCands: List<String>): ProbedSession? {
        val model = assetFile(assetName) ?: return null
        val net = MNNNetInstance.createFromFile(model.absolutePath) ?: return null
        val session = net.createSession(MNNNetInstance.Config().apply {
            forwardType = MNNForwardType.FORWARD_CPU.type
            numThread = 4
            outputTensors = outCands.toTypedArray()
        }) ?: return null
        val inputName = inCands.firstOrNull { session.getInput(it) != null } ?: return null
        val outputName = outCands.firstOrNull { session.getOutput(it) != null } ?: return null
        return ProbedSession(net, session, inputName, outputName)
    }

    private fun assetFile(assetName: String): File? {
        return try {
            val dir = File(context.cacheDir, "ocr")
            val target = File(dir, assetName)
            if (target.exists() && target.length() > 0L) return target
            dir.mkdirs()
            context.assets.open("ocr/$assetName").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        } catch (t: Throwable) {
            null
        }
    }

    private fun readVocab(assetName: String): List<String>? {
        return try {
            context.assets.open("ocr/$assetName").bufferedReader().useLines { lines ->
                PpOcr.vocabWithBlank(lines.map { it.trim() }.toList())
            }
        } catch (t: Throwable) {
            null
        }
    }

    private class ProbedSession(
        val net: MNNNetInstance,
        val session: MNNNetInstance.Session,
        val inputName: String,
        val outputName: String
    ) {
        fun close() {
            runCatching { session.release() }
            runCatching { net.release() }
        }
    }

    // -------------------------------------------------------------------- run

    private class Engine(
        val det: ProbedSession,
        val cls: ProbedSession,
        val recLatin: ProbedSession,
        val recCj: ProbedSession,
        val latinVocab: List<String>,
        val cjVocab: List<String>
    ) {
        fun recognize(bitmap: Bitmap): List<OcrLine> {
            val srcW = bitmap.width
            val srcH = bitmap.height
            if (srcW <= 0 || srcH <= 0) return emptyList()

            val detSpec = PpOcr.detResize(srcW, srcH)
            val scaled = Bitmap.createScaledBitmap(bitmap, detSpec.width, detSpec.height, true)
            val detPixels = IntArray(detSpec.width * detSpec.height)
            scaled.getPixels(detPixels, 0, detSpec.width, 0, 0, detSpec.width, detSpec.height)
            if (scaled !== bitmap) scaled.recycle()

            val detIn = det.session.input(det.inputName) ?: return emptyList()
            detIn.reshape(intArrayOf(1, 3, detSpec.height, detSpec.width))
            det.session.reshape()
            detIn.setInputFloatData(PpOcr.toNchwRgb(detPixels, detSpec.width, detSpec.height, detSpec.width, detSpec.height))
            det.session.run()
            val detOut = det.session.output(det.outputName) ?: return emptyList()
            val dims = detOut.getDimensions() ?: return emptyList()
            val mapH: Int
            val mapW: Int
            when (dims.size) {
                4 -> { mapH = dims[2]; mapW = dims[3] }
                3 -> { mapH = dims[1]; mapW = dims[2] }
                2 -> { mapH = dims[0]; mapW = dims[1] }
                else -> return emptyList()
            }
            val mapSize = mapH * mapW
            val probsAll = detOut.getFloatData() ?: return emptyList()
            if (probsAll.size < mapSize) return emptyList()
            val probs = probsAll.copyOfRange(0, mapSize)

            val scaleX = srcW.toFloat() / mapW
            val scaleY = srcH.toFloat() / mapH
            val boxes = PpOcr.findTextBoxes(probs, mapW, mapH)

            val lines = ArrayList<OcrLine>()
            for (box in boxes) {
                val quad = box.corners.map { PointF((it.x * scaleX).coerceIn(0f, srcW - 1f), (it.y * scaleY).coerceIn(0f, srcH - 1f)) }
                val bbox = PpOcr.Quad(quad).boundingBox()
                if (bbox.width() < 2f || bbox.height() < 2f) continue
                val text = recognizeCrop(bitmap, bbox) ?: continue
                if (text.text.isEmpty()) continue
                lines.add(
                    OcrLine(
                        text = text.text,
                        boundingBox = bbox,
                        rotationDegrees = 0f,
                        quad = quad
                    )
                )
            }
            return lines
        }

        private fun recognizeCrop(bitmap: Bitmap, bbox: RectF): DecodedText? {
            val padX = maxOf(4f, bbox.width() * 0.02f).toInt()
            val padY = maxOf(4f, bbox.height() * 0.1f).toInt()
            val left = (bbox.left - padX).toInt().coerceAtLeast(0)
            val top = (bbox.top - padY).toInt().coerceAtLeast(0)
            val right = (bbox.right + padX).toInt().coerceAtMost(bitmap.width - 1)
            val bottom = (bbox.bottom + padY).toInt().coerceAtMost(bitmap.height - 1)
            val cw = (right - left + 1).coerceAtLeast(2)
            val ch = (bottom - top + 1).coerceAtLeast(2)
            val pixels = IntArray(cw * ch)
            bitmap.getPixels(pixels, 0, cw, left, top, cw, ch)

            val rotation = rotateCrop(pixels, cw, ch)
            val cropW: Int
            val cropH: Int
            val cropPx: IntArray
            if (rotation == 180) {
                cropPx = IntArray(pixels.size)
                rotate180(pixels, cw, ch, cropPx)
                cropW = cw
                cropH = ch
            } else {
                cropPx = pixels
                cropW = cw
                cropH = ch
            }

            val recSpec = PpOcr.recResize(cropW, cropH)
            val recData = PpOcr.toNchwRgb(cropPx, cropW, cropH, recSpec.width, recSpec.height)
            val latin = decodeRec(recLatin, recSpec, recData, latinVocab)
                ?: return null
            val cj = decodeRec(recCj, recSpec, recData, cjVocab)
                ?: DecodedText("", 0f)
            return PpOcr.betterRec(latin, cj)
        }

        private fun rotateCrop(pixels: IntArray, w: Int, h: Int): Int {
            val dW = PpOcr.CLS_WIDTH
            val dH = PpOcr.CLS_HEIGHT
            val data = PpOcr.toNchwRgb(pixels, w, h, dW, dH)
            val input = cls.session.input(cls.inputName) ?: return 0
            input.reshape(intArrayOf(1, 3, dH, dW))
            cls.session.reshape()
            input.setInputFloatData(data)
            cls.session.run()
            val out = cls.session.output(cls.outputName) ?: return 0
            val dims = out.getDimensions() ?: return 0
            val classes = dims.last() ?: return 0
            val probs = out.getFloatData() ?: return 0
            return PpOcr.rotationDegreesFromCls(probs, classes)
        }

        private fun decodeRec(session: ProbedSession, spec: PpOcr.ResizeSpec, data: FloatArray, vocab: List<String>): DecodedText? {
            val input = session.session.input(session.inputName) ?: return null
            input.reshape(intArrayOf(1, 3, spec.height, spec.width))
            session.session.reshape()
            input.setInputFloatData(data)
            session.session.run()
            val out = session.session.output(session.outputName) ?: return null
            val dims = out.getDimensions() ?: return null
            if (dims.size < 2) return null
            val timesteps = dims[dims.size - 2]
            val classes = dims.last()
            val probs = out.getFloatData() ?: return null
            return PpOcr.ctcDecode(vocab, probs, timesteps, classes)
        }

        private fun rotate180(src: IntArray, w: Int, h: Int, dst: IntArray) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    dst[(h - 1 - y) * w + (w - 1 - x)] = src[y * w + x]
                }
            }
        }
    }

    // ------------------------------------------------------------- tensor names

    /**
     * Candidate tensor names a converted PaddleOCR model may expose. The MNN
     * graph keeps Paddle-onnx export names (converted from PaddleX, whose
     * recognizers carry a `fetch_name_0` output and an `x` input), but older
     * converters used "input"/"output". The first resolvable pair wins and is
     * cached per model, so a format rename degrades to unavailable, never garbage.
     *
     * `fetch_name_0__before_tr` MUST stay ahead of `fetch_name_0`: on PaddleX's
     * export the post-transform alias `fetch_name_0` is a "before-translate"
     * decode placeholder that MNN reports with all-zero dims, whereas the
     * `__before_tr` tensor holds the real pre-transform output (the sigmoid map
     * at orig-scale for det, the softmax/CTC logits for cls/rec).
     */
    companion object {
        private val INPUT_CANDIDATES = listOf("x", "input", "images", "image", "pic", "data")
        private val DET_OUTPUT_CANDIDATES = listOf(
            "fetch_name_0__before_tr", "fetch_name_0", "save", "sigmoid_0", "sigmoid_0.tmp_0", "prob", "probs", "output", "out",
            "tmp_0", "sigmoid_31.tmp_0", "DetResizeForTest.0"
        )
        private val PROB_OUTPUT_CANDIDATES = listOf(
            "fetch_name_0__before_tr", "fetch_name_0", "softmax_0", "softmax_0.tmp_0", "softmax", "output", "out",
            "tmp_0", "probs", "prob", "softmax_4.tmp_0"
        )
    }
}

/** Small extension so the MNN Session API reads naturally from the detector. */
private fun MNNNetInstance.Session.input(name: String) = getInput(name)
private fun MNNNetInstance.Session.output(name: String) = getOutput(name)