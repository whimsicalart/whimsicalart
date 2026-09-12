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
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
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
 */
@Singleton
class MediaPipeFaceRectsDetector @Inject constructor(@ApplicationContext context: Context) :
    FaceRectsDetector {

    private val task: FaceDetector? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
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

    @Volatile
    private var pending = AtomicInteger(0)

    override fun detectFaces(source: Bitmap): List<Rect> {
        if (source.isRecycled) return emptyList()
        val client = task ?: return emptyList()
        if (pending.get() > 0) return emptyList() // avoid concurrent calls on same detector
        pending.incrementAndGet()
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
            pending.decrementAndGet()
        }
    }

    override fun close() {
        // Initialising MediaPipe just to close it is wasteful when the task was
        // never created (e.g. a session with no suggestion request).
        task?.close()
    }

    private companion object {
        const val MODEL_ASSET = "face_detector.tflite"
    }
}