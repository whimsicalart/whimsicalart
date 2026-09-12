package codes.pepper.whimsicalart.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * The shared colour picker (docs/effects_reference.md → *Shared business rules* →
 * *Colour picker*): ONE compact row of the most common colours ([CompactPresetColours])
 * whose **last cell is a rainbow-coloured circle** that opens the full picker as a
 * **popup dialog** (wide presets + hue–saturation box + brightness/alpha sliders).
 * Tapping any inline preset applies it immediately; choices in the popup update the
 * picked colour live and commit on confirm.
 *
 * [label] is the control's title (empty hides it). [swatches] customises the compact
 * row; the popup always offers the wide [ColourPickerPresets]. [showAlpha] puts the
 * alpha slider in the popup (text and frame carry colour opacity; the pen has its own
 * opacity slider so it passes false). [leading] is an optional cell rendered first
 * inside the row (e.g. a "default/None" swatch) so a one-row selector can carry its
 * reset affordance without stacking a second line.
 *
 * Used wherever a colour is picked — text (font / background / shadow / outline),
 * sticker tint, frame and pen — so every colour in the app uses ONE picker.
 */
@Composable
fun ColourPicker(
    label: String,
    selected: Color,
    onColorChange: (Color) -> Unit,
    swatches: List<Int> = CompactPresetColours,
    showAlpha: Boolean = true,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = MaterialTheme.typography.labelMedium.fontSize * LocalUiScale.current
                )
            )
        }
        ColourPresetRow(
            selected = selected,
            onColorChange = onColorChange,
            swatches = swatches,
            showAlpha = showAlpha,
            leading = leading
        )
    }
}

/**
 * A single compact row of the most common colours ([CompactPresetColours] by
 * default) whose last cell is a rainbow-coloured circle ([RainbowDisc]) that opens
 * the full picker popup ([ColourPickerDialog]). Tapping a preset applies it
 * immediately; the popup choice commits on confirm. Presets keep the selected
 * colour's alpha so a translucent picked colour is not lost on the row.
 * [leading] renders as the first cell of the row (before the presets).
 */
@Composable
fun ColourPresetRow(
    selected: Color,
    onColorChange: (Color) -> Unit,
    swatches: List<Int> = CompactPresetColours,
    showAlpha: Boolean = true,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val keepAlpha = selected.alpha
    val selectedArgb = selected.toArgb()
    val s = LocalUiScale.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp * s),
        horizontalArrangement = Arrangement.spacedBy(8.dp * s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
        }
        swatches.forEach { preset ->
            ColourSwatch(
                color = Color(preset).copy(alpha = keepAlpha),
                selected = Color(preset).copy(alpha = keepAlpha).toArgb() == selectedArgb,
                onClick = { onColorChange(Color(preset).copy(alpha = keepAlpha)) }
            )
        }
        RainbowDisc(
            modifier = Modifier
                .size(38.dp * s)
                .padding((4.dp * s).coerceAtLeast(2.dp))
                .clip(CircleShape)
                .clickable { showPicker = true }
        )
    }

    if (showPicker) {
        ColourPickerDialog(
            initial = selected,
            onDismiss = { showPicker = false },
            onColorChange = onColorChange,
            showAlpha = showAlpha
        )
    }
}

/**
 * The full colour picker inside a popup dialog: the wide [ColourPickerPresets]
 * swatch row, a hue–saturation box, a brightness slider and (when [showAlpha]) an
 * alpha slider. Dragging/picking updates the picked colour **live** through
 * [onColorChange]; the choice is applied on **Confirm** (Cancel reverts to
 * [initial]).
 */
@Composable
fun ColourPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onColorChange: (Color) -> Unit,
    swatches: List<Int> = ColourPickerPresets,
    showAlpha: Boolean = true
) {
    val initialArgb = initial.toArgb()
    val controller = remember(initialArgb) { ColourPickerController(initialArgb) }
    val s = LocalUiScale.current

    Dialog(
        onDismissRequest = {
            onColorChange(initial)
            onDismiss()
        }
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp * s)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp * s)
            ) {
                Text(
                    text = "Colour",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * s
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp * s),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * s)
                ) {
                    swatches.forEach { preset ->
                        val presetColour = Color(preset).copy(alpha = controller.alpha)
                        ColourSwatch(
                            color = presetColour,
                            selected = presetColour.toArgb() == controller.toArgb(),
                            onClick = {
                                controller.selectPreset(preset)
                                onColorChange(Color(controller.toArgb()))
                            }
                        )
                    }
                }

                HueSatBox(
                    hue = controller.hue,
                    sat = controller.saturation,
                    value = controller.value,
                    onPick = { h, s ->
                        controller.hue = h
                        controller.saturation = s
                        onColorChange(Color(controller.toArgb()))
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Brightness",
                        modifier = Modifier.padding(end = 8.dp * s),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                        )
                    )
                    Slider(
                        value = controller.value,
                        onValueChange = { v ->
                            controller.value = v
                            onColorChange(Color(controller.toArgb()))
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp * s)
                    )
                    Text(
                        text = "${(controller.value * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                        )
                    )
                }

                if (showAlpha) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Opacity",
                            modifier = Modifier.padding(end = 8.dp * s),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                            )
                        )
                        Slider(
                            value = controller.alpha,
                            onValueChange = { a ->
                                controller.alpha = a
                                onColorChange(Color(controller.toArgb()))
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp * s)
                        )
                        Text(
                            text = "${(controller.alpha * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp * s),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        onColorChange(initial)
                        onDismiss()
                    }) {
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    }
                    TextButton(onClick = {
                        onColorChange(Color(controller.toArgb()))
                        onDismiss()
                    }) {
                        Text(
                            "OK",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * A rainbow-coloured circle with a light centre — the picker row's last cell. Its
 * hue weaves around a full rainbow, reading as the affordance that opens the full
 * colour picker.
 */
@Composable
fun RainbowDisc(modifier: Modifier = Modifier) {
    val hues = List(13) { i -> Color.hsv(i * 30f, 1f, 1f) }
    val s = LocalUiScale.current
    Box(modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)) {
        Canvas(modifier = Modifier.size(34.dp * s)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.sweepGradient(hues, center = center),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = radius * 0.3f,
                center = center
            )
        }
    }
}

