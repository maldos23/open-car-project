# Contribuir a Open Car Project

[English](CONTRIBUTING.md) · **Español** · [简体中文](CONTRIBUTING.zh-CN.md)

🚗 ¡Gracias por ayudar a construir un launcher para el tablero! Los reportes de errores,
traducciones, documentación, módulos nuevos y correcciones son bienvenidos.

## 🧭 Formas de contribuir

- **Reportar un error**: abre un issue con los pasos para reproducirlo, el dispositivo/emulador
  y la versión de Android, y la salida de `adb logcat` si la app se cerró inesperadamente.
- **Sugerir una función**: describe el escenario de conducción que mejora.
- **Traducir**: los textos de la interfaz están en `res/values*/strings.xml` (el inglés es el
  respaldo en `values`). Para un idioma nuevo, agrega una carpeta `values-xx` y su entrada en
  `AppLanguage`. Las traducciones de la documentación van junto al archivo en inglés
  (`*.es.md`, `*.zh-CN.md`).
- **Código**: elige un issue abierto o, para cambios más grandes, propón uno primero.

## ⚡ Preparar el entorno

Dos caminos: hazlo tú mismo o deja que un asistente de IA te guíe.

### 🧑‍🔧 A mano (~15 min)

```bash
git clone https://github.com/<you>/open-car-project.git
cd open-car-project                      # default branch is develop
git remote add upstream https://github.com/maldos23/open-car-project.git
git config core.hooksPath .githooks      # rejects bad commit messages locally
export ANDROID_HOME=/path/to/android-sdk # or sdk.dir=... in local.properties (never commit it)
./gradlew test lintDebug assembleDebug   # same checks CI runs; first run downloads Gradle
```

