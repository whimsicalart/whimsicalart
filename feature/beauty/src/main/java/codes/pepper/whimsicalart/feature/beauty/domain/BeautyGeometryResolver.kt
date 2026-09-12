package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult
import codes.pepper.whimsicalart.feature.beauty.detection.FaceMeshContourMapper
import codes.pepper.whimsicalart.feature.beauty.detection.FaceMeshDetector
import codes.pepper.whimsicalart.feature.beauty.detection.HairMaskProcessor
import codes.pepper.whimsicalart.feature.beauty.detection.HairSegmenter
import codes.pepper.whimsicalart.feature.beauty.detection.SkinMaskProcessor
import codes.pepper.whimsicalart.feature.beauty.detection.SkinSegmenter
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    /**
     * Resolves the [neededFields] of [image]; see
     * [DefaultBeautyGeometryResolver.resolveLocked] for how the
     * [maxPixelCountHint] steers the adaptive ML input resolution and how stages
     * are gated per field.
     */
    suspend fun resolve(
        image: Bitmap,
        maxPixelCountHint: Int?,
        neededFields: Set<GeometryField>
    ): BeautyGeometry

    /** Convenience overload — full resolve (every field), no prefix-fold hint. */
    suspend fun resolve(image: Bitmap): BeautyGeometry = resolve(image, null, GeometryField.ALL)

    /** Convenience overload when no prefix-fold pixel count is available. */
    suspend fun resolve(image: Bitmap, maxPixelCountHint: Int?): BeautyGeometry =
        resolve(image, maxPixelCountHint, GeometryField.ALL)

    /** Releases native detector resources. No-op by default so fakes are trivially testable. */
    fun close() = Unit
}

