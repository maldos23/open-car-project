# Contributing to Open Car Project

**English** · [Español](CONTRIBUTING.es.md) · [简体中文](CONTRIBUTING.zh-CN.md)

Thanks for helping! Bug reports, translations, docs, new modules and fixes are all welcome.

## Ways to contribute

- **Report a bug** — open an issue with steps to reproduce, device/emulator + Android version,
  and `adb logcat` output if it crashed.
- **Suggest a feature** — describe the driving scenario it improves.
- **Translate** — UI strings live in `res/values*/strings.xml` (English is the `values`
  fallback). Add a `values-xx` folder and a matching `AppLanguage` entry for a new language.
  Docs translations live next to the English file (`*.es.md`, `*.zh-CN.md`).
- **Code** — pick an open issue or propose one first for larger changes.

## Getting set up

Follow [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md): it covers the SDK, emulator, permissions,
debug tools and tests.

## Branching model (gitflow)

Gitflow is **mandatory** and enforced by CI (`scripts/check-branch.sh`); `main` and `develop`
are protected.

| Branch | From | Merges into | Purpose |
|---|---|---|---|
| `main` | — | — | Released code only; each merge is a tagged release |
| `develop` | `main` | — | Integration branch — **target your PRs here** |
| `feature/<name>` | `develop` | `develop` | New features |
| `bugfix/<name>` | `develop` / `release/*` | same | Non-urgent fixes |
| `docs/<name>`, `chore/<name>` | `develop` | `develop` | Docs, tooling |
| `release/X.Y.Z` | `develop` | `main` + `develop` | Release stabilization (maintainers) |
| `hotfix/<name>` | `main` | `main` + `develop` | Urgent fix of a release (maintainers) |

1. Fork, then branch from `develop`: `git switch -c feature/volume-badge develop`.
2. Commit following the rules below.
3. Run the checks below.
4. Open a pull request **into `develop`** using the template; include screenshots or a short
   screen recording for any UI change.

## Commit messages

[Conventional Commits 1.0](https://www.conventionalcommits.org/en/v1.0.0/) are **mandatory**
for every commit and for the PR title (checked in CI):

```
<type>(<optional scope>)!: <description>

[optional body: why]

[optional footer: BREAKING CHANGE: ..., Fixes #123]
```

- Types: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`.
- Scopes (suggested): `grid` `media` `notifications` `navigation` `weather` `speed` `dock`
  `settings` `widget` `drawer` `i18n` `deps` `release`.
- English, imperative mood, lowercase start, no trailing period, header ≤ 100 characters.
- Examples: `feat(dock): add volume badge`, `fix(grid): keep items inside bounds on resize`,
  `docs: translate development guide to zh-CN`.

Enable the local hook once per clone so bad messages are rejected before pushing:

```bash
git config core.hooksPath .githooks
```

## Releases

Maintainers cut releases with a `release/X.Y.Z` branch (version bump + CHANGELOG), merge it
into `main`, tag `vX.Y.Z` and merge back into `develop`. The tag triggers the Release workflow,
which builds the signed APK and publishes it on GitHub Releases. Versions follow
[SemVer](https://semver.org/); changes are recorded in [CHANGELOG.md](CHANGELOG.md).

## Before you open a PR

```bash
./gradlew test            # must pass
./gradlew assembleDebug   # must build
./gradlew lintDebug       # no errors
```

If you touched gestures or the grid, also run `./gradlew connectedAndroidTest` on an emulator.

## Code style

- **Language:** all code comments, KDoc, commit messages, skills and agents are in **English**.
  Every user-visible text goes through `strings.xml` (no literals in Kotlin), with each key in
  all five locales: `values`, `values-es`, `values-zh-rCN`, `values-pt`, `values-fr`.
- **Kotlin:** official code style (`kotlin.code.style=official`), 4-space indent, trailing commas.
- **Architecture:** respect the layers in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
  `domain` must not import `android.*`. Put testable logic in `core/pure` and cover it with a
  JVM test in `app/src/test`.
- **Comments explain *why*,** not what. Keep them short.
- **Driving-friendly UI:** touch targets ≥ 48 dp, readable contrast in light and dark themes,
  no hard-coded colors for text on theme surfaces (use `MaterialTheme.colorScheme`).
- **No new dependencies** without discussing them in the issue first.
- **Settings:** new options get a default that keeps current behavior.

## Pull request checklist

- [ ] Branch follows gitflow and targets `develop`; commits and PR title follow Conventional Commits
- [ ] `./gradlew test lintDebug assembleDebug` passes
- [ ] New logic in `core/pure` has unit tests
- [ ] Comments in English; docs updated (and translations, if you can)
- [ ] Screenshots / recording for UI changes, tested in light **and** dark theme
- [ ] No secrets (API keys, keystores) committed

## AI-assisted contributions

The repo ships [CLAUDE.md](CLAUDE.md) plus skills and agents in [.claude/](.claude) for Claude
Code. They encode the conventions above; you are still responsible for reviewing and testing
anything an assistant produces.

## Code of conduct and security

Everyone participating follows the [Code of Conduct](CODE_OF_CONDUCT.md). Report
vulnerabilities privately as described in [SECURITY.md](SECURITY.md), never in a public issue.

## License

By contributing you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
