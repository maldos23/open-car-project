<p align="center"><img src="docs/images/icon.svg" width="120" alt="Open Car Project icon"></p>

# Open Car Project

**English** · [Español](README.es.md) · [简体中文](README.zh-CN.md)

> **Open the car you already own.** Today's head units carry hardware powerful enough to run
> features that carmakers lock away or sell as subscriptions. Open Car Project exists to
> democratize the car's infotainment system: open software, built in the open, so drivers decide
> what their own screen can do.
>
> We believe it should be a right to choose how your car looks and to modernize it your way,
> without having to buy a new one — not everyone can afford that. Hours stuck in traffic are
> better spent in a place that feels like yours, because you built it yourself.

An open-source **Android launcher for car head units**. It turns the head-unit screen into a
customizable dashboard: navigation, music, notifications, weather and a speed scene live on a
drag-and-drop grid, with large touch targets designed for use while driving.

Built with **Kotlin + Jetpack Compose (Material 3)**, native Android APIs (`AppWidgetHost`,
`MediaSession`, `NotificationListenerService`, `DataStore`) and a Clean Architecture layout.

![Home screen](docs/screenshots/home.png)

<p align="center">
  <img src="docs/screenshots/motion-glow-turn.png" width="32%" alt="Motion glow in a left turn">
  <img src="docs/screenshots/notification-bubble.png" width="32%" alt="Notification bubble">
  <img src="docs/screenshots/settings.png" width="32%" alt="Settings: language and zoom">
</p>

## Features

| Area | What you get |
|---|---|
| **Customizable home** | Long-press anywhere to edit: drag, resize (with live push-aside of neighbours), delete. Half-cell app icons, system widgets (e.g. Google Calendar), configurable grid size. |
| **Built-in modules** | **Navigation** (next-turn guidance from Google Maps / Waze, or an animated *Navigate* button), **Media player** (cover art, progress, vibrant animated background), **Notifications** (cards with previews, actions, native rendering for unmapped styles). |
| **Top bar** | Animated **weather** pill (gradient per condition) and a **speed scene**: a car / SUV / pickup / cat / dog in a city that changes with time of day and weather, turning into a rocket above a configurable speed. Optional standalone speedometer (circle or inline). |
| **Notification island** | Dynamic-Island–style pop-up for new notifications and track changes that rests as a top-center bubble with the latest notification. |
| **Motion glow** | Optional neon light that travels along the screen border towards where the car is going, brightest at that point: left/right in turns (gyroscope, or GPS bearing), up when speeding up, down and red when braking (GPS), corners in between. Adjustable sensitivity and color, with a Test button. |
| **Bottom dock** | Clock, app drawer, Google Assistant button with rotating suggestions, app shortcuts, hotspot, signal (4G/5G, Wi-Fi standard), volume, notifications, settings. |
| **Settings** | Theme (system/light/dark), accent color (incl. Material You), background (solid / wallpaper / animated gradient with particles), units (°C/°F, km/h/mph), 12/24 h, language (5), UI zoom, app-menu style and icon size, what to show in the bars, and more. |

> The UI is available in English, Spanish, Simplified Chinese, Portuguese and French (Settings →
> Language). New languages are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Quick start

Requirements: **JDK 17+**, **Android SDK** with `platforms;android-37.0` and
`build-tools;37.0.0`. Gradle comes with the wrapper.

```bash
git clone <this-repo> open-car-project && cd open-car-project
export ANDROID_HOME=/path/to/android-sdk      # or set sdk.dir in local.properties
./gradlew assembleDebug                        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                         # install on a connected device/emulator
adb shell cmd role add-role-holder android.app.role.HOME org.opencarproject.launcher
```

Then grant notification access (needed by the media player, notifications and navigation
modules):

```bash
adb shell cmd notification allow_listener \
  org.opencarproject.launcher/org.opencarproject.launcher.data.source.LauncherNotificationListener
```

Full instructions — emulator setup, debugging tools, fake media/GPS/notifications, tests —
are in **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)**.

## Tests

```bash
./gradlew test                    # JVM unit tests (no device needed)
./gradlew connectedAndroidTest    # instrumented UI tests (device/emulator)
```

## Documentation

| Document | Contents |
|---|---|
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Build, run, debug and test (emulator, adb recipes, debug-only tools) |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, packages, data flow, where to look when something breaks |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute, code style, PR checklist |
| [docs/BUILD_NOTES.md](docs/BUILD_NOTES.md) | Toolchain details (Android 17 / API 37 platform naming) |
| [docs/HOTSPOT.md](docs/HOTSPOT.md) | Showing hotspot clients (requires installing as a system app) |
| [docs/EMBEDDING.md](docs/EMBEDDING.md) | Research notes on embedding other apps' windows (system apps only) |
| [CLAUDE.md](CLAUDE.md) · [.claude/](.claude) | Conventions, skills and agents for AI coding assistants |

## Permissions

| Permission | Used for |
|---|---|
| `BIND_APPWIDGET` | Hosting system widgets |
| Notification access (listener) | Notifications, media sessions, navigation next-turn |
| `ACCESS_COARSE_LOCATION` | Weather for your area |
| `ACCESS_FINE_LOCATION` | Real vehicle speed from GPS (speed scene / speedometer) |
| `READ_PHONE_STATE` | Mobile network type (4G/5G) in the signal indicator |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Weather data, signal indicator |

Weather data: [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0; free for non-commercial use).

## Contributing

Contributions of all sizes are welcome — bug reports, translations, new modules, docs.
Start with [CONTRIBUTING.md](CONTRIBUTING.md) — the project uses **gitflow** (PRs into
`develop`) and **Conventional Commits**.

## Download

Signed APKs are published on [GitHub Releases](https://github.com/maldos23/open-car-project/releases).
Changes are listed in [CHANGELOG.md](CHANGELOG.md).

## License

[Apache License 2.0](LICENSE) © Open Car Project contributors.
