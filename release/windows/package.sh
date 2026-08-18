#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
METADATA_FILE="$ROOT_DIR/husi.properties"
DESKTOP_METADATA_FILE="$ROOT_DIR/release/desktop/package-metadata.sh"
DESKTOP_JRE_MODULES_FILE="$ROOT_DIR/release/desktop/jre-modules.sh"
NSIS_TEMPLATE_FILE="$ROOT_DIR/release/windows/desktop/installer.nsi"
WINDOWS_JAVA_OPTS_FILE="$ROOT_DIR/release/windows/desktop/desktop-java-opts.conf"
JAR_DIR_DEFAULT="$ROOT_DIR/composeApp/build/compose/jars"
OUTPUT_DIR_DEFAULT="$ROOT_DIR/composeApp/build/compose/packages/windows"
TAG_NAME=""
TAG_EPOCH=""
HOST_OS=""
PYTHON_BIN=""
NSIS_BIN=""
JBR_JMODS_DIR=""
RUNTIME_DIR=""
VARIANT_SUFFIX=""

log() {
    echo "[package] $*"
}

error() {
    echo "[package] $*" >&2
}

# shellcheck source=release/windows/codesign.sh
source "$SCRIPT_DIR/codesign.sh"

usage() {
    cat <<EOF
Usage:
  $(basename "$0") [--formats zip,nsis] [--target <platform/arch>] [--input-jar <file>] [--launcher-bin <file>] [--core-bin <file>] [--core-lib <file>] [--output-dir <dir>] [--jbr-jmods <dir>] [--no-sign]
  $(basename "$0") --check-tools [--formats zip,nsis] [--target <platform/arch>] [--jbr-jmods <dir>] [--no-sign]

Description:
  Build Windows portable zip and NSIS installer packages from desktop uber jar.

  With --jbr-jmods the packages additionally bundle a Java runtime, linked with
  jlink from the JetBrains Runtime modules of the target. Those packages need no
  system Java at all, and their file names carry a -jbr suffix.

Defaults:
  --formats      zip,nsis
  --input-jar    newest matching jar under $JAR_DIR_DEFAULT
  --launcher-bin $ROOT_DIR/launcher/zig-out/bin/launcher-windows-<x86_64|aarch64>.exe
  --core-bin     $ROOT_DIR/libcore/build/windows_<amd64|arm64>/husi-core.exe
  --core-lib     $ROOT_DIR/libcore/build/windows_<amd64|arm64>/husicore.dll
  --output-dir   $OUTPUT_DIR_DEFAULT
  --jbr-jmods    unset (env: JBR_JMODS); fetch them with ./run lib jbr windows/<arch>

Code signing:
  Enabled by default. Configure the certificate through WINDOWS_SIGNING_P12
  (or WINDOWS_SIGNING_P12_BASE64) and WINDOWS_SIGNING_P12_PASSWORD, or pass
  --no-sign to build unsigned packages. See release/windows/codesign.sh.
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

    # Named so that shellcheck can follow it; the path is only dynamic because
    # it is anchored at the repository root.
    # shellcheck source=../desktop/package-metadata.sh
    source "$DESKTOP_METADATA_FILE"
}

