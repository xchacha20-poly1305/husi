#!/usr/bin/env bash

set -euo pipefail

GIT_DEPTH="${GIT_DEPTH:-1}"

git submodule update --init --recursive --depth="$GIT_DEPTH"

git submodule foreach --recursive git submodule update --init --depth="$GIT_DEPTH"
