package codes.pepper.whimsicalart.feature.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.core.designsystem.ScaledTopBar
import codes.pepper.whimsicalart.core.designsystem.ScaledTopBarIconButton

private val SUPPORTED_FORMATS = listOf("JPEG", "PNG", "WEBP")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val s = LocalUiScale.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScaledTopBar(
            title = "Settings",
            navigationIcon = {
                ScaledTopBarIconButton(
                    onClick = onBack,
                    contentDescription = "Back",
                    icon = Icons.AutoMirrored.Filled.ArrowBack
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp * s)
        ) {
            SectionHeader("Save Format")
            Row(modifier = Modifier.padding(vertical = 4.dp * s)) {
                SUPPORTED_FORMATS.forEach { format ->
                    FilterChip(
                        selected = uiState.saveFormat == format,
                        onClick = { viewModel.updateSaveFormat(format) },
                        label = {
                            Text(
                                text = format,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize * s
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp * s)
                            .heightIn((32.dp * s).coerceAtLeast(28.dp))
                    )
                }
            }

            SectionHeader("Save Quality")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = uiState.saveQuality.toFloat(),
                    onValueChange = { viewModel.updateSaveQuality(it.toInt()) },
                    valueRange = 50f..100f,
                    steps = 4,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp * s)
                )
                Text(
                    text = "${uiState.saveQuality}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                    )
                )
            }

            SettingSwitchRow(
                label = "Dark Mode",
                checked = uiState.darkMode,
                onCheckedChange = { viewModel.updateDarkMode(it) }
            )
            SettingSwitchRow(
                label = "Haptic Feedback",
                checked = uiState.hapticFeedback,
                onCheckedChange = { viewModel.updateHapticFeedback(it) }
            )
            SettingSwitchRow(
                label = "Auto-Save",
                checked = uiState.autoSave,
                onCheckedChange = { viewModel.updateAutoSave(it) }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val s = LocalUiScale.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize * s),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp * s, bottom = 4.dp * s)
    )
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val s = LocalUiScale.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp * s)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * s),
            modifier = Modifier.weight(1f)
        )
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
