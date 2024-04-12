#!/bin/bash

source "buildScript/init/env.sh"
source "buildScript/plugin/hysteria2/build.sh"

DIR="$ROOT/x86_64"
mkdir -p $DIR
env CC="$ANDROID_X86_64_CC" GOARCH=amd64 go build -v -o $DIR/$LIB_OUTPUT -buildvcs=false -trimpath -ldflags "-s -w -buildid=" ./app