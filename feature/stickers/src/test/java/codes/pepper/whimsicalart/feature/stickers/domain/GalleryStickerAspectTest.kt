package codes.pepper.whimsicalart.feature.stickers.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryStickerAspectTest {

    @Test
    fun `intrinsicAspect keeps a plain landscape orientation`() {
        assertEquals(2f, intrinsicAspect(2000, 1000, ExifInterface.ORIENTATION_NORMAL), 0.001f)
        assertEquals(0.5f, intrinsicAspect(1000, 2000, ExifInterface.ORIENTATION_NORMAL), 0.001f)
    }

    @Test
    fun `intrinsicAspect swaps stored-wide photos that need a 90-degree rotation`() {
        // A camera stores a portrait photo as a wide buffer with a rotation tag.
        assertEquals(0.5f, intrinsicAspect(2000, 1000, ExifInterface.ORIENTATION_ROTATE_90), 0.001f)
        assertEquals(0.5f, intrinsicAspect(2000, 1000, ExifInterface.ORIENTATION_TRANSPOSE), 0.001f)
        assertEquals(2f, intrinsicAspect(1000, 2000, ExifInterface.ORIENTATION_ROTATE_270), 0.001f)
        assertEquals(2f, intrinsicAspect(1000, 2000, ExifInterface.ORIENTATION_TRANSVERSE), 0.001f)
    }

    @Test
    fun `intrinsicAspect falls back to square for garbage bounds`() {
        assertEquals(1f, intrinsicAspect(0, 0, ExifInterface.ORIENTATION_NORMAL), 0.001f)
        assertEquals(1f, intrinsicAspect(10, 0, ExifInterface.ORIENTATION_NORMAL), 0.001f)
    }

    @Test
    fun `galleryStickerAspect reads the real image's bounds-only ratio`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val file = File(app.cacheDir, "gallery_aspect.jpg")
        val src = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }

        val ratio = galleryStickerAspect(app.contentResolver, Uri.fromFile(file))
        assertEquals(2f, ratio, 0.001f)
    }

    @Test
    fun `galleryStickerAspect defers to the injected EXIF reader for orientation swaps`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val file = File(app.cacheDir, "gallery_swapped.jpg")
        val src = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        FileOutputStream(file).use { out -> src.compress(Bitmap.CompressFormat.JPEG, 100, out) }

        val swapped = galleryStickerAspect(
            app.contentResolver,
            Uri.fromFile(file),
            ExifAspectReader { _, _ -> ExifInterface.ORIENTATION_ROTATE_90 }
        )
        // Buffer is 20x10 but tagged as rotated 90° → the portrait is 10x20.
        assertEquals(0.5f, swapped, 0.001f)
    }

    @Test
    fun `galleryStickerAspect falls back to square when the file cannot be read`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        assertEquals(1f, galleryStickerAspect(app.contentResolver, Uri.fromFile(File(app.cacheDir, "missing.jpg"))), 0.001f)
        assertEquals(1f, galleryStickerAspect(app.contentResolver, Uri.parse("content://nope/1")), 0.001f)
    }
}