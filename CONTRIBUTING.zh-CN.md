# 为 Open Car Project 做贡献

[English](CONTRIBUTING.md) · [Español](CONTRIBUTING.es.md) · **简体中文**

🚗 感谢你帮助打造一款车机启动器！欢迎错误报告、翻译、文档、新模块和修复。

## 🧭 贡献方式

- **报告错误**——提交 issue，附上复现步骤、设备/模拟器及 Android 版本；如果发生崩溃，请附上
  `adb logcat` 输出。
- **建议功能**——描述它能改善的驾驶场景。
- **翻译**——界面字符串位于 `res/values*/strings.xml`（英语是 `values` 中的默认语言）。
  要添加新语言，请新建 `values-xx` 文件夹并添加对应的 `AppLanguage` 条目。
  文档翻译与英文文件放在一起（`*.es.md`、`*.zh-CN.md`）。
- **代码**——挑选一个未关闭的 issue；较大的改动请先提出 issue。

## ⚡ 环境准备

两种方式：自己动手，或者让 AI 助手一步步带你完成。

### 🧑‍🔧 手动配置（约 15 分钟）

```bash
git clone https://github.com/<you>/open-car-project.git
cd open-car-project                      # default branch is develop
git remote add upstream https://github.com/maldos23/open-car-project.git
git config core.hooksPath .githooks      # rejects bad commit messages locally
export ANDROID_HOME=/path/to/android-sdk # or sdk.dir=... in local.properties (never commit it)
./gradlew test lintDebug assembleDebug   # same checks CI runs; first run downloads Gradle
```

