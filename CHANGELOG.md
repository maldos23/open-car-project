# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). Entries are grouped from Conventional Commit types.

## [Unreleased]

## [0.1.1] - 2026-10-25

### Changed

- Build toolchain upgraded: Gradle 9.8.2, Android Gradle Plugin 9.4.1 (built-in Kotlin),
  Kotlin 2.4.21, Compose BOM 2026.09.00, kotlinx-serialization 1.12.0. The API 37
  `compileSdkMinor` / `suppressUnsupportedCompileSdk` workarounds are gone.
- Dependabot groups related dependencies so coupled upgrades arrive in a single PR.
- Dependencies: androidx core-ktx 1.19.1, datastore-preferences 1.3.0, kotlinx-coroutines-test
  1.11.0; CI actions (checkout, setup-java, setup-android, setup-gradle, upload-artifact) to
  their latest majors.

### Fixed

- Speed units follow locale changes (observable locale in Compose).
- Instrumented drag test: pre-grants startup permissions and drives the Compose clock
  manually so infinite animations don't block idling.

## [0.1.0] - 2026-10-24

First public release.

### Added

- Customizable home grid for car head units: long-press to edit, drag, resize with push-aside,
  delete; half-cell app icons without background; system App Widgets (bind + configure flow).
- Built-in modules: **Navigation** (next-turn guidance from Google Maps / Waze notifications,
  animated *Navigate* button), **Media player** (cover art, progress, vibrant animated
  background, controls), **Notifications** (cards, previews, actions, native rendering).
- Notification island (Dynamic-Island style) for new notifications and track changes.
- Top bar: animated weather pill (Open-Meteo, gradient per condition, city name) and speed
  scene (car / SUV / pickup / cat / dog, sky by time of day and weather, rocket mode) with an
  optional circular or inline speedometer; GPS or sensor speed source.
- Bottom dock: clock, app drawer with search, Google Assistant button with rotating
  suggestions, app shortcuts, hotspot, signal (4G/5G, Wi-Fi standard), volume, notifications,
  settings.
- Settings: theme, accent color (incl. Material You), background (solid / wallpaper / animated
  gradient with particles), °C/°F, km/h/mph, 12/24 h, bar contents, app-menu style, dock size.
- Docs in English, Spanish and Simplified Chinese; CI, gitflow/commit checks and signed release
  builds.

[Unreleased]: https://github.com/maldos23/open-car-project/compare/v0.1.1...develop
[0.1.1]: https://github.com/maldos23/open-car-project/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/maldos23/open-car-project/releases/tag/v0.1.0
