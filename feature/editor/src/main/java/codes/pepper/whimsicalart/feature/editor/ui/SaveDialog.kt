package codes.pepper.whimsicalart.feature.editor.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import codes.pepper.whimsicalart.core.common.PreferencesManager
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.feature.editor.domain.ImageFormat
import codes.pepper.whimsicalart.feature.editor.domain.ImageQuality
import codes.pepper.whimsicalart.feature.editor.domain.Resolution
import codes.pepper.whimsicalart.feature.editor.domain.SaveConfig

@Composable
fun SaveDialog(
    onDismiss: () -> Unit,
    onSave: (SaveConfig) -> Unit,
    onShare: (SaveConfig) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    var format by remember { mutableStateOf(ImageFormat.JPEG) }
    var quality by remember { mutableStateOf(ImageQuality.HIGH) }
    var resolution by remember { mutableStateOf(Resolution.ORIGINAL) }
    var album by remember {
        val rememberedDir = prefs.saveDirectoryUri
        mutableStateOf(
            if (rememberedDir.isNotEmpty()) {
                directoryDisplayName(context, rememberedDir) ?: "WhimsicalArt"
            } else {
                "WhimsicalArt"
            }
        )
    }
    var directoryUri by remember { mutableStateOf(prefs.saveDirectoryUri.ifEmpty { null }?.let(Uri::parse)) }

    val directoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist the grant so a later save in a fresh process can still write.
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            album = directoryDisplayName(context, uri.toString()) ?: "Picked folder"
            directoryUri = uri
            prefs.saveDirectoryUri = uri.toString()
        }
    }

    val s = LocalUiScale.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save Photo",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * s
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp * s)) {
                ChipRow(
                    label = "Format",
                    options = listOf("JPEG", "PNG"),
                    selected = format.name,
                    onSelect = {
                        format = if (it == "JPEG") ImageFormat.JPEG else ImageFormat.PNG
                    }
                )
                ChipRow(
                    label = "Quality",
                    options = ImageQuality.entries.map { it.name },
                    selected = quality.name,
                    onSelect = { quality = ImageQuality.valueOf(it) }
                )
                ChipRow(
                    label = "Resolution",
                    options = Resolution.entries.map { it.label },
                    selected = resolution.label,
                    onSelect = { label -> resolution = Resolution.entries.first { it.label == label } }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp * s)
                ) {
                    OutlinedTextField(
                        value = album,
                        onValueChange = {
                            album = it
                            // Typing a folder name manually opts out of the picked tree.
                            if (directoryUri != null) {
                                directoryUri = null
                                prefs.saveDirectoryUri = ""
                            }
                        },
                        label = {
                            Text(
                                "Album / folder",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
                                )
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { directoryPicker.launch(null) }) {
                        Text(
                            "Browse",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            val btnStyle = MaterialTheme.typography.labelLarge.copy(
                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
            )
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onShare(config(format, quality, resolution, album)) }) {
                    Text("Share", style = btnStyle)
                }
                TextButton(onClick = { onSave(config(format, quality, resolution, album, directoryUri)) }) {
                    Text("Save", style = btnStyle)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            }
        }
    )
}

@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val s = LocalUiScale.current
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * s
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Horizontally scrollable so the Quality/Resolution rows stay one line on
        // a narrow window (split-screen / scaled display) instead of wrapping or
        // clipping. Same treatment as the text-tool font row (TextEditorPanel.kt).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp * s),
            horizontalArrangement = Arrangement.spacedBy(8.dp * s)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = MaterialTheme.typography.labelMedium.fontSize * s
                        )
                    )
                },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.heightIn((32.dp * s).coerceAtLeast(28.dp))
                )
            }
        }
    }
}

private fun config(
    format: ImageFormat,
    quality: ImageQuality,
    resolution: Resolution,
    album: String,
    directory: Uri? = null
): SaveConfig {
    val folder = album.trim().ifEmpty { "WhimsicalArt" }
    return SaveConfig(
        format = format,
        quality = quality,
        resolution = resolution,
        album = folder,
        directory = directory
    )
}

/**
 * Best-effort display name for a SAF tree: prefer the provider's `DISPLAY_NAME`
 * (a DocumentsProvider query — null when the provider is busy or the authority
 * is gone), falling back to the tree document id's final segment coerced to a
 * human label (e.g. `primary:Download` → "Download").
 */
private fun directoryDisplayName(context: android.content.Context, treeUriString: String): String? {
    val treeUri = Uri.parse(treeUriString)
    val fromDocument = runCatching {
        DocumentFile.fromTreeUri(context, treeUri)?.name?.takeIf { it.isNotBlank() }
    }.getOrNull()
    if (fromDocument != null) return fromDocument

    return runCatching {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        Uri.decode(id.substringAfterLast('/').substringAfterLast(':'))
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}