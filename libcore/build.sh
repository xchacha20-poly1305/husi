#!/bin/bash

source ../buildScript/init/env_ndk.sh

set -e
# set -x

box_version=$(go run ./cmd/boxversion/)

TAGS=(
    "with_conntrack"
    "with_gvisor"
    "with_quic"
    "with_wireguard"
    "with_utls"
    "with_clash_api"
    "with_ech"
)

IFS="," BUILD_TAGS="${TAGS[*]}"

# -buildvcs require: https://github.com/SagerNet/gomobile/commit/6bc27c2027e816ac1779bf80058b1a7710dad260
# CGO_FLAGS require Go 1.23: https://go.dev/doc/go1.23#cgo
# max-page-size: https://developer.android.com/guide/practices/page-sizes
CGO_LDFLAGS="-Wl,-z,max-page-size=16384" gomobile bind -v -androidapi 21 -trimpath -buildvcs=false \
    -ldflags="-X github.com/sagernet/sing-box/constant.Version=${box_version} -s -w -buildid=" \
    -tags="$BUILD_TAGS" . || exit 1

rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
sha256sum libcore.aar
