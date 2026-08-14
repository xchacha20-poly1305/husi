---
name: husi-preference-ui
description: Husi Preference UI conventions for Compose Multiplatform. Use whenever editing any settings or profile-editor preference UI in composeApp/, including Preference.kt helpers, preferenceGroup cards, PreferenceCategory layout, conditional preference sections, ColoredIconContainer, PreferenceMaskColors, PreferenceShapes, platform SettingsScreenPlatform actuals, or icons for me.zhanghai.compose.preference rows.
---

# Husi Preference UI

Use this skill for every setting or profile-editor preference row in `composeApp/` with `me.zhanghai.compose.preference`.

## Structure

The Settings tab is a hub of category entries that push a sub-page. Each settings
sub-page is one `preferenceGroup` card with no category header (the topbar title
names the section). Profile-editor screens still use a category item followed by
one grouped card:

```kotlin
item { PreferenceCategory(text = { Text(stringResource(Res.string.general_settings)) }) }
preferenceGroup {
    GeneralSettingsGroup(...)
}
```

Compose conditional sections directly in `LazyListScope` and use
`preferenceGroup` for each card. Do not wrap a group in a separate `item` or
recreate its `ElevatedCard` styling in feature screens.

Inside a group:

- Keep preference rows as plain composable calls, not `LazyListScope.item` calls.
- Put `PreferenceDivider()` between rows, not before the first row or after the last row.
- Use private `@Composable` group functions such as `GeneralSettingsGroup`, `RouteSettingsGroup`, or
  `DnsSettingsGroup` when a section grows.
- Keep platform-only rows behind composable expect/actual helpers or a narrow platform guard such as
  `if (PlatformInfo.isAndroid) return`; do not leave obsolete `LazyListScope` expect/actual entry
  points after migrating a row into grouped composables.

## Icons

Every visible preference row should use the masked icon treatment unless the row intentionally has
no leading icon for hierarchy reasons.

Use a masked icon by default:

```kotlin
icon = {
    ColoredIconContainer(PreferenceMaskColors.IconCyan) {
        Icon(vectorResource(Res.drawable.domain), null, modifier = Modifier.size(22.dp))
    }
}
```

Do not use bare icons in settings preference rows:

```kotlin
icon = { Icon(vectorResource(Res.drawable.domain), null) }
```

If a reusable preference helper provides an icon, make its default icon masked there. Example:
`PasswordPreference` should use `ColoredIconContainer(...)` by default so callers cannot forget the
mask.

## Semantic Shapes

Use `PreferenceShapes` with `ProfilePreferenceIcon` when the row's meaning benefits from a
distinctive mask. Keep the existing `PreferenceMaskColors` choice; shape communicates semantics,
not severity or category color.

| Meaning                                    | Shape                           | Typical rows                                         |
|--------------------------------------------|---------------------------------|------------------------------------------------------|
| Risk, reduced security, or unsafe behavior | `PreferenceShapes.risk()`       | allow insecure, insecure warnings, fake DNS override |
| Credentials, certificates, or private keys | `PreferenceShapes.credential()` | CA, certificate, client certificate, private key     |
| Routing behavior                           | `PreferenceShapes.route()`      | route priority, public routing                       |

```kotlin
ProfilePreferenceIcon(
  Res.drawable.vpn_key,
  color = PreferenceMaskColors.IconCyan,
  shape = PreferenceShapes.credential(),
)
```

Do not assign a semantic shape merely because an icon is available. Leave ordinary rows on the
default shape. Add a reusable semantic shape to `PreferenceShapes` only when it has a clear,
repeated meaning across preference screens; do not define equivalent shapes locally.

## Platform Rows

Prefer composable platform hooks used from grouped settings:

```kotlin
@Composable
internal expect fun PlatformGeneralOptions(needReload: () -> Unit)
```

with matching `actual` functions in Android and desktop source sets.

When replacing old lazy-list platform hooks:

- Delete unused declarations from `SettingsScreenPlatform.kt`.
- Delete matching unused actuals from Android and desktop files.
- Remove imports that existed only for deleted lazy-list APIs, such as `LazyListScope` and
  `PreferenceType`.

## Search Checks

Before finishing, run targeted searches:

```bash
rg -n "LazyListScope\\.(autoConnect|platformGeneralOptions|platformSecurityOptions|meteredNetworkSetting|platformRouteOptions|platformMiscOptions|disableProcessText|httpProxyBypass)" composeApp/src
rg -n "icon = \\{\\s*Icon\\(" composeApp/src/commonMain/kotlin/fr/husi/ui/settings/ composeApp/src/androidMain/kotlin/fr/husi/ui/SettingsScreenPlatform.android.kt composeApp/src/desktopMain/kotlin/fr/husi/ui/SettingsScreenPlatform.desktop.kt
rg -n "isSystemInDarkTheme|LocalContentColor provides Color\\.White" composeApp/src/commonMain/kotlin/fr/husi/compose/Preference.kt
rg -n "PreferenceShapes\\.(risk|credential|route)\\(\\)" composeApp/src/commonMain/kotlin/fr/husi/ui composeApp/src/commonMain/kotlin/fr/husi/compose/Preference.kt
```

The first and third searches should normally return nothing for settings preference work. The second
search should only return intentional exceptions. The final search should show only semantically
matched usages.
