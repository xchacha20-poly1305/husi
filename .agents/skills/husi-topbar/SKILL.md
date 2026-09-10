---
name: husi-topbar
description: Husi project's topbar design system. Use whenever adding a new screen in composeApp/, editing a Scaffold, building Compose UI with a topBar / tabs / search bar, or wiring scroll-driven color changes (scrollBehavior / pinnedScrollBehavior). If a topbar is involved at all, consult this skill — do not reach for Material 3's TopAppBar or AppBarWithSearch directly.
---

# Husi Topbar Design

Husi replaces Material 3's default `TopAppBar` / `AppBarWithSearch` with a set of in-house "capsule"
components. All of them live in `composeApp/src/commonMain/kotlin/fr/husi/compose/CapsuleTopBar.kt`.

Before writing any topbar, answer one question first: **does the topbar sit directly above the
scrollable content, or is there something else (tabs, search, etc.) attached underneath?** That
choice determines which pattern to use.

## Component cheat sheet

| Component                 | Purpose                                                                                                                                     |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `CapsuleTopBar`           | Replacement for `TopAppBar`: nav icon + title + actions. Title auto-marquees with edge feathering when it overflows — see "Long titles".    |
| `CapsuleSearchTopBar`     | Replacement for `AppBarWithSearch`: nav icon + search capsule + actions                                                                     |
| `CapsuleSearchInputField` | Input field wrapper for use inside `CapsuleSearchTopBar`. Handles centered placeholder + faded leading icon in the collapsed state          |
| `CapsuleActionButton`     | Wraps an action icon (e.g. `SimpleIconButton`) in capsule styling. Every entry inside `actions = { ... }` should be wrapped in one of these |

Visual contract: the capsule fills are semi-transparent (`surfaceContainer.copy(alpha = 0.75f)`)with
a 1dp `outlineVariant` border. The topbar itself has **no background** — it floats over whatever is
beneath. The visible "background" of the topbar comes from either an outer `Surface` (Pattern B) or
the scrolling content showing through (Pattern A).

Top-level destinations (Configuration, Dashboard, Route, Log, Settings) live in `NavigationSuite`
(phone `NavigationBar`, desktop `WideNavigationRail`, TV drawer). Those screens pass
`navigationIcon = null` so the capsule row collapses the nav slot. Pushed screens use a back
`SimpleIconButton` (`arrow_back`) as `navigationIcon`.

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

## Pattern A — topbar sits directly above scrolling content

The common case. `RouteScreen`, `GroupScreen`, `LogcatScreen`, `ConfigEditScreen`,
`ConnectionDetail`, `AssetsScreen` all use this.

```kotlin
val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
val windowInsets = WindowInsets.safeDrawing

Scaffold(
    modifier = modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        CapsuleTopBar(
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
                // additional actions, each wrapped in its own CapsuleActionButton
            },
            windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            scrollBehavior = scrollBehavior,
        )
    },
) { innerPadding ->
    LazyColumn(
        contentPadding = innerPadding.withNavigation(),
        // ...
    ) { /* items */ }
}
```

Key points:

- The topbar does **not** need an outer `Surface(color = ...)`. The capsules float on top of the
  LazyColumn and list items scrolling behind them is the intended look.
- Pass `scrollBehavior` to `CapsuleTopBar` even if you aren't reading `overlappedFraction` here. The
  component sets `state.heightOffsetLimit` via `SideEffect`; without that step, Pattern B can't read
  a meaningful `overlappedFraction` later if you ever upgrade the screen.
- Use `innerPadding.withNavigation()` for LazyColumn's `contentPadding`; use
  `Modifier.paddingExceptBottom(innerPadding)` for the Modifier form. Both ensure the bottom
  navigation/FAB area is preserved.
- Pass `windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)` so the bottom inset
  is left to the Scaffold (used by FAB / `StatsBar`).
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

Used by `Configuration`, `Dashboard`, `AbstractAppList`. The topbar + the attached element
together form a single "header block", and the whole block should change color as the user scrolls (
smoothly lerping from `containerColor` to `scrolledContainerColor`).

```kotlin
val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
val topAppBarColors = TopAppBarDefaults.topAppBarColors()
val appBarContainerColor by animateColorAsState(
    targetValue = lerp(
        topAppBarColors.containerColor,
        topAppBarColors.scrolledContainerColor,
        scrollBehavior.state.overlappedFraction.fastCoerceIn(0f, 1f),
    ),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "appBarContainerColor",
)

Scaffold(
    modifier = modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        Surface(color = appBarContainerColor) {
            Column {
                CapsuleTopBar(
                    // ... navigationIcon / title / actions ...
                    windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior,
                )
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = appBarContainerColor, // required
                ) { /* tabs */ }
            }
        }
    },
) { /* ... */ }
```

Three non-negotiable pieces, all required:

1. **`Surface(color = appBarContainerColor)`** wraps the whole header block (topbar +
   tabs/search/etc). `CapsuleTopBar` is transparent — the visible "background" is this Surface
   showing through.
2. **`scrollBehavior` is passed into `CapsuleTopBar` / `CapsuleSearchTopBar`**. Their internal
   `SetHeightOffsetLimit` sets `state.heightOffsetLimit = -60.dp.toPx()`. Without it,
   `overlappedFraction`'s `if (heightOffsetLimit != 0f) { ... } else { 0f }` always returns 0 and
   the color never animates.
3. **`TabRow` is given an explicit `containerColor = appBarContainerColor`**. `PrimaryTabRow` /
   `PrimaryScrollableTabRow` default to an opaque surface color that masks the outer Surface's
   animated color.

