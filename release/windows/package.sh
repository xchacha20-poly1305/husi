#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
METADATA_FILE="$ROOT_DIR/husi.properties"
DESKTOP_METADATA_FILE="$ROOT_DIR/release/desktop/package-metadata.sh"
NSIS_TEMPLATE_FILE="$ROOT_DIR/release/windows/desktop/installer.nsi"
WINDOWS_JAVA_OPTS_FILE="$ROOT_DIR/release/windows/desktop/desktop-java-opts.conf"
JAR_DIR_DEFAULT="$ROOT_DIR/composeApp/build/compose/jars"
OUTPUT_DIR_DEFAULT="$ROOT_DIR/composeApp/build/compose/packages/windows"
TAG_NAME=""
TAG_EPOCH=""
HOST_OS=""
PYTHON_BIN=""
NSIS_BIN=""

log() {
    echo "[package] $*"
}

error() {
    echo "[package] $*" >&2
}

usage() {
    cat <<EOF
Usage:
  $(basename "$0") [--formats zip,nsis] [--target <platform/arch>] [--input-jar <file>] [--launcher-bin <file>] [--output-dir <dir>]
  $(basename "$0") --check-tools [--formats zip,nsis] [--target <platform/arch>]

Description:
  Build Windows portable zip and NSIS installer packages from desktop uber jar.

Defaults:
  --formats      zip,nsis
  --input-jar    newest matching jar under $JAR_DIR_DEFAULT
  --launcher-bin $ROOT_DIR/launcher/zig-out/bin/launcher-windows-<x86_64|aarch64>.exe
  --output-dir   $OUTPUT_DIR_DEFAULT
EOF
}

require_arg() {
    local option="$1"
    local value="${2:-}"
    if [[ -z "$value" ]]; then
        error "Missing value for $option."
        usage
        exit 1
    fi
}

render_template() {
    local template_file="$1"
    local output_file="$2"
    shift 2

    if [[ ! -f "$template_file" ]]; then
        error "Template file not found: $template_file"
        exit 1
    fi

    if [[ $(( $# % 2 )) -ne 0 ]]; then
        error "render_template requires placeholder/value pairs."
        exit 1
    fi

    "$PYTHON_BIN" - "$template_file" "$output_file" "$@" <<'PY'
import pathlib
import sys

template_path = pathlib.Path(sys.argv[1])
output_path = pathlib.Path(sys.argv[2])
pairs = sys.argv[3:]

if len(pairs) % 2 != 0:
    raise SystemExit("render_template requires placeholder/value pairs")

content = template_path.read_text(encoding="utf-8")
for index in range(0, len(pairs), 2):
    content = content.replace(pairs[index], pairs[index + 1])

output_path.write_text(content, encoding="utf-8")
PY
}

source_desktop_metadata() {
    if [[ ! -f "$DESKTOP_METADATA_FILE" ]]; then
        error "Desktop metadata file not found: $DESKTOP_METADATA_FILE"
        exit 1
    fi

    # shellcheck disable=SC1090
    source "$DESKTOP_METADATA_FILE"
}

resolve_python() {
    if command -v python3 >/dev/null 2>&1; then
        PYTHON_BIN="python3"
        return
    fi
    if command -v python >/dev/null 2>&1; then
        PYTHON_BIN="python"
        return
    fi

    error "Missing required tool: python3"
    exit 2
}

load_metadata() {
    if [[ ! -f "$METADATA_FILE" ]]; then
        error "Metadata file not found: $METADATA_FILE"
        exit 1
    fi

    PACKAGE_NAME="$(awk -F= '$1=="PACKAGE_NAME"{print $2; exit}' "$METADATA_FILE")"
    VERSION_NAME="$(awk -F= '$1=="VERSION_NAME"{print $2; exit}' "$METADATA_FILE")"

    if [[ -z "$PACKAGE_NAME" || -z "$VERSION_NAME" ]]; then
        error "Failed to parse PACKAGE_NAME or VERSION_NAME from $METADATA_FILE"
        exit 1
    fi

    source_desktop_metadata
}

resolve_host_os() {
    case "$(uname -s)" in
        Darwin)
            HOST_OS="darwin"
            ;;
        Linux)
            HOST_OS="linux"
            ;;
        MINGW*|MSYS*|CYGWIN*)
            HOST_OS="windows"
            ;;
        *)
            error "Unsupported host OS '$(uname -s)'. Use Linux, macOS or Windows/MSYS."
            exit 1
            ;;
    esac
}

