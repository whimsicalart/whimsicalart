# WhimsicalArt Developer Documentation

This guide describes WhimsicalArt's architecture, module layout, and the
conventions to follow when contributing code. It is aimed at developers
working on the codebase.

## Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM with Clean Architecture
- **Dependency Injection:** Hilt
- **Database:** Room
- **Image loading:** Coil
- **Image processing:** Editor effects fold through an ordered, reversible
  **effect stack** (`docs/effect_stack.md`) rendered by the CPU `BitmapRenderer`
  / `CascadeRenderer`. Filters run on the CPU: adjustments use
  `EditorColorMatrix` colour matrices applied per effect by
  `BitmapRenderer.applyMatrixPass`, and style/lens filters use the analytic
  per-pixel `StyleFilterProcessor` tone-maps. There is **no OpenGL / GLSL / GPU
  pipeline** — `feature/filters` holds only `FilterPresets.kt`, and no effect
  compiles or runs GLSL shaders or 3D LUTs.
- **Build:** Gradle Kotlin DSL with a version catalog
- **SDK:** minSdk 26, targetSdk 34, compileSdk 35

## Module Layout

The project is a multi-module Gradle build:

```
whimsicalart/
├── app/                    # Application entry point, navigation graph, theme
├── core/
│   ├── common/             # Shared utilities (BitmapPool, PreferencesManager)
│   ├── domain/             # Shared domain models (Rotation)
│   ├── data/               # Reserved for shared data layer (empty placeholder)
│   └── designsystem/       # Design system components (ColourMath, ColourPicker);
│   │                       #   the app theme lives in app/.../ui/theme/
├── feature/
│   ├── editor/             # Core photo editor (reversible effect stack) + text/mosaic/blur/pen tools
│   ├── filters/            # Filter presets and CPU style/lens filter processing
│   ├── beauty/             # MediaPipe FaceMesh + skin/hair segmentation, beauty tools, shared geometry context
│   ├── gallery/            # Photo gallery and selection
│   ├── collage/            # Collage maker
│   ├── stickers/           # Sticker picker and placement
│   ├── camera/             # Camera capture
│   └── settings/           # App settings
└── build-logic/            # Convention plugin scaffolding (not wired —
                            #   modules apply version-catalog aliases directly)
```

Most domain models live co-located in their owning feature module (for
example `feature/stickers/.../domain/Sticker.kt` and
`feature/editor/.../domain/ImageSaver.kt`) rather than in a shared `core`
module. `core/common` holds cross-cutting Android utilities.

## Architecture

Each feature follows MVVM + Clean Architecture:

- **`ui/`** — Composables and `ViewModel`s. ViewModels extend
  `androidx.lifecycle.ViewModel`, are `@HiltViewModel`, and expose a single
  immutable `StateFlow<XxxUiState>` via `uiState` (some editor tool ViewModels
  name theirs `XxxEditorState`).
- **`domain/`** — Models, enums, presets, and use-case-style processors.
- **`data/`** — Repositories and data sources (when needed).
- **`di/`** — Hilt modules (when needed).

Two exceptions, by design, are lightweight: `feature/beauty` exposes **no
ViewModel** — it is a stateless control panel + shared `BeautyParams` value type
driven by the editor's `EditorViewModel` — and `feature/camera` is a single
stateless capture composable (`CameraScreen`) with no domain/data/di. `feature/gallery`
is the only feature module that carries the complete `ui/domain/data/di` structure;
the others keep only the packages their size warrants (for example the editor has
`ui` + `domain` + `data` and no `di/`).

### State management

ViewModels expose an immutable `StateFlow` of an `XxxUiState` data class and
mutate a private `MutableStateFlow` with `.copy(...)`:

```kotlin
private val _uiState = MutableStateFlow(EditorUiState())
val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

fun updateBrightness(value: Float) {
    _uiState.value = _uiState.value.copy(brightness = value)
}
```

### Repository pattern

Repositories are constructor-injected via Hilt and return domain models or
`Result`/nullable values for fallible operations.

## Conventions

- One public class per file; file name matches the class name.
- Classes in PascalCase, functions/variables in camelCase, constants in
  SCREAMING_SNAKE_CASE, composables in descriptive PascalCase.
- Prefer immutable `data class` UI state mutated with `.copy(...)` behind a
  private `MutableStateFlow` (use sealed classes/interfaces only for genuinely
  distinct multi-state variants).
- Avoid `!!`; use safe calls or early returns.
- Use `Dispatchers.IO` for disk/network and `Dispatchers.Default` for
  CPU-intensive work. Never block the main thread.
- Keep composables stateless where practical; use `remember` for state
  preservation.

## Performance

- Use Coil (`rememberAsyncImagePainter`) for memory-efficient image loading.
- Reuse bitmaps with `BitmapPool` (see `core/common/BitmapPool.kt`) instead
  of allocating fresh `Bitmap`s per frame.
- Profile with Android Profiler; enable GPU rendering in developer options;
  use LeakCanary to catch leaks.

## Security

- Validate all user inputs.
- Use HTTPS for network calls.
- Do not log sensitive information.
- Keep secrets out of the repository; release signing uses a local
  `keystore.properties` that is git-ignored.

## Building

```bash
# Build a debug APK
./gradlew assembleDebug

# Run the full unit test suite
./gradlew testDebugUnitTest

# Lint
./gradlew lint
```

> **Note:** `gradlew` requires JDK 17 and the Android SDK (compileSdk 35).
> Create or update `local.properties` with `sdk.dir` if the SDK is not on the
> default path.

## Testing

- **Unit tests**: plain JUnit 4 in `src/test`. The `EditorViewModelTest` uses
  Robolectric to obtain an Android `Context` (see
  `feature/editor/src/test/.../EditorViewModelTest.kt`).
- Module `build.gradle.kts` files declare `testImplementation(libs.junit)` so
  their `src/test` suites can run.
- Use descriptive test names: backtick-quoted sentence descriptions (e.g.
  `` `rotation by 90 degrees swaps dimensions` ``) are the prevailing style;
  some UI/layout tests use `should`-prefixed camelCase.

## Further Reading

- [Effect Stack — reversible / non-destructive editing](effect_stack.md)
- [Editor effects reference — per-effect catalog & interaction rules](effects_reference.md)
- [Architecture and contribution guidelines](../CONTRIBUTING.md)
- [Public API overview](api.md)
- [User guide](user_guide.md)
