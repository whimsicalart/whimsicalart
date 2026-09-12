package codes.pepper.whimsicalart.feature.editor.ui.layers

import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.core.designsystem.ScaledTopBar

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Full-screen Layers page. Lists every applied effect as a reversible layer:
 * a thumbnail on the left, a remove action on the right (with confirmation),
 * tap to jump to that effect's tool, and drag a layer by its **move icon**
 * (immediate drag, no long press) up/down to reorder the stack; pushing the
 * dragged row into the top/bottom edge auto-scrolls the list while more rows
 * exist in that direction. A merged layer is special — it cannot be redirected. The top bar has a Back button that
 * returns to the last tool and a "Merge Layers" action.
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
    // Reorder drag state: which row is being dragged, its current vertical
    // translation in pixels, and the LIVE insertion slot (the list index the
    // dragged row will land at, recomputed on every drag event).
    var dragFrom by remember { mutableStateOf(-1) }
    var dragDelta by remember { mutableStateOf(0f) }
    var dropSlot by remember { mutableStateOf(-1) }
    val listState = rememberLazyListState()
    val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }
    val edgeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    val autoScrollStepPx = with(LocalDensity.current) { 40.dp.toPx() }

    // Computes the current drop-slot index from the dragged row's visual centre
    // (original geometry + finger delta) against the row grid — the midpoint of
    // a row sits half a row above/below its neighbours, so a drop lands in the
    // visible gap between two rows (spec: "Reorder drag feedback"). Evaluated
    // per gesture event so the slot tracks the drop target while the list
    // auto-scrolls.
    fun computeDropSlot(delta: Float): Int {
        val layout = listState.layoutInfo
        val dragged = layout.visibleItemsInfo.firstOrNull { it.index == dragFrom } ?: return dragFrom
        val step = dragged.size + spacingPx
        val centerY = dragged.offset + dragged.size / 2f + delta
        return (centerY / step).roundToInt().coerceIn(0, effects.lastIndex)
    }

    // While a reorder drag is in flight, nudge the list when the dragged row is
    // pushed into the top/bottom edge zone — but only when more items exist in
    // that direction (otherwise the row is simply clamped at the list end).
    LaunchedEffect(dragFrom) {
        if (dragFrom == -1) return@LaunchedEffect
        while (true) {
            val layout = listState.layoutInfo
            val dragged = layout.visibleItemsInfo.firstOrNull { it.index == dragFrom }
            if (dragged != null) {
                val viewportHeight = layout.viewportEndOffset - layout.viewportStartOffset
                val screenTop = dragged.offset + dragDelta
                val screenBottom = screenTop + dragged.size
                if (screenTop < edgeThresholdPx) {
                    val scrollableUp = layout.visibleItemsInfo.first().index > 0 ||
                        layout.visibleItemsInfo.first().offset > 0
                    if (scrollableUp) listState.scroll { scrollBy(-autoScrollStepPx) }
                } else if (screenBottom > viewportHeight - edgeThresholdPx) {
                    val scrollableDown = layout.visibleItemsInfo.last().index < effects.lastIndex
                    if (scrollableDown) listState.scroll { scrollBy(autoScrollStepPx) }
                }
            }
            delay(50)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            val ls = LocalUiScale.current
            ScaledTopBar(
                title = "Layers",
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size((48.dp * ls).coerceAtLeast(40.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp * ls)
                        )
                    }
                },
                actions = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        ),
                        onClick = onMerge
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp * ls)
                        )
                        Spacer(modifier = Modifier.width(4.dp * ls))
                        Text(
                            text = "Merge Layers",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * ls
                            )
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val s = LocalUiScale.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp * s)
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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                ) {
                val dragging = dragFrom != -1
                val density = LocalDensity.current
                val stepPx = if (dragging) {
                    val dragged = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == dragFrom }
                    (dragged?.size?.toFloat() ?: 0f) + spacingPx
                } else {
                    0f
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp * LocalUiScale.current),
                    // While a reorder drag is in flight the list must not scroll in
                    // its place — the move icon owns the gesture; edge auto-scroll is
                    // driven programmatically (see LaunchedEffect above).
                    userScrollEnabled = !dragging
                ) {
                    itemsIndexed(effects, key = { _, effect -> effect.layerKey }) { index, effect ->
                        // Where this row must shift (whole step) to leave the drop
                        // slot visible — the middle rows between the dragged row's
                        // original slot and the target advance one slot so the gap
                        // never sits beneath the dragged ghost.
                        val shiftPx = when {
                            !dragging || stepPx <= 0f -> 0f
                            dropSlot > dragFrom && index in (dragFrom + 1)..dropSlot -> -stepPx
                            dropSlot < dragFrom && index in dropSlot until dragFrom -> stepPx
                            else -> 0f
                        }
                        val isDraggingRow = index == dragFrom && dragging
                        // Rows slide to/from their shifted slots with a short
                        // tween; the dragged row follows the finger directly.
                        val targetShift = if (isDraggingRow) dragDelta else shiftPx
                        val animatedShift by animateFloatAsState(
                            targetValue = targetShift,
                            animationSpec = tween(durationMillis = 140),
                            label = "rowShift"
                        )
                        LayerRow(
                            effect = effect,
                            thumbnail = thumbnails[effect.layerKey],
                            index = index,
                            isDragging = isDraggingRow,
                            dragTranslationY = if (isDraggingRow) dragDelta else animatedShift,
                            onDragStart = {
                                dragFrom = index
                                dragDelta = 0f
                                dropSlot = index
                            },
                            onDrag = { delta ->
                                dragDelta += delta
                                dropSlot = computeDropSlot(dragDelta)
                            },
                            onDragEnd = {
                                // Drop into the last tracked slot (the live one —
                                // always in sync with the rendered feedback).
                                if (dragFrom != -1 && dropSlot != dragFrom) {
                                    onReorder(dragFrom, dropSlot)
                                }
                                dragFrom = -1
                                dragDelta = 0f
                                dropSlot = -1
                            },
                            onDragCancel = {
                                // Drag cancelled — restore the original order; no
                                // reorder is fired.
                                dragFrom = -1
                                dragDelta = 0f
                                dropSlot = -1
                            },
                            onSelect = { onSelect(effect) },
                            onRemove = { pendingRemoval = effect }
                        )
                    }
                }

                // The drop slot band sits exactly where the dragged row will
                // land, drawn ON TOP of the list so it is always visible
                // (never hidden beneath the dragged ghost). Offsets come from
                // the layout grid (rows are uniform), so it keeps tracking the
                // right gap while the list auto-scrolls; hidden while the
                // target row is outside the viewport.
                if (dragging && dropSlot != -1) {
                    val layout = listState.layoutInfo
                    val slotRow = layout.visibleItemsInfo.firstOrNull { it.index == dropSlot }
                    if (slotRow != null) {
                        val bandOffsetY = with(density) { slotRow.offset.toDp() }
                        val bandHeight = with(density) { slotRow.size.toDp() }
                        // Read the theme colour in composable context (drawBehind
                        // lambdas are not composable).
                        val accent = MaterialTheme.colorScheme.primary
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = bandOffsetY)
                                .height(bandHeight)
                                .padding(horizontal = 2.dp)
                                .drawBehind {
                                    drawRoundRect(
                                        color = accent.copy(alpha = 0.10f),
                                        size = Size(size.width, size.height)
                                    )
                                    val inset = 2.dp.toPx()
                                    drawRoundRect(
                                        color = accent.copy(alpha = 0.55f),
                                        topLeft = Offset(inset, inset),
                                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(14f, 10f)
                                            )
                                        )
                                    )
                                }
                        )
                    }
                }
            }

            if (pendingRemoval != null) {
                val target = pendingRemoval!!
                val ds = LocalUiScale.current
                val dialogLabel = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * ds
                )
                AlertDialog(
                    onDismissRequest = { pendingRemoval = null },
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    title = {
                        Text(
                            text = "Remove \u201C${labelFor(target)}\u201D?",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize * ds
                            )
                        )
                    },
                    text = { Text("This effect will be removed from the photo.", style = dialogLabel) },
                    confirmButton = {
                        TextButton(onClick = {
                            onRemove(target)
                            pendingRemoval = null
                        }) {
                            Text("Remove", style = dialogLabel)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingRemoval = null }) {
                            Text("Cancel", style = dialogLabel)
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
    isDragging: Boolean,
    dragTranslationY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val isMerged = effect is MergedEffect
    val ls = LocalUiScale.current
    // The parent's drag lambdas are recreated every recomposition; read the
    // latest ones inside the (long-lived) pointerInput coroutine.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Drawing order: the dragged row rides ON TOP of its neighbours as a
            // translucent ghost (elevation + zIndex), still following the finger.
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                // Note: the parameter is named dragTranslationY because a val
                // spelled `translationY` would shadow this var member, so the
                // assignment below would fail to compile.
                translationY = dragTranslationY
                if (isDragging) {
                    alpha = 0.82f
                    shadowElevation = 16f
                }
            }
            .clickable(enabled = !isMerged && !isDragging, onClick = onSelect)
            .padding(vertical = 8.dp * ls),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The move icon is the reorder drag target (immediate drag, no long
        // press): grabbing it reorders the layer instead of scrolling the list.
        Column(
            modifier = Modifier
                .width(24.dp * ls)
                .pointerInput(index, isMerged) {
                    if (isMerged) return@pointerInput
                    detectDragGestures(
                        onDragStart = { currentOnDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentOnDrag(dragAmount.y)
                        },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragCancel() }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                .size(96.dp * ls)
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
                        .padding(3.dp * ls)
                        .fillMaxWidth()
                        .fillMaxHeight()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp * ls))

        // Label in the middle.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = labelFor(effect),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * ls
                ),
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isMerged) "Merged (not editable)" else detailFor(effect),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize * ls
                ),
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
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp * ls)
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
