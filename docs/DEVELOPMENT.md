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
- **Image processing:** Custom GPU-accelerated engine (OpenGL ES 3.0 + GLSL)
- **Build:** Gradle Kotlin DSL with a version catalog
- **SDK:** minSdk 26, targetSdk 34, compileSdk 35

## Module Layout

The project is a multi-module Gradle build:

```
whimsicalart/
├── app/                    # Application entry point, navigation graph, theme
├── core/
│   ├── common/             # Shared utilities (BitmapPool, PerformanceUtils,
│   │                       #   AccessibilityUtils, PreferencesManager, AnimationUtils)
│   ├── domain/             # Reserved for shared domain models
│   ├── data/               # Reserved for shared data layer
│   └── designsystem/       # Design system components and theme
├── feature/
│   ├── editor/             # Core photo editor + text/mosaic/blur/pen tools
│   ├── filters/            # Filter presets and GPU filter pipeline
│   ├── beauty/             # ML Kit face detection + beauty tools
│   ├── gallery/            # Photo gallery and selection
│   ├── collage/            # Collage maker
│   ├── stickers/           # Sticker picker and placement
│   ├── camera/             # Camera capture
│   └── settings/           # App settings
└── build-logic/            # Convention plugins for app/library/Hilt/Room
```

Most domain models live co-located in their owning feature module (for
example `feature/stickers/.../domain/Sticker.kt` and
`feature/editor/.../domain/ImageSaver.kt`) rather than in a shared `core`
module. `core/common` holds cross-cutting Android utilities.

## Architecture

Each feature follows MVVM + Clean Architecture:

- **`ui/`** — Composables and `ViewModel`s. ViewModels extend
  `androidx.lifecycle.ViewModel`, are `@HiltViewModel`, and expose a single
  immutable `StateFlow<XxxUiState>` via `uiState`.
- **`domain/`** — Models, enums, presets, and use-case-style processors.
- **`data/`** — Repositories and data sources (when needed).
- **`di/`** — Hilt modules (when needed).

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
- Prefer sealed interfaces for multi-state UI state.
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
- Use descriptive test names starting with `should` (backtick-quoted).

## Further Reading

- [Architecture and contribution guidelines](CONTRIBUTING.md)
- [Public API overview](API.md)
- [User guide](USER_GUIDE.md)
