#!/bin/bash

source "buildScript/init/env.sh"
source "buildScript/plugin/shadowquic/init.sh"

DIR="$ROOT/armeabi-v7a"
mkdir -p $DIR

export CC=$ANDROID_ARM_CC
export CXX=$ANDROID_ARM_CXX
export RUST_ANDROID_GRADLE_CC=$ANDROID_ARM_CC
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER=$SRC_ROOT/buildScript/rust-linker/linker-wrapper.sh

cargo build --release -p shadowquic --target armv7-linux-androideabi
cp target/armv7-linux-androideabi/release/shadowquic $DIR/$LIB_OUTPUT

