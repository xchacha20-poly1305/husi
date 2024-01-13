#!/bin/bash
set -e

cd $PWD/build

curl -Lso zig.tar.xz https://ziglang.org/download/0.11.0/zig-linux-x86_64-0.11.0.tar.xz

rm -rf zig
mkdir -p zig
tar xf zig.tar.xz -strip-components=1 -C zig
rm zig.tar.xz

zig env
zig version