# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). Entries are grouped from Conventional Commit types.

## [Unreleased]

## [0.15.0] - 2026-10-28

### Added

- Settings → "Floating music island" (off by default): while music plays and you're in another
  app, such as a map, the player floats over it as a small black island with the cover, title
  and controls. Drag it anywhere and it stays there, also after a restart. Only the cover opens
  the music app; with "Shrinkable island" (off by default) a tap on the title shrinks it to a
  bubble. It covers nothing but itself, hides when you go back Home and 15 s after the music
  pauses. Needs "display over other apps".

### Screenshots

**Floating music island.** With music playing, the player floats over the app in front (here,
Google Maps) with its cover, title and controls. Drag it anywhere: it stays there next time.
Only the cover opens the music app.

![Floating music island over Google Maps](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.15.0/floating-island.png)

**Shrinkable island.** With "Shrinkable island" on, a tap on the title shrinks it to a bubble
with the cover and a level meter; a tap on the bubble expands it again.

![Floating music island shrunk to a bubble](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.15.0/floating-island-shrunk.png)

**Settings.** Settings → "Floating music island" and, below it, "Shrinkable island". Both are
off by default.

![Floating music island settings](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.15.0/floating-island-settings.png)

## [0.14.1] - 2026-10-27

### Added

- Settings → "Smooth animations" (off by default): backgrounds, glows, weather and player
  effects animate at full frame rate again instead of the lighter ~12 fps, at the cost of more
  CPU and GPU (on a K706 the home screen went from ~83% to ~177% total CPU).

### Screenshots

**Smooth animations.** Settings → "Smooth animations" brings the decorative animations back to
full frame rate for units with CPU to spare. Off by default to keep the lighter ~12 fps.

![Smooth animations setting turned on](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.14.1/smooth-animations.png)

## [0.14.0] - 2026-10-27

### Changed

- Much lower CPU and GPU use on head units:
  - The embedded map no longer runs while you're in another app or the screen is off (the
    navigation app keeps guiding in the background, as when you switch to it).
  - Map frames are shown as their own layer instead of redrawing the whole home screen every frame.
  - Decorative animations (background, glows, weather, player) run at a lower frame rate.
  - The speed scene holds still while the car is parked under a dry sky.
- Settings → "Embedded map resolution" (100% by default; 75% or 50%): the navigation app draws
  the card at a lower resolution and the system scales it up, for less CPU/GPU at the cost of a
  softer map. Touches are scaled to match.

### Screenshots

**Lighter home screen.** The same home screen with Waze live in its card, now at a fraction of
the cost. Measured on a K706: in another app total CPU went from ~215% to ~60% (the embedded map
stops drawing when nobody sees it), and the launcher redraws ~15 times a second at ~19 ms per
frame instead of ~28 at ~69 ms.

![Home screen with the embedded Waze card](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.14.0/embedded-map-light.png)

**Embedded map resolution.** Settings → "Embedded map resolution" lets the navigation app draw
the card at 75% or 50% and the system scales it up: a bit softer, a bit lighter. 100% by default.

![Embedded map resolution setting at 50%](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.14.0/embedded-map-resolution.png)

## [0.13.2] - 2026-10-27

### Fixed

- Assistant "App logo" style: on head units with no default assistant set in the system, the
  pill (and its preview in Settings) showed the animated bars instead of a logo; it now shows the
  logo of the app that answers the assistant button (usually Google).
- Media player card: the artwork no longer disappears when the card is resized. Small sizes show
  it beside the text (up to 40% of the width), and the smallest one shows it behind the play button.

### Screenshots

**Assistant with the Google logo.** With "App logo" chosen, the assistant pill at the bottom shows
the Google "G" even on head units that have no default assistant set in the system.

![Assistant pill with the Google logo](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.13.2/assistant-google-logo.png)

**Artwork at every size.** Resizing the music card no longer drops the cover: in a short card it
sits beside the title and controls, and in the smallest size it fills the card behind the play button.

![Short music card keeping its artwork while resized](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.13.2/media-cover-compact.png)

![Smallest music card with the artwork behind the play button](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.13.2/media-cover-mini.png)

## [0.13.1] - 2026-10-27

### Fixed

