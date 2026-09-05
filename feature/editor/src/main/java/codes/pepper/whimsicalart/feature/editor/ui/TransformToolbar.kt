package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class TransformTool {
    ROTATE_LEFT,
    ROTATE_RIGHT,
    FLIP_HORIZONTAL,
    FLIP_VERTICAL
}

@Composable
fun TransformToolbar(
    rotation: Float,
    onRotationChanged: (Float) -> Unit,
    onFlip: (TransformTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "free rotate",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Slider(
            value = rotation,
            onValueChange = { onRotationChanged(it) },
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${rotation.roundToInt()}°",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        // Control line (CW/CCW rotate + H/V flip) sits just below the main
        // toolbar/slider, with breathing room above and around it for clarity.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onRotationChanged(normalize(rotation - 90f)) }) {
                Icon(
                    imageVector = Icons.Default.RotateLeft,
                    contentDescription = "Rotate Left (90°)"
                )
            }
            IconButton(onClick = { onRotationChanged(normalize(rotation + 90f)) }) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Rotate Right (90°)"
                )
            }
            IconButton(onClick = { onFlip(TransformTool.FLIP_HORIZONTAL) }) {
                Icon(
                    imageVector = Icons.Default.Flip,
                    contentDescription = "Flip Horizontal"
                )
            }
            IconButton(onClick = { onFlip(TransformTool.FLIP_VERTICAL) }) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Flip Vertical"
                )
            }
        }
    }
}

private fun normalize(angle: Float): Float {
    return ((angle % 360f) + 360f) % 360f
}
