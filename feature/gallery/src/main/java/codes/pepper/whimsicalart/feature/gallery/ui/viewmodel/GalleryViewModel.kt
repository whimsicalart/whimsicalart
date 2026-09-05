package codes.pepper.whimsicalart.feature.gallery.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import codes.pepper.whimsicalart.feature.gallery.domain.GalleryRepository
import codes.pepper.whimsicalart.feature.gallery.domain.Photo
import codes.pepper.whimsicalart.feature.gallery.domain.tag.PhotoTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneClassifier
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.TagRepository
import codes.pepper.whimsicalart.feature.gallery.domain.tag.TagTransformer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    val error: String? = null,
    val isMultiSelectMode: Boolean = false,
    val selectedPhotos: Set<Long> = emptySet(),
    val selectedFolder: String? = null,
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
    private val sceneClassifier: SceneClassifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        observeTags()
    }

    fun loadPhotos() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val photos = repository.getPhotos()
                val current = _uiState.value
                _uiState.value = current.copy(
                    photos = photos,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = GalleryUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load photos"
                )
            }
        }
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
        if (tag == null) return
        viewModelScope.launch {
            val uris = tagRepository.urisFor(tag)
            val subset = _uiState.value.photos.filter { uris.contains(it.uri.toString()) }
            _uiState.update { it.copy(selectedTag = tag, filteredPhotos = subset) }
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

    fun setFolder(folder: String?) {
        _uiState.value = _uiState.value.copy(selectedFolder = folder)
    }
}