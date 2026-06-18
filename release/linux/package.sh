#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
METADATA_FILE="$ROOT_DIR/husi.properties"
DESKTOP_METADATA_FILE="$ROOT_DIR/release/desktop/package-metadata.sh"
JAR_DIR_DEFAULT="$ROOT_DIR/composeApp/build/compose/jars"
OUTPUT_DIR_DEFAULT="$ROOT_DIR/composeApp/build/compose/packages/linux"
PACKAGE_NAME_PLACEHOLDER="__HUSI_PACKAGE_NAME__"
VERSION_NAME_PLACEHOLDER="__HUSI_VERSION_NAME__"
APP_NAME_PLACEHOLDER="__HUSI_APP_NAME__"
APP_NAME_ZH_CN_PLACEHOLDER="__HUSI_APP_NAME_ZH_CN__"
APP_NAME_ZH_TW_PLACEHOLDER="__HUSI_APP_NAME_ZH_TW__"
APP_DESCRIPTION_PLACEHOLDER="__HUSI_APP_DESCRIPTION__"
APP_DESCRIPTION_ZH_CN_PLACEHOLDER="__HUSI_APP_DESCRIPTION_ZH_CN__"
APP_DESCRIPTION_ZH_TW_PLACEHOLDER="__HUSI_APP_DESCRIPTION_ZH_TW__"
STARTUP_WM_CLASS_PLACEHOLDER="__HUSI_STARTUP_WM_CLASS__"
APP_URL_PLACEHOLDER="__HUSI_APP_URL__"
MAINTAINER_PLACEHOLDER="__HUSI_MAINTAINER__"
URL_SCHEME_MIME_TYPES_PLACEHOLDER="__HUSI_URL_SCHEME_MIME_TYPES__"
LAUNCHER_PATH_PLACEHOLDER="__HUSI_LAUNCHER_PATH__"
LAUNCHER_CAPS_PLACEHOLDER="__HUSI_LAUNCHER_CAPS__"
LAUNCHER_SETCAPS="cap_setpcap,cap_net_admin,cap_net_raw,cap_net_bind_service,cap_sys_ptrace,cap_dac_read_search=ep"
TAG_NAME=""
TAG_EPOCH=""

log() {
    echo "[package] $*"
}

error() {
    echo "[package] $*" >&2
}

