package codes.pepper.whimsicalart.feature.editor.domain.mosaic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor

/**
 * MediaPipe Tasks (LiteRT) `FaceDetector`-backed [FaceRectsDetector] reading the
 * bundled `face_detector.tflite` (BlazeFace short-range, float16). [detectFaces]
 * blocks on the caller thread (expected: called from Dispatchers.Default / IO);
 * thin native seam — MediaPipe inference does not execute under Robolectric, so
 * this is intentionally not unit-tested; tests target the pure
 * [PrivacyMaskBuilder]. The task is created lazily (an empty client handily sees
 * a suggestion request that never comes) and the same bbox list offset is used
 * as for the detector's future callers.
 *
 * Detection order matches upstream MediaPipe (highest-confidence first); the
 * short-range model targets faces within ~2 m of the camera — the selfie-mosaic
 * use case. The native runtime cannot execute on the JVM under Robolectric:
 * constructing the detector never initialises the task, and a task that fails to
 * build (asset missing at runtime) degrades to no suggestions instead of
 * crashing the suggestion request.
 *
 * **Lifetime / threading contract** (the SIGSEGV fix — .tmp/log.13.txt, fault
 * `addr 0x1a0` in `AndroidPacketCreator.nativeCreateRgbaImage`): this detector
 * is deliberately NOT a singleton — it is owned once per consuming ViewModel, so
 * one session's teardown can never leave a closed native client for the next
 * mosaic session to read (a SIGSEGV is uncatchable, so the use-after-free must
 * be prevented structurally, not "handled"). Within one instance, [close] is
 * exclusive with [detectFaces]: once closed, no new call starts, and the native
 * task is only released when no call is in flight — an in-flight native call
 * releases the task itself on completion, so the LiteRT session is never freed
 * while a `detect` is reading it.
 */
class MediaPipeFaceRectsDetector @Inject constructor(@ApplicationContext private val context: Context) :
    FaceRectsDetector {

    private val taskLock = Any()

    @Volatile
    private var task: FaceDetector? = null

    @Volatile
    private var closed = false

    @Volatile
    private var released = false

    @Volatile
    private var inFlight = AtomicBoolean(false)

    /**
     * Whether the native runtime could be initialised and the detector is still
     * open. Reads [ensureTask], so a broken/missing model asset surfaces as
     * `false` — the Mosaic UI then disables "Suggest faces" instead of silently
     * returning nothing.
     */
    val isAvailable: Boolean
        get() = !closed && ensureTask() != null

    private fun ensureTask(): FaceDetector? {
        task?.let { return it }
        synchronized(taskLock) {
            if (task == null) {
                task = runCatching {
                    FaceDetector.createFromOptions(
                        context,
                        FaceDetector.FaceDetectorOptions.builder()
                            .setBaseOptions(
                                BaseOptions.builder()
                                    .setModelAssetPath(MODEL_ASSET)
                                    .setDelegate(Delegate.CPU)
                                    .build()
                            )
                            .setRunningMode(RunningMode.IMAGE)
                            .build()
                    )
                }.getOrNull()
            }
            return task
        }
    }

    override fun detectFaces(source: Bitmap): List<Rect> {
        if (closed || source.isRecycled) return emptyList()
        val client = ensureTask() ?: return emptyList()
        if (!inFlight.compareAndSet(false, true)) return emptyList() // single inference at a time
        return try {
            // Ownership contract (mirrors MediaPipeFaceMeshDetector): the caller
            // owns `source` and may recycle it while native inference still reads
            // pixels. Feed the task a PERSONAL ARGB copy and recycle it only
            // after `detect` returns.
            val owned = source.copy(Bitmap.Config.ARGB_8888, false) ?: return emptyList()
            try {
                val image: MPImage = BitmapImageBuilder(owned).build()
                try {
                    // MediaPipe reports float bounding boxes; widen to the
                    // enclosing integer rect so the whole face is covered.
                    client.detect(image).detections().map { detection ->
                        val bounds = detection.boundingBox()
                        Rect(
                            floor(bounds.left).toInt(),
                            floor(bounds.top).toInt(),
                            ceil(bounds.right).toInt(),
                            ceil(bounds.bottom).toInt()
                        )
                    }
                } finally {
                    image.close()
                }
            } finally {
                if (!owned.isRecycled) owned.recycle()
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            inFlight.set(false)
            // Last user out closes the door: if the session was closed while this
            // call ran, release the native task now that nothing reads it.
            if (closed) releaseTask()
        }
    }

    override fun close() {
        // No new call may start after this point, and the native task is only
        // released once the in-flight call has drained (it releases itself in
        // its `finally`). Never frees a LiteRT session a detect is reading.
        closed = true
        if (!inFlight.get()) releaseTask()
    }

    private fun releaseTask() {
        synchronized(taskLock) {
            if (released) return
            released = true
            task?.close()
        }
    }

    private companion object {
        const val MODEL_ASSET = "face_detector.tflite"
    }
}