#!/bin/bash
set -e

cd $PWD/build

curl -Lso zig.tar.xz https://ziglang.org/download/0.11.0/zig-linux-x86_64-0.11.0.tar.xz

tar xf zig.tar.xz
rm -rf zig
rm zig.tar.xz

mv zig* zig

zig env
zig version