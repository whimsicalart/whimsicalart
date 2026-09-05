package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.PointF
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectorManager
import codes.pepper.whimsicalart.feature.beauty.detection.FaceMeshContourMapper
import codes.pepper.whimsicalart.feature.beauty.detection.FaceMeshDetector
import codes.pepper.whimsicalart.feature.beauty.detection.FaceMeshFace
import codes.pepper.whimsicalart.feature.beauty.detection.HairMaskProcessor
import codes.pepper.whimsicalart.feature.beauty.detection.HairSegmenter
import codes.pepper.whimsicalart.feature.beauty.detection.SkinMaskProcessor
import codes.pepper.whimsicalart.feature.beauty.detection.SkinSegmenter
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive

/**
 * Resolves the full [BeautyGeometry] for an arbitrary [Bitmap] by running the
 * on-device ML pipeline (face detection + dense face-mesh contours + skin/hair
 * segmentation). This is what the editor's fold uses to recompute geometry for a
 * beauty layer placed AFTER a deformation (crop/flip/rotate/reshape), against the
 * RUNNING (deformed/intermediate) image at that layer's fold position.
 *
 * The pipeline is the same one the former `BeautyViewModel` used for the
 * pristine base; extracting it here lets BOTH the editor's geometry
 * registry and the beauty UI share one implementation. The default impl is
 * injectable via [BeautyGeometryResolver] so JVM tests can substitute a fake
 * (the native TFLite/MediaPipe runtime cannot run under Robolectric).
 */
fun interface BeautyGeometryResolver {
    suspend fun resolve(image: Bitmap): BeautyGeometry

    /** Releases native detector resources. No-op by default so fakes are trivially testable. */
    fun close() = Unit
}

/** Production ML-backed [BeautyGeometryResolver]. Hilt-provided (needs @ApplicationContext). */
class DefaultBeautyGeometryResolver(
    private val faceDetectorManager: FaceDetectorManager,
    private val faceMeshDetector: FaceMeshDetector,
    private val hairSegmenter: HairSegmenter,
    private val skinSegmenter: SkinSegmenter,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BeautyGeometryResolver {

    override suspend fun resolve(image: Bitmap): BeautyGeometry {
        val result = withContext(dispatcher) { faceDetectorManager.detectFaces(image) }
        val mesh = withContext(dispatcher) { faceMeshDetector.detect(image) }
        val enriched = enrichWithMesh(result, mesh)
        val hairPath = withContext(dispatcher) {
            val mask = hairSegmenter.segment(image)
            val path = mask?.let { HairMaskProcessor.toPath(it) }
            if (mask != null && !mask.isRecycled) mask.recycle()
            path
        }
        val skinPath = withContext(dispatcher) {
            val mask = skinSegmenter.segment(image)
            val path = mask?.let { SkinMaskProcessor.toPath(it) }
            if (mask != null && !mask.isRecycled) mask.recycle()
            path
        }
        coroutineContext.ensureActive()
        return BeautyGeometry(
            faceResult = enriched,
            skinPath = skinPath,
            hairPath = hairPath
        )
    }

    private fun enrichWithMesh(
        result: FaceDetectionResult,
        meshFaces: List<FaceMeshFace>
    ): FaceDetectionResult {
        if (meshFaces.isEmpty()) return result
        val centers = result.faces.map { face ->
            PointF(face.bounds.exactCenterX(), face.bounds.exactCenterY())
        }
        val enriched = result.faces.mapIndexed { index, face ->
            val matched = FaceMeshContourMapper.nearestMesh(centers[index], meshFaces)
                ?: return@mapIndexed face
            face.copy(landmarks = FaceMeshContourMapper.enrich(face.landmarks, matched.points))
        }
        return result.copy(faces = enriched)
    }

    override fun close() {
        faceDetectorManager.close()
        faceMeshDetector.close()
        hairSegmenter.close()
        skinSegmenter.close()
    }
}
