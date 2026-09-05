# WhimsicalArt API Overview

This document summarizes the primary public types exposed by each module. It is
intended as a developer reference rather than a full KDoc listing.

## Package conventions

Packages follow `codes.pepper.whimsicalart.<module>`. Domain types live under
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

Backed by Hilt; on construction it receives a shared `BeautyGeometryContext`
(defaulting to a no-op generator so it is unit-testable without ML).

- Exposes `val uiState: StateFlow<EditorUiState>`.
- Loading: `setImageUri(uri)`, `setBitmaps(original, edited)`,
  `setBeautyBitmap(bitmap?)`
- Tool selection: `selectTool(tool: EditTool?)`
- Adjustments: `updateBrightness/Contrast/Saturation/Sharpness/Exposure/
  Shadows/Highlights/Temperature/Tint/Vignette(value)` plus
  `updateSkinDenoise(value)`
- Filter / style / enhance / background: `setSelectedFilterId(id?)`,
  `setStyleFilter(filter?)`, `setEnabledLensFilters` (via UI state),
  `enableEnhance`, `updateBackgroundMode(mode)`,
  `updateBackgroundBlurRadius(radius)`, `updateBackgroundShape(shape)`,
  `setCustomBackground(uri)`, `setCustomBackgroundBitmap`
- Transform: `rotateLeft()`, `rotateRight()`, `flipHorizontal()`,
  `flipVertical()`
- Crop / frame: `updateCrop(rect: Rect)`, `applyCrop()`,
  `updateFrame(frameId: String?)`
- Beauty stack: `upsertBeautyEffect(effect: BeautyStackEffect)`,
  `removeBeautyEffect(layerKey: String)`,
  `setBeautyEffects(effects: List<BeautyStackEffect>)`
- Effect stack / layers: `setEffectStack(snapshot)`,
  `syncLayerState(stack, fingerprints)`, `moveEffect(from, to)`,
  `removeStackEffect(id)`, `selectStackEffect(effectId)`, `mergeLayers(bundle)`,
  `toggleLayers()`
- History: `undo()`, `redo()`
- Render: `renderStackWithBeautyGeometry(context, input, effects)` (the
  beauty-aware fold shared by the final render and layer thumbnails)
- Save/share: `saveImage(config: SaveConfig, bundle: EditorRenderBundle,
  onResult: (Boolean) -> Unit)`, `shareImage(config: SaveConfig,
  bundle: EditorRenderBundle)`
- Comparing: `startComparing()`, `stopComparing()`, `resetAdjustments()`

`EditorUiState` carries `imageUri`, `originalBitmap`, `editedBitmap`,
`isProcessing`, `isSaving`, `error`, `selectedTool`, all
adjustment floats (incl. `skinDenoise`), `rotation`, `flipHorizontal/Vertical`,
`cropRect`, `selectedFrameId`, `isComparing`, `backgroundMode`/`blurRadius`/
`shape`, `subjectMask`, `enhanceEnabled`, `styleFilter`, `selectedFilterId`,
`enabledLensFilterIds`, `effectStack`, `beautyEffects`, `hasMerged`,
`isLayersVisible`, `layerThumbnails`, `layerFingerprints`, and `history` /
`historyIndex` (`List<StackDocument>` for stack-aware undo/redo).

### `enum class EditTool`

`BEAUTY, CROP, TRANSFORM, FILTERS, SKIN_DENOISE, BRIGHTNESS, CONTRAST,
SATURATION, SHARPEN, EXPOSURE, SHADOWS, HIGHLIGHTS, TEMPERATURE, TINT,
VIGNETTE, ENHANCE, STICKERS, TEXT, FRAMES, MOSAIC, BLUR_BRUSH, PEN, BACKGROUND,
OBJECT_REMOVAL`

> Note: there is no standalone `ROTATE`/`FLIP` tool — rotation and flips are
> provided by the `TRANSFORM` tool as their own `TransformEffect` layer. It is
> fully independent of `CropEffect`; like every layer, both can be placed
> anywhere in the stack, in any relative order.

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

Exposes beauty state and drives auto-beauty, manual brushes, face reshaping, and
makeup. Face geometry comes from the on-device **MediaPipe Tasks** pipeline
(`mediapipe-tasks-vision`): **FaceLandmarker** (478-point FaceMesh) plus real
**skin / hair segmentation** — this supersedes the older ML Kit face detection
for the beauty path.

### `BeautyGeometryContext` / `DefaultBeautyGeometryContext` (`domain`)

The editor's **shared, lazily-resolved** beauty-geometry context. Exposes
`markStale()`, `flatten()`, `geometryFor(image)` (lazy generate-or-reuse) and
`lastGeometry()`. `DefaultBeautyGeometryContext(generator)` backs it with a
**single last geometry plus a single stale flag** (no history list — old
geometry is freed by layer regeneration / JVM GC).

### `BeautyProcessor` (`domain`)

The beauty render pipeline (open, Hilt-provided — injected, not a singleton
object). Applies smoothing, skin tone, spot/wrinkle removal, teeth whitening,
reshape, and the makeup tools.

### `SkinDenoiseProcessor` (`domain`)

Wavelet skin-denoise used by the editor's `SkinDenoiseEffect` (a main-toolbar
tool, not a beauty sub-tool).

### `TeethMaskProcessor` (`domain`)

Teeth-whitening alpha mask (per-pixel luminance gated by an analytic lens
aperture), Robolectric-safe.

## Represented overlay ViewModels (editor sub-tools)

- `TextEditorViewModel` (`ui.text`) — text overlays.
- `MosaicViewModel` (`ui.mosaic`) — mosaic strokes.
- `BlurViewModel` (`ui.blur`) — blur brush strokes.
- `PenViewModel` (`ui.pen`) — freehand strokes and layers.
