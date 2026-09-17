---
name: husi-topbar
description: Husi project's topbar design system. Use whenever adding a new screen in composeApp/, editing a Scaffold, building Compose UI with a topBar / tabs / search bar, wiring scroll-driven color changes (scrollBehavior / pinnedScrollBehavior), or touching Haze blur (HazeState, hazeSource, hazeBlur, CapsuleHeader). If a topbar is involved at all, consult this skill — do not reach for Material 3's TopAppBar or AppBarWithSearch directly.
---

# Husi Topbar Design

Husi replaces Material 3's default `TopAppBar` / `AppBarWithSearch` with a set of in-house "capsule"
components. All of them live in `composeApp/src/commonMain/kotlin/fr/husi/compose/Capsule.kt`.
Background blur comes from [Haze](https://github.com/chrisbanes/haze) 2.x (`haze`, `haze-blur`,
`haze-blur-material3`).

Before writing any topbar, answer one question first: **does the topbar sit directly above the
scrollable content, or is there something else (tabs, search, etc.) attached underneath?** That
choice determines which pattern to use.

## Component cheat sheet

| Component                 | Purpose                                                                                                                                  |
|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `CapsuleTopBar`           | Replacement for `TopAppBar`: nav icon + title + actions. Title auto-marquees with edge feathering when it overflows — see "Long titles". |
| `CapsuleSearchTopBar`     | Replacement for `AppBarWithSearch`: nav icon + search capsule + actions                                                                  |
| `CapsuleSearchInputField` | Input field wrapper for use inside `CapsuleSearchTopBar`. Handles centered placeholder + faded leading icon in the collapsed state       |
| `CapsuleActionButton`     | Member of `CapsuleActionsScope` (the receiver of `actions = { ... }`). Wraps one action icon in a capsule that uses the bar's haze state |
| `CapsuleHeader`           | Pattern B header block: a blurred `Column` whose tint follows the scroll state. Holds the topbar plus tabs / search / banners            |
| `CapsuleDefaults.blurStyle` | The one `HazeBlurStyle` for capsules and headers: Material 3 surface background, `tintColor` at 0.5 alpha, opaque fallback tint      |

## Visual contract

- Every capsule has a 1dp `outlineVariant` border.
- The fill depends on the `hazeState` passed to the bar:
  - **Non-null**: the capsule is clipped to its shape and blurs the content behind it
    (`hazeBlur(HazeInput.Backdrop(hazeState), CapsuleDefaults.blurStyle())`). The tint is
    `surfaceContainer` at 0.5 alpha (`CapsuleBlurTintAlpha`); the header passes its scroll-animated
    color as `tintColor` instead. The blur tint alpha is separate from the plain fill below, because
    the blur already keeps text readable.
  - **`null`**: the capsule draws a plain `CapsuleDefaults.containerColor` fill (`surfaceContainer`
    at 0.75 alpha) with no blur.
- The topbar row itself has **no background**. In Pattern A the scrolling content shows through
  around the capsules; in Pattern B the blurred `CapsuleHeader` is the background.
- Blur is enabled by default only where Haze trusts the platform. husi has `minSdk = 24`; on
  Android 11 and below Haze draws only `fallbackColorEffect`, which `blurStyle` sets to the opaque
  container color so text stays readable. Do not build a style without a fallback.

Top-level destinations (Configuration, Dashboard, Route, Log, Settings) live in `NavigationSuite`
(phone `NavigationBar`, desktop `WideNavigationRail`, TV drawer). Those screens pass
`navigationIcon = null` so the capsule row collapses the nav slot. Pushed screens use a back
`SimpleIconButton` (`arrow_back`) as `navigationIcon`.

## Haze wiring

Three pieces, always together:

1. `val hazeState = rememberHazeState()` in the screen.
2. `Modifier.hazeSource(hazeState)` on the root of the Scaffold **content slot** (or the scrolling
   container inside it). The source must never be an ancestor of the topbar — `topBar` and the
   content slot are siblings in `Scaffold`, which is what makes this work.
