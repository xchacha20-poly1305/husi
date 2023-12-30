[English](./README.md) | 简体中文

# 虎兕

何草不黄？何日不行？何人不将？经营四方。

何草不玄？何人不矜？哀我征夫，独为匪民。

匪兕匪虎，率彼旷野。哀我征夫，朝夕不暇。

有芃者狐，率彼幽草。有栈之车，行彼周道。

## 特性

[![Android API](https://img.shields.io/badge/API-34-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=34)
[![Nightly build](https://github.com/xchacha20-poly1305/husi/actions/workflows/nightly.yml/badge.svg)](https://github.com/xchacha20-poly1305/husi/actions/workflows/nightly.yml)
[![License: GPL-3.0(世界)](https://img.shields.io/badge/license-GPL--3.0(世界)-orange.svg)](https://sing-box.sagernet.org/#license)

* 安卓 API 34 & Gradle 8.2.0 & ndk 26.1.10909125。
* 节省内存的 sing-box rule_set。
* 由 sing-box 强力驱动的数不胜数的协议：socks, http, Shadowsocks, VMess, Trojan,
  WireGuard, Hysteria (1 和 2) (附带端口跳跃), ShadowTLS, VLESS, TUIC, SSH。

## 开发 / Development

### 发布新版本之前......

* `go mod tidy`

* 升级版本信息（[husi.properties](./husi.properties)）。

* 确保测试 CI 通过。

### 编译

#### 获取源代码

```shell
git clone https://github.com/xchacha20-poly1305/husi.git --depth=1
cd husi/
./run lib source
```

#### libcore

环境：

* go （版本应尽可能新）

运行：

```shell
./run lib core
```

得到 `app/libs/libcore.aar`

如果 GOPATH 中不存在 gomobile 则会自动下载编译。

#### Dashboard

请确保已设置好正确的 Node 环境（pnpm 等）。

```shell
./run lib dashboard
```

#### apk

环境：

* jdk-17-openjdk
* ndk 26.1.10909125

如果没有环境变量 `$ANDROID_HOME` 和 `$ANDROID_NDK_HOME` 可以运行脚本 `buildScript/init/env_ndk.sh`

```shell
echo "sdk.dir=${ANDROID_HOME}" > local.properties
echo "ndk.dir=${ANDROID_HOME}/ndk/26.1.10909125" >> local.properties
```

签名准备（可选，建议编译后再签名）：替换 `release.keystore` 为自己的。

```shell
echo "KEYSTORE_PASS=" >> local.properties
echo "ALIAS_NAME=" >> local.properties
echo "ALIAS_PASS=" >> local.properties
```

下载 geo 资源文件：

```shell
./run lib assets
```

正式编译：

```shell
./gradlew app:assembleOssRelease
```

在 `app/build/outputs/apk` 得到 apk 文件。

## Credits

Core:
- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)
- [Matsuridayo/sing-box-extra](https://github.com/MatsuriDayo/sing-box-extra)

Android GUI:
- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)
- [Matsuridayo/Matsuri](https://github.com/MatsuriDayo/Matsuri)
- [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)
- [SagerNet/sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- [AntiNeko/CatBoxForAndroid](https://github.com/AntiNeko/CatBoxForAndroid)
- [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)

Web Dashboard:

- [MetaCubeX/metacubexd](https://github.com/MetaCubeX/metacubexd)
