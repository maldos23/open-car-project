# Architecture

This is the map of the code: layers, packages, how data flows, and where to look when
something breaks. Root package: `org.opencarproject.launcher`.

## Layers

```
┌──────────────────────────────────────────────────────────────┐
│ presentation  — Compose UI + state                            │
│  LauncherActivity · LauncherViewModel · LauncherScreen        │
│  grid/ · widget/ (built-in modules) · common/ (bars, scene)   │
│  drawer/ · settings/ · picker/                                │
└───────────────▲───────────────────────────┬──────────────────┘
                │ observes                   │ calls
┌───────────────┴──────────┐   ┌────────────▼─────────────────┐
│ domain                   │   │ data                         │
│  repository interfaces   │◄──┤  *RepositoryImpl · sources   │
│  use cases (one per class)│  │  AppWidgetHost · listeners   │
└───────────────▲──────────┘   └──────────────────────────────┘
                │ depends on
┌───────────────┴──────────────────────────────────────────────┐
│ core — no business rules                                      │
│  model (DTOs, enums) · pure (testable logic) · ui/theme · util│
└──────────────────────────────────────────────────────────────┘
                         di/AppContainer wires everything (manual DI)
```

**Dependency rule:** arrows point to `core`. `domain` imports no `android.*`; `data`
implements `domain` interfaces; `presentation` consumes `domain` and `core`. `AppContainer`
is the only composition root.

Some system integrations that are inherently UI-bound (media session, speed, weather,
notification listener state) are exposed as small `data/source` singletons or flows read
directly by composables; that is a deliberate simplification over a full repository.

## Packages

| Package | Responsibility | Key files |
|---|---|---|
| `core.model` | Persisted/shared DTOs and enums | `Models.kt` (`PlacedItem`, `LayoutDefinition`, `LauncherSettings`), `Enums.kt` |
| `core.pure` | Pure Kotlin logic, fully unit-tested | `GridMath`, `JsonLayoutCodec`, `AccentPalette`, `SpeedMath`, `SpeedUnits`, `Temperature`, `WeatherCodes`, `NetworkLabels`, `VibrantColor`, `TextSearch` |
| `core.ui.theme` | Material 3 theme, accent colors | `Theme.kt`, `Dimens.kt` |
| `domain.repository` | Contracts | `LayoutRepository`, `SettingsRepository`, `CatalogRepository`, `WidgetHostGateway` |
| `domain.usecase` | One operation per class | `AddItem`, `MoveItem`, `ResizeItem`, `ChangeGridSize`, `UpdateSettings`, … |
| `data.source` | System I/O and persistence | `LayoutDataSource`, `SettingsDataSource` (DataStore), `SystemCatalogSource`, `LauncherNotificationListener`, `MediaSessionSource`, `WeatherSource`, `SpeedReader`, `HotspotSource` |
| `data.widget` | Widget host | `LauncherAppWidgetHost` |
| `presentation.launcher` | Activity, ViewModel, root screen, bars | `LauncherActivity`, `LauncherViewModel`, `LauncherScreen` |
| `presentation.grid` | Grid, cards, gestures, haptics | `WidgetGrid`, `PlacedItemCard`, `Haptics` |
| `presentation.widget` | Built-in modules & widget bridge | `NavigationCard`, `MediaPlayerCard`, `NotificationsCard`, `NotificationIsland`, `GlassWidgetHostView` |
| `presentation.common` | Top/bottom bar pieces | `WeatherIndicator`, `SpeedScene`, `StatusIndicators`, `VolumeControl`, `DockFavorites`, `GoogleAssistantButton` |
| `presentation.drawer` / `settings` / `picker` | App menu, settings sheet, add panel | `AppDrawerSheet`, `AppsPage`, `SettingsSheet`, `WidgetPickerPanel` |
| `debug` (debug source set) | Test-only helpers | `FakeMediaSessionReceiver` |

## The grid

- `LayoutDefinition` stores the grid in **half cells horizontally** (`columnScale = 2`): an app
  icon is 1 column wide (two per visible cell); built-in modules and widgets span twice as many
  columns. Old layouts are migrated by `LayoutDefinition.toFineColumns()`.
- All geometry (pixel→cell, clamping, collisions, push-aside, refit when the grid size
  changes, placing new items where they fit) lives in `GridMath`.
- `JsonLayoutCodec` is tolerant: unknown fields are ignored and items of an unknown kind are
  dropped individually, so a downgrade never wipes the whole layout.

## Interaction flow (drag example)

```
PlacedItemCard (gesture) ─► GridMath.targetCell ─► LauncherViewModel.onMovePreview
     WidgetGrid previews the result live with GridMath.placeWithPush
     └─► onMoveEnd ─► MoveItem ─► LayoutRepositoryImpl ─► LayoutDataSource ─► DataStore
```

## Settings flow

```
SettingsSheet ─► LauncherViewModel.onXxxChange ─► UpdateSettings ─► SettingsDataSource (DataStore)
LauncherActivity / LauncherScreen collect viewModel.settings and apply them (theme, bars, scene…)
```

To add a setting: field in `LauncherSettings` → key + read/write in `SettingsDataSource` →
`onXxxChange` in the ViewModel → control in `SettingsSheet` → wire the callback in
`LauncherScreen`. The `.claude/skills/add-setting` skill walks through it.

## Where to look when something breaks

| Symptom | Place to inspect |
|---|---|
| Not offered as a home app | `AndroidManifest.xml` (`HOME` + `DEFAULT`) |
| Panel lacks widgets/apps | `data/source/SystemCatalogSource.kt` |
| Layout not persisted / lost | `data/source/LayoutDataSource.kt`, `core/pure/JsonLayoutCodec.kt` |
| Drag/resize misbehaves | `core/pure/GridMath.kt`, `presentation/grid/` |
| Widget blank or not added | `presentation/widget/GlassWidgetHostView.kt`, `LauncherActivity` bind/configure flow |
| Music card empty | `data/source/MediaSessionSource.kt` (needs notification access) |
| Notifications missing | `data/source/LauncherNotificationListener.kt` |
| Weather missing | `data/source/WeatherSource.kt` |
| Speed always 0 | `data/source/SpeedReader.kt` |
| Assistant slow or doesn't open | `LauncherScreen` calls `Activity.showAssist` (system overlay, needs a default digital assistant); fallback intent in `WidgetHostGatewayImpl.launchAssistant` |
| Theme/colors | `core/ui/theme/Theme.kt`, `core/pure/AccentPalette.kt` |
