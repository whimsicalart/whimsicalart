# WhimsicalArt API Overview

This document summarizes the primary public types exposed by each module. It is
intended as a developer reference rather than a full KDoc listing.

## Package conventions

Packages follow `codes.pepper.whimsicalart.<module>`. Domain types live under
`...<module>.domain`, ViewModels under `...<module>.ui.viewmodel`, and UI
components under `...<module>.ui`.

## core:common

### `object BitmapPool`

Cooperative pool for reusable `Bitmap`s, keyed by size and config (see
`core/common/.../BitmapPool.kt`).

- `get(width: Int, height: Int, config: Bitmap.Config): Bitmap?` — returns a
  reusable, cleared bitmap of the requested size and config that the caller
  must fully redraw, or `null` when no matching entry is cached (allocate
  fresh in that case).
- `put(bitmap: Bitmap)` — returns a bitmap to the pool for reuse.
- `clear()` — recycles pooled bitmaps to release native memory.

### `object PreferencesManager`

Small persisted boolean/numeric/string store used for gallery viewing
preferences and settings (see `core/common/.../PreferencesManager.kt`).

## feature:editor

### `EditorViewModel` (`ui.viewmodel`)

`@HiltViewModel`; on construction it receives a shared `BeautyGeometryContext`
(the editor's geometry context for **all** geometry-requiring tools — beauty
sub-tools and the background tool's subject mask; defaulting to a no-op generator
so it is unit-testable without ML), and it is the single owner of both the
per-sub-tool beauty state and the effect stack it derives from it.

- Exposes `val uiState: StateFlow<EditorUiState>`.
- Loading: `setImageUri(uri)`, `setBitmaps(original, edited)`
- Tool selection: `selectTool(tool: EditTool?)`
- Adjustments: `updateBrightness/Contrast/Saturation/Sharpness/Exposure/
  Shadows/Highlights/Temperature/Tint/Vignette(value)` plus
  `updateSkinDenoise(value)`
- Filter / style / enhance / background: `setSelectedFilterId(id?)`,
  `setStyleFilter(filter?)`, `toggleLensFilter(filterId)`,
  `clearLensFilters()`, `toggleEnhance()`, `updateBackgroundMode(mode)`,
  `updateBackgroundBlurRadius(radius)`, `updateBackgroundShape(shape)`,
  `segmentSubject()`, `clearSubjectMask()`, `setCustomBackground(uri)`,
  `loadBackground(res)`, `clearBackground()`
- Transform: `rotateLeft()`, `rotateRight()`, `flipHorizontal()`,
  `flipVertical()`, `setRotation(rotation: Float)`
- Crop / frame: `updateCrop(rect: Rect)`, `applyCrop()`, `clearCrop()`,
  `updateFrame(frameId: String?)`, `clearTransform()`
- Beauty stack: `upsertBeautyEffect(effect: BeautyStackEffect)`,
  `removeBeautyEffect(layerKey: String)`, `setBeautyEffects(effects)`,
  `clearBeautyEffects()`, `selectBeautyTool(tool: BeautyTool?)`,
  `updateBeautyParams(transform)`, `setBeautyParams(params)`
- Text / OCR: `recognizeText(): List<OcrOverlayFragment>` (imports detected
  lines as editable `TextOverlay`s)
- Effect stack / layers: `setEffectStack(snapshot)`,
  `syncLayerState(stack, fingerprints)`, `moveEffect(from, to)`,
  `removeStackEffect(id)`, `selectStackEffect(effectId)`,
  `mergeLayers(bundle)`, `toggleLayers()`, `refreshLayerPreviews(bundle)`,
  `refreshCompositePreview(...)`, `clearCompositePreview()`
- Viewport: `setViewBox(widthPx, heightPx)`, `setZoom(zoom)`
- History: `undo()`, `redo()`
- Render: `renderStackWithBeautyGeometry(context, input, effects)` (the
  beauty-aware fold shared by the final render and layer thumbnails)
