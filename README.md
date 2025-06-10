<a href="https://apt.izzysoft.de/fdroid/index/apk/fr.husi/">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
    alt="Get it on IzzyOnDroid"
    height="80">
</a>

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

Run:

```shell
make libcore
```

This will generate `app/libs/libcore.aar`.

If gomobile is not in the GOPATH, it will be automatically downloaded and compiled.

If you don't want to build it, you can download then in [actions](https://github.com/xchacha20-poly1305/husi/actions)

#### 🎀 Rename package name (optional)

If you don't want to use the same package name, you can run `./run rename target_name`.

#### 🎁 APK

Environment:

* jdk-21
* ndk 28.1.13356709

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

Compile the release version:

```shell
make apk
```

The APK file will be located in `app/build/outputs/apk`.

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

[GPL-3.0](./LICENSE)

## 🛡️ Credits

Core:

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)

Android GUI:

- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)
- [XTLS/AnXray](https://github.com/XTLS/AnXray)
- [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)
- [SagerNet/sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- [AntiNeko/CatBoxForAndroid](https://github.com/AntiNeko/CatBoxForAndroid)
- [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
- [dyhkwong/Exclave](https://github.com/dyhkwong/Exclave)
- [chen08209/FlClash](https://github.com/chen08209/FlClash)