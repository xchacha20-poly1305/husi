#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $PWD/build/golang
cd $golang

curl -Lso go.tar.gz https://go.dev/dl/go1.22.5.linux-amd64.tar.gz
echo "904b924d435eaea086515bc63235b192ea441bd8c9b198c507e85009e6e4c7f0 go.tar.gz" | sha256sum -c -
tar xzf go.tar.gz

go version
go env
