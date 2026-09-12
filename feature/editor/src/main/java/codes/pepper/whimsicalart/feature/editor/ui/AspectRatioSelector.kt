package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale

@Composable
fun AspectRatioSelector(
    selectedAspectRatio: CropAspectRatio,
    onAspectRatioSelected: (CropAspectRatio) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val s = LocalUiScale.current
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp * s, vertical = 8.dp * s),
        horizontalArrangement = Arrangement.spacedBy(8.dp * s)
    ) {
        CropAspectRatio.entries.forEach { aspectRatio ->
            FilterChip(
                selected = selectedAspectRatio == aspectRatio,
                onClick = { onAspectRatioSelected(aspectRatio) },
                enabled = enabled,
                label = {
                    Text(
                        text = aspectRatio.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = MaterialTheme.typography.labelMedium.fontSize * s
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
}
