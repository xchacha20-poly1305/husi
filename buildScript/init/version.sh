#!/usr/bin/env bash

# Each `# renovate:` comment tells Renovate where the value below it comes from;
# the matching custom manager lives in .github/renovate.json.

# renovate: datasource=golang-version depName=go
GO_VERSION="1.27.2"
# renovate: datasource=github-releases depName=rust-lang/rust
RUST_VERSION="1.99.0"
# Bundled into the Windows JBR packages and Linux AppImage. JAVA_VERSION is the
# JBR feature version: the host jlink links these jmods, and jlink cannot read
# jmods newer than itself.
# renovate: datasource=github-releases depName=JetBrains/JetBrainsRuntime
JBR_RELEASE="25.0.4.1b635.70"
JBR_VERSION="${JBR_RELEASE%b*}"
JBR_BUILD="${JBR_RELEASE#*b}"
JAVA_VERSION="${JBR_VERSION%%.*}"
# NDK version as named by sdkmanager under $ANDROID_HOME/ndk/.
# Renovate has no datasource for it; keep androidApp/build.gradle.kts `ndkVersion` equal.
ANDROID_NDK_VERSION="30.0.16248370"
ZIG_VERSION="0.16.0"
# renovate: datasource=github-releases depName=goreleaser/nfpm
NFPM_VERSION="2.47.0"
# renovate: datasource=github-releases depName=AppImage/appimagetool
APPIMAGETOOL_VERSION="1.9.1"
# renovate: datasource=github-releases depName=AppImage/type2-runtime
APPIMAGE_RUNTIME_VERSION="20251108"
PROTOC_VERSION="36.1"

# renovate: datasource=github-releases depName=Dreamacro/maxmind-geoip
GEOIP_VERSION="20260912"

# renovate: datasource=github-releases depName=v2fly/domain-list-community
GEOSITE_VERSION="20261008093058"
