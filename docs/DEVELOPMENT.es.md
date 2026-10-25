# Guía de desarrollo

[English](DEVELOPMENT.md) · **Español** · [简体中文](DEVELOPMENT.zh-CN.md)

Cómo compilar, ejecutar, depurar y probar Open Car Project.

- [1. Entorno](#1-entorno)
- [2. Compilar e instalar](#2-compilar-e-instalar)
- [3. Configuración del emulador](#3-configuración-del-emulador)
- [4. Primera ejecución (permisos)](#4-primera-ejecución-permisos)
- [5. Depuración](#5-depuración)
- [6. Simular entradas del mundo real](#6-simular-entradas-del-mundo-real)
- [7. Pruebas](#7-pruebas)
- [8. Solución de problemas](#8-solución-de-problemas)

## 1. Entorno

| Herramienta | Versión |
|---|---|
| JDK | 17 o posterior (probado con Temurin 21) |
| Android SDK | `platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools`, `emulator` |
| Gradle | 9.8 mediante el wrapper (`./gradlew`); no hace falta instalarlo globalmente |
| Android Gradle Plugin | 9.4.x |
| Kotlin | 2.4.x |

La app tiene como objetivo **API 37 (Android 17)** y funciona en **API 26+**. Los detalles
de la toolchain están en [BUILD_NOTES.md](BUILD_NOTES.md) (en inglés).

Indícale a Gradle dónde está tu SDK de cualquiera de estas formas:

```bash
export ANDROID_HOME=/path/to/android-sdk
# or create local.properties with:  sdk.dir=/path/to/android-sdk
```

## 2. Compilar e instalar

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew installDebug         # build + install on the connected device
./gradlew assembleRelease      # unsigned release APK (sign it yourself)
```

Ábrela y configúrala como la app de inicio predeterminada:

```bash
adb shell am start -n org.opencarproject.launcher/.presentation.LauncherActivity
adb shell cmd role add-role-holder android.app.role.HOME org.opencarproject.launcher
```

## 3. Configuración del emulador

Un AVD de tablet en horizontal es lo más parecido a una unidad principal.

```bash
# Apple Silicon: use the API 35 arm64 image (API 36/37 images fall back to software
# rendering on arm64 and are very slow). The app runs fine on API 35.
sdkmanager "system-images;android-35;google_apis;arm64-v8a"
avdmanager create avd -n opencar_tablet -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_tablet
emulator -avd opencar_tablet -gpu host &
adb wait-for-device
```

**Consejo sobre el botón de inicio:** con la navegación por gestos, el Pixel Launcher
(Quickstep) del emulador maneja el deslizamiento de "inicio" y no siempre lo cede a un launcher
de terceros. Cambia el emulador a la navegación de 3 botones para hacer pruebas confiables:

```bash
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
```

## 4. Primera ejecución (permisos)

La app solicita los permisos de ubicación y de estado del teléfono al iniciar. También se
pueden otorgar desde adb:

```bash
PKG=org.opencarproject.launcher
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.READ_PHONE_STATE
# Notification access (media player, notifications, navigation next-turn):
adb shell cmd notification allow_listener $PKG/$PKG.data.source.LauncherNotificationListener
```

## 5. Depuración

- **Android Studio:** abre la raíz del proyecto, selecciona la configuración `app` y presiona
  *Debug*. El launcher es una actividad normal, así que los puntos de interrupción, el Layout
  Inspector (Compose) y el Profiler funcionan.
- **Registros:** `adb logcat --pid=$(adb shell pidof org.opencarproject.launcher)`
- **Cierres inesperados:** `adb logcat -b crash`
- **Capturas / grabaciones de pantalla:**
  ```bash
  adb exec-out screencap -p > screen.png
  adb shell screenrecord --time-limit 30 /sdcard/demo.mp4 && adb pull /sdcard/demo.mp4
  ```
- **Inspeccionar el estado guardado** (archivos de DataStore, solo en compilaciones de
  depuración):
  ```bash
  adb shell run-as org.opencarproject.launcher ls files/datastore/
  # launcher_layout.preferences_pb  -> grid + placed items (JSON inside)
  # launcher_settings.preferences_pb -> settings
  ```
- **Restablecer a un estado limpio:** `adb shell pm clear org.opencarproject.launcher`

## 6. Simular entradas del mundo real

### Sesión multimedia (solo en compilaciones de depuración)

`FakeMediaSessionReceiver` (en `app/src/debug/`) publica un reproductor simulado para que
puedas probar la tarjeta multimedia y la isla de "reproduciendo ahora" sin otras apps:

```bash
R="-n org.opencarproject.launcher/.debug.FakeMediaSessionReceiver -a org.opencarproject.launcher.DEBUG_MEDIA"
adb shell am broadcast $R --es title "Song" --es artist "Artist" --es album "Album" \
  --ei color 15022389 --el duration 210000 --el position 30000          # play
adb shell am broadcast $R --es state pause                              # pause
adb shell am broadcast $R --es state stop                               # remove session
```

### Fondo de pantalla (solo en compilaciones de depuración)

```bash
adb shell am broadcast -n org.opencarproject.launcher/.debug.FakeMediaSessionReceiver \
  -a org.opencarproject.launcher.DEBUG_WALLPAPER
```

### Notificaciones

```bash
adb shell cmd notification post -t "Title" my_tag "Body text"
adb shell cmd notification post -S bigtext -t "Long" tag2 "A very long text ..."
adb shell cmd notification post -S messaging --conversation "Team" \
  --message "Ana:Are you coming?" --message "Luis:On my way" -t "Team" chat "Ana: ..."
adb shell cmd notification cancel my_tag
```

### Ubicación y velocidad del GPS

`adb emu geo fix` no llega a las apps en algunas imágenes de API 35; usa un proveedor de prueba
en su lugar:

```bash
adb shell appops set com.android.shell android:mock_location allow
adb shell cmd location providers add-test-provider gps
adb shell cmd location providers set-test-provider-enabled gps true
adb shell cmd location providers set-test-provider-location gps --location 19.4326,-99.1332
```

La escena de velocidad calcula la velocidad a partir de posiciones consecutivas cuando la
posición no incluye velocidad. Para simular que conduces, envía una posición por segundo,
avanzando hacia el norte `speed_m_s / 111320` grados de latitud:

```python
# drive.py — 40 km/h for 20 s
import subprocess, time
lat = 19.43
for _ in range(20):
    lat += (40 / 3.6) / 111320
    subprocess.run(["adb", "shell", "cmd", "location", "providers", "set-test-provider-location",
                    "gps", "--location", f"{lat:.7f},-99.1332"])
    time.sleep(1)
```

### Indicaciones de navegación

Inicia una navegación real de Google Maps (requiere el proveedor de ubicación de arriba):

```bash
adb shell am start -a android.intent.action.VIEW -d "google.navigation:q=19.4270,-99.1677" \
  -p com.google.android.apps.maps
```

## 7. Pruebas

```bash
./gradlew test                     # unit tests (JVM): grid math, codecs, units, colors, use cases
./gradlew connectedAndroidTest     # instrumented UI test (drag gesture) on a device/emulator
```

- La lógica pura vive en `core/pure` y está cubierta por pruebas de JVM en `app/src/test`. Pon
  ahí la lógica nueva que no sea de interfaz para poder probarla sin un dispositivo.
- `connectedAndroidTest` **desinstala la app al terminar**, lo que borra tu diseño y tus
  permisos en ese dispositivo. Después, vuelve a instalarla y a otorgar los permisos
  (secciones 2 y 4).

## 8. Solución de problemas

| Síntoma | Dónde buscar |
|---|---|
| No aparece como app de inicio | `AndroidManifest.xml` (categorías `HOME` + `DEFAULT`); asigna el rol (sección 2) |
| Faltan widgets / no se agregan | `SystemCatalogSource`, el flujo de vinculación en `LauncherActivity` (`ACTION_APPWIDGET_BIND`) |
| La tarjeta multimedia dice "Nada sonando" | No se otorgó el acceso a notificaciones; `MediaSessionSource` |
| No hay clima / "Activar clima" | Permiso de ubicación o red; `WeatherSource` |
| La velocidad se queda en 0 | Permiso de ubicación precisa, proveedor de GPS; `SpeedReader` |
| Se pierde el diseño tras una actualización | `JsonLayoutCodec` (los tipos de elementos desconocidos se descartan, no el diseño completo) |
| El botón de inicio no hace nada (emulador) | Limitación de la navegación por gestos; consulta la sección 3 |

Más información en [ARCHITECTURE.md](ARCHITECTURE.md#where-to-look-when-something-breaks) (en
inglés).
