---
name: add-builtin-module
description: Add a new built-in module (a native Compose card such as navigation, media or notifications) that users can place, move and resize on the Open Car Project grid. Use when asked for a new card/module/tile on the home screen.
---

# Add a built-in module

Paths are under `app/src/main/kotlin/org/opencarproject/launcher/`.

1. **Kind** — add a value to `CatalogItemKind` in `core/model/Enums.kt` with a KDoc line.
   Never rename existing values: they are persisted in saved layouts.
2. **Key** — add a `BuiltinKeys.XXX = "builtin/xxx"` constant in `core/model/Models.kt`.
   Optionally add it to the default layout there.
3. **Catalog** — list it in `data/source/SystemCatalogSource.kt` (`builtin(...)` with label and
   description) so it appears in the "+" panel.
4. **Initial size** — add a span in `domain/usecase/AddItem.kt` (sizes are in half-cell
   columns: multiply visible columns by `scale`), and include the kind in the non-widget branch.
5. **Card** — create `presentation/widget/XxxCard.kt`. Use `MaterialTheme.colorScheme`, touch
   targets ≥ 48 dp, and handle the "no data / no permission" state with a clear action.
   Data from the system goes in a `data/source` class; pure formatting/logic in `core/pure`
   with a unit test. Every visible text is a `stringResource` key present in all five
   `res/values*/strings.xml` locales; name the module in `WidgetPickerPanel`'s `CatalogTile`.
6. **Render** — add the branch in `presentation/grid/PlacedItemCard.kt`'s `when (item.kind)`.
7. **Verify** — `./gradlew test assembleDebug`; on the emulator long-press the home screen,
   add the module from "+", drag and resize it, restart the app and check it persisted.
