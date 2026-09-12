package codes.pepper.whimsicalart.feature.stickers.domain

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max

/**
 * Intrinsic, orientation-corrected aspect ratio of a picked Gallery photo, used
 * to give a Gallery sticker its own proportional on-canvas box (spec:
 * "Gallery stickers keep the original image's aspect ratio"). Bounds-only read
 * (no pixel decode), EXIF-baked exactly like the editor's raw decode, so a
 * landscape photo stored with a rotation tag still yields a wide box. Returns 1
 * (square — the emoji/preset default) when the URI cannot be read.
 */

/** Bounds + orientation → the oriented width:height ratio (1 = square fallback). */
fun intrinsicAspect(width: Int, height: Int, exifOrientation: Int): Float {
    if (width <= 0 || height <= 0) return 1f
    val (w, h) = if (exifOrientation in EXIF_SWAP_TAGS) height to width else width to height
    return w.toFloat() / max(h, 1)
}

/** Reads a URI's EXIF orientation (injectable for Robolectric tests). */
fun interface ExifAspectReader {
    fun read(contentResolver: ContentResolver, uri: Uri): Int

    companion object {
        /** Real implementation over `android.media.ExifInterface` (minSdk 26). */
        val Default = ExifAspectReader { contentResolver, uri ->
            runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input)
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
    }
}

/** The intrinsic EXIF-oriented width:height ratio of [uri], bounds-only. */
fun galleryStickerAspect(
    contentResolver: ContentResolver,
    uri: Uri,
    orientationReader: ExifAspectReader = ExifAspectReader.Default
): Float {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, opts)
        }
    }
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return 1f
    return intrinsicAspect(opts.outWidth, opts.outHeight, orientationReader.read(contentResolver, uri))
}

private val EXIF_SWAP_TAGS = setOf(
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_ROTATE_270,
    ExifInterface.ORIENTATION_TRANSVERSE
)