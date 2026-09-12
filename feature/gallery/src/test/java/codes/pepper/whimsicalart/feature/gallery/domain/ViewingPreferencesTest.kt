package codes.pepper.whimsicalart.feature.gallery.domain

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewingPreferencesTest {

    private fun photo(
        id: Long,
        name: String = "photo_$id.jpg",
        dateAdded: Long = 0L,
        dateModified: Long = 0L,
        size: Long = 0L,
        path: String = "",
        mimeType: String = "image/jpeg",
        dateTaken: Long = 0L
    ) = Photo(
        id = id,
        uri = Uri.parse("content://media/$id"),
        displayName = name,
        dateAdded = dateAdded,
        width = 0,
        height = 0,
        dateModified = dateModified,
        size = size,
        path = path,
        mimeType = mimeType,
        dateTaken = dateTaken
    )

    @Test
    fun `sorts ascending and descending for each sort field`() {
        val photos = listOf(
            photo(1, name = "b.jpg", path = "/z/", size = 2, dateModified = 200, dateTaken = 300),
            photo(2, name = "a.jpg", path = "/a/", size = 3, dateModified = 100, dateTaken = 100),
            photo(3, name = "c.jpg", path = "/m/", size = 1, dateModified = 300, dateTaken = 200)
        )

        assertEquals(
            listOf(2L, 1L, 3L),
            sortPhotos(photos, SortField.NAME, SortDirection.ASC).map { it.id }
        )
        assertEquals(
            listOf(3L, 1L, 2L),
            sortPhotos(photos, SortField.NAME, SortDirection.DESC).map { it.id }
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            sortPhotos(photos, SortField.PATH, SortDirection.ASC).map { it.id }
        )
        assertEquals(
            listOf(3L, 1L, 2L),
            sortPhotos(photos, SortField.SIZE, SortDirection.ASC).map { it.id }
        )
        assertEquals(
            listOf(2L, 1L, 3L),
            sortPhotos(photos, SortField.LAST_MODIFIED, SortDirection.ASC).map { it.id }
        )
        assertEquals(
            listOf(3L, 1L, 2L),
            sortPhotos(photos, SortField.LAST_MODIFIED, SortDirection.DESC).map { it.id }
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            sortPhotos(photos, SortField.DATE_TAKEN, SortDirection.ASC).map { it.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            sortPhotos(photos, SortField.DATE_TAKEN, SortDirection.DESC).map { it.id }
        )
    }

    @Test
    fun `sort is stable and ties fall back to last-modified-descending`() {
        val photos = listOf(
            photo(1, name = "same.jpg", dateModified = 100),
            photo(2, name = "same.jpg", dateModified = 300),
            photo(3, name = "same.jpg", dateModified = 200)
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            sortPhotos(photos, SortField.NAME, SortDirection.ASC).map { it.id }
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            sortPhotos(photos, SortField.NAME, SortDirection.DESC).map { it.id }
        )
    }

    @Test
    fun `zeroed timestamp keys fall back to the insertion time`() {
        val photos = listOf(
            // photo 1 was MOST RECENTLY ADDED — its DATE_MODIFIED and DATE_TAKEN
            // still read 0 (the scanner has not finalised them). The fallback
            // must rank it newest on both timestamp fields, never last.
            photo(1, dateAdded = 3_000L, dateModified = 0L, dateTaken = 0L),
            photo(2, dateAdded = 1_000L, dateModified = 5_000L, dateTaken = 5_000L),
            photo(3, dateAdded = 2_000L, dateModified = 4_000L, dateTaken = 4_000L)
        )

        assertEquals(
            listOf(1L, 2L, 3L),
            sortPhotos(photos, SortField.LAST_MODIFIED, SortDirection.DESC).map { it.id }
        )
        assertEquals(
            listOf(3L, 2L, 1L),
            sortPhotos(photos, SortField.LAST_MODIFIED, SortDirection.ASC).map { it.id }
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            sortPhotos(photos, SortField.DATE_TAKEN, SortDirection.DESC).map { it.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            sortPhotos(photos, SortField.DATE_TAKEN, SortDirection.ASC).map { it.id }
        )
    }

    @Test
    fun `groups by folder file type and extension labels`() {
        val photos = listOf(
            photo(1, name = "a.jpg", path = "/storage/Pictures/Trips/", mimeType = "image/jpeg"),
            photo(2, name = "b.jpg", path = "/storage/DCIM/", mimeType = "image/jpeg"),
            photo(3, name = "c.png", path = "/storage/Pictures/Trips/", mimeType = "image/png"),
            photo(4, name = "d", path = "/storage/Downloads/", mimeType = "")
        )

        assertEquals(
            listOf("DCIM", "Downloads", "Trips"),
            groupPhotos(photos, GroupDimension.FOLDER, GroupDirection.ASC).map { it.label }
        )
        assertEquals(
            listOf("Unknown type", "image/jpeg", "image/png"),
            groupPhotos(photos, GroupDimension.FILE_TYPE, GroupDirection.ASC).map { it.label }
        )
        assertEquals(
            listOf("No extension", "jpg", "png"),
            groupPhotos(photos, GroupDimension.EXTENSION, GroupDirection.ASC).map { it.label }
        )
    }

    @Test
    fun `groups date buckets into yyyy-MM-dd and yyyy-MM labels`() {
        val day1 = 1_700_000_000_000L
        val modified = listOf(
            photo(1, dateModified = day1),
            photo(2, dateModified = day1 + 30_000L),
            photo(3, dateModified = day1 + 100L)
        )

        val daily = groupPhotos(modified, GroupDimension.MODIFIED_DAILY, GroupDirection.ASC)
        assertEquals(1, daily.size)
        assertEquals(DateBuckets.daily(day1), daily.single().label)
        assertEquals(3, daily.single().photos.size)

        val takenMonthly = groupPhotos(
            listOf(photo(1, dateTaken = day1), photo(2, dateTaken = day1)),
            GroupDimension.TAKEN_MONTHLY,
            GroupDirection.ASC
        )
        assertEquals(1, takenMonthly.size)
        assertEquals(DateBuckets.monthly(day1), takenMonthly.single().label)
        assertEquals(1L, takenMonthly.single().firstPhoto.id)
    }

    @Test
    fun `group direction orders date buckets chronologically`() {
        val day1 = 1_700_000_000_000L
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val photos = listOf(
            photo(1, dateModified = day1 + weekMs),
            photo(2, dateModified = day1),
            photo(3, dateModified = day1 + 2 * weekMs)
        )

        val ascending = groupPhotos(photos, GroupDimension.MODIFIED_DAILY, GroupDirection.ASC)
        assertEquals(
            listOf(DateBuckets.daily(day1), DateBuckets.daily(day1 + weekMs), DateBuckets.daily(day1 + 2 * weekMs)),
            ascending.map { it.label }
        )

        val descending = groupPhotos(photos, GroupDimension.MODIFIED_DAILY, GroupDirection.DESC)
        assertEquals(
            listOf(DateBuckets.daily(day1 + 2 * weekMs), DateBuckets.daily(day1 + weekMs), DateBuckets.daily(day1)),
            descending.map { it.label }
        )
    }

    @Test
    fun `no grouping returns an empty group list`() {
        val photos = listOf(photo(1))

        assertTrue(groupPhotos(photos, GroupDimension.NONE, GroupDirection.ASC).isEmpty())
        assertTrue(groupPhotos(photos, GroupDimension.NONE, GroupDirection.DESC).isEmpty())
    }

    @Test
    fun `group first photo is the first under the sort by ordering`() {
        val photos = listOf(
            photo(1, name = "a.jpg", dateModified = 100, path = "Pictures/"),
            photo(2, name = "b.jpg", dateModified = 300, path = "Pictures/"),
            photo(3, name = "c.jpg", dateModified = 200, path = "Pictures/")
        )
        val sorted = sortPhotos(photos, SortField.NAME, SortDirection.ASC)
        val group = groupPhotos(sorted, GroupDimension.FOLDER, GroupDirection.ASC).single()

        assertEquals(3, group.photos.size)
        assertEquals(1L, group.firstPhoto.id)
    }
}