source_desktop_jre_modules() {
    if [[ ! -f "$DESKTOP_JRE_MODULES_FILE" ]]; then
        error "Desktop JRE module list not found: $DESKTOP_JRE_MODULES_FILE"
        exit 1
    fi

    # shellcheck source=../desktop/jre-modules.sh
    source "$DESKTOP_JRE_MODULES_FILE"
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
    source_desktop_jre_modules
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

# The bundled runtime is what makes a package installable on a machine with no
# Java at all. It is opt-in: without --jbr-jmods the packages stay thin.
resolve_jbr_jmods() {
    local requested="$1"

    if [[ -n "$requested" ]]; then
        JBR_JMODS_DIR="$requested"
    elif [[ -n "${JBR_JMODS:-}" ]]; then
        JBR_JMODS_DIR="${JBR_JMODS}"
    else
        # Bare `return` would carry the failed test's status into set -e.
        return 0
    fi

    if [[ ! -d "$JBR_JMODS_DIR" ]]; then
        error "JetBrains Runtime modules directory not found: $JBR_JMODS_DIR"
        error "Fetch them first: ./run lib jbr windows/$TARGET_ARCH"
        exit 1
    fi

    VARIANT_SUFFIX="-jbr"
}

resolve_jlink() {
    if [[ -z "$JBR_JMODS_DIR" ]]; then
        return 0
    fi

    if command -v jlink >/dev/null 2>&1; then
        return 0
    fi

    error "Missing required tool: jlink. Install a JDK and put its bin directory on PATH."
    error "Its feature version has to be at least the one of the JetBrains Runtime being linked."
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
    local candidate=""
    local -a matches=()
    shopt -s nullglob
    # Everything but the wildcard stays quoted, so only the glob expands.
    matches=("$JAR_DIR_DEFAULT/${PACKAGE_NAME}-windows-${JAR_ARCH}-"*.jar)
    shopt -u nullglob
    for candidate in "${matches[@]}"; do
        if [[ -z "$latest" || "$candidate" -nt "$latest" ]]; then
            latest="$candidate"
        fi
    done
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

resolve_core_bin() {
    local requested="$1"
    local default_path="$ROOT_DIR/libcore/build/${TARGET_PLATFORM}_${TARGET_ARCH}/husi-core.exe"

    if [[ -n "$requested" ]]; then
        if [[ ! -f "$requested" ]]; then
            error "Core host binary not found: $requested"
            exit 1
        fi
        INPUT_CORE_BIN="$requested"
        return
    fi

    if [[ -f "$default_path" ]]; then
        INPUT_CORE_BIN="$default_path"
        return
    fi

    error "Core host binary not found: $default_path"
    error "Build one first: make core_desktop DESKTOP_TARGETS=${TARGET_PLATFORM}/${TARGET_ARCH}"
    exit 1
}

resolve_core_lib() {
    local requested="$1"
    local default_path="$ROOT_DIR/libcore/build/${TARGET_PLATFORM}_${TARGET_ARCH}/husicore.dll"

    if [[ -n "$requested" ]]; then
        if [[ ! -f "$requested" ]]; then
            error "Core native library not found: $requested"
            exit 1
        fi
        INPUT_CORE_LIB="$requested"
        return
    fi

    if [[ -f "$default_path" ]]; then
        INPUT_CORE_LIB="$default_path"
        return
    fi

    error "Core native library not found: $default_path"
    error "Build one first: make libcore_desktop DESKTOP_TARGETS=${TARGET_PLATFORM}/${TARGET_ARCH}"
    exit 1
}

nsis_url_scheme_install_entries() {
    local scheme
    for scheme in "${DESKTOP_URL_SCHEMES[@]}"; do
        cat <<EOF
    WriteRegStr HKCU "Software\\Classes\\$scheme" "" "URL:$scheme Protocol"
    WriteRegStr HKCU "Software\\Classes\\$scheme" "URL Protocol" ""
    WriteRegStr HKCU "Software\\Classes\\$scheme\\DefaultIcon" "" "\$INSTDIR\\\${APP_NAME}.exe,0"
    WriteRegStr HKCU "Software\\Classes\\$scheme\\shell\\open\\command" "" '"\$INSTDIR\\\${APP_NAME}.exe" open "%1"'
EOF
    done
}

nsis_url_scheme_uninstall_entries() {
    local scheme
    for scheme in "${DESKTOP_URL_SCHEMES[@]}"; do
        echo "    DeleteRegKey HKCU \"Software\\Classes\\$scheme\""
    done
}

# jlink links an image for the platform its modules belong to, not for the host,
# so this runs on Linux just as well as on Windows. Two flags the Linux AppImage
# passes are deliberately absent: --strip-native-debug-symbols shells out to
# objcopy and only understands ELF, and Windows debug symbols live in separate
# pdb files anyway; --generate-cds-archive cannot be generated cross-platform.
build_runtime() {
    local runtime_dir="$1"

    rm -rf "$runtime_dir"
    mkdir -p "$(dirname "$runtime_dir")"
    jlink \
        --module-path "$JBR_JMODS_DIR" \
        --add-modules "$DESKTOP_JRE_MODULES_WINDOWS" \
        --strip-java-debug-attributes \
        --no-header-files \
        --no-man-pages \
        --compress=zip-6 \
        --output "$runtime_dir"
    log "Linked bundled runtime: $runtime_dir"
}

output_filename() {
    local extension="$1"
    echo "${PACKAGE_NAME}-${VERSION_NAME}-windows-${TARGET_ARCH}${VARIANT_SUFFIX}${extension}"
}

# jlink emits hundreds of files, so the installer takes the tree wholesale and
# the uninstaller drops it the same way. RMDir /r stays scoped to this one
# subdirectory of $INSTDIR.
nsis_runtime_install_entries() {
    cat <<EOF
    SetOutPath "\$INSTDIR\\runtime"
    File /r "$RUNTIME_DIR/*"
    SetOutPath "\$INSTDIR"
EOF
}

nsis_runtime_uninstall_entries() {
    cat <<'EOF'
    RMDir /r "$INSTDIR\runtime"
EOF
}

prepare_rootfs() {
    local root="$1"
    local launcher_name="$APP_NAME.exe"
    local launcher_path="$root/$launcher_name"
    local core_path="$root/husi-core.exe"
    local core_lib_path="$root/husicore.dll"

    mkdir -p "$root/app"
    cp "$INPUT_JAR" "$root/app/$PACKAGE_NAME.jar"
    cp "$INPUT_LAUNCHER_BIN" "$launcher_path"
    chmod 755 "$launcher_path"
    cp "$INPUT_CORE_BIN" "$core_path"
    chmod 755 "$core_path"
    # Sidecar anja library next to husi-core (N7); UI sets anja.natives.dir to this dir.
    cp "$INPUT_CORE_LIB" "$core_lib_path"
    chmod 755 "$core_lib_path"
    cp "$WINDOWS_JAVA_OPTS_FILE" "$root/desktop-java-opts.conf.template"
    cp "$ROOT_DIR/release/linux/desktop/desktop-app-args.conf" "$root/desktop-app-args.conf.template"
    cp "$ROOT_DIR/LICENSE" "$root/LICENSE"
    if [[ -n "$RUNTIME_DIR" ]]; then
        cp -a "$RUNTIME_DIR" "$root/runtime"
    fi
    touch_path_tree "$root"
}

build_zip() {
    local portable_root="$1"
    local output_path
    output_path="$OUTPUT_DIR/$(output_filename ".zip")"

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
    local output_path
    output_path="$OUTPUT_DIR/$(output_filename "-installer.exe")"
    local vi_version
    local url_scheme_registry
    local url_scheme_unregistry
    local runtime_install=""
    local runtime_uninstall=""

    vi_version="$(normalize_vi_version)" || {
        error "VERSION_NAME=$VERSION_NAME cannot be converted to a VIProductVersion."
        exit 1
    }

    url_scheme_registry="$(nsis_url_scheme_install_entries)"
    url_scheme_unregistry="$(nsis_url_scheme_uninstall_entries)"
    if [[ -n "$RUNTIME_DIR" ]]; then
        runtime_install="$(nsis_runtime_install_entries)"
        runtime_uninstall="$(nsis_runtime_uninstall_entries)"
    fi

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
        "__HUSI_CORE_FILE__" "$INPUT_CORE_BIN" \
        "__HUSI_CORE_LIB_FILE__" "$INPUT_CORE_LIB" \
        "__HUSI_JAR_FILE__" "$INPUT_JAR" \
        "__HUSI_JAVA_OPTS_FILE__" "$WINDOWS_JAVA_OPTS_FILE" \
        "__HUSI_APP_ARGS_FILE__" "$ROOT_DIR/release/linux/desktop/desktop-app-args.conf" \
        "__HUSI_URL_SCHEME_REGISTRY__" "$url_scheme_registry" \
        "__HUSI_URL_SCHEME_UNREGISTRY__" "$url_scheme_unregistry" \
        "__HUSI_RUNTIME_INSTALL__" "$runtime_install" \
        "__HUSI_RUNTIME_UNINSTALL__" "$runtime_uninstall"

    rm -f "$output_path"
    "$NSIS_BIN" "$nsis_source"
    # Sign before the timestamp is forced: signing rewrites the file.
    if [[ "$SIGNING_ENABLED" -eq 1 ]]; then
        sign_pe "$output_path"
    fi
    touch_path "$output_path"
    log "Built NSIS installer: $output_path"
}

TARGET=""
TARGET_PLATFORM=""
TARGET_ARCH=""
INPUT_JAR=""
INPUT_LAUNCHER_BIN=""
INPUT_CORE_BIN=""
INPUT_CORE_LIB=""
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
JBR_JMODS_ARG=""

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
        --core-bin)
            require_arg "$1" "${2:-}"
            INPUT_CORE_BIN="$2"
            shift 2
            ;;
        --core-lib)
            require_arg "$1" "${2:-}"
            INPUT_CORE_LIB="$2"
            shift 2
            ;;
        -o|--output-dir)
            require_arg "$1" "${2:-}"
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --jbr-jmods)
            require_arg "$1" "${2:-}"
            JBR_JMODS_ARG="$2"
            shift 2
            ;;
        --no-sign)
            SIGNING_ENABLED=0
            shift
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
resolve_jbr_jmods "$JBR_JMODS_ARG"
resolve_jlink
require_signing_tool
require_tools

