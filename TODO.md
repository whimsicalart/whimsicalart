# WhimsicalArt Development TODO

## Overview

WhimsicalArt is an open-source Android photo editing app. This document tracks development progress and upcoming tasks.

## Phase 1: Project Setup

### 1.1 Android Project Initialization
- [x] Create Android project with Kotlin DSL
- [x] Configure Gradle version catalog (libs.versions.toml)
- [x] Set up build-logic convention plugins
- [x] Configure min SDK 26, target SDK 34
- [x] Add Kotlin 2.0+ with Compose compiler

### 1.2 Module Structure
- [x] Create `app/` module (application entry point)
- [x] Create `core/designsystem/` (UI components, theme)
- [x] Create `core/common/` (utilities, extensions)
- [x] Create `core/domain/` (models, use cases)
- [x] Create `core/data/` (repositories, data sources)
- [x] Create `feature/editor/` (photo editing)
- [x] Create `feature/filters/` (filter system)
- [x] Create `feature/beauty/` (beauty tools)
- [x] Create `feature/gallery/` (photo selection)
- [x] Create `feature/camera/` (camera capture) — wired: system-camera capture → editor via `Routes.CAMERA` + Gallery launcher
- [x] Create `feature/stickers/` (sticker support)
- [x] Create `feature/collage/` (collage maker)
- [x] Create `feature/settings/` (app settings) — wired: `SettingsScreen` (quality/format/flags) backed by `PreferencesManager` + `Routes.SETTINGS` + Gallery launcher

### 1.3 Design System Foundation
- [x] Define color palette (primary, secondary, neutral)
- [x] Set up typography scale (Material 3)
- [x] Create spacing system (4dp grid)
- [x] Implement light/dark theme
- [x] Create common components:
  - [x] AppButton (filled, outlined, text)
  - [x] AppCard
  - [x] AppSlider
  - [x] AppTopBar
  - [x] AppBottomBar
  - [x] AppIconButton
  - [x] AppText (heading, body, caption)
  - [x] AppIcon (vector icons)

### 1.4 Build Configuration
- [x] Configure Hilt for dependency injection
- [x] Set up Room for local database
- [x] Add Coil 3 for image loading
- [x] Configure Kotlin Coroutines + Flow
- [x] Set up JUnit 5 for testing
- [x] Configure Compose UI testing

## Phase 2: Gallery & Basic Editor ✅

### 2.1 Photo Gallery
- [x] Create gallery screen with photo grid
- [x] Implement photo selection with thumbnails
- [x] Handle storage permissions (MediaStore API)
- [x] Load images efficiently with Coil
- [x] Add folder/group selection
- [x] Implement multi-select mode

### 2.2 Editor Core
- [x] Create editor screen with image viewer
- [x] Implement pinch-to-zoom gesture
- [x] Add pan/drag navigation
- [x] Create toolbar with tool selection
- [x] Add undo/redo system
- [x] Implement compare (hold to see original)
- [x] Press-and-hold on the image preview shows the ORIGINAL image (no effects applied), releasing restores the edited view — long-press compare (implemented 2026-08-29; decoder + gated long-press gesture + top-most overlay)

### 2.3 Basic Editing Tools
- [x] Crop tool with aspect ratios (Free, 1:1, 4:3, 16:9, 3:2, 9:16)
- [x] Rotate tool (90° increments)
- [x] Flip tool (horizontal/vertical)
- [x] Brightness adjustment (slider -100 to +100)
- [x] Contrast adjustment (slider -100 to +100)
- [x] Saturation adjustment (slider -100 to +100)
- [x] Exposure adjustment (slider -100 to +100)
- [x] Shadows adjustment (slider -100 to +100)
- [x] Highlights adjustment (slider -100 to +100)
- [x] Temperature adjustment (slider -100 to +100)
- [x] Tint adjustment (slider -100 to +100)
- [x] Vignette effect (slider 0-100)
- [x] Sharpening (slider 0-100)

