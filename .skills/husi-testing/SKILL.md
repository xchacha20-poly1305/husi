---
name: husi-testing
description: Husi project's Kotlin/Compose Multiplatform testing conventions. Use whenever adding, updating, or debugging tests under composeApp/ — ViewModels, updaters, ktx helpers, anything in commonTest/desktopTest. Also use when a piece of production code needs a DI seam to become testable (HTTP, DataStore, Libcore). Do not reach for mockk first or invent a new base class — consult this skill before writing the first test in a new file.
---

# Husi Testing

Husi tests its Kotlin code from `composeApp/src/commonTest/` against the desktop JVM target. There
is no Android instrumented test suite. Tests follow a strong **inject seams + fake them** style:
the existing suite uses `mockk` almost nowhere despite it being on the classpath — every reusable
test dependency is a hand-rolled `Fake*` registered through Koin or passed via constructor.

Before writing tests, answer two questions:

1. **Does the code under test touch Koin-resolved singletons (`Repository`, `HttpClientFactory`,
   `DataStore`)?** → extend one of the Koin-aware base classes below.
2. **Does it use `viewModelScope` / `Dispatchers.IO` / `delay`?** → you need a
   `StandardTestDispatcher` and `runTest(dispatcher.scheduler) { ... advanceUntilIdle() }`.

The answers determine the base class and the boilerplate. Don't roll your own.

## Toolchain & where things live

| Item               | Value                                                             |
|--------------------|-------------------------------------------------------------------|
| Test source set    | `composeApp/src/commonTest/kotlin/` (runs as `desktopTest`)       |
| Desktop-only tests | `composeApp/src/desktopTest/kotlin/` (rare; one file at present)  |
| Test framework     | `kotlin.test` (`@Test`, `assertEquals`, `assertIs`, …) on JUnit 5 |
| Coroutines         | `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`)   |
| Mocking            | `mockk` exists but is **avoided** — prefer fakes (see below)      |
| Run all tests      | `make test_gradle` (= `./gradlew :composeApp:allTests`)           |
| Run one class      | `./gradlew :composeApp:desktopTest --tests fr.husi.foo.BarTest`   |
| Test reports       | `composeApp/build/test-results/desktopTest/TEST-*.xml`            |

Test files mirror the package of the file under test:
`fr.husi.ui.tools.SpeedTestScreenViewModel` →
`commonTest/kotlin/fr/husi/ui/tools/SpeedTestScreenViewModelTest.kt`.

Function names use backticked sentences describing the behaviour, e.g.
`` `setServer with blank value sets urlError` ``. Match the existing style — see
`MainViewModelTest.kt`, `ProfileEditorViewModelTest.kt`, the `fmt/` tests.

## Base class cheat sheet

All three live in `composeApp/src/commonTest/kotlin/fr/husi/test/`.

| Base class                   | Sets up                                                                                                | Use when…                                                                          |
|------------------------------|--------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| _(nothing)_                  | —                                                                                                      | Pure logic — `fmt/`, `ktx/`, `RuleItemTest`. No coroutines, no Koin.               |
| `MainDispatcherTest`         | `StandardTestDispatcher` swapped into `Dispatchers.Main`.                                              | `viewModelScope` is involved but no Koin singletons are touched.                   |
| `HusiKoinTest`               | + `initHusiKoin(FakeRepository())` / `stopKoin()`.                                                     | Code resolves `Repository` (e.g. `resolveRepository()`), but no coroutine timing.  |
| `HusiKoinMainDispatcherTest` | Both of the above.                                                                                     | Default for ViewModel tests. Resolves Koin singletons and uses `viewModelScope`.   |
| `HusiHttpKoinTest`           | + `HttpClientFactory` overridden with `FakeHttpClientFactory`, `DataStore.configurationStore.reset()`. | The code under test calls `Libcore.newHttpClient()` (directly or via the factory). |

The hooks `preStartKoin / postStartKoin / preStopKoin / postStopKoin` (suspend, all `open`) are the
extension points. `HusiHttpKoinTest` exposes its own `postStartKoinWithHttp()` because it already
finalises `postStartKoin`.

## Why fakes, not mockk

The codebase uses `Fake*` classes that implement the production interfaces (`FakeRepository`,
`FakeHttpClientFactory`, `FakeHTTPClient`, `FakeHTTPRequest`, `FakeHTTPResponse`, `FakeURL`). They:

- compile-check against the real interface so renaming/refactoring stays sound,
- can be reused across many tests with no per-test setup boilerplate,
- read like a recorder — you assert on `lastClient?.lastRequest?.headers["Referer"]` instead of
  decoding `verify { … }` blocks,
- never require `mockkStatic` against gobind classes, which is fragile on the desktop JVM.

If you find yourself reaching for `mockk`, first check whether a fake already exists, or whether
the right move is to extract an interface and add a fake to `commonTest/kotlin/fr/husi/test/`. Treat
each new fake as infrastructure for *future* tests, not just the one you're writing.

