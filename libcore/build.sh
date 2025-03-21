#!/usr/bin/env bash

set -e
# set -x

TAGS=(
    "with_conntrack"
    "with_gvisor"
    "with_quic"
    "with_wireguard"
    "with_utls"
    "with_ech"
)

IFS="," BUILD_TAGS="${TAGS[*]}"

CGO_ENABLED=0 go install -v -trimpath -ldflags="-w -s" github.com/sagernet/gomobile/cmd/gobind@v0.1.5
CGO_ENABLED=0 go install -v -trimpath -ldflags="-w -s" github.com/sagernet/gomobile/cmd/gomobile@v0.1.5

box_version=$(go run ./cmd/boxversion/)
export CGO_ENABLED=1
export GO386=softfloat

# -buildvcs require: https://github.com/SagerNet/gomobile/commit/6bc27c2027e816ac1779bf80058b1a7710dad260
# max-page-size: https://developer.android.com/guide/practices/page-sizes
GOEXPERIMENT="synchashtriemap" gomobile bind -v -androidapi 21 -trimpath -buildvcs=false \
    -ldflags="-X github.com/sagernet/sing-box/constant.Version=${box_version} -s -w -buildid=" \
    -tags="$BUILD_TAGS" . || exit 1

rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
sha256sum libcore.aar
