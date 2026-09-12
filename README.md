# WhimsicalArt

An open-source Android photo editing application inspired by classic mobile photo editors, bringing powerful editing tools to modern Android devices.

## About

WhimsicalArt is a modern Android photo editing app built with current Android APIs and best practices. It aims to deliver a clean, fast, and feature-rich editing experience without the bloat, ads, or subscription requirements common in modern photo editors.

### Why WhimsicalArt?

Many beloved photo editing apps have fallen victim to enshittification over the years:
- Core features moved behind paywalls
- Online account requirements for basic functionality
- Subscription-only access to filters and tools
- Bloatware and advertisements degrading user experience
- Abandonment of older devices despite capable hardware

WhimsicalArt exists to provide a modern, open-source alternative that respects users and their devices.

## Features

- **Classic Filter Collection** - Color, style, and lens filter looks
- **Basic Editing Tools** - Crop, rotate/transform, adjust brightness/contrast/saturation/exposure/shadows/highlights/temperature/tint/vignette/sharpening
- **Beauty Tools** - Skin smoothing, blemish removal, face enhancement, reshaping, and makeup (on-device, offline)
- **Text & OCR** - Editable text overlays with fonts, styles, and Recognize Text (OCR)
- **Stickers and Decorations** - Stickers, emoji, mosaic, blur brush, pen, frames
- **Layers** - Every edit is a reorderable, reversible layer; Merge Layers to flatten
- **Background** - Object removal, background blur, and background replace
- **Collage Maker** - Combine multiple photos into beautiful layouts
- **Save & Share** - JPEG/PNG export at your chosen quality and resolution
- **No Ads** - Clean, distraction-free experience
- **No Account Required** - Works offline, no sign-up needed
- **Free Forever** - All features available at no cost

## Requirements

- Android 8.0 (API 26) or higher
- Photo access permission (to browse the gallery and save edits)
- Camera permission (for camera capture)

## Building from Source

### Prerequisites

- Android Studio (Iguana 2024.2.1) or newer
- JDK 17
- Android SDK 35 (compileSdk)

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/whimsicalart/whimsicalart.git
   ```

2. Open the project in Android Studio

3. Sync Gradle and build:
   ```bash
   ./gradlew assembleDebug
   ```

4. Install on device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Download

WhimsicalArt is distributed as a **GitHub Release APK** (see
[Releases](https://github.com/whimsicalart/whimsicalart/releases)) and is planned
for **F-Droid**. The app works fully offline, requires no account, and contains
no ads or trackers.

## Documentation

- [User Guide](docs/user_guide.md) — how to use the app
- [Developer Documentation](docs/development.md) — architecture and conventions
- [API Overview](docs/api.md) — public types by module
- [Contributing Guidelines](CONTRIBUTING.md)
- [Privacy Policy](PRIVACY.md)

## Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting a pull request.

### Areas for Contribution

- Modern UI/UX improvements using Jetpack Compose
- Performance optimizations
- Additional filters and effects
- Accessibility improvements
- Bug fixes and testing
- Documentation

## Roadmap

- [x] Core photo editing engine (reversible, layered effect stack)
- [x] Modern Material Design 3 UI
- [x] Filter library (color / style / lens looks)
- [x] Beauty tools suite
- [x] Collage maker
- [x] Sticker, text, mosaic, pen, and frame tools
- [x] Background blur / replace and object removal
- [ ] Batch editing capabilities
- [ ] Widget for quick edits
- [ ] Release to F-Droid

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

WhimsicalArt draws inspiration from the golden age of mobile photo editing applications, particularly the classic Meitu app versions that prioritized user experience and powerful editing tools before the industry shift toward monetization over quality.

## Disclaimer

WhimsicalArt is an independent project. It is not affiliated with, endorsed by, or connected to Meitu Inc. or any of its subsidiaries or affiliates. All trademarks are property of their respective owners.

## Support

- [Issue Tracker](https://github.com/whimsicalart/whimsicalart/issues)
- [Discussions](https://github.com/whimsicalart/whimsicalart/discussions)

---

<p align="center">Made with ❤️ by the open-source community</p>
