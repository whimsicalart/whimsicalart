package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale

@Composable
internal fun OnOffChipToggle(
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    ControlLabel(label)
    val s = LocalUiScale.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = enabled,
            onClick = onToggle,
            label = {
                Text(
                    text = if (enabled) "On" else "Off",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = MaterialTheme.typography.labelMedium.fontSize * s
                    )
                )
            },
            colors = chipColors()
        )
    }
}

@Composable
internal fun ControlLabel(text: String) {
    val s = LocalUiScale.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = MaterialTheme.typography.labelMedium.fontSize * s
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp * s, bottom = 4.dp * s)
    )
}

@Composable
internal fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)