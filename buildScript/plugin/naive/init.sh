#!/usr/bin/env bash

source "buildScript/init/env.sh"

CURR="plugin/naive"
CURR_PATH="$SRC_ROOT/$CURR"

git submodule update --init --recursive "$CURR/*"
cd $CURR_PATH/src/main/jni/naiveproxy/src