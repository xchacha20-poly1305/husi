#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $PWD/build/golang
cd $golang

curl -Lso go.tar.gz https://go.dev/dl/go1.21.4.linux-amd64.tar.gz
echo "73cac0215254d0c7d1241fa40837851f3b9a8a742d0b54714cbdfb3feaf8f0af go.tar.gz" | sha256sum -c -
tar xzf go.tar.gz

go version
go env