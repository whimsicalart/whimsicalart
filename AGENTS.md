# WhimsicalArt - AI Agent Guidelines

## Project Overview

WhimsicalArt is an open-source Android photo editing application built with modern Android development practices. This document provides guidance for AI agents working on this codebase.

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Clean Architecture
- **Dependency Injection**: Hilt
- **Image Processing**: CPU effect-stack engine (BitmapRenderer / CascadeRenderer) — no GPU/GLSL pipeline
- **Build System**: Gradle with Kotlin DSL
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

## Project Structure

```
whimsicalart/
├── app/                    # Main application module (navigation, theme, onboarding)
├── core/                   # Core business logic
│   ├── domain/            # Shared domain models (Rotation)
│   ├── data/              # Reserved for shared data layer (empty placeholder)
│   ├── common/            # Shared utilities (BitmapPool, PreferencesManager)
│   └── designsystem/      # Design system components (ColourMath, ColourPicker)
├── feature/               # Feature modules
│   ├── editor/            # Core photo editor (reversible effect stack) + text/mosaic/blur/pen tools
│   ├── filters/           # Filter presets and CPU style/lens filter processing
│   ├── beauty/            # Beauty tools (MediaPipe FaceMesh + hair/skin segmentation)
│   ├── gallery/           # Photo gallery and selection
│   ├── collage/           # Collage maker
│   ├── stickers/          # Sticker picker and placement
│   ├── camera/            # Camera capture
│   └── settings/          # App settings
└── build-logic/           # Convention plugin scaffolding (not wired — modules apply version-catalog aliases directly)
```

## Code Style

### Kotlin

- Follow Kotlin coding conventions
- Use coroutines for asynchronous operations
- UI state is an immutable `data class` mutated with `.copy(...)` behind a private `MutableStateFlow`; use sealed classes/interfaces only for genuinely distinct multi-state variants
- Use extension functions for utility methods
- Avoid `!!` operator - use safe calls or early returns

### Compose

- Use Material 3 components
- Keep composables stateless where possible
- Use `remember` for state preservation
- Extract complex UI into separate composables
- Follow Compose naming conventions (PascalCase for composables)

### Architecture

- Single source of truth pattern
- Unidirectional data flow
- Repository pattern for data access
- Use cases for business logic
- ViewModels for UI state management

## Key Conventions

### File Organization

- One public class per file
- File name should match the class name
- Group related files in feature packages

### Naming

- Classes: PascalCase
- Functions/variables: camelCase
- Constants: SCREAMING_SNAKE_CASE
- Composables: PascalCase with descriptive names

### Testing

- Unit tests for domain logic
- Integration tests for data layer
- UI tests for critical user flows
- Use descriptive test names — backtick-quoted sentence descriptions are the prevailing style (some UI/layout tests use `should`-prefixed camelCase)

## Common Patterns

### State Management

```kotlin
// UI state is an immutable data class with flags, mutated behind a
// private MutableStateFlow (use sealed interfaces only for genuinely
// distinct multi-state variants)
data class EditorUiState(
    val brightness: Float = 0f,
    val isSaving: Boolean = false,
    val selectedTool: EditTool? = null
)
```

### Repository Pattern

```kotlin
interface ImageRepository {
    suspend fun loadImage(uri: Uri): Result<Image>
    suspend fun saveImage(image: Image, format: Format): Result<Uri>
}
```

### Feature Module Structure

Each feature module should contain:
- `ui/` - Composables and ViewModels
- `domain/` - Use cases and domain models
- `data/` - Data sources if needed
- `di/` - Hilt modules

## Performance Guidelines

- Use `Dispatchers.IO` for disk operations
- Use `Dispatchers.Default` for CPU-intensive work
- Avoid main thread blocking operations
- Use image loading libraries (Coil) for efficient memory management
- Implement proper image compression for saves

## Security Considerations

- Validate all user inputs
- Use HTTPS for network calls
- Implement proper error handling
- Don't log sensitive information
- Use Android's security best practices

## Git Conventions

- Branch naming: `feature/`, `bugfix/`, `chore/`
- Commit messages: imperative mood, lowercase
- PR descriptions should explain what and why
- Reference issues in commits when applicable

## Documentation

- Document public APIs with KDoc
- Keep README.md updated
- Update CHANGELOG.md for user-facing changes
- Maintain CONTRIBUTING.md for contributors

## Debugging Tips

- Use Android Studio's Layout Inspector for Compose
- Enable GPU rendering in developer options
- Use LeakCanary for memory leak detection
- Profile with Android Profiler for performance issues

## Build & Test

- **Full build** (assemble + tests + lint — run before every commit): `scripts/build.sh`
- **Fast build** (skip lint + tests): `scripts/build.sh nolint notest`
- **Sync only** (no build/test/lint — just sync the working tree into the container, leaving it free for manual commands): `scripts/build.sh nobuild nolint notest`
- **Reading build logs**: use `docker exec whimsicalart-build cat /tmp/build.log` (do NOT re-run the build over and over just to inspect output).

## Manual / Device Testing

- Start the emulator: `scripts/emulator.sh`
- Once the emulator is running, use standard Android tools to drive the app:
  - `uiautomator dump` / `uiautomator dump /dev/stdout` for view hierarchy
  - `input text`, `input tap`, `input swipe`, `input keyevent` for interactions
  - `screencap -p` for screenshots
  - `logcat` for logs
- Use the emulator's VNC mode (`scripts/emulator.sh vnc`) for visual inspection when needed.
- Stop the emulator to release memory when no longer needed: `scripts/emulator.sh stop`

## Questions?

For questions about architecture decisions or implementation details, check the project's technical documentation or open a discussion in the repository.
