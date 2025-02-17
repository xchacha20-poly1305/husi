#!/usr/bin/env bash

#set -x

source "buildScript/init/env.sh"

export CGO_ENABLED=1
export GOOS=android

CURR="plugin/mieru"
CURR_PATH="$SRC_ROOT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="mieru"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/go/mieru