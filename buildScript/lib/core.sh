#!/usr/bin/env bash

build_android=0
for argument in "$@"; do
  if [ "$argument" == "--android" ]; then
    build_android=1
    break
  fi
done

if [ "$build_android" -eq 1 ]; then
  source buildScript/init/env.sh
fi

caller_pwd="$PWD"
declare -a args
while [ "$#" -gt 0 ]; do
  case "$1" in
  --jniinclude)
    if [ -n "$2" ] && [[ "$2" != /* ]]; then
      args+=("$1" "$(realpath -m "$caller_pwd/$2")")
    else
      args+=("$1" "$2")
    fi
    shift 2
    ;;
  --jniinclude=*)
    jni_include="${1#*=}"
    if [[ "$jni_include" != /* ]]; then
      jni_include="$(realpath -m "$caller_pwd/$jni_include")"
    fi
    args+=("--jniinclude=$jni_include")
    shift
    ;;
  --darwinsdk)
    if [ -n "$2" ] && [[ "$2" != /* ]]; then
      args+=("$1" "$(realpath -m "$caller_pwd/$2")")
    else
      args+=("$1" "$2")
    fi
    shift 2
    ;;
  --darwinsdk=*)
    darwin_sdk="${1#*=}"
    if [[ "$darwin_sdk" != /* ]]; then
      darwin_sdk="$(realpath -m "$caller_pwd/$darwin_sdk")"
    fi
    args+=("--darwinsdk=$darwin_sdk")
    shift
    ;;
  *)
    args+=("$1")
    shift
    ;;
  esac
done

cd libcore
./build.sh "${args[@]}" || exit 1
