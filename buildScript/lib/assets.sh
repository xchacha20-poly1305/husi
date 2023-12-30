#!/bin/bash

set -e

DIR=app/src/main/assets/sing-box
rm -rf $DIR
mkdir -p $DIR
cd $DIR

get_latest_release() {
  curl --silent "https://api.github.com/repos/$1/releases/latest" | # Get latest release from GitHub api
    grep '"tag_name":' |                                            # Get tag line
    sed -E 's/.*"([^"]+)".*/\1/'                                    # Pluck JSON value
}

get_rule_set() {
  local type=$1 # example: geoip
  local zip_name="${type}.zip"
  local rule_set_name="sing-${type}-rule-set"

  unzip -q $zip_name
  rm $zip_name
  mv $rule_set_name $type
  zip -r -q -X $zip_name $type -9
  rm -rf $type
}

####
# VERSION_GEOIP=`get_latest_release "SagerNet/sing-geoip"`
VERSION_GEOIP=$(date +%Y%m%d)
echo VERSION_GEOIP=$VERSION_GEOIP
echo -n $VERSION_GEOIP >geoip.version.txt
curl -fLSso geoip.zip https://codeload.github.com/SagerNet/sing-geoip/zip/refs/heads/rule-set
get_rule_set geoip
# xz -9 geoip.db

####
VERSION_GEOSITE=$(date "+%Y%m%d")
echo VERSION_GEOSITE=$VERSION_GEOSITE
echo -n $VERSION_GEOSITE >geosite.version.txt
curl -fLSso geosite.zip https://codeload.github.com/SagerNet/sing-geosite/zip/refs/heads/rule-set
get_rule_set geosite
# xz -9 geosite.db
