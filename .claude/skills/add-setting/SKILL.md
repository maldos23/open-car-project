---
name: add-setting
description: Add a new user setting to Open Car Project end to end (model, DataStore persistence, ViewModel, settings sheet UI, usage). Use when a feature needs a toggle or option the user can change.
---

# Add a setting

Paths are under `app/src/main/kotlin/org/opencarproject/launcher/`.

1. **Model** — add a field with a default that keeps today's behavior to `LauncherSettings`
   in `core/model/Models.kt`. Enums go in `core/model/Enums.kt`.
2. **Persistence** — in `data/source/SettingsDataSource.kt` add a preferences key next to the
   others, read it where the settings object is built (fall back to the default; parse enums
   with a safe fallback so old or unknown values never crash) and write it where settings are
   saved. `UpdateSettings` takes a `(LauncherSettings) -> LauncherSettings` transform, so no
   new use case is needed.
3. **ViewModel** — add `onXxxChange(value)` in `presentation/launcher/LauncherViewModel.kt`:
   `viewModelScope.launch { updateSettings { it.copy(xxx = value) } }`.
4. **UI** — add the control to `presentation/settings/SettingsSheet.kt`, reusing the existing
   row/switch/segmented components there, and pass the callback through
   `presentation/launcher/LauncherScreen.kt`.
5. **Use it** where the behavior lives, reading `settings.xxx`.
6. **Verify** — `./gradlew test assembleDebug`, install, toggle it on the emulator, restart
   the app (`adb shell am force-stop org.opencarproject.launcher`) and check it persisted.

Labels go in `res/values*/strings.xml` (all five locales: `values`, `-es`, `-zh-rCN`, `-pt`,
`-fr`) and are read with `stringResource`; comments are English.
