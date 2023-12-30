#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $PWD/build/golang
cd $golang

curl -Lso go.tar.gz https://go.dev/dl/go1.21.5.linux-amd64.tar.gz
echo "e2bc0b3e4b64111ec117295c088bde5f00eeed1567999ff77bc859d7df70078e go.tar.gz" | sha256sum -c -
tar xzf go.tar.gz

go version
go env