#!/bin/bash

set -e

cd $PWD/build

curl -Lo node.tar.xz https://nodejs.org/dist/latest/node-v21.7.1-linux-x64.tar.xz

echo "cb25d7a4aa57d15f280ce45cd72f95e9d2020702b7ca75c7fe632444f7c0452c node.tar.xz" | sha256sum -c -

rm -rf node
mkdir -p node
tar xf node.tar.xz --strip-components=1 -C node

rm node.tar.xz

npm install -g bun

node -v
bun -v
