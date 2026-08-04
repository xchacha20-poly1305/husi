#!/usr/bin/env bash
# Shared driver for the plugin build scripts in this directory.
#
# A plugin script sets PLUGIN (and, for Go plugins, GO_SOURCE_DIR, GO_MAIN_PKG,
# optionally GO_EXTRA_LDFLAGS) before sourcing this file, then calls
# `dispatch "$@"`. The default plugin_init/plugin_build_abi implement Go
# plugins; Rust/custom plugins redefine both after sourcing.
#
# dispatch implements the `./run plugin <name> [arg]` contract used by
# setupPlugin in buildSrc: no argument builds every ABI, `init` prepares
# sources, an ABI name builds that ABI, and `end` finalizes a per-ABI build
# (currently nothing to do).

source "buildScript/init/env.sh"

ALL_ABIS=(armeabi-v7a arm64-v8a x86 x86_64)

JNI_ROOT="$SRC_ROOT/plugin/$PLUGIN/src/main/jniLibs"
LIB_OUTPUT="lib$PLUGIN.so"

go_env() {
  export CGO_ENABLED=1
  export GOOS=android
  cd "$SRC_ROOT/plugin/$PLUGIN/$GO_SOURCE_DIR" || exit
}

plugin_init() {
  go_env
  go mod download -x
}

plugin_build_abi() {
  local abi="$1" cc goarch goarm=""
  case "$abi" in
  armeabi-v7a) cc="$ANDROID_ARM_CC" goarch=arm goarm=7 ;;
  arm64-v8a) cc="$ANDROID_ARM64_CC" goarch=arm64 ;;
  x86) cc="$ANDROID_X86_CC" goarch=386 ;;
  x86_64) cc="$ANDROID_X86_64_CC" goarch=amd64 ;;
  esac

  local dir="$JNI_ROOT/$abi"
  mkdir -p "$dir"
  go_env
  env CC="$cc" GOARCH="$goarch" ${goarm:+GOARM="$goarm"} go build -v \
    -o "$dir/$LIB_OUTPUT" -buildvcs=false -trimpath \
    -ldflags "-s -w${GO_EXTRA_LDFLAGS:+ $GO_EXTRA_LDFLAGS} -buildid=" \
    "$GO_MAIN_PKG"
}

dispatch() {
  case "${1:-}" in
  "")
    plugin_init
    local abi
    for abi in "${ALL_ABIS[@]}"; do
      plugin_build_abi "$abi"
    done
    ;;
  init)
    plugin_init
    ;;
  end) ;;
  armeabi-v7a | arm64-v8a | x86 | x86_64)
    plugin_build_abi "$1"
    ;;
  *)
    local IFS='|'
    echo "usage: $0 [init|end|${ALL_ABIS[*]}]" >&2
    exit 1
    ;;
  esac
}
