#!/bin/bash

set -e

source buildScript/init/version.sh

DIR="app/src/main/assets/sing-box"
GENERATER="libcore/cmd/ruleset_generate"
rm -rf $DIR
mkdir -p $DIR

# get_latest_release() {
#   curl --silent "https://api.github.com/repos/$1/releases/latest" | # Get latest release from GitHub api
#     grep '"tag_name":' |                                            # Get tag line
#     sed -E 's/.*"([^"]+)".*/\1/'                                    # Pluck JSON value
# }

function pack() {
  local name=$1
  zip -r -q -X "${name}.zip" "ruleset/$name"
}

echo "GEOIP: $GEOIP_VERSION"
echo "GEOSITE: $GEOSITE_VERSION"
pushd $GENERATER
rm -rf ruleset
rm *.zip
go run . -geoip=$GEOIP_VERSION -geosite=$GEOSITE_VERSION || exit 1
pack "geoip"
pack "geosite"
popd

cp -r "$GENERATER/geoip.zip" "$DIR"
cp -r "$GENERATER/geosite.zip" "$DIR"

cd $DIR
echo -n $GEOIP_VERSION >geoip.version.txt
echo -n $GEOSITE_VERSION >geosite.version.txt
