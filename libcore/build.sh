#!/usr/bin/env bash

set -e
# set -x

get_go_bin_version() {
    local bin_path="$1"
    go version -m "$bin_path" | awk '/^[ \t]*mod/ {print $3}' || echo "unknown"
}

# Returns 1 if same
compare_version() {
  local bin_path="$1"
  local expected_version="$2"
  local actual_version=$(get_go_bin_version "$bin_path")
  if [ "$actual_version" == "$expected_version" ]; then
    echo "1"
  else
    echo "0"
  fi
}

TAGS=(
    "with_conntrack"
    "with_gvisor"
    "with_quic"
    "with_wireguard"
    "with_utls"
    "with_naive_outbound"
)

IFS="," BUILD_TAGS="${TAGS[*]}"

BUILD_DESKTOP=0
BUILD_ANDROID=0
PLATFORM_SPECIFIED=0
DESKTOP_TARGETS=""
DESKTOP_OUTPUTS=()

resolve_host_desktop_target() {
    local host_os
    local host_arch
    host_os="$(go env GOOS)"
    host_arch="$(go env GOARCH)"
    echo "${host_os}/${host_arch}"
}

desktop_jar_name() {
    local desktop_target="$1"
    local platform="${desktop_target%%/*}"
    local arch="${desktop_target#*/}"
    if [ "$platform" == "$arch" ]; then
        echo "libcore-desktop-${platform}.jar"
        return
    fi
    echo "libcore-desktop-${platform}-${arch}.jar"
}

while [ "$#" -gt 0 ]; do
    case "$1" in
    --desktop)
        BUILD_DESKTOP=1
        PLATFORM_SPECIFIED=1
        shift
        ;;
    --android)
        BUILD_ANDROID=1
        PLATFORM_SPECIFIED=1
        shift
        ;;
    --desktoptargets)
        if [ -z "$2" ]; then
            echo "Missing value for --desktoptargets"
            exit 1
        fi
        BUILD_DESKTOP=1
        PLATFORM_SPECIFIED=1
        DESKTOP_TARGETS="$2"
        shift 2
        ;;
    --desktoptargets=*)
        BUILD_DESKTOP=1
        PLATFORM_SPECIFIED=1
        DESKTOP_TARGETS="${1#*=}"
        shift
        ;;
    *)
        echo "Unknown argument: $1"
        exit 1
        ;;
    esac
done

if [ "$PLATFORM_SPECIFIED" == "0" ]; then
    BUILD_ANDROID=1
fi

# Just install gomobile & gobind if not have or version not same
GOMOBILE_VERSION="v0.1.12-0.20260222013644-e0aa60aa8e68"
if [ "$(compare_version "$(command -v anja)" "$GOMOBILE_VERSION")" == "0" ]; then
    echo ">> Installing gomobile"
    CGO_ENABLED=0 go install -v -trimpath -ldflags="-w -s" github.com/xchacha20-poly1305/anja/cmd/anja@$GOMOBILE_VERSION
fi
if [ "$(compare_version "$(command -v anjb)" "$GOMOBILE_VERSION")" == "0" ]; then
    echo ">> Installing gobind"
    CGO_ENABLED=0 go install -v -trimpath -ldflags="-w -s" github.com/xchacha20-poly1305/anja/cmd/anjb@$GOMOBILE_VERSION
fi

box_version=$(go run ./cmd/boxversion/)
export CGO_ENABLED=1
export GO386=softfloat

ANJA_COMMON_ARGS=(
    -v
    -trimpath
    -buildvcs=false
    -ldflags="-X github.com/sagernet/sing-box/constant.Version=${box_version} -s -w -buildid="
    -javapkg="fr.husi"
    -tags="$BUILD_TAGS"
)

ANJA_ANDROID_ARGS=(
    bind
    -target=android
    -androidapi
    23
    "${ANJA_COMMON_ARGS[@]}"
)

if [ "$BUILD_ANDROID" == "1" ]; then
    if [ -f libcore.aar ]; then
        rm -f libcore.aar
    fi
    if [ -f libcore-sources.jar ]; then
        rm -f libcore-sources.jar
    fi
    # -buildvcs require: https://github.com/SagerNet/gomobile/commit/6bc27c2027e816ac1779bf80058b1a7710dad260
    anja "${ANJA_ANDROID_ARGS[@]}" . || exit 1
fi

if [ "$BUILD_DESKTOP" == "1" ]; then
    if [ -z "$DESKTOP_TARGETS" ]; then
        DESKTOP_TARGETS="host"
    fi
    IFS="," read -r -a desktop_target_list <<< "$DESKTOP_TARGETS"
    for desktop_target in "${desktop_target_list[@]}"; do
        desktop_target="${desktop_target//[[:space:]]/}"
        if [ -z "$desktop_target" ]; then
            continue
        fi
        if [ "$desktop_target" == "host" ]; then
            desktop_target="$(resolve_host_desktop_target)"
        fi
        desktop_output="$(desktop_jar_name "$desktop_target")"
        if [ -f "$desktop_output" ]; then
            rm -f "$desktop_output"
        fi
        anja bind -target=jvm \
            -desktoptargets "$desktop_target" \
            "${ANJA_COMMON_ARGS[@]}" \
            -o "$desktop_output" . || exit 1
        DESKTOP_OUTPUTS+=("$desktop_output")
    done
fi

proj=../composeApp/libs
mkdir -p $proj
if [ "$BUILD_ANDROID" == "1" ]; then
    cp -f libcore.aar $proj
    echo ">> Installed $(realpath $proj)/libcore.aar"
    sha256sum libcore.aar
fi

if [ "$BUILD_DESKTOP" == "1" ]; then
    for desktop_output in "${DESKTOP_OUTPUTS[@]}"; do
        cp -f "$desktop_output" $proj
        echo ">> Installed $(realpath $proj)/$desktop_output"
        sha256sum "$desktop_output"
    done
fi
