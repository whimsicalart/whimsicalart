package codes.pepper.whimsicalart.feature.collage.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects a single "face crop focus" for a photo so the collage renderer can drift
 * its cover crop toward the subject. Runs the bundled `face_detector.tflite`
 * (MediaPipe Tasks `FaceDetector`, BlazeFace short-range) and returns the
 * fractional centre of the largest detected face — preferring the nose-tip
 * keypoint (BlazeFace keypoint index 2) as the drift focus, since the bbox
 * centre of a tilted head can sit well away from the subject — or null when no
 * face is found (the renderer then centre-crops).
 *
 * Entry points are `suspend` and hop onto [Dispatchers.Default] (the caller
 * decodes/reads the URI, which is IO-bound); MediaPipe inference is synchronous.
 * The task is created lazily and a failed build (missing asset / native runtime)
 * degrades to `null` — a collage frame then centre-crops instead of failing.
 * MediaPipe inference does not execute under Robolectric, so this class is
 * intentionally not unit-tested (CollageViewModelTest drives the fallback path).
 */
@Singleton
class CollageFaceDetector @Inject constructor(@ApplicationContext context: Context) {

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

    /** @return the fractional face centre of the largest face, or null if none. */
    suspend fun detectFaceCenter(
        resolver: ContentResolver,
        uri: Uri
    ): PointF? = withContext(Dispatchers.Default) {
        val bitmap = decode(resolver, uri) ?: return@withContext null
        try {
            val detection = largestFace(bitmap) ?: return@withContext null
            val bounds = detection.boundingBox()
            // Drift toward the nose-tip keypoint when present (more accurate than
            // the bbox centre for tilted heads), else the bbox centre.
            val centreX = noseKeypointX(detection, bitmap.width) ?: (bounds.left + bounds.right) / 2f
            PointF(centreX / bitmap.width, (bounds.top + bounds.bottom) / 2f / bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? = try {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    private fun largestFace(bitmap: Bitmap): Detection? {
        val client = task ?: return null
        val image: MPImage = BitmapImageBuilder(bitmap).build()
        return try {
            // Prefer the largest face as the composition focus.
            client.detect(image).detections()
                .maxByOrNull { d -> d.boundingBox().width() * d.boundingBox().height() }
        } finally {
            image.close()
        }
    }

    /**
     * BlazeFace short-range keypoints: 0 left eye, 1 right eye, 2 nose tip,
     * 3 mouth centre, 4/5 ear tragions. Returns the nose tip's x in pixels, or
     * null when the keypoint list is absent (degrade to the bbox centre).
     */
    private fun noseKeypointX(detection: Detection, bitmapWidth: Int): Float? {
        val keypoints = detection.keypoints().orElse(null) ?: return null
        val nose = keypoints.getOrNull(NOSE_TIP_KEYPOINT_INDEX) ?: return null
        return nose.x() * bitmapWidth
    }

    fun close() {
        // Initialising MediaPipe just to close it is wasteful when the task was
        // never created (a collage run with no face tracking).
        task?.close()
    }

    private companion object {
        const val MODEL_ASSET = "face_detector.tflite"
        const val NOSE_TIP_KEYPOINT_INDEX = 2
    }
}