resolve_tag_epoch() {
    local candidate="v$VERSION_NAME"

    if git rev-parse -q --verify "refs/tags/$candidate" >/dev/null 2>&1; then
        TAG_NAME="$candidate"
        TAG_EPOCH="$(git log -1 --format=%ct "refs/tags/$candidate" | tr -d '\r\n')"
    fi

    if [[ -z "$TAG_NAME" || -z "$TAG_EPOCH" ]]; then
        error "No matching tag found for VERSION_NAME=$VERSION_NAME (required: v$VERSION_NAME)."
        exit 1
    fi

    if [[ ! "$TAG_EPOCH" =~ ^[0-9]+$ ]]; then
        error "Invalid tag epoch '$TAG_EPOCH' from tag '$TAG_NAME'."
        exit 1
    fi
}

normalize_platform() {
    local value
    value="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
    case "$value" in
        windows|win)
            echo "windows"
            ;;
        *)
            error "Unsupported platform '$1'. Use windows."
            exit 1
            ;;
    esac
}

normalize_arch() {
    local value
    value="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
    case "$value" in
        amd64|x86_64)
            echo "amd64"
            ;;
        arm64|aarch64)
            echo "arm64"
            ;;
        *)
            error "Unsupported arch '$1'. Use amd64 or arm64."
            exit 1
            ;;
    esac
}

resolve_target() {
    if [[ -n "$TARGET" ]]; then
        local raw_platform="${TARGET%%/*}"
        local raw_arch="${TARGET#*/}"
        if [[ "$raw_platform" == "$raw_arch" ]]; then
            error "Invalid --target '$TARGET'. Use <platform>/<arch>, e.g. windows/amd64."
            exit 1
        fi
        TARGET_PLATFORM="$(normalize_platform "$raw_platform")"
        TARGET_ARCH="$(normalize_arch "$raw_arch")"
        return
    fi

    error "Windows packaging requires --target <platform/arch>, e.g. windows/amd64."
    exit 1
}

resolve_arch() {
    case "$TARGET_ARCH" in
        amd64)
            JAR_ARCH="x64"
            LAUNCHER_MACHINE="x86_64"
            ;;
        arm64)
            JAR_ARCH="arm64"
            LAUNCHER_MACHINE="aarch64"
            ;;
        *)
            error "Unsupported architecture '$TARGET_ARCH'."
            exit 1
            ;;
    esac
}

resolve_formats() {
    local value="$1"
    local item
    declare -gA ENABLED_FORMATS=()

    IFS=',' read -r -a items <<<"$value"
    for item in "${items[@]}"; do
        item="$(printf '%s' "$item" | tr '[:upper:]' '[:lower:]' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
        case "$item" in
            zip)
                ENABLED_FORMATS["zip"]=1
                ;;
            nsis|installer)
                ENABLED_FORMATS["nsis"]=1
                ;;
            "")
                ;;
            *)
                error "Unknown format '$item'. Use zip,nsis."
                exit 1
                ;;
        esac
    done

    if [[ "${#ENABLED_FORMATS[@]}" -eq 0 ]]; then
        error "No valid formats selected."
        exit 1
    fi
}

resolve_nsis() {
    if [[ -z "${ENABLED_FORMATS[nsis]:-}" ]]; then
        return
    fi

    if command -v makensis >/dev/null 2>&1; then
        NSIS_BIN="makensis"
        return
    fi

    error "Missing required tool: makensis (NSIS). Install nsis package."
    exit 2
}

require_tools() {
    local -a tools=(awk sed cp mkdir mktemp git sort head rm "$PYTHON_BIN")
    local -a missing=()
    local tool

    for tool in "${tools[@]}"; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            missing+=("$tool")
        fi
    done

    if [[ "${#missing[@]}" -gt 0 ]]; then
        error "Missing required tools: ${missing[*]}"
        exit 2
    fi
}

touch_path() {
    local path="$1"
    "$PYTHON_BIN" - "$TAG_EPOCH" "$path" <<'PY'
import os
import sys

epoch = int(sys.argv[1])
path = sys.argv[2]
os.utime(path, (epoch, epoch))
PY
}

touch_path_tree() {
    local root="$1"
    "$PYTHON_BIN" - "$TAG_EPOCH" "$root" <<'PY'
import os
import sys

epoch = int(sys.argv[1])
root = sys.argv[2]

for current, dir_names, file_names in os.walk(root):
    os.utime(current, (epoch, epoch))
    dir_names.sort()
    file_names.sort()
    for file_name in file_names:
        os.utime(os.path.join(current, file_name), (epoch, epoch))
PY
}

normalize_vi_version() {
    "$PYTHON_BIN" - "$VERSION_NAME" <<'PY'
import re
import sys

parts = [int(part) for part in re.findall(r"\d+", sys.argv[1])]
if not parts:
    raise SystemExit("invalid")
while len(parts) < 4:
    parts.append(0)

print(".".join(str(p) for p in parts[:4]))
PY
}

