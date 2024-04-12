#!/bin/bash

set -e

cd $PWD/build

curl -Lo node.tar.xz https://nodejs.org/dist/latest/node-v21.7.3-linux-x64.tar.xz

echo "19e17a77e59044de169cd19be3f3bccae686982fba022f9634421b44724ee90c node.tar.xz" | sha256sum -c -

rm -rf node
mkdir -p node
tar xf node.tar.xz --strip-components=1 -C node

rm node.tar.xz

npm install -g bun

node -v
bun -v
