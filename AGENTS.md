# Project overview

Husi is a recreational proxy tool for Android and Desktop (Linux/macOS/Windows). The Kotlin/Compose
Multiplatform UI in `composeApp/` calls into a Go core (`libcore/`) via JNI; protocol plugins under
`plugin/` are separately built from Go/Rust submodules and packaged as auxiliary Android APKs.

# Build & development commands

All build orchestration goes through the root `Makefile`. Toolchain: **JDK**, **Android NDK**, **Go
**, **Zig** (used for the desktop launcher and Darwin cross-builds) — pinned versions live in
`buildScript/init/`. `./run <subcmd>` is a generic dispatcher that resolves to
`buildScript/<subcmd>.sh`.

First-time setup needs git submodules for plugins and library pins:

```
./run lib source     # = git submodule update --init --recursive
make assets          # downloads geoip/geosite into composeApp resources
```

Common targets:

| Target                                                      | Purpose                                                                          |
|-------------------------------------------------------------|----------------------------------------------------------------------------------|
| `make assets`                                               | Download geoip/geosite into `composeApp` resources (run once before first build) |
| `make libcore_android`                                      | Build Go core into `composeApp/libs/libcore.aar` (required for Android builds)   |
| `make libcore` / `make libcore_desktop DESKTOP_TARGETS=...` | Build Go core into `composeApp/libs/libcore-desktop-<os>-<arch>.jar`             |
| `make apk` / `make apk_debug`                               | Assemble `androidApp:assembleFossRelease` / `Debug`                              |
| `make desktop` / `make desktop_release`                     | Run Compose desktop app via `gradlew :composeApp:run[Release]`                   |
| `make desktop_uberjar`                                      | Single fat jar in `composeApp/build/compose/jars/`                               |
| `make desktop_package[_linux/_macos/_windows]`              | Native packages under `composeApp/build/compose/packages/`                       |
| `make launcher`                                             | Build the Zig native launcher (used by Linux/macOS/Windows installers)           |
| `make plugin PLUGIN=<name>`                                 | Assemble plugin APK; valid names: `hysteria2 juicity naive mieru shadowquic`     |
| `make aboutlibraries`                                       | Regenerate OSS license metadata (run before release)                             |
| `make generate_option`                                      | Regenerate sing-box option mappings (boxoption); output piped through `$CLIP`    |

The `BUILD_PLUGIN` env var (read in `settings.gradle.kts`) controls which plugin modules are
included; `BUILD_PLUGIN=none` excludes all plugins to speed up app-only builds (this is what the
Makefile does).

Cross-compiling desktop libcore: pass `JNI_INCLUDE=/path/to/jni` when JNI headers aren't
auto-detected; for Darwin targets on non-Darwin hosts also pass `DARWIN_SDK=/path/to/MacOSX.sdk` (
uses `zig cc`). Linux desktop builds pull in `cronet-go` for naive outbound — set `CRONET_GO_ROOT`
if it's not in `../../cronet-go` or `$HOME/cronet-go`.

In restricted environments without a `cronet-go` checkout (or without network access to fetch
one), pass `NO_NAIVE=1` to `make libcore` / `make libcore_desktop` (or `--no-naive` to
`libcore/build.sh` directly) to drop the `with_naive_outbound` build tag and skip the cronet-go
toolchain setup entirely. The resulting build omits the naive outbound protocol.

Desktop Gradle picks the libcore jar from `os.name`/`os.arch`; override with
`./gradlew -p composeApp run -PdesktopTarget=linux/amd64`. Missing jars fail the build immediately
rather than silently falling back.

## Tests & lint

```
make test                # = test_gradle + test_go
make test_gradle         # ./gradlew :composeApp:allTests (JUnit5)
make test_go             # cd libcore && go test -v -count=1 ./...
make lint_go             # GOOS=android golangci-lint run ./...
make fmt_go              # gofumpt + gofmt + gci (run before committing Go)
```

