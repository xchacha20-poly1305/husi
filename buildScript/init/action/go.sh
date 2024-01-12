#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $PWD/build/golang
cd $golang

curl -Lso go.tar.gz https://go.dev/dl/go1.21.6.linux-amd64.tar.gz
echo "3f934f40ac360b9c01f616a9aa1796d227d8b0328bf64cb045c7b8c4ee9caea4 go.tar.gz" | sha256sum -c -
tar xzf go.tar.gz

go version
go env