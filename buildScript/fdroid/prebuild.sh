#!/bin/bash

buildScript/lib/source.sh

# Setup go & external library
export golang=$PWD/build/golang
export GOPATH=$golang/gopath
export GOROOT=$golang/go
export PATH=$golang/go/bin:$GOPATH/bin:$PATH
buildScript/init/action/go.sh
buildScript/init/action/gradle.sh

# Build libcore
buildScript/lib/core.sh

# Setup Node.js & Build dashboard
export PATH=$PWD/build/node/bin:$PATH
buildScript/init/action/node.sh
buildScript/lib/dashboard.sh
