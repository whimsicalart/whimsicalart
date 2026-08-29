package com.whimsicalart.feature.gallery.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whimsicalart.feature.gallery.domain.GalleryRepository
import com.whimsicalart.feature.gallery.domain.Photo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val photos: List<Photo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isMultiSelectMode: Boolean = false,
    val selectedPhotos: Set<Long> = emptySet(),
    val selectedFolder: String? = null
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: GalleryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun loadPhotos() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val photos = repository.getPhotos()
                _uiState.value = GalleryUiState(
                    photos = photos,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = GalleryUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load photos"
                )
            }
        }
    }

    fun toggleMultiSelectMode() {
        val isCurrentlyMultiSelect = _uiState.value.isMultiSelectMode
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = !isCurrentlyMultiSelect,
            selectedPhotos = if (isCurrentlyMultiSelect) emptySet() else _uiState.value.selectedPhotos
        )
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
