package codes.pepper.whimsicalart.feature.editor.domain

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * EXIF orientation is baked into the editor-entry decode
 * (`decodeBoundedOriented`). `BitmapFactory.decodeStream` never applies
 * orientation itself, so each of the 8 EXIF tags must map to its documented
 * rotation/flip matrix:
 *   1 normal · 2 flip-H · 3 rotate-180 · 4 flip-V · 5 transpose ·
 *   6 rotate-90 · 7 transverse · 8 rotate-270.
 *
 * Tags 5-8 swap the bitmap dimensions (a 400×300 source returns 300×400);
 * tags 1-4 keep them. The EXIF read itself is stubbed (the real
 * `android.media.ExifInterface` needs native parsing that is not
 * Robolectric-safe) — the matrix mapping and decode stay deterministic.
 */
@RunWith(RobolectricTestRunner::class)
class BitmapDecoderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val contentResolver: ContentResolver = context.contentResolver

    private val W = 400
    private val H = 300

    /** A real JPEG file on disk; orientation is injected via [orientationReader]. */
    private fun jpegFile(): Pair<File, Uri> {
        val file = File(context.cacheDir, "orient_probe.jpg")
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            // A large green square in the top-left corner — big enough to
            // survive JPEG's lossy codec, so transformed corner positions stay
            // observable in the decoded result.
            for (y in 0 until MARKER) {
                for (x in 0 until MARKER) {
                    setPixel(x, y, Color.GREEN)
                }
            }
        }
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return file to Uri.fromFile(file)
    }

    private fun orientationTag(tag: Int) = ExifOrientationReader { _, _ -> tag }

    @Test
    fun `tag 1 and absent tag decode unchanged - no premature rotation`() {
        val (_, uri) = jpegFile()
        val normal = decodeBoundedOriented(contentResolver, uri, MAX_DIM_SMALL, orientationTag(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(W, normal!!.width)
        assertEquals(H, normal.height)
        val absent = decodeBoundedOriented(contentResolver, uri, MAX_DIM_SMALL, orientationTag(ExifInterface.ORIENTATION_UNDEFINED))
        assertEquals(W, absent!!.width)
        assertEquals(H, absent.height)
    }

    @Test
    fun `non-swapping tags 2,3,4 keep dimensions`() {
        for (tag in listOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_VERTICAL
        )) {
            val (_, uri) = jpegFile()
            val bmp = decodeBoundedOriented(contentResolver, uri, MAX_DIM_SMALL, orientationTag(tag))!!
            assertEquals("tag $tag width", W, bmp.width)
            assertEquals("tag $tag height", H, bmp.height)
        }
    }

    @Test
    fun `swapping tags 5,6,7,8 swap dimensions`() {
        for (tag in listOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270
        )) {
            val (_, uri) = jpegFile()
            val bmp = decodeBoundedOriented(contentResolver, uri, MAX_DIM_SMALL, orientationTag(tag))!!
            assertEquals("tag $tag width swapped", H, bmp.width)
            assertEquals("tag $tag height swapped", W, bmp.height)
        }
    }

    @Test
    fun `decode is bounded to maxDim on the longest side`() {
        val (_, uri) = jpegFile()
        // 400x300 source with a 128 max dim must downsample to <= 128 long side.
        val bmp = decodeBoundedOriented(contentResolver, uri, maxDim = 128)
        assertTrue("long side under cap", maxOf(bmp!!.width, bmp.height) <= 128)
        assertEquals(W.toFloat() / H, bmp.width.toFloat() / bmp.height, 0.2f)
    }

    @Test
    fun `decode of a missing uri returns null instead of throwing`() {
        val missing = Uri.fromFile(File(context.cacheDir, "does_not_exist.jpg"))
        assertEquals(null, decodeBoundedOriented(contentResolver, missing, MAX_DIM_SMALL))
    }

    @Test
    fun `matrix for invalid tag is null (leave decoded untouched)`() {
        assertEquals(null, exifAdjustmentMatrix(99))
    }

    @Test
    fun `boundedOrientedDims caps the longest side to the fold base ceiling`() {
        // Regression for the blurry-first-preview report: the preview-resolution
        // estimator must seed from the ACTUAL fold base geometry (the bounded,
        // oriented decode), not the raw 4000x3000 header — a raw seed made the
        // scale half what the base could deliver.
        assertEquals(Pair(2000, 1500), boundedOrientedDims(4000, 3000, ExifInterface.ORIENTATION_NORMAL, 2048))
        assertEquals(Pair(1024, 1024), boundedOrientedDims(2048, 2048, ExifInterface.ORIENTATION_NORMAL, 2048))
        assertEquals(Pair(1035, 768), boundedOrientedDims(2070, 1536, ExifInterface.ORIENTATION_NORMAL, 2048))
    }

    @Test
    fun `boundedOrientedDims swaps dimensions for swapping exif tags only`() {
        // Swap tags 5-8 interchange the output dims (matching the EXIF matrix);
        // tags 1-4 keep them.
        assertEquals(Pair(300, 400), boundedOrientedDims(400, 300, ExifInterface.ORIENTATION_ROTATE_90, 2048))
        assertEquals(Pair(300, 400), boundedOrientedDims(400, 300, ExifInterface.ORIENTATION_ROTATE_270, 2048))
        assertEquals(Pair(300, 400), boundedOrientedDims(400, 300, ExifInterface.ORIENTATION_TRANSPOSE, 2048))
        assertEquals(Pair(300, 400), boundedOrientedDims(400, 300, ExifInterface.ORIENTATION_TRANSVERSE, 2048))
        assertEquals(Pair(400, 300), boundedOrientedDims(400, 300, ExifInterface.ORIENTATION_FLIP_HORIZONTAL, 2048))
        assertEquals(Pair(400, 300), boundedOrientedDims(400, 300, ExifInterface.ORIENTATION_ROTATE_180, 2048))
    }

    @Test
    fun `boundedOrientedDims matches decodeBoundedOriented exactly`() {
        // The bounds-only prediction must agree with the pixel decode's real
        // output (including BitmapFactory's ceil rounding and the swap).
        for ((w, h) in listOf(800 to 600, 3000 to 2000, 4000 to 3000, 1501 to 750)) {
            val (_, uri) = jpegFileSized(w, h)
            val decoded = decodeBoundedOriented(
                contentResolver, uri, maxDim = 2048, orientationReader = orientationTag(ExifInterface.ORIENTATION_ROTATE_90)
            )
            val predicted = boundedOrientedDims(w, h, ExifInterface.ORIENTATION_ROTATE_90, 2048)
            assertEquals("source ${w}x$h decoded ${decoded!!.width}x${decoded.height}",
                decoded.width to decoded.height, predicted)
        }
    }

    private fun jpegFileSized(w: Int, h: Int): Pair<File, Uri> {
        val file = File(context.cacheDir, "orient_probe_${w}x$h.jpg")
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        return file to Uri.fromFile(file)
    }

    private companion object {
        const val MAX_DIM_SMALL = 2048
        const val MARKER = 100
    }
}