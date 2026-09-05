package codes.pepper.whimsicalart.feature.gallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import codes.pepper.whimsicalart.feature.gallery.domain.Photo
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneTag
import codes.pepper.whimsicalart.feature.gallery.ui.viewmodel.GalleryViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onPhotoSelected: (Uri) -> Unit,
    onPhotosSelected: (List<Uri>) -> Unit,
    onOpenCamera: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.loadPhotos()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadPhotos()
        } else {
            permissionLauncher.launch(mediaPermission)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (uiState.isMultiSelectMode) {
                    Text("Select Photos (${uiState.selectedPhotos.size})")
                } else {
                    Text("Gallery")
                }
            },
            actions = {
                if (uiState.isMultiSelectMode) {
                    TextButton(onClick = { viewModel.selectAllPhotos() }) {
                        Text("Select All")
                    }
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text("Cancel")
                    }
                } else {
                    onOpenCamera?.let {
                        TextButton(onClick = it) { Text("Camera") }
                    }
                    androidx.compose.material3.TextButton(
                        enabled = !uiState.isTagging && uiState.photos.isNotEmpty(),
                        onClick = {
                            val targets = uiState.filteredPhotos ?: uiState.photos
                            targets.forEach { viewModel.tagPhoto(it) }
                        }
                    ) {
                        Text(if (uiState.isTagging) "Tagging…" else "Tag All")
                    }
                    onOpenSettings?.let {
                        TextButton(onClick = it) { Text("Settings") }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        TagFilterRow(
            availableTags = uiState.availableTags,
            selectedTag = uiState.selectedTag,
            onTagSelected = { viewModel.setSelectedTag(it) }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !hasPermission -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Photo access is required to show your gallery.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { permissionLauncher.launch(mediaPermission) },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.photos.isEmpty() -> {
                    Text(
                        text = "No photos found",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    val visible = uiState.filteredPhotos ?: uiState.photos
                    PhotoGrid(
                        photos = visible,
                        selectedPhotos = uiState.selectedPhotos,
                        isMultiSelectMode = uiState.isMultiSelectMode,
                        onPhotoClick = { photo ->
                            if (uiState.isMultiSelectMode) {
                                viewModel.togglePhotoSelection(photo.id)
                            } else {
                                onPhotoSelected(photo.uri)
                            }
                        },
                        onPhotoLongClick = { photo ->
                            if (!uiState.isMultiSelectMode) {
                                viewModel.toggleMultiSelectMode()
                                viewModel.togglePhotoSelection(photo.id)
                            }
                        }
                    )
                }
            }
        }

        if (uiState.isMultiSelectMode && uiState.selectedPhotos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    val selectedUris = viewModel.getSelectedPhotos().map { it.uri }
                    onPhotosSelected(selectedUris)
                    viewModel.clearSelection()
                }) {
                    Text("Open (${uiState.selectedPhotos.size})")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    photos: List<Photo>,
    selectedPhotos: Set<Long>,
    isMultiSelectMode: Boolean,
    onPhotoClick: (Photo) -> Unit,
    onPhotoLongClick: (Photo) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoItem(
                photo = photo,
                isSelected = selectedPhotos.contains(photo.id),
                isMultiSelectMode = isMultiSelectMode,
                onClick = { onPhotoClick(photo) },
                onLongClick = { onPhotoLongClick(photo) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoItem(
    photo: Photo,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        )

        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        content()
    }
}

/** A horizontal row of toggleable scene-tag filter chips. */
@Composable
private fun TagFilterRow(
    availableTags: List<SceneTag>,
    selectedTag: SceneTag?,
    onTagSelected: (SceneTag?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (selectedTag != null) {
            FilterChip(
                selected = false,
                onClick = { onTagSelected(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        availableTags.forEach { tag ->
            FilterChip(
                selected = selectedTag == tag,
                onClick = { onTagSelected(if (selectedTag == tag) null else tag) },
                label = { Text(tag.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}
