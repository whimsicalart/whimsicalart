package codes.pepper.whimsicalart.feature.editor.ui.layers

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.feature.editor.domain.MergedEffect
import codes.pepper.whimsicalart.feature.editor.domain.FilterEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyStackEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyAutoEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautySmoothingEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyTeethEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyEyeBrightenEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyDarkCircleEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautySpotEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyWrinkleEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautySkinToneEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautySlimEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyEyeEnlargeEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyNoseEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyJawEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyLipstickEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyBlushEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyEyeShadowEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyEyelinerEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyFoundationEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyHairEffect
import codes.pepper.whimsicalart.feature.editor.domain.BeautyPenEffect
import codes.pepper.whimsicalart.feature.editor.domain.StackEffect
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import kotlin.math.roundToInt

/**
 * Full-screen Layers page. Lists every applied effect as a reversible layer:
 * a thumbnail on the left, a remove action on the right (with confirmation),
 * tap to jump to that effect's tool, and drag a layer up/down (long-press, then
 * drag) to reorder the stack. A merged layer is special — it cannot be
 * redirected. The top bar has a Back button that returns to the last tool and a
 * "Merge Layers" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersPanel(
    effects: List<StackEffect>,
    thumbnails: Map<String, Bitmap?>,
    hasMerged: Boolean,
    onSelect: (StackEffect) -> Unit,
    onRemove: (StackEffect) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onMerge: () -> Unit,
    onBack: () -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<StackEffect?>(null) }
    // Long-press-drag reorder state: which row is being dragged and its current
    // vertical translation in pixels.
    var dragFrom by remember { mutableStateOf(-1) }
    var dragDelta by remember { mutableStateOf(0f) }
    // Nominal row height (thumbnail 96dp + 8dp top/bottom padding) used to turn
    // a drag delta into a target index.
    val rowHeightPx = with(LocalDensity.current) { 112.dp.toPx() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Layers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onMerge) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Merge Layers")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (effects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasMerged) "Layers merged." else "No effects applied yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(effects, key = { _, effect -> effect.layerKey }) { index, effect ->
                    LayerRow(
                        effect = effect,
                        thumbnail = thumbnails[effect.layerKey],
                        index = index,
                        dragFrom = dragFrom,
                        dragDelta = dragDelta,
                        onDragStart = { dragFrom = index; dragDelta = 0f },
                        onDrag = { delta ->
                            dragDelta += delta
                        },
                        onDragEnd = {
                            if (dragFrom != -1) {
                                val toIndex = (dragFrom + (dragDelta / rowHeightPx).roundToInt())
                                    .coerceIn(0, effects.lastIndex)
                                // Only fire if the order actually changed.
                                if (toIndex != dragFrom) onReorder(dragFrom, toIndex)
                            }
                            dragFrom = -1
                            dragDelta = 0f
                        },
                        onSelect = { onSelect(effect) },
                        onRemove = { pendingRemoval = effect }
                    )
                }
            }

            if (pendingRemoval != null) {
                val target = pendingRemoval!!
                AlertDialog(
                    onDismissRequest = { pendingRemoval = null },
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    title = { Text("Remove \u201C${labelFor(target)}\u201D?") },
                    text = { Text("This effect will be removed from the photo.") },
                    confirmButton = {
                        TextButton(onClick = {
                            onRemove(target)
                            pendingRemoval = null
                        }) {
                            Text("Remove")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingRemoval = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LayerRow(
    effect: StackEffect,
    thumbnail: Bitmap?,
    index: Int,
    dragFrom: Int,
    dragDelta: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val isMerged = effect is MergedEffect
    val isDragging = index == dragFrom
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Long-press + drag reorders the layer stack (drag handle/track only).
            .graphicsLayer {
                if (isDragging) translationY = dragDelta
            }
            .pointerInput(index, isMerged, dragFrom) {
                if (isMerged) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .clickable(enabled = !isMerged && !isDragging, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle on the far left (only while reordering matters).
        Column(modifier = Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!isMerged) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Thumbnail.
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbnail
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = labelFor(effect),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(3.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Label in the middle.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = labelFor(effect),
                style = MaterialTheme.typography.bodyLarge,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isMerged) "Merged (not editable)" else detailFor(effect),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Remove action on the right.
        IconButton(onClick = onRemove, enabled = !isMerged) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove layer",
                tint = if (isMerged) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun labelFor(effect: StackEffect): String = when (effect) {
    is MergedEffect -> "Merged"
    is FilterEffect -> "Filter"
    is BeautyStackEffect -> "Beauty \u00B7 ${beautyLabel(effect)}"
    else -> effect.tool.label()
}

private fun beautyLabel(effect: BeautyStackEffect): String = when (effect) {
    is BeautyAutoEffect -> "Auto Beautify"
    is BeautySmoothingEffect -> "Smoothing"
    is BeautyTeethEffect -> "Teeth"
    is BeautyEyeBrightenEffect -> "Eye Brightening"
    is BeautyDarkCircleEffect -> "Dark Circles"
    is BeautySpotEffect -> "Spots"
    is BeautyWrinkleEffect -> "Wrinkles"
    is BeautySkinToneEffect -> "Skin Tone"
    is BeautySlimEffect -> "Slim"
    is BeautyEyeEnlargeEffect -> "Eye Enlarge"
    is BeautyNoseEffect -> "Nose"
    is BeautyJawEffect -> "Jaw"
    is BeautyLipstickEffect -> "Lipstick"
    is BeautyBlushEffect -> "Blush"
    is BeautyEyeShadowEffect -> "Eye Shadow"
    is BeautyEyelinerEffect -> "Eyeliner"
    is BeautyFoundationEffect -> "Foundation"
    is BeautyHairEffect -> "Hair Color"
    is BeautyPenEffect -> "Pen"
    else -> "Beauty"
}

private fun detailFor(effect: StackEffect): String = when (effect) {
    is MergedEffect -> "All layers combined into one layer"
    is BeautyStackEffect -> "Tap to edit ${beautyLabel(effect).lowercase()}"
    else -> "Tap to edit ${effect.tool.label().lowercase()}"
}

private fun EditTool.label(): String = when (this) {
    EditTool.BRIGHTNESS -> "Brightness"
    EditTool.CONTRAST -> "Contrast"
    EditTool.SATURATION -> "Saturation"
    EditTool.SHARPEN -> "Sharpness"
    EditTool.EXPOSURE -> "Exposure"
    EditTool.SHADOWS -> "Shadows"
    EditTool.HIGHLIGHTS -> "Highlights"
    EditTool.TEMPERATURE -> "Warmth"
    EditTool.TINT -> "Tint"
    EditTool.VIGNETTE -> "Vignette"
    EditTool.ENHANCE -> "Auto-Enhance"
    EditTool.FILTERS -> "Filters"
    EditTool.BACKGROUND -> "Background"
    EditTool.MOSAIC -> "Mosaic"
    EditTool.BLUR_BRUSH -> "Blur Brush"
    EditTool.PEN -> "Pen"
    EditTool.OBJECT_REMOVAL -> "Object Removal"
    EditTool.STICKERS -> "Stickers"
    EditTool.TEXT -> "Text"
    EditTool.FRAMES -> "Frames"
    EditTool.CROP -> "Crop"
    EditTool.TRANSFORM -> "Transform"
    EditTool.SKIN_DENOISE -> "Skin Denoise"
    EditTool.BEAUTY -> "Beauty"
}