3. `hazeState = hazeState` on `CapsuleTopBar` / `CapsuleSearchTopBar` (and on `CapsuleHeader` in
   Pattern B).

`hazeState` is a required parameter with no default, so every call site states whether it blurs.
Pass `null` only when nothing can scroll under the bar: the content is placed with
`Modifier.paddingExceptBottom(innerPadding)` / `Modifier.padding(innerPadding)`, or the bar sits in
a plain `Column` above the content (`ProfilePickerContent`). Current `null` screens: `StunScreen`,
`NetworkQualityScreen`, `RuleSetMatchScreen`, `ConfigSettingScreen`, `TaskerActivity`,
`ProfilePickerContent`.

## Long titles

`CapsuleTopBar` handles title overflow automatically. The title slot is wrapped in
`Box(Modifier.weight(1f))` so the pill is bounded to the row space left over after the nav icon and
actions, and inside the pill the slot content is wrapped in `Modifier.basicMarquee()`. When the
content's intrinsic width exceeds the pill's bounded width, a 16dp horizontal edge feather is added
on each side via the shared `Modifier.fadingEdge(...)` from
`composeApp/src/commonMain/kotlin/fr/husi/compose/Fading.kt`. Short titles render at intrinsic
width with no marquee and no fade; actions stay right-pinned in both cases.

The title pill itself provides a bounded ripple via the internal `PillCapsule` surface, even though
the click has no business action. Keep that empty click handler in the component so all topbar
titles give consistent press feedback, and do not wrap caller-provided title content in another
clickable/ripple modifier.

Caller-side rule: just pass `title = { Text(stringResource(...)) }`. **Do not** add `maxLines = 1`,
`softWrap = false`, your own `Modifier.basicMarquee()`, or any width constraint on the title — the
component already does all of that, and stacking marquees / constraints breaks the layout.

`Modifier.fadingEdge(...)` is reusable outside the topbar. Pass a `ScrollableState` for
scroll-driven fades (e.g. LazyColumn top/bottom — this is the common case, and the function
defaults `fadeStart = false`, `fadeEnd = true` for that scenario), or omit the `ScrollableState`
(it defaults to `null`) for unconditional fade like the topbar's marquee branch uses.

## Action buttons

`actions` has the receiver `CapsuleActionsScope`, which extends `RowScope` and carries the bar's
`hazeState`. `CapsuleActionButton` is a member of that scope, so callers never pass the state:

```kotlin
actions = {
    CapsuleActionButton {
        SimpleIconButton(/* ... */)
    }
    CapsuleActionButton {
        Box {
            SimpleIconButton(/* more_vert */)
            DropdownMenuPopup(/* ... */) { /* ... */ }
        }
    }
},
```

`RowScope` and `BoxScope` carry `@LayoutScopeMarker`, so `CapsuleActionButton` cannot be called
from inside a `Box { }` that sits in `actions`. Put the `Box` **inside** the button (as above, and
as `husi-actions-dropdown-menu` describes), never around it.

## Pattern A — topbar sits directly above scrolling content

The common case. `RouteScreen`, `GroupScreen`, `LogcatScreen`, `ConfigEditScreen`, `AssetsScreen`
all use this.

```kotlin
val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
val windowInsets = WindowInsets.safeDrawing
val hazeState = rememberHazeState()

Scaffold(
    modifier = modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        CapsuleTopBar(
            hazeState = hazeState,
            navigationIcon = null, // top-level tab
            title = { Text(stringResource(Res.string.menu_xxx)) },
            actions = {
                CapsuleActionButton {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.update),
                        contentDescription = stringResource(Res.string.update),
                        onClick = { /* ... */ },
                    )
                }
            },
            windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            scrollBehavior = scrollBehavior,
        )
    },
) { innerPadding ->
    LazyColumn(
        modifier = Modifier.hazeSource(hazeState),
        contentPadding = innerPadding.withNavigation(),
        // ...
    ) { /* items */ }
}
```

