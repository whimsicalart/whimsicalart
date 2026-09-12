package codes.pepper.whimsicalart.feature.stickers.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import coil.compose.rememberAsyncImagePainter
import codes.pepper.whimsicalart.core.designsystem.ColourPicker
import codes.pepper.whimsicalart.core.designsystem.ColourPickerPresets
import codes.pepper.whimsicalart.core.designsystem.ColourSwatch
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.feature.stickers.domain.Sticker
import codes.pepper.whimsicalart.feature.stickers.domain.StickerCategory
import codes.pepper.whimsicalart.feature.stickers.domain.StickerLineStyle
import codes.pepper.whimsicalart.feature.stickers.domain.StickerPlacement
import codes.pepper.whimsicalart.feature.stickers.domain.galleryStickerAspect
import kotlin.math.roundToInt

/**
 * The default line colour for a sticker outline when none has been picked
 * (report: "make the default line colour black"). Shared by the picker swatch
 * and the render fallbacks in EditorScreen/BitmapRenderer so the picker and the
 * canvas always agree on an unpicked line.
 */
const val DEFAULT_LINE_COLOUR: Int = 0xFF000000.toInt()

/**
 * Picker + edit controls for the sticker tool. The EMOJI category is the emoji
 * entry point (launches the Android emoji popup); every other category shows the
 * bundled vector stickers. As the LAST picker entry a **Gallery** option launches
 * the phone's photo picker (`PickVisualMedia`) so any image from the device
 * gallery is picked and becomes a sticker (non-custom-colour — its own pixels
 * are the art). When a placement is selected the controls expose an opacity
 * slider, a rotation slider, a fill colour wheel and a **Line** section (style /
 * thickness / colour — both the fill and line controls show for colour-enabled
 * stickers only) and a Remove button.
 */
