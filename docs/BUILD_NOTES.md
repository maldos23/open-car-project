# Build notes

The toolchain the project is verified with, and the non-obvious decisions behind it.

## Toolchain

| Component | Version |
|---|---|
| JDK | 17+ (CI and local builds use Temurin 21) |
| Gradle | 9.8.2 (wrapper) |
| Android Gradle Plugin | 9.4.1 (built-in Kotlin support) |
| Kotlin | 2.4.21 (Compose compiler and serialization plugins) |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk | 37 (Android 17) |
| minSdk | 26 |

## SDK location (macOS + Homebrew)

```bash
brew install --cask android-commandlinetools android-platform-tools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
```

Alternatively set `sdk.dir` in `local.properties` (not committed).

## Android 17 = API 37

Android 17 ships as platform `android-37.0`. AGP 9.x resolves `compileSdk = 37` to it
natively, so no `compileSdkMinor` or `android.suppressUnsupportedCompileSdk` workaround is
needed (both were required with AGP 8.13 and were removed in the 9.x upgrade).

## AGP 9 notes

- Kotlin is built into AGP 9: the `org.jetbrains.kotlin.android` plugin is **not** applied.
  Only the Compose compiler and serialization plugins are, and they must share the Kotlin
  version declared in the root `build.gradle.kts`.
- Compose 1.12+ (BOM 2026.x) requires AGP 9.1 or newer.
- Lint from Compose flags `Locale.getDefault()` inside composables (`NonObservableLocale`);
  use `androidx.compose.ui.text.intl.Locale.current`.

## Verified commands

```bash
./gradlew assembleDebug             # app/build/outputs/apk/debug/app-debug.apk (~17 MB)
./gradlew test                      # JVM unit tests
./gradlew assembleDebugAndroidTest  # builds the instrumented suite
./gradlew connectedAndroidTest      # runs it on a device/emulator
```

## Widget binding (third-party launchers)

A third-party launcher doesn't hold `BIND_APPWIDGET`, so binding goes through the system
consent dialog:

1. `AppWidgetHost.allocateAppWidgetId()`.
2. Try `AppWidgetManager.bindAppWidgetIdIfAllowed(id, provider)`.
3. If that fails, launch `ACTION_APPWIDGET_BIND` with the id and provider; the user grants
   permission once per provider.
4. If the provider has a configuration activity, launch it with
   `AppWidgetHost.startAppWidgetConfigureActivityForResult`.

Implemented in `LauncherActivity` + `LauncherViewModel` + `AddItem` / `AddBoundWidget`.

> **Emulator note:** on the `google_apis` API 35 image the system widget *picker*
> (`AppWidgetPickActivity`) can crash with a missing resource; the launcher uses its own picker
> and `ACTION_APPWIDGET_BIND`, which is not affected.
