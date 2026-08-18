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
make libcore         # host desktop libcore jar — Gradle sync fails without it
```

Common targets:

| Target                                                      | Purpose                                                                          |
|-------------------------------------------------------------|----------------------------------------------------------------------------------|
| `make assets`                                               | Download geoip/geosite into `composeApp` resources (run once before first build) |
| `make libcore_android`                                      | Build Go core into `composeApp/libs/libcore.aar` (required for Android builds)   |
| `make libcore` / `make libcore_desktop DESKTOP_TARGETS=...` | One anja bind: fat `libcore-desktop-<os>-<arch>.jar` + sidecar `libhusicore.*` in `libcore/build/<os>_<arch>/` |
| `make core_desktop DESKTOP_TARGETS=...`                     | Build the Zig `husi-core` shim (source: `libcore/shim/`) into `libcore/build/<os>_<arch>/` (next to the sidecar) |
| `make apk` / `make apk_debug`                               | Assemble `androidApp:assembleFossRelease` / `Debug`                              |
| `make desktop` / `make desktop_release`                     | Run Compose desktop app via `gradlew :composeApp:run[Release]`                   |
| `make desktop_uberjar`                                      | Thin release jar (no libcore native); needs the `husi-core` shim **and** `libhusicore.*` next to the jar, or `husi-core` on `PATH` with the library next to that binary |
| `make desktop_package[_linux/_macos/_windows]`              | Native packages under `composeApp/build/compose/packages/`                       |
| `make launcher`                                             | Build the Zig native UI launcher from `launcher/` (used by Linux/macOS/Windows installers) |
| `make plugin PLUGIN=<name>`                                 | Assemble plugin APK; valid names: `hysteria2 juicity naive mieru shadowquic`     |
| `make aboutlibraries`                                       | Regenerate committed OSS license JSON (Gradle plugin is offline; run before release) |
| `make generate_option`                                      | Regenerate sing-box option mappings (boxoption); output piped through `$CLIP`    |
| `make proto`                                                | Re-vendor sing-box schema and regenerate Go gRPC stubs under `libcore/pb/` via `protoc`       |
| `make proto_install`                                        | Print instructions for installing `protoc`                                       |

The `BUILD_PLUGIN` env var (read in `settings.gradle.kts`) controls which plugin modules are
included; `BUILD_PLUGIN=none` excludes all plugins to speed up app-only builds (this is what the
Makefile does).

Cross-compiling desktop libcore: pass `JNI_INCLUDE=/path/to/jni` when JNI headers aren't
auto-detected; for Darwin targets on non-Darwin hosts also pass `DARWIN_SDK=/path/to/MacOSX.sdk` (
uses `zig cc`). Linux desktop builds use Zig for naive outbound, while the required prebuilt Cronet
library is downloaded through Go modules.

Desktop Gradle picks the libcore jar from `os.name`/`os.arch`; override with
`./gradlew -p composeApp run -PdesktopTarget=linux/amd64`. A missing jar fails Gradle sync (and any
build) immediately rather than silently falling back — even Android-only work in the IDE needs the
host desktop jar built first.

## Tests & lint

```
make test                # = test_gradle + test_go + test_no_go_core_binary + test_zig
make test_gradle         # ./gradlew :composeApp:allTests (JUnit5)
make test_go             # cd libcore && go test -v -count=1 ./...
make test_zig            # zig build test in both launcher/ and libcore/shim/
make lint_go             # golangci-lint for linux + android + windows
make fmt_go              # gofumpt + gofmt + gci (run before committing Go)
```

`lint_go` runs one pass per shipped GOOS, because each one only ever compiles its own half of the
platform-split files; the individual passes are `lint_go_linux`, `lint_go_android` and
`lint_go_windows` (the last needs `zig` on `PATH` as the cgo cross compiler). Darwin has no pass:
sing-tun's gvisor backend does not typecheck without a full Darwin SDK. Formatting is deliberately
left out of `libcore/.golangci.yml` — `make fmt_go` owns it, and golangci-lint's bundled `gci`
formats imports differently enough that both would fight over the tree. CI runs `make lint_go` as
the `lint-go` job in `.github/workflows/test.yml`.

Run a single Gradle test class: `./gradlew :composeApp:desktopTest --tests fr.husi.SomeTest`. Run a
single Go test: `cd libcore && go test -run TestName ./pkg/...`. Install Go tooling once with
`make fmt_go_install lint_go_install`.

# Architecture

## Module layout

- `libcore/` — Go module exposed to JVM. Built **two ways**:
    - Android via `gomobile` → `composeApp/libs/libcore.aar` (entry: `libcore.go`, with sibling Go
      files for tun, dns, ping, ruleset, plugin glue, etc.).
    - Desktop via `anja` (JNI bindings) → one c-shared library per target
      (`libhusicore.so` / `libhusicore.dylib` / `husicore.dll`) packed into
      `libcore-desktop-<os>-<arch>.jar` under gitignored `composeApp/libs/`, and also emitted as a
      plain sidecar under `libcore/build/<os>_<arch>/`. The jar stays fat for dev (`gradlew run` /
      tests); release uberjars keep only the target bucket under `natives/` and additionally drop
      `natives/<os>-<arch>/libhusicore.*` (libraries sharing that family — androidx sqlite's
      `libsqliteJni` — have no sidecar, so only libcore's own entry goes), loading the sidecar
      via `anja.natives.dir`
      (set early in `DesktopMain` when a packaged layout is found). The process host is a small
      Zig shim (`make core_desktop` → `husi-core`) that `dlopen`s the sibling library and calls
      `HusiCoreMain`; packaged installs ship **one** Go artifact (the library) plus that shim.
    - `libcore/coresvc/` hosts sing-box's `daemon.StartedService` plus husi's
      `CoreService` / `ApplicationService` / `AppService` over the app-private `api.sock` UDS
      (desktop: `husi-core session`, spawned by the UI when no daemon is installed, or
      `husi-core run` as the system daemon; Android: still in-process in `:bg`). Bound `Service` is
      `Start`/`Close` (socket + protect) and `StartService`/`StopService`/`HasInstance`
      (instance lifecycle). On Android, `StartService` takes a serialized
      `husi.v1.StartServiceRequest` plus a plugin working dir, and
      `PublishServiceEvent` fans husi lifecycle/speed/alerts to
      `SubscribeServiceEvents` subscribers.
    - `libcore/daemonhost/` is the desktop process host (session/daemon, peercred, service
      install of the shim+library pair). Entrypoint is `libcore/coreentry` (`//export HusiCoreMain`).
    - `libcore/shim/` is that host's `main()`: a standalone Zig project (**not** part of the Go
      module) producing the console `husi-core` executable, which `dlopen`s the `libhusicore.*`
      sitting next to it — absolute path only, never a search path — and calls `HusiCoreMain`.
      It is a separate project from `launcher/` on purpose: it links libc dynamically because
      `dlopen` needs it and because the anja library it loads is a glibc cgo artifact, whereas the
      launcher is static musl. Keeping them in one `build.zig` made the two exes collide in
      `zig-out/bin/` (same `{os}-{arch}` name, ABI not in the name) — do not merge them back.
    - `libcore/pluginpool/` is the shared Go process pool for plugin children
      (supervise, restart-or-fatal). Used by desktop `daemonhost` and by Android
      `:bg` via the bound `Service`.
    - `libcore/coreclient/` is the raw gRPC bridge (`Invoke`/`Stream`/`Probe` with a
      passthrough proto codec). Bound as `BridgeClient` for Kotlin.
    - `libcore/cmd/` holds `boxoption` (option codegen), `boxversion`, `licencecollect`,
      `ruleset_generate`. `libcore/plugin/` houses Go-side plugin support: outbound adapters (
      `http`, `juicity`, `trusttunnel`, `vless`), plus `mieruproto` (Mieru traffic-pattern
      protobuf), `raybridge` (*ray-compatible API shim), `plugindns` (plugin DNS conn plumbing), and
      `pluginoption` (option types/constants for hooked protocols).
    - `libcore/pb/` is generated by `make proto` and committed, so building the core never needs
      `protoc`.
