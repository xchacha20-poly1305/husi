#!/usr/bin/env bash

set -e

if [ -z "$HUSI_ZIG_TARGET" ]; then
    echo "HUSI_ZIG_TARGET is required" >&2
    exit 1
fi

arguments=()
library_dir=""
for argument in "$@"; do
    case "$argument" in
    -L*)
        library_dir="${argument#-L}"
        arguments+=("$argument")
        ;;
    -l:libcronet.a)
        if [ -z "$library_dir" ]; then
            echo "Missing library path for libcronet.a" >&2
            exit 1
        fi
        # Zig's driver does not accept GNU ld's exact-library spelling.
        arguments+=("$library_dir/libcronet.a")
        ;;
    *)
        arguments+=("$argument")
        ;;
    esac
done

exec zig cc -target "$HUSI_ZIG_TARGET" "${arguments[@]}"
