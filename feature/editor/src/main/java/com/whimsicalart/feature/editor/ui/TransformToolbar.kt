package com.whimsicalart.feature.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class TransformTool {
    ROTATE_LEFT,
    ROTATE_RIGHT,
    FLIP_HORIZONTAL,
    FLIP_VERTICAL
}

@Composable
fun TransformToolbar(
    onToolSelected: (TransformTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = { onToolSelected(TransformTool.ROTATE_LEFT) }) {
            Icon(
                imageVector = Icons.Default.RotateLeft,
                contentDescription = "Rotate Left"
            )
        }

        IconButton(onClick = { onToolSelected(TransformTool.ROTATE_RIGHT) }) {
            Icon(
                imageVector = Icons.Default.RotateRight,
                contentDescription = "Rotate Right"
            )
        }

        IconButton(onClick = { onToolSelected(TransformTool.FLIP_HORIZONTAL) }) {
            Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = "Flip Horizontal"
            )
        }

        IconButton(onClick = { onToolSelected(TransformTool.FLIP_VERTICAL) }) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = "Flip Vertical"
            )
        }
    }
}
