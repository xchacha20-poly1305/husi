---
name: husi-actions-dropdown-menu
description: Husi actions overflow menu conventions for Compose Material3 DropdownMenuPopup. Use this whenever editing or creating a DropdownMenuPopup inside CapsuleTopBar or CapsuleSearchTopBar actions, adding a more_vert actions menu, touching MenuDefaults.Label section headers, DropdownMenuGroup shapes, checked/selected menu rows, or improving the visual hierarchy of topbar action menus in composeApp/.
---

# Husi Actions Dropdown Menus

Use this skill when a topbar `actions = { ... }` block contains an overflow menu, usually a
`more_vert` `SimpleIconButton` wrapped by `CapsuleActionButton`, and the menu is implemented with
Material3 Expressive `DropdownMenuPopup`.

If the task also changes topbar structure, first follow the `husi-topbar` skill. This skill only
covers the overflow menu content and its visual hierarchy.

## Current house pattern

Actions overflow menus should look like this:

```kotlin
CapsuleActionButton {
    Box {
        SimpleIconButton(
            imageVector = vectorResource(Res.drawable.more_vert),
            contentDescription = stringResource(Res.string.more),
            onClick = { isOverflowMenuExpanded = true },
        )

        DropdownMenuPopup(
            expanded = isOverflowMenuExpanded,
            onDismissRequest = { isOverflowMenuExpanded = false },
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
            ) {
                DropdownMenuSectionHeader(stringResource(Res.string.custom_config))
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.outbound)) },
                    onClick = {
                        isOverflowMenuExpanded = false
                        // action
                    },
                    shape = MenuDefaults.itemShape(0, 2).shape,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.full)) },
                    onClick = {
                        isOverflowMenuExpanded = false
                        // action
                    },
                    shape = MenuDefaults.itemShape(1, 2).shape,
                )
            }
        }
    }
}
```

Required pieces:

- Wrap the overflow icon in `CapsuleActionButton`, and place the popup in the same `Box`.
- Use `DropdownMenuPopup`, not the older plain `DropdownMenu`, for new topbar actions menus unless
  the surrounding screen already intentionally uses plain `DropdownMenu`.
- Use `DropdownMenuGroup` for each visual group and separate groups with
  `Spacer(Modifier.height(MenuDefaults.GroupSpacing))`.
- Use `MenuDefaults.groupShape(groupIndex, groupCount)` for grouped popup sections.

## Section headers

Section headers in actions menus are labels, not actions. Do not create fake title rows like:

```kotlin
DropdownMenuItem(
    text = { MenuDefaults.Label { Text(...) } },
    onClick = {},
)
```

That makes the title look and behave like a normal clickable menu row. It also adds misleading
touch/ripple semantics.

Use the shared helper instead:

```kotlin
DropdownMenuSectionHeader(stringResource(Res.string.sort_mode))
```

The helper lives at:

`composeApp/src/commonMain/kotlin/fr/husi/compose/DropdownMenuSectionHeader.kt`

It applies the current house style:

- `MenuDefaults.Label`
- full-width centered text
- `MaterialTheme.typography.labelMedium`
- `FontWeight.SemiBold`
- `MaterialTheme.colorScheme.onSurfaceVariant`
- `HorizontalDivider` with `MenuDefaults.HorizontalDividerPadding`

If the helper is missing in an older branch, add it instead of duplicating the style in each screen.

## Item shapes after headers

Headers do not count as menu items for item shape indexing. Count only the actionable rows in that
group.

For plain click rows, Material3's API takes a single `Shape`, so use `.shape`:

```kotlin
DropdownMenuItem(
    text = { Text(stringResource(Res.string.full)) },
    onClick = { /* ... */ },
    shape = MenuDefaults.itemShape(index, itemCount).shape,
)
```

For `selected` or `checked` rows, Material3's API takes `MenuItemShapes`, so use `shapes`:

```kotlin
DropdownMenuItem(
    selected = sortMode == uiState.sortMode,
    onClick = { /* ... */ },
    text = { Text(stringResource(text)) },
    shapes = MenuDefaults.itemShape(index, itemCount),
)

DropdownMenuItem(
    checked = sortMode == uiState.sortMode,
    onCheckedChange = { checked ->
        if (!checked) return@DropdownMenuItem
        // action
    },
    text = { Text(stringResource(text)) },
    shapes = MenuDefaults.itemShape(index, itemCount),
)
```

This distinction matters: using `shapes = ...` on a plain `onClick` item will not compile, while
using `shape = MenuDefaults.itemShape(...).shape` preserves the grouped rounded-corner behavior.

## Checked filters

For rows that toggle a boolean and show a checkbox, prefer making the row itself the action:

```kotlin
DropdownMenuItem(
    text = { Text(stringResource(Res.string.connection_status_active)) },
    onClick = {
        viewModel.setQueryActivate(!uiState.showActivate)
    },
    leadingIcon = {
        Checkbox(
            checked = uiState.showActivate,
            onCheckedChange = null,
        )
    },
    shape = MenuDefaults.itemShape(0, 2).shape,
)
```

Use `onCheckedChange = null` on the checkbox when the containing row handles the click. This keeps
the interaction target simple and avoids nested competing actions.

## Dismiss behavior

Close the popup when the action navigates, opens another screen/dialog, or commits a one-shot
selection:

```kotlin
onClick = {
    isOverflowMenuExpanded = false
    onOpenConfigEditor(...)
}
```

For non-destructive filter toggles that can be toggled repeatedly, leaving the menu open can be
reasonable. Match the surrounding menu's behavior.

## Imports and local components

Prefer Husi wrappers inside menu content:

- `fr.husi.compose.material3.Text`
- `fr.husi.compose.material3.Icon`
- `fr.husi.compose.material3.Checkbox`
- `fr.husi.compose.DropdownMenuSectionHeader`

Use Compose resource helpers:

- `stringResource(Res.string...)`
- `vectorResource(Res.drawable...)`

Avoid hardcoded colors or text sizes in menu rows. Use `MaterialTheme` roles in shared helpers only
when the visual treatment is a deliberate app convention.

## Search and verification

Before editing:

```bash
rg -n "DropdownMenuPopup|MenuDefaults\\.Label|more_vert|actions\\s*=" composeApp/src/commonMain/kotlin/fr/husi -g '*.kt'
```

After editing:

```bash
rg -n "MenuDefaults\\.Label" composeApp/src/commonMain/kotlin/fr/husi -g '*.kt'
./gradlew :composeApp:compileKotlinDesktop
git diff --check -- composeApp/src/commonMain/kotlin/fr/husi
```

If the change touches Android-only UI or resources, also run:

```bash
./gradlew :composeApp:compileAndroidMain
```

Known reference implementations:

- `composeApp/src/commonMain/kotlin/fr/husi/ui/RouteSettingsScreen.kt`
- `composeApp/src/commonMain/kotlin/fr/husi/ui/dashboard/Dashboard.kt`
- `composeApp/src/commonMain/kotlin/fr/husi/ui/profile/ProfileEditorScreen.kt`
- `composeApp/src/commonMain/kotlin/fr/husi/compose/DropdownMenuSectionHeader.kt`
