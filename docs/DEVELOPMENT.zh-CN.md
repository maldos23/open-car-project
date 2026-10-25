# 开发指南

[English](DEVELOPMENT.md) · [Español](DEVELOPMENT.es.md) · **简体中文**

如何构建、运行、调试和测试 Open Car Project。

- [1. 环境](#1-环境)
- [2. 构建与安装](#2-构建与安装)
- [3. 模拟器设置](#3-模拟器设置)
- [4. 首次运行与权限](#4-首次运行与权限)
- [5. 调试](#5-调试)
- [6. 模拟真实输入](#6-模拟真实输入)
- [7. 测试](#7-测试)
- [8. 故障排除](#8-故障排除)

## 1. 环境

| 工具 | 版本 |
|---|---|
| JDK | 17 或更高（已用 Temurin 21 测试） |
| Android SDK | `platforms;android-37.0`、`build-tools;37.0.0`、`platform-tools`、`emulator` |
| Gradle | 通过 wrapper 使用 9.8（`./gradlew`）——无需全局安装 |
| Android Gradle Plugin | 9.4.x |
| Kotlin | 2.4.x |

应用的目标版本为 **API 37 (Android 17)**，可在 **API 26+** 上运行。工具链详情请参见
[BUILD_NOTES.md](BUILD_NOTES.md)。

用以下任一方式让 Gradle 找到你的 SDK：

```bash
export ANDROID_HOME=/path/to/android-sdk
# or create local.properties with:  sdk.dir=/path/to/android-sdk
```

## 2. 构建与安装

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew installDebug         # build + install on the connected device
./gradlew assembleRelease      # unsigned release APK (sign it yourself)
```

启动应用并将其设为默认主屏幕应用：

```bash
adb shell am start -n org.opencarproject.launcher/.presentation.LauncherActivity
adb shell cmd role add-role-holder android.app.role.HOME org.opencarproject.launcher
```

## 3. 模拟器设置

横屏的平板 AVD 是最接近车机的环境。

```bash
# Apple Silicon: use the API 35 arm64 image (API 36/37 images fall back to software
# rendering on arm64 and are very slow). The app runs fine on API 35.
sdkmanager "system-images;android-35;google_apis;arm64-v8a"
avdmanager create avd -n opencar_tablet -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_tablet
emulator -avd opencar_tablet -gpu host &
adb wait-for-device
```

**主屏幕按钮提示：** 使用手势导航时，模拟器自带的 Pixel Launcher (Quickstep) 会处理“返回主屏幕”手势，并不总是将其交给第三方启动器。为了可靠地测试，请把模拟器切换为三按钮导航：

```bash
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
```

## 4. 首次运行与权限

应用在启动时会请求位置和电话状态权限。也可以通过 adb 授予这些权限：

```bash
PKG=org.opencarproject.launcher
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.READ_PHONE_STATE
# Notification access (media player, notifications, navigation next-turn):
adb shell cmd notification allow_listener $PKG/$PKG.data.source.LauncherNotificationListener
```

## 5. 调试

- **Android Studio：** 打开项目根目录，选择 `app` 配置并点击 *Debug*。该启动器是一个普通的
  Activity，因此断点、Layout Inspector (Compose) 和 Profiler 都能正常使用。
- **日志：** `adb logcat --pid=$(adb shell pidof org.opencarproject.launcher)`
- **崩溃：** `adb logcat -b crash`
- **截图 / 录屏：**
  ```bash
  adb exec-out screencap -p > screen.png
  adb shell screenrecord --time-limit 30 /sdcard/demo.mp4 && adb pull /sdcard/demo.mp4
  ```
- **查看已保存的状态**（DataStore 文件，仅限调试版）：
  ```bash
  adb shell run-as org.opencarproject.launcher ls files/datastore/
  # launcher_layout.preferences_pb  -> grid + placed items (JSON inside)
  # launcher_settings.preferences_pb -> settings
  ```
- **重置为干净状态：** `adb shell pm clear org.opencarproject.launcher`

## 6. 模拟真实输入

### 媒体会话（仅限调试版）

`FakeMediaSessionReceiver`（位于 `app/src/debug/`）会发布一个模拟播放器，这样无需其他应用即可测试媒体卡片和“正在播放”通知岛：

```bash
R="-n org.opencarproject.launcher/.debug.FakeMediaSessionReceiver -a org.opencarproject.launcher.DEBUG_MEDIA"
adb shell am broadcast $R --es title "Song" --es artist "Artist" --es album "Album" \
  --ei color 15022389 --el duration 210000 --el position 30000          # play
adb shell am broadcast $R --es state pause                              # pause
adb shell am broadcast $R --es state stop                               # remove session
```

### 壁纸（仅限调试版）

```bash
adb shell am broadcast -n org.opencarproject.launcher/.debug.FakeMediaSessionReceiver \
  -a org.opencarproject.launcher.DEBUG_WALLPAPER
```

### 通知

```bash
adb shell cmd notification post -t "Title" my_tag "Body text"
adb shell cmd notification post -S bigtext -t "Long" tag2 "A very long text ..."
adb shell cmd notification post -S messaging --conversation "Team" \
  --message "Ana:Are you coming?" --message "Luis:On my way" -t "Team" chat "Ana: ..."
adb shell cmd notification cancel my_tag
```

### GPS 位置与速度

在某些 API 35 镜像上，`adb emu geo fix` 无法传递到应用；请改用测试定位提供程序：

```bash
adb shell appops set com.android.shell android:mock_location allow
adb shell cmd location providers add-test-provider gps
adb shell cmd location providers set-test-provider-enabled gps true
adb shell cmd location providers set-test-provider-location gps --location 19.4326,-99.1332
```

当定位数据不含速度时，速度场景会根据连续的定位点计算速度。要模拟驾驶，每秒发送一个定位点，每次向北移动 `speed_m_s / 111320` 度纬度：

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

### 导航提示

启动真实的 Google 地图导航（需要上面的定位提供程序）：

```bash
adb shell am start -a android.intent.action.VIEW -d "google.navigation:q=19.4270,-99.1677" \
  -p com.google.android.apps.maps
```

## 7. 测试

```bash
./gradlew test                     # unit tests (JVM): grid math, codecs, units, colors, use cases
./gradlew connectedAndroidTest     # instrumented UI test (drag gesture) on a device/emulator
```

- 纯逻辑位于 `core/pure`，由 `app/src/test` 中的 JVM 测试覆盖。新的非界面逻辑请放在这里，这样无需设备即可测试。
- `connectedAndroidTest` **在结束时会卸载应用**，这会清除该设备上的布局和权限。之后请重新安装并重新授权（第 2 节和第 4 节）。

## 8. 故障排除

| 症状 | 排查位置 |
|---|---|
| 没有作为主屏幕应用出现 | `AndroidManifest.xml`（`HOME` + `DEFAULT` 类别）；设置角色（第 2 节） |
| 小部件缺失 / 无法添加 | `SystemCatalogSource`、`LauncherActivity` 中的绑定流程（`ACTION_APPWIDGET_BIND`） |
| 媒体卡片显示 "Nada sonando" | 未授予通知访问权限；`MediaSessionSource` |
| 没有天气 / "Activar clima" | 位置权限或网络；`WeatherSource` |
| 速度一直为 0 | 精确位置权限、GPS 提供程序；`SpeedReader` |
| 更新后布局丢失 | `JsonLayoutCodec`（只丢弃未知的项目类型，而不是整个布局） |
| 主屏幕按钮无反应（模拟器） | 手势导航的限制——参见第 3 节 |

更多内容见 [ARCHITECTURE.md](ARCHITECTURE.md#where-to-look-when-something-breaks)。
