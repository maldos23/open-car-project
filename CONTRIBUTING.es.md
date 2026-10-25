# Contribuir a Open Car Project

[English](CONTRIBUTING.md) · **Español** · [简体中文](CONTRIBUTING.zh-CN.md)

¡Gracias por ayudar! Los reportes de errores, traducciones, documentación, módulos nuevos y
correcciones son bienvenidos.

## Formas de contribuir

- **Reportar un error**: abre un issue con los pasos para reproducirlo, el dispositivo/emulador
  y la versión de Android, y la salida de `adb logcat` si la app se cerró inesperadamente.
- **Sugerir una función**: describe el escenario de conducción que mejora.
- **Traducir**: por ahora la interfaz está en español y escrita directamente en Compose. Mover
  los textos a `res/values/strings.xml` (y agregar `values-en`, `values-zh-rCN`, …) es un
  excelente primer PR. Las traducciones de la documentación van junto al archivo en inglés
  (`*.es.md`, `*.zh-CN.md`).
- **Código**: elige un issue abierto o, para cambios más grandes, propón uno primero.

## Preparar el entorno

Sigue [docs/DEVELOPMENT.es.md](docs/DEVELOPMENT.es.md): cubre el SDK, el emulador, los
permisos, las herramientas de depuración y las pruebas.

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

## Estilo de código

- **Idioma:** todos los comentarios de código, KDoc, mensajes de commit, skills y agentes van
  en **inglés**. Los textos de la interfaz pueden localizarse.
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

## Código de conducta y seguridad

Todas las personas que participan siguen el [Código de conducta](CODE_OF_CONDUCT.md) (en
inglés). Reporta las vulnerabilidades de forma privada como se describe en
[SECURITY.md](SECURITY.md) (en inglés), nunca en un issue público.

## Licencia

Al contribuir, aceptas que tus contribuciones se licencien bajo la
[Apache License 2.0](LICENSE).
