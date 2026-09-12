package codes.pepper.whimsicalart.feature.gallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import codes.pepper.whimsicalart.feature.gallery.domain.GroupDimension
import codes.pepper.whimsicalart.feature.gallery.domain.GroupDirection
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.core.designsystem.ScaledTopBar
import codes.pepper.whimsicalart.core.designsystem.ScaledTopBarIconButton
import kotlinx.coroutines.launch
import codes.pepper.whimsicalart.feature.gallery.domain.Photo
import codes.pepper.whimsicalart.feature.gallery.domain.PhotoGroup
import codes.pepper.whimsicalart.feature.gallery.domain.SortDirection
import codes.pepper.whimsicalart.feature.gallery.domain.SortField
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
    val s = LocalUiScale.current

    var showSortGroupDialog by remember { mutableStateOf(false) }

    // The gallery top bar is a primary-coloured bar; its action TextButtons must
    // render onPrimary text (ButtonDefaults default content colour is primary, which
    // is invisible on a primary background). "Open (N)" and the dialog's "Done" live
    // on light surfaces and keep the default colours.
    val topBarTextButtonColors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
    )

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

    // Load ONLY on the first entry into composition with permission and no
    // cached list. The viewModel is scoped to the navigation backstack entry,
    // so returning from Settings/the editor recomposes this screen but the VM
    // still holds the cached list — reloading here unconditionally would throw
    // the grid away to the full-screen loader and re-run the MediaStore query +
    // thumbnail fetch on EVERY back navigation (the slow-return report). Only
    // onGalleryResumed() refreshes (the lifecycle hook below), and only when
    // the media store genuinely changed (mediaDirty). An empty list after a
    // failed load re-runs the query on a fresh composition (retry for free).
    LaunchedEffect(hasPermission, uiState.photos.isEmpty()) {
        if (hasPermission) {
            if (uiState.photos.isEmpty()) {
                viewModel.loadPhotos()
            }
        } else {
            permissionLauncher.launch(mediaPermission)
        }
    }

    // rememberSaveable — not plain remember() — so the flag survives the
    // Settings/editor round-trip: the navigation backstack keeps the gallery
    // entry's saved state alive while it is off the composition, and a
    // back-return must look like a REAL resume (run onGalleryResumed), not like
    // a brand-new screen whose "first resume" skips the mediaDirty refresh.
    var hasResumedOnce by rememberSaveable { mutableStateOf(false) }
    val currentHasPermission by rememberUpdatedState(hasPermission)
    LifecycleResumeEffect(Unit) {
        val isFirstResume = !hasResumedOnce
        hasResumedOnce = true
        if (currentHasPermission && !isFirstResume) {
            // Reuse the cached list: only a real media-store change (observer
            // fired since the last query) re-runs the MediaStore query, so an
            // immediate Settings/editor round-trip never rebuilds the grid.
            viewModel.onGalleryResumed()
        }
        onPauseOrDispose { }
    }

    // System back is NOT a nav pop here — the group drill-in is pure VM state
    // (openedGroup) and the gallery is the NavHost start destination, so an
    // unhandled back-press would bubble to the activity and exit the app.
    // Back with a group (or a multi-select in progress) consumes the gesture
    // instead: multi-select flips back to normal first (matching the top-bar
    // Cancel action), otherwise the drill-in closes and the grid returns.
    BackHandler(enabled = uiState.openedGroup != null || uiState.isMultiSelectMode) {
        if (uiState.isMultiSelectMode) {
            viewModel.clearSelection()
        } else {
            viewModel.closeGroup()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScaledTopBar(
            title = when {
                uiState.isMultiSelectMode -> {
                    "Select Photos (${uiState.selectedPhotos.size})"
                }
                uiState.openedGroup != null -> {
                    uiState.openedGroup.orEmpty()
                }
                else -> {
                    "Gallery"
                }
            },
            navigationIcon = {
                if (uiState.openedGroup != null && !uiState.isMultiSelectMode) {
                    ScaledTopBarIconButton(
                        onClick = { viewModel.closeGroup() },
                        contentDescription = "Back to gallery",
                        icon = Icons.AutoMirrored.Filled.ArrowBack
                    )
                }
            },
            actions = {
                if (uiState.isMultiSelectMode) {
                    TextButton(
                        colors = topBarTextButtonColors,
                        onClick = { viewModel.selectAllPhotos() }
                    ) {
                        Text(
                            text = "Select All",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    }
                    TextButton(
                        colors = topBarTextButtonColors,
                        onClick = { viewModel.clearSelection() }
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    }
                } else if (uiState.openedGroup == null) {
                    ScaledTopBarIconButton(
                        onClick = { showSortGroupDialog = true },
                        contentDescription = "Sort / Group",
                        icon = Icons.Filled.FolderOpen
                    )
                    onOpenCamera?.let {
                        ScaledTopBarIconButton(
                            onClick = it,
                            contentDescription = "Camera",
                            icon = Icons.Filled.PhotoCamera
                        )
                    }
                    ScaledTopBarIconButton(
                        enabled = !uiState.isTagging && uiState.photos.isNotEmpty(),
                        onClick = {
                            val targets = uiState.filteredPhotos ?: uiState.photos
                            targets.forEach { viewModel.tagPhoto(it) }
                        },
                        contentDescription = if (uiState.isTagging) "Tagging" else "Tag all photos",
                        icon = Icons.Filled.Tag
                    )
                    onOpenSettings?.let {
                        ScaledTopBarIconButton(
                            onClick = it,
                            contentDescription = "Settings",
                            icon = Icons.Filled.Settings
                        )
                    }
                } else {
                    ScaledTopBarIconButton(
                        enabled = !uiState.isTagging && uiState.photos.isNotEmpty(),
                        onClick = {
                            val targets = uiState.filteredPhotos ?: uiState.photos
                            targets.forEach { viewModel.tagPhoto(it) }
                        },
                        contentDescription = if (uiState.isTagging) "Tagging" else "Tag all photos",
                        icon = Icons.Filled.Tag
                    )
                }
            }
        )

        TagFilterRow(
            availableTags = uiState.availableTags,
            selectedTag = uiState.selectedTag,
            onTagSelected = { viewModel.setSelectedTag(it) }
        )

        if (showSortGroupDialog) {
            ViewingOptionsDialog(
                sortField = uiState.sortField,
                sortDirection = uiState.sortDirection,
                groupDimension = uiState.groupDimension,
                groupDirection = uiState.groupDirection,
                onSortField = viewModel::setSortField,
                onSortDirection = viewModel::setSortDirection,
                onGroupDimension = viewModel::setGroupDimension,
                onGroupDirection = viewModel::setGroupDirection,
                onDismiss = { showSortGroupDialog = false }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !hasPermission -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp * s),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Photo access is required to show your gallery.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * s
                            ),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { permissionLauncher.launch(mediaPermission) },
                            modifier = Modifier.padding(top = 16.dp * s)
                        ) {
                            Text(
                                "Grant Permission",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                                )
                            )
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
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * s
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.photos.isEmpty() -> {
                    Text(
                        text = "No photos found",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * s
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    GalleryRefreshContainer(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refreshPhotos() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.openedGroup != null) {
                            val group = uiState.groups.firstOrNull {
                                it.label == uiState.openedGroup
                            }
                            if (group != null) {
                                PhotoGrid(
                                    photos = group.photos,
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
                        } else if (uiState.groups.isNotEmpty()) {
                            GroupGrid(groups = uiState.groups) { group ->
                                viewModel.openGroup(group.label)
                            }
                        } else {
                            PhotoGrid(
                                photos = uiState.displayedPhotos,
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
            }
        }

        if (uiState.isMultiSelectMode && uiState.selectedPhotos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp * s),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    val selectedUris = viewModel.getSelectedPhotos().map { it.uri }
                    onPhotosSelected(selectedUris)
                    viewModel.clearSelection()
                }) {
                    Text(
                        "Open (${uiState.selectedPhotos.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                        )
                    )
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
    val s = LocalUiScale.current
    val gap = 4.dp * s
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupGrid(
    groups: List<PhotoGroup>,
    onGroupClick: (PhotoGroup) -> Unit
) {
    val s = LocalUiScale.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp * s),
        horizontalArrangement = Arrangement.spacedBy(4.dp * s),
        verticalArrangement = Arrangement.spacedBy(8.dp * s),
        modifier = Modifier.fillMaxSize()
    ) {
        items(groups, key = { it.label }) { group ->
            GroupTile(group = group, onClick = { onGroupClick(group) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTile(
    group: PhotoGroup,
    onClick: () -> Unit
) {
    val s = LocalUiScale.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
    ) {
        AsyncImage(
            model = group.firstPhoto.uri,
            contentDescription = group.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Text(
            text = group.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * s
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp * s, vertical = 2.dp * s)
        )
    }
}

@Composable
private fun TextButton(
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.TextButton(colors = colors, onClick = onClick) {
        content()
    }
}

/**
 * Sort / Group panel: two sections — sort by (field + direction) and group by
 * (dimension + direction). The group direction row is visible but inert while
 * the dimension is None. Every selection applies and persists immediately.
 */
@Composable
private fun ViewingOptionsDialog(
    sortField: SortField,
    sortDirection: SortDirection,
    groupDimension: GroupDimension,
    groupDirection: GroupDirection,
    onSortField: (SortField) -> Unit,
    onSortDirection: (SortDirection) -> Unit,
    onGroupDimension: (GroupDimension) -> Unit,
    onGroupDirection: (GroupDirection) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalUiScale.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sort / Group",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * s
                )
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = MaterialTheme.typography.titleSmall.fontSize * s
                    )
                )
                SortField.entries.forEach { field ->
                    SelectableChipRow(
                        label = field.label,
                        selected = field == sortField,
                        onClick = { onSortField(field) }
                    )
                }
                DirectionRow(
                    ascending = sortDirection == SortDirection.ASC,
                    onAscending = { onSortDirection(SortDirection.ASC) },
                    onDescending = { onSortDirection(SortDirection.DESC) }
                )

                Spacer(modifier = Modifier.height(16.dp * s))

                Text(
                    text = "Group by",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = MaterialTheme.typography.titleSmall.fontSize * s
                    )
                )
                GroupDimension.entries.forEach { dimension ->
                    SelectableChipRow(
                        label = dimension.label,
                        selected = dimension == groupDimension,
                        onClick = { onGroupDimension(dimension) }
                    )
                }
                DirectionRow(
                    ascending = groupDirection == GroupDirection.ASC,
                    onAscending = { onGroupDirection(GroupDirection.ASC) },
                    onDescending = { onGroupDirection(GroupDirection.DESC) },
                    enabled = groupDimension != GroupDimension.NONE
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            }
        }
    )
}

@Composable
private fun SelectableChipRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val s = LocalUiScale.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                )
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn((32.dp * s).coerceAtLeast(28.dp))
            .padding(vertical = 2.dp * s)
    )
}

