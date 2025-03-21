#!/usr/bin/env bash

source buildScript/init/env.sh

cd libcore
./build.sh || exit 1
