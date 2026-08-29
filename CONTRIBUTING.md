# Contributing to WhimsicalArt

Thank you for your interest in contributing to WhimsicalArt! This document provides guidelines and instructions for contributing.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Create a new branch for your feature or fix
4. Make your changes
5. Submit a pull request

## Development Setup

### Prerequisites

- Android Studio (Iguana 2024.2.1) or newer
- JDK 17
- Android SDK 35 (compileSdk), with minSdk 26 / targetSdk 34

### Module Layout

The project is a multi-module Gradle build powered by version-catalog conventions:

| Path | Purpose |
|------|---------|
| `app/` | Application entry point, theme, navigation |
| `core/` | Shared domain, data, and common utilities |
| `feature/` | Editor, filters, beauty, collage, stickers, gallery, settings |
| `designsystem/` | Design system components |
| `build-logic/` | Convention plugins for app/library/Hilt/Room modules |

### Building

```bash
./gradlew assembleDebug
```

Build configuration is centralized:

- **Versions** live in `gradle.properties` (`VERSION_CODE`, `VERSION_NAME`) and are exposed via generated `BuildConfig`
- **Dependencies** are declared in `gradle/libs.versions.toml`
- **CI** validates `assembleDebug`, unit tests, and lint on every push/PR (`.github/workflows/ci.yml`)

### Running Tests

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
```

### Release & Signing

Local release builds are signed through a gitignored `keystore.properties` at the repository root:

```properties
storeFile=keystore/release.keystore
storePassword=<your-store-password>
keyAlias=whimsicalart
keyPassword=<your-key-password>
```

If `keystore.properties` is absent, release builds fall back to the debug signing
config so the project still compiles on a fresh checkout.

Cutting a release is handled by `.github/workflows/release.yml`: pushing a tag like
`v1.0.0` builds and verifies a signed APK and publishes a GitHub Release. Signing
credentials are injected from repository secrets (`KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) — never commit keystore
material or passwords to the repository.

## Code Style

### Kotlin

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use coroutines for asynchronous operations
- Prefer sealed classes for state management
- Avoid `!!` operator - use safe calls or early returns

### Compose

- Use Material 3 components
- Keep composables stateless where possible
- Follow Compose naming conventions (PascalCase for composables)

### Architecture

- Follow MVVM with Clean Architecture patterns
- Use Repository pattern for data access
- Keep ViewModels focused on UI state management

## Pull Request Process

1. Update documentation if needed
2. Add tests for new features
3. Ensure all tests pass
4. Request review from maintainers

## Reporting Issues

- Use the issue tracker for bugs and feature requests
- Provide as much detail as possible
- Include steps to reproduce for bugs

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
