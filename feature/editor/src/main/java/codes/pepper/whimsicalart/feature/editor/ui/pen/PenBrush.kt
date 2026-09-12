package codes.pepper.whimsicalart.feature.editor.ui.pen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.ColourPresetRow
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.feature.editor.domain.PenBrushKind
import codes.pepper.whimsicalart.feature.editor.domain.StrokeLayer
import codes.pepper.whimsicalart.feature.editor.domain.StrokeOverlayRenderer
import codes.pepper.whimsicalart.feature.editor.domain.StrokeType
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun PenStrokeOverlay(
    strokes: List<PenStroke>,
    currentStroke: PenStroke?,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    brushingEnabled: Boolean = true
) {
    val all = currentStroke?.let { strokes + it } ?: strokes

    Canvas(
        modifier = modifier.then(
            if (brushingEnabled) {
                Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> onDragStart(offset) },
                        onDrag = { change, _ ->
                            change.consume()
                            onDragMove(change.position)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
                    )
                }
            } else {
                Modifier
            }
        )
    ) {
        all.forEach { stroke -> drawPenStroke(stroke) }
    }
}

private fun DrawScope.drawPenStroke(stroke: PenStroke) {
    if (stroke.points.isEmpty()) return
    drawPenRasterStroke(stroke)
}

/**
 * Draws a stroke using the SAME max-alpha raster the committed fold composites —
 * [StrokeOverlayRenderer.penStrokeRaster] — so every brush (SOLID, GLOW, NEON,
 * RAINBOW) previews pixel-identical to the committed result at every hardness
 * and opacity (docs/effects_reference.md → Pen controls). The raster is the
 * stroke's bounding region placed at its frame offset; drawn as-is, the whole
 * multi-segment stroke is one accumulation pass:
 * - overlapping stamps never compound opacity (a soft stroke does not darken
 *   where it crosses itself, a GLOW/NEON halo does not brighten where two
 *   segments' halos overlap at a sharp bend or a slow drag);
 * - GLOW's white-hot centre and band colours and RAINBOW's per-stamp hue shift
 *   come out of the same committed profile; nothing is left to the overlay's
 *   blended draw calls.
 */
private fun DrawScope.drawPenRasterStroke(stroke: PenStroke) {
    val w = size.width.toInt().coerceAtLeast(1)
    val h = size.height.toInt().coerceAtLeast(1)
    if (w == 0 || h == 0) return
    val layer = StrokeLayer(
        type = StrokeType.PEN,
        points = stroke.points.map { p -> p.x / w to p.y / h },
        brushSize = stroke.size.coerceAtLeast(2f) / w,
        color = stroke.color.copy(alpha = stroke.opacity.coerceIn(0f, 1f)).toArgb(),
        hardness = stroke.hardness.coerceIn(0f, 1f),
        brush = when (stroke.brushType) {
            PenBrushType.SOLID -> PenBrushKind.SOLID
            PenBrushType.GLOW -> PenBrushKind.GLOW
            PenBrushType.NEON -> PenBrushKind.NEON
            PenBrushType.RAINBOW -> PenBrushKind.RAINBOW
        }
    )
    val raster = StrokeOverlayRenderer.penStrokeRaster(w, h, layer) ?: return
    drawImage(
        image = raster.bitmap.asImageBitmap(),
        topLeft = Offset(raster.offsetX.toFloat(), raster.offsetY.toFloat())
    )
}

/**
 * The rainbow hue advances 18°/sample stamp exactly like the committed raster's
 * per-sample `hueIndex`. Sample (drawingIndex) and hue both live in
 * BitmapRenderer.kt's drawPenStroke pass, so the live SOLID/RAINBOW stroke —
 * which delegates to that same pass via [drawPenRasterStroke] — can never drift
 * from the committed hue cycle. These two helpers stay as the documented rule
 * that PenRainbowParityTest pins.
 */
internal fun rainbowHueDegrees(sampleIndex: Int): Float = (sampleIndex * 18f) % 360f

/**
 * Number of stamps the committed rasteriser samples along a segment of length
 * [distPx] — one every `radius*0.5` px plus the endpoint ([StrokeOverlayRenderer.penStrokeRaster]
 * delegates to the same spacing, so the hue density of a live RAINBOW stroke is
 * identical to the committed pass's).
 */
internal fun penSegmentSampleCount(distPx: Float, radiusPx: Float): Int =
    ceil(distPx / (radiusPx * 0.5f)).toInt().coerceAtLeast(1) + 1

