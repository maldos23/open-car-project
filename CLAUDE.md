# Open Car Project — notes for AI assistants

Android launcher for car head units. Kotlin + Jetpack Compose (Material 3), Clean
Architecture, manual DI in `di/AppContainer`. Root package `org.opencarproject.launcher`.

## Must follow

- **English only** for code comments, KDoc, commit messages, skills and agents.
- **i18n (mandatory):** every user-visible text (Compose `Text`, `contentDescription`, toasts,
  widgets) comes from `res/values*/strings.xml` via `stringResource` / `getString` — never a
  literal. Add each new key to all five locales: `values` (English, fallback), `values-es`,
  `values-zh-rCN`, `values-pt`, `values-fr`. Use `<plurals>` for counts and `%1$s` args, not
  string concatenation.
- Respect the layers in `docs/ARCHITECTURE.md`. `domain` never imports `android.*`.
- Put non-UI logic in `core/pure` and add a JVM test under `app/src/test`.
- Driving UI: touch targets ≥ 48 dp; use `MaterialTheme.colorScheme` (no hard-coded text
  colors on theme surfaces); check light and dark themes.
- App icons on the desktop and in the dock render **without background** (logo only):
  `rememberAppIcon(..., withoutBackground = true)`.
- New settings must default to current behavior; persist them in `SettingsDataSource`.
- Never put the whole layout at risk: `JsonLayoutCodec` must stay tolerant of unknown data.
- When updating an English doc that has `.es.md` / `.zh-CN.md` siblings, update them too.

## Git (mandatory — see the `git-workflow` skill)

- **Gitflow:** never commit to `main` or `develop`. Work on `feature/*`, `bugfix/*`, `docs/*`
  or `chore/*` branched from `develop`; releases use `release/X.Y.Z`, urgent fixes `hotfix/*`.
- **Conventional Commits** for every commit and PR title: `type(scope): description`, English,
  imperative. Check with `scripts/check-commit-msg.sh --text "..."`.
- Never commit secrets, keystores, APKs or `local.properties`; keep `.gitignore` intact.
- Never write IP addresses, session IDs/links (no `Claude-Session:` trailer) or device serials in
  commits, PR bodies, docs or code — the repo is public. `check-commit-msg.sh` rejects them.
- Update `CHANGELOG.md` (`Unreleased`) for user-visible changes.
- Don't push, tag, open PRs or change GitHub settings unless the user asked for it.

## Verify your work

```bash
./gradlew test lintDebug assembleDebug   # always
./gradlew connectedAndroidTest        # gestures/grid changes (uninstalls the app afterwards!)
```

For UI changes, install on the emulator and check a screenshot
(`adb exec-out screencap -p > s.png`). See the `run-on-emulator` and `debug-tools` skills.

When scrolling the settings sheet with `adb shell input swipe`, swipe near the sheet's left
edge (inside it, away from the centered segmented buttons): a swipe that can't scroll may be
delivered as a tap and silently change a setting.

## Skills and agents

- Skills (`.claude/skills/`): `git-workflow`, `run-on-emulator`, `debug-tools`, `run-tests`,
  `add-builtin-module`, `add-setting`.
- Agents (`.claude/agents/`): `android-reviewer`, `emulator-verifier`, `docs-translator`.
