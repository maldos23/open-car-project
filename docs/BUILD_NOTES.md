# Build notes

The toolchain the project is verified with, and the non-obvious decisions behind it.

## Toolchain

| Component | Version |
|---|---|
| JDK | Temurin 21 (`openjdk 21.0.6`) |
| Gradle | 8.13 (wrapper) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.1.21 |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk | 37 (Android 17) |
| minSdk | 26 |

## SDK location (macOS + Homebrew)

```bash
brew install --cask android-commandlinetools android-platform-tools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
```

Alternatively set `sdk.dir` in `local.properties` (not committed).

## Android 17 = API 37 and the "minor version" scheme

Android 17 ships as platform `android-37.0` (major **37**, minor **0**), not `android-37`.
AGP 8.13 resolves `compileSdk = 37` to the old-style `android-37` directory, which doesn't
exist, so the minor version is set explicitly:

```kotlin
// app/build.gradle.kts
android {
    compileSdk = 37
    compileSdkMinor = 0   // resolves to platforms/android-37.0
}
```

The "untested compile SDK" warning is silenced in `gradle.properties`:

```properties
android.suppressUnsupportedCompileSdk=37,37.0
```

> With AGP 9.x, `compileSdk = 37` resolves `android-37.0` natively and `compileSdkMinor` can
> be dropped.

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
