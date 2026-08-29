package com.whimsicalart.feature.editor.ui.frames

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip

/**
 * Horizontal list of frame presets (plus "None" to clear) wired to the
 * editor's selected-frame state. The chip swatch previews the border colour.
 */
@Composable
fun FramePicker(
    selectedFrameId: String?,
    onFrameSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFrameId == null,
                onClick = { onFrameSelected(null) },
                label = {
                    Text(
                        text = "None",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            FramePresets.frames.forEach { frame ->
                FilterChip(
                    selected = selectedFrameId == frame.id,
                    onClick = { onFrameSelected(frame.id) },
                    label = {
                        Text(
                            text = frame.name,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(frame.borderColor))
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
}

/**
 * Draws the selected frame's border over the edit preview. Corner radius and
 * border use the same dp values the save pipeline normalises onto the bitmap,
 * so the preview matches the exported result.
 */
@Composable
fun FramePreview(
    frame: Frame,
    modifier: Modifier = Modifier
) {
    val radius = frame.cornerRadius.dp
    val border = frame.borderWidth.dp
    val color = Color(frame.borderColor)
    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = border.toPx().coerceAtLeast(2f)
        val corner = radius.toPx().coerceAtLeast(0f)
        val inset = stroke / 2f
        if (corner > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2f * inset, size.height - 2f * inset),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = stroke)
            )
        } else {
            drawRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2f * inset, size.height - 2f * inset),
                style = Stroke(width = stroke)
            )
        }
    }
}