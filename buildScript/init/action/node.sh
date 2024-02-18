#!/bin/bash

set -e

cd $PWD/build

curl -Lo node.tar.xz https://nodejs.org/dist/latest/node-v21.6.2-linux-x64.tar.xz

echo "593dd28f5c78d797e76b730937b95fcdfc594f053a8756b1d0860a4555bed58e node.tar.xz" | sha256sum -c -

rm -rf node
mkdir -p node
tar xf node.tar.xz --strip-components=1 -C node

rm node.tar.xz

npm install -g bun

node -v
bun -v
