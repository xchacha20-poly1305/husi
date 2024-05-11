#!/bin/bash

set -e

cd $PWD/build

curl -Lo node.tar.xz https://nodejs.org/dist/latest/node-v22.1.0-linux-x64.tar.xz

echo "22330ad3a1796ac30d75fab6f98cbe2b883239d4e31c2a52ec8f4e6cc52ace54 node.tar.xz" | sha256sum -c -

rm -rf node
mkdir -p node
tar xf node.tar.xz --strip-components=1 -C node

rm node.tar.xz

npm install -g pnpm

node -v
pnpm -v