/**
 * A square hue–saturation field: hue sweeps left→right and saturation
 * bottom→top over the current [value] (brightness is an outer slider). Dragging
 * reports (hue, saturation). The marker is positioned by the live [hue]/[sat]
 * so it stays in sync with preset/brightness picks.
 */
@Composable
private fun HueSatBox(
    hue: Float,
    sat: Float,
    value: Float,
    onPick: (hue: Float, sat: Float) -> Unit
) {
    var dragSize by remember { mutableStateOf(IntSize.Zero) }
    val s = LocalUiScale.current
    val boxModifier = Modifier
        .fillMaxWidth()
        .height(120.dp * s)
        .onSizeChanged { dragSize = it }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    val w = dragSize.width.coerceAtLeast(1).toFloat()
                    val hgt = dragSize.height.coerceAtLeast(1).toFloat()
                    onPick((offset.x / w).coerceIn(0f, 1f) * 360f, 1f - (offset.y / hgt).coerceIn(0f, 1f))
                },
                onDrag = { change, _ ->
                    val w = dragSize.width.coerceAtLeast(1).toFloat()
                    val hgt = dragSize.height.coerceAtLeast(1).toFloat()
                    onPick((change.position.x / w).coerceIn(0f, 1f) * 360f, 1f - (change.position.y / hgt).coerceIn(0f, 1f))
                }
            )
        }
    Box(modifier = boxModifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp * s)) {
            val boxW = dragSize.width.coerceAtLeast(1).toFloat()
            val boxH = dragSize.height.coerceAtLeast(1).toFloat()
            val hueBrush = Brush.horizontalGradient(
                List(360) { i -> Color(ColourMath.hsvToArgb(i.toFloat(), 1f, value)) }
            )
            drawRect(brush = hueBrush)
            // Saturation overlay: fully saturated at the top, desaturated (grey)
            // at the bottom, so saturation increases bottom→top.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(android.graphics.Color.TRANSPARENT),
                        Color(ColourMath.hsvToArgb(hue, 0f, value)).copy(alpha = 0.9f)
                    )
                )
            )
            // Marker at the current hue/sat.
            val mx = (hue / 360f) * boxW
            val my = (1f - sat) * boxH
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(mx, my),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

/**
 * The working state of the full picker popup, extracted from the dialog so its
 * behaviour is unit-testable without a composition host
 * (docs/effects_reference.md → *Shared business rules* → *Colour picker*).
 * Holds the live hue/saturation/brightness/alpha; [toArgb] is the current picked
 * colour (committed by the dialog's Confirm button). Selecting a preset keeps the
 * current alpha (colour opacity is a separate channel the user controls with the
 * alpha slider, so a quick preset pick must not reset it).
 */
class ColourPickerController(initialArgb: Int) {

    /** Hue 0–360. */
    var hue: Float

    /** Saturation 0–1. */
    var saturation: Float

    /** Brightness (HSV value) 0–1. */
    var value: Float

    /** Colour opacity 0–1. */
    var alpha: Float

    init {
        val (h, s, v) = ColourMath.argbToHsv(initialArgb)
        hue = h
        saturation = s
        value = v
        alpha = Color(initialArgb).alpha
    }

    /** The packed ARGB of the currently working colour. */
    fun toArgb(): Int = ColourMath.hsvToArgb(hue, saturation, value, alpha)

    /** Adopts a preset swatch's colour, keeping the current alpha. */
    fun selectPreset(presetArgb: Int) {
        val (h, s, v) = ColourMath.argbToHsv(presetArgb)
        hue = h
        saturation = s
        value = v
    }
}

/**
 * A round, selected-capable preset swatch. [selected] shows the primary ring.
 * A transparent colour renders as an outlined empty cell.
 */
@Composable
fun ColourSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = CircleShape
    val s = LocalUiScale.current
    if (color.alpha == 0f) {
        Box(
            modifier = modifier
                .padding(4.dp * s)
                .size(26.dp * s)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable(onClick = onClick)
        )
    } else {
        Box(
            modifier = modifier
                .padding(4.dp * s)
                .size(26.dp * s)
                .clip(shape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = shape
                )
                .clickable(onClick = onClick)
        )
    }
}