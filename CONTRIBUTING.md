# Contributing to Open Car Project

**English** · [Español](CONTRIBUTING.es.md) · [简体中文](CONTRIBUTING.zh-CN.md)

🚗 Thanks for helping build a launcher for the dashboard! Bug reports, translations, docs, new
modules and fixes are all welcome.

## 🧭 Ways to contribute

- **Report a bug** — open an issue with steps to reproduce, device/emulator + Android version,
  and `adb logcat` output if it crashed.
- **Suggest a feature** — describe the driving scenario it improves.
- **Translate** — UI strings live in `res/values*/strings.xml` (English is the `values`
  fallback). Add a `values-xx` folder and a matching `AppLanguage` entry for a new language.
  Docs translations live next to the English file (`*.es.md`, `*.zh-CN.md`).
- **Code** — pick an open issue or propose one first for larger changes.

## ⚡ Getting set up

Two ways in: do it yourself, or let an AI assistant walk you through it.

### 🧑‍🔧 By hand (~15 min)

```bash
git clone https://github.com/<you>/open-car-project.git
cd open-car-project                      # default branch is develop
git remote add upstream https://github.com/maldos23/open-car-project.git
git config core.hooksPath .githooks      # rejects bad commit messages locally
export ANDROID_HOME=/path/to/android-sdk # or sdk.dir=... in local.properties (never commit it)
./gradlew test lintDebug assembleDebug   # same checks CI runs; first run downloads Gradle
```