/** Production ML-backed [BeautyGeometryResolver]. Hilt-provided (needs @ApplicationContext). */
class DefaultBeautyGeometryResolver(
    private val faceMeshDetector: FaceMeshDetector,
    private val hairSegmenter: HairSegmenter,
    private val skinSegmenter: SkinSegmenter,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BeautyGeometryResolver {

    private val mutex = Mutex()

    /**
     * Resolves the ML geometry for [image] against an input whose **pixel count**
     * is adaptive to the DEVICE's memory profile (docs: Beauty tools terminate
     * silently on a real device). `ImageSegmenter` emits its confidence masks AT
     * INPUT SIZE and [codes.pepper.whimsicalart.feature.beauty.detection.MediaPipeHairSegmenter.computeAlphaMask]
     * then allocates a second full-res ARGB copy per channel, so a naive
     * full-size run on a 48–108 MP photo needs over 1 GB of transient
     * allocations and the native runtime aborts the process.
     *
     * The input is downscaled once to an [adaptiveInputPixelCap] PIXEL COUNT
     * (`width × height` — the honest unit of the pipeline's compute/memory, both
     * of which scale with area, not any single linear dimension) — the cap is
     * re-derived from the running device on every resolve and clamped between
     * [ML_MIN_PIXELS] (a quality floor, so small photos are never downscaled at
     * all and detail is preserved) and [ML_MAX_PIXELS] (a CPU ceiling, so giant
     * photos cannot blow the pipeline's time). The downscale is uniform in BOTH
     * axes (aspect preserved) via the square root of `capPixels / pixels`. All
     * returned mask Paths, face bounds and landmarks are scaled back to [image]'s
     * pixel space, so every downstream consumer (beauty processor, mask builders)
     * is unchanged.
     *
     * [maxPixelCountHint] is the geometry TRACK's full-size pixel count for the
     * segment being resolved — computed from the prefix effects' dimension
     * estimators (the same pure math Crop/Transform use) rather than from this
     * [image], which may be its preview-resolution downscale. It lets the cap be
     * evaluated against the FINAL content footprint, so the desired input
     * resolution is recomputed whenever a size-changing effect (crop / transform
     * with rotation / background) changes that footprint — after such an effect
     * the geometry track hands the new predicted pixel count and [resolve] is
     * invoked afresh for the new base instance.
     */
    override suspend fun resolve(
        image: Bitmap,
        maxPixelCountHint: Int?,
        neededFields: Set<GeometryField>
    ): BeautyGeometry {
        if (image.isRecycled) {
            return BeautyGeometry(faceResult = FaceDetectionResult.empty(), skinPath = null, hairPath = null)
        }
        return mutex.withLock {
            resolveLocked(image, maxPixelCountHint, neededFields)
        }
    }

    /**
     * Overlap guard: the ML calls below hand `working` to native inference
     * threads that may still be locking pixels when a second overlapping
     * resolve returns and recycles `working`. Serializing the body (including
     * the recycle in the finally) with a per-instance [mutex] makes it
     * impossible for one resolve to recycle a bitmap the other's native runtime
     * still owns.
     */
    private suspend fun resolveLocked(
        image: Bitmap,
        maxPixelCountHint: Int?,
        neededFields: Set<GeometryField>
    ): BeautyGeometry {
        val referencePixels = maxPixelCountHint?.takeIf { it > 0 }
            ?: image.width * image.height
        val cap = adaptiveInputPixelCap()
        val factor = adaptiveInputScaleFactor(referencePixels, cap)
        val working = if (factor < 1f) {
            withContext(dispatcher) {
                val w = (image.width * factor).roundToInt().coerceAtLeast(1)
                val h = (image.height * factor).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(image, w, h, true)
            }
        } else {
            image
        }
        try {
            // Per-field laziness: run ONLY the ML stages for the requested
            // fields (docs/effect_stack.md → Shared geometry context). A face
            // effect needs only the face detection/mesh stages; hair color adds
            // the hair segmenter; the auto/smoothing/etc. tools add the skin
            // segmenter. A stage that is not requested is left null — the
            // context merges partial results incrementally, so a later request
            // for another field re-resolves JUST that stage against the retained
            // base. Each requested stage is individually guarded: ONE failing
            // detector degrades gracefully (empty faces / no mesh / null mask)
            // instead of letting an ML Kit / MediaPipe exception propagate out of
            // `resolve` and abort the whole preview fold mid-way — which the user
            // sees as "effect not applying / nothing changes". The other stages
            // still run. SUBJECT_MASK is NOT resolvable here (the editor supplies
            // it through the same context via its own generator).
            val faceResult = if (GeometryField.FACE in neededFields) {
                withContext(dispatcher) {
                    val meshFaces = runCatching { faceMeshDetector.detect(working) }.getOrElse { emptyList() }
                    FaceMeshContourMapper.toFaceDetectionResult(meshFaces, working.width, working.height)
                }
            } else {
                null
            }
            val hairPath = if (GeometryField.HAIR in neededFields) {
                withContext(dispatcher) {
                    val mask = runCatching { hairSegmenter.segment(working) }.getOrNull()
                    val path = mask?.let { HairMaskProcessor.toPath(it) }
                    if (mask != null && !mask.isRecycled) mask.recycle()
                    path
                }
            } else {
                null
            }
            val skinPath = if (GeometryField.SKIN in neededFields) {
                withContext(dispatcher) {
                    val mask = runCatching { skinSegmenter.segment(working) }.getOrNull()
                    val path = mask?.let { SkinMaskProcessor.toPath(it) }
                    if (mask != null && !mask.isRecycled) mask.recycle()
                    path
                }
            } else {
                null
            }
            coroutineContext.ensureActive()
            // Scale back using the exact per-axis reverse ratios (not 1/factor):
            // working.width = (image.width * factor).roundToInt(), so
            // working.width * (image.width / working.width) == image.width
            // exactly — the rescale is pixel-exact in both axes.
            val sx = if (factor < 1f) image.width.toFloat() / working.width else 1f
            val sy = if (factor < 1f) image.height.toFloat() / working.height else 1f
            android.util.Log.d(
                "BeautyGeometryML",
                "resolve input=${image.width}x${image.height} factor=$factor fields=$neededFields " +
                    "faces=${faceResult?.faces?.size} " +
                    " hair=${hairPath != null} skin=${skinPath != null}"
            )
            return BeautyGeometry(
                faceResult = faceResult?.scaled(sx, sy),
                skinPath = skinPath?.scaled(sx, sy),
                hairPath = hairPath?.scaled(sx, sy)
            )
        } finally {
            if (working !== image && !working.isRecycled) working.recycle()
        }
    }

    /**
     * The device-adaptive ML input pixel budget (`width × height`). The
     * ImageSegmenter pipeline keeps a bounded set of full-res ARGB planes at the
     * input size on top of the working bitmap; budget a fixed fraction of the
     * DEVICE heap (via [Runtime.maxMemory], which tracks the device's memory
     * class / largeHeap flag) for that whole set and back out the largest input
     * PIXEL COUNT that fits. Larger-heap devices therefore run the pipeline over
     * a larger image (better landmark/mask fidelity), smaller-heap devices over
     * a smaller one (stays alive) — always inside the hard
     * [ML_MIN_PIXELS]..[ML_MAX_PIXELS] clamp.
     */
    private fun adaptiveInputPixelCap(): Int {
        val budgetBytes = Runtime.getRuntime().maxMemory().toDouble() * MEMORY_BUDGET_FRACTION
        return adaptivePixelCountCap(budgetBytes)
    }

    // The per-axis rescale of the ML result back to the input's pixel space
    // lives in BeautyGeometryScale.kt (shared with the editor's geometry-track
    // cap): FaceDetectionResult/PointF/Path [scaled] and Bitmap [scaledTo].

    override fun close() {
        faceMeshDetector.close()
        hairSegmenter.close()
        skinSegmenter.close()
    }

}

// Quality floor / CPU ceiling for the ML input pixel count (see
// adaptiveInputPixelCap): ≈ 1 MP (the pixel count of a 1024×1024 square — the
// quality floor, so images at or below it are never downscaled) and ≈ 4 MP
// (the pixel count of a 2048×2048 square — the CPU ceiling, so no source can
// drive the pipeline's time unbounded). Both square the previous long-edge
// clamps, keeping the worst-case memory envelope unchanged while every
// non-square aspect ratio now spends its budget fairly.
internal const val ML_MIN_PIXELS = 1_048_576
/**
 * The hard CPU ceiling on the ML input pixel count (≈ 4 MP, the pixel count of
 * a 2048×2048 square). PUBLIC because the editor's GEOMETRY TRACK derives its
 * fold scale from it (docs/effect_stack.md → Lazy beauty geometry — "the
 * adaptive 1–4 MP budget, never the full-size original"): a multi-MP source is
 * capped to `sqrt(ML_MAX_PIXELS / footprintPixels)` so geometry-changing
 * deforming effects are never folded at full size.
 */
const val ML_MAX_PIXELS = 4_194_304

// Fraction of the device heap budgeted for the ML pipeline's transient working
// set (input + mask/confidence copies + fold headroom).
internal const val MEMORY_BUDGET_FRACTION = 0.35

// Worst-case simultaneous full-res ARGB planes the pipeline touches at the
// input size (working input, each segmenter's mask MPImage, extracted and
// confidence copies, plus safety margin).
internal const val WORST_CASE_PIXEL_PLANES = 8.0

internal const val BYTES_PER_ARGB_PIXEL = 4.0

/** Largest input pixel count (`width × height`) that fits [budgetBytes]. */
internal fun adaptivePixelCountCap(budgetBytes: Double): Int =
    (budgetBytes / (BYTES_PER_ARGB_PIXEL * WORST_CASE_PIXEL_PLANES))
        .roundToInt()
        .coerceIn(ML_MIN_PIXELS, ML_MAX_PIXELS)

/**
 * Uniform area-based downscale factor for an ML input of [referencePixels]
 * against a [capPixels] budget: when the source exceeds the cap both axes are
 * scaled by `sqrt(capPixels / pixels)` so the aspect ratio is preserved and the
 * rescaled pixel count lands exactly on (or just under) the budget; 1 (no
 * downscale) otherwise.
 */
internal fun adaptiveInputScaleFactor(referencePixels: Int, capPixels: Int): Float =
    if (referencePixels > capPixels) sqrt(capPixels.toDouble() / referencePixels).toFloat() else 1f
