#!/usr/bin/env bash

buildScript/plugin/juicity/init.sh &&  set -x &&
  buildScript/plugin/juicity/armeabi-v7a.sh &&
  buildScript/plugin/juicity/arm64-v8a.sh &&
  buildScript/plugin/juicity/x86.sh &&
  buildScript/plugin/juicity/x86_64.sh