if [[ "$CHECK_TOOLS" -eq 1 ]]; then
    log "All required tools are available for target: windows/$TARGET_ARCH"
    exit 0
fi

resolve_tag_epoch
resolve_input_jar "$INPUT_JAR"
resolve_launcher_bin "$INPUT_LAUNCHER_BIN"
resolve_core_bin "$INPUT_CORE_BIN"
resolve_core_lib "$INPUT_CORE_LIB"
mkdir -p "$OUTPUT_DIR"

work_dir="$(mktemp -d)"
cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

resolve_signing "$work_dir"
sign_payloads "$work_dir"

if [[ -n "$JBR_JMODS_DIR" ]]; then
    RUNTIME_DIR="$work_dir/runtime"
    build_runtime "$RUNTIME_DIR"
fi

if [[ -n "${ENABLED_FORMATS[zip]:-}" ]]; then
    portable_root="$work_dir/${APP_NAME}-${VERSION_NAME}-windows-${TARGET_ARCH}${VARIANT_SUFFIX}"
    prepare_rootfs "$portable_root"
    build_zip "$portable_root"
fi
if [[ -n "${ENABLED_FORMATS[nsis]:-}" ]]; then
    build_nsis "$work_dir"
fi

log "Done. Output directory: $OUTPUT_DIR"
