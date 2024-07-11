#!/usr/bin/env bash

buildScript/plugin/naive/init.sh && set -x &&
  buildScript/plugin/naive/armeabi-v7a.sh &&
  buildScript/plugin/naive/arm64-v8a.sh &&
  buildScript/plugin/naive/x86.sh &&
  buildScript/plugin/naive/x86_64.sh

