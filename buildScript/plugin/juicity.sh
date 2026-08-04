#!/usr/bin/env bash

set -euo pipefail

PLUGIN=juicity
GO_SOURCE_DIR="src/main/go/juicity"
GO_MAIN_PKG="./cmd/client"

source "buildScript/plugin/common.sh"

dispatch "$@"
