# AGENTS.md

Read [CONTRIBUTING.md](./CONTRIBUTING.md) before writing code.

## Tools & commands

First-time setup — run once after a fresh clone or when submodules/assets are missing; skip if `composeApp/libs/` already contains the host desktop `libcore-desktop-*.jar`:

```
./run lib source     # git submodule update --init --recursive
make assets          # geoip/geosite into composeApp resources
make libcore         # host desktop libcore jar — Gradle sync fails without it
```

Common targets:

| Target | Purpose |
|---|---|
| `make assets` | Download geoip/geosite (once before first build) |
| `make libcore_android` | Go core into `composeApp/libs/libcore.aar` (required for Android) |
| `make libcore` / `make libcore_desktop DESKTOP_TARGETS=...` | Desktop `libcore-desktop-<os>-<arch>.jar` + sidecar `libhusicore.*` |
| `make core_desktop DESKTOP_TARGETS=...` | Zig `husi-core` shim into `libcore/build/<os>_<arch>/` |
| `make apk` / `make apk_debug` | Android APK (foss release/debug) |
| `make desktop` / `make desktop_release` | Run Compose desktop app |
| `make desktop_uberjar` | Thin release jar (needs `husi-core` + `libhusicore.*` beside it) |
| `make desktop_package[_linux/_macos/_windows]` | Native packages |
| `make desktop_package_windows_jbr DESKTOP_TARGET=...` | Windows zip/NSIS plus a -jbr pair with jlink JetBrains Runtime |
| `make launcher` | Zig native UI launcher from `launcher/` |
| `make plugin PLUGIN=<name>` | Plugin APK; valid: `hysteria2 juicity naive mieru shadowquic` |
| `make icon` | Regenerate all icons from `art/` (needs `rsvg-convert` + ImageMagick) |
| `make aboutlibraries` | Regenerate OSS license JSON |
| `make generate_option` | Regenerate sing-box option mappings; output piped through `$CLIP` |
| `make proto` | Re-vendor sing-box schema and regenerate Go gRPC stubs |
| `make test` | `test_gradle` + `test_go` + `test_no_go_core_binary` + `test_zig` |
| `make test_gradle` | `./gradlew :composeApp:allTests` (JUnit5) |
| `make test_go` | `cd libcore && go test -v -count=1 ./...` |
| `make test_zig` | zig build test in both `launcher/` and `libcore/shim/` |
| `make lint_go` | golangci-lint for linux + android + windows |
| `make fmt_go` | golangci-lint fmt |

Run a single Gradle test class: `./gradlew :composeApp:desktopTest --tests fr.husi.SomeTest`.
Run a single Go test: `cd libcore && go test -run TestName ./pkg/...`.
Install Go tooling: `make lint_go_install`.

`lint_go` runs one pass per shipped GOOS (`lint_go_linux`, `lint_go_android`, `lint_go_windows`); no Darwin pass because sing-tun's gvisor backend does not typecheck without a full Darwin SDK. `lint_go_windows` needs `zig` on PATH as the cgo cross compiler.

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

### Zig projects

`launcher/` and `libcore/shim/` are separate Zig projects on purpose: the launcher links musl statically; the shim links libc dynamically (it needs `dlopen` and loads a glibc cgo artifact). Do not merge them.

### Windows signing

`daemonhost.VerifyCorePairSignature` binds `husi-core.exe` to the `husicore.dll` it loads. The certificate's validity window matters at runtime — `validateUntrustedSelfSignedCertificate` compares `time.Now()` against the certificate, not the countersignature. An expired cert starts failing installed daemons. Building without a certificate requires explicit `WINDOWS_NO_SIGN=1`.

## Project-specific context

### Desktop Gradle requires the host libcore jar

Desktop Gradle picks the libcore jar from `os.name`/`os.arch`. A missing jar fails Gradle sync immediately — even Android-only IDE work needs the host desktop jar built first (`make libcore`).

### Android two-process model

The app runs in two processes: UI and `:bg`. Only `:bg` calls `Libcore.initCore(shouldOperateFiles=true, ...)` and `boxService.start()`. The binder (`SagerConnection`) is lifecycle-only — `BIND_AUTO_CREATE` starts/keeps `:bg` alive; the data plane is gRPC over `<filesDir>/api.sock`.

### Desktop core host

Each UI instance needs its own host directory. The single-instance lock holder uses `<dataDir>/core/`; a `--many` instance gets `<dataDir>/core/instances/<pid>/` (deleted on exit, stale dirs pruned). `coresvc.Host.Start` refuses a socket another host still answers on rather than unlinking it.

`DaemonService.AttachClient` is a lease: the daemon stops the service once the last lease ends (after a grace period). A service the daemon restored on boot keeps running until a client attaches and leaves.

### Android signing

A `FossRelease` build with no keystore calls `exitProcess(0)` in `setupAppCommon` — that is intentional, not a bug.

### Cross-compiling desktop libcore

Pass `JNI_INCLUDE=/path/to/jni` when JNI headers aren't auto-detected; for Darwin targets on non-Darwin hosts also pass `DARWIN_SDK=/path/to/MacOSX.sdk`.