Run a single Gradle test class: `./gradlew :composeApp:desktopTest --tests fr.husi.SomeTest`. Run a
single Go test: `cd libcore && go test -run TestName ./pkg/...`. Install Go tooling once with
`make fmt_go_install lint_go_install`.

# Architecture

## Module layout

- `libcore/` — Go module exposed to JVM. Built **two ways**:
    - Android via `gomobile` → `composeApp/libs/libcore.aar` (entry: `libcore.go`, with sibling Go
      files for tun, dns, ping, ruleset, plugin glue, etc.).
    - Desktop via `anja` (JNI bindings) → per-platform `libcore-desktop-<os>-<arch>.jar` checked
      into `composeApp/libs/` for IDE consumption.
    - `libcore/cmd/` holds `boxoption` (option codegen), `boxversion`, `licencecollect`,
      `ruleset_generate`. `libcore/plugin/` houses Go-side plugin support: outbound adapters (
      `http`, `juicity`, `trusttunnel`, `vless`), plus `mieruproto` (Mieru traffic-pattern
      protobuf), `raybridge` (*ray-compatible API shim), `plugindns` (plugin DNS conn plumbing), and
      `pluginoption` (option types/constants for hooked protocols).
- `composeApp/` — Kotlin Multiplatform shared module (Android library + JVM `desktop` target).
  Source sets: `commonMain`, `androidMain`, `desktopMain`, plus `androidDebug` and tests. Package
  root is `fr.husi`.
- `androidApp/` — thin Android `application` that depends on `:composeApp`. Defines the
  `AndroidManifest.xml`, ABI splits, signing, and `foss`/`play` flavors.
- `library/` — vendored `DragDropSwipeLazyColumn` submodule plus `libcore-stub` for IDE indexing.
- `launcher/` — Zig executable embedded into desktop installers (`launcher/src/main.zig`); on Linux
  it gets `setcap` for ambient capabilities before exec'ing the JVM, on Windows it embeds an
  admin-elevating manifest.
- `plugin/api/` — shared Android library that plugin APKs link against.
  `plugin/{hysteria2,juicity,mieru,naive,shadowquic}/` each wrap a Go/Rust submodule built into JNI
  libs by `buildScript/plugin/<name>.sh`. The matching Gradle module's `setupPlugin(...)` registers
  an `externalBuild` `Exec` task that hooks into `mergeJniLibFolders`.
- `buildSrc/src/main/kotlin/Helpers.kt` — Gradle conventions: `setupApp`, `setupAppCommon`,
  `setupKotlinCommon`, `setupPlugin`, `requireMetadata` (reads `husi.properties`),
  `requireLocalProperties`, APK renamer, and `writePlatformInfo` (used to emit `expect`/`actual`
  `PlatformInfo` per desktop target).
- `buildScript/` — bash scripts invoked via `./run`: `lib/{core,assets,source,update}.sh`,
  `plugin/<name>.sh`, `init/{env,env_ndk,version}.sh`, plus `rename.sh` for forking under a new
  package name.
- `release/{linux,macos,windows}/package.sh` — invoked by `make desktop_package_*` after the uber
  jar exists. Linux packaging uses `nfpm`.

## Compose UI (composeApp)

- DI via **Koin**. Boot at `fr.husi.di.initHusiKoin(repository)` (called from `Application.onCreate`
  on Android, `DesktopMain.main` on desktop). Koin modules: `commonUiModule()`,
  `commonNavigationModule`, plus `expect` `platformKoinModules()` / `platformRepositoryModule()`.
- `Repository` (`fr.husi.repository.Repository`) is the central platform-abstraction interface (
  filesystem dirs, string resources, service start/stop, `boxService`). `SagerRepository` is the
  Android impl (subclass `AndroidRepository`); `DesktopRepository` is the desktop impl. Always go
  through `Repository` instead of touching `Context` directly in `commonMain`.
