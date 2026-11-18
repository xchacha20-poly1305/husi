# Contributing to Husi

Welcome, and thank you for contributing! 🎉

## Ways to Contribute

* **Localization:** Help translate or fix translations on [Weblate](https://hosted.weblate.org/engage/husi/).
* **Code & Documentation:** Before writing code, read the [Code Style Guide](./docs/code-style.md).

## AI Policy

* **Code & Documentation:** AI-generated content and code do not need to be labeled or attributed, **but you are responsible for verifying that everything you submit is accurate and factual**. Do not submit unverified or fabricated claims, test results, or code.
* **Translations:** Translations must be done **manually**. You may use AI to understand what a string means in context or to reference translations in other languages, but do **not** blindly generate translations with AI.

## Developer Documentation

* [Code Style Guide](./docs/code-style.md) — readability principles, naming, comments, cross-platform path handling (`invariantPathString()`), and Go / Kotlin conventions.
* [Build & Packaging Guide](./docs/build.md) — reproducible builds, cross-compilation, native packages, JBR bundling, and code signing.
* [Project Skills](./.agents/skills/README.md) — Compose UI (`husi-topbar`, `husi-actions-dropdown-menu`, `husi-preference-ui`) and testing (`husi-testing`) conventions.
* Protocol specifications:
  * [Socket Protect Path Protocol (Unix)](./docs/protect.md)
  * [Socket Protect Path Protocol for Windows](./docs/protect_windows.md)
  * [Daemon Host (`husi-core run` & `daemon.json`)](./docs/daemon.md)

## First-Time Setup

Run once after a fresh clone or when submodules/assets are missing:

```shell
./run lib source     # git submodule update --init --recursive
make assets          # download geoip/geosite into composeApp resources
make core_desktop    # build host husi-core binary (the desktop app runs it as its core process)
```

## Common Commands

Always use the root `Makefile` targets when one covers your task instead of assembling raw `./gradlew`, `go`, or `golangci-lint` invocations.

| Target | Purpose |
|---|---|
| `make desktop` | Run Compose desktop app (`make desktop_release` for release build) |
| `make apk_debug` | Build Android debug APK (`make apk` for release) |
| `make test` | Run all tests (`test_gradle` + `test_web` + `test_go` + `test_zig`) |
| `make test_gradle` | `./gradlew :composeApp:desktopTest` (JUnit5) |
| `make test_web` | `./gradlew :composeApp:wasmJsBrowserTest` (`commonTest` + `wasmJsTest` in headless Chrome) |
| `make test_go` | `cd libcore && go test -v -count=1 -tags with_quic,badlinkname -ldflags=-checklinkname=0 ./...` |
| `make test_zig` | `zig build test` in `launcher/` |
| `make lint_go` | `golangci-lint` across Linux (`CGO_ENABLED=0`), Android, Windows (`CGO_ENABLED=0`), and Darwin |
| `make fmt_go` | Format Go code with `golangci-lint fmt` |
| `make lint_android` | Android lint (`NewApi` only) over `commonMain` + `jvmMain` + `androidMain` |
| `make proto` | Re-vendor sing-box / Mieru schemas and regenerate Go gRPC stubs |
| `make icon` | Regenerate all icons from `art/` (requires `rsvg-convert` + ImageMagick) |
| `make aboutlibraries` | Regenerate OSS license JSON across all platforms |
| `make generate_option` | Regenerate sing-box option mappings (piped through `$CLIP`) |

Useful single-target commands:

```shell
# Run a single Gradle test class
./gradlew :composeApp:desktopTest --tests fr.husi.SomeTest

# Run a single Go test
cd libcore && go test -tags with_quic,badlinkname -ldflags=-checklinkname=0 -run TestName ./pkg/...
```

### Simulated backend (`husi-core demo`)

For UI and dashboard development without starting a real proxy, loading user configuration, or requiring administrator privileges:

```shell
make core_desktop
# Linux amd64 example; use the matching libcore/build/<os>_<arch>/ directory.
./libcore/build/linux_amd64/husi-core demo
# Optional LAN access and Bearer authentication:
./libcore/build/linux_amd64/husi-core demo --listen 0.0.0.0:9090 --secret demo-secret
```

The default listen address is `http://127.0.0.1:9090` with no secret.

## Pre-Submit Checklist

* **Go changes:**

  ```shell
  make fmt_go
  make lint_go
  make test_go
  ```

* **Kotlin / Compose changes:**

  ```shell
  make test_gradle
  make lint_android
  # Also required when touching commonMain or wasmJsMain:
  make test_web
  ```

* **Launcher (`launcher/`) changes:**

  ```shell
  make test_zig
  ```

## Architecture & Workflow Overview

### Kotlin source sets

* `commonMain` holds only what the native app and the web dashboard both use: shared dashboard and remote-tool UI and presentation models. It must not reference `fr.husi.libcore`, which only the Android AAR provides.
* `jvmMain` is shared by Android and Desktop and owns native gRPC sessions, Room/DataStore, local configuration, and native service integration.
* `skikoMain` is shared by Desktop and Wasm and owns Skia image and pointer implementations.
* `wasmJsMain` owns the web dashboard (RPC session, presenters, screen bindings, Koin module, routes, browser JS interop, entrypoint, and static packaging).

The source set is the platform boundary, so no package is named after a platform. Web code lives in the same feature package as its `jvmMain` counterpart (`ui.dashboard`, `ui.tools`, `tailscale`, etc.).

Tests follow the same split: pure and portable Wire-message tests in `commonTest`, JVM service tests in `jvmTest`, and web dashboard tests in `wasmJsTest`.

### Process & RPC boundary

* **Desktop has no Go in the JVM:** There is no `libcore` jar. Anything needing Go (sing-box internals, ICMP sockets, embedded root bundles) is an `ApplicationService` RPC served by the `husi-core` process. Plain logic lives in Kotlin `commonMain`, shared with Android.
* **Android two-process model:** The app runs in UI and `:bg` processes. Only `:bg` loads `libcore` (`Seq.setContext`, `Libcore.initCore`, `boxService.start()`); the UI process must not touch any `fr.husi.libcore` class and talks to Go via `CoreClient` over `<filesDir>/api.sock`. `SagerConnection` is lifecycle-only; WorkManager background work without an activity wraps core calls in `withBackgroundProcess`. App HTTP and WebDAV run through OkHttp in the calling process and use the local mixed HTTP proxy when connected. HTTPS reads the core-exported `plugin-ca.pem`. Only the local IPC endpoint accepts age decryption calls.
* **`gomobile` export surface:** Package `libcore` is what `gomobile` binds. Avoid exporting interfaces with unbindable types (proto slices, func values) or dead Go-only structs there; put shared Go-side types in a sibling package.

### Proto workflow

* `daemon/started_service.proto` is vendored from pinned sing-box by `make proto` — never edit it by hand. `KEEP_STARTED_SERVICE_RPCS` in `buildScript/proto.sh` is the allowlist of upstream RPCs.
* `mieru/traffic_pattern.proto` is vendored from the pinned Mieru submodule by `buildScript/mieru_proto.py` (run by `make proto`) — never edit it by hand.
* Adding a husi message means editing `husi/v1/`, then running `make proto`.
* Kotlin messages and RPC clients are generated by Wire in `:proto` commonMain for Android, Desktop and Wasm. Android and Desktop use Wire with OkHttp, and Wasm uses the gRPC-Web bridge in `composeApp/browser`. Kotlin generation needs no protoc. Go stubs are regenerated for `husi/v1` only.

### Room database migrations

`database/SagerDatabase` uses an explicit `AutoMigration` chain plus custom `Migration` specs in `database/Migrations.kt`. Add a new entry whenever you bump the schema or KSP will fail.

### Icons & `aboutlibraries`

* `art/icon.svg` (full mark) and `art/icon-small.svg` (simplified variant for `<= 96px`) must not be merged, and `buildScript/icon.py` accepts only `<path>` elements. Edit the SVGs and run `make icon` rather than hand-editing generated icon files.
* Run `exportLibraryDefinitions`, `exportLibraryDefinitionsDesktop`, and `exportLibraryDefinitionsWasmJs` via separate `make aboutlibraries_*` invocations (`aboutlibraries_go` requires `go mod download` in `libcore` first).
