# Development guide

**English** · [Español](DEVELOPMENT.es.md) · [简体中文](DEVELOPMENT.zh-CN.md)

How to build, run, debug and test Open Car Project.

- [1. Environment](#1-environment)
- [2. Build and install](#2-build-and-install)
- [3. Emulator setup](#3-emulator-setup)
- [4. First run (permissions)](#4-first-run-permissions)
- [5. Debugging](#5-debugging)
- [6. Simulating real-world input](#6-simulating-real-world-input)
- [7. Tests](#7-tests)
- [8. Troubleshooting](#8-troubleshooting)

## 1. Environment

| Tool | Version |
|---|---|
| JDK | 17 or newer (tested with Temurin 21) |
| Android SDK | `platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools`, `emulator` |
| Gradle | 8.13 via the wrapper (`./gradlew`) — no global install needed |
| Android Gradle Plugin | 8.13.x |
| Kotlin | 2.1.x |

The app targets **API 37 (Android 17)** and runs on **API 26+**. See
[BUILD_NOTES.md](BUILD_NOTES.md) for why `compileSdkMinor = 0` is set.

Point Gradle at your SDK with either:

```bash
export ANDROID_HOME=/path/to/android-sdk
# or create local.properties with:  sdk.dir=/path/to/android-sdk
```

## 2. Build and install

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew installDebug         # build + install on the connected device
./gradlew assembleRelease      # unsigned release APK (sign it yourself)
```

Launch it and make it the default home app:

```bash
adb shell am start -n org.opencarproject.launcher/.presentation.LauncherActivity
adb shell cmd role add-role-holder android.app.role.HOME org.opencarproject.launcher
```

## 3. Emulator setup

A tablet AVD in landscape is the closest thing to a head unit.

```bash
# Apple Silicon: use the API 35 arm64 image (API 36/37 images fall back to software
# rendering on arm64 and are very slow). The app runs fine on API 35.
sdkmanager "system-images;android-35;google_apis;arm64-v8a"
avdmanager create avd -n opencar_tablet -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_tablet
emulator -avd opencar_tablet -gpu host &
adb wait-for-device
```

**Home button tip:** with gesture navigation, the emulator's Pixel Launcher (Quickstep) handles
the "home" swipe and does not always hand it to a third-party launcher. Switch the emulator to
3-button navigation for reliable testing:

```bash
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
```

## 4. First run (permissions)

The app asks for location and phone-state at startup. These can also be granted from adb:

```bash
PKG=org.opencarproject.launcher
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.READ_PHONE_STATE
# Notification access (media player, notifications, navigation next-turn):
adb shell cmd notification allow_listener $PKG/$PKG.data.source.LauncherNotificationListener
```

## 5. Debugging

- **Android Studio:** open the project root, select the `app` configuration and press
  *Debug*. The launcher is a normal activity, so breakpoints, Layout Inspector (Compose) and
  the Profiler all work.
- **Logs:** `adb logcat --pid=$(adb shell pidof org.opencarproject.launcher)`
- **Crashes:** `adb logcat -b crash`
- **Screenshots / screen recordings:**
  ```bash
  adb exec-out screencap -p > screen.png
  adb shell screenrecord --time-limit 30 /sdcard/demo.mp4 && adb pull /sdcard/demo.mp4
  ```
- **Inspect saved state** (DataStore files, debug builds only):
  ```bash
  adb shell run-as org.opencarproject.launcher ls files/datastore/
  # launcher_layout.preferences_pb  -> grid + placed items (JSON inside)
  # launcher_settings.preferences_pb -> settings
  ```
- **Reset to a clean state:** `adb shell pm clear org.opencarproject.launcher`

## 6. Simulating real-world input

### Media session (debug builds only)

`FakeMediaSessionReceiver` (in `app/src/debug/`) publishes a fake player so you can test the
media card and the "now playing" island without other apps:

```bash
R="-n org.opencarproject.launcher/.debug.FakeMediaSessionReceiver -a org.opencarproject.launcher.DEBUG_MEDIA"
adb shell am broadcast $R --es title "Song" --es artist "Artist" --es album "Album" \
  --ei color 15022389 --el duration 210000 --el position 30000          # play
adb shell am broadcast $R --es state pause                              # pause
adb shell am broadcast $R --es state stop                               # remove session
```

### Wallpaper (debug builds only)

```bash
adb shell am broadcast -n org.opencarproject.launcher/.debug.FakeMediaSessionReceiver \
  -a org.opencarproject.launcher.DEBUG_WALLPAPER
```

### Notifications

```bash
adb shell cmd notification post -t "Title" my_tag "Body text"
adb shell cmd notification post -S bigtext -t "Long" tag2 "A very long text ..."
adb shell cmd notification post -S messaging --conversation "Team" \
  --message "Ana:Are you coming?" --message "Luis:On my way" -t "Team" chat "Ana: ..."
adb shell cmd notification cancel my_tag
```

### GPS location and speed

`adb emu geo fix` does not reach apps on some API 35 images; use a test provider instead:

```bash
adb shell appops set com.android.shell android:mock_location allow
adb shell cmd location providers add-test-provider gps
adb shell cmd location providers set-test-provider-enabled gps true
adb shell cmd location providers set-test-provider-location gps --location 19.4326,-99.1332
```

The speed scene computes speed from consecutive fixes when the fix has no speed. To simulate
driving, send one fix per second moving north by `speed_m_s / 111320` degrees of latitude:

```python
# drive.py — 40 km/h for 20 s
import subprocess, time
lat = 19.43
for _ in range(20):
    lat += (40 / 3.6) / 111320
    subprocess.run(["adb", "shell", "cmd", "location", "providers", "set-test-provider-location",
                    "gps", "--location", f"{lat:.7f},-99.1332"])
    time.sleep(1)
```

### Navigation guidance

Start a real Google Maps navigation (needs the location provider above):

```bash
adb shell am start -a android.intent.action.VIEW -d "google.navigation:q=19.4270,-99.1677" \
  -p com.google.android.apps.maps
```

## 7. Tests

```bash
./gradlew test                     # unit tests (JVM): grid math, codecs, units, colors, use cases
./gradlew connectedAndroidTest     # instrumented UI test (drag gesture) on a device/emulator
```

- Pure logic lives in `core/pure` and is covered by JVM tests in `app/src/test`. Put new
  non-UI logic there so it can be tested without a device.
- `connectedAndroidTest` **uninstalls the app when it finishes**, which wipes your layout and
  permissions on that device. Reinstall and re-grant afterwards (sections 2 and 4).

## 8. Troubleshooting

| Symptom | Where to look |
|---|---|
| Doesn't appear as a home app | `AndroidManifest.xml` (`HOME` + `DEFAULT` categories); set the role (section 2) |
| Widgets missing / not added | `SystemCatalogSource`, the bind flow in `LauncherActivity` (`ACTION_APPWIDGET_BIND`) |
| Media card says "Nada sonando" | Notification access not granted; `MediaSessionSource` |
| No weather / "Activar clima" | Location permission or network; `WeatherSource` |
| Speed stays at 0 | Fine-location permission, GPS provider; `SpeedReader` |
| Layout lost after update | `JsonLayoutCodec` (unknown item kinds are dropped, not the whole layout) |
| Home button does nothing (emulator) | Gesture-nav limitation — see section 3 |

More in [ARCHITECTURE.md](ARCHITECTURE.md#where-to-look-when-something-breaks).