You need JDK 17+ and the SDK packages from [DEVELOPMENT §1](docs/DEVELOPMENT.md#1-environment).
Emulator: [§3](docs/DEVELOPMENT.md#3-emulator-setup). On Apple Silicon, use the API 35 arm64 image.
Install and set as HOME: [§2](docs/DEVELOPMENT.md#2-build-and-install).
Permissions: [§4](docs/DEVELOPMENT.md#4-first-run-permissions).

### 🤖 With an AI assistant

Open the repo in Claude Code (or any assistant that can run shell commands) and paste Prompt 1
from [AI-assisted contributions](#ai-assisted-contributions). You approve every command it runs.

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

Check a message first: `scripts/check-commit-msg.sh --text "feat(dock): add volume badge"`.

Touched the website? Preview it locally:
`rm -rf /tmp/ocp-site && mkdir -p /tmp/ocp-site && cp site/index.html /tmp/ocp-site/ && rsync -a --exclude legacy docs/screenshots/ /tmp/ocp-site/screenshots/ && python3 -m http.server -d /tmp/ocp-site 8000`

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

### 🧠 How it loads

- **`CLAUDE.md`** is read automatically at the start of every Claude Code session in this repo.
  It holds the must-follow rules.
- **Skills** live in `.claude/skills/<name>/SKILL.md`. At first Claude only sees each skill's
  `name` and `description`. The full instructions load when you type `/<name>` (for example
  `/run-on-emulator`) or when your request matches the description ("add a toggle for…" loads
  `add-setting`).
- **Agents** live in `.claude/agents/<name>.md`. They are sub-assistants, each with its own
  instructions and a limited tool list. Claude hands work to one when the task matches, or when
  you name it ("use the android-reviewer agent on my diff"). `/agents` lists them.
- Other assistants do not load these files automatically. Tell them which file to read (see
  Prompt 4).

### 🗂️ Skills and agents

| | Name | Kind | Use it when… |
|---|---|---|---|
| 🌿 | `git-workflow` | skill | creating a branch, committing, opening a PR, cutting a release or hotfix (gitflow + Conventional Commits) |
| 📱 | `run-on-emulator` | skill | you want to build, install, launch, grant permissions and screenshot the launcher |
| 🧪 | `run-tests` | skill | finishing any code change: JVM tests, build, lint, instrumented drag test (it uninstalls the app) |
| 🎛️ | `debug-tools` | skill | testing the media card, notification island, speed scene, weather or navigation with fake input instead of real apps or a car |
| 🧱 | `add-builtin-module` | skill | adding a new card/module/tile that users can place, move and resize on the grid |
| ⚙️ | `add-setting` | skill | a feature needs a toggle or option, from model through DataStore and ViewModel to the settings sheet |
| 🔍 | `android-reviewer` | agent · Read/Grep/Glob/Bash | after implementing, before a PR: layering, Compose/state bugs, lifecycle leaks, driving UI, English comments |
| 👀 | `emulator-verifier` | agent · Read/Bash/Glob/Grep | verifying a UI change end to end: installs, runs the scenario via adb, reports screenshots |
| 🌐 | `docs-translator` | agent · Read/Write/Edit/Glob/Grep | an English doc (README, CONTRIBUTING, DEVELOPMENT) changed and the `.es.md`/`.zh-CN.md` copies must catch up |

### 💬 Prompts you can paste

Replace the `<...>` parts, paste, and keep an eye on what it runs. 👇

**Prompt 1: 🛠️ Set up and verify my machine** (Claude Code)

```
I want to contribute to Open Car Project. Read CLAUDE.md and docs/DEVELOPMENT.md first.
Then, step by step, asking before you install or download anything:
1. Detect my OS/CPU and check JDK 17+, ANDROID_HOME (or sdk.dir in local.properties) and the SDK packages
   platforms;android-37.0, build-tools;37.0.0, platform-tools, emulator. List what is missing with the exact fix command.
2. Run `git config core.hooksPath .githooks` and confirm I'm on an up-to-date develop.
3. Use the run-tests skill to run `./gradlew test lintDebug assembleDebug` and explain any failure in plain words
   (test name + assertion, lint file:line). Don't fix anything without my OK.
Never commit local.properties, push, or change GitHub settings. End with a checklist: ready / still needs me.
```

**Prompt 2: 📱 Run it on an emulator**

```
Set up an emulator following docs/DEVELOPMENT.md §3 (tablet AVD, landscape; on Apple Silicon the API 35 arm64 image),
asking before downloading system images. Then use the run-on-emulator skill to build, install, make it the HOME app,
grant notification access and launch it. Take a screenshot, open it and tell me what is on screen.
```

**Prompt 3: 🌱 Start my first contribution**

```
I want to work on: <describe the issue or paste its URL>.
Follow the git-workflow skill: create the right branch (feature/, bugfix/, docs/ or chore/, kebab-case) from an up-to-date develop.
Before coding, give me a short plan: files you'll touch and the project skill that fits (add-setting, add-builtin-module), if any.
Implement it following CLAUDE.md: user-visible text in all 5 strings.xml locales, a JVM test for core/pure logic,
a CHANGELOG "Unreleased" entry if users will notice it. Run the run-tests skill, then the android-reviewer agent on the diff,
and fix what it finds. Propose a Conventional Commit message, check it with scripts/check-commit-msg.sh --text,
and commit only after I approve. Don't push or open a PR unless I ask.
```

**Prompt 4: 🤖 Any other assistant (ChatGPT, Copilot, Cursor…)**

```
You are helping me contribute to Open Car Project, an Android launcher (Kotlin, Jetpack Compose).
Read these repo files first and treat them as the source of truth: CLAUDE.md, CONTRIBUTING.md, docs/DEVELOPMENT.md,
and the skill that matches my task in .claude/skills/<name>/SKILL.md (e.g. git-workflow, run-on-emulator, add-setting).
Don't invent commands that aren't in them. My OS is <macOS/Linux/Windows>; my task is: <...>.
Give me one command at a time and wait for my output before continuing.
```

## Code of conduct and security

Everyone participating follows the [Code of Conduct](CODE_OF_CONDUCT.md). Report
vulnerabilities privately as described in [SECURITY.md](SECURITY.md), never in a public issue.

## License

By contributing you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
