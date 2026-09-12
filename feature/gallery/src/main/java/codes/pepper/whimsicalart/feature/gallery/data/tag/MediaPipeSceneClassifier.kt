package codes.pepper.whimsicalart.feature.gallery.data.tag

import android.content.Context
import android.graphics.Bitmap
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneClassifier
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneLabel
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaPipe Tasks (LiteRT) `ImageClassifier`-backed [SceneClassifier] running
 * the bundled `efficientnet_lite0.tflite` (float32, shipped with its own label
 * metadata). [label] hops onto [Dispatchers.Default] and blocks the worker
 * thread (expected: called from the ViewModel's classifier flow). Thin native
 * seam — MediaPipe inference does not execute under Robolectric, so this is
 * intentionally not unit-tested; tests target the pure [TagTransformer].
 *
 * The task is created lazily so merely constructing the labeler (e.g. in unit
 * tests that exercise the ViewModel) never initializes the native client; a task
 * that fails to build (missing asset at runtime) degrades to an empty tag list.
 */
@Singleton
class MediaPipeSceneClassifier @Inject constructor(@ApplicationContext context: Context) : SceneClassifier {

    private val classifier: ImageClassifier? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            ImageClassifier.createFromOptions(
                context,
                ImageClassifier.ImageClassifierOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET)
                            .setDelegate(Delegate.CPU)
                            .build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setMaxResults(MAX_RESULTS)
                    .setScoreThreshold(MIN_SCORE_THRESHOLD)
                    .build()
            )
        }.getOrNull()
    }

    private val pending = AtomicInteger(0)

    override suspend fun label(bitmap: Bitmap): List<SceneLabel> = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled) return@withContext emptyList()
        val client = classifier ?: return@withContext emptyList()
        if (pending.get() > 0) return@withContext emptyList() // avoid concurrent calls on same labeler
        pending.incrementAndGet()
        try {
            val image: MPImage = BitmapImageBuilder(bitmap).build()
            try {
                client.classify(image)
                    .classificationResult()
                    .classifications()
                    .flatMap { it.categories() }
                    .map { category ->
                        SceneLabel(
                            text = category.categoryName(),
                            confidence = category.score()
                        )
                    }
            } finally {
                image.close()
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            pending.decrementAndGet()
        }
    }

    fun close() {
        // Initialising MediaPipe just to close it is wasteful when the task was
        // never created (a gallery run with no tagging requests).
        classifier?.close()
    }

    private companion object {
        const val MODEL_ASSET = "efficientnet_lite0.tflite"
        // The evaluator's default image labeler returned its most confident
        // labels; keep the top few with a sane floor so the tag chips stay useful.
        const val MAX_RESULTS = 5
        const val MIN_SCORE_THRESHOLD = 0.35f
    }
}