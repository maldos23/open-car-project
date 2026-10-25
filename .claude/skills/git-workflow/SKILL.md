---
name: git-workflow
description: Mandatory gitflow branching, Conventional Commits and release procedure for Open Car Project. Use before creating a branch, committing, opening a PR, cutting a release or a hotfix.
---

# Git workflow (mandatory)

## Branches (gitflow)

| Branch | From | Merges into | Purpose |
|---|---|---|---|
| `main` | — | — | Released code only; every commit is a tagged release |
| `develop` | `main` | — | Integration branch; default PR target |
| `feature/<name>` | `develop` | `develop` | New features |
| `bugfix/<name>` | `develop` or `release/*` | same | Non-urgent fixes |
| `docs/<name>`, `chore/<name>` | `develop` | `develop` | Docs, tooling |
| `release/X.Y.Z` | `develop` | `main` **and** `develop` | Stabilize a release |
| `hotfix/<name>` | `main` | `main` **and** `develop` | Urgent fix of a release |

Never commit directly to `main` or `develop` (both are protected; CI enforces the table with
`scripts/check-branch.sh`). Names: lowercase kebab-case, e.g. `feature/volume-badge`.

## Commits (Conventional Commits 1.0)

`<type>(<scope>)!: <description>` — imperative, lowercase start, no trailing period, ≤ 100 chars.
Types: `feat fix docs style refactor perf test build ci chore revert`. Suggested scopes:
`grid media notifications navigation weather speed dock settings widget drawer i18n deps release`.
Breaking changes: `!` after the scope and a `BREAKING CHANGE:` footer. Body explains *why*.
All in English. Validate with `scripts/check-commit-msg.sh --text "<msg>"`; enable the local
hook once per clone: `git config core.hooksPath .githooks`.

When an AI assistant authors a commit, append the attribution trailer required by its harness.

## Release (X.Y.Z, SemVer)

```bash
git switch develop && git pull
git switch -c release/X.Y.Z
# bump versionName (and versionCode +1) in app/build.gradle.kts
# move CHANGELOG "Unreleased" entries under "## [X.Y.Z] - YYYY-MM-DD", update compare links
git commit -am "chore(release): X.Y.Z"
git push -u origin release/X.Y.Z && gh pr create --base main --title "chore(release): X.Y.Z"
# after merge (merge commit, not squash):
git switch main && git pull && git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z
gh pr create --base develop --head main --title "chore(release): merge X.Y.Z back into develop"
```

Pushing the tag runs `.github/workflows/release.yml`: it checks the tag is on `main` and equals
`versionName`, builds the signed APK and publishes the GitHub Release with CHANGELOG notes.

## Hotfix

Branch `hotfix/<name>` from `main`, fix + bump patch version + CHANGELOG, PR into `main`, tag,
then merge `main` back into `develop` — same as a release.

## Before pushing

`./gradlew test lintDebug assembleDebug` must pass. No secrets, APKs or `local.properties`
(see `.gitignore`).
