<a href="https://apt.izzysoft.de/fdroid/index/apk/fr.husi/">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
    alt="Get it on IzzyOnDroid"
    height="80">
</a>

# Husi (虎兕)

Husi is a non-professional and recreational proxy tool integration, aiming at promoting proxy customization.

## 🛠️ Contribution

## 🧭 Guide

[CONTRIBUTING](./CONTRIBUTING.md)

### 📚 Localization

Is husi not in your language, or the translation is incorrect or incomplete? Get involved in the
translations on our [Weblate](https://hosted.weblate.org/engage/husi/).

[![Translation status](https://hosted.weblate.org/widgets/husi/-/horizontal-auto.svg)](https://hosted.weblate.org/engage/husi/)

### 🔨 Learn to Compilation

In Linux, you can build husi reproducibly for release version.

For this, you should use the same version of JDK, NDK as below. And Go version should as same
as [version.sh](./buildScript/init/version.sh).

#### 🧰 Get the Source Code

```shell
git clone https://github.com/xchacha20-poly1305/husi.git --depth=1
cd husi/
./run lib source # Will help you to get submodules
```

#### ⚖️ libcore

Environment:

* These versions need to apply patch.

  <details>
    <summary>Unfold</summary>

  1.22.5: Apply [this patch](./libcore/patches/cgo_go1225.diff) to `${GOROOT}/src/runtime/cgocall.go`

  1.23.0-1.23.3: Apply [this patch](https://github.com/golang/go/commit/76a8409eb81eda553363783dcdd9d6224368ae0e.patch)
  to`${GOROOT}`. `make patch_go1230`

  1.23.4: Apply [this patch](https://github.com/golang/go/commit/59b7d40774b29bd1da1aa624f13233111aff4ad2.patch) to `$(GOROOT)`. `make patch_go1234`

  </details>

* Openjdk-21 (Later may OK, too.)

For Android:

```shell
make libcore_android
```

This will generate `composeApp/libs/libcore.aar`.

For desktop, build libcore for your host platform:

```shell
make libcore
```

This will generate `composeApp/libs/libcore-desktop-<host-platform>-<host-arch>.jar`.

Or for specific targets:

```shell
make libcore_desktop DESKTOP_TARGETS=linux/amd64,darwin/arm64
```

If desktop build needs an explicit JNI headers directory, pass `JNI_INCLUDE`:

```shell
make libcore_desktop DESKTOP_TARGETS=linux/amd64 JNI_INCLUDE=/path/to/jni
```

For Darwin targets on non-Darwin hosts, also pass the macOS SDK explicitly:

```shell
make libcore_desktop DESKTOP_TARGETS=darwin/arm64 JNI_INCLUDE=/path/to/jni DARWIN_SDK=/path/to/MacOSX.sdk
```

Common desktop targets:

* `linux/amd64`
* `linux/arm64`
* `darwin/arm64`

`darwin/amd64` is no longer supported and `windows/arm64` is temporarily not shipped: androidx
sqlite-bundled has no binary for them, so the app cannot open its database there. See
[issuetracker 495864182](https://issuetracker.google.com/issues/495864182) for `osx_x64` and
[issuetracker 426464784](https://issuetracker.google.com/issues/426464784) for `windows_arm64`.

Linux desktop targets use `zig cc` / `zig c++` with a glibc 2.31 target for the `with_naive_outbound` build; the
required prebuilt Cronet library is downloaded through Go modules, so no `cronet-go` checkout is needed. Darwin
targets use Xcode on macOS, or Zig plus an explicit macOS SDK path via `DARWIN_SDK` or `--darwinsdk` on other hosts.

Desktop Gradle builds select `composeApp/libs/libcore-desktop-<platform>-<arch>.jar` automatically from the current
`os.name` and `os.arch`.

You can override it explicitly:

```shell
./gradlew -p composeApp run -PdesktopTarget=linux/amd64
```

If the selected jar is missing, the build fails immediately.

If you run `libcore/build.sh` directly:

* `--android`: build Android only
* `--desktop`: build desktop only (default target: `host`)
* `--android --desktop`: build both
* `--jniinclude <path>`: pass JNI headers include path to desktop `anja bind -target=jvm`
* `--darwinsdk <path>`: pass a macOS SDK path for Darwin desktop targets on non-Darwin hosts
* no platform args: defaults to Android only

If anja is not in GOPATH, it will be automatically downloaded and compiled.

#### 🎀 Rename package name (optional)

If you don't want to use the same package name, you can run `./run rename target_name`.

#### 🎁 APK

Environment:

* jdk-21
* ndk 29.0.14206865

If the environment variables `$ANDROID_HOME` and `$ANDROID_NDK_HOME` are not set, source
`buildScript/init/env_ndk.sh` to set them:

```shell
source buildScript/init/env_ndk.sh
```

Then write the SDK path to `local.properties`:

```shell
echo "sdk.dir=${ANDROID_HOME}" > local.properties
```

Signing preparation (optional, it is recommended to sign after compilation): Replace `release.keystore` with your own
keystore.

```shell
echo "KEYSTORE_PASS=" >> local.properties
echo "ALIAS_NAME=" >> local.properties
echo "ALIAS_PASS=" >> local.properties
```

Download geo resource files:

```shell
make assets
```

Generate open source license metadata:

```shell
make aboutlibraries_go
make aboutlibraries_android
```

This writes Android metadata to
`composeApp/src/androidMain/composeResources/files/aboutlibraries.json`.

Compile the release version:

```shell
make apk
```

The APK file will be located in `androidApp/build/outputs/apk`.

#### 🖥️ Desktop

Environment:

* jdk-21
* zig 0.16

Run the desktop application:

```shell
make desktop
```

Generate desktop open source license metadata:

```shell
make aboutlibraries_go
make aboutlibraries_desktop
```

This writes desktop metadata to
`composeApp/src/desktopMain/composeResources/files/aboutlibraries.json`.

Package a distributable for the current OS:

```shell
make desktop_package
```

This dispatches to the host-native packaging flow:

* Linux: `make desktop_package_linux`
* macOS: `make desktop_package_macos`
* Windows/MSYS: `make desktop_package_windows`

Build a ProGuard-shrunk **uber JAR** that runs on system Java (no bundled JRE/runtime image):

```shell
make desktop_uberjar
```

Output directory:

```shell
composeApp/build/compose/jars/
```

Run it with system Java (JDK/JRE 21+):

```shell
java -jar composeApp/build/compose/jars/fr.husi-<platform>-<arch>-<version>.jar
```

Build Linux native packages (`deb/rpm/pacman`) with Java 21 dependency metadata:

```shell
make desktop_package_linux
```

This command still builds the uber jar first, then packages it with `nfpm`.
Required host tools: `zig`, `git`, `nfpm`.

Package timestamps are derived from git tag `v<VERSION_NAME>` from `husi.properties`,
not from local build time.
Default output directory:

```shell
composeApp/build/compose/packages/linux/
```

You can select target formats:

```shell
make desktop_package_linux LINUX_PACKAGE_FORMATS=deb,pacman
```

### Installing without root

`deb`, `rpm` and `pacman` all need a package manager and therefore root. Two other
formats do not, which is what makes husi usable on a managed machine — a lab, a
work laptop, a school computer:

`tarball` is the relocatable app subtree as a `.tar.zst`. It needs `tar` and
`zstd` to build, and unpacks to a directory holding the jar, the launcher,
`husi-core`, `libhusicore.so` and its own installer:

```shell
./install.sh                     # installs under ~/.local, no root anywhere
./install.sh --with-daemon       # ... and installs the daemon too, via pkexec
./install.sh --prefix /opt/husi  # or somewhere else entirely
```

`install.sh` copies the tree to `<prefix>/lib`, symlinks `<prefix>/bin`, and
registers a desktop entry and icon under `$XDG_DATA_HOME` with absolute paths, so
the application menu and the URL schemes work whether or not `<prefix>/bin` is on
`PATH`. It leaves a matching `uninstall.sh` next to the installed tree. Without
`--with-daemon` nothing ever asks for privileges; husi still runs as a local
proxy, and only TUN needs the daemon, which Settings can install later.

`appimage` is a single self-contained file. Unlike every other Linux format it
bundles its own Java runtime — linked with `jlink` from the JDK modules the app
actually uses (the module list is shared with the Windows JBR packages, in
[`release/desktop/jre-modules.sh`](release/desktop/jre-modules.sh)) — so it does not require a system Java 21 at all. Its glibc floor
comes from that bundled runtime rather than from the launcher, which is static
musl. Building one additionally needs `jlink`, `appimagetool` and an `objcopy`
for the target architecture; cross-building also needs that architecture's JDK
modules, since `jlink` links a runtime for the target, not for the host:

```shell
make desktop_package_linux DESKTOP_TARGET=linux/arm64 \
    LINUX_PACKAGE_FORMATS=appimage \
    JLINK_JMODS=/path/to/aarch64-jdk/jmods \
    APPIMAGE_RUNTIME=/path/to/runtime-aarch64
```

Neither format is built by default, but releases ship both.

The daemon is the one privileged piece in all of this. The native packages install
and enable it themselves; everywhere else it is an explicit, optional step, either
`--with-daemon`, the Settings entry, or `husi-core service install` as root. The
first two go through `pkexec` and so need polkit.

Linux desktop data directory is `$XDG_CONFIG_HOME/husi/` if set, otherwise
`$HOME/.config/husi/`.

Installed launcher supports user config files in the same Linux config directory:

* `<config-dir>/husi/desktop-java-opts.conf` for JVM options
* `<config-dir>/husi/desktop-app-args.conf` for application startup arguments

Linux native packages include a native launcher built with Zig from `launcher/`.

Build the launcher standalone:

```shell
make launcher
```

The default packaging flow runs `make launcher` first, then `package.sh` consumes that binary.
Zig targets musl by default for static linking; no external C toolchain is needed.
The launcher runs unprivileged. Privileges live in the separate `husi-core`
daemon (`husi-core service install`; systemd / launchd / Windows SCM service
`husi-daemon`). Without a daemon the UI falls back to an unprivileged session
host; TUN requires the daemon.

`husi-core` is a second, independent Zig project under `libcore/shim/`, built by
`make core_desktop` and packaged alongside `libhusicore.*`. It links libc
dynamically, unlike the static launcher, so the two are deliberately kept apart.

You can preflight required tooling without producing packages:

```shell
./release/linux/package.sh --check-tools --formats deb,rpm,pacman
```

Build macOS `.dmg` packages with system Java runtime dependency:

```shell
make desktop_package_macos
```

This command builds the uber jar first, then packages it into `Husi.app` and a `.dmg` image.

The app bundle icon is a checked-in static asset generated from
`composeApp/src/commonMain/composeResources/drawable/ic_launcher_foreground.xml`,
so packaging no longer builds icons dynamically.

On macOS hosts it uses native tooling: `hdiutil`.
On Linux hosts it uses `xorrisofs` and emits a compatibility `.dmg`
(an ISO9660/HFS hybrid image that macOS can mount). For Linux fallback, `DESKTOP_TARGET`
is required because the Gradle uber-jar task otherwise defaults to the Linux host target:

```shell
make desktop_package_macos DESKTOP_TARGET=darwin/arm64
```

Required host tools:

* Common: `zig`, `git`
* macOS host: `hdiutil`
* Linux fallback: `xorrisofs`

Package timestamps are derived from git tag `v<VERSION_NAME>` from `husi.properties`,
not from local build time.
Default output directory:

```shell
composeApp/build/compose/packages/macos/
```

Installed app bundle uses the same native launcher from `launcher/` as Linux packaging.
User config files are created under:

* `~/Library/Application Support/husi/desktop-java-opts.conf` for JVM options
* `~/Library/Application Support/husi/desktop-app-args.conf` for application startup arguments

Build Windows portable zip and NSIS installer packages:

```shell
make desktop_package_windows DESKTOP_TARGET=windows/amd64
```

This command builds the uber jar first, then packages it with the same Zig launcher used by Linux and macOS.
Required host tools:

* `zig`
* `git`
* `python3`
* `makensis` (NSIS)
* `osslsigncode` (unless building with `WINDOWS_NO_SIGN=1`, see below)

Default output directory:

```shell
composeApp/build/compose/packages/windows/
```

Outputs:

* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>.zip`
* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>-installer.exe`

The installer is a per-user NSIS installer that installs into `%LOCALAPPDATA%\Programs\Husi`, creates a Start Menu
shortcut, and registers the configured URL schemes for the current user. The Windows launcher is unprivileged.
Privileges live in the `husi-core` daemon installed as a Windows service (`husi-core service install`); TUN requires the
daemon. Without it the UI falls back to an unprivileged session host.

The UI and the daemon therefore install to **two different places**, and both hold a copy of `husi-core.exe` +
`husicore.dll`:

| What                                           | Where                          | Installed by                               |
|------------------------------------------------|--------------------------------|--------------------------------------------|
| UI (launcher, jar, and the core pair it ships) | `%LOCALAPPDATA%\Programs\Husi` | the NSIS installer, per user, no elevation |
| Daemon (the core pair the service runs)        | `%ProgramFiles%\husi`          | `husi-core service install`, elevated      |
| Daemon state (snapshots, ownership)            | `%ProgramData%\husi`           | the daemon itself                          |

The installer runs `husi-core.exe service install` from its own directory at the end, so the per-user copy is what seeds
the Program Files copy — that is why the same two files exist twice. The uninstaller reverses both, calling
`husi-core.exe service uninstall` before removing its own directory. It does not pass `--purge`, so `%ProgramData%\husi`
survives an uninstall; remove it by hand, or run `husi-core service uninstall --purge` yourself beforehand.

##### ☕ Packages with a bundled runtime

Both of the above ask the machine for a Java 21. A second pair does not.
`make desktop_package_windows_jbr` still writes the thin zip and installer from
the same signed payloads, and additionally:

```shell
make desktop_package_windows_jbr DESKTOP_TARGET=windows/amd64
```

* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>-jbr.zip`
* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>-jbr-installer.exe`

These carry a `runtime\` directory next to the launcher, linked with `jlink` from the modules of the
[JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime) — the JDK IntelliJ ships, whose Skia, HiDPI and font
rendering work is exactly what Compose Desktop wants. The launcher prefers that runtime over anything installed on the
machine; only the `JAVA` environment variable overrides it.

The modules are fetched into `build/jbr/` on demand, pinned by `JBR_VERSION` / `JBR_BUILD` in
`buildScript/init/version.sh`. Fetch them yourself, or point the packaging at a copy you already have:

```shell
./run lib jbr windows/amd64
make desktop_package_windows_jbr DESKTOP_TARGET=windows/amd64 JBR_JMODS=/path/to/jbrsdk/jmods
```

Building these additionally needs `jlink` on `PATH`. Its feature version has to be at least the JetBrains Runtime's —
`jlink` cannot read modules newer than itself — which is why `JBR_VERSION` tracks `JAVA_VERSION`. Nothing else about the
host matters: `jlink` links an image for the platform its modules belong to, so the Windows runtime is linked on the
Linux release runner like everything else.

The bundled runtime is signed by JetBrains, not by this project; the code signing below covers our own payloads only.

##### 🔏 Windows code signing

Windows releases are Authenticode signed: the launcher, `husi-core.exe`,
`husicore.dll` and the installer all carry the **same self-signed certificate**, published as [
`release/windows/husi-signing-cert.pem`](release/windows/husi-signing-cert.pem). Its fingerprints and how to check a
download against it are in
[`release/windows/README.md`](release/windows/README.md).

Being self-signed, it earns no SmartScreen reputation — Windows still calls the publisher unknown, and that is expected.
The signature is there so the privileged daemon can tell that `husi-core.exe` and the `husicore.dll` it loads are the
pair that shipped together: the shim loads that DLL by absolute path and runs it as SYSTEM, so a swapped DLL would be a
swapped SYSTEM process. The check lives in `daemonhost.VerifyCorePairSignature` and follows sing-box's `boxdd`.

**If you build Windows packages yourself, read this:**

* Signing is **on by default** and packaging fails without a certificate. That is deliberate — an unsigned release
  should never happen by accident. To build unsigned on purpose:

  ```shell
  make desktop_package_windows DESKTOP_TARGET=windows/amd64 WINDOWS_NO_SIGN=1
  ```

  Unsigned builds install and run fine: the daemon logs a warning and skips the pair check, because there is nothing to
  compare.

* To sign with a certificate of your own, mint a self-signed code signing certificate (see the Windows signing entry
  in [AGENTS.md](AGENTS.md)) and point the packaging at it:

  ```shell
  export WINDOWS_SIGNING_P12=/path/to/your-signing.p12
  export WINDOWS_SIGNING_P12_PASSWORD=...
  make desktop_package_windows DESKTOP_TARGET=windows/amd64
  ```

  Your own certificate works fine. The daemon requires the shim and the library to share a signer, not to match any
  particular certificate.

* **Do not mix payloads from different builds.** Taking `husi-core.exe` from an official release and pairing it with a
  `husicore.dll` you signed yourself (or the reverse) makes the daemon refuse to start. Ship whole packages.

* Give your certificate a long life. The validity window is checked against the current time rather than against the
  signature timestamp, so an expired certificate starts failing already-installed daemons.

* If you redistribute your build, say so. The published fingerprint above is how users tell an official release from a
  rebuild, and nothing in the daemon makes that distinction for them.

#### 🌈 Plugins

```shell
make plugin PLUGIN=<Plugin name>
```

Plugin name list:

* `hysteria2`
* `juicity`
* `naive` (Deprecated. Build official repository directly, please. )
* `mieru`
* `shadowquic`

## 🏃‍♂️ Run

### Desktop

Requirement: >= Java Runtime **21**

_No bundled JRE for end users_

```shell
$ fr.husi --help
Usage: fr.husi [<options>] [<deep-link>]...

Options:
  -d, --dir=<path>       Data directory
  -l, --log-level=<int>  Log level override (0-6)
  -m, --many             Ignore exist instance
  -b, --background       Start without opening the main window
  -h, --help             Show this message and exit

Arguments:
  <deep-link>  Deep links
```

#### Arguments

URLs to import.

## ☠️ End users

[Wiki](https://github.com/xchacha20-poly1305/husi/wiki)

## 📖 License

[GPL-3.0 or later](./LICENSE)

## 🌐 GeoIP data

Husi can generate and use GeoIP rule-set assets derived from MaxMind GeoLite2 data. GeoLite2 data is
created by MaxMind and is subject to the
[GeoLite2 End User License Agreement](https://www.maxmind.com/en/geolite/eula) and related attribution
and update requirements.

Third-party mirrors or re-published GeoLite2 database files do not grant additional rights beyond
MaxMind's terms. If you build, redistribute, or use Husi with generated GeoIP assets, you are
responsible for ensuring that your use and redistribution of those assets complies with the
applicable MaxMind license terms.

## 🤝 Acknowledgements

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)
- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)
- [XTLS/AnXray](https://github.com/XTLS/AnXray)
- [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)
- [SagerNet/sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- [AntiNeko/CatBoxForAndroid](https://github.com/AntiNeko/CatBoxForAndroid)
- [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
- [dyhkwong/Exclave](https://github.com/dyhkwong/Exclave)
- [chen08209/FlClash](https://github.com/chen08209/FlClash)
- [RikkaApps/RikkaX](https://github.com/RikkaApps/RikkaX)

Developing

- [![](https://resources.jetbrains.com/storage/products/company/brand/logos/jetbrains.svg)](https://www.jetbrains.com)

  JetBrains' powerful IDE.