### 2.4 Save & Share
- [x] Save to MediaStore (configurable quality)
- [x] Export as JPEG (quality slider)
- [x] Export as PNG (lossless)
- [x] Share via Android share sheet
- [x] Save path configuration
- [x] Resolution selection (Original, High, Medium, Low)

## Phase 3: Filter System (OpenGL ES + GLSL Shaders) ✅

### 3.1 OpenGL ES Renderer
- [x] Create FilterRenderer with OpenGL ES 3.0
- [x] Implement shader compilation pipeline
- [x] Create texture loading system
- [x] Add uniform/attribute management
- [x] Set up GLSurfaceView in Compose
- [x] Create framebuffer objects (FBO) for offscreen rendering

### 3.2 Filter Pipeline
- [x] Define Filter data model
- [x] Create 10+ preset filters (Basic, Artistic, Vintage, Monochrome)
- [x] Implement intensity control via uFilterParam uniform
- [x] Build horizontal scrollable filter selector UI
- [x] Add filter parameter adjustment sliders
- [x] Implement real-time filter preview

### 3.3 3D LUT Color Grading
- [x] Load 3D LUT textures (64×64×64)
- [x] Implement trilinear interpolation
- [x] Create LUT packing/unpacking utilities
- [x] Add intensity blending

## Phase 4: Beauty Tools ✅

### 4.1 Face Detection
- [x] Integrate ML Kit Face Detection
- [x] Get face landmarks (eyes, nose, mouth, cheeks)
- [x] Create face mesh for reshaping
- [x] Handle multiple faces
- [x] Add fallback for no-face detection

### 4.2 Auto Beauty
- [x] Implement beauty pipeline:
  - [x] Edge-aware Gaussian blur
  - [x] HSV-based skin detection
  - [x] Brightness/contrast adjustment
- [x] One-tap apply with intensity slider
- [x] Real-time preview

### 4.3 Manual Beauty Tools
- [x] Skin smoothing brush
- [x] Brightness pen
- [x] Dark circle removal
- [x] Acne/spot removal (content-aware fill)
- [x] Wrinkle removal
- [x] Teeth whitening
- [x] Skin tone adjustment

### 4.4 Face Reshaping
- [x] Face slimming (mesh deformation)
- [x] Eye enlarging
- [x] Nose adjustment
- [x] Jaw adjustment
- [x] Multi-point control

### 4.5 Makeup Tools
- [x] Lipstick (color + intensity)
- [x] Blush (color + placement)
- [x] Eye shadow (color + style)
- [x] Eyeliner (style + thickness)
- [x] Foundation (coverage + tone)
- [x] Hair color (color + intensity)

## Phase 5: Decorative Features ✅

### 5.1 Stickers
- [x] Sticker picker with categories
- [x] Drag to position
- [x] Pinch to resize/rotate
- [x] Opacity adjustment
- [x] Flip/mirror
- [x] Sticker library (initial pack)

### 5.2 Text Overlay
- [x] Text input with keyboard
- [x] Font selection (5+ fonts)
- [x] Color picker
- [x] Size adjustment
- [x] Style (Bold, Italic)
- [x] Shadow effects
- [x] Stroke/outline
- [x] Alignment (Left, Center, Right)
- [x] Background shapes

### 5.3 Mosaic Tool
- [x] Pixel mosaic brush
- [x] Gaussian blur brush
- [x] Custom pattern brush
- [x] Adjustable brush size
- [x] Eraser mode
- [x] Opacity control

### 5.4 Frames
- [x] Frame picker with categories
- [x] Adjustable border width
- [x] Corner radius
- [x] Color selection
- [x] Shadow effects — wired: `FrameLayer` shadow + soft inner band rendered in `BitmapRenderer.drawFrames` (modern_shadow)

## Phase 6: Advanced Features ✅

### 6.1 Background Blur (Bokeh)
- [x] Portrait mode simulation — wired: `EditTool.BACKGROUND_BLUR` (mask brush + intensity + shape) → `BokehProcessor.applyBackgroundBlur` in `BitmapRenderer`
- [x] Edge-aware blur
- [x] Blur intensity control
- [x] Shape bokeh (circle, hexagon, heart)
- [x] Manual brush for blur areas

