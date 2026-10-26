# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). Entries are grouped from Conventional Commit types.

## [Unreleased]

### Added

- Motion glow setting "Turn source": sensors + GPS (default) or GPS only.
- Motion glow setting "Glow smoothness": how softly the light glides between directions.

### Changed

- The motion glow glides smoothly between directions and reacts about a second sooner to
  turns read from the GPS.

## [0.3.3] - 2026-10-25

### Changed

- The notification island keeps an ongoing call (pulsing green dot) or the music playing (a small
  level meter) pinned, and returns to it after each alert and when the screen wakes.

### Fixed

- Song changes no longer go unannounced when the player loads the next track before it starts.
- Notifications keep arriving after the system disconnects the notification listener.

## [0.3.2] - 2026-10-25

### Fixed

- The motion glow lights the side edges in turns again when the gyroscope is idle or missing
  (emulator, some head units) or the GPS gives no bearing: turns then come from the GPS track.

## [0.3.1] - 2026-10-25

### Changed

- The assistant opens faster: it is shown as a system overlay instead of launching the Google app.
- Dependencies: datastore-preferences 1.3.0 (the 0.1.1 notes listed it, but the build still used 1.1.1).

## [0.3.0] - 2026-10-25

### Fixed

- Two quick changes to the home screen (e.g. deleting two items) no longer undo each other.
- Cancelling a widget's setup screen removes the half-added widget.
- Pressing Back in edit mode also closes the add panel.
- Adding an app or module that is already on the home screen no longer creates a broken copy.
- Adding a widget whose app was just uninstalled shows a message instead of crashing.
- A corrupt layout file falls back to the default menu instead of crashing the launcher.
- The sensor speedometer no longer jumps to ~50 km/h for a moment on start.
- With both Waze and Google Maps navigating, the navigation card prefers Waze, as intended.
- Newly installed or removed apps show up in the app menu without restarting the launcher.
- The speed scene no longer keeps the GPS or accelerometer running while another app is in
  front, saving battery.

### Changed

- Smoother home screen with less battery use: the speed scene, weather pill, assistant button,
  player and navigation cards and card dragging now animate by redrawing instead of rebuilding
  the screen on every frame.
- Settings switches toggle by tapping anywhere on their row; color swatches and the dock's
  remove badge have full 48 dp touch targets and announce their selected state.
- With the system wallpaper in light theme, the bars' scrim is light so their text stays readable.

## [0.2.0] - 2026-10-25

### Added

- UI translations: English, Spanish, Simplified Chinese, Portuguese and French. Settings →
  Language picks one or follows the system (default); units and regional formats keep
  following the device region.
- Settings → Zoom slider (80–130 %) scales the whole interface, settings and dialogs included.
- Settings → App menu icon size slider (75–150 %) for the app menu and its full-page variant.
- Motion glow (off by default): a neon light travels along the screen border towards where the
  car is going, brightest at that point and fading along the edge. Left/right in turns
  (gyroscope, GPS bearing as fallback), up when speeding up, down and red when braking (GPS),
  corners when combined. Adjustable sensitivity and color, and a Test button that sweeps it
  around the border.

### Changed

- New app icon: an open steering wheel with a neon arc (also shown at the top of the READMEs).
- The notification island stays at the top center as a bubble with the newest notification's
  icon and a one-line summary; it hides when there are none, or until a newer one arrives after
  a swipe up.

### Fixed

- No crash on Android 8–10 when the GPS provider changes state (location listeners now
  implement every callback).
- Home starts even if the settings file is slow to read or corrupted.
- Home-screen widgets keep updating after the language or zoom changes.

## [0.1.2] - 2026-10-25

### Fixed

- Widget picker shows each widget's real preview (generated preview, `previewLayout` or
  `previewImage`) instead of a generic icon.
- Widgets added through the system permission dialog get their suggested size and land in free
  cells (they were always 1×1 and could overlap another item when the grid was full).
- Edit-mode delete and resize controls are smaller and sit on the card corners, so 1×1 icons
  can still be dragged (touch targets stay 48 dp).
- Weather keeps showing the city when a geocoder lookup fails, and retries it later.
- Bottom bar measures the real assistant pill width, so free shortcut slots are no longer hidden.

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

[Unreleased]: https://github.com/maldos23/open-car-project/compare/v0.3.3...develop
[0.3.3]: https://github.com/maldos23/open-car-project/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/maldos23/open-car-project/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/maldos23/open-car-project/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/maldos23/open-car-project/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/maldos23/open-car-project/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/maldos23/open-car-project/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/maldos23/open-car-project/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/maldos23/open-car-project/releases/tag/v0.1.0
