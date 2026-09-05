package codes.pepper.whimsicalart.feature.beauty.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

/**
 * On-device, fully-offline 478-landmark Face Mesh backed by MediaPipe Tasks
 * (LiteRT) `FaceLandmarker`, reading the bundled `face_landmarker.task`.
 *
 * The task bundle embeds the face detector, the landmark model and the geometry
 * pipeline metadata, so correct absolute landmarks are produced without
 * hand-rolling the face-detection -> crop -> affine-transform stage. This is the
 * machine-learnt "where TF wins" upgrade over the ML Kit face contours: 478 dense
 * points give a much tighter eye / lip / brow outline for the makeup tools.
 *
 * Runs off the calling thread (the ViewModel already wraps detection in
 * [Dispatcher][kotlinx.coroutines.Dispatchers.Default]). The native runtime
 * cannot execute on the JVM under Robolectric, so this class is intentionally
 * thin: the pure contour mapping lives in [FaceMeshContourMapper]. If the model
 * fails to load or the native runtime errors, [detect] returns an empty list and
 * callers fall back to ML Kit.
 */
class MediaPipeFaceMeshDetector(context: Context) : FaceMeshDetector {

    private val landmarker: FaceLandmarker? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            FaceLandmarker.createFromOptions(
                context,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET)
                            .build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(MAX_FACES)
                    .build()
            )
        }.getOrNull()
    }

    override fun detect(source: Bitmap): List<FaceMeshFace> {
        val task = landmarker ?: return emptyList()
        return runCatching {
            val image: MPImage = BitmapImageBuilder(source).build()
            try {
                val result = task.detect(image)
                result.faceLandmarks().map { landmarks ->
                    val points = landmarks.map { l: NormalizedLandmark ->
                        PointF(l.x() * source.width, l.y() * source.height)
                    }
                    FaceMeshFace(
                        points = points,
                        bounds = FaceMeshContourMapper.boundsOf(points)
                    )
                }
            } finally {
                image.close()
            }
        }.getOrElse { emptyList() }
    }

    override fun close() {
        landmarker?.close()
    }

    private companion object {
        const val MODEL_ASSET = "face_landmarker.task"
        const val MAX_FACES = 4
    }
}