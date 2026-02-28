#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
METADATA_FILE="$ROOT_DIR/husi.properties"
LAUNCHER_SOURCE="$SCRIPT_DIR/husi-launcher.c"

OUTPUT_PATH=""
PACKAGE_NAME=""
CC_BIN="${CC:-cc}"
TARGET=""
TARGET_PLATFORM=""
TARGET_ARCH=""
TARGET_MACHINE=""

usage() {
    cat <<EOF
Usage:
  $(basename "$0") [--output <file>] [--package-name <name>] [--target <platform/arch>] [--cc <compiler>]

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

normalize_platform() {
    local value
    value="$(echo "$1" | tr '[:upper:]' '[:lower:]')"
    case "$value" in
        linux)
            echo "linux"
            ;;
        *)
            echo "[launcher-build] Unsupported platform '$1'. Use linux." >&2
            exit 1
            ;;
    esac
}

normalize_arch() {
    local value
    value="$(echo "$1" | tr '[:upper:]' '[:lower:]')"
    case "$value" in
        amd64|x86_64)
            echo "amd64"
            ;;
        arm64|aarch64)
            echo "arm64"
            ;;
        *)
            echo "[launcher-build] Unsupported arch '$1'. Use amd64 or arm64." >&2
            exit 1
            ;;
    esac
}

resolve_target() {
    if [[ -n "$TARGET" ]]; then
        local raw_platform="${TARGET%%/*}"
        local raw_arch="${TARGET#*/}"
        if [[ "$raw_platform" == "$raw_arch" ]]; then
            echo "[launcher-build] Invalid --target '$TARGET'. Use <platform>/<arch>, e.g. linux/amd64." >&2
            exit 1
        fi
        TARGET_PLATFORM="$(normalize_platform "$raw_platform")"
        TARGET_ARCH="$(normalize_arch "$raw_arch")"
    else
        TARGET_PLATFORM="linux"
        TARGET_ARCH="$(normalize_arch "$(uname -m)")"
    fi

    case "$TARGET_ARCH" in
        amd64)
            TARGET_MACHINE="x86_64"
            ;;
        arm64)
            TARGET_MACHINE="aarch64"
            ;;
    esac
}

resolve_default_compiler() {
    local host_arch
    host_arch="$(normalize_arch "$(uname -m)")"
    if [[ "$TARGET_ARCH" == "$host_arch" ]]; then
        return
    fi

    if [[ "$CC_BIN" != "cc" ]]; then
        return
    fi

    local cross_cc=""
    case "$TARGET_ARCH" in
        amd64)
            cross_cc="x86_64-linux-gnu-gcc"
            ;;
        arm64)
            cross_cc="aarch64-linux-gnu-gcc"
            ;;
    esac

    if command -v "$cross_cc" >/dev/null 2>&1; then
        CC_BIN="$cross_cc"
        return
    fi

    echo "[launcher-build] Cross compiler '$cross_cc' not found for target '$TARGET_PLATFORM/$TARGET_ARCH'." >&2
    echo "[launcher-build] Set CC/--cc explicitly or install the cross compiler." >&2
    exit 1
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
        --target)
            require_arg "$1" "${2:-}"
            TARGET="$2"
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
resolve_target
resolve_default_compiler

if [[ -z "$OUTPUT_PATH" ]]; then
    OUTPUT_PATH="$SCRIPT_DIR/build/${PACKAGE_NAME}-launcher-bin-${TARGET_MACHINE}"
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