Key points:

- No outer `Surface` around the topbar. The list scrolls under the capsules and they blur it.
- Content must reach the top of the screen and be offset with `contentPadding`
  (`innerPadding.withNavigation()`). If you pad with a Modifier instead, nothing ever passes under
  the bar; pass `hazeState = null` in that case.
- Pass `scrollBehavior` to `CapsuleTopBar` even if you aren't reading `overlappedFraction` here. The
  component sets `state.heightOffsetLimit` via `SideEffect`; without it `CapsuleHeader` can't read a
  meaningful `overlappedFraction` if the screen later moves to Pattern B.
- Pass `windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)` so the bottom inset
  is left to the Scaffold (used by FAB / `StatsBar`).
- If the content is a private composable, add a `modifier: Modifier = Modifier` parameter, apply it
  to its root, and pass `Modifier.hazeSource(hazeState)` from the Scaffold.
- Pushed screens set `navigationIcon` to a back button:

```kotlin
navigationIcon = {
    SimpleIconButton(
        imageVector = vectorResource(Res.drawable.arrow_back),
        contentDescription = stringResource(Res.string.back),
        onClick = onBackPress,
    )
},
```

## Pattern B — topbar has tabs / search bar / other non-content attached below

Used by `ConfigurationScreen`, `Dashboard`, `AbstractAppList`. The topbar and the attached elements
form one header block. The block is blurred as a whole, and its tint lerps from `containerColor` to
`scrolledContainerColor` as content scrolls under it. `CapsuleHeader` owns all of that — do not
rebuild the `animateColorAsState` + `lerp` + `Surface` combination in a screen.

```kotlin
val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
val hazeState = rememberHazeState()

Scaffold(
    modifier = modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        CapsuleHeader(
            hazeState = hazeState,
            scrollBehavior = scrollBehavior,
        ) {
            CapsuleTopBar(
                hazeState = hazeState,
                // ... navigationIcon / title / actions ...
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent, // required
            ) { /* tabs */ }
        }
    },
) { innerPadding ->
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .paddingHorizontal(innerPadding)
            .hazeSource(hazeState),
    ) { page ->
        PageContent(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + SagerFabClearance,
            ),
        )
    }
}
```

Required pieces:

1. **`CapsuleHeader(hazeState, scrollBehavior)`** wraps the whole header block. It is a `Column`;
   put children directly in it.
2. **`scrollBehavior` is passed into `CapsuleTopBar` / `CapsuleSearchTopBar`**. Their internal
   `SetHeightOffsetLimit` sets `state.heightOffsetLimit`. Without it, `overlappedFraction` always
   returns 0 and the header tint never changes.
3. **Tab rows use `containerColor = Color.Transparent`**. `PrimaryTabRow` /
   `PrimaryScrollableTabRow` default to an opaque surface that hides the blur.
4. **Content goes under the header.** The content slot takes only horizontal padding
   (`Modifier.paddingHorizontal(innerPadding)` from `compose/EdgeToEdge.kt`); the top and bottom
   padding go to each page as `contentPadding: PaddingValues`. Page composables take
   `contentPadding`, not a `bottomPadding: Dp`.
5. **Window insets go inside the header.** If the header needs a top inset that the topbar does not
   apply (Dashboard puts the tabs and `RemoteSessionBanner` below a bar with horizontal-only
   insets), add a `Column(Modifier.windowInsetsPadding(...))` **inside** `CapsuleHeader`. Passing
   the inset padding as `CapsuleHeader`'s `modifier` applies it before the blur and leaves the
   status bar area unblurred.

### Search topbar variant

`Dashboard` shows `CapsuleSearchTopBar` on the Connections page and `CapsuleTopBar` elsewhere,
sharing the same `scrollBehavior` and `hazeState`:

