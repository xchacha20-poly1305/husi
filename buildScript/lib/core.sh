#!/usr/bin/env bash

set -euo pipefail

for argument in "$@"; do
  if [ "$argument" == "--android" ]; then
    source buildScript/init/env.sh
    break
  fi
done

caller_pwd="$PWD"

# Plain prefixing instead of `realpath -m`: the BSD realpath on macOS has no -m.
absolute_path() {
  if [[ "$1" == /* ]]; then
    echo "$1"
  else
    echo "$caller_pwd/$1"
  fi
}

args=()
while [ "$#" -gt 0 ]; do
  case "$1" in
  --darwinsdk)
    value="${2:-}"
    if [ -n "$value" ]; then
      value="$(absolute_path "$value")"
    fi
    args+=("$1" "$value")
    shift 2
    ;;
  --darwinsdk=*)
    value="$(absolute_path "${1#*=}")"
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
# The `+` guard keeps bash 3.2 (macOS) from rejecting an empty array under `set -u`.
./build.sh ${args[@]+"${args[@]}"}
