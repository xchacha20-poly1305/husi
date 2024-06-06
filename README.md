<a href="https://apt.izzysoft.de/fdroid/index/apk/fr.husi/">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
    alt="Get it on IzzyOnDroid"
    height="80">
</a>

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

* Go (should be as up-to-date as possible)
* Openjdk-17 (Other version is ok. But require version 17 to build the same file as action.)

Run:

```shell
make libcore
```

This will generate `app/libs/libcore.aar`.

If gomobile is not in the GOPATH, it will be automatically downloaded and compiled.

If you don't want to build it, you can download then in [actions](https://github.com/xchacha20-poly1305/husi/actions)

#### 🎁 APK

Environment:

* jdk-17-openjdk
* ndk 26.3.11579264

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
make assets
```

Compile the release version:

```shell
make apk
```

The APK file will be located in `app/build/outputs/apk`.

#### 🌈 Plugins

```shell
make <Plugin name>
```

Plugin name list:

* `hysteria2`

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
