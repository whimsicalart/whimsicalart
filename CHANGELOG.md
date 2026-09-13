# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-09-13

### Added

- Photo gallery with photo grid, multi-select, folder grouping, and MediaStore-based selection
- Core editor with pinch-to-zoom, pan, compare (hold to view original), and undo/redo
- Crop tool with aspect ratios (Free, 1:1, 4:3, 16:9, 3:2, 9:16), rotate (90° steps), and flip (horizontal/vertical)
- Adjustment tools: brightness, contrast, saturation, exposure, shadows, highlights, temperature, tint, vignette, sharpening
- Save & share with quality slider, JPEG/PNG export, resolution selection (Original/High/Medium/Low), and MediaStore integration
- Filter library with preset color grades, style tone-maps, and lens filters (CPU, matrix/analytic based), with single- and multi-select and intensity control
- Beauty tools with on-device MediaPipe face detection, edge-aware auto beauty pipeline, manual brushes, face reshaping (mesh deformation), and makeup tools
- Stickers with categories, drag/pinch-to-scale/rotate, opacity, and flip
- Text overlays with font selection, color picker, styles, shadows, stroke, alignment, and background shapes
- Mosaic tool with pixel, blur, and custom-pattern brushes plus eraser and opacity control
- Manual blur brush and magic pen with brush types (solid, glow, neon, rainbow) and layer management
- Collage maker with grid layouts (2–9 photos), template library, free-form drag-to-place slots, borders, and background color
- Background blur (bokeh) simulation, object removal with content-aware fill, clone stamp, and healing brush
- Frames with adjustable border width, corner radius, color, and shadows
- Adaptive launcher icon

### Changed

- Bitmap pooling across the render pipeline to reduce native allocations (core/common `BitmapPool`)
- Loading indicators added to editor, collage, and beauty save flows
- App version centralized in `gradle.properties` (`VERSION_CODE`, `VERSION_NAME`) and exposed via generated `BuildConfig`

### Added (Release Tooling)

- Release signing configured through gitignored `keystore.properties`, with automatic fallback to debug signing
- Manually-triggered release workflow (`.github/workflows/release.yml`, via **Actions → Run workflow**) that resolves the version (explicit input, or last tag +1 patch, or `0.1.0` when none), builds, verifies, creates the tag + GitHub release with auto-generated notes, and uploads the signed APK
- Version management and release automation documented in TODO.md

### Documentation

- Store listing description, community guidelines, contribution guidelines, and security policy added in earlier milestones
- F-Droid release requirements documented (build without signing config, version from git tags, no non-free dependencies)
- TODO.md tracks the 8-phase development roadmap