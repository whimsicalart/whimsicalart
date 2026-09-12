package codes.pepper.whimsicalart.feature.beauty.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.ColourPresetRow
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale

/**
 * Beauty sub-tool controls hosted in the editor's bottom control panel — the
 * same panel that every other editing tool uses. It renders only the sub-tool
 * chips, sliders and color picker; it does NOT draw its own full-screen frame or
 * a separate preview image. The editor's own canvas shows the live result.
 *
 * This panel is STATELESS: the editor owns the [BeautyParams] (its single source
 * of truth) and drives this UI through [params], [selectedTool], [onToolSelected]
 * and [onChange]. Each control edit reports a full updated [BeautyParams] back
 * via [onChange], which the editor applies and re-derives its beauty stack from.
 */
@Composable
fun BeautyControls(
    params: BeautyParams,
    selectedTool: BeautyTool?,
    onToolSelected: (BeautyTool?) -> Unit,
    onChange: (BeautyParams) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BeautyToolbar(
            selectedTool = selectedTool,
            onToolSelected = onToolSelected
        )

        when (selectedTool) {
            BeautyTool.AUTO_BEAUTY -> BeautySlider(
                label = "Auto Beauty",
                value = params.auto,
                onValueChange = { onChange(params.copy(auto = it)) }
            )
            BeautyTool.SKIN_SMOOTHING -> BeautySlider(
                label = "Skin Smoothing",
                value = params.smoothing,
                onValueChange = { onChange(params.copy(smoothing = it)) }
            )
            BeautyTool.TEETH_WHITENING -> BeautySlider(
                label = "Teeth Whitening",
                value = params.teeth,
                onValueChange = { onChange(params.copy(teeth = it)) }
            )
            BeautyTool.EYE_BRIGHTENING -> BeautySlider(
                label = "Eye Brightening",
                value = params.eyeBrighten,
                onValueChange = { onChange(params.copy(eyeBrighten = it)) }
            )
            BeautyTool.DARK_CIRCLE_REMOVAL -> BeautySlider(
                label = "Dark Circle Removal",
                value = params.darkCircle,
                onValueChange = { onChange(params.copy(darkCircle = it)) }
            )
            BeautyTool.SPOT_REMOVAL -> BeautySlider(
                label = "Spot Removal",
                value = params.spots,
                onValueChange = { onChange(params.copy(spots = it)) }
            )
            BeautyTool.WRINKLE_REMOVAL -> BeautySlider(
                label = "Wrinkle Removal",
                value = params.wrinkles,
                onValueChange = { onChange(params.copy(wrinkles = it)) }
            )
            BeautyTool.SKIN_TONE -> BeautySlider(
                label = "Skin Tone",
                value = params.skinTone,
                valueRange = -1f..1f,
                onValueChange = { onChange(params.copy(skinTone = it)) }
            )
            BeautyTool.FACE_SLIM -> BeautySlider(
                label = "Face Slimming",
                value = params.faceSlim,
                valueRange = -1f..1f,
                onValueChange = { onChange(params.copy(faceSlim = it)) }
            )
            BeautyTool.EYE_ENLARGE -> BeautySlider(
                label = "Eye Enlarging",
                value = params.eyeEnlarge,
                valueRange = -1f..1f,
                onValueChange = { onChange(params.copy(eyeEnlarge = it)) }
            )
            BeautyTool.NOSE_ADJUST -> BeautySlider(
                label = "Nose Adjustment",
                value = params.nose,
                valueRange = -1f..1f,
                onValueChange = { onChange(params.copy(nose = it)) }
            )
            BeautyTool.JAW_ADJUST -> BeautySlider(
                label = "Jaw Adjustment",
                value = params.jaw,
                valueRange = -1f..1f,
                onValueChange = { onChange(params.copy(jaw = it)) }
            )
            BeautyTool.BRIGHTNESS_PEN -> {
                ColorPicker(
                    selectedColor = params.penColor,
                    onColorSelected = { onChange(params.copy(penColor = it)) }
                )
                BeautySlider(
                    label = "Brush Size",
                    value = params.brushSize,
                    valueRange = 10f..80f,
                    isPercent = false,
                    onValueChange = { onChange(params.copy(brushSize = it)) }
                )
                BeautySlider(
                    label = "Brush Opacity",
                    value = params.brushOpacity,
                    onValueChange = { onChange(params.copy(brushOpacity = it)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * LocalUiScale.current)
                ) {
                    val s = LocalUiScale.current
                    TextButton(
                        onClick = {
                            if (params.brushStrokes.isNotEmpty()) {
                                onChange(params.copy(brushStrokes = params.brushStrokes.dropLast(1)))
                            }
                        }
                    ) {
                        Text(
                            text = "Undo",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    }
                    TextButton(
                        onClick = { onChange(params.copy(brushStrokes = emptyList(), activeStroke = null)) }
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    }
                }
            }
            BeautyTool.LIPSTICK,
            BeautyTool.BLUSH,
            BeautyTool.EYE_SHADOW,
            BeautyTool.EYELINER,
            BeautyTool.FOUNDATION,
            BeautyTool.HAIR_COLOR -> {
                // The active sub-tool's OWN colour slot: sharing one field here is
                // what leaked a Lipstick pick into the Blush/Eye Shadow selection
                // (docs/effects_reference.md → Beauty → Quirks).
                val colorValue = when (selectedTool) {
                    BeautyTool.LIPSTICK -> params.lipstickColor
                    BeautyTool.BLUSH -> params.blushColor
                    BeautyTool.EYE_SHADOW -> params.eyeShadowColor
                    BeautyTool.EYELINER -> params.eyelinerColor
                    BeautyTool.FOUNDATION -> params.foundationColor
                    BeautyTool.HAIR_COLOR -> params.hairColor
                    else -> 0
                }
                val colorUpdate: (Int) -> Unit = when (selectedTool) {
                    BeautyTool.LIPSTICK -> { c -> onChange(params.copy(lipstickColor = c)) }
                    BeautyTool.BLUSH -> { c -> onChange(params.copy(blushColor = c)) }
                    BeautyTool.EYE_SHADOW -> { c -> onChange(params.copy(eyeShadowColor = c)) }
                    BeautyTool.EYELINER -> { c -> onChange(params.copy(eyelinerColor = c)) }
                    BeautyTool.FOUNDATION -> { c -> onChange(params.copy(foundationColor = c)) }
                    BeautyTool.HAIR_COLOR -> { c -> onChange(params.copy(hairColor = c)) }
                    else -> { _: Int -> }
                }
                ColorPicker(
                    selectedColor = colorValue,
                    onColorSelected = colorUpdate
                )
                val label = when (selectedTool) {
                    BeautyTool.LIPSTICK -> "Lipstick"
                    BeautyTool.BLUSH -> "Blush"
                    BeautyTool.EYE_SHADOW -> "Eye Shadow"
                    BeautyTool.EYELINER -> "Eyeliner"
                    BeautyTool.FOUNDATION -> "Foundation"
                    BeautyTool.HAIR_COLOR -> "Hair Color"
                    else -> ""
                }
                val value = when (selectedTool) {
                    BeautyTool.LIPSTICK -> params.lipstick
                    BeautyTool.BLUSH -> params.blush
                    BeautyTool.EYE_SHADOW -> params.eyeShadow
                    BeautyTool.EYELINER -> params.eyeliner
                    BeautyTool.FOUNDATION -> params.foundation
                    BeautyTool.HAIR_COLOR -> params.hair
                    else -> 0f
                }
                val update: (Float) -> Unit = when (selectedTool) {
                    BeautyTool.LIPSTICK -> { v -> onChange(params.copy(lipstick = v)) }
                    BeautyTool.BLUSH -> { v -> onChange(params.copy(blush = v)) }
                    BeautyTool.EYE_SHADOW -> { v -> onChange(params.copy(eyeShadow = v)) }
                    BeautyTool.EYELINER -> { v -> onChange(params.copy(eyeliner = v)) }
                    BeautyTool.FOUNDATION -> { v -> onChange(params.copy(foundation = v)) }
                    BeautyTool.HAIR_COLOR -> { v -> onChange(params.copy(hair = v)) }
                    else -> { _: Float -> }
                }
                BeautySlider(label = label, value = value, onValueChange = update)
            }
            null -> {}
        }
    }
}

@Composable
private fun BeautyToolbar(
    selectedTool: BeautyTool?,
    onToolSelected: (BeautyTool?) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalUiScale.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp * s, vertical = 8.dp * s),
        horizontalArrangement = Arrangement.spacedBy(8.dp * s)
    ) {
        BeautyTool.entries.forEach { tool ->
            FilterChip(
                selected = selectedTool == tool,
                onClick = { onToolSelected(if (selectedTool == tool) null else tool) },
                label = {
                    Text(
                        text = when (tool) {
                            BeautyTool.AUTO_BEAUTY -> "Auto"
                            BeautyTool.SKIN_SMOOTHING -> "Smooth"
                            BeautyTool.TEETH_WHITENING -> "Teeth"
                            BeautyTool.EYE_BRIGHTENING -> "Eyes"
                            BeautyTool.BRIGHTNESS_PEN -> "Pen"
                            BeautyTool.DARK_CIRCLE_REMOVAL -> "Circles"
                            BeautyTool.SPOT_REMOVAL -> "Spots"
                            BeautyTool.WRINKLE_REMOVAL -> "Wrinkles"
                            BeautyTool.SKIN_TONE -> "Skin Tone"
                            BeautyTool.FACE_SLIM -> "Slim"
                            BeautyTool.EYE_ENLARGE -> "Eyes"
                            BeautyTool.NOSE_ADJUST -> "Nose"
                            BeautyTool.JAW_ADJUST -> "Jaw"
                            BeautyTool.LIPSTICK -> "Lips"
                            BeautyTool.BLUSH -> "Blush"
                            BeautyTool.EYE_SHADOW -> "Eye Sh."
                            BeautyTool.EYELINER -> "Liner"
                            BeautyTool.FOUNDATION -> "Base"
                            BeautyTool.HAIR_COLOR -> "Hair"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = MaterialTheme.typography.labelMedium.fontSize * s
                        )
                    )
                },
                modifier = Modifier.heightIn(min = (48.dp * s).coerceAtLeast(32.dp)),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun ColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // The SHARED picker (core.designsystem): one compact row of the common makeup
    // colours with a rainbow circle as the last cell, opening the full picker
    // popup (documented under *Shared business rules → Colour picker*). Alpha is
    // hidden: makeup/pen colour is carried opaque, the pen has its own opacity.
    ColourPresetRow(
        selected = Color(selectedColor),
        onColorChange = { onColorSelected(it.toArgb()) },
        swatches = MakeupPalette.colors,
        showAlpha = false,
        modifier = modifier
    )
}

@Composable
private fun BeautySlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isPercent: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Styled to mirror the editor's AdjustmentSlider so beauty sub-tools match
    // the padding / font sizes / slider sizing of every other edit tool.
    val s = LocalUiScale.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * s, vertical = 8.dp * s)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp * s))
            Text(
                text = if (isPercent) "${(value * 100).toInt()}%" else value.toInt().toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize * s
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height((4.dp * s).coerceAtLeast(2.dp)))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp * s)
        )
    }
}