resolve_input_jar() {
    local requested="$1"
    if [[ -n "$requested" ]]; then
        if [[ ! -f "$requested" ]]; then
            error "Input jar not found: $requested"
            exit 1
        fi
        INPUT_JAR="$requested"
        return
    fi

    local exact="$JAR_DIR_DEFAULT/${PACKAGE_NAME}-windows-${JAR_ARCH}-${VERSION_NAME}.jar"
    if [[ -f "$exact" ]]; then
        INPUT_JAR="$exact"
        return
    fi

    local latest=""
    local -a matches=()
    shopt -s nullglob
    matches=("$JAR_DIR_DEFAULT"/${PACKAGE_NAME}-windows-${JAR_ARCH}-*.jar)
    shopt -u nullglob
    if [[ "${#matches[@]}" -gt 0 ]]; then
        latest="$(ls -t "${matches[@]}" 2>/dev/null | head -n 1)"
    fi
    if [[ -n "$latest" ]]; then
        INPUT_JAR="$latest"
        return
    fi

    error "No matching desktop jar found in $JAR_DIR_DEFAULT"
    error "Build one first: ./gradlew -p composeApp packageUberJarForCurrentOS -PdesktopTarget=windows/$TARGET_ARCH"
    exit 1
}

resolve_launcher_bin() {
    local requested="$1"
    local default_path="$ROOT_DIR/launcher/zig-out/bin/launcher-${TARGET_PLATFORM}-${LAUNCHER_MACHINE}.exe"

    if [[ -n "$requested" ]]; then
        if [[ ! -f "$requested" ]]; then
            error "Launcher binary not found: $requested"
            exit 1
        fi
        INPUT_LAUNCHER_BIN="$requested"
        return
    fi

    if [[ -f "$default_path" ]]; then
        INPUT_LAUNCHER_BIN="$default_path"
        return
    fi

    error "Launcher binary not found: $default_path"
    error "Build one first: cd launcher && zig build -Doptimize=ReleaseSmall -Dtarget=${LAUNCHER_MACHINE}-windows"
    exit 1
}

nsis_url_scheme_install_entries() {
    local scheme
    for scheme in "${DESKTOP_URL_SCHEMES[@]}"; do
        cat <<EOF
    WriteRegStr HKCU "Software\\Classes\\$scheme" "" "URL:$scheme Protocol"
    WriteRegStr HKCU "Software\\Classes\\$scheme" "URL Protocol" ""
    WriteRegStr HKCU "Software\\Classes\\$scheme\\DefaultIcon" "" "\$INSTDIR\\\${APP_NAME}.exe,0"
    WriteRegStr HKCU "Software\\Classes\\$scheme\\shell\\open\\command" "" '"\$INSTDIR\\\${APP_NAME}.exe" "%1"'
EOF
    done
}

nsis_url_scheme_uninstall_entries() {
    local scheme
    for scheme in "${DESKTOP_URL_SCHEMES[@]}"; do
        echo "    DeleteRegKey HKCU \"Software\\Classes\\$scheme\""
    done
}

prepare_rootfs() {
    local root="$1"
    local launcher_name="$APP_NAME.exe"
    local launcher_path="$root/$launcher_name"

    mkdir -p "$root/app"
    cp "$INPUT_JAR" "$root/app/$PACKAGE_NAME.jar"
    cp "$INPUT_LAUNCHER_BIN" "$launcher_path"
    chmod 755 "$launcher_path"
    cp "$WINDOWS_JAVA_OPTS_FILE" "$root/desktop-java-opts.conf.template"
    cp "$ROOT_DIR/release/linux/desktop/desktop-app-args.conf" "$root/desktop-app-args.conf.template"
    cp "$ROOT_DIR/LICENSE" "$root/LICENSE"
    touch_path_tree "$root"
}

build_zip() {
    local portable_root="$1"
    local output_path="$OUTPUT_DIR/${PACKAGE_NAME}-${VERSION_NAME}-windows-${TARGET_ARCH}.zip"

    rm -f "$output_path"
    "$PYTHON_BIN" - "$portable_root" "$output_path" <<'PY'
import os
import sys
import zipfile

root = os.path.abspath(sys.argv[1])
output_path = os.path.abspath(sys.argv[2])
parent = os.path.dirname(root)

with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for current, dir_names, file_names in os.walk(root):
        dir_names.sort()
        file_names.sort()
        rel_dir = os.path.relpath(current, parent).replace(os.sep, "/")
        if rel_dir != ".":
            archive.write(current, rel_dir.rstrip("/") + "/")
        for file_name in file_names:
            path = os.path.join(current, file_name)
            arcname = os.path.relpath(path, parent).replace(os.sep, "/")
            archive.write(path, arcname)
PY
    touch_path "$output_path"
    log "Built zip: $output_path"
}

