package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditorUiState

/**
 * The editor's top chrome row: back / layers / undo / redo / save. Pure
 * presentation — every flag comes from [uiState] and every tap goes through
 * [actions], so the composable has no ViewModel dependency.
 *
 * The bar is a compact custom row (not the fixed-height M3 [androidx.compose.material3.TopAppBar])
 * so that its HEIGHT and touch targets shrink with [LocalUiScale] in multi-window /
 * split-screen. At uiScale == 1f the geometry matches the Material 3 64dp app bar:
 * 64dp row (below the status bar), 48dp IconButtons, 24dp icons.
 */
@Composable
internal fun EditorTopBar(
    uiState: EditorUiState,
    actions: EditorActions
) {
    val s = LocalUiScale.current
    val barHeight = (64.dp * s).coerceAtLeast(48.dp)
    val iconButtonSize = (48.dp * s).coerceAtLeast(40.dp)
    val iconSize = 24.dp * s
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .height(barHeight)
                .padding(horizontal = (4.dp * s).coerceAtLeast(2.dp)),
            horizontalArrangement = Arrangement.spacedBy((4.dp * s).coerceAtLeast(2.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (uiState.isLayersVisible) actions.onToggleLayers() else actions.onBack()
                },
                modifier = Modifier.size(iconButtonSize)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(iconSize)
                )
            }
            Text(
                text = "Edit Photo",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * s
                ),
                modifier = Modifier.padding(start = (8.dp * s).coerceAtLeast(4.dp))
            )
            Spacer(modifier = Modifier.weight(1f))
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(
                    onClick = { actions.onToggleLayers() },
                    modifier = Modifier.size(iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = "Layers",
                        modifier = Modifier.size(iconSize),
                        tint = if (uiState.isLayersVisible) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onPrimary
                    )
                }
                IconButton(
                    onClick = { actions.onUndo() },
                    enabled = uiState.historyIndex > 0,
                    modifier = Modifier.size(iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        modifier = Modifier.size(iconSize),
                        tint = if (uiState.historyIndex > 0) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                    )
                }
                IconButton(
                    onClick = { actions.onRedo() },
                    enabled = uiState.historyIndex < uiState.history.lastIndex,
                    modifier = Modifier.size(iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        modifier = Modifier.size(iconSize),
                        tint = if (uiState.historyIndex < uiState.history.lastIndex) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                    )
                }
                IconButton(
                    onClick = { actions.onRequestSave() },
                    modifier = Modifier.size(iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Save",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}
