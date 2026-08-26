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

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Building

```bash
./gradlew assembleDebug
```

### Running Tests

```bash
./gradlew test
```

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
