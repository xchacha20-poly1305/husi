#!/usr/bin/env bash

set -euo pipefail

for argument in "$@"; do
  if [ "$argument" == "--android" ]; then
    source buildScript/init/env.sh
    break
  fi
done

caller_pwd="$PWD"
args=()
while [ "$#" -gt 0 ]; do
  case "$1" in
  --jniinclude | --darwinsdk)
    value="${2:-}"
    if [ -n "$value" ] && [[ "$value" != /* ]]; then
      value="$(realpath -m "$caller_pwd/$value")"
    fi
    args+=("$1" "$value")
    shift 2
    ;;
  --jniinclude=* | --darwinsdk=*)
    value="${1#*=}"
    if [[ "$value" != /* ]]; then
      value="$(realpath -m "$caller_pwd/$value")"
    fi
    args+=("${1%%=*}=$value")
    shift
    ;;
  *)
    args+=("$1")
    shift
    ;;
  esac
done

cd libcore
./build.sh "${args[@]}"
