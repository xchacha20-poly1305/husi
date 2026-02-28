#!/bin/sh
set -e

if [ "$1" = "remove" ] || [ "$1" = "purge" ]; then
    if [ -e '__HUSI_LAUNCHER_PATH__' ]; then
        setcap -r '__HUSI_LAUNCHER_PATH__' || true
    fi
fi
