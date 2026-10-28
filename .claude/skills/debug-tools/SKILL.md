---
name: debug-tools
description: Feed fake input to Open Car Project for debugging — fake media session, notifications, GPS location/speed, navigation guidance, wallpaper — and read logs or saved state. Use when testing the media card, notification island, speed scene, weather or navigation module without real apps or a car.
---

# Debug tools

All commands assume `P=org.opencarproject.launcher` and a debug build installed.

## Media session (debug builds only)

```bash
R="-n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_MEDIA"
adb shell am broadcast $R --es title "Song" --es artist "Artist" --es album "Album" \
  --ei color 15022389 --el duration 210000 --el position 30000
adb shell am broadcast $R --es state pause   # or: stop
```

Escape spaces in values for the device shell: `--es title "Midnight\ Drive"`.

## Wallpaper

`adb shell am broadcast -n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_WALLPAPER`

## Parking radar (debug builds only)

Feeds the reverse overlay without a car. Zones per sensor, left to right: -1 none, 0 clear,
1 closest … 10 farthest. Bumpers have 6 slots (default: corners absent), sides 4.

```bash
adb shell am broadcast -a com.qf.action.BACKCAR_START -p $P      # show the overlay
adb shell am broadcast -n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_RADAR \
  --es rear "-1,0,1,5,0,-1" --es front "-1,0,0,10,0,-1"
adb shell am broadcast -n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_RADAR --ez clear true
```

## Climate bar (debug builds only)

The emulator has no CAN service, so the climate settings are hidden. `everywhere` switches the
bar on (and over every app or not); the bar shows only on a change, so send two readings.

```bash
adb shell am broadcast -n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_CLIMATE --ez everywhere true --ei temp 21
adb shell am broadcast -n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_CLIMATE --ei temp 23 --es air front   # face|feet|front, --ez off true
```

Door alert (`--ez enable true` turns the launcher's alert on; needs "display over other apps"):

```bash
adb shell am broadcast -n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_DOORS --ez enable true --es open "fl,trunk"
adb shell am broadcast -n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_DOORS --es open none   # all closed
```

Launcher volume indicator (recent Android no longer broadcasts volume changes, so fake one with `level`):

```bash
adb shell am broadcast -n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_VOLUME --ez enable true
adb shell am broadcast -n $P/.debug.FakeMediaSessionReceiver -a $P.DEBUG_VOLUME --ei level 60
```

## Notifications

```bash
adb shell cmd notification post -t "Title" tag1 "Body"
adb shell cmd notification post -S messaging --conversation "Team" --message "Ana:Hi" -t "Team" tag2 "Ana: Hi"
adb shell cmd notification cancel tag1
```

## GPS location and speed

```bash
adb shell appops set com.android.shell android:mock_location allow
adb shell cmd location providers add-test-provider gps
adb shell cmd location providers set-test-provider-enabled gps true
adb shell cmd location providers set-test-provider-location gps --location 19.4326,-99.1332
```

Speed is derived from consecutive fixes: send one fix per second, moving
`speed_m_s / 111320` degrees of latitude each time (script in `docs/DEVELOPMENT.md` §6).

## Navigation guidance

```bash
adb shell am start -a android.intent.action.VIEW -d "google.navigation:q=19.4270,-99.1677" -p com.google.android.apps.maps
```

## Logs and state

```bash
adb logcat --pid=$(adb shell pidof $P)        # app logs
adb logcat -b crash                           # crashes
adb logcat -s OCP.Notif OCP.Media             # listener bind/rebind and media session errors
adb shell run-as $P ls files/datastore/       # saved layout / settings
adb shell pm clear $P                         # reset everything
```
