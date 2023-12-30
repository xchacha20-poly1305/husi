#!/bin/bash

# Install gomobile
if [ ! -f "$GOPATH/bin/gomobile" ]; then
    go install golang.org/x/mobile/cmd/gomobile@latest
    go install golang.org/x/mobile/cmd/gobind@latest
fi

gomobile init