usage() {
    cat <<EOF
Usage:
  $(basename "$0") [--formats deb,rpm,pacman] [--target <platform/arch>] [--input-jar <file>] [--launcher-bin <file>] [--output-dir <dir>] [--pkgrel <n>]
  $(basename "$0") --check-tools [--formats deb,rpm,pacman]

Description:
  Build Linux native packages for system Java runtime from desktop uber jar via nfpm.

Defaults:
  --formats    deb,rpm,pacman
  --input-jar  newest matching jar under $JAR_DIR_DEFAULT
  --launcher-bin  $ROOT_DIR/launcher/zig-out/bin/launcher-linux-<x86_64|aarch64>
  --output-dir $OUTPUT_DIR_DEFAULT
  --pkgrel     1
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

escape_for_sed() {
    printf '%s' "$1" | sed -e 's/[\\&#]/\\&/g'
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

    local -a sed_args=()
    local placeholder
    local value
    local escaped_value
    while [[ $# -gt 0 ]]; do
        placeholder="$1"
        value="$2"
        escaped_value="$(escape_for_sed "$value")"
        sed_args+=(-e "s#${placeholder}#${escaped_value}#g")
        shift 2
    done

    sed "${sed_args[@]}" "$template_file" >"$output_file"
}

source_desktop_metadata() {
    if [[ ! -f "$DESKTOP_METADATA_FILE" ]]; then
        error "Desktop metadata file not found: $DESKTOP_METADATA_FILE"
        exit 1
    fi

    # shellcheck disable=SC1090
    source "$DESKTOP_METADATA_FILE"
}

normalize_pkgrel() {
    local rel="$1"
    if [[ "$rel" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
        echo "$rel"
        return
    fi
    error "Invalid pkgrel '$rel'. Use numeric value like 1 or 1.1."
    exit 1
}

normalize_rpm_version() {
    local version="$1"
    echo "$version" | sed -E 's/[^A-Za-z0-9._+~]+/./g'
}

normalize_pacman_version() {
    local version="$1"
    version="${version#*:}"
    version="$(echo "$version" | sed -E 's/[^A-Za-z0-9._+]+/_/g')"
    version="$(echo "$version" | sed -E 's/^_+//; s/_+$//')"
    if [[ -z "$version" ]]; then
        version="0"
    fi
    echo "$version"
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
    URL_SCHEME_MIME_TYPES="$(desktop_url_scheme_mime_types)"
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
    value="$(echo "$1" | tr '[:upper:]' '[:lower:]')"
    case "$value" in
        linux)
            echo "linux"
            ;;
        *)
            error "Unsupported platform '$1'. Use linux."
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
            error "Invalid --target '$TARGET'. Use <platform>/<arch>, e.g. linux/amd64."
            exit 1
        fi
        TARGET_PLATFORM="$(normalize_platform "$raw_platform")"
        TARGET_ARCH="$(normalize_arch "$raw_arch")"
    else
        TARGET_PLATFORM="linux"
        TARGET_ARCH="$(normalize_arch "$(uname -m)")"
    fi
}

resolve_arch() {
    case "$TARGET_ARCH" in
        amd64)
            JAR_ARCH="x64"
            DEB_ARCH="amd64"
            RPM_ARCH="x86_64"
            PACMAN_ARCH="x86_64"
            LAUNCHER_MACHINE="x86_64"
            ;;
        arm64)
            JAR_ARCH="arm64"
            DEB_ARCH="arm64"
            RPM_ARCH="aarch64"
            PACMAN_ARCH="aarch64"
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
        item="$(echo "$item" | tr '[:upper:]' '[:lower:]' | xargs)"
        case "$item" in
            deb|rpm|pacman)
                ENABLED_FORMATS["$item"]=1
                ;;
            "")
                ;;
            *)
                error "Unknown format '$item'. Use deb,rpm,pacman."
                exit 1
                ;;
        esac
    done

    if [[ "${#ENABLED_FORMATS[@]}" -eq 0 ]]; then
        error "No valid formats selected."
        exit 1
    fi
}

require_tools_for_formats() {
    local -a tools=(awk sed find cp mkdir mktemp date xargs git nfpm)
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

    local exact="$JAR_DIR_DEFAULT/${PACKAGE_NAME}-linux-${JAR_ARCH}-${VERSION_NAME}.jar"
    if [[ -f "$exact" ]]; then
        INPUT_JAR="$exact"
        return
    fi

    local latest
    latest="$(find "$JAR_DIR_DEFAULT" -maxdepth 1 -type f -name "${PACKAGE_NAME}-linux-${JAR_ARCH}-*.jar" -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -n 1 | awk '{print $2}')"
    if [[ -n "$latest" ]]; then
        INPUT_JAR="$latest"
        return
    fi

    error "No matching desktop jar found in $JAR_DIR_DEFAULT"
    error "Build one first: ./gradlew -p composeApp packageUberJarForCurrentOS"
    exit 1
}

resolve_launcher_bin() {
    local requested="$1"
    local default_path="$ROOT_DIR/launcher/zig-out/bin/launcher-${TARGET_PLATFORM}-${LAUNCHER_MACHINE}"

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
    error "Build one first: cd launcher && zig build -Doptimize=ReleaseSmall -Dtarget=${LAUNCHER_MACHINE}-linux-musl"
    exit 1
}