- Embedded map: opening the app drawer and coming back no longer restarts the map app.
- Embedded map: after using the navigation app full screen (or after the head unit closes it in
  the background), the card puts it back within a couple of seconds, with its current state,
  instead of freezing on an old frame.

### Screenshots

**Embedded map stays live.** Back on the home screen after using Waze full screen, the card shows
the app as it was left (here the "Going home?" prompt dismissed in the full app) within a couple
of seconds. Opening the app drawer no longer restarts it, and if the head unit closes the app in
the background the card relaunches it by itself.

![Embedded Waze card showing the state left in the full app](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.13.1/embedded-map-restored.png)

## [0.13.0] - 2026-10-27

### Added

- Settings → Reverse camera → "Clean up the image" (off by default): the analog reverse video is
  processed on the GPU before it's shown: interlacing lines blended, edge-preserving and temporal
  denoise (removes most of the analog grain, colour speckles included), and a light sharpen.
  "Noise reduction", "Sharpness", "Brightness" and "Contrast" sliders tune it live while the
  camera is open. Falls back to
  the plain preview if the GPU can't run it.

### Changed

- Reverse overlay radar: a one-color top view of the car with three proximity arcs in front and
  behind. The installed sensors are counted and their readings interpolated across the bumper, so
  the arcs grow towards the car right where the obstacle is, with smooth transitions.

### Fixed

- With "Hide the system camera" on, the head unit's full-screen reverse view is closed the moment
  it appears (it announces itself), instead of staying on screen for up to a few seconds.
- The reverse overlay shows even if reverse was engaged right as the launcher started, and its
  radar panel is no longer squeezed into a thin strip.
- The floating reverse camera is no longer mirrored compared to the head unit's own view: it
  applies the same flip the system camera app does (`persist.sys.backcar_mirror`).

### Screenshots

**Top-view radar.** The reverse overlay shows the car from above in one color, with three
proximity arcs in front and behind. The sensors the car has are counted and their readings
interpolated across the bumper, so the arcs light up right where the obstacle is and grow
towards the car (green, amber, red) as it gets closer.

![Reverse radar as a top view of the car](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.13.0/reverse-radar.png)

**Cleaner reverse camera.** "Clean up the image" processes the analog video on the GPU. Left:
noise reduction at 0%; right: at 100%, same night scene on a K706. Colour speckles and flicker
are gone (frame-to-frame noise 13.6 → 3.4). Noise reduction, sharpness, brightness and contrast
are sliders applied live.

![Reverse camera before and after the cleanup](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.13.0/reverse-clean-image.png)

## [0.12.0] - 2026-10-27

### Added

- "Maps (embedded)" module: Google Maps (or Waze) runs live inside a home-screen card and
  responds to touch. It needs a head unit whose adbd runs as root on TCP port 9876 (for example
  the K706); the first time, allow USB debugging for the launcher in the system prompt. On
  other devices the card explains it isn't available and offers to open the map normally.
  Settings → "Embedded map app" picks Google Maps or Waze (Automatic uses the default
  navigation app).

### Screenshots

**Maps inside a card.** Add "Maps (embedded)" from "+" and Google Maps or Waze runs live in the
card and responds to touch like the real app. Captured on a K706 head unit, where adb runs as
root on port 9876.

![Google Maps running inside a home-screen card](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.12.0/embedded-map.png)

**Choose the map app.** Settings → "Embedded map app" switches the card between Google Maps and
Waze; Automatic keeps the default navigation app.

![Embedded map app picker in Settings](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.12.0/embedded-map-setting.png)

## [0.11.1] - 2026-10-27

### Fixed

- The player no longer stays on "Nothing playing" when the car wakes up with music already
  playing: some head units (seen on a Unisoc K706) skip the system's session-change event after
  sleep, so the launcher now re-checks the active media session while it's on screen.

### Screenshots

**Player recovers after the car wakes up.** When the head unit wakes from sleep and the music app
starts a new session without the system telling the launcher, the player card picks it up on its
own within a few seconds instead of staying on "Nothing playing".

![Player showing the current track after waking](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.11.1/player-after-wake.png)

## [0.11.0] - 2026-10-27

### Added

