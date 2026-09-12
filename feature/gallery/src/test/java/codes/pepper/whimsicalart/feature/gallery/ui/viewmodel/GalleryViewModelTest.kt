package codes.pepper.whimsicalart.feature.gallery.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.core.common.PreferencesManager
import codes.pepper.whimsicalart.feature.gallery.domain.GalleryRepository
import codes.pepper.whimsicalart.feature.gallery.domain.GroupDimension
import codes.pepper.whimsicalart.feature.gallery.domain.GroupDirection
import codes.pepper.whimsicalart.feature.gallery.domain.Photo
import codes.pepper.whimsicalart.feature.gallery.domain.SortDirection
import codes.pepper.whimsicalart.feature.gallery.domain.SortField
import codes.pepper.whimsicalart.feature.gallery.domain.tag.PhotoTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneClassifier
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneLabel
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class GalleryViewModelTest {

    private lateinit var repository: FakeGalleryRepository
    private lateinit var viewModel: GalleryViewModel
    private lateinit var contentResolver: ContentResolver
    private lateinit var context: Context
    private lateinit var preferences: PreferencesManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        contentResolver = context.contentResolver
        repository = FakeGalleryRepository()
        preferences = PreferencesManager(context)
        viewModel = GalleryViewModel(
            repository = repository,
            context = context,
            tagRepository = FakeTagRepository(),
            sceneClassifier = FakeSceneClassifier(),
            preferences = preferences
        )
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun photo(
        id: Long,
        name: String = "photo_$id.jpg",
        dateModified: Long = id,
        dateAdded: Long = id,
        size: Long = id,
        path: String = "",
        mimeType: String = "image/jpeg",
        dateTaken: Long = id
    ) = Photo(
        id = id,
        uri = Uri.parse("content://media/external/images/media/$id"),
        displayName = name,
        dateAdded = dateAdded,
        width = 1920,
        height = 1080,
        dateModified = dateModified,
        size = size,
        path = path,
        mimeType = mimeType,
        dateTaken = dateTaken
    )

    @Test
    fun `refreshPhotos keeps the grid visible and publishes fresh photos`() {
        repository.photos = listOf(photo(1), photo(2))
        viewModel.loadPhotos()
        idleMainLooper()
        assertEquals(2, viewModel.uiState.value.photos.size)
        assertFalse(viewModel.uiState.value.isLoading)

        repository.photos = listOf(photo(3))
        viewModel.refreshPhotos()
        idleMainLooper()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.photos.size)
        assertEquals(3, viewModel.uiState.value.photos.first().id)
    }

    @Test
    fun `refreshPhotos returns isRefreshing to false on success`() {
        repository.photos = listOf(photo(3))
        viewModel.refreshPhotos()
        idleMainLooper()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(1, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `refreshPhotos returns isRefreshing to false on error while keeping the grid`() {
        repository.photos = listOf(photo(1), photo(2))
        viewModel.loadPhotos()
        idleMainLooper()
        assertEquals(2, viewModel.uiState.value.photos.size)

        repository.error = IllegalStateException("disk gone")
        viewModel.refreshPhotos()
        idleMainLooper()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertNull("error must stay hidden while the grid is populated", viewModel.uiState.value.error)
        assertEquals(2, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `refreshPhotos returns isRefreshing to false on error with empty grid`() {
        viewModel.loadPhotos()
        idleMainLooper()

        repository.error = IllegalStateException("disk gone")
        viewModel.refreshPhotos()
        idleMainLooper()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals("disk gone", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.photos.isEmpty())
    }

    @Test
    fun `media store change triggers a debounced reload`() {
        repository.photos = listOf(photo(1))
        viewModel.loadPhotos()
        idleMainLooper()
        assertEquals(1, repository.queryCount)

        repository.photos = listOf(photo(1), photo(2))
        contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
        idleMainLooper()
        assertEquals(1, repository.queryCount)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(2, repository.queryCount)
        assertEquals(2, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `burst of media store changes collapses into one reload`() {
        viewModel.loadPhotos()
        idleMainLooper()
        assertEquals(1, repository.queryCount)

        contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
        contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
        contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        assertEquals(2, repository.queryCount)
    }

    @Test
    fun `resume without a media change reuses the cached list without querying`() {
        repository.photos = listOf(photo(1), photo(2))
        viewModel.loadPhotos()
        idleMainLooper()
        assertEquals(1, repository.queryCount)
        val cached = viewModel.uiState.value.photos

        viewModel.onGalleryResumed()
        idleMainLooper()

        assertEquals("no store change since the last query → no re-query", 1, repository.queryCount)
        assertSame("the cached list must stay untouched", cached, viewModel.uiState.value.photos)
    }

    @Test
    fun `resume after a media change re-queries immediately`() {
        repository.photos = listOf(photo(1))
        viewModel.loadPhotos()
        idleMainLooper()
        assertEquals(1, repository.queryCount)

        repository.photos = listOf(photo(1), photo(2))
        contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
        idleMainLooper() // deliver the observer → mediaDirty=true (debounce still pending)
        viewModel.onGalleryResumed() // the 400ms debounce has not elapsed
        idleMainLooper()

        assertTrue("resume after a real store change must re-query", repository.queryCount >= 2)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.photos.map { it.id })
    }

    @Test
    fun `refresh returning the same rows keeps the same photos instance`() {
        repository.photos = listOf(photo(1), photo(2))
        viewModel.loadPhotos()
        idleMainLooper()
        val cached = viewModel.uiState.value.photos

        viewModel.refreshPhotos()
        idleMainLooper()

        assertSame("identical rows must not recreate the list (no grid churn)", cached, viewModel.uiState.value.photos)
    }

    @Test
    fun `refresh with one new row updates only that row and keeps the other instances`() {
        repository.photos = listOf(photo(1), photo(2))
        viewModel.loadPhotos()
        idleMainLooper()
        val original = viewModel.uiState.value.photos

        repository.photos = listOf(photo(1), photo(2), photo(3))
        viewModel.refreshPhotos()
        idleMainLooper()

        val updated = viewModel.uiState.value.photos
        assertEquals(3, updated.size)
        assertSame("unchanged row keeps its instance", original[0], updated[0])
        assertSame("unchanged row keeps its instance", original[1], updated[1])
        assertTrue(updated.any { it.id == 3L })
    }

    @Test
    fun `refresh drops rows that left the media store`() {
        repository.photos = listOf(photo(1), photo(2), photo(3))
        viewModel.loadPhotos()
        idleMainLooper()

        repository.photos = listOf(photo(1), photo(3))
        viewModel.refreshPhotos()
        idleMainLooper()

        assertEquals(listOf(1L, 3L), viewModel.uiState.value.photos.map { it.id })
    }

    @Test
    fun `fresh row with a zeroed timestamp sorts on insertion then adopts its finalised timestamp`() {
        fun newPhoto(id: Long, dateModified: Long, dateAdded: Long) = Photo(
            id = id,
            uri = Uri.parse("content://media/external/images/media/$id"),
            displayName = "new_$id.jpg",
            dateAdded = dateAdded,
            width = 1920,
            height = 1080,
            dateModified = dateModified,
            size = id,
            path = "",
            mimeType = "image/jpeg",
            dateTaken = dateAdded
        )

        repository.photos = listOf(
            photo(1, dateModified = 5_000L),
            photo(2, dateModified = 6_000L)
        )
        viewModel.loadPhotos()
        idleMainLooper()
        assertEquals(listOf(2L, 1L), viewModel.uiState.value.displayedPhotos.map { it.id })

        // The new row is merged while its DATE_MODIFIED still reads 0 (the
        // scanner has not finalised it). The insertion-time fallback keeps it at
        // the top under the default LAST_MODIFIED DESC.
        repository.photos = listOf(
            photo(1, dateModified = 5_000L),
            photo(2, dateModified = 6_000L),
            newPhoto(3, dateModified = 0L, dateAdded = 10_000L)
        )
        viewModel.refreshPhotos()
        idleMainLooper()
        assertEquals(listOf(3L, 2L, 1L), viewModel.uiState.value.displayedPhotos.map { it.id })

        // The next refresh carries the finalised timestamp: mergePhotos must
        // adopt the FRESH instance (the stale 0 must never be pinned forever)
        // and the photo must hold its sorted position.
        repository.photos = listOf(
            photo(1, dateModified = 5_000L),
            photo(2, dateModified = 6_000L),
            newPhoto(3, dateModified = 7_000L, dateAdded = 10_000L)
        )
        viewModel.refreshPhotos()
        idleMainLooper()
        assertEquals(7_000L, viewModel.uiState.value.photos.single { it.id == 3L }.dateModified)
        assertEquals(listOf(3L, 2L, 1L), viewModel.uiState.value.displayedPhotos.map { it.id })
    }

    @Test
    fun `new row that reports zero-modified sorts to top and adopts the finalised timestamp on the next refresh`() {
        repository.photos = listOf(
            photo(1, dateModified = 5_000_000),
            photo(2, dateModified = 4_000_000)
        )
        viewModel.loadPhotos()
        idleMainLooper()
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.displayedPhotos.map { it.id })

        // MediaStore notifies; the fresh row still reports DATE_MODIFIED == 0
        // (the scanner finalises it asynchronously). It must land in its sort
        // position — top for newest — not dead-last (the coalesce happened at the
        // repository, so the stored value is whatever the query returned).
        repository.photos = listOf(
            photo(1, dateModified = 5_000_000),
            photo(2, dateModified = 4_000_000),
            photo(3, dateModified = 0, dateAdded = 9_000_000)
        )
        viewModel.refreshPhotos()
        idleMainLooper()

        val storedStale = viewModel.uiState.value.photos.single { it.id == 3L }
        assertEquals("the stale zero is stored as the query returned it", 0L, storedStale.dateModified)
        assertEquals(
            "the zero-modified row sorts top via the date-added fallback",
            listOf(3L, 1L, 2L),
            viewModel.uiState.value.displayedPhotos.map { it.id }
        )

        // The scanner finalises DATE_MODIFIED on the next refresh; mergePhotos
        // must adopt the fresh instance so the real timestamp replaces the stale
        // one and the photo holds its sorted position.
        repository.photos = listOf(
            photo(1, dateModified = 5_000_000),
            photo(2, dateModified = 4_000_000),
            photo(3, dateModified = 9_000_000, dateAdded = 9_000_000)
        )
        viewModel.refreshPhotos()
        idleMainLooper()

        val adopted = viewModel.uiState.value.photos.single { it.id == 3L }
        assertEquals(9_000_000L, adopted.dateModified)
        assertEquals(
            "photo must hold its top sorted position",
            listOf(3L, 1L, 2L),
            viewModel.uiState.value.displayedPhotos.map { it.id }
        )
    }

    @Test
    fun `refresh keeps existing instances only for unchanged rows and adopts fresh for metadata-changed rows`() {
        val existing1 = photo(1, dateModified = 5_000_000)
        val existing2 = photo(2, dateModified = 4_000_000)
        repository.photos = listOf(existing1, existing2)
        viewModel.loadPhotos()
        idleMainLooper()
        val before = viewModel.uiState.value.photos
        assertSame(existing1, before[0])
        assertSame(existing2, before[1])

        // Row 1 unchanged; row 2's metadata changed (a finalised timestamp).
        val fresh2 = photo(2, dateModified = 8_000_000)
        repository.photos = listOf(existing1, fresh2)
        viewModel.refreshPhotos()
        idleMainLooper()

        val after = viewModel.uiState.value.photos
        assertSame("unchanged row keeps its instance", existing1, after[0])
        assertNotSame("metadata-changed row adopts the fresh instance", existing2, after[1])
        assertEquals(8_000_000L, after[1].dateModified)
    }

    @Test
    fun `viewing preferences default to last-modified desc and no grouping`() {
        assertEquals(SortField.LAST_MODIFIED, viewModel.uiState.value.sortField)
        assertEquals(SortDirection.DESC, viewModel.uiState.value.sortDirection)
        assertEquals(GroupDimension.NONE, viewModel.uiState.value.groupDimension)
        assertEquals(GroupDirection.ASC, viewModel.uiState.value.groupDirection)
        assertTrue(viewModel.uiState.value.groups.isEmpty())
    }

    @Test
    fun `sort by name ascending orders displayed photos by name`() {
        repository.photos = listOf(
            photo(1, name = "b.jpg"),
            photo(2, name = "a.jpg"),
            photo(3, name = "c.jpg")
        )
        viewModel.loadPhotos()
        idleMainLooper()

        viewModel.setSortField(SortField.NAME)
        viewModel.setSortDirection(SortDirection.ASC)

        assertEquals(
            listOf("a.jpg", "b.jpg", "c.jpg"),
            viewModel.uiState.value.displayedPhotos.map { it.displayName }
        )
    }

    @Test
    fun `sort ties fall back to last-modified-descending`() {
        repository.photos = listOf(
            photo(1, name = "same.jpg", dateModified = 1000),
            photo(2, name = "same.jpg", dateModified = 3000),
            photo(3, name = "same.jpg", dateModified = 2000)
        )
        viewModel.loadPhotos()
        idleMainLooper()

        viewModel.setSortField(SortField.NAME)
        viewModel.setSortDirection(SortDirection.ASC)

        assertEquals(
            listOf(2L, 3L, 1L),
            viewModel.uiState.value.displayedPhotos.map { it.id }
        )
    }

    @Test
    fun `grouping by folder buckets photos and uses the first sorted photo per group`() {
        repository.photos = listOf(
            photo(1, name = "x.jpg", dateModified = 100, path = "Pictures/Trips/"),
            photo(2, name = "y.jpg", dateModified = 200, path = "DCIM/"),
            photo(3, name = "z.png", dateModified = 300, path = "Pictures/Trips/")
        )
        viewModel.loadPhotos()
        idleMainLooper()

        viewModel.setGroupDimension(GroupDimension.FOLDER)

        val groups = viewModel.uiState.value.groups
        assertEquals(listOf("DCIM", "Trips"), groups.map { it.label })
        assertEquals(2, groups.first { it.label == "Trips" }.photos.size)
        assertEquals(3L, groups.first { it.label == "Trips" }.firstPhoto.id)
    }

    @Test
    fun `descending group direction reverses the group order`() {
        repository.photos = listOf(
            photo(1, path = "Pictures/A/"),
            photo(2, path = "DCIM/"),
            photo(3, path = "Pictures/B/")
        )
        viewModel.loadPhotos()
        idleMainLooper()

        viewModel.setGroupDimension(GroupDimension.FOLDER)
        viewModel.setGroupDirection(GroupDirection.DESC)

        assertEquals(listOf("DCIM", "B", "A"), viewModel.uiState.value.groups.map { it.label })
    }

    @Test
    fun `opening a group and closing returns to the main gallery`() {
        repository.photos = listOf(
            photo(1, path = "DCIM/"),
            photo(2, path = "Pictures/")
        )
        viewModel.loadPhotos()
        idleMainLooper()

        viewModel.setGroupDimension(GroupDimension.FOLDER)
        viewModel.openGroup("DCIM")

        assertEquals("DCIM", viewModel.uiState.value.openedGroup)
        assertEquals(1, viewModel.uiState.value.groups.first { it.label == "DCIM" }.photos.size)

        viewModel.closeGroup()
        assertNull(viewModel.uiState.value.openedGroup)
    }

    @Test
    fun `back while multi-selecting clears the selection, matching the Cancel action`() {
        repository.photos = listOf(photo(1, path = "DCIM/"), photo(2, path = "DCIM/"))
        viewModel.loadPhotos()
        idleMainLooper()

        viewModel.toggleMultiSelectMode()
        viewModel.togglePhotoSelection(1L)
        assertTrue(viewModel.uiState.value.isMultiSelectMode)
        assertEquals(setOf(1L), viewModel.uiState.value.selectedPhotos)

        // The GalleryScreen BackHandler calls exactly this for multi-select:
        // leave selection mode with an empty selection (the top-bar Cancel).
        viewModel.clearSelection()

        assertFalse(viewModel.uiState.value.isMultiSelectMode)
        assertTrue(viewModel.uiState.value.selectedPhotos.isEmpty())
    }

    @Test
    fun `changing the group dimension clears the open drill-in`() {
        repository.photos = listOf(photo(1, path = "DCIM/"))
        viewModel.loadPhotos()
        idleMainLooper()

        viewModel.setGroupDimension(GroupDimension.FOLDER)
        viewModel.openGroup("DCIM")
        viewModel.setGroupDimension(GroupDimension.FILE_TYPE)

        assertNull(viewModel.uiState.value.openedGroup)
        assertEquals(GroupDimension.FILE_TYPE, viewModel.uiState.value.groupDimension)
    }

    @Test
    fun `viewing preferences persist and are restored on the next launch`() {
        repository.photos = listOf(photo(1))
        viewModel.setSortField(SortField.SIZE)
        viewModel.setSortDirection(SortDirection.ASC)
        viewModel.setGroupDimension(GroupDimension.TAKEN_MONTHLY)
        viewModel.setGroupDirection(GroupDirection.DESC)

        val relaunched = GalleryViewModel(
            repository = repository,
            context = context,
            tagRepository = FakeTagRepository(),
            sceneClassifier = FakeSceneClassifier(),
            preferences = PreferencesManager(context)
        )

        assertEquals(SortField.SIZE, relaunched.uiState.value.sortField)
        assertEquals(SortDirection.ASC, relaunched.uiState.value.sortDirection)
        assertEquals(GroupDimension.TAKEN_MONTHLY, relaunched.uiState.value.groupDimension)
        assertEquals(GroupDirection.DESC, relaunched.uiState.value.groupDirection)
    }

    private class FakeGalleryRepository : GalleryRepository {
        var photos: List<Photo> = emptyList()
        var queryCount = 0
        var error: Exception? = null

        override suspend fun getPhotos(): List<Photo> {
            queryCount++
            error?.let { throw it }
            return photos
        }

        override suspend fun getPhoto(uri: Uri): Photo? = photos.firstOrNull { it.uri == uri }
    }

    private class FakeTagRepository : TagRepository {
        override suspend fun upsert(tag: PhotoTag) = Unit
        override suspend fun remove(uri: Uri, tag: SceneTag) = Unit
        override suspend fun replace(uri: Uri, tags: List<PhotoTag>) = Unit
        override fun observeTags(): Flow<List<SceneTag>> = flowOf(emptyList())
        override fun observeFor(uri: Uri): Flow<List<PhotoTag>> = flowOf(emptyList())
        override suspend fun urisFor(tag: SceneTag): List<String> = emptyList()
        override fun observeCount(): Flow<Int> = flowOf(0)
    }

    private class FakeSceneClassifier : SceneClassifier {
        override suspend fun label(bitmap: Bitmap): List<SceneLabel> = emptyList()
    }
}
