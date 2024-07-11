#!/usr/bin/env bash

#set -x

source "buildScript/init/env.sh"

CURR="plugin/naive"
CURR_PATH="$SRC_ROOT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="naive"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/jni/naiveproxy/src

if [ -x "$HOME/.local/lib/git/bin/git" ]; then
  export PATH="$HOME/.local/lib/git/bin:$PATH"
fi

