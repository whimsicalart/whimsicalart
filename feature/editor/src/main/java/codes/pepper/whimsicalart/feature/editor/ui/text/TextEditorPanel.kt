package codes.pepper.whimsicalart.feature.editor.ui.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.ColourPicker
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.core.domain.wrapDegrees
import codes.pepper.whimsicalart.feature.editor.domain.TextAlignment
import codes.pepper.whimsicalart.feature.editor.domain.TextBackgroundShape
import codes.pepper.whimsicalart.feature.editor.domain.TextFont
import codes.pepper.whimsicalart.feature.editor.domain.TextStyleFlag
import codes.pepper.whimsicalart.feature.editor.ui.ControlLabel
import codes.pepper.whimsicalart.feature.editor.ui.OnOffChipToggle
import codes.pepper.whimsicalart.feature.editor.ui.chipColors

/**
 * Full Text-tool control panel (spec: docs/effects_reference.md → *Text*): the
 * multi-line field feeds a live item while in add mode or re-edits the selected
 * item in place; alignment, font family, size, style toggles, opacity, shadow
 * (angle/distance/colour), outline (size/colour/blur) and background shape
 * (colour/opacity) mirror the parameterised `TextItem`.
 */
@Composable
fun TextEditorPanel(
    state: TextEditorUiState,
    onTextChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit,
    onTextOpacityChange: (Float) -> Unit,
    onFontChange: (TextFont) -> Unit,
    onStyleToggle: (TextStyleFlag) -> Unit,
    onAlignmentChange: (TextAlignment) -> Unit,
    onRotationChange: (Float) -> Unit,
    onShadowToggle: () -> Unit,
    onShadowAngleChange: (Float) -> Unit,
    onShadowDistanceChange: (Float) -> Unit,
    onShadowColorChange: (Color) -> Unit,
    onOutlineToggle: () -> Unit,
    onOutlineSizeChange: (Float) -> Unit,
    onOutlineColorChange: (Color) -> Unit,
    onOutlineBlurChange: (Float) -> Unit,
    onBackgroundShapeChange: (TextBackgroundShape) -> Unit,
    onBackgroundColorChange: (Color) -> Unit,
    onBackgroundOpacityChange: (Float) -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onRecognizeText: () -> Unit,
    recognizingText: Boolean,
    ocrAvailable: Boolean = true,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * LocalUiScale.current, vertical = 8.dp * LocalUiScale.current)
    ) {
        val ts = LocalUiScale.current
        OutlinedTextField(
            value = state.currentText,
            onValueChange = onTextChange,
            label = {
                Text(
                    text = "Text",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = MaterialTheme.typography.labelMedium.fontSize * ts
                    )
                )
            },
            minLines = 1,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * ts
            )
        )

        ControlLabel("Alignment")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp * ts)
        ) {
            TextEditorIconButton(
                selected = state.currentAlignment == TextAlignment.LEFT,
                onClick = { onAlignmentChange(TextAlignment.LEFT) },
                description = "Align left"
            ) {
                Icon(Icons.Filled.FormatAlignLeft, contentDescription = null)
            }
            TextEditorIconButton(
                selected = state.currentAlignment == TextAlignment.CENTER,
                onClick = { onAlignmentChange(TextAlignment.CENTER) },
                description = "Align centre"
            ) {
                Icon(Icons.Filled.FormatAlignCenter, contentDescription = null)
            }
            TextEditorIconButton(
                selected = state.currentAlignment == TextAlignment.RIGHT,
                onClick = { onAlignmentChange(TextAlignment.RIGHT) },
                description = "Align right"
            ) {
                Icon(Icons.Filled.FormatAlignRight, contentDescription = null)
            }
            TextEditorIconButton(
                selected = state.currentAlignment == TextAlignment.JUSTIFIED,
                onClick = { onAlignmentChange(TextAlignment.JUSTIFIED) },
                description = "Justify"
            ) {
                Icon(Icons.Filled.FormatAlignJustify, contentDescription = null)
            }
        }

        ControlLabel("Font")
        // Horizontally scrollable so all five fonts stay reachable on a narrow
        // window (split-screen / scaled display) instead of clipping out.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp * ts)
        ) {
            TextFont.entries.forEach { font ->
                FilterChip(
                    selected = state.currentFont == font,
                    onClick = { onFontChange(font) },
                    label = {
                        Text(
                            text = font.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = MaterialTheme.typography.labelMedium.fontSize * ts
                            )
                        )
                    },
                    colors = chipColors(),
                    modifier = Modifier.heightIn((32.dp * ts).coerceAtLeast(28.dp))
                )
            }
        }

        SliderRow(
            label = "Size",
            value = state.currentFontSize,
            range = 10f..72f,
            display = { "${it.toInt()}dp" },
            onValueChange = onFontSizeChange
        )

        ControlLabel("Colour")
        ColourPicker(
            label = "",
            selected = state.effectiveTextColor,
            onColorChange = onColorChange
        )
        SliderRow(
            label = "Opacity",
            value = state.textOpacity,
            range = 0f..1f,
            display = { "${(it * 100).toInt()}%" },
            onValueChange = onTextOpacityChange
        )
        SliderRow(
            label = "Rotation",
            value = state.currentRotation,
            range = 0f..360f,
            display = { "${wrapDegrees(it).toInt()}°" },
            onValueChange = onRotationChange
        )

        ControlLabel("Style")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp * ts)
        ) {
            TextStyleFlag.entries.forEach { flag ->
                TextStyleToggleButton(
                    flag = flag,
                    selected = flag in state.currentStyle,
                    onToggle = onStyleToggle
                )
            }
        }

        ShadowControls(
            enabled = state.hasShadow,
            angle = state.shadowAngle,
            distance = state.shadowDistance,
            color = state.shadowColor,
            onToggle = onShadowToggle,
            onAngleChange = onShadowAngleChange,
            onDistanceChange = onShadowDistanceChange,
            onColorChange = onShadowColorChange
        )

        OutlineControls(
            enabled = state.hasOutline,
            size = state.outlineSize,
            blur = state.outlineBlur,
            color = state.outlineColor,
            onToggle = onOutlineToggle,
            onSizeChange = onOutlineSizeChange,
            onBlurChange = onOutlineBlurChange,
            onColorChange = onOutlineColorChange
        )

        ControlLabel("Background")
        // Horizontally scrollable so all six shapes stay reachable on a narrow
        // window (split-screen / scaled display) instead of wrapping "Blur" onto
        // its own line.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("backgroundShapeRow"),
            horizontalArrangement = Arrangement.spacedBy(4.dp * ts)
        ) {
            TextBackgroundShape.entries.forEach { shape ->
                FilterChip(
                    selected = state.currentBackgroundShape == shape,
                    onClick = { onBackgroundShapeChange(shape) },
                    label = {
                        Text(
                            text = shape.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = MaterialTheme.typography.labelMedium.fontSize * ts
                            )
                        )
                    },
                    colors = chipColors(),
                    modifier = Modifier.heightIn((32.dp * ts).coerceAtLeast(28.dp))
                )
            }
        }
        if (state.currentBackgroundShape == TextBackgroundShape.BLUR) {
            SliderRow(
                label = "Blur",
                value = state.currentBackgroundBlur,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                onValueChange = onBackgroundBlurChange
            )
        } else {
            ColourPicker(
                label = "",
                selected = state.effectiveBackgroundColor,
                onColorChange = onBackgroundColorChange
            )
            SliderRow(
                label = "Bg opacity",
                value = state.backgroundOpacity,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                onValueChange = onBackgroundOpacityChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            val actionStyle = MaterialTheme.typography.labelLarge.copy(
                fontSize = MaterialTheme.typography.labelLarge.fontSize * ts
            )
            TextButton(
                enabled = state.selectedOverlayId != null,
                onClick = onRemove
            ) { Text("Remove", style = actionStyle) }
            TextButton(onClick = onAdd) { Text("Add", style = actionStyle) }
        }

        // OCR lives at the bottom of the panel — reachable in every text-editor
        // state (no first-state condition) whenever the on-device OCR runtime is
        // available; on devices/emulators where the engine can't initialize
        // (e.g. an ABI without native model libs) the action is hidden so the
        // rest of the text tool stays fully usable.
        if (ocrAvailable) {
            TextButton(
                enabled = !recognizingText,
                onClick = onRecognizeText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (recognizingText) "Recognizing…" else "Recognize Text",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * ts
                    )
                )
            }
        }
    }
}

