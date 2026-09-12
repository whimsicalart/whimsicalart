package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale

/**
 * The readout text for [AdjustmentSlider]: a plain value prints its integer; a
 * percent range (e.g. the 0..1 skin denoise backend) reads as a live
 * `0..100%` figure. Pure so the rendering of both readouts is unit-testable.
 */
internal fun sliderReadout(value: Float, isPercent: Boolean): String =
    if (isPercent) "${(value * 100).toInt()}%" else value.toInt().toString()

@Composable
fun AdjustmentSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -100f..100f,
    isPercent: Boolean = false
) {
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
                text = sliderReadout(value, isPercent),
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
