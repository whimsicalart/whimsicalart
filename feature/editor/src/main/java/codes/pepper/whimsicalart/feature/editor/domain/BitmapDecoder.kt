package codes.pepper.whimsicalart.feature.editor.domain

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/**
 * Shared "decode + orient" entry point: decodes [uri] bounded to [maxDim] on its
 * longest side (two-phase inSampleSize like the editor's raw decode) and bakes
 * the JPEG's EXIF orientation into the returned bitmap by rotating/flipping it
 * (`android.media.ExifInterface` covers all 8 orientation tags; minSdk 26 so the
 * framework class suffices — no androidx dependency needed).
 *
 * Applying orientation here means the editor's `originalBitmap`/`editedBitmap`
 * are right-side up from the start, and every LATER decode (segment source,
 * sharpen source, cached original/bounds) sees the SAME correctly-oriented
 * geometry because they all route through this helper — a JPEG that opens on its
 * side / mirrored on a real camera device never comes back sideways.
 *
 * Non-EXIF images (PNG/WebP without orientation) and streams that fail to
 * expose EXIF are returned decoded untouched — opening the photo never fails
 * because of orientation work.
 *
 * [orientationReader] is injectable so the (native, not-Robolectric-safe) EXIF
 * read can be stubbed in unit tests while the matrix mapping stays deterministic.
 */
fun decodeBoundedOriented(
    contentResolver: ContentResolver,
    uri: Uri,
    maxDim: Int = DEFAULT_MAX_DIM,
    orientationReader: ExifOrientationReader = ExifOrientationReader.Default
): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize >= maxDim) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: return@runCatching null

    val orientation = orientationReader.read(contentResolver, uri)
    val matrix = exifAdjustmentMatrix(orientation)
    if (matrix == null) return@runCatching decoded
    val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (oriented !== decoded && !decoded.isRecycled) decoded.recycle()
    oriented
}.getOrNull()

/**
 * Bounds-only prediction of what [decodeBoundedOriented] would produce for a
 * source measuring [boundsWidth]×[boundsHeight] with EXIF [orientation] (capped
 * at [maxDim] on the longest side, orientation baked in via the swap tags). No
 * pixel decode — preview-resolution estimators use this to agree with the fold
 * base's real geometry without allocating a bitmap: a raw `inJustDecodeBounds`
 * header reports the UNCAPPED dims, which would scale the first preview
 * half-size (the fold base is always the bounded, oriented decode).
 */
internal fun boundedOrientedDims(
    boundsWidth: Int,
    boundsHeight: Int,
    orientation: Int,
    maxDim: Int = DEFAULT_MAX_DIM
): Pair<Int, Int> {
    var sampleSize = 1
    while (max(boundsWidth, boundsHeight) / sampleSize >= maxDim) {
        sampleSize *= 2
    }
    // BitmapFactory decodes with inSampleSize by rounding dimensions UP
    // (ceil), so mirror exactly that before the orientation swap.
    val w = (boundsWidth + sampleSize - 1) / sampleSize
    val h = (boundsHeight + sampleSize - 1) / sampleSize
    return if (orientation in EXIF_SWAP_TAGS) h to w else w to h
}

private val EXIF_SWAP_TAGS = setOf(
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_ROTATE_270,
    ExifInterface.ORIENTATION_TRANSVERSE
)

/** Pulls the EXIF orientation tag off [uri]; injectable so tests can stub native reads. */
fun interface ExifOrientationReader {
    fun read(contentResolver: ContentResolver, uri: Uri): Int

    companion object {
        /** Real implementation over `android.media.ExifInterface` (minSdk 26). */
        val Default = ExifOrientationReader { contentResolver, uri ->
            runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    val orient = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    // BitmapFactory.decodeStream never applies orientation itself
                    // (unlike ImageDecoder), so every valid tag 2..8 is actionable.
                    orient.takeIf { it in EXIF_VALID_TAGS } ?: ExifInterface.ORIENTATION_NORMAL
                } ?: ExifInterface.ORIENTATION_NORMAL
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
    }
}

/**
 * The rotation/flip matrix for an EXIF orientation tag, or `null` for NORMAL
 * (no transform). Covers all 8 EXIF orientations from the documented mapping:
 * 1 normal, 2 flip-H, 3 rotate-180, 4 flip-V, 5 transpose (flip-H + 270),
 * 6 rotate-90, 7 transverse (flip-V + 90), 8 rotate-270.
 */
internal fun exifAdjustmentMatrix(orientation: Int): Matrix? {
    return when (orientation) {
        ExifInterface.ORIENTATION_NORMAL -> null
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
        // Transpose: mirror H then rotate 270 CW → reflection about the TL-BR
        // diagonal: (x, y) → (y, x).
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
            postRotate(270f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply {
            postRotate(90f)
        }
        // Transverse: mirror H then rotate 90 CW → reflection about the TR-BL
        // anti-diagonal: (x, y) → (−y, −x).
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
            postRotate(90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply {
            postRotate(270f)
        }
        else -> null
    }
}

private fun max(a: Int, b: Int) = if (a > b) a else b

const val DEFAULT_MAX_DIM = 2048

private val EXIF_VALID_TAGS = setOf(
    ExifInterface.ORIENTATION_NORMAL,
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
    ExifInterface.ORIENTATION_ROTATE_180,
    ExifInterface.ORIENTATION_FLIP_VERTICAL,
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSVERSE,
    ExifInterface.ORIENTATION_ROTATE_270
)