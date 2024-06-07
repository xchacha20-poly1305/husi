#!/bin/bash

source "buildScript/init/env.sh"
source "buildScript/plugin/juicity/build.sh"

DIR="$ROOT/x86"
mkdir -p $DIR
env CC="$ANDROID_X86_CC" GOARCH=386 go build -v -o $DIR/$LIB_OUTPUT -buildvcs=false -trimpath -ldflags "-s -w -buildid=" ./cmd/client