- `proto/` — the gRPC contract between the UI and the core host, and the single source of truth for
  it. Two trees, generated the same way but owned differently:
    - `daemon/started_service.proto` is **vendored verbatim from the pinned sing-box** by
      `make proto` (only Java options are injected, which never reach the wire). It is the
      core-scoped surface — status, log, connections, groups, clash mode, OpenConnect — so husi
      stays wire compatible with the original sing-box daemon, and the Go side reuses
      `github.com/sagernet/sing-box/daemon` instead of regenerating it. Never edit it by hand.
    - `husi/v1/*.proto` is husi's own: what sing-box has no place for (plugin processes, pushed
      assets, husi's URL test knobs, schema generation).
  Both share one protoc include path for the `:proto` Gradle module, which generates the
  Kotlin/Java message classes into `build/proto/`; `composeApp` `commonMain` consumes them as
  `fr.husi.proto.v1` and `fr.husi.proto.daemon`.
  Adding a message means editing `husi/v1/`, then `make proto`.
  Kotlin talks to the host through `fr.husi.core.CoreClient` (typed suspend/Flow over
  `BridgeClient`), injected via Koin.
- `composeApp/` — Kotlin Multiplatform shared module (Android library + JVM `desktop` target).
  Source sets: `commonMain`, `androidMain`, `desktopMain`, plus `androidDebug` and tests. Package
  root is `fr.husi`.
- `androidApp/` — thin Android `application` that depends on `:composeApp`. Defines the
  `AndroidManifest.xml`, ABI splits, signing, and `foss`/`play` flavors.
- `library/` — vendored `DragDropSwipeLazyColumn` submodule.
- `launcher/` — Zig project for the UI launcher (`src/main.zig`) embedded into desktop installers.
  Windowed, statically linked against musl so one binary runs on any distro. It only locates a JVM
  and execs the jar; privileges live in the core host process (`husi-core service install` copies
  the shim+library pair into a protected path).
- `plugin/api/` — shared Android library that plugin APKs link against.
  `plugin/{hysteria2,juicity,mieru,naive,shadowquic}/` each wrap a Go/Rust submodule built into JNI
  libs by `buildScript/plugin/<name>.sh`. The matching Gradle module's `setupPlugin(...)` registers
  an `externalBuild` `Exec` task that hooks into `mergeJniLibFolders`.
- `buildSrc/src/main/kotlin/Helpers.kt` — Gradle conventions: `setupApp`, `setupAppCommon`,
  `setupKotlinCommon`, `setupPlugin`, `requireMetadata` (reads `husi.properties`),
  `requireLocalProperties`, APK renamer, and `writePlatformInfo` (used to emit `expect`/`actual`
  `PlatformInfo` per desktop target).
- `buildScript/` — bash scripts invoked via `./run`: `lib/{core,assets,source}.sh`,
  `plugin/<name>.sh`, `init/{env,env_ndk,version}.sh`, plus `rename.sh` for forking under a new
  package name.
- `release/{linux,macos,windows}/package.sh` — invoked by `make desktop_package_*` after the uber
  jar exists. Linux packaging uses `nfpm` for `deb`/`rpm`/`pacman`; the two root-free formats are
  `tarball` (ships `release/linux/desktop/install.sh`, which installs under `~/.local`) and
  `appimage` (bundles a `jlink` runtime, so it needs no system Java — `release/linux/appimage/`).
  Windows packaging Authenticode signs its payloads via `release/windows/codesign.sh`; its NSIS
  installer is per-user (`RequestExecutionLevel user`) and only elevates for the optional service.

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
  `DeepLinkDispatcher`). All plugin spawning — including standalone URL test — goes through
  the Go `pluginpool` in the core host. On Android this powers `ProxyService`/`VpnService`/
  `TileService` running in the `:bg` process; on desktop it's wired through
  `DesktopTaskScheduler`/`DesktopTaskRegistry`.
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

`:bg` serves the same gRPC surface as the desktop host (`coresvc` on
`<filesDir>/api.sock`). The UI process talks to it through `CoreClient` /
`BridgeClient` (state, speed and alerts via `SubscribeServiceEvents`; dashboard /
groups / logs / … via the shared daemon + husi services). The binder
(`SagerConnection` + a plain `Binder` on `ProxyService`/`VpnService`) is
**lifecycle-only** — `BIND_AUTO_CREATE` starts/keeps `:bg` alive; the old AIDL
data plane (`IServiceControl` / `IServiceObserver` / `SpeedDisplayData`) is
gone. All plugin processes (service start and standalone URL test) are spawned
by the Go `pluginpool` inside `:bg` (or the desktop session/daemon host).

## Desktop entry point

`DesktopMain.kt` is a Clikt CLI: `-d/--dir`, `-l/--log-level`, `-m/--many` (allow multiple
instances), `-b/--background`, plus deep-link arguments. Uses Compose
`application { Window(...) Tray(...) }`. Single-instance enforcement and tray live here. Data dir
defaults to the platform config directory: Linux uses `$XDG_CONFIG_HOME/husi` when set, otherwise
`$HOME/.config/husi`; macOS uses `$HOME/Library/Application Support/husi`; Windows uses
`%APPDATA%\husi` or `%USERPROFILE%\AppData\Roaming\husi`. Override with `-d` for a custom data dir.

# Coding conventions

Canonical conventions live in [CONTRIBUTING.md](./CONTRIBUTING.md). Read it first if you need to write code.

# Repo conventions to know