- Climate module for head units with a QF CAN app (`com.qf.vehicle`): cabin temperatures,
  power, A/C, AUTO, recirculation, air direction, defrosters and fan. The launcher follows the
  car configured in the head unit (`persist.sys.car_mode`) and sends the same commands as the
  vendor's own AC panel, so it works with every car and CAN box that app supports.
- Climate card redesign: ice flakes blowing with the fan (they settle and fade when the climate
  is off), fan level as bars with the car's own number of levels, and the air direction icon.
- Settings → Climate: switch the head unit's own AC popup on or off, and an optional climate bar
  at the bottom of the home screen with blue-to-red temperature bars.
- Reverse camera overlay (Settings → Reverse camera, off by default): in reverse, a floating,
  draggable window over any app shows the rear camera next to the CAN parking sensors (front,
  rear and side bars plus the nearest rear distance). The camera is captured at its native
  720x480 with high-quality edge and noise processing, sharper than the system's upscaled view.
  Needs "display over other apps"; the settings row opens that permission and has a Test button.
  "Hide the system camera" closes the head unit's own full-screen reverse view so the floating
  one is visible; the floating camera reopens as soon as the system releases it.


### Screenshots

**Climate module.** On head units with a QF CAN app, the climate card shows both cabin
temperatures, power, fan level and air direction, with ice flakes blowing while the A/C is on.
The optional climate bar (Settings → Climate) slides up at the bottom of the home screen when the
climate changes, and the same section can hide the head unit's own A/C popup.

![Climate card](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.11.0/climate-card.png)

**Reverse camera.** In reverse, a floating window over any app shows the rear camera next to the
parking sensors, which turn green, amber or red as an obstacle gets closer. Captured on the
emulator with its virtual camera (no CAN radar there, hence "No sensors").

![Floating reverse camera with parking sensors](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.11.0/reverse-camera.png)

## [0.10.4] - 2026-10-27

### Fixed

- Notifications and the media player no longer stay empty or frozen on head units whose system
  kills the launcher and never reconnects its notification listener (seen on a Unisoc K706):
  the launcher now detects the dropped listener and binds it again.

### Screenshots

**Player and notifications keep working after a restart.** When the system kills the launcher in
the background, the player card, the notifications card and the island reconnect on their own
instead of staying on "Nothing playing" until the head unit reboots.

![Player and notifications after the launcher restarts](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.10.4/media-after-restart.png)

## [0.10.3] - 2026-10-26

### Changed

- The project website is redeployed on every release, not only when its own files change.
  No app changes in this release, so there are no new screenshots.

## [0.10.2] - 2026-10-26

### Changed

- README (English, Spanish and Chinese) links to the project website and the latest APK.

### Screenshots

**Website link in the README.** The README now links to the project website and to the
latest APK, right under the language selector.

![README with the website and APK links](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.10.2/readme-links.png)

## [0.10.1] - 2026-10-26

### Added

- Project website (GitHub Pages) in English, Spanish and Chinese with a download button for the
  latest APK, plus a friendlier contributor guide with ready-to-paste AI prompts and a
  skills/agents reference.

### Screenshots

**Project website.** https://maldos23.github.io/open-car-project/ shows what the launcher does
with animated cards and screenshots, in English, Spanish and Chinese, and the download button
always points to the latest APK.

![Project website with the download button and the home screen](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.10.1/website.png)

## [0.10.0] - 2026-10-26

### Added

- Settings: shape of the built-in module cards (navigation, media, notifications): rounded or
  a wavy "flower" edge, with adjustable corners (0–32 dp).
- Settings: assistant button style with live previews: the animated Google pill, a pill with
  the default assistant app's logo, or just three animated sparkles in Google colors. The
  pills' colored halo and border can be turned off.

### Screenshots

**Flower-shaped modules.** Settings → Module shape → Flower gives the navigation, media and
notification cards a wavy edge; Module corners sets how round their corners are (0–32 dp).
The bottom bar shows the new sparkles assistant button.

![Module cards with the flower edge and the sparkles assistant button](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.10.0/flower-sparkles.png)

**Assistant style.** Settings → Assistant style picks the animated Google pill, a pill with
the default assistant's logo, or three animated sparkles, each with a live preview. Colored
border turns the pills' Google-colored halo and border on or off.

