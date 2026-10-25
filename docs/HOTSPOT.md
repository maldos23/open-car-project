# Hotspot: list of connected devices

The dock's hotspot icon works at two levels:

| Install type | On/off icon | Device count and list |
|---|---|---|
| Regular APK | Yes (`WIFI_AP_STATE_CHANGED` broadcast) | No — Android only allows this for system apps |
| priv-app with `TETHER_PRIVILEGED` | Yes | Yes (`TetheringManager`: name, IP and MAC) |

Since Android 10, regular apps can't read `/proc/net/arp` either, so there is no other way.

## Install as a system app (rooted head unit or custom firmware)

```bash
adb root && adb remount            # needs root / verity disabled
adb shell mkdir -p /system/priv-app/OpenCarProject
# A signed APK: app-debug.apk, or a release build signed with your keystore
adb push app/build/outputs/apk/debug/app-debug.apk /system/priv-app/OpenCarProject/OpenCarProject.apk
adb push docs/privapp-permissions-opencarproject.xml /system/etc/permissions/
adb reboot
```

Check the permission after rebooting:

```bash
adb shell dumpsys package org.opencarproject.launcher | grep TETHER_PRIVILEGED   # granted=true
```

If the device doesn't boot after copying the APK, the permissions XML is usually missing:
Android refuses to boot when a priv-app requests a privileged permission that isn't
allow-listed.
