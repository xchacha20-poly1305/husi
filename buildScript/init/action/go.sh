#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $PWD/build/golang
cd $golang

curl -Lso go.tar.gz https://go.dev/dl/go1.22.1.linux-amd64.tar.gz
echo "aab8e15785c997ae20f9c88422ee35d962c4562212bb0f879d052a35c8307c7f go.tar.gz" | sha256sum -c -
tar xzf go.tar.gz

go version
go env

