#!/bin/bash

source "buildScript/init/env.sh"
source "buildScript/plugin/shadowquic/init.sh"

DIR="$ROOT/x86"
mkdir -p $DIR

export CC=$ANDROID_X86_CC
export CXX=$ANDROID_X86_CXX
export RUST_ANDROID_GRADLE_CC=$ANDROID_X86_CC
export CARGO_TARGET_I686_LINUX_ANDROID_LINKER=$SRC_ROOT/buildScript/rust-linker/linker-wrapper.sh

cargo build --release -p shadowquic --target i686-linux-android
cp target/i686-linux-android/release/shadowquic $DIR/$LIB_OUTPUT