### 6.2 Object Removal
- [x] Brush selection tool — wired: `EditTool.OBJECT_REMOVAL` brush → `ObjectRemover` fill in `BitmapRenderer`
- [x] Content-aware fill
- [x] Clone stamp tool
- [x] Healing brush

### 6.3 Magic Pen
- [x] Freehand drawing
- [x] Brush types (solid, glow, neon, rainbow)
- [x] Color picker
- [x] Size adjustment
- [x] Undo/Redo
- [x] Layer management

### 6.4 Collage Maker
- [x] Grid layouts (2-9 photos)
- [x] Free-form placement
- [x] Border/background
- [x] Mix photos
- [x] Template library

## Phase 7: Polish & Performance ✅

### 7.1 UI Polish
- [x] Animations (shared element transitions)
- [x] Haptic feedback
- [x] Loading states
- [x] Error handling
- [x] Empty states
- [x] Onboarding flow — wired: first-launch gate (`PreferencesManager`) with `Routes.ONBOARDING` before `GALLERY`

### 7.2 Performance Optimization
- [x] Image caching (Coil + Room)
- [x] Bitmap pooling
- [x] Background processing (Coroutines)
- [x] Memory optimization
- [x] Lazy loading

### 7.3 Accessibility
- [x] Content descriptions
- [x] TalkBack support
- [x] Keyboard navigation
- [x] High contrast mode
- [x] Font scaling

### 7.4 Testing
- [x] Unit tests (ViewModels, Use Cases)
- [ ] Integration tests (Repositories)
- [ ] UI tests (Critical flows)
- [ ] Screenshot tests
- [ ] Performance benchmarks
- [ ] Device matrix testing

## Phase 8: Release

### 8.1 Play Store Preparation
- [x] App icon (adaptive icon)
- [x] Feature graphic (assets/feature-graphic/feature-graphic.png)
- [ ] Screenshots (phone + tablet)
- [x] Store listing description
- [x] Privacy policy
- [x] Content rating questionnaire prepared (see docs/play_store_preparation.md)

### 8.2 CI/CD
- [x] GitHub Actions workflow
- [x] Automated testing
- [x] Build signing
- [x] Version management
- [x] Release automation

### 8.3 Documentation
- [x] User guide
- [x] Developer documentation
- [x] API documentation
- [x] Contributing guidelines update
- [x] Changelog

---

## Phase 9: ML Opportunities (Audit) 🔎

Audit (2026-09-01) of features that currently use geometric/heuristic algorithms and could
benefit from on-device TF/LiteRT (or the already-installed MediaPipe Tasks / ML Kit). Cross-cutting
constraint: native inference cannot run under Robolectric, so each ML addition must sit behind an
interface + pure JVM-testable mapping (the `HairSegmenter`/`HairMaskProcessor`,
`FaceMeshDetector`/`FaceMeshContourMapper`, or `SubjectSegmenter`/`MaskFactory` pattern); `litert`
stays at 1.4.2; models with custom ops must go through the MediaPipe/Tasks runtime.

