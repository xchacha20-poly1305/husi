#!/usr/bin/env bash

source "buildScript/init/env.sh"

export AR=$ANDROID_AR
export LD=$ANDROID_LD

ndkVer=$(grep Pkg.Revision $ANDROID_NDK_HOME/source.properties)
ndkVer=${ndkVer#*= }
ndkVer=${ndkVer%%.*}

export CARGO_NDK_MAJOR_VERSION=$ndkVer
export RUST_ANDROID_GRADLE_PYTHON_COMMAND=python3
export RUST_ANDROID_GRADLE_LINKER_WRAPPER_PY=$SRC_ROOT/buildScript/rust-linker/linker-wrapper.py
export RUST_ANDROID_GRADLE_CC_LINK_ARG=""
export BINDGEN_EXTRA_CLANG_ARGS=--sysroot=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/

CURR="plugin/shadowquic"
CURR_PATH="$SRC_ROOT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="shadowquic"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/rust/shadowquic