@Composable
fun StickerPickerPanel(
    selectedCategory: StickerCategory,
    onCategorySelected: (StickerCategory) -> Unit,
    stickers: List<Sticker>,
    onStickerSelected: (Sticker) -> Unit,
    selectedPlacement: StickerPlacement?,
    selectedSticker: Sticker?,
    onOpacityChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onTintChange: (Int?) -> Unit,
    onLineStyleChange: (StickerLineStyle) -> Unit,
    onLineThicknessChange: (Float) -> Unit,
    onLineColourChange: (Int?) -> Unit,
    onRemove: () -> Unit,
    onEmojiPicked: (String) -> Unit,
    onGalleryPicked: (String, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            takePersistableUriPermission(context, it)
            // Carry the picked image's intrinsic ratio (bounds-only read) so the
            // gallery sticker's on-canvas box keeps the photo's proportion.
            val aspect = galleryStickerAspect(context.contentResolver, it)
            onGalleryPicked(it.toString(), aspect)
        }
    }
    // Devices/frameworks without the photo picker fall back to the classic
    // ACTION_PICK gallery intent.
    val galleryFallback = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        uri?.let {
            takePersistableUriPermission(context, it)
            val aspect = galleryStickerAspect(context.contentResolver, it)
            onGalleryPicked(it.toString(), aspect)
        }
    }
    val openGallery: () -> Unit = {
        try {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (e: Exception) {
            if (e is ActivityNotFoundException || e is SecurityException) {
                galleryFallback.launch(
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                        type = "image/*"
                    }
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val s = LocalUiScale.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp * s, vertical = 8.dp * s),
            horizontalArrangement = Arrangement.spacedBy(8.dp * s)
        ) {
            StickerCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = {
                        Text(
                            text = category.name.lowercase().replaceFirstChar { it.uppercase() },
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
            // The Gallery entry is always the LAST picker option (spec:
            // effects_reference.md → Stickers → "Controls").
            FilterChip(
                selected = false,
                onClick = openGallery,
                label = {
                    Text(
                        text = "Gallery",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = MaterialTheme.typography.labelMedium.fontSize * s
                        )
                    )
                },
                leadingIcon = {
                    Text(
                        text = "🖼️",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * s
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp * s, vertical = 8.dp * s),
            horizontalArrangement = Arrangement.spacedBy(12.dp * s)
        ) {
            if (selectedCategory == StickerCategory.EMOJI) {
                EmojiEntry(onClick = { showEmojiPicker = true })
            } else {
                stickers.forEach { sticker ->
                    StickerCell(
                        sticker = sticker,
                        onClick = { onStickerSelected(sticker) }
                    )
                }
            }
        }

        val placement = selectedPlacement
        if (placement != null) {
            StickerControls(
                placement = placement,
                customColor = selectedSticker?.customColor == true,
                onOpacityChange = onOpacityChange,
                onRotationChange = onRotationChange,
                onTintChange = onTintChange,
                onLineStyleChange = onLineStyleChange,
                onLineThicknessChange = onLineThicknessChange,
                onLineColourChange = onLineColourChange,
                onRemove = onRemove
            )
        }
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            onDismiss = { showEmojiPicker = false },
            onEmojiPicked = { emoji ->
                showEmojiPicker = false
                onEmojiPicked(emoji)
            }
        )
    }
}

@Composable
private fun EmojiEntry(onClick: () -> Unit) {
    val s = LocalUiScale.current
    Column(
        modifier = Modifier
            .width(72.dp * s)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp * s)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Text(
                text = "😀",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * s
                )
            )
        }

        Text(
            text = "Emoji",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = MaterialTheme.typography.labelSmall.fontSize * s
            ),
            modifier = Modifier.padding(top = 4.dp * s),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StickerCell(
    sticker: Sticker,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalUiScale.current
    Column(
        modifier = modifier
            .width(72.dp * s)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = rememberAsyncImagePainter(sticker.drawableRes),
            contentDescription = sticker.name,
            contentScale = ContentScale.Crop,
            // Show a shape's DEFAULT colour in the picker (matches what a fresh
            // placement renders as, since no tint means the default).
            colorFilter = sticker.defaultTintColor?.let { ColorFilter.tint(Color(it)) },
            modifier = Modifier
                .size(64.dp * s)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
        )

        Text(
            text = sticker.name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = MaterialTheme.typography.labelSmall.fontSize * s
            ),
            modifier = Modifier.padding(top = 4.dp * s),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StickerControls(
    placement: StickerPlacement,
    customColor: Boolean,
    onOpacityChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onTintChange: (Int?) -> Unit,
    onLineStyleChange: (StickerLineStyle) -> Unit,
    onLineThicknessChange: (Float) -> Unit,
    onLineColourChange: (Int?) -> Unit,
    onRemove: () -> Unit
) {
    val s = LocalUiScale.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * s, vertical = 8.dp * s)
    ) {
        OpacitySlider(value = placement.opacity, onChange = onOpacityChange)
        RotationSlider(value = placement.rotation, onChange = onRotationChange)
        if (customColor) {
            TintSelector(current = placement.tintColor, onSelect = onTintChange)
            LineLayer(
                style = placement.lineStyle,
                onStyleChange = onLineStyleChange,
                thickness = placement.lineThickness,
                onThicknessChange = onLineThicknessChange,
                colour = placement.lineColor,
                onColourChange = onLineColourChange
            )
        }
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp * s),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text(
                text = "Remove sticker",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                )
            )
        }
    }
}

@Composable
private fun OpacitySlider(value: Float, onChange: (Float) -> Unit) {
    val s = LocalUiScale.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Opacity ${(value * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * s
            )
        )
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.padding(horizontal = 8.dp * s)
        )
    }
}

