#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
METADATA_FILE="$ROOT_DIR/husi.properties"
JAR_DIR_DEFAULT="$ROOT_DIR/composeApp/build/compose/jars"
OUTPUT_DIR_DEFAULT="$ROOT_DIR/composeApp/build/compose/packages/linux"
PACKAGE_NAME_PLACEHOLDER="__HUSI_PACKAGE_NAME__"
VERSION_NAME_PLACEHOLDER="__HUSI_VERSION_NAME__"
APP_NAME_PLACEHOLDER="__HUSI_APP_NAME__"
APP_DESCRIPTION_PLACEHOLDER="__HUSI_APP_DESCRIPTION__"
APP_URL_PLACEHOLDER="__HUSI_APP_URL__"
MAINTAINER_PLACEHOLDER="__HUSI_MAINTAINER__"
DEB_ARCH_PLACEHOLDER="__HUSI_DEB_ARCH__"
RPM_VERSION_PLACEHOLDER="__HUSI_RPM_VERSION__"
RPM_ARCH_PLACEHOLDER="__HUSI_RPM_ARCH__"
PKGREL_PLACEHOLDER="__HUSI_PKGREL__"
CHANGELOG_DATE_PLACEHOLDER="__HUSI_CHANGELOG_DATE__"
PIXMAP_FILE_ENTRY_PLACEHOLDER="__HUSI_PIXMAP_FILE_ENTRY__"
PACMAN_VERSION_FULL_PLACEHOLDER="__HUSI_PACMAN_VERSION_FULL__"
PACMAN_ARCH_PLACEHOLDER="__HUSI_PACMAN_ARCH__"
BUILD_DATE_PLACEHOLDER="__HUSI_BUILD_DATE__"
SIZE_BYTES_PLACEHOLDER="__HUSI_SIZE_BYTES__"
LAUNCHER_PATH_PLACEHOLDER="__HUSI_LAUNCHER_PATH__"
LAUNCHER_CAPS_PLACEHOLDER="__HUSI_LAUNCHER_CAPS__"
LAUNCHER_SETCAPS="cap_setpcap,cap_net_admin,cap_net_raw,cap_net_bind_service,cap_sys_ptrace,cap_dac_read_search=ep"

log() {
    echo "[package-native] $*"
}

error() {
    echo "[package-native] $*" >&2
}

usage() {
    cat <<EOF
Usage:
  $(basename "$0") [--formats deb,rpm,pacman] [--input-jar <file>] [--launcher-bin <file>] [--output-dir <dir>] [--pkgrel <n>]
  $(basename "$0") --check-tools [--formats deb,rpm,pacman]

Description:
  Build Linux native packages for system Java runtime from desktop uber jar.

Defaults:
  --formats    deb,rpm,pacman
  --input-jar  newest matching jar under $JAR_DIR_DEFAULT
  --launcher-bin  $ROOT_DIR/launcher/build/<package>-launcher-bin-<machine>
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

    APP_NAME="Husi"
    APP_DESCRIPTION="Husi desktop proxy integration tool"
    APP_URL="https://github.com/xchacha20-poly1305/husi"
    MAINTAINER="Husi contributors"
}

resolve_arch() {
    local machine
    machine="$(uname -m)"
    case "$machine" in
        x86_64|amd64)
            JAR_ARCH="x64"
            DEB_ARCH="amd64"
            RPM_ARCH="x86_64"
            PACMAN_ARCH="x86_64"
            ;;
        aarch64|arm64)
            JAR_ARCH="arm64"
            DEB_ARCH="arm64"
            RPM_ARCH="aarch64"
            PACMAN_ARCH="aarch64"
            ;;
        *)
            error "Unsupported architecture '$machine'."
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
    local -a tools=(awk sed find cp mkdir mktemp date du bsdtar zstd xargs)
    if [[ -n "${ENABLED_FORMATS[deb]:-}" ]]; then
        tools+=(dpkg-deb)
    fi
    if [[ -n "${ENABLED_FORMATS[rpm]:-}" ]]; then
        tools+=(rpmbuild)
    fi

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
    local default_path="$ROOT_DIR/launcher/build/${PACKAGE_NAME}-launcher-bin-$(uname -m)"

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
    error "Build one first: ./launcher/build.sh"
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
    local system_launcher="$rootfs/usr/bin/$PACKAGE_NAME"
    local desktop_entry_path="$rootfs/usr/share/applications/$PACKAGE_NAME.desktop"

    if [[ ! -f "$java_opts_template" || ! -f "$app_args_template" || ! -f "$desktop_entry_template" ]]; then
        error "Missing launcher templates under release/linux/desktop"
        exit 1
    fi

    mkdir -p "$bin_dir" "$app_dir" "$rootfs/usr/bin" "$rootfs/usr/share/applications" "$rootfs/usr/share/pixmaps"
    cp "$INPUT_JAR" "$app_dir/$PACKAGE_NAME.jar"
    cp "$INPUT_LAUNCHER_BIN" "$main_launcher"
    chmod 755 "$main_launcher"

    cp "$java_opts_template" "$bin_dir/desktop-java-opts.conf.template"
    cp "$app_args_template" "$bin_dir/desktop-app-args.conf.template"
    ln -s "../lib/$PACKAGE_NAME/bin/$PACKAGE_NAME" "$system_launcher"

    render_template \
        "$desktop_entry_template" \
        "$desktop_entry_path" \
        "$PACKAGE_NAME_PLACEHOLDER" "$PACKAGE_NAME" \
        "$APP_NAME_PLACEHOLDER" "$APP_NAME" \
        "$APP_DESCRIPTION_PLACEHOLDER" "$APP_DESCRIPTION"

    local icon_source="$ROOT_DIR/fastlane/metadata/android/en-US/images/icon.png"
    if [[ -f "$icon_source" ]]; then
        cp "$icon_source" "$rootfs/usr/share/pixmaps/$PACKAGE_NAME.png"
    fi
}

