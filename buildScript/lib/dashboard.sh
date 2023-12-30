#!/bin/bash

set -e

DEST_NAME="Dash-metacubexd"

pnpm -v

rm -rf ./${DEST_NAME}

pushd metacubexd/
mkdir -p ../${DEST_NAME}
pnpm install
pnpm build --outDir ../${DEST_NAME} -- --no-cache
popd

zip -r -X app/src/main/assets/dashboard.zip ./${DEST_NAME} -9
VERSION_DASH=$(date +%Y%m%d)
echo -n "$VERSION_DASH" >app/src/main/assets/dashboard.version.txt

echo ">> install ${DEST_NAME} to  app/src/main/assets/dashboard.zip"
sha256sum app/src/main/assets/dashboard.zip