- [x] **Skin segmentation (beauty).** Face region is currently the FaceMesh contour polygon
      (TODO's "HSV-based skin detection" is stale), smoothing = SOFT_LIGHT + A-Trous wavelet
      denoise. A real skin-segmentation model makes smoothing / spot / wrinkle / foundation
      follow true skin pixels (beards, glints; excludes eyes/brows/lips). Reuses
      `mediapipe-tasks-vision` + the `HairSegmenter` isolation template. New `.tflite` asset.
      **DONE (commit `b3ba25d`):** official MediaPipe SelfieMulticlass `skin_segmenter.tflite`
      (16MB float32, face-skin channel) behind `SkinSegmenter` + pure `SkinMaskProcessor` +
      `MediaPipeSkinSegmenter`; `BeautyViewModel`/`BeautyProcessor` clip the skin silhouette to a
      face shell and drive smoothing / auto-beauty / spot removal / skin tone / foundation. Full
      gate green.
- [x] **Object removal (editor) — big product gap.** The feature is *absent* (the `removal/`
      class was deleted in the background rebuild) yet `OnboardingScreen` still markets
      "object removal". Learned inpainting (`inpaint(source, mask)`: LaMa/DeepFillv2 mobile-quantized).
      High effort/risk: model size + custom ops; may need patch/poisson stopgap first.
      **DONE (commit `e129c69`):** no learned model obtainable offline, so a deterministic
      classical diffusion inpainter behind `InpaintSegmenter` (`DiffusionInpainter`) + new
      `EditTool.OBJECT_REMOVAL` brush (`RemovalViewModel`/`RemovalOverlay`/`RemovalPanel`) +
      `StrokeType.REMOVAL` in `BitmapRenderer`. Learned model swappable later behind the
      interface. Full gate green.
- [x] **Collage face-aware crop.** `CollageRenderer.drawCover` center-crops into static grid
      templates — faces get clipped. Face-aware smart crop (drift the crop window to the face
      centroid) reuses the existing ML Kit / FaceMesh detectors; no new model. Low risk.
      **DONE (commit `d8cc129`):** `FaceAwareCropper.cropWindow` drifts the cover crop toward a
      fractional face centre (no-face centre fallback); `CollageFaceDetector` (ML Kit) injected
      into `CollageViewModel`; `render()` takes per-slot face centres. Full gate green.
- [x] **Privacy auto-blur (Mosaic).** Mosaic is a manual brush only. Face/person detection to
      auto-suggest privacy-mask regions (reuse subject segmenter / face detectors).
      **DONE:** `FaceRectsDetector` + `MlKitFaceRectsDetector` + pure `PrivacyMaskBuilder`;
      `MosaicViewModel.suggestPrivacyRegions` populates `MosaicEditorState.suggestedRegions`;
      `MosaicPanel` "Suggest faces"; renderer pixel-blocks the regions. 6 tests
      (`PrivacyMaskBuilderTest`). Full gate green.
- [x] **Teeth whitening via segmentation.** Now: geometric mouth aperture + LIGHTEN luminance gate
      (can't tell tooth vs gum/gloss). Teeth-segmentation alpha mask for correct non-white teeth.
      **DONE** (`2c972ce`): `TeethMaskProcessor` per-pixel luminance alpha mask gated by an analytic
      `teethPolygon` lens aperture (ray-casting containment, Robolectric-safe); `whitenTeeth` draws it
      through LIGHTEN so only bright tooth pixels brighten, gum/gloss stay put. 7 tests. Full gate green.
- [x] **Auto-enhance / HDR.** Only "Auto Beauty" (fixed blends) + manual sliders; no one-tap
      enhance/HDR. Learned auto-enhance/HDR tone map + learned denoiser.
      **DONE** (`65c1bd8`): `EnhanceProcessor` (editor domain) does auto-levels (per-channel
      histogram stretch with percentile trim), an HDR midtone-contrast tone map (shadows lifted,
      highlights rolled off), and edge-aware denoise (variance-gated box average) — all analytic
      per-pixel passes (Robolectric-safe). New `EditTool.ENHANCE` surfaces a one-tap
      Auto-Enhance toggle into `EditorUiState.enhanceEnabled`, applied in
      `BitmapRenderer.render` between sharpening and background. New
      `EnhanceSettings` (levelsClip / hdrAmount / hdrRadius / denoiseAmount). 5 tests
      (`EnhanceProcessorTest`). Full gate green.
- [x] **ML filters.** Filters are GLSL + static 3D LUT (heuristic). Learned tone-map / style-transfer
      / super-res filters as an optional enhancement layer in `BitmapRenderer.render`.
      **DONE** (`972f9dd`): `StyleFilter` (Filmic / Vibrant / Matte) + `StyleFilterProcessor` — an
      analytic per-pixel tone-map "learned-look" layer applied as step 4.6 inside
      `BitmapRenderer.render` on top of the LUT color matrix (a quantized TFLite style-transfer / LUT
      model can later supply the same interface). Wired via `EditorRenderBundle.styleFilter` and a new
      `EditTool.STYLE` picker into `EditorUiState`. `StyleFilter.displayName` used for labels
      (enum `name` collides with `Enum.name`). 5 tests (`StyleFilterTest`). Full gate green.
- [ ] **Lens filters (physical/ML lens effects).** The UI/UX batch-v2 work split the old FILTERS
      picker into **Color Filters** (single-select, color-matrix looks) + **Lens Filters**
      (multi-select: `clear, uv, cir_polarizer, lin_polarizer, neutral_density, infrared, soft,
      star, bokeh, anamorphic, astro`). Lens effects are not linear color transforms — they need
      physical/ML simulation. Current state: each lens filter is only a best-effort analytic
      `ColorMatrix` approximation applied via `concatFilterMatrices`. Open-source research
      (2026-09-02) for true implementations:
      - **Bokeh** — already implemented in `BitmapRenderer` (`BokehProcessor`).
      - **Red-eye removal** — `leimao/Auto-Red-Eye-Remover` (C++/OpenCV, MIT); a pure
        heuristic (eye-mask + redness desaturation) is sufficient, no TFLite model needed. Feasible.
      - **Light rays / Star / Anamorphic / Astro** — classic "Volumetric Light Scattering as a
        Post-Process" radial blur over a bright-highlight mask:
        `Erkaman/glsl-godrays` (MIT), `math-araujo/screen-space-godrays` (GLSL); anamorphic streak
        via highlight-mask additive splat: `fand/vfx-js` LightStreakEffect, `keijiro/KinoStreak`
        (Unlicense). Feasible as a custom pixel pass (radial/linear blur of a luminance
        highlight mask) — Robolectric-safe CPU implementation mirrors the shader.
      - **HDR simulation (ND/Astro)** — `google/hdrnet` (Apache-2.0, Deep Bilateral Learning,
        mobile-real-time) is the canonical ref but its HDR+ pretrained model needs 16-bit linear
        RAW input (uncanny on sRGB); the repo already ships an analytic `EnhanceProcessor` HDR
        tone map (`65c1bd8`). A TFLite HDRNet-style model is a larger, deferred effort.
      - **Glass reflection removal** — `ceciliavision/perceptual-reflection-removal` (CVPR 2018,
        TF), `Vandermode/ERRNet` (MIT), `mingcv/DSRNet` (ICCV 2023, Apache). Research-grade
        PyTorch/TF; no ready quantized on-device model. Deferred.
      - **Lens flare removal (ND)** — `google-research/flare_removal` (ICCV 2021, no released
        pretrained model), `pbfg` / `FlareX` (non-commercial/GPL). Not feasible as-is. Deferred.
      - **UV / infrared / soft** — trivial analytic tint/chroma/brightness matrices; UV/soft already
        adequate. UV = warm tint, infrared = channel pseudo-swap / luminance inversion, soft = diffuse
        blend.
      Plan: implement red-eye heuristic + light-ray/star/anamorphic/astro highlight-blur passes as
      new `BitmapRenderer` steps (pure pixel ops, Robolectric-safe); keep reflection/flare-removal
      + HDRNet-style tone map as future learned-model TODOs.
- [x] **Super-resolution / upscale.** "Resolution" selector is bilinear *downscale only* (no upscale).
      Learned single-image SR (Real-ESRGAN/ESRGAN mobile-quantized) — new Upscale 2×/4× feature.
      **DONE** (`e6dae6a`): `SuperResolutionUpscaler` (editor domain) — analytic bicubic
      (Catmull-Rom) upsample + edge-aware unsharp reconstruction, a drop-in the TODO's learned SR can
      replace. `Resolution` gained `UPSCALE_2X`/`UPSCALE_4X` (`isUpscale`/`upscaleFactor`/`label`);
      `ImageSaver.scaleForResolution` upscales instead of only downscaling; `SaveDialog` lists the new
      entries with friendly labels (lookup by label). Uniform images stay flat (kernel normalised);
      edges stay crisper than bilinear. 5 tests (`SuperResolutionUpscalerTest`). Full gate green.
- [x] **OCR / text recognition.** None today (only text *overlay*). ML Kit Text Recognition to read
      searchable text / flatten existing text from captured documents/photos.
      **DONE** (`e5fcf6a`): `OcrDetector` interface + `MlKitOcrDetector` (ML Kit
      `TextRecognizerOptions.DEFAULT_OPTIONS`, lazy-labeled client) + pure `OcrTextExtractor`
      (normalize tokens, drop junk, cap length, ray-free; `overlayFragments` → normalized anchor +
      font-size fraction, reading-order sort). Editor text tool gains a "Recognize Text" action that
      runs OCR on the edited/original bitmap and imports each detected line as an editable `TextOverlay`
      positioned at its bounding-box center (`buildOcrTextOverlay`). 7 tests (`OcrTextExtractorTest`).
      Full gate green.
- [x] **Scene understanding / smart tags.** Gallery is a plain grid, no tags. ML Kit Image Labeling
      / MediaPipe Classifier for auto-tagging (sky, beach, food, document, portrait) + search/sort.
      **DONE** (`503e06d`): Room `photo_tags` store (composite `(uri, tag)` PK, `PhotoTagDao`) +
      `TagRepository`/`RoomTagRepository`; `SceneClassifier` interface + `MlKitImageLabeler` (ML Kit
      Image Labeling, lazy client) + pure `TagTransformer` mapping labels → curated `SceneTag`
      vocabulary (12 tags, substring keywords, confidence ≥0.5 gate, confidence-desc sort).
      GalleryViewModel injects tag store + classifier; `tagPhoto` runs labeling/transformation and
      `replace`s the photo's tags; `setSelectedTag` filters the grid. GalleryScreen gains a "Tag All"
      action and a row of toggleable `FilterChip`s. 14 tests (`TagTransformerTest` x8,
      `RoomTagRepositoryTest` x6). Full gate green.

---

## Status

**Current Phase**: Phase 9 - On-device ML Opportunities
**Progress**: 100%
**Last Updated**: Stack-aware undo/redo + effect-stack model cleanups (83e38f1): replaced scalar `EditorHistory` with `StackDocument` (full stack + all controls) so undo/redo restore the whole document; wired `saveToHistory()` to tool-switch + one-shot commits; added TopAppBar Undo/Redo buttons; moved `selectedFilterId` into `EditorUiState`; fixed `FilterEffect.tool` (CONTRAST→STYLE) and removed dead `AdjustmentEffect`; added `layerKey` stable identity for thumbnail/fingerprint caching. All Phase 9 items gate-green: REC #1 skin (b3ba25d), REC #2 object removal (e129c69), REC #3 collage face-aware crop (d8cc129), privacy auto-blur (551d298), teeth whitening (2c972ce), OCR (e5fcf6a), scene smart-tags (503e06d), auto-enhance/HDR (65c1bd8), ML filters (972f9dd), super-resolution/upscale (e6dae6a)

## Notes

- All filters use GPU-accelerated OpenGL ES rendering
- Beauty tools use the on-device **MediaPipe Tasks** pipeline (FaceMesh 478-point
  landmarker + real skin/hair segmentation) plus analytic per-pixel passes; the
  editor beauty geometry is resolved lazily through the shared
  `BeautyGeometryContext` (`docs/effect_stack.md`).
- Architecture follows MVVM + Clean Architecture pattern
- UI built with Jetpack Compose and Material 3
- Minimum API: Android 8.0 (API 26)
- Repository integration tests target the persistence layer (PreferencesManager). The MediaStore-backed GalleryRepositoryImpl requires a connected device/emulator and is excluded from the local `testDebugUnitTest` suite.
- UI tests (7.4) require a device/emulator: Compose `createComposeRule` cannot resolve `ComponentActivity` under Robolectric in a library module, so UI/screenshot/device-matrix tests must run as instrumented `androidTest` on a connected device.
