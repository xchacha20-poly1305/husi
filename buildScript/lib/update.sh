#!/bin/bash

set -e

UPDATE_CMD="git submodule update --remote"

$UPDATE_CMD

pushd sing-box-extra/
$UPDATE_CMD
popd

pushd metacubexd/
$UPDATE_CMD
popd
