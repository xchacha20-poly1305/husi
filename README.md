<a href="https://apt.izzysoft.de/fdroid/index/apk/fr.husi/">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
    alt="Get it on IzzyOnDroid"
    height="80">
</a>

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

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/xchacha20-poly1305/husi)

New here? You can use [DeepWiki](https://deepwiki.com/xchacha20-poly1305/husi) to known basic structure of husi and ask anything you want.

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

Run:

```shell
make libcore
```

This will build libcore for your current host environment and generate
`composeApp/libs/libcore-desktop-<host-platform>-<host-arch>.jar`.

For Android:

```shell
make libcore_android
```

This will generate `composeApp/libs/libcore.aar`.

For desktop, build libcore for your host platform:

```shell
make libcore_desktop_host
```

Or for specific targets:

```shell
make libcore_desktop DESKTOP_TARGETS=linux/amd64,darwin/arm64
```

Common desktop targets:

* `linux/amd64`
* `linux/arm64`
* `darwin/amd64`
* `darwin/arm64`
* `windows/amd64`
* `windows/arm64`

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
* no platform args: defaults to Android only

If gomobile is not in the GOPATH, it will be automatically downloaded and compiled.

If you don't want to build it, you can download then in [actions](https://github.com/xchacha20-poly1305/husi/actions)

#### 🎀 Rename package name (optional)

If you don't want to use the same package name, you can run `./run rename target_name`.

#### 🎁 APK

Environment:

* jdk-21
* ndk 29.0.14206865

If the environment variables `$ANDROID_HOME` and `$ANDROID_NDK_HOME` are not set, you can run the script
`buildScript/init/env_ndk.sh`:

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
./gradlew :composeApp:exportLibraryDefinitions
```

Compile the release version:

```shell
make apk
```

The APK file will be located in `androidApp/build/outputs/apk`.

#### 🖥️ Desktop

Environment:

* jdk-21

Run the desktop application:

```shell
make desktop
```

Package a distributable for the current OS:

```shell
make desktop_package
```

This now builds an **uber JAR** that runs on system Java (no bundled JRE/runtime image).
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
Required host tools: `cc`, `dpkg-deb`, `rpmbuild`, `bsdtar`, `zstd`.
Default output directory:

```shell
composeApp/build/compose/packages/linux/
```

You can select target formats:

```shell
make desktop_package_linux LINUX_PACKAGE_FORMATS=deb,pacman
```

Installed launcher supports user config files:

* `~/.config/husi/desktop-java-opts.conf` for JVM options
* `~/.config/husi/desktop-app-args.conf` for application startup arguments

Linux native packages include a native launcher from `launcher/husi-launcher.c`.
The default packaging flow runs `./launcher/build.sh` first, then `package-native.sh` consumes that binary.
`./launcher/build.sh` uses static linking for launcher and exits directly if static link cannot be completed.
You can choose compiler via `CC=<compiler> ./launcher/build.sh` or `./launcher/build.sh --cc <compiler>`.
For smaller static launcher in CI, use `CC=musl-gcc ./launcher/build.sh` (requires `musl-tools`).
Package install scripts call `setcap` on the launcher so capabilities can be raised to ambient set before starting the JVM.

Or run the direct command:

```shell
make desktop_uberjar
```

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

## ☠️ End users

[Wiki](https://github.com/xchacha20-poly1305/husi/wiki)

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
