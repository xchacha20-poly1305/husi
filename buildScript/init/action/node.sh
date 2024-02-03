#!/bin/bash

set -e

cd $PWD/build

curl -Lo node.tar.xz https://nodejs.org/dist/latest/node-v21.6.0-linux-x64.tar.xz

echo "d940589762748bdbfc1a39132d27a16455b9d283ac3d8a84c3415005269effe4 node.tar.xz" | sha256sum -c -

rm -rf node
mkdir -p node
tar xf node.tar.xz --strip-components=1 -C node

rm node.tar.xz

npm install -g bun

node -v
bun -v
