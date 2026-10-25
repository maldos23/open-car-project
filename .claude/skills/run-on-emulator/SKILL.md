---
name: run-on-emulator
description: Build, install and launch Open Car Project on an Android emulator or device, grant its permissions and take a screenshot to verify a UI change. Use when asked to run, install, try or screenshot the launcher.
---

# Run on emulator

1. Make sure a device is attached: `adb devices`. If none, start the tablet AVD
   (`emulator -avd <name> -gpu host &` then `adb wait-for-device`). See `docs/DEVELOPMENT.md` §3.
2. Build and install (the `-g` flag grants runtime permissions):
   ```bash
   ./gradlew assembleDebug && adb install -r -g app/build/outputs/apk/debug/app-debug.apk
   ```
3. Make it the home app and grant notification access:
   ```bash
   P=org.opencarproject.launcher
   adb shell cmd role add-role-holder android.app.role.HOME $P
   adb shell cmd notification allow_listener $P/$P.data.source.LauncherNotificationListener
   adb shell am start -n $P/.presentation.LauncherActivity
   ```
4. Wait ~4 s, then capture and look at the screen:
   ```bash
   adb exec-out screencap -p > /tmp/screen.png
   ```
   Open the PNG and check the change in both light and dark theme when colors are involved
   (`adb shell cmd uimode night yes|no`).

## Gotchas

- The Home key on gesture navigation is handled by Pixel Launcher on emulators; enable
  3-button navigation (`docs/DEVELOPMENT.md` §3).
- Scrolling the settings sheet with `adb shell input swipe`: swipe inside the sheet near its
  left edge, away from the centered segmented buttons, or the swipe can land as a tap and
  change a setting.
- `connectedAndroidTest` uninstalls the app — rerun steps 2–3 afterwards.
- For fake music, notifications or GPS, use the `debug-tools` skill.