build_deb() {
    local rootfs="$1"
    local output_dir="$2"
    local work_dir="$3"
    local deb_root="$work_dir/deb-rootfs"
    local control_dir="$deb_root/DEBIAN"
    local control_template="$ROOT_DIR/release/linux/desktop/deb.control"
    local postinst_template="$ROOT_DIR/release/linux/desktop/deb.postinst"
    local postrm_template="$ROOT_DIR/release/linux/desktop/deb.postrm"
    local control_file="$control_dir/control"
    local postinst_file="$control_dir/postinst"
    local postrm_file="$control_dir/postrm"
    local output_path="$output_dir/${PACKAGE_NAME}_${VERSION_NAME}_${DEB_ARCH}.deb"
    local launcher_path="/usr/lib/$PACKAGE_NAME/bin/$PACKAGE_NAME"

    rm -rf "$deb_root"
    mkdir -p "$deb_root"
    cp -a "$rootfs/." "$deb_root/"
    mkdir -p "$control_dir"
    render_template \
        "$control_template" \
        "$control_file" \
        "$PACKAGE_NAME_PLACEHOLDER" "$PACKAGE_NAME" \
        "$VERSION_NAME_PLACEHOLDER" "$VERSION_NAME" \
        "$DEB_ARCH_PLACEHOLDER" "$DEB_ARCH" \
        "$MAINTAINER_PLACEHOLDER" "$MAINTAINER" \
        "$APP_DESCRIPTION_PLACEHOLDER" "$APP_DESCRIPTION"
    render_template \
        "$postinst_template" \
        "$postinst_file" \
        "$LAUNCHER_PATH_PLACEHOLDER" "$launcher_path" \
        "$LAUNCHER_CAPS_PLACEHOLDER" "$LAUNCHER_SETCAPS"
    render_template \
        "$postrm_template" \
        "$postrm_file" \
        "$LAUNCHER_PATH_PLACEHOLDER" "$launcher_path"
    chmod 755 "$postinst_file" "$postrm_file"

    dpkg-deb --root-owner-group --build "$deb_root" "$output_path"
    log "Built deb: $output_path"
}

