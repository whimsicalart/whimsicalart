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

- **Classic Filter Collection** - Time-tested photo filters and effects
- **Basic Editing Tools** - Crop, rotate, adjust brightness/contrast/saturation
- **Beauty Tools** - Skin smoothing, blemish removal, face enhancement
- **Stickers and Text** - Add creative elements to your photos
- **Collage Maker** - Combine multiple photos into beautiful layouts
- **No Ads** - Clean, distraction-free experience
- **No Account Required** - Works offline, no sign-up needed
- **Free Forever** - All features available at no cost

## Requirements

- Android 8.0 (API 26) or higher
- Camera permission (for camera features)
- Storage permission (for saving photos)

## Building from Source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

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

## Documentation

- [User Guide](docs/USER_GUIDE.md) — how to use the app
- [Developer Documentation](docs/DEVELOPMENT.md) — architecture and conventions
- [API Overview](docs/API.md) — public types by module
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

- [ ] Core photo editing engine
- [ ] Modern Material Design 3 UI
- [ ] Filter library implementation
- [ ] Beauty tools suite
- [ ] Collage maker
- [ ] Sticker pack support
- [ ] Batch editing capabilities
- [ ] Widget for quick edits

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