@Composable
private fun RotationSlider(value: Float, onChange: (Float) -> Unit) {
    val s = LocalUiScale.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Rotation ${value.toInt()}°",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * s
            )
        )
        Slider(
            // The stored rotation is always wrapped into [0°, 360°) (spec
            // "Rotation wraps at 360°"): a full turn flips the slider back to
            // 0° and continues, mirroring the pinch-twist live.
            value = value.coerceIn(0f, 360f),
            onValueChange = onChange,
            valueRange = 0f..360f,
            modifier = Modifier.padding(horizontal = 8.dp * s)
        )
    }
}

/**
 * The colour wheel for custom-colour stickers; picking "None" keeps the source colours.
 * ONE horizontal row: a leading "None" (keep-source) swatch then the shared preset row
 * with the full-picker rainbow cell — no more stacked swatch + picker lines.
 */
@Composable
private fun TintSelector(current: Int?, onSelect: (Int?) -> Unit) {
    ColourPicker(
        label = "Fill colour",
        selected = Color(current ?: ColourPickerPresets.first()),
        onColorChange = { c -> onSelect(c.toArgb()) },
        leading = {
            ColourSwatch(
                color = Color.Transparent,
                selected = current == null,
                onClick = { onSelect(null) }
            )
        }
    )
}

/**
 * The **Line** section (colour-enabled stickers only, spec:
 * effects_reference.md → *Stickers* → *Line*): the line style chip row
 * (None / Solid / Dashed / Dotted — a **None** style draws nothing), and — only
 * while a style other than None is selected — the line thickness slider (0–20%
 * of the sticker's shorter side, stored as a fraction) and the line colour
 * picker. The line colour defaults to **black** when the stored colour is null.
 */
@Composable
private fun LineLayer(
    style: StickerLineStyle,
    onStyleChange: (StickerLineStyle) -> Unit,
    thickness: Float,
    onThicknessChange: (Float) -> Unit,
    colour: Int?,
    onColourChange: (Int?) -> Unit
) {
    val s = LocalUiScale.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Line",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * s
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp * s)
        ) {
            StickerLineStyle.entries.forEach { candidate ->
                val label = candidate.name.lowercase().replaceFirstChar { it.uppercase() }
                FilterChip(
                    selected = style == candidate,
                    onClick = { onStyleChange(candidate) },
                    label = {
                        Text(
                            text = label,
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

        // A None line draws nothing, so its thickness/colour controls stay hidden
        // until a style is actually selected (report: "make the line controls
        // (thickness and colour) hidden when none is selected").
        if (style != StickerLineStyle.NONE) {
            val thicknessPercent = (thickness * 100f).coerceIn(0f, 20f)
            Text(
                text = "Line thickness ${thicknessPercent.roundToInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = MaterialTheme.typography.labelMedium.fontSize * s
                )
            )
            Slider(
                value = thicknessPercent,
                onValueChange = { onThicknessChange(it / 100f) },
                valueRange = 0f..20f,
                modifier = Modifier.padding(horizontal = 8.dp * s)
            )

            ColourPicker(
                label = "Line colour",
                selected = Color(colour ?: DEFAULT_LINE_COLOUR),
                onColorChange = { c -> onColourChange(c.toArgb()) },
                leading = {
                    ColourSwatch(
                        color = Color.Transparent,
                        selected = colour == null,
                        onClick = { onColourChange(null) }
                    )
                }
            )
        }
    }
}

@Composable
private fun EmojiPickerDialog(
    onDismiss: () -> Unit,
    onEmojiPicked: (String) -> Unit
) {
    val context = LocalContext.current
    val s = LocalUiScale.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            }
        },
        text = {
            AndroidView(
                factory = { ctx ->
                    EmojiPickerView(ctx).apply {
                        setOnEmojiPickedListener { emojiItem ->
                            onEmojiPicked(emojiItem.emoji)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp * s)
            )
        }
    )
}

/**
 * Keeps read access to a picked photo's content URI across process restarts /
 * re-edit after relaunch (the photo picker grants access until the activity is
 * finished unless the permission is persisted). Best-effort — a provider that
 * does not support persisting simply returns and the in-session sticker still
 * works.
 */
private fun takePersistableUriPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}