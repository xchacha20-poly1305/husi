#!/usr/bin/env bash

buildScript/plugin/shadowquic/init.sh &&
  buildScript/plugin/shadowquic/armeabi-v7a.sh &&
  buildScript/plugin/shadowquic/arm64-v8a.sh &&
  buildScript/plugin/shadowquic/x86.sh &&
  buildScript/plugin/shadowquic/x86_64.sh
