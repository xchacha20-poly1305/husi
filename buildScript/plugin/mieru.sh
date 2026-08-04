#!/usr/bin/env bash

set -euo pipefail

PLUGIN=mieru
GO_SOURCE_DIR="src/main/go/mieru"
GO_MAIN_PKG="./cmd/mieru"

source "buildScript/plugin/common.sh"

dispatch "$@"
