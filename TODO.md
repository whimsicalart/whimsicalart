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
- [x] Create `feature/camera/` (camera capture)
- [x] Create `feature/stickers/` (sticker support)
- [x] Create `feature/collage/` (collage maker)
- [x] Create `feature/settings/` (app settings)

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
- [x] Shadow effects

## Phase 6: Advanced Features ✅

### 6.1 Background Blur (Bokeh)
- [x] Portrait mode simulation
- [x] Edge-aware blur
- [x] Blur intensity control
- [x] Shape bokeh (circle, hexagon, heart)
- [x] Manual brush for blur areas

### 6.2 Object Removal
- [x] Brush selection tool
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
- [x] Onboarding flow

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
- [x] Content rating questionnaire prepared (see docs/PLAY_STORE_PREPARATION.md)

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

## Status

**Current Phase**: Phase 8 - Release
**Progress**: 74%
**Last Updated**: Feature graphic asset generated; content rating questionnaire prepared; unit + integration tests green

## Notes

- All filters use GPU-accelerated OpenGL ES rendering
- Beauty tools use standard computer vision techniques (skin detection, Gaussian blur)
- Architecture follows MVVM + Clean Architecture pattern
- UI built with Jetpack Compose and Material 3
- Minimum API: Android 8.0 (API 26)
- Repository integration tests target the persistence layer (PreferencesManager). The MediaStore-backed GalleryRepositoryImpl requires a connected device/emulator and is excluded from the local `testDebugUnitTest` suite.
- UI tests (7.4) require a device/emulator: Compose `createComposeRule` cannot resolve `ComponentActivity` under Robolectric in a library module, so UI/screenshot/device-matrix tests must run as instrumented `androidTest` on a connected device.