prepare_rootfs() {
    local rootfs="$1"
    local app_root="$rootfs/usr/lib/$PACKAGE_NAME"
    local bin_dir="$app_root/bin"
    local app_dir="$app_root/app"
    local java_opts_template="$ROOT_DIR/release/linux/desktop/desktop-java-opts.conf"
    local app_args_template="$ROOT_DIR/release/linux/desktop/desktop-app-args.conf"
    local desktop_entry_template="$ROOT_DIR/release/linux/desktop/husi.desktop"
    local main_launcher="$bin_dir/$PACKAGE_NAME"
    local desktop_entry_path="$rootfs/usr/share/applications/$PACKAGE_NAME.desktop"
    local startup_wm_class="${PACKAGE_NAME//./-}-DesktopMainKt"

    if [[ ! -f "$java_opts_template" || ! -f "$app_args_template" || ! -f "$desktop_entry_template" ]]; then
        error "Missing launcher templates under release/linux/desktop"
        exit 1
    fi

    mkdir -p "$bin_dir" "$app_dir" "$rootfs/usr/share/applications" "$rootfs/usr/share/pixmaps"
    cp "$INPUT_JAR" "$app_dir/$PACKAGE_NAME.jar"
    cp "$INPUT_LAUNCHER_BIN" "$main_launcher"
    chmod 755 "$main_launcher"

    cp "$java_opts_template" "$bin_dir/desktop-java-opts.conf.template"
    cp "$app_args_template" "$bin_dir/desktop-app-args.conf.template"

    render_template \
        "$desktop_entry_template" \
        "$desktop_entry_path" \
        "$PACKAGE_NAME_PLACEHOLDER" "$PACKAGE_NAME" \
        "$APP_NAME_PLACEHOLDER" "$APP_NAME" \
        "$APP_NAME_ZH_CN_PLACEHOLDER" "$APP_NAME_ZH_CN" \
        "$APP_NAME_ZH_TW_PLACEHOLDER" "$APP_NAME_ZH_TW" \
        "$APP_DESCRIPTION_PLACEHOLDER" "$APP_DESCRIPTION" \
        "$APP_DESCRIPTION_ZH_CN_PLACEHOLDER" "$APP_DESCRIPTION_ZH_CN" \
        "$APP_DESCRIPTION_ZH_TW_PLACEHOLDER" "$APP_DESCRIPTION_ZH_TW" \
        "$URL_SCHEME_MIME_TYPES_PLACEHOLDER" "$URL_SCHEME_MIME_TYPES" \
        "$STARTUP_WM_CLASS_PLACEHOLDER" "$startup_wm_class"

    local icon_source="$ROOT_DIR/fastlane/metadata/android/en-US/images/icon.png"
    if [[ -f "$icon_source" ]]; then
        cp "$icon_source" "$rootfs/usr/share/pixmaps/$PACKAGE_NAME.png"
    fi
}

prepare_script_templates() {
    local work_dir="$1"
    local launcher_path="/usr/lib/$PACKAGE_NAME/bin/$PACKAGE_NAME"

    render_template \
        "$ROOT_DIR/release/linux/desktop/postinstall.sh" \
        "$work_dir/deb-postinstall.sh" \
        "$LAUNCHER_PATH_PLACEHOLDER" "$launcher_path" \
        "$LAUNCHER_CAPS_PLACEHOLDER" "$LAUNCHER_SETCAPS"

    render_template \
        "$ROOT_DIR/release/linux/desktop/postremove.sh" \
        "$work_dir/deb-postremove.sh" \
        "$LAUNCHER_PATH_PLACEHOLDER" "$launcher_path"

    render_template \
        "$ROOT_DIR/release/linux/desktop/posttrans.sh" \
        "$work_dir/rpm-posttrans.sh" \
        "$LAUNCHER_PATH_PLACEHOLDER" "$launcher_path" \
        "$LAUNCHER_CAPS_PLACEHOLDER" "$LAUNCHER_SETCAPS"

    render_template \
        "$ROOT_DIR/release/linux/desktop/postinstall.arch.sh" \
        "$work_dir/arch-postinstall.sh" \
        "$LAUNCHER_PATH_PLACEHOLDER" "$launcher_path" \
        "$LAUNCHER_CAPS_PLACEHOLDER" "$LAUNCHER_SETCAPS"

    render_template \
        "$ROOT_DIR/release/linux/desktop/postupgrade.arch.sh" \
        "$work_dir/arch-postupgrade.sh" \
        "$LAUNCHER_PATH_PLACEHOLDER" "$launcher_path" \
        "$LAUNCHER_CAPS_PLACEHOLDER" "$LAUNCHER_SETCAPS"

    chmod 755 \
        "$work_dir/deb-postinstall.sh" \
        "$work_dir/deb-postremove.sh" \
        "$work_dir/rpm-posttrans.sh" \
        "$work_dir/arch-postinstall.sh" \
        "$work_dir/arch-postupgrade.sh"
}