![Assistant style catalog with live previews and the colored border switch](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.10.0/assistant-styles.png)

## [0.9.0] - 2026-10-26

### Added

- Speed scene: past the rocket speed the vehicle shakes and sparks, turns into a rocket and
  lifts off into space; it lands back on the road when you slow down.
- Speed scene: a picture catalog of silhouettes, with new sports car, van, bus, motorbike and
  an 8-bit pixel cat with a rainbow trail.
- Speed scene: selectable animated scenery: city, pixel art, countryside, beach, desert, snow
  and jungle (the sky still follows the time and the weather).
- More detail on vehicles (doors, mirrors, exhaust, brake light, night headlight beam), cats
  (stripes, whiskers, blinking) and dogs (spots, collar, flapping ear, tongue out when fast).

### Screenshots

**Rocket launch.** Past the rocket speed (Settings → Speed scene → Rocket from) the car
shakes and throws sparks, a rocket grows out of it in the car's color, then it tilts and lifts
off while the city fades into space. When you slow down it plays backwards and lands.

![Vehicle turning into a rocket](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.9.0/rocket-launch.png)

**Pixel cat and pixel art scenery.** An original 8-bit cat that runs with a waving rainbow and
twinkling stars. Instead of a rocket it flies into space with a thicker rainbow. The pixel art
city has a banded sky, blocky clouds and windows that blink at night.

![Pixel cat on the pixel art city and flying in space](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.9.0/pixel-cat.png)

**Silhouette catalog.** Settings → Speed scene → Silhouette is now a row of picture cards
that you scroll sideways. It has 10 options, shown in the chosen color (the pixel cat keeps
its own colors).

![Silhouette catalog](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.9.0/silhouettes.png)

**Scenery.** Pick where you drive: city (default), pixel art, countryside (windmill, trees,
fence), beach (waves, palms, sailboat), desert (mesas, cacti, tumbleweed, heat haze), snow
(peaks, pines, snowfall) or jungle (vines, fireflies at night). Each one scrolls with your speed.

![Scenery catalog](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.9.0/sceneries.png)

The catalogs follow the light and dark themes.

