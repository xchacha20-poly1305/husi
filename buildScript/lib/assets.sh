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

function pack() {
  local name=$1
  tar --mtime='1970-01-01' -czf "${name}.tar.gz" "ruleset/$name"
}

echo "GEOIP: $GEOIP_VERSION"
echo "GEOSITE: $GEOSITE_VERSION"
pushd $GENERATER
rm -rf ruleset || true
rm *.tar.gz || true
go run . -geoip=$GEOIP_VERSION -geosite=$GEOSITE_VERSION
pack "geoip"
pack "geosite"
popd

cp -r "$GENERATER/geoip.tar.gz" "$DIR"
cp -r "$GENERATER/geosite.tar.gz" "$DIR"

cd $DIR
echo -n $GEOIP_VERSION >geoip.version.txt
echo -n $GEOSITE_VERSION >geosite.version.txt
