#!/usr/bin/env bash

source buildScript/init/env_ndk.sh

if [[ "$OSTYPE" =~ ^darwin ]]; then
  export SRC_ROOT="$PWD"
  _NDK_HOST="darwin-x86_64"
else
  SRC_ROOT="$(realpath .)"
  export SRC_ROOT
  _NDK_HOST="linux-x86_64"
fi

export ANDROID_TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$_NDK_HOST"
_BIN="$ANDROID_TOOLCHAIN/bin"

export ANDROID_AR="$_BIN/llvm-ar"

export ANDROID_ARM_CC="$_BIN/armv7a-linux-androideabi21-clang"
export ANDROID_ARM_CXX="$_BIN/armv7a-linux-androideabi21-clang++"

export ANDROID_ARM64_CC="$_BIN/aarch64-linux-android21-clang"
export ANDROID_ARM64_CXX="$_BIN/aarch64-linux-android21-clang++"

export ANDROID_X86_CC="$_BIN/i686-linux-android21-clang"
export ANDROID_X86_CXX="$_BIN/i686-linux-android21-clang++"

export ANDROID_X86_64_CC="$_BIN/x86_64-linux-android21-clang"
export ANDROID_X86_64_CXX="$_BIN/x86_64-linux-android21-clang++"