Necesitas JDK 17+ y los paquetes del SDK de [DEVELOPMENT §1](docs/DEVELOPMENT.es.md#1-entorno).
Emulador: [§3](docs/DEVELOPMENT.es.md#3-configuración-del-emulador). En Apple Silicon, usa la
imagen arm64 de API 35.
Instalar y fijar como HOME: [§2](docs/DEVELOPMENT.es.md#2-compilar-e-instalar).
Permisos: [§4](docs/DEVELOPMENT.es.md#4-primera-ejecución-permisos).

### 🤖 Con un asistente de IA

Abre el repositorio en Claude Code (o en cualquier asistente que pueda ejecutar comandos de
shell) y pega el Prompt 1 de
[Contribuciones asistidas por IA](#contribuciones-asistidas-por-ia). Tú apruebas cada comando
que ejecuta.

## Modelo de ramas (gitflow)

Gitflow es **obligatorio** y la CI lo verifica (`scripts/check-branch.sh`); `main` y `develop`
están protegidas.

| Rama | Desde | Se fusiona en | Propósito |
|---|---|---|---|
| `main` | — | — | Solo código publicado; cada fusión es una versión etiquetada |
| `develop` | `main` | — | Rama de integración: **dirige aquí tus PR** |
| `feature/<name>` | `develop` | `develop` | Funciones nuevas |
| `bugfix/<name>` | `develop` / `release/*` | la misma | Correcciones no urgentes |
| `docs/<name>`, `chore/<name>` | `develop` | `develop` | Documentación, herramientas |
| `release/X.Y.Z` | `develop` | `main` + `develop` | Estabilización de una versión (mantenedores) |
| `hotfix/<name>` | `main` | `main` + `develop` | Corrección urgente de una versión (mantenedores) |

1. Haz un fork y crea una rama desde `develop`: `git switch -c feature/volume-badge develop`.
2. Haz commits siguiendo las reglas de abajo.
3. Ejecuta las verificaciones de abajo.
4. Abre un pull request **hacia `develop`** usando la plantilla; incluye capturas de pantalla o
   una grabación corta de la pantalla para cualquier cambio en la interfaz.

## Mensajes de commit

[Conventional Commits 1.0](https://www.conventionalcommits.org/en/v1.0.0/) son **obligatorios**
en cada commit y en el título del PR (se verifican en la CI):

```
<type>(<optional scope>)!: <description>

[optional body: why]

[optional footer: BREAKING CHANGE: ..., Fixes #123]
```

- Tipos: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`.
- Ámbitos (sugeridos): `grid` `media` `notifications` `navigation` `weather` `speed` `dock`
  `settings` `widget` `drawer` `i18n` `deps` `release`.
- En inglés, en modo imperativo, empezando en minúscula, sin punto final y con un encabezado
  de ≤ 100 caracteres.
- Ejemplos: `feat(dock): add volume badge`, `fix(grid): keep items inside bounds on resize`,
  `docs: translate development guide to zh-CN`.

Activa el hook local una vez por clon para que los mensajes incorrectos se rechacen antes de
hacer push:

```bash
git config core.hooksPath .githooks
```

## Versiones

Los mantenedores preparan cada versión con una rama `release/X.Y.Z` (incremento de versión +
CHANGELOG), la fusionan en `main`, crean la etiqueta `vX.Y.Z` y la fusionan de vuelta en
`develop`. La etiqueta dispara el workflow Release, que compila el APK firmado y lo publica en
GitHub Releases. Las versiones siguen [SemVer](https://semver.org/); los cambios se registran en
[CHANGELOG.md](CHANGELOG.md) (en inglés).

## Antes de abrir un PR

```bash
./gradlew test            # must pass
./gradlew assembleDebug   # must build
./gradlew lintDebug       # no errors
```

Si modificaste los gestos o la cuadrícula, ejecuta también `./gradlew connectedAndroidTest` en
un emulador.

Revisa un mensaje primero: `scripts/check-commit-msg.sh --text "feat(dock): add volume badge"`.

¿Tocaste el sitio web? Previsualízalo en local:
`rm -rf /tmp/ocp-site && mkdir -p /tmp/ocp-site && cp site/index.html /tmp/ocp-site/ && rsync -a --exclude legacy docs/screenshots/ /tmp/ocp-site/screenshots/ && python3 -m http.server -d /tmp/ocp-site 8000`

## Estilo de código

- **Idioma:** todos los comentarios de código, KDoc, mensajes de commit, skills y agentes van
  en **inglés**. Todo texto visible pasa por `strings.xml` (sin literales en Kotlin), con cada
  llave en los cinco idiomas: `values`, `values-es`, `values-zh-rCN`, `values-pt`, `values-fr`.
- **Kotlin:** estilo de código oficial (`kotlin.code.style=official`), sangría de 4 espacios,
  comas finales.
- **Arquitectura:** respeta las capas de [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (en
  inglés). `domain` no debe importar `android.*`. Pon la lógica comprobable en `core/pure` y
  cúbrela con una prueba de JVM en `app/src/test`.
- **Los comentarios explican el *porqué*,** no el qué. Mantenlos breves.
- **Interfaz apta para conducir:** áreas táctiles ≥ 48 dp, contraste legible en los temas claro
  y oscuro, sin colores fijos para el texto sobre superficies del tema (usa
  `MaterialTheme.colorScheme`).
- **Sin dependencias nuevas** sin antes discutirlas en el issue.
- **Ajustes:** las opciones nuevas llevan un valor predeterminado que conserva el
  comportamiento actual.

## Lista de verificación del pull request

- [ ] La rama sigue gitflow y apunta a `develop`; los commits y el título del PR siguen Conventional Commits
- [ ] `./gradlew test lintDebug assembleDebug` pasa
- [ ] La lógica nueva en `core/pure` tiene pruebas unitarias
- [ ] Comentarios en inglés; documentación actualizada (y sus traducciones, si puedes)
- [ ] Capturas / grabación para cambios en la interfaz, probados en tema claro **y** oscuro
- [ ] No se subieron secretos (claves de API, keystores)

## Contribuciones asistidas por IA

El repositorio incluye [CLAUDE.md](CLAUDE.md), además de skills y agentes en [.claude/](.claude)
para Claude Code. Estos codifican las convenciones anteriores; aun así, eres responsable de
revisar y probar todo lo que produzca un asistente.

### 🧠 Cómo se cargan

- **`CLAUDE.md`** se lee automáticamente al inicio de cada sesión de Claude Code en este
  repositorio. Contiene las reglas obligatorias.
- **Skills** viven en `.claude/skills/<name>/SKILL.md`. Al principio Claude solo ve el `name` y
  la `description` de cada skill. Las instrucciones completas se cargan cuando escribes
  `/<name>` (por ejemplo `/run-on-emulator`) o cuando tu petición coincide con la descripción
  ("agrega un interruptor para…" carga `add-setting`).
- **Agentes** viven en `.claude/agents/<name>.md`. Son subasistentes, cada uno con sus propias
  instrucciones y una lista limitada de herramientas. Claude le pasa el trabajo a uno cuando la
  tarea coincide, o cuando lo nombras ("usa el agente android-reviewer en mi diff"). `/agents`
  los lista.
- Otros asistentes no cargan estos archivos automáticamente. Diles qué archivo leer (ver el
  Prompt 4).

### 🗂️ Skills y agentes

| | Nombre | Tipo | Úsalo cuando… |
|---|---|---|---|
| 🌿 | `git-workflow` | skill | creas una rama, haces commit, abres un PR, preparas una versión o un hotfix (gitflow + Conventional Commits) |
| 📱 | `run-on-emulator` | skill | quieres compilar, instalar, abrir, dar permisos y capturar la pantalla del launcher |
| 🧪 | `run-tests` | skill | terminas cualquier cambio de código: pruebas de JVM, compilación, lint, prueba instrumentada de arrastre (desinstala la app) |
| 🎛️ | `debug-tools` | skill | pruebas la tarjeta multimedia, la isla de notificaciones, la escena de velocidad, el clima o la navegación con entradas falsas en lugar de apps reales o un auto |
| 🧱 | `add-builtin-module` | skill | agregas una tarjeta/módulo/mosaico nuevo que se pueda colocar, mover y redimensionar en la cuadrícula |
| ⚙️ | `add-setting` | skill | una función necesita un interruptor u opción, desde el modelo pasando por DataStore y el ViewModel hasta la hoja de ajustes |
| 🔍 | `android-reviewer` | agente · Read/Grep/Glob/Bash | después de implementar, antes de un PR: capas, errores de Compose/estado, fugas de ciclo de vida, interfaz para conducir, comentarios en inglés |
| 👀 | `emulator-verifier` | agente · Read/Bash/Glob/Grep | verificas un cambio de interfaz de punta a punta: instala, ejecuta el escenario con adb, reporta capturas |
| 🌐 | `docs-translator` | agente · Read/Write/Edit/Glob/Grep | cambió un documento en inglés (README, CONTRIBUTING, DEVELOPMENT) y las copias `.es.md`/`.zh-CN.md` deben ponerse al día |

### 💬 Prompts para pegar

Puedes escribir el prompt en español; el asistente responde en tu idioma.

Reemplaza las partes `<...>`, pega y fíjate en lo que ejecuta. 👇

**Prompt 1: 🛠️ Preparar y verificar mi equipo** (Claude Code)

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

**Prompt 2: 📱 Ejecutarlo en un emulador**

```
Set up an emulator following docs/DEVELOPMENT.md §3 (tablet AVD, landscape; on Apple Silicon the API 35 arm64 image),
asking before downloading system images. Then use the run-on-emulator skill to build, install, make it the HOME app,
grant notification access and launch it. Take a screenshot, open it and tell me what is on screen.
```

**Prompt 3: 🌱 Empezar mi primera contribución**

```
I want to work on: <describe the issue or paste its URL>.
Follow the git-workflow skill: create the right branch (feature/, bugfix/, docs/ or chore/, kebab-case) from an up-to-date develop.
Before coding, give me a short plan: files you'll touch and the project skill that fits (add-setting, add-builtin-module), if any.
Implement it following CLAUDE.md: user-visible text in all 5 strings.xml locales, a JVM test for core/pure logic,
a CHANGELOG "Unreleased" entry if users will notice it. Run the run-tests skill, then the android-reviewer agent on the diff,
and fix what it finds. Propose a Conventional Commit message, check it with scripts/check-commit-msg.sh --text,
and commit only after I approve. Don't push or open a PR unless I ask.
```

**Prompt 4: 🤖 Cualquier otro asistente (ChatGPT, Copilot, Cursor…)**

```
You are helping me contribute to Open Car Project, an Android launcher (Kotlin, Jetpack Compose).
Read these repo files first and treat them as the source of truth: CLAUDE.md, CONTRIBUTING.md, docs/DEVELOPMENT.md,
and the skill that matches my task in .claude/skills/<name>/SKILL.md (e.g. git-workflow, run-on-emulator, add-setting).
Don't invent commands that aren't in them. My OS is <macOS/Linux/Windows>; my task is: <...>.
Give me one command at a time and wait for my output before continuing.
```

## Código de conducta y seguridad

Todas las personas que participan siguen el [Código de conducta](CODE_OF_CONDUCT.md) (en
inglés). Reporta las vulnerabilidades de forma privada como se describe en
[SECURITY.md](SECURITY.md) (en inglés), nunca en un issue público.

## Licencia

Al contribuir, aceptas que tus contribuciones se licencien bajo la
[Apache License 2.0](LICENSE).
