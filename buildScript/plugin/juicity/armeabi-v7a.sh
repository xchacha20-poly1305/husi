#!/bin/bash

# set -x

source "buildScript/init/env.sh"
source "buildScript/plugin/juicity/build.sh"

DIR="$ROOT/armeabi-v7a"
mkdir -p $DIR
env CC="$ANDROID_ARM_CC" GOARCH=arm GOARM=7 go build -v -o $DIR/$LIB_OUTPUT -buildvcs=false -trimpath -ldflags "-s -w -buildid=" ./cmd/client