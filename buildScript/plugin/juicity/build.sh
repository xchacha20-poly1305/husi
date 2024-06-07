#!/usr/bin/env bash

#set -x

source "buildScript/init/env.sh"

export CGO_ENABLED=1
export GOOS=android

CURR="plugin/juicity"
CURR_PATH="$SRC_ROOT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="juicity"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/go/juicity