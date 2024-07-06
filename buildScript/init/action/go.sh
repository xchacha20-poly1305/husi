#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $PWD/build/golang
cd $golang

curl -Lso go.tar.gz https://go.dev/dl/go1.22.4.linux-amd64.tar.gz
echo "ba79d4526102575196273416239cca418a651e049c2b099f3159db85e7bade7d go.tar.gz" | sha256sum -c -
tar xzf go.tar.gz

go version
go env