@Composable
private fun ShadowControls(
    enabled: Boolean,
    angle: Float,
    distance: Float,
    color: Color,
    onToggle: () -> Unit,
    onAngleChange: (Float) -> Unit,
    onDistanceChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit
) {
    OnOffChipToggle("Shadow", enabled, onToggle)
    if (enabled) {
        SliderRow("Angle", angle, 0f..360f, { "${it.toInt()}°" }, onAngleChange)
        SliderRow("Distance", distance, 0f..24f, { "${it.toInt()}dp" }, onDistanceChange)
        ColourPicker(label = "", selected = color, onColorChange = onColorChange)
    }
}

@Composable
private fun OutlineControls(
    enabled: Boolean,
    size: Float,
    blur: Float,
    color: Color,
    onToggle: () -> Unit,
    onSizeChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit
) {
    OnOffChipToggle("Outline", enabled, onToggle)
    if (enabled) {
        SliderRow("Size", size, 0f..16f, { "${it.toInt()}dp" }, onSizeChange)
        ControlLabel("Blur (0% = solid)")
        Slider(
            value = blur,
            onValueChange = onBlurChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
        ColourPicker(label = "", selected = color, onColorChange = onColorChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    val s = LocalUiScale.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(end = 8.dp * s),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
            )
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp * s)
        )
        Text(
            text = display(value),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
            )
        )
    }
}

