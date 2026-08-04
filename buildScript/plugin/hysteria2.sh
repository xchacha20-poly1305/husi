#!/usr/bin/env bash

set -euo pipefail

PLUGIN=hysteria2
GO_SOURCE_DIR="src/main/go/hysteria2"
GO_MAIN_PKG="./app"
GO_EXTRA_LDFLAGS="-checklinkname=0"

source "buildScript/plugin/common.sh"

dispatch "$@"