## Refactoring for testability

When the code you want to test calls a global static (`Libcore.xxx`) or an internal Kotlin object
(`DataStore`), introduce a DI seam instead of working around it in the test:

1. **Interface in `commonMain`** (e.g. `fr.husi.libcore.HttpClientFactory`) wrapping the static.
2. **Default object impl** (e.g. `LibcoreHttpClientFactory`) that just delegates.
3. **Koin binding** in `commonUiModule()` (or a new module if domain-specific).
4. **Constructor injection on the consumer**, with the Koin singleton as the default.
5. **Fake in `commonTest`** implementing the same interface; recording inputs, replaying canned
   outputs. Register it via `loadKoinModules(module { single<T> { fake } })` in a base class.

Worked example: `HttpClientFactory.kt` + `LibcoreHttpClientFactory` + `FakeHttpClientFactory` +
`HusiHttpKoinTest`. `DataStore` itself is *not* abstracted — it's a Kotlin object backed by
DataStore-Preferences, and `DataStore.configurationStore.reset()` (in `postStartKoin`) is enough
for tests to start clean. Don't replicate that level of plumbing unless you need to.

Inject `CoroutineDispatcher` (default `Dispatchers.IO`) on ViewModels that use it explicitly —
otherwise `advanceUntilIdle()` can't drive the background work. Don't inject `viewModelScope`
itself; let `StandardTestDispatcher` + `Dispatchers.setMain` (handled by `MainDispatcherTest`)
control it.

