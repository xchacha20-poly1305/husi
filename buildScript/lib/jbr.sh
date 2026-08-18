#!/usr/bin/env bash
#
# Download the JetBrains Runtime SDK modules for one desktop target.
#
# Only the jmods directory is unpacked: it is all jlink reads, and the rest of
# the SDK is a Windows tree that is of no use on the build host. The resolved
# jmods path is printed to stdout so the Makefile can capture it; everything
# else goes to stderr.
#
# Usage: ./run lib jbr windows/amd64

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# shellcheck source=../init/version.sh
source "$ROOT_DIR/buildScript/init/version.sh"

readonly JBR_BASE_URL="https://cache-redirector.jetbrains.com/intellij-jbr"

log() {
    echo "[jbr] $*" >&2
}

error() {
    echo "[jbr] $*" >&2
}

usage() {
    cat >&2 <<USAGE
Usage:
  $(basename "$0") <platform/arch>

Supported targets: windows/amd64, windows/arm64
USAGE
}

if [[ $# -ne 1 ]]; then
    usage
    exit 1
fi

target="$1"
platform="${target%%/*}"
arch="${target#*/}"

if [[ "$platform" != "windows" ]]; then
    error "Unsupported platform '$platform'. Only Windows bundles a JetBrains Runtime today."
    exit 1
fi

case "$arch" in
    amd64 | x86_64)
        arch="amd64"
        jbr_arch="x64"
        ;;
    arm64 | aarch64)
        arch="arm64"
        jbr_arch="aarch64"
        ;;
    *)
        error "Unsupported arch '$arch'. Use amd64 or arm64."
        exit 1
        ;;
esac

archive_name="jbrsdk-${JBR_VERSION}-${platform}-${jbr_arch}-b${JBR_BUILD}.tar.gz"
install_dir="$ROOT_DIR/build/jbr/${platform}_${arch}"
jmods_dir="$install_dir/jmods"

if [[ -d "$jmods_dir" ]]; then
    log "Already present: $jmods_dir"
    echo "$jmods_dir"
    exit 0
fi

log "Fetching $archive_name"
rm -rf "$install_dir"
mkdir -p "$install_dir"
# The tarball is a few hundred megabytes and jmods is a fraction of it, so it is
# streamed rather than written to disk first.
if ! curl -sSfL "$JBR_BASE_URL/$archive_name" \
    | tar -C "$install_dir" --strip-components=1 --wildcards -xzf - '*/jmods/*'; then
    rm -rf "$install_dir"
    error "Failed to fetch or unpack $archive_name"
    exit 1
fi

if [[ ! -d "$jmods_dir" ]]; then
    rm -rf "$install_dir"
    error "$archive_name carried no jmods directory."
    exit 1
fi

log "Unpacked modules: $jmods_dir"
echo "$jmods_dir"