/**
 * Standard text-editor icon button: a square multi-selection toggle used for
 * ALIGNMENT (left/centre/right/justify) and STYLE (B / I / U / S). A selected
 * button fills with the primary colour; unselected shows a flat surface with an
 * outline. The content inherits the [MaterialTheme] content colour (icons and
 * letter glyphs both honour it), reflecting each item's state.
 */
@Composable
private fun TextEditorIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val s = LocalUiScale.current
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size((40.dp * s).coerceAtLeast(36.dp))
            .clip(shape)
            .background(container)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/** The classic text-editor style glyph button (B / I / U / S), still combinable. */
@Composable
private fun TextStyleToggleButton(
    flag: TextStyleFlag,
    selected: Boolean,
    onToggle: (TextStyleFlag) -> Unit,
    modifier: Modifier = Modifier
) {
    val letter = when (flag) {
        TextStyleFlag.BOLD -> "B"
        TextStyleFlag.ITALIC -> "I"
        TextStyleFlag.UNDERLINE -> "U"
        TextStyleFlag.STRIKETHROUGH -> "S"
    }
    TextEditorIconButton(
        selected = selected,
        onClick = { onToggle(flag) },
        description = flag.name.lowercase().replaceFirstChar { it.uppercase() },
        modifier = modifier
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = MaterialTheme.typography.labelLarge.fontSize * LocalUiScale.current,
                fontWeight = if (flag == TextStyleFlag.BOLD) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (flag == TextStyleFlag.ITALIC) FontStyle.Italic else FontStyle.Normal,
                textDecoration = when (flag) {
                    TextStyleFlag.UNDERLINE -> TextDecoration.Underline
                    TextStyleFlag.STRIKETHROUGH -> TextDecoration.LineThrough
                    else -> TextDecoration.None
                }
            )
        )
    }
}

private val TextFont.displayName: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

private val TextBackgroundShape.displayName: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }