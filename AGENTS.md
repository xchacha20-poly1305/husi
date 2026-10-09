# AGENTS.md

Read [CONTRIBUTING.md](./CONTRIBUTING.md) before writing code.

## Tools & commands

First-time setup — run once after a fresh clone or when submodules/assets are missing:

```
./run lib source     # git submodule update --init --recursive
make assets          # geoip/geosite into composeApp resources
make core_desktop    # host husi-core binary — the desktop app runs it as its core process
```

Common targets:

| Target | Purpose |
|---|---|
| `make assets` | Download geoip/geosite (once before first build) |
| `make libcore_android` | Go core into `composeApp/libs/libcore.aar` (required for Android) |
| `make core_desktop [DESKTOP_TARGETS=...]` | Go `husi-core` binary into `libcore/build/<os>_<arch>/` (default: host) |
| `make apk` / `make apk_debug` | Android APK (foss release/debug) |
| `make desktop` / `make desktop_release` | Run Compose desktop app |
| `make desktop_uberjar` | Release jar (needs `husi-core` beside it or on `PATH`) |
| `make desktop_package[_linux/_macos/_windows]` | Native packages |
| `make desktop_package_windows_jbr DESKTOP_TARGET=...` | Windows zip/NSIS plus a -jbr pair with jlink JetBrains Runtime |
| `make launcher` | Zig native UI launcher from `launcher/` |
| `make plugin PLUGIN=<name>` | Plugin APK; valid: `hysteria2 juicity naive mieru shadowquic` |
| `make icon` | Regenerate all icons from `art/` (needs `rsvg-convert` + ImageMagick) |
| `make aboutlibraries` | Regenerate OSS license JSON |
| `make generate_option` | Regenerate sing-box option mappings; output piped through `$CLIP` |
| `make proto` | Re-vendor sing-box schema and regenerate Go gRPC stubs |
| `make test` | `test_gradle` + `test_go` + `test_zig` |
| `make test_gradle` | `./gradlew :composeApp:allTests` (JUnit5) |
| `make test_go` | `cd libcore && go test -v -count=1 -tags with_quic,badlinkname -ldflags=-checklinkname=0 ./...` |
| `make test_zig` | zig build test in `launcher/` |
| `make lint_go` | golangci-lint for linux + android + windows + darwin |
| `make fmt_go` | golangci-lint fmt |
| `make lint_android` | Android lint (NewApi only) over composeApp's `commonMain` + `androidMain` |

Run a single Gradle test class: `./gradlew :composeApp:desktopTest --tests fr.husi.SomeTest`.
Run a single Go test: `cd libcore && go test -tags with_quic,badlinkname -ldflags=-checklinkname=0 -run TestName ./pkg/...`.
Install Go tooling: `make lint_go_install`.

`lint_go` runs one pass per shipped GOOS (`lint_go_linux`, `lint_go_android`, `lint_go_windows`, `lint_go_darwin`); the Linux and Windows passes run with `CGO_ENABLED=0`, as those builds ship.

`BUILD_PLUGIN=none` (what the Makefile sets for app-only builds) excludes all plugin modules to speed up Gradle.

## Workflow requirements

### gomobile export surface

Package `libcore` is what gomobile binds. An exported interface there whose methods gomobile cannot bind (proto slices, func values) fails `make libcore_android` with "proxy … does not implement", and an exported struct only adds a dead Java class. Nothing Kotlin does not call belongs in `libcore`'s exported surface; put shared Go-side types in a sibling package.

### Proto workflow

- `daemon/started_service.proto` is vendored from the pinned sing-box by `make proto` — never edit it by hand.
- `KEEP_STARTED_SERVICE_RPCS` in `buildScript/proto.sh` is the allowlist of upstream RPCs. Using a new upstream RPC means adding it to that list and re-running `make proto`; an allowlisted RPC that upstream renamed or dropped fails the run.
- Adding a husi message means editing `husi/v1/`, then `make proto`.
- Go stubs are regenerated for `husi/v1` only — a second copy of `daemon/started_service.proto` in the binary would panic the protobuf registry.

### Room database migrations

`database/SagerDatabase` uses an explicit `AutoMigration` chain plus custom `Migration` specs in `database/Migrations.kt`. Add a new entry every time you bump the schema or KSP will fail.

### aboutlibraries

Do not run `exportLibraryDefinitions` and `exportLibraryDefinitionsDesktop` in one Gradle invocation: `configPath` is chosen from the start-parameter task names, so both tasks would share the desktop merge output. `aboutlibraries_go` scans the local module cache — run `go mod download` in `libcore` first.

### Icon pipeline

- `art/icon.svg` is the full mark; `art/icon-small.svg` is the simplified variant (triangle + outer bowl) used at or below 96px — they serve different size ranges and must not be merged.
- `buildScript/icon.py` rejects anything but `<path>` elements in the SVG.
- Never hand-edit generated icon files (Android `<vector>` XML included) — edit the SVG and re-run `make icon`.

### Desktop has no Go in the JVM

The desktop UI loads no Go code: there is no libcore jar. Anything that needs Go — sing-box internals, ICMP sockets, the embedded root bundles — is an `ApplicationService` RPC served by the `husi-core` process; plain logic lives in Kotlin `commonMain`, shared with Android. `commonMain` must not reference `fr.husi.libcore`, which only the Android aar provides.

### Windows signing

Windows payloads (launcher, `husi-core.exe`, installer) are Authenticode signed with the self-signed certificate in `release/windows/`. Nothing checks it at runtime. Building without a certificate requires explicit `WINDOWS_NO_SIGN=1`.

## Project-specific context

### Desktop core binary

`gradlew run` looks for `husi-core` under `libcore/build/<os>_<arch>/` (relative to the launch directory), then next to a packaged launcher, then on `PATH`. Build it with `make core_desktop`.

### Android two-process model

The app runs in two processes: UI and `:bg`. Only `:bg` loads libcore (`Seq.setContext`, `Libcore.initCore`, `boxService.start()`); the UI process must not touch any `fr.husi.libcore` class, and reaches Go the way the desktop does, through `CoreClient`. The binder (`SagerConnection`) is lifecycle-only — `BIND_AUTO_CREATE` starts/keeps `:bg` alive; the data plane is gRPC over `<filesDir>/api.sock`. Main-process work that no activity covers (WorkManager updates) wraps its core calls in `withBackgroundProcess`, as `BackgroundProcessHttpFetcher` does.

### Desktop core host

Each UI instance needs its own host directory. The single-instance lock holder uses `<dataDir>/core/`; a `--many` instance gets `<dataDir>/core/instances/<pid>/` (deleted on exit, stale dirs pruned). `coresvc.Host.Start` refuses a socket another host still answers on rather than unlinking it.

`DaemonService.AttachClient` is a lease: the daemon stops the service once the last lease ends (after a grace period). A service the daemon restored on boot keeps running until a client attaches and leaves.

### Android signing

A `FossRelease` build with no keystore calls `exitProcess(0)` in `setupAppCommon` — that is intentional, not a bug.

### Cross-compiling husi-core

Linux and Windows `husi-core` builds use no cgo: Cronet is loaded through purego from `libcronet.so` / `libcronet.dll`, which `build.sh` copies next to the binary. Anything that moves `husi-core` — packaging, `daemonhost`'s `service install` copy — must move that library with it (`coreLibraries` in `daemonhost`).

Darwin keeps cgo (Cronet has no purego build there, and sing-box reads the system certificate store and DNS configuration through cgo). It builds natively on macOS with Xcode; on non-Darwin hosts it needs `zig` and `DARWIN_SDK=/path/to/MacOSX.sdk`.

