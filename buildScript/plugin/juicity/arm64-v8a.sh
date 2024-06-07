#!/bin/bash

source "buildScript/init/env.sh"
source "buildScript/plugin/juicity/build.sh"

DIR="$ROOT/arm64-v8a"
mkdir -p $DIR
env CC="$ANDROID_ARM64_CC" GOARCH="arm64" go build -v -o $DIR/$LIB_OUTPUT -buildvcs=false -trimpath -ldflags "-s -w -buildid=" ./cmd/client