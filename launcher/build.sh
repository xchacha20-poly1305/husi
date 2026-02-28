#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
METADATA_FILE="$ROOT_DIR/husi.properties"
LAUNCHER_SOURCE="$SCRIPT_DIR/husi-launcher.c"

OUTPUT_PATH=""
PACKAGE_NAME=""
CC_BIN="${CC:-cc}"

usage() {
    cat <<EOF
Usage:
  $(basename "$0") [--output <file>] [--package-name <name>] [--cc <compiler>]

Description:
  Build native Linux launcher binary for current host.
  The launcher is linked statically by default and fails directly on static link errors.

Environment:
  CC            C compiler command (default: cc)
EOF
}

require_arg() {
    local option="$1"
    local value="${2:-}"
    if [[ -z "$value" ]]; then
        echo "[launcher-build] Missing value for $option." >&2
        usage
        exit 1
    fi
}

load_package_name() {
    if [[ -n "$PACKAGE_NAME" ]]; then
        return
    fi
    if [[ ! -f "$METADATA_FILE" ]]; then
        echo "[launcher-build] Metadata file not found: $METADATA_FILE" >&2
        exit 1
    fi
    PACKAGE_NAME="$(awk -F= '$1=="PACKAGE_NAME"{print $2; exit}' "$METADATA_FILE")"
    if [[ -z "$PACKAGE_NAME" ]]; then
        echo "[launcher-build] Failed to parse PACKAGE_NAME from $METADATA_FILE" >&2
        exit 1
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -o|--output)
            require_arg "$1" "${2:-}"
            OUTPUT_PATH="$2"
            shift 2
            ;;
        --package-name)
            require_arg "$1" "${2:-}"
            PACKAGE_NAME="$2"
            shift 2
            ;;
        --cc)
            require_arg "$1" "${2:-}"
            CC_BIN="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "[launcher-build] Unknown argument: $1" >&2
            usage
            exit 1
            ;;
    esac
done

if [[ ! -f "$LAUNCHER_SOURCE" ]]; then
    echo "[launcher-build] Launcher source not found: $LAUNCHER_SOURCE" >&2
    exit 1
fi

if ! command -v "$CC_BIN" >/dev/null 2>&1; then
    echo "[launcher-build] C compiler '$CC_BIN' not found in PATH." >&2
    exit 1
fi

load_package_name

if [[ -z "$OUTPUT_PATH" ]]; then
    OUTPUT_PATH="$SCRIPT_DIR/build/${PACKAGE_NAME}-launcher-bin-$(uname -m)"
fi

mkdir -p "$(dirname "$OUTPUT_PATH")"

if ! "$CC_BIN" \
    -std=c11 \
    -Os \
    -Wall \
    -Wextra \
    -D_GNU_SOURCE \
    -static \
    -s \
    -DHUSI_PACKAGE_NAME="\"$PACKAGE_NAME\"" \
    "$LAUNCHER_SOURCE" \
    -o "$OUTPUT_PATH"; then
    echo "[launcher-build] Static link failed. Please install static libc development files for your toolchain." >&2
    exit 1
fi

chmod 755 "$OUTPUT_PATH"
echo "[launcher-build] Built: $OUTPUT_PATH"
