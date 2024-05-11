#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $PWD/build/golang
cd $golang

curl -Lso go.tar.gz https://go.dev/dl/go1.22.3.linux-amd64.tar.gz
echo "8920ea521bad8f6b7bc377b4824982e011c19af27df88a815e3586ea895f1b36 go.tar.gz" | sha256sum -c -
tar xzf go.tar.gz

go version
go env