```kotlin
CapsuleHeader(
    hazeState = hazeState,
    scrollBehavior = scrollBehavior,
) {
    Column(
        modifier = Modifier.windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top)),
    ) {
        if (isConnectionsPage) {
            CapsuleSearchTopBar(
                hazeState = hazeState,
                inputField = searchInputField,
                navigationIcon = null,
                actions = { /* CapsuleActionButton { ... } */ },
                windowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        } else {
            CapsuleTopBar(
                hazeState = hazeState,
                navigationIcon = null,
                title = { Text(stringResource(Res.string.menu_dashboard)) },
                windowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        }
        RemoteSessionBanner(/* ... */)
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
        ) { /* ... */ }
    }
}
```

`CapsuleSearchInputField` is the standard way to construct `inputField`:

```kotlin
val searchInputField: @Composable () -> Unit = {
    CapsuleSearchInputField(
        textFieldState = vm.searchTextFieldState,
        searchBarState = searchBarState,
        onSearch = { focusManager.clearFocus() },
        placeholder = { Text(stringResource(Res.string.search_go)) },
        leadingIcon = { Icon(vectorResource(Res.drawable.search), null) },
        trailingIcon = if (searchBarState.currentValue == SearchBarValue.Expanded) {
            { SimpleIconButton(/* close icon */) }
        } else null,
    )
}
```

Search capsule gestures:

- `CapsuleSearchTopBar` supports `onSearchPillLongPress` for a secondary action on the search
  capsule shell. Use it when migrating an old title-bar long-press behavior, such as
  Configuration's "jump to selected proxy" action.
- Do **not** repurpose the search capsule's normal tap for navigation or scrolling. A tap belongs
  to the search field and should keep expanding/focusing search.
- If a screen needs a "tap the current section again to jump within the list" behavior, attach it
  to the selected tab's `onClick` branch instead of the search field.

`ExpandedFullScreenSearchBar` content is not under the header, so pages rendered there get
`contentPadding = PaddingValues()`.

### Material 3 SearchBar special case (`AbstractAppList`)

`AbstractAppList` (the per-app selection screen) places Material 3's `SearchBar` under the topbar —
not `CapsuleSearchTopBar`, because this screen needs `ExpandedFullScreenSearchBar` integration.
`SearchBar` has its own pill styling driven by `SearchBarTokens.ContainerColor`, and its perceived
"border" comes from the color contrast against whatever surrounds it.

**Do not** pass `colors = SearchBarDefaults.colors(...)` to blend it with the header. Leave
`SearchBar`'s colors alone and let the blurred `CapsuleHeader` surround it:

```kotlin
CapsuleHeader(
    hazeState = hazeState,
    scrollBehavior = scrollBehavior,
) {
    CapsuleTopBar(hazeState = hazeState, ..., scrollBehavior = scrollBehavior)
    SearchBar(
        state = searchBarState,
        inputField = searchInputField,
        modifier = Modifier.fillMaxWidth(),
        // no `colors =` — keep SearchBar's own pill look
    )
    extraTopBarContent()
}
```

Its list already uses `contentPadding = innerPadding.withNavigation()`, and `hazeSource` sits on the
`Crossfade` in the content slot.

## Pattern selection

```
What does your topBar look like?
├── A single bar sitting directly above a LazyColumn / Column of scrolling content
│   → Pattern A:
│     ├── rememberHazeState(); hazeSource on the content slot
│     ├── CapsuleTopBar(hazeState = hazeState), no Surface wrapper
│     └── content offset with contentPadding, not Modifier padding
│         (Modifier padding → nothing passes under the bar → hazeState = null)
│
└── Topbar with tabs / search / other non-content attached underneath
    → Pattern B:
      ├── CapsuleHeader(hazeState, scrollBehavior) around the whole block
      ├── scrollBehavior + hazeState passed into CapsuleTopBar / CapsuleSearchTopBar
      ├── TabRow containerColor = Color.Transparent
      │   (Material 3 SearchBar is the exception — keep its default colors)
      └── content: paddingHorizontal(innerPadding) + hazeSource; pages take contentPadding
```

