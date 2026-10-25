# Security Policy

## Supported versions

Only the latest release receives security fixes.

## Reporting a vulnerability

Please **do not open a public issue**. Report it privately via
[GitHub Security Advisories](https://github.com/maldos23/open-car-project/security/advisories/new).
Include steps to reproduce, affected version and device/Android version. You will get an
answer within 7 days; fixes ship as a `hotfix/*` release.

## Secrets

The repository must never contain API keys, keystores or passwords. Release signing uses
GitHub Actions secrets only (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). If you find a leaked secret, report it as above.
