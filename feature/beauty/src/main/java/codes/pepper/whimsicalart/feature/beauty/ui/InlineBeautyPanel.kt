package codes.pepper.whimsicalart.feature.beauty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            if (params.brushStrokes.isNotEmpty()) {
                                onChange(params.copy(brushStrokes = params.brushStrokes.dropLast(1)))
                            }
                        }
                    ) { Text("Undo") }
                    TextButton(
                        onClick = { onChange(params.copy(brushStrokes = emptyList(), activeStroke = null)) }
                    ) { Text("Clear") }
                }
            }
            BeautyTool.LIPSTICK,
            BeautyTool.BLUSH,
            BeautyTool.EYE_SHADOW,
            BeautyTool.EYELINER,
            BeautyTool.FOUNDATION,
            BeautyTool.HAIR_COLOR -> {
                ColorPicker(
                    selectedColor = params.makeupColor,
                    onColorSelected = { onChange(params.copy(makeupColor = it)) }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MakeupPalette.colors.forEach { color ->
            val isSelected = selectedColor == color
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPercent) "${(value * 100).toInt()}%" else value.toInt().toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )
    }
}