- `database/SagerDatabase` — Room database with explicit `AutoMigration` chain plus custom
  `Migration` specs in `database/Migrations.kt`. Add a new entry every time you bump the schema or
  KSP will fail. Schemas land in `composeApp/schemas/`.
- `bg/` — service plumbing (subscription/route asset auto-update, `BackendState`,
  `GuardedProcessPool`, `DeepLinkDispatcher`). On Android this powers `ProxyService`/`VpnService`/
  `TileService` running in the `:bg` process; on desktop it's wired through `DesktopTaskScheduler`/
  `DesktopTaskRegistry`.
- `libcore/BoxServiceFactory.kt` is the `expect`/`actual` bridge that hands the platform a
  configured `Service` from the Go core.
- `ui/` is the Compose tree. Sub-packages mirror feature areas (`profile`, `dashboard`,
  `configuration`, `tools`); ViewModels follow Jetpack Lifecycle. Navigation uses Compose
  `navigation3`.
- Resources: shared in `composeApp/src/commonMain/composeResources/`, accessed via the generated
  `fr.husi.resources.Res` (`packageOfResClass = "fr.husi.resources"`).

## Android process model

The Android app runs in **two processes**: the main UI process and `:bg` (proxy service, tile,
BootReceiver, Tasker receiver, Room invalidation service). `Application.kt` branches on
`isMainProcess`/`isBgProcess` and only `isBgProcess` calls
`Libcore.initCore(shouldOperateFiles=true, ...)` and `boxService.start()`. Code added under `bg/`
should respect this split.

## Desktop entry point

`DesktopMain.kt` is a Clikt CLI: `-d/--dir`, `-l/--log-level`, `-m/--many` (allow multiple
instances), `-b/--background`, plus deep-link arguments. Uses Compose
`application { Window(...) Tray(...) }`. Single-instance enforcement and tray live here. Data dir
defaults to the platform config directory: Linux uses `$XDG_CONFIG_HOME/husi` when set, otherwise
`$HOME/.config/husi`; macOS uses `$HOME/Library/Application Support/husi`; Windows uses
`%APPDATA%\husi` or `%USERPROFILE%\AppData\Roaming\husi`. Override with `-d` for a custom data dir.

# Coding conventions

Canonical conventions live in `CONTRIBUTING.md` (English-only comments, path handling via
`File.resolve` and `fr.husi.ktx.invariantPathString`, no fully-qualified Kotlin imports, `forEach`
only at chain ends, `also` over `apply` when `this` is ambiguous, `make fmt_go` + `make test_go`
before committing Go). Read it before editing Kotlin or Go.

# Repo conventions to know

- Version metadata lives in `husi.properties` (`PACKAGE_NAME`, `VERSION_NAME`, `VERSION_CODE`, plus
  `<PLUGIN>_VERSION_*` per plugin). `requireMetadata()` reads it from Gradle.
- Signing: drop `KEYSTORE_PASS` / `ALIAS_NAME` / `ALIAS_PASS` into `local.properties` (or env), and
  replace `release.keystore`. A `FossRelease` build with no keystore aborts via `exitProcess(0)` in
  `setupAppCommon` — that's intentional, not a bug.
- `composeApp/executableSo/` is added as a JNI libs source dir for the Android app (used to bundle
  plugin executables alongside the host APK).
- The Go module uses `golangci-lint` with `GOOS=android`; many files have `//go:build` constraints
  for android vs desktop vs darwin/linux variants — when adding platform-specific code, follow the
  existing `*_android.go` / `*_darwin.go` / `*_linux.go` / `*_stub.go` split.
- `.gitignore` excludes `.claude/`, `.gemini/`, `.codex/`, generated `composeApp/libs/`, and
  submodule trees under `external/`. Build outputs (`build/`, `*.aar`, `*.jar` in libs, `*.tar.zst`
  assets) are also ignored.
