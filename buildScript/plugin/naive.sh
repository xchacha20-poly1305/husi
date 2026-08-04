#!/usr/bin/env bash

set -euo pipefail

PLUGIN=naive

source "buildScript/plugin/common.sh"

if [ -x "$HOME/.local/lib/git/bin/git" ]; then
  export PATH="$HOME/.local/lib/git/bin:$PATH"
fi

NAIVE_SRC="$SRC_ROOT/plugin/naive/src/main/jni/naiveproxy/src"

plugin_init() {
  cd "$SRC_ROOT"
  git submodule update --init --recursive "plugin/naive/*"
}

# Chromium's out/Release is stateful, so each ABI keeps its own tree under
# out/Release<Abi> and swaps it in for the duration of the build.
plugin_build_abi() {
  local abi="$1" cpu cache
  case "$abi" in
  armeabi-v7a) cpu=arm cache=ReleaseArm ;;
  arm64-v8a) cpu=arm64 cache=ReleaseArm64 ;;
  x86) cpu=x86 cache=ReleaseX86 ;;
  x86_64) cpu=x64 cache=ReleaseX64 ;;
  esac

  cd "$NAIVE_SRC"
  rm -rf out/Release
  mv -f "out/$cache" out/Release || true
  export EXTRA_FLAGS="target_os=\"android\" target_cpu=\"$cpu\""
  ./get-clang.sh
  ./build.sh

  local dir="$JNI_ROOT/$abi"
  rm -rf "$dir"
  mkdir -p "$dir"
  cp out/Release/naive "$dir/$LIB_OUTPUT"
  mv -f out/Release "out/$cache"
}

dispatch "$@"
