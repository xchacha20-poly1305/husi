#!/usr/bin/env bash

source "buildScript/init/env.sh"

export CGO_ENABLED=1
export GOOS=android

CURR="plugin/juicity"
CURR_PATH="$SRC_ROOT/$CURR"

#git submodule update --init "$CURR/*"
cd $CURR_PATH/src/main/go/juicity
go mod download -x