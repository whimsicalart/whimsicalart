package codes.pepper.whimsicalart.feature.beauty.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
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
 * [Dispatcher][kotlinx.coroutines.Dispatchers.Default]). The task is pinned to
 * the **CPU** delegate (`BaseOptions.Delegate.CPU`): these bundled models include
 * custom/segmentation ops with no GPU kernels, and MediaPipe's default delegate
 * selection can leave the tasks failing to init on-device. The native runtime
 * cannot execute on the JVM under Robolectric, so this class is intentionally
 * thin: the pure contour mapping lives in [FaceMeshContourMapper]. If the model
 * fails to load or the native runtime errors, [detect] returns an empty list and
 * callers degrade to an empty face result.
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
                            .setDelegate(Delegate.CPU)
                            .build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(MAX_FACES)
                    .setOutputFaceBlendshapes(true)
                    .build()
            )
        }.getOrNull()
    }

    override fun detect(source: Bitmap): List<FaceMeshFace> {
        if (source.isRecycled) return emptyList()
        val task = landmarker ?: return emptyList()
        return runCatching {
            // Ownership contract (mirrors MediaPipeHairSegmenter.segment): the
            // caller owns `source` and may recycle it while native inference
            // still reads pixels — a recycled bitmap handed straight to
            // `BitmapImageBuilder` produced the getWidth/getHeight warnings and
            // SIGSEGV on-device. Build the MPImage from a PERSONAL ARGB copy
            // and recycle that copy only after `task.detect` returns.
            val owned = source.copy(Bitmap.Config.ARGB_8888, false) ?: return emptyList()
            val ownedWidth = owned.width
            val ownedHeight = owned.height
            try {
                val image: MPImage = BitmapImageBuilder(owned).build()
                try {
                    val result = task.detect(image)
                    val blendshapeSets = result.faceBlendshapes().orElse(null)
                    result.faceLandmarks().mapIndexed { index, landmarks ->
                        val points = landmarks.map { l: NormalizedLandmark ->
                            PointF(l.x() * ownedWidth, l.y() * ownedHeight)
                        }
                        val blendshapes = blendshapeSets
                            ?.getOrNull(index)
                            ?.associate { c: com.google.mediapipe.tasks.components.containers.Category ->
                                c.categoryName().lowercase() to c.score()
                            }
                            .orEmpty()
                        FaceMeshFace(
                            points = points,
                            bounds = FaceMeshContourMapper.boundsOf(points),
                            blendshapes = blendshapes
                        )
                    }
                } finally {
                    image.close()
                }
            } finally {
                if (!owned.isRecycled) owned.recycle()
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