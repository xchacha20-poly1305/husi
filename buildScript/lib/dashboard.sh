#!/bin/bash

set -e

DIST_NAME="Dash-metacubexd"

bun -v

rm -rf ./${DIST_NAME}

pushd metacubexd/
bun install
bun run build
mv dist ../${DIST_NAME}
popd

zip -r -X app/src/main/assets/dashboard.zip ./${DIST_NAME} -9
VERSION_DASH=$(date +%Y%m%d)
echo -n "$VERSION_DASH" >app/src/main/assets/dashboard.version.txt

echo ">> install ${DIST_NAME} to  app/src/main/assets/dashboard.zip"
sha256sum app/src/main/assets/dashboard.zip
