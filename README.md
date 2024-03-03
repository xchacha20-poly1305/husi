<a href="https://apt.izzysoft.de/fdroid/index/apk/fr.husi/">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
    alt="Get it on IzzyOnDroid"
    height="80">
</a>

## 💰 Selling Points

[![Android API](https://img.shields.io/badge/API-34-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=34)
[![Nightly build](https://github.com/xchacha20-poly1305/husi/actions/workflows/nightly.yml/badge.svg)](https://github.com/xchacha20-poly1305/husi/actions/workflows/nightly.yml)
[![License: GPL-3.0(nekohasekai)](https://img.shields.io/badge/license-GPL--3.0(nekohasekai)-orange.svg)](https://sing-box.sagernet.org/#license)
[![Pull Request](https://img.shields.io/github/issues-pr-closed/xchacha20-poly1305/husi)](https://github.com/xchacha20-poly1305/husi/pulls)

* Android API 34 & Gradle 8.6 & NDK 26.2.11394342.
* Rich DNS modes, you can choose conservatively, safely or fastly.
* Many ways to test connectivity, including ICMP, TCP ping and URL test.
* Powerful [TCP Brutal](https://github.com/apernet/tcp-brutal) congestion control algorithm provides fast speed.
* Route based on WI-FI status. 
* Trust the certificate list trusted by Mozilla to prevent certain hijacks.

## 🛠️ Contribution

### 📚 Localization

Is husi not in your language, or the translation is incorrect or incomplete? Get involved in the 
translations on our [Weblate](https://hosted.weblate.org/engage/husi/).

[![Translation status](https://hosted.weblate.org/widgets/husi/-/horizontal-auto.svg)](https://hosted.weblate.org/engage/husi/)

### 🔨 Learn to Compilation

#### 🧰 Get the Source Code

```shell
git clone https://github.com/xchacha20-poly1305/husi.git --depth=1
cd husi/
./run lib source # Will help you to get submodules
```

#### ⚖️ libcore

Environment:

* go (should be as up-to-date as possible)

Run:

```shell
./run lib core
```

This will generate `app/libs/libcore.aar`.

If gomobile is not in the GOPATH, it will be automatically downloaded and compiled.

If you don't want to build it, you can download then in [actions](https://github.com/xchacha20-poly1305/husi/actions)

#### 🎗️ Dashboard

Ensure that the Node environment is set up correctly (with bun, etc.).

```shell
./run lib dashboard
```

#### 🎁 APK

Environment:

* jdk-17-openjdk
* ndk 26.2.11394342

If the environment variables `$ANDROID_HOME` and `$ANDROID_NDK_HOME` are not set, you can run the script `buildScript/init/env_ndk.sh`:

```shell
echo "sdk.dir=${ANDROID_HOME}" > local.properties
```

Signing preparation (optional, it is recommended to sign after compilation): Replace `release.keystore` with your own keystore.

```shell
echo "KEYSTORE_PASS=" >> local.properties
echo "ALIAS_NAME=" >> local.properties
echo "ALIAS_PASS=" >> local.properties
```

Download geo resource files:

```shell
./run lib assets
```

Compile the release version:

```shell
./gradlew app:assembleFossRelease
```

The APK file will be located in `app/build/outputs/apk`.

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

Web Dashboard:

- [MetaCubeX/metacubexd](https://github.com/MetaCubeX/metacubexd)
