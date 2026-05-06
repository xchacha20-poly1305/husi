<a href="https://apt.izzysoft.de/fdroid/index/apk/fr.husi/">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
    alt="Get it on IzzyOnDroid"
    height="80">
</a>

We have migrated to [codeberg](https://codeberg.org/xchacha20-poly1305/husi)!

# Husi (虎兕)

Husi is a non-professional and recreational proxy tool integration, aiming at promoting proxy customization.

## 🗣️ Alert

In August 2025, Google [announced](https://developer.android.com/developer-verification) that as of September 2026, it will no longer be possible to develop apps for the Android platform without first registering centrally with Google. This registration will involve:

- Paying a fee to Google
- Agreeing to Google’s Terms and Conditions
- Providing government identification
- Uploading evidence of the developer’s private signing key
- Listing all current and future application identifiers

As a free software, husi will never submit to Google. Visit [Keep Android Open](https://keepandroidopen.org/) to defend the openness!

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
git clone https://codeberg.org/xchacha20-poly1305/husi.git --depth=1
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
* `darwin/amd64`
* `darwin/arm64`

For Linux desktop targets, the build includes `with_naive_outbound` and consults a
[`cronet-go`](https://github.com/sagernet/cronet-go) checkout via `build-naive env`. If `CRONET_GO_ROOT` is unset,
`libcore/build.sh` first checks `../../cronet-go`, then falls back to `$HOME/cronet-go`:

```shell
CRONET_GO_ROOT=/path/to/cronet-go make libcore
```

For Linux targets, `cronet-go` exports the naiveproxy cross-toolchain environment directly.
For Darwin targets on a Darwin host, `libcore/build.sh` keeps `with_naive_outbound` and derives `CC`/`CXX`/`CGO_*`
from the Chromium clang and hermetic Xcode toolchain inside the `cronet-go` checkout. If the Darwin SDK/linker tree
is missing, the desktop build fails immediately.
For Darwin targets on non-Darwin hosts, `libcore/build.sh` uses `zig cc` / `zig c++`, requires an explicit macOS SDK
path via `DARWIN_SDK` or `--darwinsdk`, exports the matching `CGO_*` sysroot/library flags, keeps
`with_naive_outbound`, and does not require a `cronet-go` checkout, so `zig` must be available in `PATH`.

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

Build an **uber JAR** that runs on system Java (no bundled JRE/runtime image):

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

This command still builds the uber jar first, then packages it with native Linux tooling.
Required host tools: `zig`, `git`, `dpkg-deb`, `rpmbuild`, `bsdtar`, `zstd`.
If building `deb`, `gzip` is also required.

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

Desktop data directory is `~/.config/husi/` (`$XDG_CONFIG_HOME/husi` if set).

Installed launcher supports user config files:

* `~/.config/husi/desktop-java-opts.conf` for JVM options
* `~/.config/husi/desktop-app-args.conf` for application startup arguments

Linux native packages include a native launcher built with Zig from `launcher/`.

Build the launcher standalone:

```shell
make launcher
```

The default packaging flow runs `make launcher` first, then `package.sh` consumes that binary.
Zig targets musl by default for static linking; no external C toolchain is needed.
Package install scripts call `setcap` on the launcher so capabilities can be raised to ambient set before starting the JVM.

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
On Linux hosts it falls back to `genisoimage` or `mkisofs` and emits a compatibility `.dmg`
(an ISO9660/HFS hybrid image that macOS can mount). For Linux fallback, `DESKTOP_TARGET`
is required because the Gradle uber-jar task otherwise defaults to the Linux host target:

```shell
make desktop_package_macos DESKTOP_TARGET=darwin/arm64
```

Required host tools:

* Common: `zig`, `git`
* macOS host: `hdiutil`
* Linux fallback: `genisoimage` or `mkisofs`

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

Default output directory:

```shell
composeApp/build/compose/packages/windows/
```

Outputs:

* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>.zip`
* `<PACKAGE_NAME>-<VERSION_NAME>-windows-<arch>-installer.exe`

The installer is a per-user NSIS installer that installs into `%LOCALAPPDATA%\Programs\Husi`,
creates a Start Menu shortcut, and registers the configured URL schemes for the current user.
The Windows launcher embeds an application manifest and requests administrator elevation via UAC at launch time.

#### 🌈 Plugins

```shell
make plugin PLUGIN=<Plugin name>
```

Plugin name list:

* `hysteria2`
* `juicity`
* `naive` ( Deprecated. Build official repository directly, please. )
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

[Wiki](https://codeberg.org/xchacha20-poly1305/husi/wiki)

## 📖 License

[GPL-3.0 or later](./LICENSE)

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
