#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $PWD/build/golang
cd $golang

curl -Lso go.tar.gz https://go.dev/dl/go1.22.2.linux-amd64.tar.gz
echo "5901c52b7a78002aeff14a21f93e0f064f74ce1360fce51c6ee68cd471216a17 go.tar.gz" | sha256sum -c -
tar xzf go.tar.gz

go version
go env

