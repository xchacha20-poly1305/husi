#!/usr/bin/env bash
#
# Download the JetBrains Runtime SDK pinned by JBR_RELEASE for one target.
#
# JBR is the only JDK this project uses: it builds everything, and its modules
# are what jlink links into the bundled runtimes (Linux AppImage, Windows -jbr
# packages). Keeping one vendor means the runtime users get is the one CI built
# and tested with.
#
# By default only the jmods directory is unpacked: it is all jlink reads, and
# the rest of a foreign-platform SDK is of no use on the build host. --sdk
# unpacks the whole SDK instead, for use as the build JDK, and prints its
# JAVA_HOME. The resolved path is printed to stdout so callers can capture it;
# everything else goes to stderr.
#
# Usage: ./run lib jbr <platform/arch> [--sdk]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# shellcheck source=../init/version.sh
source "$ROOT_DIR/buildScript/init/version.sh"

readonly JBR_BASE_URL="https://cache-redirector.jetbrains.com/intellij-jbr"
# Versioned so that bumping JBR_RELEASE never reuses an older unpacked copy.
readonly INSTALL_ROOT="$ROOT_DIR/build/jbr/$JBR_RELEASE"

log() {
    echo "[jbr] $*" >&2
}

error() {
    echo "[jbr] $*" >&2
}

usage() {
    cat >&2 <<USAGE
Usage:
  $(basename "$0") <platform/arch> [--sdk]

Targets: linux/amd64, linux/arm64, windows/amd64, windows/arm64,
         darwin/amd64 and darwin/arm64 (--sdk only)

Without --sdk, prints the jmods directory; with --sdk, prints JAVA_HOME.
USAGE
}

want_sdk=false
target=""
for argument in "$@"; do
    case "$argument" in
        --sdk)
            want_sdk=true
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            if [[ -n "$target" ]]; then
                usage
                exit 1
            fi
            target="$argument"
            ;;
    esac
done

if [[ -z "$target" ]]; then
    usage
    exit 1
fi

platform="${target%%/*}"
arch="${target#*/}"

# java_home_subdir: where JAVA_HOME sits inside the unpacked archive.
case "$platform" in
    linux | windows)
        jbr_platform="$platform"
        java_home_subdir=""
        ;;
    darwin | macos | osx)
        platform="darwin"
        jbr_platform="osx"
        java_home_subdir="Contents/Home"
        if [[ "$want_sdk" != true ]]; then
            error "No macOS package bundles a runtime, so darwin modules are only fetched with --sdk."
            exit 1
        fi
        ;;
    *)
        error "Unsupported platform '$platform'. Use linux, windows or darwin."
        exit 1
        ;;
esac

case "$arch" in
    amd64 | x86_64 | x64)
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

archive_name="jbrsdk-${JBR_VERSION}-${jbr_platform}-${jbr_arch}-b${JBR_BUILD}.tar.gz"
if [[ "$want_sdk" == true ]]; then
    install_dir="$INSTALL_ROOT/sdk/${platform}_${arch}"
    result_dir="$install_dir${java_home_subdir:+/$java_home_subdir}"
    # Every JDK image carries a release file at JAVA_HOME, on any platform.
    marker="$result_dir/release"
else
    install_dir="$INSTALL_ROOT/jmods/${platform}_${arch}"
    result_dir="$install_dir/jmods"
    marker="$result_dir/java.base.jmod"
fi

if [[ -e "$marker" ]]; then
    log "Already present: $result_dir"
    echo "$result_dir"
    exit 0
fi

log "Fetching $archive_name"
rm -rf "$install_dir"
mkdir -p "$install_dir"
# The tarball is a few hundred megabytes, so it is streamed rather than written
# to disk first.
if [[ "$want_sdk" == true ]]; then
    extract=(tar -C "$install_dir" --strip-components=1 -xzf -)
else
    extract=(tar -C "$install_dir" --strip-components=1 --wildcards -xzf - '*/jmods/*')
fi
if ! curl -sSfL "$JBR_BASE_URL/$archive_name" | "${extract[@]}"; then
    rm -rf "$install_dir"
    error "Failed to fetch or unpack $archive_name"
    exit 1
fi

if [[ ! -e "$marker" ]]; then
    rm -rf "$install_dir"
    error "$archive_name did not contain ${marker#"$install_dir"/}."
    exit 1
fi

log "Unpacked: $result_dir"
echo "$result_dir"