@Composable
fun PenBrushPanel(
    state: PenEditorState,
    onBrushTypeChange: (PenBrushType) -> Unit,
    onColorChange: (Color) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onBrushOpacityChange: (Float) -> Unit,
    onBrushHardnessChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onAddLayer: () -> Unit,
    onRemoveLayer: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onToggleLayerVisibility: (Int) -> Unit,
    onMoveLayerToTop: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val showClearConfirm = remember { mutableStateOf(false) }
    val showRemoveLayerConfirm = remember { mutableStateOf(false) }
    val s = LocalUiScale.current

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp * s, vertical = 8.dp * s)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            PenBrushType.entries.forEach { type ->
                FilterChip(
                    selected = state.selectedBrushType == type,
                    onClick = { onBrushTypeChange(type) },
                    label = {
                        Text(
                            text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // RAINBOW derives its hue from the stroke point index, ignoring
        // selectedColor entirely — hide the palette so the UI doesn't suggest a
        // colour selection that has no effect (spec: rainbow colour clause).
        if (state.selectedBrushType != PenBrushType.RAINBOW) {
            ColourPresetRow(
                selected = state.selectedColor,
                onColorChange = onColorChange,
                showAlpha = false,
                modifier = Modifier.padding(top = 8.dp * s)
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Size",
                modifier = Modifier.padding(end = 8.dp * s),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                )
            )
            Slider(
                value = state.brushSize,
                onValueChange = onBrushSizeChange,
                valueRange = 2f..48f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp * s)
            )
            Text(
                text = "${state.brushSize.toInt()}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                )
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Opacity",
                modifier = Modifier.padding(end = 8.dp * s),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                )
            )
            Slider(
                value = state.brushOpacity,
                onValueChange = onBrushOpacityChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp * s)
            )
            Text(
                text = "${(state.brushOpacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                )
            )
        }

        // Hardness is only meaningful for SOLID and RAINBOW (the radial-gradient
        // stamp's edge); GLOW/NEON preview and commit ignore it, so hide the
        // slider for them (spec: hardness = solid/rainbow only, hidden for
        // glow/neon). Opacity stays visible for every brush.
        if (state.selectedBrushType == PenBrushType.SOLID || state.selectedBrushType == PenBrushType.RAINBOW) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Hardness",
                    modifier = Modifier.padding(end = 8.dp * s),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                    )
                )
                Slider(
                    value = state.brushHardness,
                    onValueChange = onBrushHardnessChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp * s)
                )
                Text(
                    text = "${(state.brushHardness * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Layers",
                modifier = Modifier.padding(end = 8.dp * s),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                )
            )
            TextButton(onClick = onAddLayer) {
                Text(
                    "Add",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            }
            TextButton(
                onClick = { showRemoveLayerConfirm.value = true },
                enabled = state.layers.size > 1
            ) {
                Text(
                    "Delete",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp * s)
        ) {
            state.layers.forEachIndexed { index, layer ->
                val active = state.activeLayerIndex == index
                val cellTextStyle = MaterialTheme.typography.labelMedium.copy(
                    fontSize = MaterialTheme.typography.labelMedium.fontSize * s
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectLayer(index) }
                        .padding(horizontal = 8.dp * s, vertical = 4.dp * s)
                ) {
                    Text(
                        layer.name,
                        style = cellTextStyle,
                        color = if (layer.visible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline
                    )
                    Row {
                        TextButton(
                            onClick = { onToggleLayerVisibility(index) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(if (layer.visible) "Hide" else "Show", style = cellTextStyle)
                        }
                        if (!active && state.layers.size > 1) {
                            TextButton(
                                onClick = { onMoveLayerToTop(index) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Top", style = cellTextStyle)
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            val btnStyle = MaterialTheme.typography.labelLarge.copy(
                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
            )
            TextButton(
                onClick = onUndo,
                enabled = state.strokes.isNotEmpty()
            ) { Text("Undo", style = btnStyle) }
            TextButton(
                onClick = onRedo,
                enabled = state.undoStack.isNotEmpty()
            ) { Text("Redo", style = btnStyle) }
            TextButton(onClick = { showClearConfirm.value = true }) { Text("Clear All", style = btnStyle) }
        }
    }

    if (showClearConfirm.value) {
        val ds = LocalUiScale.current
        val titleStyle = MaterialTheme.typography.titleLarge.copy(
            fontSize = MaterialTheme.typography.titleLarge.fontSize * ds
        )
        val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = MaterialTheme.typography.bodyLarge.fontSize * ds
        )
        AlertDialog(
            onDismissRequest = { showClearConfirm.value = false },
            title = { Text("Clear pen strokes?", style = titleStyle) },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirm.value = false
                }) { Text("Clear", style = bodyStyle) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm.value = false }) {
                    Text("Cancel", style = bodyStyle)
                }
            }
        )
    }

    if (showRemoveLayerConfirm.value) {
        val ds = LocalUiScale.current
        val titleStyle = MaterialTheme.typography.titleLarge.copy(
            fontSize = MaterialTheme.typography.titleLarge.fontSize * ds
        )
        val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = MaterialTheme.typography.bodyLarge.fontSize * ds
        )
        AlertDialog(
            onDismissRequest = { showRemoveLayerConfirm.value = false },
            title = { Text("Delete active layer?", style = titleStyle) },
            text = { Text("The active layer and its strokes will be removed.", style = bodyStyle) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveLayer()
                    showRemoveLayerConfirm.value = false
                }) { Text("Delete", style = bodyStyle) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveLayerConfirm.value = false }) {
                    Text("Cancel", style = bodyStyle)
                }
            }
        )
    }
}
