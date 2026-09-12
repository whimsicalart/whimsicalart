package codes.pepper.whimsicalart.feature.gallery.ui.viewmodel

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import codes.pepper.whimsicalart.core.common.PreferencesManager
import codes.pepper.whimsicalart.feature.gallery.domain.GalleryRepository
import codes.pepper.whimsicalart.feature.gallery.domain.GroupDimension
import codes.pepper.whimsicalart.feature.gallery.domain.GroupDirection
import codes.pepper.whimsicalart.feature.gallery.domain.Photo
import codes.pepper.whimsicalart.feature.gallery.domain.PhotoGroup
import codes.pepper.whimsicalart.feature.gallery.domain.SortDirection
import codes.pepper.whimsicalart.feature.gallery.domain.SortField
import codes.pepper.whimsicalart.feature.gallery.domain.groupPhotos
import codes.pepper.whimsicalart.feature.gallery.domain.sortPhotos
import codes.pepper.whimsicalart.feature.gallery.domain.tag.PhotoTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneClassifier
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.TagRepository
import codes.pepper.whimsicalart.feature.gallery.domain.tag.TagTransformer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GalleryUiState(
    val photos: List<Photo> = emptyList(),
    val filteredPhotos: List<Photo>? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isMultiSelectMode: Boolean = false,
    val selectedPhotos: Set<Long> = emptySet(),
    val sortField: SortField = SortField.LAST_MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESC,
    val groupDimension: GroupDimension = GroupDimension.NONE,
    val groupDirection: GroupDirection = GroupDirection.ASC,
    val displayedPhotos: List<Photo> = emptyList(),
    val groups: List<PhotoGroup> = emptyList(),
    val openedGroup: String? = null,
    val availableTags: List<SceneTag> = emptyList(),
    val selectedTag: SceneTag? = null,
    val photoTags: Map<Uri, List<PhotoTag>> = emptyMap(),
    val isTagging: Boolean = false,
    val tagError: String? = null
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: GalleryRepository,
    @ApplicationContext private val context: Context,
    private val tagRepository: TagRepository,
    private val sceneClassifier: SceneClassifier,
    private val preferences: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaContentObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            scheduleMediaReload()
        }
    }
    private var mediaReloadJob: Job? = null

    /**
     * True from a media-store notification (scheduleMediaReload) until the next
     * successful query. ON_RESUME consults it so returning from Settings / the
     * editor re-queries ONLY when the store actually changed, instead of
     * re-running the full MediaStore query on every resume (the navigation
     * churn report: "reuse a cached one and just update the differences").
     */
    private var mediaDirty = false

    init {
        observeTags()
        registerMediaObserver()
        restoreViewingPreferences()
        publishDisplay()
    }

    fun loadPhotos() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val photos = repository.getPhotos()
                mediaDirty = false
                val current = _uiState.value
                _uiState.value = current.copy(
                    photos = photos,
                    isLoading = false,
                    error = null
                )
                publishDisplay()
            } catch (e: Exception) {
                _uiState.value = GalleryUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load photos"
                )
            }
        }
    }

    /**
     * ON_RESUME / navigation-return hook: shows the cached in-memory list
     * instantly (it already lives in [GalleryUiState.photos]) and re-queries
     * ONLY when the media-store change observer fired since the last query —
     * returning from Settings or the editor never re-runs the full MediaStore
     * query (tracker: "make it reuse a cached one and just update the
     * differences").
     */
    fun onGalleryResumed() {
        if (mediaDirty) {
            refreshPhotos()
        }
    }

    /**
     * Non-disruptive reload used by pull-to-refresh, [ContentObserver] media
     * changes and [onGalleryResumed]. Keeps the grid visible while refreshing
     * instead of flipping to the full-screen loader. The fresh rows are
     * DIFF-MERGED into the current list ([mergePhotos]) so unchanged rows keep
     * their instances (stable LazyGrid keys / scroll / Coil thumbnails) and only
     * added/removed/updated rows change the grid.
     */
    fun refreshPhotos() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
                val photos = repository.getPhotos()
                mediaDirty = false
                _uiState.update {
                    it.copy(photos = mergePhotos(it.photos, photos), isRefreshing = false, error = null)
                }
                publishDisplay()
            } catch (e: Exception) {
                _uiState.update {
                    if (it.photos.isEmpty()) {
                        it.copy(isRefreshing = false, error = e.message ?: "Failed to refresh")
                    } else {
                        it.copy(isRefreshing = false)
                    }
                }
            }
        }
    }

    /**
     * Pure diff-merge of a fresh MediaStore query into the current list, keyed by
     * stable photo id: rows that are data-equal keep their EXISTING instances (so
     * the grid never churns for an identical refresh — even returning the very
     * same list instance), removed ids drop, and only genuinely new or updated
     * rows come from [fresh]. Adopting the fresh instance whenever its metadata
     * changed is what lets a FINALISED `DATE_MODIFIED` replace a stale `0` on the
     * next refresh (otherwise the diff-merge pins the first-read timestamp
     * forever). Order follows [fresh] (the store's authoritative order).
     */
    internal fun mergePhotos(existing: List<Photo>, fresh: List<Photo>): List<Photo> {
        if (existing == fresh) return existing
        if (existing.isEmpty() || fresh.isEmpty()) return fresh
        val existingById = existing.associateBy { it.id }
        val merged = fresh.map { freshPhoto ->
            val existingPhoto = existingById[freshPhoto.id]
            if (existingPhoto != null && existingPhoto == freshPhoto) existingPhoto else freshPhoto
        }
        return if (merged == existing) existing else merged
    }

    private fun registerMediaObserver() {
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaContentObserver
            )
        }
    }

    /** Debounce bursty media-store notifications (eg. a camera writing multiple rows). */
    private fun scheduleMediaReload() {
        mediaDirty = true
        mediaReloadJob?.cancel()
        mediaReloadJob = viewModelScope.launch {
            delay(MEDIA_RELOAD_DEBOUNCE_MS)
            refreshPhotos()
        }
    }

    override fun onCleared() {
        mediaReloadJob?.cancel()
        runCatching { context.contentResolver.unregisterContentObserver(mediaContentObserver) }
        super.onCleared()
    }

    private fun observeTags() {
        viewModelScope.launch {
            tagRepository.observeTags().collect { tags ->
                _uiState.update { it.copy(availableTags = tags) }
            }
        }
    }

    /**
     * Runs on-device scene labeling on [photo] and persists the curated tags.
     * ML Kit inference happens on [Dispatchers.Default]; the pure [TagTransformer]
     * mapping is the only place label text meets the curated vocabulary.
     */
    fun tagPhoto(photo: Photo) {
        if (_uiState.value.isTagging) return
        _uiState.update { it.copy(isTagging = true, tagError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val bitmap = decode(photo.uri)
                        ?: throw IllegalStateException("Could not decode image")
                    val tags = try {
                        val labels = sceneClassifier.label(bitmap)
                        TagTransformer.transform(labels)
                            .filter { it.confidence > 0f }
                            .map { bridge ->
                                PhotoTag(
                                    uri = photo.uri,
                                    tag = bridge.tag,
                                    confidence = bridge.confidence,
                                    taggedAt = System.currentTimeMillis()
                                )
                            }
                    } finally {
                        bitmap.recycle()
                    }
                    tags
                }
            }
            _uiState.update { it.copy(isTagging = false) }
            result.onSuccess { tags ->
                viewModelScope.launch { tagRepository.replace(photo.uri, tags) }
            }.onFailure { e ->
                _uiState.update { it.copy(tagError = e.message ?: "Tagging failed") }
            }
        }
    }

    fun removePhotoTag(uri: Uri, tag: SceneTag) {
        viewModelScope.launch { tagRepository.remove(uri, tag) }
    }

    /** Narrow the grid to photos that carry [tag]; null clears the filter. */
    fun setSelectedTag(tag: SceneTag?) {
        _uiState.update { it.copy(selectedTag = tag, filteredPhotos = null) }
        if (tag == null) {
            publishDisplay()
            return
        }
        viewModelScope.launch {
            val uris = tagRepository.urisFor(tag)
            val subset = _uiState.value.photos.filter { uris.contains(it.uri.toString()) }
            _uiState.update { it.copy(selectedTag = tag, filteredPhotos = subset) }
            publishDisplay()
        }
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()

    fun toggleMultiSelectMode() {
        _uiState.apply {
            val isNowMulti = !value.isMultiSelectMode
            value = value.copy(
                isMultiSelectMode = isNowMulti,
                selectedPhotos = if (isNowMulti) emptySet() else value.selectedPhotos
            )
        }
    }

    fun togglePhotoSelection(photoId: Long) {
        val currentSelected = _uiState.value.selectedPhotos.toMutableSet()
        if (currentSelected.contains(photoId)) {
            currentSelected.remove(photoId)
        } else {
            currentSelected.add(photoId)
        }
        _uiState.value = _uiState.value.copy(selectedPhotos = currentSelected)
    }

    fun selectAllPhotos() {
        val allIds = _uiState.value.photos.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(selectedPhotos = allIds)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedPhotos = emptySet(),
            isMultiSelectMode = false
        )
    }

    fun getSelectedPhotos(): List<Photo> {
        return _uiState.value.photos.filter {
            _uiState.value.selectedPhotos.contains(it.id)
        }
    }

    fun setSortField(field: SortField) {
        _uiState.update { it.copy(sortField = field) }
        preferences.gallerySortField = field.name
        publishDisplay()
    }

    fun setSortDirection(direction: SortDirection) {
        _uiState.update { it.copy(sortDirection = direction) }
        preferences.gallerySortDirection = direction.name
        publishDisplay()
    }

    fun setGroupDimension(dimension: GroupDimension) {
        _uiState.update { it.copy(groupDimension = dimension, openedGroup = null) }
        preferences.galleryGroupDimension = dimension.name
        publishDisplay()
    }

    fun setGroupDirection(direction: GroupDirection) {
        _uiState.update { it.copy(groupDirection = direction) }
        preferences.galleryGroupDirection = direction.name
        publishDisplay()
    }

    /** Drill into [label]'s photos; the top bar back button returns to the main gallery. */
    fun openGroup(label: String) {
        _uiState.update { it.copy(openedGroup = label) }
    }

    fun closeGroup() {
        _uiState.update { it.copy(openedGroup = null) }
    }

    /** Restores the last sort/group combination from preferences on launch. */
    private fun restoreViewingPreferences() {
        val field = runCatching { SortField.valueOf(preferences.gallerySortField) }
            .getOrDefault(SortField.LAST_MODIFIED)
        val fieldDirection = runCatching { SortDirection.valueOf(preferences.gallerySortDirection) }
            .getOrDefault(SortDirection.DESC)
        val dimension = runCatching { GroupDimension.valueOf(preferences.galleryGroupDimension) }
            .getOrDefault(GroupDimension.NONE)
        val groupDirection = runCatching { GroupDirection.valueOf(preferences.galleryGroupDirection) }
            .getOrDefault(GroupDirection.ASC)
        _uiState.update {
            it.copy(
                sortField = field,
                sortDirection = fieldDirection,
                groupDimension = dimension,
                groupDirection = groupDirection
            )
        }
    }

    /**
     * Derives the [displayedPhotos] and [groups] for the main/group grid from the
     * current photos (or narrowed tag filter), active sort rule and grouping.
     */
    private fun publishDisplay() {
        val state = _uiState.value
        val base = state.filteredPhotos ?: state.photos
        val sorted = sortPhotos(base, state.sortField, state.sortDirection)
        val groups = if (state.groupDimension == GroupDimension.NONE) {
            emptyList()
        } else {
            groupPhotos(sorted, state.groupDimension, state.groupDirection)
        }
        val opened = state.openedGroup?.takeIf { label -> groups.any { it.label == label } }
        _uiState.update {
            it.copy(displayedPhotos = sorted, groups = groups, openedGroup = opened)
        }
    }

    companion object {
        private const val MEDIA_RELOAD_DEBOUNCE_MS = 400L
    }
}