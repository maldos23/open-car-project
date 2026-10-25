# 为 Open Car Project 做贡献

[English](CONTRIBUTING.md) · [Español](CONTRIBUTING.es.md) · **简体中文**

感谢你的帮助！欢迎错误报告、翻译、文档、新模块和修复。

## 贡献方式

- **报告错误**——提交 issue，附上复现步骤、设备/模拟器及 Android 版本；如果发生崩溃，请附上
  `adb logcat` 输出。
- **建议功能**——描述它能改善的驾驶场景。
- **翻译**——目前界面为西班牙语，并硬编码在 Compose 中。把字符串移到
  `res/values/strings.xml`（并添加 `values-en`、`values-zh-rCN` 等）是非常好的第一个 PR。
  文档翻译与英文文件放在一起（`*.es.md`、`*.zh-CN.md`）。
- **代码**——挑选一个未关闭的 issue；较大的改动请先提出 issue。

## 环境准备

按照 [docs/DEVELOPMENT.zh-CN.md](docs/DEVELOPMENT.zh-CN.md) 操作：其中涵盖 SDK、模拟器、权限、调试工具和测试。

## 分支模型（gitflow）

gitflow 是**强制**的，并由 CI 检查（`scripts/check-branch.sh`）；`main` 和 `develop` 受保护。

| 分支 | 来源 | 合并到 | 用途 |
|---|---|---|---|
| `main` | — | — | 仅包含已发布的代码；每次合并都是一个打了标签的版本 |
| `develop` | `main` | — | 集成分支——**请将 PR 提交到这里** |
| `feature/<name>` | `develop` | `develop` | 新功能 |
| `bugfix/<name>` | `develop` / `release/*` | 同来源 | 非紧急修复 |
| `docs/<name>`、`chore/<name>` | `develop` | `develop` | 文档、工具 |
| `release/X.Y.Z` | `develop` | `main` + `develop` | 版本发布前的稳定（维护者） |
| `hotfix/<name>` | `main` | `main` + `develop` | 已发布版本的紧急修复（维护者） |

1. Fork 仓库，然后从 `develop` 创建分支：`git switch -c feature/volume-badge develop`。
2. 按照下面的规则提交 commit。
3. 运行下面的检查。
4. 使用模板向 **`develop`** 提交 pull request；任何界面改动都请附上截图或简短的录屏。

## 提交信息

每个 commit 和 PR 标题都**必须**遵循
[Conventional Commits 1.0](https://www.conventionalcommits.org/en/v1.0.0/)（由 CI 检查）：

```
<type>(<optional scope>)!: <description>

[optional body: why]

[optional footer: BREAKING CHANGE: ..., Fixes #123]
```

- 类型：`feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`。
- 范围（建议）：`grid` `media` `notifications` `navigation` `weather` `speed` `dock`
  `settings` `widget` `drawer` `i18n` `deps` `release`。
- 使用英文、祈使语气、小写开头、末尾不加句号，标题行 ≤ 100 个字符。
- 示例：`feat(dock): add volume badge`、`fix(grid): keep items inside bounds on resize`、
  `docs: translate development guide to zh-CN`。

每个克隆只需启用一次本地 hook，这样不合规的提交信息会在推送前被拒绝：

```bash
git config core.hooksPath .githooks
```

## 版本发布

维护者通过 `release/X.Y.Z` 分支（版本号更新 + CHANGELOG）发布版本，将其合并到 `main`，打上
`vX.Y.Z` 标签，再合并回 `develop`。该标签会触发 Release 工作流，构建已签名的 APK 并发布到
GitHub Releases。版本号遵循 [SemVer](https://semver.org/)；变更记录在
[CHANGELOG.md](CHANGELOG.md)（英文）中。

## 提交 PR 之前

```bash
./gradlew test            # must pass
./gradlew assembleDebug   # must build
./gradlew lintDebug       # no errors
```

如果你改动了手势或网格，还需要在模拟器上运行 `./gradlew connectedAndroidTest`。

## 代码风格

- **语言：** 所有代码注释、KDoc、提交信息、技能和代理均使用**英文**。界面字符串可以本地化。
- **Kotlin：** 官方代码风格（`kotlin.code.style=official`），4 空格缩进，使用尾随逗号。
- **架构：** 遵守 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 中的分层。`domain` 不得导入
  `android.*`。可测试的逻辑放在 `core/pure` 中，并在 `app/src/test` 中用 JVM 测试覆盖。
- **注释解释*为什么*，**而不是做了什么。保持简短。
- **适合驾驶的界面：** 触控目标 ≥ 48 dp，浅色和深色主题下对比度都清晰可读，主题表面上的文字不要使用硬编码颜色（使用 `MaterialTheme.colorScheme`）。
- **不要引入新依赖**，除非先在 issue 中讨论过。
- **设置：** 新选项的默认值应保持当前行为。

## Pull request 检查清单

- [ ] 分支遵循 gitflow 并以 `develop` 为目标；commit 和 PR 标题遵循 Conventional Commits
- [ ] `./gradlew test lintDebug assembleDebug` 通过
- [ ] `core/pure` 中的新逻辑有单元测试
- [ ] 注释使用英文；文档已更新（如果可以，也更新翻译）
- [ ] 界面改动附有截图 / 录屏，并在浅色**和**深色主题下测试过
- [ ] 没有提交任何机密信息（API 密钥、密钥库）

## AI 辅助贡献

仓库中提供了 [CLAUDE.md](CLAUDE.md) 以及位于 [.claude/](.claude) 中、供 Claude Code 使用的技能和代理。它们包含了上述约定；但你仍需对助手生成的任何内容负责审查和测试。

## 行为准则与安全

所有参与者都需遵守[行为准则](CODE_OF_CONDUCT.md)（英文）。请按照 [SECURITY.md](SECURITY.md)（英文）中的说明私下报告安全漏洞，切勿在公开 issue 中报告。

## 许可证

参与贡献即表示你同意你的贡献按照 [Apache License 2.0](LICENSE) 授权。
