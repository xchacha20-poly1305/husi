#!/usr/bin/env bash

set -euo pipefail

PLUGIN=shadowquic

source "buildScript/plugin/common.sh"

export AR="$ANDROID_AR"

ndk_ver=$(grep Pkg.Revision "$ANDROID_NDK_HOME/source.properties")
ndk_ver=${ndk_ver#*= }
export CARGO_NDK_MAJOR_VERSION=${ndk_ver%%.*}
export RUST_ANDROID_GRADLE_PYTHON_COMMAND=python3
export RUST_ANDROID_GRADLE_LINKER_WRAPPER_PY="$SRC_ROOT/buildScript/rust-linker/linker-wrapper.py"
export RUST_ANDROID_GRADLE_CC_LINK_ARG=""
export BINDGEN_EXTRA_CLANG_ARGS="--sysroot=$ANDROID_TOOLCHAIN/sysroot/"

CARGO_FEATURES="shadowquic-quinn,sunnyquic-noq,ring"

plugin_init() { :; }

plugin_build_abi() {
  local abi="$1" triple cc cxx
  case "$abi" in
  armeabi-v7a) triple=armv7-linux-androideabi cc="$ANDROID_ARM_CC" cxx="$ANDROID_ARM_CXX" ;;
  arm64-v8a) triple=aarch64-linux-android cc="$ANDROID_ARM64_CC" cxx="$ANDROID_ARM64_CXX" ;;
  x86) triple=i686-linux-android cc="$ANDROID_X86_CC" cxx="$ANDROID_X86_CXX" ;;
  x86_64) triple=x86_64-linux-android cc="$ANDROID_X86_64_CC" cxx="$ANDROID_X86_64_CXX" ;;
  esac

  local dir="$JNI_ROOT/$abi"
  mkdir -p "$dir"

  export CC="$cc" CXX="$cxx" RUST_ANDROID_GRADLE_CC="$cc"
  local linker_var
  linker_var="CARGO_TARGET_$(echo "$triple" | tr 'a-z-' 'A-Z_')_LINKER"
  export "$linker_var=$SRC_ROOT/buildScript/rust-linker/linker-wrapper.sh"

  cd "$SRC_ROOT/plugin/shadowquic/src/main/rust/shadowquic"
  cargo build --release -p shadowquic --target "$triple" \
    --no-default-features --features "$CARGO_FEATURES"
  cp "target/$triple/release/shadowquic" "$dir/$LIB_OUTPUT"
}

dispatch "$@"