- Version metadata lives in `husi.properties` (`PACKAGE_NAME`, `VERSION_NAME`, `VERSION_CODE`, plus
  `<PLUGIN>_VERSION_*` per plugin). `requireMetadata()` reads it from Gradle.
- Android signing: drop `KEYSTORE_PASS` / `ALIAS_NAME` / `ALIAS_PASS` into `local.properties` (or
  env), and replace `release.keystore`. A `FossRelease` build with no keystore aborts via
  `exitProcess(0)` in `setupAppCommon` — that's intentional, not a bug.
- Windows signing (`release/windows/codesign.sh`, sourced by `release/windows/package.sh`): the
  launcher, `husi-core.exe`, `husicore.dll` and the NSIS installer are Authenticode signed with
  **one self-signed certificate** via `osslsigncode` (CI cross-compiles on Ubuntu, so `signtool` is
  not an option). The point is not SmartScreen — it is that `daemonhost.VerifyCorePairSignature`
  binds `husi-core.exe` to the `husicore.dll` it loads, following sing-box's `boxdd`. Configure with
  `WINDOWS_SIGNING_P12` (or `WINDOWS_SIGNING_P12_BASE64` for CI) plus
  `WINDOWS_SIGNING_P12_PASSWORD`; signing is on by default, and building without a certificate has
  to be asked for with `make desktop_package_windows … WINDOWS_NO_SIGN=1`. Mint the certificate
  with a long life — `validateUntrustedSelfSignedCertificate` compares `time.Now()` against the
  **certificate** validity window, not the countersignature, so an expired certificate starts
  failing installed daemons regardless of the PE timestamp:

  ```
  openssl req -x509 -newkey rsa:4096 -keyout husi-signing-key.pem -out husi-signing-cert.pem \
      -days 3650 -nodes -subj "/CN=husi" \
      -addext "extendedKeyUsage=codeSigning" -addext "basicConstraints=critical,CA:true"
  openssl pkcs12 -export -inkey husi-signing-key.pem -in husi-signing-cert.pem -out husi-signing.p12
  ```

  The public half is committed as `release/windows/husi-signing-cert.pem` so downloads can be
  checked against it — see `release/windows/README.md` for the fingerprints. Nothing in the code
  reads that file: `VerifyCorePairSignature` compares the shim against its own library rather than
  pinning a certificate, so the published fingerprint is for humans, not for the daemon. Keep the
  private key out of the repository.
- `composeApp/executableSo/` is added as a JNI libs source dir for the Android app (used to bundle
  plugin executables alongside the host APK).
- `make aboutlibraries` (`aboutlibraries_go` + `aboutlibraries_android` +
  `aboutlibraries_desktop`) rewrites the committed OSS metadata at
  `composeApp/src/{android,desktop}Main/composeResources/files/aboutlibraries.json`. The UI
  loads those files at runtime; regular `make apk` / `make desktop` do not regenerate them.
  The AboutLibraries Gradle plugin runs with `offlineMode = true` and will not download SPDX
  license texts (or any other remote license data). Full texts are vendored as
  `composeApp/src/commonMain/aboutlibraries/licenses/<SPDX-id>.json` (shared) and
  `composeApp/src/desktopMain/aboutlibraries/licenses/` (desktop-only, currently the LGPLs).
  `hash` / `spdxId` must be the SPDX id that library presets reference (e.g.
  `GPL-3.0-or-later`). Go module presets from `libcore/cmd/licencecollect` only carry those
  ids, no body; without a matching file here, the next export ships empty `content` and the
  OSS screen falls back to opening the license URL. Add a new JSON when a dependency
  introduces a license that is not already vendored and is not already present in a Maven
  POM. `aboutlibraries_go` still talks to pkgsite — only the Gradle half is offline. Do not
  run `exportLibraryDefinitions` and `exportLibraryDefinitionsDesktop` in one Gradle
  invocation: `configPath` is chosen from the start-parameter task names, so both tasks would
  share the desktop merge output.
- `make proto` re-vendors the sing-box schema and regenerates the Go stubs for `husi/v1` only via
  `protoc` — a second copy of `daemon/started_service.proto` in the binary would panic the
  protobuf registry. The protoc plugins are pinned by the `tool` block in `libcore/go.mod` rather
  than by whatever happens to be installed globally.
- `.gitignore` excludes `.claude/`, `.codex/`, `.agents`, generated `composeApp/libs/`, and
  submodule trees under `external/`. Build outputs (`build/`, `*.aar`, `*.jar` in libs, `*.tar.zst`
  assets) are also ignored.
