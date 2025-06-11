#!/bin/bash

source "buildScript/init/env.sh"
source "buildScript/plugin/shadowquic/init.sh"

DIR="$ROOT/arm64-v8a"
mkdir -p $DIR

export CC=$ANDROID_ARM64_CC
export CXX=$ANDROID_ARM64_CXX
export RUST_ANDROID_GRADLE_CC=$ANDROID_ARM64_CC
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$SRC_ROOT/buildScript/rust-linker/linker-wrapper.sh

cargo build --release -p shadowquic --target aarch64-linux-android
cp target/aarch64-linux-android/release/shadowquic $DIR/$LIB_OUTPUT