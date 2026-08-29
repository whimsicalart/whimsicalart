# WhimsicalArt API Overview

This document summarizes the primary public types exposed by each module. It is
intended as a developer reference rather than a full KDoc listing.

## Package conventions

Packages follow `com.whimsicalart.<module>`. Domain types live under
`...<module>.domain`, ViewModels under `...<module>.ui.viewmodel`, and UI
components under `...<module>.ui`.

## core:common

### `object BitmapPool`

Cooperative pool for reusable `Bitmap`s of a fixed output size (see
`core/common/.../BitmapPool.kt`).

- `get(width: Int, height: Int): Bitmap` — returns a blank, cleared bitmap of
  the requested size that the caller must fully redraw.
- `put(bitmap: Bitmap)` — returns a bitmap to the pool for reuse.
- `clear()` — recycles pooled bitmaps to release native memory.

### `object PerformanceUtils`

- `suspend fun <T> measurePerformance(traceName: String, block: suspend () -> T): T` —
  runs `block` on `Dispatchers.Default` inside a `Trace` section.
- `fun optimizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap` —
  scales a bitmap down if it exceeds the given bounds.

### `object AccessibilityUtils`

- `@Composable fun isAccessibilityEnabled(): Boolean`
- `@Composable fun isPreviewMode(): Boolean`
- `fun getContentDescription(toolName: String, state: String? = null): String`

## feature:editor

### `EditorViewModel` (`ui.viewmodel`)

- Exposes `val uiState: StateFlow<EditorUiState>`.
- `setImageUri(uri: Uri)`, `setBitmaps(original, edited)`
- Tool selection: `selectTool(tool: EditTool?)`
- Adjustments: `updateBrightness/Contrast/Saturation/Sharpness/Exposure/
  Shadows/Highlights/Temperature/Tint/Vignette(value: Float)`
- Transform: `rotateLeft()`, `rotateRight()`, `flipHorizontal()`,
  `flipVertical()`
- Crop: `updateCrop(rect: Rect)`, `applyCrop()`
- History: `undo()`, `redo()`
- Save/share: `saveImage(config: SaveConfig, onResult: (Boolean) -> Unit)`,
  `shareImage(config: SaveConfig)`
- Comparing: `startComparing()`, `stopComparing()`, `resetAdjustments()`

`EditorUiState` carries `imageUri`, `originalBitmap`, `editedBitmap`,
`isProcessing`, `isSaving`, `error`, `selectedTool`, all adjustment floats,
`rotation`, `flipHorizontal/Vertical`, `cropRect`, `isComparing`, `history`,
and `historyIndex`.

### `enum class EditTool`

`CROP, ROTATE, FLIP, BRIGHTNESS, CONTRAST, SATURATION, SHARPEN, EXPOSURE,
SHADOWS, HIGHLIGHTS, TEMPERATURE, TINT, VIGNETTE, STICKERS, TEXT, MOSAIC,
BLUR_BRUSH, PEN`

### `enum class ImageFormat` / `enum class ImageQuality` / `enum class Resolution`

Save/export settings used by `ImageSaver`.

- `ImageFormat`: `JPEG`, `PNG`
- `ImageQuality`: `ORIGINAL(100)`, `HIGH(85)`, `MEDIUM(60)`, `LOW(30)`
- `Resolution`: `ORIGINAL(0)`, `HIGH(2560)`, `MEDIUM(1920)`, `LOW(1280)`

### `data class SaveConfig`

Default `SaveConfig(format = JPEG, quality = HIGH, resolution = ORIGINAL,
album = "WhimsicalArt")`.

### `class ImageSaver`

- `saveImage(bitmap, config, filename: String? = null): Uri?` — saves via
  MediaStore (Q+) or external storage and returns the new content URI.
- `shareImage(bitmap, config, context)` — writes a cache file and launches the
  share sheet through a FileProvider.

### `data class Image`

`(uri: Uri, width: Int, height: Int)`.

## feature:filters

### `data class Filter`

`(id, name, category, shaderCode, defaultParams, intensity, previewColorMatrix)`.
`shaderCode` is GLSL ES 3.0 source rendered by the GPU filter pipeline.

### `enum class FilterCategory`

`BASIC, ARTISTIC, PORTRAIT, LANDSCAPE, VINTAGE, MONOCHROME`

### `object FilterPresets`

- `filters: List<Filter>`
- `getFilterById(id: String): Filter?`
- `getFiltersByCategory(category: FilterCategory): List<Filter>`

## feature:stickers

### `enum class StickerCategory`

Emoji, Nature, Food, Animals, Objects, Decoration.

### `data class Sticker` / `StickerPlacement`

`StickerPresets` exposes `stickers`, `getStickersByCategory(category)`, and
`getStickerById(id)`. Placements store position, scale, rotation, opacity, and
flip state.

### `StickersViewModel`

Exposes `uiState: StateFlow<StickersUiState>` with `selectCategory`,
`placeSticker`, `moveSticker`, `scaleSticker`, `rotateSticker`,
`flipSticker`, `setOpacity`, `removeSticker`, `clearAllStickers`,
`selectSticker`.

## feature:gallery

### `GalleryViewModel`

Exposes photo grid state, selection, folder grouping, and multi-select.

## feature:beauty

### `BeautyViewModel`

Exposes beauty state and triggers ML Kit face detection, auto-beauty, manual
brushes, face reshaping, and makeup.

## Represented overlay ViewModels (editor sub-tools)

- `TextEditorViewModel` (`ui.text`) — text overlays.
- `MosaicViewModel` (`ui.mosaic`) — mosaic strokes.
- `BlurViewModel` (`ui.blur`) — blur brush strokes.
- `PenViewModel` (`ui.pen`) — freehand strokes and layers.
