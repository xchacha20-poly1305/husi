#!/bin/bash

set -e

RESET_GIT() {
	local BRANCH=$1
	git checkout $BRANCH
	git reset --hard origin/$BRANCH
}

git submodule update --remote

pushd libcore/

pushd sing-box/
RESET_GIT def
popd

pushd sing-quic
RESET_GIT dev
popd

go mod tidy

popd
