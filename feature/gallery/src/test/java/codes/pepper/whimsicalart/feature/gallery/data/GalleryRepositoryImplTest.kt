package codes.pepper.whimsicalart.feature.gallery.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.gallery.domain.GalleryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class GalleryRepositoryImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun repository(
        columns: Array<String>,
        rows: List<Array<Any?>>,
        onProjection: (Array<out String>) -> Unit = {}
    ): GalleryRepository {
        val cursor = MatrixCursor(columns)
        rows.forEach { cursor.addRow(it) }
        ShadowContentResolver.registerProviderInternal(
            MediaStore.AUTHORITY,
            object : ContentProvider() {
                override fun onCreate(): Boolean = true

                override fun query(
                    uri: Uri,
                    projection: Array<out String>?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                    sortOrder: String?
                ): Cursor? {
                    if (projection != null) onProjection(projection)
                    return cursor
                }

                override fun getType(uri: Uri): String? = null

                override fun insert(uri: Uri, values: ContentValues?): Uri? = null

                override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

                override fun update(
                    uri: Uri,
                    values: ContentValues?,
                    selection: String?,
                    selectionArgs: Array<out String>?
                ): Int = 0
            }
        )
        return GalleryRepositoryImpl(context.contentResolver)
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `getPhotos maps the relative-path projection on API 29+`() {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            "relative_path"
        )
        val repo = repository(
            columns,
            listOf(
                arrayOf(
                    7L, "holiday.jpg", 1000L, 1920, 1080, 2000L, 42L,
                    "image/jpeg", 1500L, "Pictures/Holiday/"
                )
            )
        )

        val photo = runBlocking { repo.getPhotos() }.single()

        assertEquals(7L, photo.id)
        assertEquals("holiday.jpg", photo.displayName)
        assertEquals(1000L, photo.dateAdded)
        assertEquals(1920, photo.width)
        assertEquals(1080, photo.height)
        assertEquals(2_000_000L, photo.dateModified)
        assertEquals(42L, photo.size)
        assertEquals("Pictures/Holiday/", photo.path)
        assertEquals("image/jpeg", photo.mimeType)
        assertEquals(1500L, photo.dateTaken)
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `getPhoto maps the same columns for the editor path on API 29+`() {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            "relative_path"
        )
        val repo = repository(
            columns,
            listOf(
                arrayOf(
                    11L, "portrait.jpg", 500L, 800, 1200, 600L, 900L,
                    "image/png", 400L, "DCIM/"
                )
            )
        )

        val uri = Uri.parse("content://media/external/images/media/11")
        val photo = runBlocking { repo.getPhoto(uri) }

        assertNotNull(photo)
        photo!!.let {
            assertEquals(11L, it.id)
            assertEquals("portrait.jpg", it.displayName)
            assertEquals(600_000L, it.dateModified)
            assertEquals(900L, it.size)
            assertEquals("DCIM/", it.path)
            assertEquals("image/png", it.mimeType)
            assertEquals(400L, it.dateTaken)
            assertEquals(uri, it.uri)
        }
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `query projection on API 29+ never requests non-public columns`() {
        var projection: Array<out String>? = null
        val cursorColumns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            "relative_path"
        )
        val repo = repository(cursorColumns, emptyList()) { projection = it }

        runBlocking { repo.getPhotos() }

        val names = projection!!.toList()
        assertTrue("date_modified_ms must never be projected", "date_modified_ms" !in names)
        assertTrue(names.contains(MediaStore.Images.Media.SIZE))
        assertTrue(names.contains("relative_path"))
        assertTrue(names.contains(MediaStore.Images.Media.DATA).not())
        assertTrue("every projected column must be public", publicColumns.containsAll(names))
    }

    @Config(sdk = [Build.VERSION_CODES.O])
    @Test
    fun `getPhotos works below API 29 without the size column and falls back to seconds`() {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATA
        )
        val repo = repository(
            columns,
            listOf(
                arrayOf(
                    3L, "old.jpg", 1000L, 640, 480, 2000L,
                    "image/jpeg", 1500L, "/storage/emulated/0/Pictures/old.jpg"
                )
            )
        )

        val photo = runBlocking { repo.getPhotos() }.single()

        assertEquals(3L, photo.id)
        assertEquals("old.jpg", photo.displayName)
        assertEquals(2_000_000L, photo.dateModified)
        assertEquals(0L, photo.size)
        assertEquals("/storage/emulated/0/Pictures/old.jpg", photo.path)
        assertEquals("image/jpeg", photo.mimeType)
        assertEquals(1500L, photo.dateTaken)
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `getPhotos coalesces a zero-modified zero-taken fresh row to date-added`() {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            "relative_path"
        )
        val repo = repository(
            columns,
            listOf(
                arrayOf(
                    15L, "fresh.jpg", 7000L, 1920, 1080, 0L, 42L,
                    "image/jpeg", 0L, "Pictures/"
                )
            )
        )

        val photo = runBlocking { repo.getPhotos() }.single()

        // DATE_MODIFIED and DATE_TAKEN read 0 (the scanner finalises them
        // asynchronously); both must coalesce to DATE_ADDED so the row carries a
        // valid, recent sort key instead of dead-last 0.
        assertEquals("dateModified falls back to date-added", 7_000_000L, photo.dateModified)
        assertEquals("dateTaken falls back to date-added", 7000L, photo.dateTaken)
        assertEquals(7000L, photo.dateAdded)
    }

    @Config(sdk = [Build.VERSION_CODES.O])
    @Test
    fun `query projection below API 29 requests only pre-Q public columns`() {
        var projection: Array<out String>? = null
        val cursorColumns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATA
        )
        val repo = repository(cursorColumns, emptyList()) { projection = it }

        runBlocking { repo.getPhotos() }

        val names = projection!!.toList()
        assertTrue("date_modified_ms must never be projected", "date_modified_ms" !in names)
        assertTrue("_size is not public below API 29", MediaStore.Images.Media.SIZE !in names)
        assertTrue("relative_path is not public below API 29", "relative_path" !in names)
        assertTrue(names.contains(MediaStore.Images.Media.DATA))
        assertTrue("every projected column must be public", publicColumns.containsAll(names))
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `getPhotos coalesces zeroed timestamp columns to the insertion time`() {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            "relative_path"
        )
        val repo = repository(
            columns,
            listOf(
                // DATE_MODIFIED and DATE_TAKEN both read 0 — a freshly-written row
                // whose scanner has not finalised the timestamps yet. dateAdded
                // (seconds) must backfill both so the photo never sorts below its
                // real creation moment.
                arrayOf(5L, "new.jpg", 88_000L, 1920, 1080, 0L, 10L, "image/jpeg", 0L, "Pictures/")
            )
        )

        val photo = runBlocking { repo.getPhotos() }.single()

        assertEquals(88_000_000L, photo.dateModified)
        assertEquals(88_000L, photo.dateTaken)
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `getPhotos keeps real nonzero timestamps untouched`() {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            "relative_path"
        )
        val repo = repository(
            columns,
            listOf(
                arrayOf(6L, "done.jpg", 1_000L, 1920, 1080, 7_000L, 10L, "image/jpeg", 8_000L, "Pictures/")
            )
        )

        val photo = runBlocking { repo.getPhotos() }.single()

        assertEquals(7_000_000L, photo.dateModified)
        assertEquals(8_000L, photo.dateTaken)
        assertEquals(1_000L, photo.dateAdded)
    }

    private companion object {
        private val publicColumns = setOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            "relative_path"
        )
    }
}