![Catalogs in dark theme](https://raw.githubusercontent.com/maldos23/open-car-project/main/docs/screenshots/releases/0.9.0/catalog-dark.png)

## [0.8.0] - 2026-10-26

### Added

- Long-press an app in the app drawer to enter edit mode: icons jiggle and a trash button
  uninstalls them; tap outside an icon to finish.

## [0.7.0] - 2026-10-26

### Added

- The player shows the playing app's queue ("Up next") when the app publishes one: tap the list
  button, then a song to play it.
- The player's background is an animated gradient of the cover's colors that blends into the next
  track's.

### Changed

- The logo of the app that is playing moved to the player's bottom-right corner.

## [0.6.1] - 2026-10-26

### Fixed

- Touching anywhere outside the open notification island (or pressing Back) collapses it, and
  the touch no longer reaches what is underneath.
- The notification island no longer overlaps the weather or the speed scene: it only takes the
  free space between them, also on smaller screens, and the open preview grows downward.

## [0.6.0] - 2026-10-26

### Added

- The media player shows the logo of the app that is playing.

### Fixed

- Apps whose logo lives in the icon's background layer, such as Chrome, no longer show up as a
  blank square on the desktop and in the dock.
- After installing or updating the launcher, the player picks up audio from other apps right
  away instead of showing "Nothing playing".

## [0.5.0] - 2026-10-26

### Changed

- Tapping the notification island expands it into the full preview, the same one the
  notifications module shows: long text, picture, progress and the app's buttons, and Android's
  own view for special notifications such as playback, radio or calls. The music bubble shows
  player controls. Tap again to open the app; swipe up to close it.

## [0.4.1] - 2026-10-25

### Fixed

- The weather comes back as soon as the internet does after an outage, instead of up to 30 minutes later.
- The weather and city follow the car: refreshed every 15 minutes and after driving about 5 km.
- The weather icon and colors switch between day and night by the local sunrise and sunset, also
  when the last update is old.

## [0.4.0] - 2026-10-25

### Added

- Motion glow setting "Turn source": sensors + GPS (default) or GPS only.
- Motion glow setting "Glow smoothness": how softly the light glides between directions.

### Changed

- The motion glow glides smoothly between directions and reacts about a second sooner to
  turns read from the GPS.

## [0.3.3] - 2026-10-25

### Changed

- The notification island keeps an ongoing call (pulsing green dot) or the music playing (a small
  level meter) pinned, and returns to it after each alert and when the screen wakes.

### Fixed

- Song changes no longer go unannounced when the player loads the next track before it starts.
- Notifications keep arriving after the system disconnects the notification listener.

## [0.3.2] - 2026-10-25

### Fixed

- The motion glow lights the side edges in turns again when the gyroscope is idle or missing
  (emulator, some head units) or the GPS gives no bearing: turns then come from the GPS track.

## [0.3.1] - 2026-10-25

### Changed

- The assistant opens faster: it is shown as a system overlay instead of launching the Google app.
- Dependencies: datastore-preferences 1.3.0 (the 0.1.1 notes listed it, but the build still used 1.1.1).

## [0.3.0] - 2026-10-25

### Fixed

- Two quick changes to the home screen (e.g. deleting two items) no longer undo each other.
- Cancelling a widget's setup screen removes the half-added widget.
- Pressing Back in edit mode also closes the add panel.
- Adding an app or module that is already on the home screen no longer creates a broken copy.
- Adding a widget whose app was just uninstalled shows a message instead of crashing.
- A corrupt layout file falls back to the default menu instead of crashing the launcher.
- The sensor speedometer no longer jumps to ~50 km/h for a moment on start.
- With both Waze and Google Maps navigating, the navigation card prefers Waze, as intended.
- Newly installed or removed apps show up in the app menu without restarting the launcher.
- The speed scene no longer keeps the GPS or accelerometer running while another app is in
  front, saving battery.

### Changed

- Smoother home screen with less battery use: the speed scene, weather pill, assistant button,
  player and navigation cards and card dragging now animate by redrawing instead of rebuilding
  the screen on every frame.
- Settings switches toggle by tapping anywhere on their row; color swatches and the dock's
  remove badge have full 48 dp touch targets and announce their selected state.
- With the system wallpaper in light theme, the bars' scrim is light so their text stays readable.

## [0.2.0] - 2026-10-25

### Added

- UI translations: English, Spanish, Simplified Chinese, Portuguese and French. Settings →
  Language picks one or follows the system (default); units and regional formats keep
  following the device region.
- Settings → Zoom slider (80–130 %) scales the whole interface, settings and dialogs included.
- Settings → App menu icon size slider (75–150 %) for the app menu and its full-page variant.
- Motion glow (off by default): a neon light travels along the screen border towards where the
  car is going, brightest at that point and fading along the edge. Left/right in turns
  (gyroscope, GPS bearing as fallback), up when speeding up, down and red when braking (GPS),
  corners when combined. Adjustable sensitivity and color, and a Test button that sweeps it
  around the border.

### Changed

- New app icon: an open steering wheel with a neon arc (also shown at the top of the READMEs).
- The notification island stays at the top center as a bubble with the newest notification's
  icon and a one-line summary; it hides when there are none, or until a newer one arrives after
  a swipe up.

### Fixed

- No crash on Android 8–10 when the GPS provider changes state (location listeners now
  implement every callback).
- Home starts even if the settings file is slow to read or corrupted.
- Home-screen widgets keep updating after the language or zoom changes.

## [0.1.2] - 2026-10-25

### Fixed

- Widget picker shows each widget's real preview (generated preview, `previewLayout` or
  `previewImage`) instead of a generic icon.
- Widgets added through the system permission dialog get their suggested size and land in free
  cells (they were always 1×1 and could overlap another item when the grid was full).
- Edit-mode delete and resize controls are smaller and sit on the card corners, so 1×1 icons
  can still be dragged (touch targets stay 48 dp).
- Weather keeps showing the city when a geocoder lookup fails, and retries it later.
- Bottom bar measures the real assistant pill width, so free shortcut slots are no longer hidden.

## [0.1.1] - 2026-10-25

### Changed