你需要 JDK 17+ 以及 [DEVELOPMENT §1](docs/DEVELOPMENT.zh-CN.md#1-环境) 中列出的 SDK 软件包。
模拟器：[§3](docs/DEVELOPMENT.zh-CN.md#3-模拟器设置)。在 Apple Silicon 上请使用 API 35 arm64 镜像。
安装并设为 HOME：[§2](docs/DEVELOPMENT.zh-CN.md#2-构建与安装)。
权限：[§4](docs/DEVELOPMENT.zh-CN.md#4-首次运行与权限)。

### 🤖 使用 AI 助手

在 Claude Code（或任何能运行 shell 命令的助手）中打开仓库，然后粘贴
[AI 辅助贡献](#ai-辅助贡献) 中的 Prompt 1。它运行的每条命令都需要你批准。

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

先检查提交信息：`scripts/check-commit-msg.sh --text "feat(dock): add volume badge"`。

改动了网站？在本地预览：
`rm -rf /tmp/ocp-site && mkdir -p /tmp/ocp-site && cp site/index.html /tmp/ocp-site/ && rsync -a --exclude legacy docs/screenshots/ /tmp/ocp-site/screenshots/ && python3 -m http.server -d /tmp/ocp-site 8000`

## 代码风格

- **语言：** 所有代码注释、KDoc、提交信息、技能和代理均使用**英文**。所有可见文字都通过 `strings.xml`（Kotlin 中不写字面量），每个键都要出现在五种语言中：`values`、`values-es`、`values-zh-rCN`、`values-pt`、`values-fr`。
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

### 🧠 加载方式

- **`CLAUDE.md`** 会在本仓库中每次 Claude Code 会话开始时自动读取，其中包含必须遵守的规则。
- **技能（skills）** 位于 `.claude/skills/<name>/SKILL.md`。一开始 Claude 只能看到每个技能的
  `name` 和 `description`。当你输入 `/<name>`（例如 `/run-on-emulator`），或你的请求与描述相符时
  （“为……加一个开关”会加载 `add-setting`），完整说明才会加载。
- **代理（agents）** 位于 `.claude/agents/<name>.md`。它们是子助手，各自有自己的说明和受限的工具列表。
  当任务相符，或你点名使用时（“用 android-reviewer 代理检查我的 diff”），Claude 会把工作交给它。
  `/agents` 会列出所有代理。
- 其他助手不会自动加载这些文件。请告诉它们要读取哪个文件（见 Prompt 4）。

### 🗂️ 技能与代理

| | 名称 | 类型 | 适用场景 |
|---|---|---|---|
| 🌿 | `git-workflow` | 技能 | 创建分支、提交 commit、发起 PR、发布版本或 hotfix（gitflow + Conventional Commits） |
| 📱 | `run-on-emulator` | 技能 | 构建、安装、启动启动器，授予权限并截图 |
| 🧪 | `run-tests` | 技能 | 完成任何代码改动时：JVM 测试、构建、lint、插桩拖动测试（会卸载应用） |
| 🎛️ | `debug-tools` | 技能 | 用模拟输入代替真实应用或汽车，测试媒体卡片、通知岛、速度场景、天气或导航 |
| 🧱 | `add-builtin-module` | 技能 | 添加用户可以在网格上放置、移动和调整大小的新卡片/模块/磁贴 |
| ⚙️ | `add-setting` | 技能 | 某个功能需要开关或选项，从模型经 DataStore 和 ViewModel 一直到设置面板 |
| 🔍 | `android-reviewer` | 代理 · Read/Grep/Glob/Bash | 实现之后、发起 PR 之前：分层、Compose/状态错误、生命周期泄漏、驾驶界面、英文注释 |
| 👀 | `emulator-verifier` | 代理 · Read/Bash/Glob/Grep | 端到端验证界面改动：安装、通过 adb 运行场景、报告截图 |
| 🌐 | `docs-translator` | 代理 · Read/Write/Edit/Glob/Grep | 英文文档（README、CONTRIBUTING、DEVELOPMENT）有改动，需要同步 `.es.md`/`.zh-CN.md` 副本 |

### 💬 可直接粘贴的提示词

你可以用中文书写提示词；助手会用你的语言回复。

替换 `<...>` 部分，粘贴，然后留意它运行的命令。👇

**Prompt 1：🛠️ 配置并检查我的电脑**（Claude Code）

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

**Prompt 2：📱 在模拟器上运行**

```
Set up an emulator following docs/DEVELOPMENT.md §3 (tablet AVD, landscape; on Apple Silicon the API 35 arm64 image),
asking before downloading system images. Then use the run-on-emulator skill to build, install, make it the HOME app,
grant notification access and launch it. Take a screenshot, open it and tell me what is on screen.
```

**Prompt 3：🌱 开始我的第一次贡献**

```
I want to work on: <describe the issue or paste its URL>.
Follow the git-workflow skill: create the right branch (feature/, bugfix/, docs/ or chore/, kebab-case) from an up-to-date develop.
Before coding, give me a short plan: files you'll touch and the project skill that fits (add-setting, add-builtin-module), if any.
Implement it following CLAUDE.md: user-visible text in all 5 strings.xml locales, a JVM test for core/pure logic,
a CHANGELOG "Unreleased" entry if users will notice it. Run the run-tests skill, then the android-reviewer agent on the diff,
and fix what it finds. Propose a Conventional Commit message, check it with scripts/check-commit-msg.sh --text,
and commit only after I approve. Don't push or open a PR unless I ask.
```

**Prompt 4：🤖 其他任何助手（ChatGPT、Copilot、Cursor……）**

```
You are helping me contribute to Open Car Project, an Android launcher (Kotlin, Jetpack Compose).
Read these repo files first and treat them as the source of truth: CLAUDE.md, CONTRIBUTING.md, docs/DEVELOPMENT.md,
and the skill that matches my task in .claude/skills/<name>/SKILL.md (e.g. git-workflow, run-on-emulator, add-setting).
Don't invent commands that aren't in them. My OS is <macOS/Linux/Windows>; my task is: <...>.
Give me one command at a time and wait for my output before continuing.
```

## 行为准则与安全

所有参与者都需遵守[行为准则](CODE_OF_CONDUCT.md)（英文）。请按照 [SECURITY.md](SECURITY.md)（英文）中的说明私下报告安全漏洞，切勿在公开 issue 中报告。

## 许可证

参与贡献即表示你同意你的贡献按照 [Apache License 2.0](LICENSE) 授权。