@Composable
private fun DirectionRow(
    ascending: Boolean,
    onAscending: () -> Unit,
    onDescending: () -> Unit,
    enabled: Boolean = true
) {
    val s = LocalUiScale.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp * s)) {
        FilterChip(
            selected = ascending,
            onClick = onAscending,
            enabled = enabled,
            label = {
                Text(
                    text = "Ascending",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            },
            modifier = Modifier.heightIn((32.dp * s).coerceAtLeast(28.dp))
        )
        FilterChip(
            selected = !ascending,
            onClick = onDescending,
            enabled = enabled,
            label = {
                Text(
                    text = "Descending",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            },
            modifier = Modifier.heightIn((32.dp * s).coerceAtLeast(28.dp))
        )
    }
}

private val SortField.label: String
    get() = when (this) {
        SortField.NAME -> "Name"
        SortField.PATH -> "Path"
        SortField.SIZE -> "Size"
        SortField.LAST_MODIFIED -> "Last modified"
        SortField.DATE_TAKEN -> "Date taken"
    }

private val GroupDimension.label: String
    get() = when (this) {
        GroupDimension.NONE -> "None"
        GroupDimension.FOLDER -> "Folder"
        GroupDimension.FILE_TYPE -> "File type"
        GroupDimension.EXTENSION -> "Extension"
        GroupDimension.MODIFIED_DAILY -> "Last modified (daily)"
        GroupDimension.MODIFIED_MONTHLY -> "Last modified (monthly)"
        GroupDimension.TAKEN_DAILY -> "Date taken (daily)"
        GroupDimension.TAKEN_MONTHLY -> "Date taken (monthly)"
    }

/** A horizontal row of toggleable scene-tag filter chips. */
@Composable
private fun TagFilterRow(
    availableTags: List<SceneTag>,
    selectedTag: SceneTag?,
    onTagSelected: (SceneTag?) -> Unit
) {
    val s = LocalUiScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp * s, vertical = 4.dp * s),
        horizontalArrangement = Arrangement.spacedBy(8.dp * s)
    ) {
        if (selectedTag != null) {
            FilterChip(
                selected = false,
                onClick = { onTagSelected(null) },
                label = {
                    Text(
                        text = "All",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                        )
                    )
                },
                modifier = Modifier.heightIn((32.dp * s).coerceAtLeast(28.dp)),
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
                label = {
                    Text(
                        text = tag.displayName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                        )
                    )
                },
                modifier = Modifier.heightIn((32.dp * s).coerceAtLeast(28.dp)),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

// Exposes the indicator's live opacity to tests (read back from the semantics
// node, not from pixels). Only published when indicatorTestTag is set.
internal val IndicatorAlphaKey = SemanticsPropertyKey<Float>("indicatorAlpha")

/**
 * Pull-to-refresh container for the [PhotoGrid]. Replaces material3's opaque
 * [androidx.compose.material3.pulltorefresh.PullToRefreshBox] so the app owns
 * the indicator's lifetime. material3 1.3.1's boxed indicator can fail its
 * detach/fade-out and linger after `isRefreshing` drops back to false — notably
 * when the true→false flip lands in a single frame, which Compose coalesces into
 * only observing `false`. We therefore run the indicator through an app-owned
 * `Animatable` and remove it from the composition only after our own exit fade
 * has finished, betting on nothing inside material3.
 *
 * Visual lifecycle (standard pull-to-refresh feedback):
 *  - while the finger drags the grid down the indicator is composed (it never
 *    awaited `isRefreshing`, which only flips once `refreshPhotos()` runs) and
 *    follows the pull via `PullToRefreshState.distanceFraction` (the
 *    [PullToRefreshDefaults.Indicator] draws the pull progress ring itself);
 *  - past the threshold material3 fires [onRefresh], `isRefreshing` goes true
 *    and the indicator becomes the spinning progress icon for the re-read;
 *  - on completion the indicator is NOT hard-removed: it fades/scales out and
 *    only then leaves the composition.
 *
 * Owner model (single owner for the exit — the `81e026d` regression ran a
 * second, app-owned `animateToHidden()` that raced material3's internal reset
 * through the state's `MutatorMutex`):
 *  - the pull STATE is reset by material3 only: `Modifier.pullToRefresh` carries
 *    its own `LaunchedEffect(isRefreshing)` that calls `animateToHidden()` when
 *    the flag drops false, so the app never calls it (a second concurrent reset
 *    on the same backing `Animatable` would cancel the first).
 *  - the exit fade is driven PURELY off `isRefreshing`, never off
 *    `distanceFraction`: the moment a completed refresh is observed (even as the
 *    only observation of a coalesced true→false frame) our fade takes over and
 *    reaches 0, so the indicator cannot linger.
 *  - when the fade ends the app pins the state to rest with `snapTo(0f)` so
 *    nothing is left to resurrect the icon: material3 1.3.1's state is a single
 *    `Animatable` whose own `animateToHidden()` settles to exactly 0 and our
 *    snap cancels it to rest, so after an exit `distanceFraction` is
 *    deterministically 0 and the next pull's feedback reappears normally.
 *
 * Feedback is transiently guarded while `exitFadeRunning` is up (a settling
 * `distanceFraction` tick must not fight the fade), and the icon only ever
 * re-appears when the user starts a real pull again. Gesture wiring is
 * identical to `PullToRefreshBox`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    pullState: PullToRefreshState = rememberPullToRefreshState(),
    indicatorTestTag: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val distanceFraction = pullState.distanceFraction

    val indicatorAlpha = remember { Animatable(0f) }
    var exitFadeRunning by remember { mutableStateOf(false) }

    // Completion exit — the only owner of the fade. Runs whenever isRefreshing
    // reads false while the icon is up, so a coalesced true→false frame still
    // triggers it exactly once (the d186ec6 guard). The pull STATE's own reset
    // is left to material3's modifier; here we only fade our Animatable and then
    // pin the state to rest, so no residual distanceFraction is left behind.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            exitFadeRunning = false
            indicatorAlpha.snapTo(1f)
        } else if (indicatorAlpha.value > 0f) {
            exitFadeRunning = true
            indicatorAlpha.animateTo(0f, tween(durationMillis = 220))
            pullState.snapTo(0f)
            exitFadeRunning = false
        }
    }

    // Live in-gesture feedback (before the threshold / without a refresh): the
    // indicator appears as the pull starts and follows distanceFraction. Keyed on
    // exitFadeRunning so the fade→clear transition can't re-run this effect with
    // a still-settling distanceFraction. No persistent post-exit latch: material3
    // 1.3.1's PullToRefreshState is a single Animatable whose animateToHidden
    // settles to exactly 0, and the exit above force-snaps it to 0, so after a
    // completed exit distanceFraction is deterministically at rest — the next
    // real pull can and must show feedback again.
    LaunchedEffect(distanceFraction, isRefreshing, exitFadeRunning) {
        if (isRefreshing || exitFadeRunning) return@LaunchedEffect
        indicatorAlpha.snapTo(if (distanceFraction > 0f) 1f else 0f)
    }

    Box(
        modifier = modifier.pullToRefresh(
            state = pullState,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh
        )
    ) {
        content()
        if (indicatorAlpha.value > 0f || isRefreshing) {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val progress = indicatorAlpha.value
                        alpha = progress
                        scaleX = progress
                        scaleY = progress
                    }
                    .then(
                        if (indicatorTestTag != null) {
                            Modifier
                                .semantics { this[IndicatorAlphaKey] = indicatorAlpha.value }
                                .testTag(indicatorTestTag)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}
