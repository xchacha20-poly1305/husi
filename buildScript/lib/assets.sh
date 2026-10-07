#!/usr/bin/env bash

set -euo pipefail

source buildScript/init/version.sh

DIR="composeApp/src/commonMain/composeResources/files/sing-box"
GENERATER="libcore/cmd/internal/ruleset_generate"
rm -rf "$DIR"
mkdir -p "$DIR"

echo "GEOIP: $GEOIP_VERSION"
echo "GEOSITE: $GEOSITE_VERSION"
pushd "$GENERATER"
go run . -geoip="$GEOIP_VERSION" -geosite="$GEOSITE_VERSION" -so="geosite.tar.gz" -io="geoip.tar.gz"
popd

cp "$GENERATER/geoip.tar.gz" "$DIR"
cp "$GENERATER/geosite.tar.gz" "$DIR"
sha256sum "$DIR"/*.tar.gz

cd "$DIR"
echo -n "$GEOIP_VERSION" >geoip.version.txt
echo -n "$GEOSITE_VERSION" >geosite.version.txt
