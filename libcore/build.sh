#!/usr/bin/env bash

set -e
# set -x

get_go_bin_version() {
    local bin_path="$1"
    go version -m "$bin_path" | awk '/^[ \t]*mod/ {print $3}' || echo "unknown"
}

# Returns 1 if same
compare_version() {
  local bin_path="$1"
  local expected_version="$2"
  local actual_version=$(get_go_bin_version "$bin_path")
  if [ "$actual_version" == "$expected_version" ]; then
    echo "1"
  else
    echo "0"
  fi
}

TAGS=(
    "with_conntrack"
    "with_gvisor"
    "with_quic"
    "with_wireguard"
    "with_utls"
)

IFS="," BUILD_TAGS="${TAGS[*]}"

# Just install gomobile & gobind if not have or version not same
GOMOBILE_VERSION="v0.1.6"
if [ "$(compare_version "$(command -v gomobile)" "$GOMOBILE_VERSION")" == "0" ]; then
    echo ">> Installing gomobile"
    CGO_ENABLED=0 go install -v -trimpath -ldflags="-w -s" github.com/sagernet/gomobile/cmd/gomobile@$GOMOBILE_VERSION
fi
if [ "$(compare_version "$(command -v gobind)" "$GOMOBILE_VERSION")" == "0" ]; then
    echo ">> Installing gobind"
    CGO_ENABLED=0 go install -v -trimpath -ldflags="-w -s" github.com/sagernet/gomobile/cmd/gobind@$GOMOBILE_VERSION
fi

box_version=$(go run ./cmd/boxversion/)
export CGO_ENABLED=1
export GO386=softfloat

# -buildvcs require: https://github.com/SagerNet/gomobile/commit/6bc27c2027e816ac1779bf80058b1a7710dad260
GOEXPERIMENT="synchashtriemap" gomobile bind -v -androidapi 21 -trimpath -buildvcs=false \
    -ldflags="-X github.com/sagernet/sing-box/constant.Version=${box_version} -s -w -buildid=" \
    -tags="$BUILD_TAGS" . || exit 1

rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> Installed $(realpath $proj)/libcore.aar"
sha256sum libcore.aar
