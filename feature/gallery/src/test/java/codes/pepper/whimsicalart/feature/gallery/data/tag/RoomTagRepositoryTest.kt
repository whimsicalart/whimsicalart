package codes.pepper.whimsicalart.feature.gallery.data.tag

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.feature.gallery.domain.tag.PhotoTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTagRepositoryTest {

    private lateinit var db: GalleryDatabase
    private lateinit var repository: RoomTagRepository
    private val uriA = Uri.parse("content://media/external/images/media/1")
    private val uriB = Uri.parse("content://media/external/images/media/2")

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java).build()
        repository = RoomTagRepository(db.photoTagDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun tag(uri: Uri, tag: SceneTag, conf: Float) =
        PhotoTag(uri, tag, conf, System.currentTimeMillis())

    @Test
    fun upsertThenUrisForReturnsUri() = runBlocking {
        repository.upsert(tag(uriA, SceneTag.FOOD, 0.9f))
        assertTrue(repository.urisFor(SceneTag.FOOD).contains(uriA.toString()))
    }

    @Test
    fun replaceClearsOldTagsThenAppliesNewOnes() = runBlocking {
        repository.upsert(tag(uriA, SceneTag.FOOD, 0.9f))
        repository.upsert(tag(uriA, SceneTag.OUTDOOR, 0.7f))
        repository.replace(uriA, listOf(tag(uriA, SceneTag.NATURE, 0.8f)))

        assertTrue(repository.urisFor(SceneTag.FOOD).isEmpty())
        assertTrue(repository.urisFor(SceneTag.OUTDOOR).isEmpty())
        assertTrue(repository.urisFor(SceneTag.NATURE).contains(uriA.toString()))
    }

    @Test
    fun removeDeletesASingleMapping() = runBlocking {
        repository.upsert(tag(uriA, SceneTag.FOOD, 0.9f))
        repository.upsert(tag(uriA, SceneTag.NATURE, 0.8f))
        repository.remove(uriA, SceneTag.FOOD)

        assertTrue(repository.urisFor(SceneTag.FOOD).isEmpty())
        assertTrue(repository.urisFor(SceneTag.NATURE).contains(uriA.toString()))
    }

    @Test
    fun observeTagsEmitsDistinctTags() = runBlocking {
        repository.upsert(tag(uriA, SceneTag.FOOD, 0.9f))
        repository.upsert(tag(uriB, SceneTag.FOOD, 0.6f))
        repository.upsert(tag(uriA, SceneTag.NATURE, 0.8f))

        val tags = repository.observeTags().first()
        assertTrue(tags.contains(SceneTag.FOOD))
        assertTrue(tags.contains(SceneTag.NATURE))
    }

    @Test
    fun observeForUriReturnsThatPhotosTags() = runBlocking {
        repository.upsert(tag(uriA, SceneTag.FOOD, 0.9f))
        repository.upsert(tag(uriB, SceneTag.NATURE, 0.8f))

        val a = repository.observeFor(uriA).first()
        assertEquals(1, a.size)
        assertEquals(SceneTag.FOOD, a[0].tag)
        assertEquals(uriA, a[0].uri)
    }

    @Test
    fun observeCountTracksRows() = runBlocking {
        repository.upsert(tag(uriA, SceneTag.FOOD, 0.9f))
        repository.upsert(tag(uriA, SceneTag.NATURE, 0.8f))
        repository.upsert(tag(uriB, SceneTag.FOOD, 0.6f))
        assertEquals(3, repository.observeCount().first())
    }
}