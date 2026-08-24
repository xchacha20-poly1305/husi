---
name: husi-preference-ui
description: Husi Preference UI conventions for Compose Multiplatform. Use whenever editing any settings or profile-editor preference UI in composeApp/, including Preference.kt helpers, preferenceGroup, PreferenceGroupDefaults, PreferenceItemSurface, the fr.husi.compose preference row wrappers, PreferenceCategory layout, conditional preference sections, MaskedIcon, IconMaskColors, IconMaskShapes, or platform SettingsScreenPlatform actuals.
---

# Husi Preference UI

Use this skill for every setting or profile-editor preference row in `composeApp/`.

## Import rows from `fr.husi.compose`, never from `me.zhanghai`

`fr.husi.compose.PreferenceItems.kt` redeclares the upstream rows with the same signatures and wraps
each one in `PreferenceItemSurface`, which is what gives a row its own container. Importing the
upstream composable directly produces a row with no background that visually merges into its
neighbours.

```kotlin
import fr.husi.compose.ListPreference
import fr.husi.compose.Preference
import fr.husi.compose.SliderPreference
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextFieldPreference
```

Only `ListPreferenceType` and `ProvidePreferenceLocals` still come from
`me.zhanghai.compose.preference` at a call site; neither is a row. `PreferenceCategory` also has a
wrapper in `fr.husi.compose`, but that one deliberately gets **no** item surface: it is a header
that sits *outside* the group.

If a row type is needed that has no wrapper yet, add one to `PreferenceItems.kt` rather than
importing upstream at the call site.

## Structure

The Settings tab is a hub of category entries that push a sub-page. Each settings
sub-page is one `preferenceGroup` with no category header (the topbar title
names the section). Profile-editor screens still use a category item followed by
one group:

```kotlin
item { PreferenceCategory(text = { Text(stringResource(Res.string.general_settings)) }) }
preferenceGroup {
    GeneralSettingsGroup(...)
}
```

Compose conditional sections directly in `LazyListScope` and use
`preferenceGroup` for each group. Do not wrap a group in a separate `item` or
recreate its container styling in feature screens.

Inside a group:

- Keep preference rows as plain composable calls, not `LazyListScope.item` calls.
- Put nothing between two rows. Rows are separated by a gap, not by a line — see *Segmented rows*.
- Use private `@Composable` group functions such as `GeneralSettingsGroup`, `RouteSettingsGroup`, or
  `DnsSettingsGroup` when a section grows.
- Keep platform-only rows behind composable expect/actual helpers or a narrow platform guard such as
  `if (PlatformInfo.isAndroid) return`; do not leave obsolete `LazyListScope` expect/actual entry
  points after migrating a row into grouped composables.

## Segmented rows

A group looks like a native Android preference group: every row is its own rounded surface, and the
group only supplies the large outer radius. There are no divider lines, and there is no
`PreferenceDivider` — it was removed.

`preferenceGroup` is a transparent `Column` clipped to `PreferenceGroupDefaults.groupShape`, laid
out with `PreferenceGroupDefaults.itemArrangement` (a 2dp gap). Each row draws
`PreferenceGroupDefaults.itemShape` in `itemContainerColor`.

Rows never pass a position. A row's own corners are always small, and the group's clip rounds the
first and last row up to the large radius, because the intersection of a small corner with the
group's large one is the large corner. This is why rows can appear and disappear behind `if` or
`AnimatedVisibility` without any index bookkeeping — unlike `DropdownMenuGroup`, which makes callers
pass `MenuDefaults.groupShape(index, count)`. Do not reintroduce index/count parameters here.

A nested `Column` holding preference rows — typically the body of an `AnimatedVisibility` — needs
the same gap, or its rows will touch:

```kotlin
AnimatedVisibility(visible = uiState.enableMux) {
    Column(verticalArrangement = PreferenceGroupDefaults.itemArrangement) {
        SwitchPreference(...)
        ListPreference(...)
    }
}
```

A collapsed `AnimatedVisibility` leaves a 4dp gap rather than 2dp at that boundary, because a
zero-height child still counts for `Arrangement.spacedBy`. That is accepted; do not work around it
with position bookkeeping.

Change the look in `PreferenceGroupDefaults` / `PreferenceItemSurface` only. Never give a single row
its own `Surface`, `Card`, background, or shape at a call site.

## Icons

Every visible preference row should use the masked icon treatment unless the row intentionally has
no leading icon for hierarchy reasons.

Use `MaskedIcon` with an `IconMaskColors` entry by default:

```kotlin
icon = { MaskedIcon(Res.drawable.domain, color = IconMaskColors.IconCyan) }
```

Do not use bare icons in settings preference rows:

```kotlin
icon = { Icon(vectorResource(Res.drawable.domain), null) }
```

If a reusable preference helper provides an icon, make its default icon masked there. Example:
`PasswordPreference` defaults to `MaskedIcon(...)` so callers cannot forget the mask.

## Semantic Shapes

Pass an `IconMaskShapes` shape to `MaskedIcon` when the row's meaning benefits from a distinctive
mask. Keep the existing `IconMaskColors` choice; shape communicates semantics, not severity or
category color.

| Meaning                                    | Shape                            | Typical rows                                         |
|--------------------------------------------|----------------------------------|------------------------------------------------------|
| Risk, reduced security, or unsafe behavior | `IconMaskShapes.risk()`          | allow insecure, insecure warnings, fake DNS override |
| Credentials, certificates, or private keys | `IconMaskShapes.credential()`    | CA, certificate, client certificate, private key     |
| Routing behavior                           | `IconMaskShapes.route()`         | route priority, public routing                       |

```kotlin
MaskedIcon(
  Res.drawable.vpn_key,
  color = IconMaskColors.IconCyan,
  shape = IconMaskShapes.credential(),
)
```

Do not assign a semantic shape merely because an icon is available. Leave ordinary rows on the
default shape. Add a reusable semantic shape to `IconMaskShapes` only when it has a clear,
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
rg -n "PreferenceDivider|HorizontalDivider" composeApp/src/commonMain/kotlin/fr/husi/ui/settings composeApp/src/commonMain/kotlin/fr/husi/ui/profile
rg -n "^import me\\.zhanghai\\.compose\\.preference\\.(Preference|SwitchPreference|TwoTargetSwitchPreference|ListPreference|MultiSelectListPreference|TextFieldPreference|SliderPreference)$" composeApp/src
rg -n "LazyListScope\\.(autoConnect|platformGeneralOptions|platformSecurityOptions|meteredNetworkSetting|platformRouteOptions|platformMiscOptions|disableProcessText|httpProxyBypass)" composeApp/src
rg -n "icon = \\{\\s*Icon\\(" composeApp/src/commonMain/kotlin/fr/husi/ui/settings/ composeApp/src/androidMain/kotlin/fr/husi/ui/SettingsScreenPlatform.android.kt composeApp/src/desktopMain/kotlin/fr/husi/ui/SettingsScreenPlatform.desktop.kt
rg -n "IconMaskShapes\\.(risk|credential|route)\\(\\)" composeApp/src/commonMain/kotlin/fr/husi/ui composeApp/src/commonMain/kotlin/fr/husi/compose/Preference.kt
```

The first three searches must return nothing: a divider line inside a group, an upstream row import,
and an obsolete lazy-list platform hook are all mistakes. The fourth should only return intentional
exceptions. The last should show only semantically matched usages.

Also check that any nested `Column` you added inside a group carries
`verticalArrangement = PreferenceGroupDefaults.itemArrangement`.
