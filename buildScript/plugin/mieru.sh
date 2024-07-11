#!/usr/bin/env bash

buildScript/plugin/mieru/init.sh &&  set -x &&
  buildScript/plugin/mieru/armeabi-v7a.sh &&
  buildScript/plugin/mieru/arm64-v8a.sh &&
  buildScript/plugin/mieru/x86.sh &&
  buildScript/plugin/mieru/x86_64.sh