### Search topbar variant

`Dashboard` shows `CapsuleSearchTopBar` on the Connections page and `CapsuleTopBar` elsewhere,
sharing the same `scrollBehavior`:

```kotlin
Surface(color = appBarContainerColor) {
    Column {
        if (isConnectionsPage) {
            CapsuleSearchTopBar(
                inputField = searchInputField,
                navigationIcon = null,
            ),
            actions = { /* CapsuleActionButton { ... } */ },
            windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            scrollBehavior = scrollBehavior,
            )
        } else {
            CapsuleTopBar(
                navigationIcon = null,
            ),
            title = { Text(stringResource(Res.string.menu_dashboard)) },
            windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            scrollBehavior = scrollBehavior,
            )
        }
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = appBarContainerColor,
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

### Material 3 SearchBar special case (`AbstractAppList`)

`AbstractAppList` (the per-app selection screen) places Material 3's `SearchBar` under the topbar —
not `CapsuleSearchTopBar`, because this screen needs `ExpandedFullScreenSearchBar` integration.
`SearchBar` has its own pill styling driven by `SearchBarTokens.ContainerColor`, and its perceived "
border" comes from the color contrast against whatever surrounds it.

**Do not** pass `colors = SearchBarDefaults.colors(containerColor = appBarContainerColor)`. Matching
the surrounding color makes the pill blend into the background and the border disappears. Leave
`SearchBar`'s colors alone and let the outer `Surface` animate around it (the topbar area, the
gutters next to the SearchBar, and `extraTopBarContent`):

```kotlin
Surface(color = appBarContainerColor) {
    Column {
        CapsuleTopBar(..., scrollBehavior = scrollBehavior)
        SearchBar(
            state = searchBarState,
            inputField = searchInputField,
            modifier = Modifier.fillMaxWidth(),
            // no `colors =` — keep SearchBar's own pill look
        )
        extraTopBarContent()
    }
}
```

## Pattern selection

```
What does your topBar look like?
├── A single bar sitting directly above a LazyColumn / Column of scrolling content
│   → Pattern A: CapsuleTopBar with no Surface wrapper
│
└── Topbar with tabs / search / other non-content attached underneath
    └── The whole block must change color as content scrolls behind it
        → Pattern B:
        ├── Compute appBarContainerColor with animateColorAsState + lerp
        ├── Wrap header in Surface(color = appBarContainerColor)
        ├── Pass scrollBehavior into CapsuleTopBar / CapsuleSearchTopBar
        └── Pass containerColor = appBarContainerColor to the TabRow
            (Material 3 SearchBar is the exception — keep its default colors)
```

## Common pitfalls

- **`overlappedFraction` is always 0 / color never changes.** You forgot to pass `scrollBehavior`
  into `CapsuleTopBar` / `CapsuleSearchTopBar`. Their internal `SetHeightOffsetLimit` is the
  prerequisite for the color animation. Material 3's `TopAppBar` sets
  `heightOffsetLimit = -placeable.height` inside its Layout; the capsule components achieve the same
  via `SideEffect` + a fixed 60.dp, but only when `scrollBehavior` is actually wired in.
- **Tabs stay one color while only the topbar half animates.** `PrimaryTabRow` defaults to an opaque
  surface containerColor that hides the outer Surface. Explicitly pass
  `containerColor = appBarContainerColor`.
- **SearchBar lost its border / the pill is invisible.** Don't override `SearchBar`'s
  `colors.containerColor`. Its outline is the color contrast with the surrounding area.
- **Action icons look wrong / don't have the capsule background.** Every `SimpleIconButton` inside
  `actions = { ... }` must be wrapped in `CapsuleActionButton { ... }`. Don't drop bare icon buttons
  in directly.
- **Wrong navigation icon.** Top-level tabs pass `navigationIcon = null` (the capsule row
  collapses that slot). Pushed pages use `SimpleIconButton(arrow_back)` — see `AssetsScreen` and
  `GroupScreen`. Do not add a hamburger; top-level switching is `NavigationSuite`.
- **Bottom inset fighting with FAB / StatsBar.** Give the topbar
  `windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)` so the Scaffold owns the
  bottom inset. Use `innerPadding.withNavigation()` on LazyColumn `contentPadding`, or
  `Modifier.paddingExceptBottom(innerPadding)` for the Modifier form.
- **Manually adding `Modifier.basicMarquee()`, `maxLines = 1`, or width constraints to a title
  `Text`.** `CapsuleTopBar` already bounds the title pill (`Box(Modifier.weight(1f))`) and applies
  `basicMarquee` + a conditional `fadingEdge` based on overflow detection inside the pill. Adding
  your own marquee compounds the animation, and width constraints fight the slot's weighted layout.
  Just pass a plain `Text(stringResource(...))`.

## Reference implementations

- Pattern A: `composeApp/src/commonMain/kotlin/fr/husi/ui/RouteScreen.kt`, `GroupScreen.kt`,
  `LogcatScreen.kt`
- Pattern B (tabs + conditional search):
  `composeApp/src/commonMain/kotlin/fr/husi/ui/dashboard/Dashboard.kt`
- Pattern B (search + tabs):
  `composeApp/src/commonMain/kotlin/fr/husi/ui/configuration/ConfigurationScreen.kt`
- Pattern B (Material 3 SearchBar special case):
  `composeApp/src/androidMain/kotlin/fr/husi/ui/AbstractAppList.kt`
- Component source: `composeApp/src/commonMain/kotlin/fr/husi/compose/CapsuleTopBar.kt`