build_nsis() {
    local work_dir="$1"
    local nsis_source="$work_dir/installer.nsi"
    local output_path="$OUTPUT_DIR/${PACKAGE_NAME}-${VERSION_NAME}-windows-${TARGET_ARCH}-installer.exe"
    local vi_version
    local url_scheme_registry
    local url_scheme_unregistry

    vi_version="$(normalize_vi_version)" || {
        error "VERSION_NAME=$VERSION_NAME cannot be converted to a VIProductVersion."
        exit 1
    }

    url_scheme_registry="$(nsis_url_scheme_install_entries)"
    url_scheme_unregistry="$(nsis_url_scheme_uninstall_entries)"

    render_template \
        "$NSIS_TEMPLATE_FILE" \
        "$nsis_source" \
        "__HUSI_PACKAGE_NAME__" "$PACKAGE_NAME" \
        "__HUSI_APP_NAME__" "$APP_NAME" \
        "__HUSI_APP_NAME_ZH_CN__" "$APP_NAME_ZH_CN" \
        "__HUSI_APP_VERSION__" "$VERSION_NAME" \
        "__HUSI_APP_DESCRIPTION__" "$APP_DESCRIPTION" \
        "__HUSI_APP_URL__" "$APP_URL" \
        "__HUSI_MAINTAINER__" "$MAINTAINER" \
        "__HUSI_VI_VERSION__" "$vi_version" \
        "__HUSI_OUTPUT_FILE__" "$output_path" \
        "__HUSI_LICENSE_FILE__" "$ROOT_DIR/LICENSE" \
        "__HUSI_LAUNCHER_FILE__" "$INPUT_LAUNCHER_BIN" \
        "__HUSI_JAR_FILE__" "$INPUT_JAR" \
        "__HUSI_JAVA_OPTS_FILE__" "$WINDOWS_JAVA_OPTS_FILE" \
        "__HUSI_APP_ARGS_FILE__" "$ROOT_DIR/release/linux/desktop/desktop-app-args.conf" \
        "__HUSI_URL_SCHEME_REGISTRY__" "$url_scheme_registry" \
        "__HUSI_URL_SCHEME_UNREGISTRY__" "$url_scheme_unregistry"

    rm -f "$output_path"
    "$NSIS_BIN" "$nsis_source"
    touch_path "$output_path"
    log "Built NSIS installer: $output_path"
}

TARGET=""
TARGET_PLATFORM=""
TARGET_ARCH=""
INPUT_JAR=""
INPUT_LAUNCHER_BIN=""
OUTPUT_DIR="$OUTPUT_DIR_DEFAULT"
FORMATS="zip,nsis"
CHECK_TOOLS=0
PACKAGE_NAME=""
VERSION_NAME=""
APP_NAME=""
APP_NAME_ZH_CN=""
APP_DESCRIPTION=""
APP_URL=""
MAINTAINER=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --target)
            require_arg "$1" "${2:-}"
            TARGET="$2"
            shift 2
            ;;
        --formats)
            require_arg "$1" "${2:-}"
            FORMATS="$2"
            shift 2
            ;;
        -i|--input-jar)
            require_arg "$1" "${2:-}"
            INPUT_JAR="$2"
            shift 2
            ;;
        --launcher-bin)
            require_arg "$1" "${2:-}"
            INPUT_LAUNCHER_BIN="$2"
            shift 2
            ;;
        -o|--output-dir)
            require_arg "$1" "${2:-}"
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --check-tools)
            CHECK_TOOLS=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            error "Unknown argument: $1"
            usage
            exit 1
            ;;
    esac
done

resolve_python
load_metadata
resolve_host_os
resolve_target
resolve_arch
resolve_formats "$FORMATS"
resolve_nsis
require_tools

if [[ "$CHECK_TOOLS" -eq 1 ]]; then
    log "All required tools are available for target: windows/$TARGET_ARCH"
    exit 0
fi

resolve_tag_epoch
resolve_input_jar "$INPUT_JAR"
resolve_launcher_bin "$INPUT_LAUNCHER_BIN"
mkdir -p "$OUTPUT_DIR"

work_dir="$(mktemp -d)"
cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

if [[ -n "${ENABLED_FORMATS[zip]:-}" ]]; then
    portable_root="$work_dir/${APP_NAME}-${VERSION_NAME}-windows-${TARGET_ARCH}"
    prepare_rootfs "$portable_root"
    build_zip "$portable_root"
fi
if [[ -n "${ENABLED_FORMATS[nsis]:-}" ]]; then
    build_nsis "$work_dir"
fi

log "Done. Output directory: $OUTPUT_DIR"
