#!/bin/bash

set -e

source buildScript/init/version.sh

DIR="app/src/main/assets/sing-box"
GENERATER="libcore/cmd/ruleset_generate"
rm -rf $DIR || true
mkdir -p $DIR

# get_latest_release() {
#   curl --silent "https://api.github.com/repos/$1/releases/latest" | # Get latest release from GitHub api
#     grep '"tag_name":' |                                            # Get tag line
#     sed -E 's/.*"([^"]+)".*/\1/'                                    # Pluck JSON value
# }

echo "GEOIP: $GEOIP_VERSION"
echo "GEOSITE: $GEOSITE_VERSION"
pushd $GENERATER
go run . -geoip=$GEOIP_VERSION -geosite=$GEOSITE_VERSION -so="geosite.tar.zst" -io="geoip.tar.zst"
popd

cp -r "$GENERATER/geoip.tar.zst" "$DIR"
cp -r "$GENERATER/geosite.tar.zst" "$DIR"
sha256sum $DIR/*.tar.zst

cd $DIR
echo -n $GEOIP_VERSION >geoip.version.txt
echo -n $GEOSITE_VERSION >geosite.version.txt
