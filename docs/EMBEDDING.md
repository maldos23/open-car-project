# Embedding other apps' windows (research notes)

Goal: show another app (e.g. a navigation app) *running inside* an area of the launcher.
This is **not implemented** and **not possible for a regular third-party launcher**; these
notes collect the options for OEMs or system-app builds. Today the launcher instead reads
next-turn guidance from navigation notifications and opens the nav app full-screen.

> API names below are a guide. Several are `@SystemApi`/hidden and change between platform
> releases — use the AOSP source for your branch as the reference.

## Options

| Approach | Who can use it | Notes |
|---|---|---|
| **Activity Embedding** (Jetpack WindowManager, API 32+) | Any app, for **its own** activities; other apps' activities only if they opt in (`android:allowUntrustedActivityEmbedding` or `knownActivityEmbeddingCerts`) | Split the task side-by-side. Navigation apps don't opt in, so not useful here. |
| **TaskView / TaskFragmentOrganizer** (API 33+) | System apps (platform-signed) | What Android Automotive's `CarLauncher` uses to host the map. |
| **VirtualDisplay + SurfaceView** | Needs `CAPTURE_VIDEO_OUTPUT`/system privileges to launch third-party apps on a private display | Launch with `ActivityOptions.setLaunchDisplayId(...)`; input forwarding is up to you. |
| **Android Auto / Car App Library templates** | Apps that implement them | Rendering requires the Android Auto host, not a launcher. |

## Requirements for the system-app route

1. Sign with the platform key (or ship it in the OEM image).
2. Install in `/system/priv-app` with a privapp-permissions allow-list
   (see [HOTSPOT.md](HOTSPOT.md) for the procedure).
3. Build against the platform SDK if you need hidden APIs. Reflection does **not** bypass the
   permission checks.

### VirtualDisplay sketch

```kotlin
val dm = context.getSystemService(DisplayManager::class.java)
val display = dm.createVirtualDisplay("embedded-app", width, height, densityDpi, surface, 0)
val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.display.displayId)
context.startActivity(appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
```

Without system privileges the launch is rejected with a `SecurityException` for apps that
aren't yours.

## Android Automotive OS

On AAOS the OEM launcher already embeds apps (`CarLauncher` + `TaskView`). The practical
path there is to extend the system launcher rather than replace it.
