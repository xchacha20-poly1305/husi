#!/usr/bin/env bash

source "buildScript/init/env.sh"
source "buildScript/plugin/hysteria2/build.sh"

git reset HEAD --hard
git clean -fdx