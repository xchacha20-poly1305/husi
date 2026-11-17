# Building and Packaging Husi

On Linux, you can build Husi reproducibly for release versions. Use the same JDK and NDK versions listed below, and match the Go version pinned in [buildScript/init/version.sh](../buildScript/init/version.sh).

## Get the Source Code

```shell
git clone https://github.com/xchacha20-poly1305/husi.git --depth=1
cd husi/
./run lib source # Initialize and update git submodules
```

## `libcore`

### Environment

* Go runtime patches (for older Go versions):

  <details>
    <summary>Unfold</summary>

  * 1.22.5: Apply [cgo_go1225.diff](../libcore/patches/cgo_go1225.diff) to `${GOROOT}/src/runtime/cgocall.go`.
  * 1.23.0–1.23.3: Apply [this patch](https://github.com/golang/go/commit/76a8409eb81eda553363783dcdd9d6224368ae0e.patch) to `${GOROOT}` (`make patch_go1230`).
  * 1.23.4: Apply [this patch](https://github.com/golang/go/commit/59b7d40774b29bd1da1aa624f13233111aff4ad2.patch) to `$(GOROOT)` (`make patch_go1234`).

  </details>

* OpenJDK 21 (or newer).

### Android

```shell
make libcore_android
```

This generates `composeApp/libs/libcore.aar`. The Android build installs the `gomobile` and `gobind` versions pinned in `libcore/go.mod`.

### Desktop

Build the `husi-core` binary for your host platform:

```shell
make core_desktop
```

This generates `libcore/build/<host-platform>_<host-arch>/husi-core`, plus `libcronet.so` or `libcronet.dll` beside it on Linux and Windows. The desktop app runs the core as this separate process; the JVM side loads no Go code.

Or build for specific targets:

```shell
make core_desktop DESKTOP_TARGETS=linux/amd64,darwin/arm64
```

For Darwin targets on non-Darwin hosts, pass the macOS SDK explicitly:

```shell
make core_desktop DESKTOP_TARGETS=darwin/arm64 DARWIN_SDK=/path/to/MacOSX.sdk
```

Common desktop targets:

* `linux/amd64`
* `linux/arm64`
* `darwin/arm64`
* `windows/amd64`

`darwin/amd64` is no longer supported and `windows/arm64` is temporarily not shipped: `androidx.sqlite:sqlite-bundled` has no binary for them, so the app cannot open its database there. See [issuetracker 495864182](https://issuetracker.google.com/issues/495864182) for `osx_x64` and [issuetracker 426464784](https://issuetracker.google.com/issues/426464784) for `windows_arm64`.

Linux and Windows targets build without cgo, so they need no C toolchain: `husi-core` loads the prebuilt Cronet shared library (glibc 2.17 or newer on Linux) from its own directory at runtime, and `make core_desktop` copies it there from the Go module cache. Keep the two files together. Darwin targets link Cronet through cgo: they use Xcode on macOS, or Zig plus an explicit macOS SDK path via `DARWIN_SDK` or `--darwinsdk` on other hosts.

Desktop Gradle builds pick the target from the current `os.name` and `os.arch`. You can override it explicitly:

```shell
./gradlew -p composeApp run -PdesktopTarget=linux/amd64
```

`gradlew run` finds `husi-core` under `libcore/build/<os>_<arch>/`, next to a packaged launcher, or on `PATH`.

If you run `libcore/build.sh` directly:

* `--android`: build Android only
* `--desktop`: build the desktop `husi-core` binary only (default target: `host`)
* `--android --desktop`: build both
* `--darwinsdk <path>`: pass a macOS SDK path for Darwin desktop targets on non-Darwin hosts
* no platform args: defaults to Android only

## Android APK

### Environment

* JDK 21
* NDK `30.0.16248370`

If `$ANDROID_HOME` and `$ANDROID_NDK_HOME` are not set, source `buildScript/init/env_ndk.sh`:

```shell
source buildScript/init/env_ndk.sh
```

Write the SDK path to `local.properties`:

```shell
echo "sdk.dir=${ANDROID_HOME}" > local.properties
```

Signing preparation (optional; recommended for release builds): replace `release.keystore` with your own keystore and set credentials in `local.properties` or environment variables:

```shell
echo "KEYSTORE_PASS=" >> local.properties
echo "ALIAS_NAME=" >> local.properties
echo "ALIAS_PASS=" >> local.properties
```

> [!NOTE]
> A `FossRelease` build without a keystore intentionally calls `exitProcess(0)` in `setupAppCommon`.

Download geo resource files:

```shell
make assets
```

Generate open source license metadata (written to `composeApp/src/androidMain/composeResources/files/aboutlibraries.json`):

```shell
make aboutlibraries_go
make aboutlibraries_android
```

Compile the release APK:

```shell
make apk
```

The APK will be located under `androidApp/build/outputs/apk`. For a debug build, run `make apk_debug`.

## Desktop

### Environment

* JDK 21
* Zig 0.17

Run the desktop application from source:

```shell
make desktop
```

Generate desktop open source license metadata (written to `composeApp/src/desktopMain/composeResources/files/aboutlibraries.json`):

```shell
make aboutlibraries_go
make aboutlibraries_desktop
```

Build the standalone Zig native launcher (`launcher/`, targeting static musl on Linux with no external C toolchain needed):

```shell
make launcher
```

Build a ProGuard-shrunk **uber JAR** that runs on system Java (output in `composeApp/build/compose/jars/`):

```shell
make desktop_uberjar
```

Run it with system Java (JDK/JRE 21+); ensure `husi-core` is beside it or on `PATH`:

```shell
java -jar composeApp/build/compose/jars/fr.husi-<platform>-<arch>-<version>.jar
```

Package a distributable for the current host OS:

```shell
make desktop_package
```

This dispatches to the host-native packaging flow:

* Linux: `make desktop_package_linux`
* macOS: `make desktop_package_macos`
* Windows/MSYS: `make desktop_package_windows`

### Linux packaging

Build Linux packages (runs `desktop_uberjar`, `make launcher`, `make core_desktop`, then `release/linux/package.sh`):

```shell
make desktop_package_linux
```

Package timestamps are derived from git tag `v<VERSION_NAME>` in `husi.properties`, not local build time.
Default output directory: `composeApp/build/compose/packages/linux/`.

Select specific package formats with `LINUX_PACKAGE_FORMATS` (default `deb,rpm,pacman`):

```shell
make desktop_package_linux LINUX_PACKAGE_FORMATS=deb,pacman
```

You can preflight required tooling without producing packages:

```shell
./release/linux/package.sh --check-tools --formats deb,rpm,pacman
```

Supported formats and tool requirements:

* `deb`, `rpm`, `pacman`: built with `nfpm`; requires `zig`, `git`, `nfpm`.
* `tarball`: relocatable `.tar.zst` subtree holding the jar, launcher, `husi-core`, and `install.sh`; requires `tar`, `zstd`.
* `appimage`: single self-contained file bundling a `jlink`-linked JetBrains Runtime (module list in [release/desktop/jre-modules.sh](../release/desktop/jre-modules.sh)). Requires `jlink`, `appimagetool`, and an `objcopy` for the target architecture:

  ```shell
  make desktop_package_linux DESKTOP_TARGET=linux/arm64 \
      LINUX_PACKAGE_FORMATS=appimage \
      APPIMAGE_RUNTIME=/path/to/runtime-aarch64
  # or with modules you already have: JBR_JMODS=/path/to/jbrsdk/jmods
  ```

* `flatpak`: bundles the same Java runtime, launcher, jar, core, and Cronet library. Install `flatpak`, `jlink`, the target architecture's runtimes (pinned with CI in [release/linux/flatpak/config.sh](../release/linux/flatpak/config.sh)), and the target `objcopy` when cross-compiling.

### macOS packaging

Build macOS `.dmg` packages with system Java runtime dependency:

```shell
make desktop_package_macos
```

This builds the uber jar, Zig launcher, and `husi-core`, then packages them into `Husi.app` and a `.dmg` image under `composeApp/build/compose/packages/macos/`.

The app bundle icon is a checked-in static asset generated from `composeApp/src/commonMain/composeResources/drawable/ic_launcher_foreground.xml`.

On macOS hosts, packaging uses `hdiutil`. On Linux hosts, it falls back to `xorrisofs` and emits an ISO9660/HFS hybrid `.dmg`. `DESKTOP_TARGET` is required on Linux hosts:

```shell
make desktop_package_macos DESKTOP_TARGET=darwin/arm64
```

Required host tools:

* Common: `zig`, `git`
* macOS host: `hdiutil`
* Linux fallback: `xorrisofs`

### Windows packaging

Build Windows portable `.zip` and `.msi` installer packages (output in `composeApp/build/compose/packages/windows/`):

```shell
make desktop_package_windows DESKTOP_TARGET=windows/amd64
```

Required host tools:

* `zig`
* `git`
* `python3`
* `nfpm` v2.49.0 or newer
* `osslsigncode` (unless building with `WINDOWS_NO_SIGN=1`)

Outputs:

* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>.zip`
* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>.msi`

#### Packages with a bundled runtime

To build both the thin packages above and a `-jbr` pair bundling a `jlink`-trimmed [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime):

```shell
make desktop_package_windows_jbr DESKTOP_TARGET=windows/amd64
```

Outputs include:

* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>-jbr.zip`
* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>-jbr.msi`

The JBR modules are fetched into `build/jbr/` on demand, pinned by `JBR_RELEASE` in [buildScript/init/version.sh](../buildScript/init/version.sh). You can fetch them ahead of time or point packaging at an existing copy:

```shell
./run lib jbr windows/amd64
make desktop_package_windows_jbr DESKTOP_TARGET=windows/amd64 JBR_JMODS=/path/to/jbrsdk/jmods
```

Building `-jbr` packages requires `jlink` on `PATH` with a feature version at least as new as `JBR_RELEASE` (CI builds with the same JetBrains Runtime; `./run lib jbr <host> --sdk` prints a ready `JAVA_HOME`). Because `jlink` links an image for the target platform its modules belong to, Windows runtimes can be linked on Linux release runners.

#### Windows code signing

Official Windows payloads (the launcher, `husi-core.exe`, `libcronet.dll`, and the installer) are Authenticode signed with the project's self-signed certificate published at [release/windows/husi-signing-cert.pem](../release/windows/husi-signing-cert.pem). See [release/windows/README.md](../release/windows/README.md) for certificate fingerprints and verification instructions. Nothing in Husi checks this signature at runtime.

The bundled JetBrains Runtime is signed by JetBrains, not by this project; the code signing step covers Husi's own payloads only.

When building Windows packages yourself:

* Signing is **on by default** and packaging fails without a certificate so an unsigned release is never produced by accident. To build unsigned intentionally:

  ```shell
  make desktop_package_windows DESKTOP_TARGET=windows/amd64 WINDOWS_NO_SIGN=1
  ```

* To sign with your own PKCS#12 code signing certificate (`.p12`):

  ```shell
  export WINDOWS_SIGNING_P12=/path/to/your-signing.p12
  export WINDOWS_SIGNING_P12_PASSWORD=...
  make desktop_package_windows DESKTOP_TARGET=windows/amd64
  ```

* If you redistribute your build, state so clearly. The published fingerprint is how users distinguish official releases from third-party rebuilds.

## Web Dashboard (Wasm)

Build with Node.js 26+ and the project's JDK:

```shell
make web
make web_run
make web_zip
```

No `libcore` binary, route assets, `protoc`, or native plugin builds are needed.

* Production assets: `composeApp/build/dist/wasmJs/productionExecutable/`
* ZIP archive: `composeApp/build/distributions/`

Serve the output over HTTP(S) with all relative paths intact. Opening `index.html` via `file://` is not supported.

## Plugins

```shell
make plugin PLUGIN=<Plugin name>
```

Valid plugin names:

* `hysteria2`
* `juicity`
* `naive` (Deprecated; build the official repository directly.)
* `mieru`
* `shadowquic`