- Save/share: `saveImage(config: SaveConfig, bundle: EditorRenderBundle,
  onResult: (Boolean) -> Unit)`, `shareImage(config: SaveConfig,
  bundle: EditorRenderBundle)`
- Comparing: `startComparing()`, `stopComparing()`, `resetAdjustments()`

> This list summarizes the primary public calls; the full surface (including
> the many `update*` / `refresh*` helpers) lives in `EditorViewModel.kt`.

`EditorUiState` carries `imageUri`, `originalBitmap`, `editedBitmap`,
`isProcessing`, `isSaving`, `error`, `selectedTool`, all
adjustment floats (incl. `skinDenoise`), `rotation`, `flipHorizontal/Vertical`,
`cropRect`, per-tier frame state (`innerFrameStyle`/`outerFrameStyle` +
thickness/colour/edge-size/lines/line-distance/line-thickness for each tier),
`isComparing`, `backgroundMode`/`blurRadius`/
`shape`, `subjectMask`, `enhanceEnabled`, `styleFilter`, `selectedFilterId`,
`enabledLensFilterIds`, `effectStack`, `beauty` (the per-sub-tool
`BeautyParams`, from which the beauty layers are derived into `effectStack`) +
`selectedBeautyTool`, `hasMerged`, `isLayersVisible`, `layerThumbnails`,
`layerFingerprints`, and `history` / `historyIndex`
(`List<StackDocument>` for stack-aware undo/redo; each entry carries a
`fine_grained`/`coarse_grained` operation kind and is bounded by
`FINE_GRAINED_LIMIT` / `COARSE_GRAINED_LIMIT` per kind — see
`docs/effect_stack.md` → *Undo / redo*).

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
- `Resolution`: `ORIGINAL(0)`, `HIGH(2560)`, `MEDIUM(1920)`, `LOW(1280)`, plus
  the upscaling entries `UPSCALE_2X(-2)` / `UPSCALE_4X(-4)` (with `isUpscale`,
  `upscaleFactor` and a friendly `label`; see `ImageSaver.kt`)

### `data class SaveConfig`

Default `SaveConfig(format = JPEG, quality = HIGH, resolution = ORIGINAL,
album = "WhimsicalArt", directory = null)`.

- `album` — the album/folder name; written through MediaStore's
  `RELATIVE_PATH` (`Pictures/<album>`) on Q+ or the corresponding external
  storage folder before Q.
- `directory` — an optional **persisted directory target** (`Uri`) chosen from
  the system folder picker (SAF `ACTION_OPEN_DOCUMENT_TREE`). When set, the
  image is written into that directory instead of the album path, and the
  choice is remembered for the next save.

### `class ImageSaver`

- `saveImage(bitmap, config, filename: String? = null): Uri?` — saves via
  MediaStore (Q+), external storage, or the **SAF directory picked in the save
  dialog** (when `config.directory != null`) and returns the new content URI.
- `shareImage(bitmap, config, context)` — writes a cache file and launches the
  share sheet through a FileProvider. The share URI is served by the app's own
  FileProvider, declared at authority **`${applicationId}.fileprovider`** and
  exposing the cache directory (`<cache-path path="."/>`), so the authority
  always matches the running build's `packageName` (including the `.debug`
  suffix).

## feature:filters

### `data class Filter`

`(id, name, category, shaderCode, defaultParams, intensity, previewColorMatrix)`.
Filters are **CPU** grades: each preset carries a `previewColorMatrix`
(`android.graphics.ColorMatrix`) that `BitmapRenderer` applies to the image.
The `shaderCode` field is a vestigial `IDENTITY_SHADER` GLSL placeholder
retained for legacy compatibility; nothing compiles or runs it (there is no GPU
pipeline). `FilterPresets.concatFilterMatrices(colorFilterId,
enabledLensFilterIds)` composes the single-selected color filter with all
enabled lens filters into one matrix — **excluding** the real-pass lens filters
(`FilterPresets.realPassLensFilterIds`: `red_eye`, `star`, `anamorphic`,
`astro`), which are applied as per-pixel CPU passes by
`codes.pepper.whimsicalart.feature.editor.domain.filter.LensFilterProcessor`
inside `FilterEffect`/`StyleEffect` (and `BitmapRenderer.render`), never as a
matrix approximation.

