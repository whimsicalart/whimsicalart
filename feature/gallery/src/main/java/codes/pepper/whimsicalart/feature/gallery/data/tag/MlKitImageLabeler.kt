package codes.pepper.whimsicalart.feature.gallery.data.tag

import android.graphics.Bitmap
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneClassifier
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneLabel
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit-backed [SceneClassifier] using the default on-device image labeler.
 * [label] blocks on the caller thread (expected: called from Dispatchers.Default).
 * Thin native seam — ML Kit inference does not execute under Robolectric, so this
 * is intentionally not unit-tested; tests target the pure [TagTransformer].
 */
@Singleton
class MlKitImageLabeler @Inject constructor() : SceneClassifier {

    // Created lazily so merely constructing the labeler (e.g. in unit tests that
    // exercise the ViewModel) never initializes the ML Kit client.
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }
    private val pending = AtomicInteger(0)

    override suspend fun label(bitmap: Bitmap): List<SceneLabel> {
        if (pending.get() > 0) return emptyList() // avoid concurrent calls on same labeler
        pending.incrementAndGet()
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            Tasks.await(labeler.process(image)).map { element ->
                SceneLabel(
                    text = element.text,
                    confidence = element.confidence
                )
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            pending.decrementAndGet()
        }
    }
}