## Common pitfalls

- **No blur, capsules look flat.** Either `hazeState` is `null`, or no `hazeSource` with the same
  state exists in the content slot, or the content is placed with Modifier padding so nothing is
  ever under the bar.
- **`hazeSource` on a node that contains the topbar** (e.g. on the `Scaffold` modifier). A source
  must not be an ancestor of its effect. Put it on the content slot.
- **Header tint never changes on scroll.** `scrollBehavior` is not passed into `CapsuleTopBar` /
  `CapsuleSearchTopBar`; their `SetHeightOffsetLimit` is what makes `overlappedFraction` non-zero.
- **Tabs are a solid band inside the blurred header.** The tab row still uses its default opaque
  `containerColor`. Pass `Color.Transparent`.
- **Status bar area is not blurred.** Window-inset padding was passed as `CapsuleHeader`'s
  `modifier`. Move it into a `Column` inside the header.
- **First list item hidden under the header in Pattern B.** The page ignores the top value of
  `contentPadding`. Pages must forward the whole `PaddingValues` to their `LazyColumn`.
- **SearchBar lost its border / the pill is invisible.** Don't override `SearchBar`'s
  `colors.containerColor`. Its outline is the color contrast with the surrounding area.
- **`CapsuleActionButton` is unresolved inside `Box { }`.** `@LayoutScopeMarker` hides the outer
  `CapsuleActionsScope`. Put the `Box` inside the button.
- **Action icons without the capsule background.** Every `SimpleIconButton` inside
  `actions = { ... }` must be wrapped in `CapsuleActionButton { ... }`.
- **Custom blur style for one capsule or header.** Use `CapsuleDefaults.blurStyle(tintColor)` with an opaque color;
  a style without `fallbackColorEffect` renders transparent on Android 11 and below.
- **Wrong navigation icon.** Top-level tabs pass `navigationIcon = null` (the capsule row
  collapses that slot). Pushed pages use `SimpleIconButton(arrow_back)` — see `AssetsScreen` and
  `GroupScreen`. Do not add a hamburger; top-level switching is `NavigationSuite`.
- **Bottom inset fighting with FAB / StatsBar.** Give the topbar
  `windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)` so the Scaffold owns the
  bottom inset. Use `innerPadding.withNavigation()` on LazyColumn `contentPadding`.
- **Manually adding `Modifier.basicMarquee()`, `maxLines = 1`, or width constraints to a title
  `Text`.** `CapsuleTopBar` already bounds the title pill (`Box(Modifier.weight(1f))`) and applies
  `basicMarquee` + a conditional `fadingEdge` based on overflow detection inside the pill. Adding
  your own marquee compounds the animation, and width constraints fight the slot's weighted layout.
  Just pass a plain `Text(stringResource(...))`.

## Reference implementations

- Pattern A: `composeApp/src/commonMain/kotlin/fr/husi/ui/RouteScreen.kt`, `GroupScreen.kt`,
  `LogcatScreen.kt`
- Pattern A with a private content composable taking `modifier`:
  `composeApp/src/commonMain/kotlin/fr/husi/ui/RouteSettingsScreen.kt`
- Pattern A with `hazeState = null` (Modifier-padded content):
  `composeApp/src/commonMain/kotlin/fr/husi/ui/tools/StunScreen.kt`
- Pattern B (tabs + conditional search + inner inset Column):
  `composeApp/src/commonMain/kotlin/fr/husi/ui/dashboard/Dashboard.kt`
- Pattern B (search + tabs):
  `composeApp/src/commonMain/kotlin/fr/husi/ui/configuration/ConfigurationScreen.kt`
- Pattern B (Material 3 SearchBar special case):
  `composeApp/src/androidMain/kotlin/fr/husi/ui/AbstractAppList.kt`
- Component source: `composeApp/src/commonMain/kotlin/fr/husi/compose/Capsule.kt`
