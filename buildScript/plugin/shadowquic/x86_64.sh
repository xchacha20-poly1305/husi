#!/bin/bash

source "buildScript/init/env.sh"
source "buildScript/plugin/shadowquic/init.sh"

DIR="$ROOT/x86_64"
mkdir -p $DIR

export CC=$ANDROID_X86_64_CC
export CXX=$ANDROID_X86_64_CXX
export RUST_ANDROID_GRADLE_CC=$ANDROID_X86_64_CC
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER=$SRC_ROOT/buildScript/rust-linker/linker-wrapper.sh

cargo build --release -p shadowquic --target x86_64-linux-android
cp target/x86_64-linux-android/release/shadowquic $DIR/$LIB_OUTPUT

