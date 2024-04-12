#!/usr/bin/env bash

buildScript/plugin/hysteria2/init.sh &&  set -x &&
  buildScript/plugin/hysteria2/armeabi-v7a.sh &&
  buildScript/plugin/hysteria2/arm64-v8a.sh &&
  buildScript/plugin/hysteria2/x86.sh &&
  buildScript/plugin/hysteria2/x86_64.sh