- Build toolchain upgraded: Gradle 9.8.2, Android Gradle Plugin 9.4.1 (built-in Kotlin),
  Kotlin 2.4.21, Compose BOM 2026.09.00, kotlinx-serialization 1.12.0. The API 37
  `compileSdkMinor` / `suppressUnsupportedCompileSdk` workarounds are gone.
- Dependabot groups related dependencies so coupled upgrades arrive in a single PR.
- Dependencies: androidx core-ktx 1.19.1, datastore-preferences 1.3.0, kotlinx-coroutines-test
  1.11.0; CI actions (checkout, setup-java, setup-android, setup-gradle, upload-artifact) to
  their latest majors.

### Fixed

- Speed units follow locale changes (observable locale in Compose).
- Instrumented drag test: pre-grants startup permissions and drives the Compose clock
  manually so infinite animations don't block idling.

## [0.1.0] - 2026-10-24

First public release.

### Added

- Customizable home grid for car head units: long-press to edit, drag, resize with push-aside,
  delete; half-cell app icons without background; system App Widgets (bind + configure flow).
- Built-in modules: **Navigation** (next-turn guidance from Google Maps / Waze notifications,
  animated *Navigate* button), **Media player** (cover art, progress, vibrant animated
  background, controls), **Notifications** (cards, previews, actions, native rendering).
- Notification island (Dynamic-Island style) for new notifications and track changes.
- Top bar: animated weather pill (Open-Meteo, gradient per condition, city name) and speed
  scene (car / SUV / pickup / cat / dog, sky by time of day and weather, rocket mode) with an
  optional circular or inline speedometer; GPS or sensor speed source.
- Bottom dock: clock, app drawer with search, Google Assistant button with rotating
  suggestions, app shortcuts, hotspot, signal (4G/5G, Wi-Fi standard), volume, notifications,
  settings.
- Settings: theme, accent color (incl. Material You), background (solid / wallpaper / animated
  gradient with particles), °C/°F, km/h/mph, 12/24 h, bar contents, app-menu style, dock size.
- Docs in English, Spanish and Simplified Chinese; CI, gitflow/commit checks and signed release
  builds.

[Unreleased]: https://github.com/maldos23/open-car-project/compare/v0.15.0...develop
[0.15.0]: https://github.com/maldos23/open-car-project/compare/v0.14.1...v0.15.0
[0.14.1]: https://github.com/maldos23/open-car-project/compare/v0.14.0...v0.14.1
[0.14.0]: https://github.com/maldos23/open-car-project/compare/v0.13.2...v0.14.0
[0.13.2]: https://github.com/maldos23/open-car-project/compare/v0.13.1...v0.13.2
[0.13.1]: https://github.com/maldos23/open-car-project/compare/v0.13.0...v0.13.1
[0.13.0]: https://github.com/maldos23/open-car-project/compare/v0.12.0...v0.13.0
[0.12.0]: https://github.com/maldos23/open-car-project/compare/v0.11.1...v0.12.0
[0.11.1]: https://github.com/maldos23/open-car-project/compare/v0.11.0...v0.11.1
[0.11.0]: https://github.com/maldos23/open-car-project/compare/v0.10.4...v0.11.0
[0.10.4]: https://github.com/maldos23/open-car-project/compare/v0.10.3...v0.10.4
[0.10.3]: https://github.com/maldos23/open-car-project/compare/v0.10.2...v0.10.3
[0.10.2]: https://github.com/maldos23/open-car-project/compare/v0.10.1...v0.10.2
[0.10.1]: https://github.com/maldos23/open-car-project/compare/v0.10.0...v0.10.1
[0.10.0]: https://github.com/maldos23/open-car-project/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/maldos23/open-car-project/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/maldos23/open-car-project/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/maldos23/open-car-project/compare/v0.6.1...v0.7.0
[0.6.1]: https://github.com/maldos23/open-car-project/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/maldos23/open-car-project/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/maldos23/open-car-project/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/maldos23/open-car-project/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/maldos23/open-car-project/compare/v0.3.3...v0.4.0
[0.3.3]: https://github.com/maldos23/open-car-project/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/maldos23/open-car-project/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/maldos23/open-car-project/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/maldos23/open-car-project/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/maldos23/open-car-project/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/maldos23/open-car-project/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/maldos23/open-car-project/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/maldos23/open-car-project/releases/tag/v0.1.0
