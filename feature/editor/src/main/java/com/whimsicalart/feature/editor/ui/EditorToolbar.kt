package com.whimsicalart.feature.editor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BorderAll
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.whimsicalart.feature.editor.ui.viewmodel.EditTool

@Composable
fun EditorToolbar(
    selectedTool: EditTool?,
    onToolSelected: (EditTool?) -> Unit,
    modifier: Modifier = Modifier,
    onOpenBeauty: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        onOpenBeauty?.let { beautify ->
            FilterChip(
                selected = false,
                onClick = beautify,
                label = {
                    Text(
                        text = "Beauty",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }

        EditTool.entries.forEach { tool ->
            FilterChip(
                selected = selectedTool == tool,
                onClick = {
                    onToolSelected(if (selectedTool == tool) null else tool)
                },
                label = {
                    Text(
                        text = tool.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

private val EditTool.label: String
    get() = when (this) {
        EditTool.CROP -> "Crop"
        EditTool.ROTATE -> "Rotate"
        EditTool.FLIP -> "Flip"
        EditTool.BRIGHTNESS -> "Brightness"
        EditTool.CONTRAST -> "Contrast"
        EditTool.SATURATION -> "Saturation"
        EditTool.SHARPEN -> "Sharpen"
        EditTool.EXPOSURE -> "Exposure"
        EditTool.SHADOWS -> "Shadows"
        EditTool.HIGHLIGHTS -> "Highlights"
        EditTool.TEMPERATURE -> "Temperature"
        EditTool.TINT -> "Tint"
        EditTool.VIGNETTE -> "Vignette"
        EditTool.STICKERS -> "Stickers"
        EditTool.TEXT -> "Text"
        EditTool.FRAMES -> "Frames"
        EditTool.MOSAIC -> "Mosaic"
        EditTool.BLUR_BRUSH -> "Blur Brush"
        EditTool.PEN -> "Pen"
    }

private val EditTool.icon: ImageVector
    get() = when (this) {
        EditTool.CROP -> Icons.Filled.Crop
        EditTool.ROTATE -> Icons.Filled.RotateRight
        EditTool.FLIP -> Icons.Filled.Flip
        EditTool.BRIGHTNESS -> Icons.Filled.BrightnessHigh
        EditTool.CONTRAST -> Icons.Filled.Contrast
        EditTool.SATURATION -> Icons.Filled.Palette
        EditTool.SHARPEN -> Icons.Filled.AutoFixHigh
        EditTool.EXPOSURE -> Icons.Filled.Exposure
        EditTool.SHADOWS -> Icons.Filled.BrightnessLow
        EditTool.HIGHLIGHTS -> Icons.Filled.WbSunny
        EditTool.TEMPERATURE -> Icons.Filled.Thermostat
        EditTool.TINT -> Icons.Filled.InvertColors
        EditTool.VIGNETTE -> Icons.Filled.Lens
        EditTool.STICKERS -> Icons.Filled.EmojiEmotions
        EditTool.TEXT -> Icons.Filled.TextFields
        EditTool.FRAMES -> Icons.Filled.BorderAll
        EditTool.MOSAIC -> Icons.Filled.GridOn
        EditTool.BLUR_BRUSH -> Icons.Filled.BlurOn
        EditTool.PEN -> Icons.Filled.Edit
    }
