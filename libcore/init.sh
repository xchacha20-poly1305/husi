#!/bin/bash
set -e

[[ -e "inited" ]] && exit 0

# Install gomobile
CGO_ENABLED=0 go install -v -trimpath -ldflags="-w -s" github.com/sagernet/gomobile/cmd/gobind@6bc27c2027e816ac1779bf80058b1a7710dad260
CGO_ENABLED=0 go install -v -trimpath -ldflags="-w -s" github.com/sagernet/gomobile/cmd/gomobile@6bc27c2027e816ac1779bf80058b1a7710dad260

gomobile init
touch inited
