#!/bin/bash

set -e

RESET_GIT() {
	local BRANCH=$1
	git checkout $BRANCH
	git reset --hard origin/$BRANCH
}

git submodule update --remote

pushd metacubexd
RESET_GIT main
popd