write_content_entry() {
    local config_file="$1"
    local src="$2"
    local dst="$3"
    local type="$4"

    cat >>"$config_file" <<EOF
  - src: $src
    dst: $dst
EOF

    if [[ -n "$type" ]]; then
        cat >>"$config_file" <<EOF
    type: $type
EOF
    fi
}

write_common_nfpm_config() {
    local config_file="$1"
    local rootfs="$2"
    local package_version="$3"
    local package_arch="$4"
    local output_time
    output_time="$(date -u -d "@$TAG_EPOCH" "+%Y-%m-%dT%H:%M:%SZ")"

    cat >"$config_file" <<EOF
name: $PACKAGE_NAME
arch: $package_arch
platform: linux
version: $package_version
version_schema: none
release: "$PKGREL"
section: net
priority: optional
maintainer: $MAINTAINER
description: $APP_DESCRIPTION
homepage: $APP_URL
license: GPL-3.0-or-later
mtime: $output_time
contents:
EOF

    write_content_entry "$config_file" "$rootfs/usr/lib/$PACKAGE_NAME" "/usr/lib/$PACKAGE_NAME" "tree"
    write_content_entry "$config_file" "../lib/$PACKAGE_NAME/bin/$PACKAGE_NAME" "/usr/bin/$PACKAGE_NAME" "symlink"
    write_content_entry "$config_file" "$rootfs/usr/share/applications/$PACKAGE_NAME.desktop" "/usr/share/applications/$PACKAGE_NAME.desktop" ""

    local icon_path="$rootfs/usr/share/pixmaps/$PACKAGE_NAME.png"
    if [[ -f "$icon_path" ]]; then
        write_content_entry "$config_file" "$icon_path" "/usr/share/pixmaps/$PACKAGE_NAME.png" ""
    fi

}

append_deb_nfpm_config() {
    local config_file="$1"
    local work_dir="$2"

    cat >>"$config_file" <<EOF
depends:
  - java21-runtime | openjdk-21-jre | openjdk-21-jre-headless
  - libcap2-bin
  - ca-certificates
  - nftables
scripts:
  postinstall: $work_dir/deb-postinstall.sh
  postremove: $work_dir/deb-postremove.sh
deb:
  arch: $DEB_ARCH
EOF
}

append_rpm_nfpm_config() {
    local config_file="$1"
    local work_dir="$2"

    cat >>"$config_file" <<EOF
depends:
  - java >= 21
  - libcap
  - ca-certificates
  - nftables
rpm:
  arch: $RPM_ARCH
  summary: $APP_DESCRIPTION
  packager: $MAINTAINER
  scripts:
    posttrans: $work_dir/rpm-posttrans.sh
EOF
}

append_pacman_nfpm_config() {
    local config_file="$1"
    local work_dir="$2"

    cat >>"$config_file" <<EOF
depends:
  - java-runtime>=21
  - libcap
  - ca-certificates
  - nftables
scripts:
  postinstall: $work_dir/arch-postinstall.sh
archlinux:
  arch: $PACMAN_ARCH
  pkgbase: $PACKAGE_NAME
  packager: $MAINTAINER
  scripts:
    postupgrade: $work_dir/arch-postupgrade.sh
EOF
}

normalize_file_mtimes() {
    local rootfs="$1"
    local work_dir="$2"

    find "$rootfs" -exec touch -d "@$TAG_EPOCH" {} +
    find "$work_dir" -maxdepth 1 -type f -exec touch -d "@$TAG_EPOCH" {} +
}

