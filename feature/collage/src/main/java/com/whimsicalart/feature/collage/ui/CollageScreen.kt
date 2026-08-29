package com.whimsicalart.feature.collage.ui

import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.whimsicalart.feature.collage.domain.CollageCell
import com.whimsicalart.feature.collage.domain.CollageTemplates
import com.whimsicalart.feature.collage.ui.viewmodel.CollageViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    initialPhotos: List<Uri>,
    onBack: () -> Unit,
    viewModel: CollageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialPhotos) {
        if (initialPhotos.isNotEmpty()) {
            viewModel.setInitialPhotos(initialPhotos)
        }
    }

    var pickingSlot by remember { mutableStateOf<Int?>(null) }
    var pickingTemplateSlot by remember { mutableStateOf<Int?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        pickingSlot?.let { slot ->
            viewModel.assignPhoto(slot, uris.firstOrNull())
            pickingSlot = null
        }
        pickingTemplateSlot?.let { templateSlot ->
            val template = CollageTemplates.all[templateSlot]
            val slotUris = uris.take(template.photoCount)
            viewModel.selectTemplate(template)
            slotUris.forEachIndexed { i, uri -> viewModel.assignPhoto(i, uri) }
            pickingTemplateSlot = null
        }
    }

    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        pickingSlot?.let { slot ->
            viewModel.assignPhoto(slot, uri)
            pickingSlot = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Collage Maker") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CollagePreview(
                slotUris = uiState.slots,
                templateCells = uiState.template.cells,
                slotRects = uiState.slotRects,
                isFreeForm = uiState.isFreeForm,
                borderWidthRatio = uiState.borderWidth,
                borderColor = uiState.borderColor,
                backgroundColor = uiState.backgroundColor,
                onCellClick = { slot ->
                    pickingSlot = slot
                    singlePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onMoveSlot = { slot, dx, dy ->
                    viewModel.translateSlot(slot, dx, dy)
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Placement", modifier = Modifier.padding(end = 12.dp))
                FilterChip(
                    selected = uiState.isFreeForm,
                    onClick = { viewModel.toggleFreeForm() },
                    label = { Text("Free form") }
                )
            }

            Text("Layout", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(CollageTemplates.all) { index, template ->
                    TemplateChip(
                        selected = uiState.template.id == template.id,
                        name = template.name,
                        photoCount = template.photoCount,
                        onClick = {
                            viewModel.selectTemplate(template)
                            pickingTemplateSlot = index
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Spacing", modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = uiState.borderWidth,
                    onValueChange = { viewModel.updateBorderWidth(it) },
                    valueRange = 0f..40f,
                    modifier = Modifier.weight(1f)
                )
                Text("${uiState.borderWidth.toInt()}px")
            }

            ColorRow(
                label = "Border",
                colors = ColorOptions.borders,
                selected = uiState.borderColor,
                onSelect = { viewModel.updateBorderColor(it) }
            )
            ColorRow(
                label = "Background",
                colors = ColorOptions.backgrounds,
                selected = uiState.backgroundColor,
                onSelect = { viewModel.updateBackgroundColor(it) }
            )

            Button(
                onClick = {
                    viewModel.saveCollage(context.contentResolver) { saved ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (saved) "Collage saved" else "Save failed"
                            )
                        }
                    }
                },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (uiState.isSaving) "Saving..." else "Save Collage")
            }
        }
    }

    SnackbarHost(hostState = snackbarHostState)
}

object ColorOptions {
    val borders = listOf(
        Color.White,
        Color.Black,
        Color(0xFFCFD8DC),
        Color(0xFFFFE082),
        Color(0xFFEF9A9A),
        Color(0xFFCE93D8)
    )
    val backgrounds = listOf(
        Color(0xFFECEFF1),
        Color.White,
        Color(0xFF263238),
        Color(0xFFFFF3E0),
        Color(0xFFE1BEE7)
    )
}

@Composable
private fun CollagePreview(
    slotUris: List<Uri?>,
    templateCells: List<CollageCell>,
    slotRects: Map<Int, RectF>,
    isFreeForm: Boolean,
    borderWidthRatio: Float,
    borderColor: Color,
    backgroundColor: Color,
    onCellClick: (Int) -> Unit,
    onMoveSlot: (Int, Float, Float) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .aspectRatio(1f)
    ) {
        val gap = maxOf(borderWidthRatio, 1f).dp
        templateCells.forEachIndexed { index, cell ->
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val rect = if (isFreeForm) (slotRects[index] ?: cell.toRectF()) else cell.toRectF()
            val inset = gap.value
            val left = rect.left * width + inset
            val top = rect.top * height + inset
            val right = rect.right * width - inset
            val bottom = rect.bottom * height - inset
            val cellWidth = (right - left).coerceAtLeast(1f)
            val cellHeight = (bottom - top).coerceAtLeast(1f)
            val uri = slotUris.getOrNull(index)

            Box(
                modifier = Modifier
                    .offset { IntOffset(left.toInt(), top.toInt()) }
                    .size(cellWidth.dp, cellHeight.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .pointerInput(index) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMoveSlot(index, dragAmount.x / width, dragAmount.y / height)
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCellClick(index) }
            ) {
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF90A4AE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add photo",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateChip(
    selected: Boolean,
    name: String,
    photoCount: Int,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name)
                Text("$photoCount", style = MaterialTheme.typography.labelSmall)
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun ColorRow(
    label: String,
    colors: List<Color>,
    selected: Color,
    onSelect: (Color) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.padding(end = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEach { color ->
                val isSelected = selected == color
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(color) }
                )
            }
        }
    }
}