> **Koin registration gotcha for ViewModels with default-valued params.** `viewModelOf(::FooVM)`
> uses reflection and tries to resolve **every** constructor parameter from Koin — it ignores
> Kotlin default values. If your ViewModel takes `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`
> (or any other type that isn't bound), `viewModelOf` blows up at runtime with
> `NoDefinitionFoundException: No definition found for type 'kotlinx.coroutines.CoroutineDispatcher'`.
> Use an explicit factory instead and only pass the things Koin actually has:
>
> ```kotlin
> viewModel { FooViewModel(httpClientFactory = get()) }
> ```
>
> Reserve `viewModelOf(...)` for ViewModels whose constructor only takes Koin-bound types.

## ViewModel test recipe

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class FooViewModelTest : HusiKoinMainDispatcherTest() {

    private fun newViewModel() = FooViewModel(
        // Inject the things you faked. Defaults from constructor stay for production.
        ioDispatcher = dispatcher,
    )

    @Test
    fun `setX writes through to state`() = runTest(dispatcher.scheduler) {
        val vm = newViewModel()

        vm.setX("hello")
        advanceUntilIdle()

        assertEquals("hello", vm.uiState.value.x)
    }

    @Test
    fun `doThing emits Snackbar event`() = runTest(dispatcher.scheduler) {
        val vm = newViewModel()

        val event = backgroundScope.async { vm.uiEvent.first() }

        vm.doThing()
        advanceUntilIdle()

        assertIs<FooUiEvent.Snackbar>(event.await())
    }
}
```

Key points:

- Use `runTest(dispatcher.scheduler)` — not just `runTest {}`. Without passing the scheduler, the
  test dispatcher and `viewModelScope` end up driven by different clocks and `advanceUntilIdle`
  becomes a no-op.
- Collect `SharedFlow` events with `backgroundScope.async { flow.first() }` *before* triggering the
  emit. `_uiEvent` defaults to `replay = 0`; if you start collecting after the emit, you'll
  deadlock.
- For `StateFlow` you can read `.value` directly after `advanceUntilIdle()` — no collector needed.
- Reset any global object state your test mutates (e.g. `DataStore.serviceState`) in `@AfterTest`.
  `HusiHttpKoinTest` resets `configurationStore`; it does not reset `serviceState` because that's a
  `@Volatile var` outside the preference store.

## Testing HTTP / Libcore code

If the code calls `Libcore.newHttpClient()` directly, **refactor it to use `HttpClientFactory`**
first (see "Refactoring for testability"). Then in the test:

```kotlin
class BarUpdaterTest : HusiHttpKoinTest() {

    @Test
    fun `update issues a GET to the configured URL`() = runTest(dispatcher.scheduler) {
        fakeHttp.nextDownloadBytes = 8 * 1024
        // run code under test
        // …
        val request = assertNotNull(fakeHttp.lastClient?.lastRequest)
        assertEquals("https://example.com/list", request.url)
        assertEquals(fakeHttp.userAgent, request.userAgent)
    }

    @Test
    fun `update surfaces error when execute throws`() = runTest(dispatcher.scheduler) {
        fakeHttp.nextThrowable = IOException("boom")
        // assert the code under test emits whatever error event it should
    }
}
```

`FakeHttpClientFactory` supports:

- `nextDownloadBytes` — total bytes the next response reports through `CopyCallback.setLength`.
- `nextChunkCount` — how many `CopyCallback.update(n)` calls drive the progress callback.
- `nextThrowable` — when set, the next `execute()` throws this instead of returning a response.
- `lastClient.socks5` / `lastRequest.headers` / `lastRequest.contentZero` / `lastResponse.closed`
  for assertions on what the production code configured.

Source: `composeApp/src/commonTest/kotlin/fr/husi/test/FakeHttpClientFactory.kt`.

## DataStore in tests

`DataStore` is a Kotlin `object` backed by a real persistent file via
`DataStorePreferenceDataStore`.
Its file path is resolved through `resolveRepository().resolveDatabaseFile(...)`, which requires
Koin to be initialised — that's why ViewModel/updater tests **must** go through `HusiKoinTest` or
deeper, not `MainDispatcherTest` alone.

Reset between tests:

```kotlin
override suspend fun postStartKoin() {
    DataStore.configurationStore.reset()
}
```

`HusiHttpKoinTest` already does this. If you extend it, you don't have to reset again.

For non-preference fields (e.g. `DataStore.serviceState`, which is `@Volatile var serviceState`),
restore them yourself in `@AfterTest`.

## Common pitfalls

- **Tests hang or `advanceUntilIdle()` does nothing.** You passed `runTest { ... }` without
  `dispatcher.scheduler`. Use `runTest(dispatcher.scheduler) { ... }`.
- **`uiEvent.first()` returns immediately with the wrong event** (or never returns). You started
  collecting *after* the producer emitted, into a 0-replay `SharedFlow`. Wrap the collection in
  `backgroundScope.async { flow.first() }` *before* triggering the producer.
- **`DataStore` access throws `IllegalStateException: KoinApplicationException`.** You extended
  `MainDispatcherTest` instead of `HusiKoinMainDispatcherTest`. `DataStore.configurationStore`'s
  factory calls `resolveRepository()` which needs Koin.
- **A test "passes" but only because production code silently swallowed the exception.** Inspect the
  `FakeHTTPClient` / `FakeHTTPRequest` recorders — `lastClient` being `null` is usually the
  smoking gun. Treat unread recorders as a smell.
- **Tests pollute each other via `DataStore.serviceState` or other `var` globals.** Reset them in
  `@AfterTest`. The Koin / configurationStore lifecycle resets between tests automatically; loose
  vars do not.
- **Reaching for `mockkStatic(Libcore::class)`.** That's a refactor-shaped problem in disguise. Add
  an interface seam (see the `HttpClientFactory` worked example) and put the fake in `commonTest`.
- **Forgetting to register a new `viewModelOf(::FooViewModel)` in `commonNavigationModule` after
  switching the Composable to `koinViewModel<...>()`.** The Compose preview will still compile
  (constructor has defaults) but production will throw `NoDefinitionFound` at runtime.
- **`viewModelOf(::FooViewModel)` throws `NoDefinitionFound` for a defaulted constructor param**
  (e.g. `CoroutineDispatcher`). `viewModelOf` resolves *all* params from Koin and doesn't see
  Kotlin defaults. Switch to `viewModel { FooViewModel(httpClientFactory = get()) }`.

## Reference implementations

- ViewModel + flows + Koin: `commonTest/kotlin/fr/husi/ui/MainViewModelTest.kt`
- ViewModel without Koin (pure state): `commonTest/kotlin/fr/husi/ui/AssetsScreenViewModelTest.kt`
- StateFlow `isDirty` collector pattern:
  `commonTest/kotlin/fr/husi/ui/profile/ProfileEditorViewModelTest.kt`
- Pure logic, no base class: `commonTest/kotlin/fr/husi/fmt/V2RayFmtTest.kt`,
  `commonTest/kotlin/fr/husi/ktx/MapsKtTest.kt`
- HTTP-touching code with fakes:
  `commonTest/kotlin/fr/husi/ui/tools/SpeedTestScreenViewModelTest.kt`
- Background scheduling: `commonTest/kotlin/fr/husi/bg/SubscriptionAutoUpdateTest.kt`,
  `RouteAssetAutoUpdateTest.kt`

## Source layout reminders

- Production seam: `composeApp/src/commonMain/kotlin/fr/husi/libcore/HttpClientFactory.kt`
- Fakes: `composeApp/src/commonTest/kotlin/fr/husi/test/FakeHttpClientFactory.kt`
- Base classes:
    - `commonTest/kotlin/fr/husi/test/MainDispatcherTest.kt`
    - `commonTest/kotlin/fr/husi/test/HusiKoinTest.kt`
    - `commonTest/kotlin/fr/husi/test/HusiKoinMainDispatcherTest.kt`
    - `commonTest/kotlin/fr/husi/test/HusiHttpKoinTest.kt`
- Koin module registering production singletons:
  `composeApp/src/commonMain/kotlin/fr/husi/di/Koin.kt`
- ViewModel registry: `composeApp/src/commonMain/kotlin/fr/husi/di/Navigation.kt`
  (`viewModelOf(::FooViewModel)` inside `scope<MainScreenScope> { ... }`)