output_filename() {
    local format="$1"
    local package_version="$2"
    case "$format" in
        deb)
            echo "${PACKAGE_NAME}_${package_version}_${DEB_ARCH}.deb"
            ;;
        rpm)
            echo "${PACKAGE_NAME}-${package_version}-${PKGREL}.${RPM_ARCH}.rpm"
            ;;
        pacman)
            echo "${PACKAGE_NAME}-${package_version}-${PKGREL}-${PACMAN_ARCH}.pkg.tar.zst"
            ;;
        *)
            error "Unknown output format '$format'."
            exit 1
            ;;
    esac
}

build_with_nfpm() {
    local rootfs="$1"
    local work_dir="$2"
    local format="$3"
    local packager
    local package_version
    local package_arch="$TARGET_ARCH"

    case "$format" in
        deb)
            packager="deb"
            package_version="$VERSION_NAME"
            ;;
        rpm)
            packager="rpm"
            package_version="$(normalize_rpm_version "$VERSION_NAME")"
            ;;
        pacman)
            packager="archlinux"
            package_version="$(normalize_pacman_version "$VERSION_NAME")"
            ;;
        *)
            error "Unsupported format '$format'."
            exit 1
            ;;
    esac

    local config_file="$work_dir/nfpm-$format.yaml"
    write_common_nfpm_config "$config_file" "$rootfs" "$package_version" "$package_arch"

    case "$format" in
        deb)
            append_deb_nfpm_config "$config_file" "$work_dir"
            ;;
        rpm)
            append_rpm_nfpm_config "$config_file" "$work_dir"
            ;;
        pacman)
            append_pacman_nfpm_config "$config_file" "$work_dir"
            ;;
    esac

    local output_path="$OUTPUT_DIR/$(output_filename "$format" "$package_version")"
    nfpm package --config "$config_file" --packager "$packager" --target "$output_path"
    log "Built $format: $output_path"
}

FORMATS="deb,rpm,pacman"
TARGET=""
TARGET_PLATFORM=""
TARGET_ARCH=""
INPUT_JAR=""
INPUT_LAUNCHER_BIN=""
OUTPUT_DIR="$OUTPUT_DIR_DEFAULT"
PKGREL="1"
CHECK_TOOLS=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        -f|--formats)
            require_arg "$1" "${2:-}"
            FORMATS="$2"
            shift 2
            ;;
        --target)
            require_arg "$1" "${2:-}"
            TARGET="$2"
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
        --pkgrel)
            require_arg "$1" "${2:-}"
            PKGREL="$2"
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

PKGREL="$(normalize_pkgrel "$PKGREL")"
load_metadata
resolve_tag_epoch
resolve_target
resolve_arch
resolve_formats "$FORMATS"
require_tools_for_formats

if [[ "$CHECK_TOOLS" -eq 1 ]]; then
    log "All required tools are available for formats: $FORMATS"
    exit 0
fi

resolve_input_jar "$INPUT_JAR"
resolve_launcher_bin "$INPUT_LAUNCHER_BIN"
mkdir -p "$OUTPUT_DIR"

work_dir="$(mktemp -d)"
cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

rootfs="$work_dir/rootfs"
mkdir -p "$rootfs"
prepare_rootfs "$rootfs"
prepare_script_templates "$work_dir"
normalize_file_mtimes "$rootfs" "$work_dir"

if [[ -n "${ENABLED_FORMATS[deb]:-}" ]]; then
    build_with_nfpm "$rootfs" "$work_dir" "deb"
fi

if [[ -n "${ENABLED_FORMATS[rpm]:-}" ]]; then
    build_with_nfpm "$rootfs" "$work_dir" "rpm"
fi

if [[ -n "${ENABLED_FORMATS[pacman]:-}" ]]; then
    build_with_nfpm "$rootfs" "$work_dir" "pacman"
fi

log "Done. Output directory: $OUTPUT_DIR"