build_rpm() {
    local rootfs="$1"
    local output_dir="$2"
    local work_dir="$3"
    local rpm_top="$work_dir/rpmbuild"
    local spec_template="$ROOT_DIR/release/linux/desktop/husi.spec"
    local rpm_version
    rpm_version="$(normalize_rpm_version "$VERSION_NAME")"
    local changelog_date
    changelog_date="$(LC_ALL=C date "+%a %b %d %Y")"
    local spec_file="$rpm_top/SPECS/${PACKAGE_NAME}.spec"
    local icon_target="$rootfs/usr/share/pixmaps/$PACKAGE_NAME.png"
    local icon_entry=""
    local launcher_path="/usr/lib/$PACKAGE_NAME/bin/$PACKAGE_NAME"

    mkdir -p "$rpm_top"/BUILD "$rpm_top"/BUILDROOT "$rpm_top"/RPMS "$rpm_top"/SOURCES "$rpm_top"/SPECS "$rpm_top"/SRPMS

    if [[ -f "$icon_target" ]]; then
        icon_entry="/usr/share/pixmaps/$PACKAGE_NAME.png"
    fi

    render_template \
        "$spec_template" \
        "$spec_file" \
        "$PACKAGE_NAME_PLACEHOLDER" "$PACKAGE_NAME" \
        "$RPM_VERSION_PLACEHOLDER" "$rpm_version" \
        "$PKGREL_PLACEHOLDER" "$PKGREL" \
        "$APP_DESCRIPTION_PLACEHOLDER" "$APP_DESCRIPTION" \
        "$APP_URL_PLACEHOLDER" "$APP_URL" \
        "$RPM_ARCH_PLACEHOLDER" "$RPM_ARCH" \
        "$PIXMAP_FILE_ENTRY_PLACEHOLDER" "$icon_entry" \
        "$CHANGELOG_DATE_PLACEHOLDER" "$changelog_date" \
        "$MAINTAINER_PLACEHOLDER" "$MAINTAINER" \
        "$LAUNCHER_PATH_PLACEHOLDER" "$launcher_path" \
        "$LAUNCHER_CAPS_PLACEHOLDER" "$LAUNCHER_SETCAPS"

    rpmbuild \
        --define "_topdir $rpm_top" \
        --define "husi_root $rootfs" \
        -bb "$spec_file"

    local rpm_output
    rpm_output="$(find "$rpm_top/RPMS" -type f -name '*.rpm' | head -n 1)"
    if [[ -z "$rpm_output" ]]; then
        error "Failed to locate built rpm package."
        exit 1
    fi
    cp "$rpm_output" "$output_dir/"
    log "Built rpm: $output_dir/$(basename "$rpm_output")"
}

build_pacman() {
    local rootfs="$1"
    local output_dir="$2"
    local work_dir="$3"
    local pacman_root="$work_dir/pacman-rootfs"
    local pkginfo_template="$ROOT_DIR/release/linux/desktop/.PKGINFO"
    local install_template="$ROOT_DIR/release/linux/desktop/pacman.install"
    local pacman_pkgver
    pacman_pkgver="$(normalize_pacman_version "$VERSION_NAME")"
    local pkgver_full="${pacman_pkgver}-${PKGREL}"
    local output_pkg="$output_dir/${PACKAGE_NAME}-${pacman_pkgver}-${PKGREL}-${PACMAN_ARCH}.pkg.tar.zst"
    local size_bytes
    size_bytes="$(du -sb "$rootfs" | awk '{print $1}')"
    local build_date
    build_date="$(date +%s)"
    local launcher_path="/usr/lib/$PACKAGE_NAME/bin/$PACKAGE_NAME"

    rm -rf "$pacman_root"
    mkdir -p "$pacman_root"
    cp -a "$rootfs/." "$pacman_root/"

    render_template \
        "$pkginfo_template" \
        "$pacman_root/.PKGINFO" \
        "$PACKAGE_NAME_PLACEHOLDER" "$PACKAGE_NAME" \
        "$PACMAN_VERSION_FULL_PLACEHOLDER" "$pkgver_full" \
        "$APP_DESCRIPTION_PLACEHOLDER" "$APP_DESCRIPTION" \
        "$APP_URL_PLACEHOLDER" "$APP_URL" \
        "$BUILD_DATE_PLACEHOLDER" "$build_date" \
        "$MAINTAINER_PLACEHOLDER" "$MAINTAINER" \
        "$SIZE_BYTES_PLACEHOLDER" "$size_bytes" \
        "$PACMAN_ARCH_PLACEHOLDER" "$PACMAN_ARCH"
    render_template \
        "$install_template" \
        "$pacman_root/.INSTALL" \
        "$LAUNCHER_PATH_PLACEHOLDER" "$launcher_path" \
        "$LAUNCHER_CAPS_PLACEHOLDER" "$LAUNCHER_SETCAPS"

    bsdtar --numeric-owner --uid 0 --gid 0 -C "$pacman_root" -cf - .PKGINFO .INSTALL usr | zstd -q -19 -T0 >"$output_pkg"
    log "Built pacman: $output_pkg"
}

FORMATS="deb,rpm,pacman"
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

if [[ -n "${ENABLED_FORMATS[deb]:-}" ]]; then
    build_deb "$rootfs" "$OUTPUT_DIR" "$work_dir"
fi

if [[ -n "${ENABLED_FORMATS[rpm]:-}" ]]; then
    build_rpm "$rootfs" "$OUTPUT_DIR" "$work_dir"
fi

if [[ -n "${ENABLED_FORMATS[pacman]:-}" ]]; then
    build_pacman "$rootfs" "$OUTPUT_DIR" "$work_dir"
fi

log "Done. Output directory: $OUTPUT_DIR"