### `enum class FilterCategory`

`BASIC, ARTISTIC, PORTRAIT, LANDSCAPE, VINTAGE, MONOCHROME`

### `object FilterPresets`

- `filters: List<Filter>`
- `colorFilters: List<Filter>` — single-select color grading presets
- `lensFilters: List<Filter>` — multi-select simulated lens filters
- `realPassLensFilterIds: Set<String>` — lens ids rendered as pixel passes
- `isRealPassLensFilter(id: String): Boolean`
- `isLensFilter(id: String): Boolean`
- `getFilterById(id: String): Filter?`
- `getFiltersByCategory(category: FilterCategory): List<Filter>`

## feature:stickers

### `enum class StickerCategory`

EMOJI, SHAPES, DECORATION, NATURE, FOOD, ANIMALS, OBJECTS.

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

The gallery spec — screen structure, sort & group-by **viewing preferences**
(persisted, with defaults), tag filters, multi-select, permission and
shared-image behaviour — lives in [`docs/gallery.md`](./gallery.md).

## feature:beauty

Beauty state and the beauty sub-tools are **owned by the editor**
(`EditorViewModel` and its `InlineBeautyPanel`) — there is no standalone
`BeautyViewModel`. Face geometry comes from the on-device **MediaPipe Tasks**
pipeline (`mediapipe-tasks-vision`): **FaceLandmarker** (478-point FaceMesh)
plus real **skin / hair segmentation** — this supersedes the older ML Kit face
detection for the beauty path.

### `BeautyGeometryContext` / `DefaultBeautyGeometryContext` (`domain`)

The editor's **shared, lazily-resolved** geometry context — used by **every
geometry-requiring tool** (beauty sub-tools AND the background tool, whose subject
mask is its geometry). Exposes
`markStale()`, `flatten()`, `geometryFor(image, hint)` (lazy generate-or-reuse)
and `lastGeometry()`. `DefaultBeautyGeometryContext(generator)` backs it with a
**single retained geometry base bitmap** plus a **single last geometry** (each ML
field — face detection/mesh, skin, hair, background subject mask — resolved **on
demand per the caller's needed-field set**) plus a **single stale flag** (no
history list — old geometry is freed by layer regeneration / JVM GC; the
retained bitmap is recycled on stale/flatten/budget, never while a fold holds
it).

### `BeautyProcessor` (`domain`)

The beauty render pipeline (open, Hilt-provided — injected, not a singleton
object). Applies smoothing, skin tone, spot/wrinkle removal, teeth whitening,
reshape, and the makeup tools.

### `SkinDenoiseProcessor` (`domain`)

Wavelet skin-denoise used by the editor's `SkinDenoiseEffect` (a main-toolbar
tool, not a beauty sub-tool).

### `TeethMaskProcessor` (`domain`)

Teeth-whitening alpha mask (per-pixel luminance gate bounded by the mouth
aperture), Robolectric-safe. The aperture polygon is the MediaPipe Face Mesh
**inner-lip ring** — the actual mouth opening where the teeth show — resampled
to a node count proportional to the aperture's pixel footprint on the working
bitmap (dense for wide smiles, cheap for small mouths), falling back to the
analytic lens between the mouth corners and the lip bands when no inner-lip
contour is available.

## Represented overlay ViewModels (editor sub-tools)

- `TextEditorViewModel` (`ui.text`) — text overlays.
- `MosaicViewModel` (`ui.mosaic`) — mosaic strokes.
- `BlurViewModel` (`ui.blur`) — blur brush strokes.
- `PenViewModel` (`ui.pen`) — freehand strokes and layers.
