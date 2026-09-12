package codes.pepper.whimsicalart.feature.gallery.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Sort field for the gallery grid. */
enum class SortField {
    NAME, PATH, SIZE, LAST_MODIFIED, DATE_TAKEN
}

/** Sort direction; applied to whatever [SortField] is active. */
enum class SortDirection {
    ASC, DESC
}

/** Grouping dimension; [NONE] keeps the flat no-label grid. */
enum class GroupDimension {
    NONE, FOLDER, FILE_TYPE, EXTENSION,
    MODIFIED_DAILY, MODIFIED_MONTHLY,
    TAKEN_DAILY, TAKEN_MONTHLY
}

/** Direction groups are ordered with; ignored while [GroupDimension.NONE] is active. */
enum class GroupDirection {
    ASC, DESC
}

/** A named bucket of photos; the group tile thumbnail is [firstPhoto]. */
data class PhotoGroup(
    val label: String,
    val photos: List<Photo>
) {
    val firstPhoto: Photo get() = photos.first()
}

/**
 * Parent-folder name for [GroupDimension.FOLDER]: the last path segment above
 * the file name. Blank when there is no parent.
 */
fun Photo.folderName(): String = path.substringBeforeLast('/', "").substringAfterLast('/')

/** File extension for [GroupDimension.EXTENSION]: the suffix after the last dot. */
fun Photo.extension(): String = displayName.substringAfterLast('.', "")

/** The [GroupDimension] label a photo falls under (used to bucket groups). */
fun Photo.groupLabel(dimension: GroupDimension): String = when (dimension) {
    GroupDimension.NONE -> ""
    GroupDimension.FOLDER -> folderName().ifBlank { "Unknown folder" }
    GroupDimension.FILE_TYPE -> mimeType.ifBlank { "Unknown type" }
    GroupDimension.EXTENSION -> extension().ifBlank { "No extension" }
    GroupDimension.MODIFIED_DAILY -> DateBuckets.daily(dateModified)
    GroupDimension.MODIFIED_MONTHLY -> DateBuckets.monthly(dateModified)
    GroupDimension.TAKEN_DAILY -> DateBuckets.daily(dateTaken)
    GroupDimension.TAKEN_MONTHLY -> DateBuckets.monthly(dateTaken)
}

/**
 * Sort key for [field], hardening the two timestamp fields: a `0` value (a
 * freshly-written MediaStore row frequently reports `DATE_MODIFIED == 0` / 
 * `DATE_TAKEN == 0` until the scanner finalises it) falls back to the
 * insertion time so no photo can ever sort below its real creation moment.
 * `DATE_ADDED` is stored in seconds while `DATE_MODIFIED` is in millis; the
 * fallback is scaled to the active column's unit to keep comparisons fair.
 */
fun Photo.sortKey(field: SortField): Long = when (field) {
    SortField.LAST_MODIFIED -> if (dateModified > 0L) dateModified else dateAdded * 1000L
    SortField.DATE_TAKEN -> if (dateTaken > 0L) dateTaken else dateAdded
    else -> 0L
}

/**
 * Stable sort: photos sharing the primary key fall back to the default
 * last-modified-descending order. Photos whose active timestamp is 0 (MediaStore
 * reports 0 before the scanner finalises it) fall back to [Photo.dateAdded] so no
 * row can sort below its real creation moment.
 */
fun sortPhotos(photos: List<Photo>, field: SortField, direction: SortDirection): List<Photo> {
    val primary: Comparator<Photo> = when (field) {
        SortField.NAME -> compareBy { it.displayName }
        SortField.PATH -> compareBy { it.path }
        SortField.SIZE -> compareBy { it.size }
        SortField.LAST_MODIFIED -> compareBy { it.sortKey(SortField.LAST_MODIFIED) }
        SortField.DATE_TAKEN -> compareBy { it.sortKey(SortField.DATE_TAKEN) }
    }
    val base = when (direction) {
        SortDirection.ASC -> primary
        SortDirection.DESC -> primary.reversed()
    }
    val stable = base.thenComparator { a, b ->
        b.dateModified.ifZero(b.dateAdded).compareTo(a.dateModified.ifZero(a.dateAdded))
    }
    return photos.sortedWith(stable)
}

private fun Long.ifZero(fallback: Long): Long = if (this > 0L) this else fallback

/**
 * Buckets the already-sorted [photos] into [PhotoGroup]s keyed by the
 * dimension's label. Returns an empty list while [GroupDimension.NONE].
 * Groups are ordered by the group direction: plain text comparison for
 * folder/file-type/extension, chronological for date buckets (their
 * `yyyy-MM-dd` / `yyyy-MM` labels sort chronologically as strings).
 */
fun groupPhotos(
    sorted: List<Photo>,
    dimension: GroupDimension,
    direction: GroupDirection
): List<PhotoGroup> {
    if (dimension == GroupDimension.NONE) return emptyList()
    val byLabel = LinkedHashMap<String, MutableList<Photo>>()
    for (photo in sorted) {
        byLabel.getOrPut(photo.groupLabel(dimension)) { mutableListOf() }.add(photo)
    }
    val groups = byLabel.entries.map { (label, photos) ->
        PhotoGroup(label = label, photos = photos)
    }.sortedBy { it.label }
    return when (direction) {
        GroupDirection.ASC -> groups
        GroupDirection.DESC -> groups.reversed()
    }
}

/** Date-bucket labels; kept internal so tests can pair values with their labels. */
internal object DateBuckets {
    private val DAILY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val MONTHLY = DateTimeFormatter.ofPattern("yyyy-MM")

    fun daily(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DAILY)

    fun monthly(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(MONTHLY)
}