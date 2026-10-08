#!/usr/bin/env bash

set -e
# set -x

TAGS=(
    "with_quic"
    "with_wireguard"
    "with_openconnect"
    "with_openvpn"
    "with_utls"
    "with_naive_outbound"
    "badlinkname"
    "tfogo_checklinkname0"
)

IFS="," BUILD_TAGS="${TAGS[*]}"

# Room needs a libsqliteJni for the target, and androidx sqlite-bundled has none for osx_x64.
DARWIN_AMD64_SQLITE_ISSUE="https://issuetracker.google.com/issues/495864182"

BUILD_DESKTOP=0
BUILD_ANDROID=0
PLATFORM_SPECIFIED=0
DESKTOP_TARGETS=""
EXTERNAL_DARWIN_SDKROOT="${DARWIN_SDKROOT:-${SDKROOT:-}}"
EXTERNAL_MACOSX_DEPLOYMENT_TARGET="${DARWIN_MACOSX_DEPLOYMENT_TARGET:-${MACOSX_DEPLOYMENT_TARGET:-}}"
DARWIN_SDKROOT="$EXTERNAL_DARWIN_SDKROOT"
ANDROID_MIN_API=24

# gomobile only recognizes SDK platforms named android-<N>: an SDK that holds
# nothing but minor releases such as android-37.2 looks empty to it. Build a
# shadow SDK that links every entry of the real one, except that platforms/
# holds only the newest platform at or above the minimum API, under its major
# number. Prints the shadow SDK path.
make_gomobile_android_home() {
    local sdk="${ANDROID_HOME:-}"
    local best_dir=""
    local best_version=""
    local platform_dir
    local version
    local entry
    local shadow

    for platform_dir in "$sdk"/platforms/android-*; do
        [ -f "$platform_dir/android.jar" ] || continue
        version="${platform_dir##*/android-}"
        [[ "$version" =~ ^[0-9]+(\.[0-9]+)?$ ]] || continue
        [ "${version%%.*}" -ge "$ANDROID_MIN_API" ] || continue
        if [ -z "$best_version" ] || [ "$(printf '%s\n%s\n' "$best_version" "$version" | sort -V | tail -n 1)" == "$version" ]; then
            best_dir="$platform_dir"
            best_version="$version"
        fi
    done
    if [ -z "$best_dir" ]; then
        echo "No Android SDK platform with API >= $ANDROID_MIN_API under ${sdk:-\$ANDROID_HOME}/platforms" >&2
        return 1
    fi

    shadow="$(mktemp -d)"
    for entry in "$sdk"/*; do
        [ "${entry##*/}" == "platforms" ] && continue
        ln -s "$entry" "$shadow/${entry##*/}"
    done
    mkdir "$shadow/platforms"
    ln -s "$best_dir" "$shadow/platforms/android-${best_version%%.*}"
    echo "$shadow"
}

resolve_host_desktop_target() {
    local host_os
    local host_arch
    host_os="$(go env GOOS)"
    host_arch="$(go env GOARCH)"
    echo "${host_os}/${host_arch}"
}

desktop_build_dir() {
    local desktop_target="$1"
    local platform="${desktop_target%%/*}"
    local arch="${desktop_target#*/}"
    echo "build/${platform}_${arch}"
}

# macOS may lack the coreutils sha256sum; its shasum prints the same line.
print_sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1"
        return
    fi
    shasum -a 256 "$1"
}

read_husi_version() {
    local properties_file="../husi.properties"
    local version=""
    if [ -f "$properties_file" ]; then
        version="$(awk -F= '$1=="VERSION_NAME"{print $2; exit}' "$properties_file" | tr -d '\r')"
    fi
    if [ -z "$version" ]; then
        version="dev"
    fi
    echo "$version"
}

add_build_tag() {
    local build_tags="$1"
    local add_tag="$2"
    local tag
    IFS="," read -r -a input_tags <<< "$build_tags"
    for tag in "${input_tags[@]}"; do
        if [ "$tag" == "$add_tag" ]; then
            echo "$build_tags"
            return
        fi
    done
    if [ -z "$build_tags" ]; then
        echo "$add_tag"
        return
    fi
    echo "$build_tags,$add_tag"
}

apply_darwin_toolchain_env() {
    local desktop_target="$1"
    local host_platform
    local arch="${desktop_target#*/}"
    local deployment_target
    local sdk_root
    local clang_arch
    local zig_target
    local clang_bin
    local clang_bin_cxx

    host_platform="$(go env GOOS)"

    case "$arch" in
    arm64)
        clang_arch="arm64"
        zig_target="aarch64-macos"
        ;;
    amd64)
        echo "darwin/amd64 is dropped: androidx sqlite-bundled has no osx_x64 binary, see $DARWIN_AMD64_SQLITE_ISSUE"
        exit 1
        ;;
    *)
        echo "Unsupported Darwin desktop target: $desktop_target"
        exit 1
        ;;
    esac

    # Darwin cgo packages with Objective-C sources add -lobjc per package.
    # Keep zig/lld from preserving repeated direct dylib load commands.
    local dead_strip_dylibs="-Wl,-dead_strip_dylibs"

    if [ "$host_platform" != "darwin" ]; then
        local framework_root sdk_include_root
        if ! command -v zig >/dev/null 2>&1; then
            echo "Missing zig compiler in PATH for Darwin desktop target $desktop_target"
            exit 1
        fi
        if [ -z "$DARWIN_SDKROOT" ]; then
            echo "Missing Darwin SDK root for desktop target $desktop_target on non-Darwin host"
            echo "Pass --darwinsdk /path/to/MacOSX.sdk or set DARWIN_SDKROOT/SDKROOT."
            exit 1
        fi
        if [ ! -d "$DARWIN_SDKROOT" ]; then
            echo "Missing Darwin SDK root: $DARWIN_SDKROOT"
            exit 1
        fi
        framework_root="$DARWIN_SDKROOT/System/Library/Frameworks"
        sdk_include_root="$DARWIN_SDKROOT/usr/include"
        if [ ! -d "$framework_root" ]; then
            echo "Missing Darwin frameworks under $framework_root"
            exit 1
        fi
        if [ ! -d "$sdk_include_root" ]; then
            echo "Missing Darwin SDK headers under $sdk_include_root"
            exit 1
        fi
        export SDKROOT="$DARWIN_SDKROOT"
        export CC="zig cc -target $zig_target"
        export CXX="zig c++ -target $zig_target"
        # Zig links its own UBSan runtime by default; a release binary has no use for it.
        export CGO_CFLAGS="-isysroot $SDKROOT -isystem $sdk_include_root -F$framework_root -Wno-deprecated-declarations -fno-sanitize=undefined -fno-sanitize=integer"
        export CGO_CXXFLAGS="$CGO_CFLAGS"
        export CGO_LDFLAGS="-isysroot $SDKROOT -L$SDKROOT/usr/lib -F$framework_root $dead_strip_dylibs"
        if [ -n "$EXTERNAL_MACOSX_DEPLOYMENT_TARGET" ]; then
            export MACOSX_DEPLOYMENT_TARGET="$EXTERNAL_MACOSX_DEPLOYMENT_TARGET"
            export CGO_CFLAGS="$CGO_CFLAGS -mmacos-version-min=$MACOSX_DEPLOYMENT_TARGET"
            export CGO_CXXFLAGS="$CGO_CFLAGS"
            export CGO_LDFLAGS="$CGO_LDFLAGS -mmacos-version-min=$MACOSX_DEPLOYMENT_TARGET"
        fi
        return
    fi

    if ! command -v xcrun >/dev/null 2>&1; then
        echo "Missing Xcode command-line tools for Darwin desktop target $desktop_target"
        exit 1
    fi
    sdk_root="$(xcrun --sdk macosx --show-sdk-path)"
    clang_bin="$(xcrun --sdk macosx --find clang)"
    clang_bin_cxx="$(xcrun --sdk macosx --find clang++)"
    if [ -z "$sdk_root" ] || [ ! -d "$sdk_root" ] || [ ! -x "$clang_bin" ] || [ ! -x "$clang_bin_cxx" ]; then
        echo "Unable to resolve the macOS SDK and clang toolchain with xcrun"
        exit 1
    fi
    deployment_target="$EXTERNAL_MACOSX_DEPLOYMENT_TARGET"
    if [ -z "$deployment_target" ]; then
        deployment_target="12.0"
    fi
    export SDKROOT="$sdk_root"
    export MACOSX_DEPLOYMENT_TARGET="$deployment_target"
    export CC="$clang_bin --target=${clang_arch}-apple-macos"
    export CXX="$clang_bin_cxx --target=${clang_arch}-apple-macos"
    export CGO_CFLAGS="-isysroot $SDKROOT -mmacos-version-min=$MACOSX_DEPLOYMENT_TARGET -Wno-deprecated-declarations"
    export CGO_CXXFLAGS="$CGO_CFLAGS"
    export CGO_LDFLAGS="-isysroot $SDKROOT -mmacos-version-min=$MACOSX_DEPLOYMENT_TARGET $dead_strip_dylibs"
}

# Linux and Windows load Cronet through purego, so husi-core needs no cgo there
# and the shared library ships beside it instead: the loader looks in the
# executable's own directory first. Apple platforms have no purego Cronet.
cronet_uses_purego() {
    local desktop_platform="$1"
    local build_tags="$2"
    [[ ",$build_tags," == *",with_naive_outbound,"* ]] || return 1
    [ "$desktop_platform" == "linux" ] || [ "$desktop_platform" == "windows" ]
}

cronet_library_name() {
    local desktop_platform="$1"
    if [ "$desktop_platform" == "windows" ]; then
        echo "libcronet.dll"
        return
    fi
    echo "libcronet.so"
}

# Copies the prebuilt Cronet shared library that go.mod pins for the target
# next to husi-core.
install_cronet_library() {
    local desktop_target="$1"
    local output_dir="$2"
    local desktop_platform="${desktop_target%%/*}"
    local desktop_arch="${desktop_target#*/}"
    local library_module="github.com/sagernet/cronet-go/lib/${desktop_platform}_${desktop_arch}"
    local library_name
    local module_dir
    library_name="$(cronet_library_name "$desktop_platform")"
    module_dir="$(go list -m -f '{{.Dir}}' "$library_module")"
    if [ -z "$module_dir" ] || [ ! -f "$module_dir/$library_name" ]; then
        echo "Missing $library_name in $library_module" >&2
        exit 1
    fi
    # The module cache is read-only; the copy must stay replaceable.
    install -m 644 "$module_dir/$library_name" "$output_dir/$library_name"
    echo ">> Installed $(realpath "$output_dir/$library_name")"
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
        # Targets apply to --desktop (the husi-core binary).
        PLATFORM_SPECIFIED=1
        DESKTOP_TARGETS="$2"
        shift 2
        ;;
    --desktoptargets=*)
        PLATFORM_SPECIFIED=1
        DESKTOP_TARGETS="${1#*=}"
        shift
        ;;
    --darwinsdk)
        if [ -z "$2" ]; then
            echo "Missing value for --darwinsdk"
            exit 1
        fi
        DARWIN_SDKROOT="$2"
        shift 2
        ;;
    --darwinsdk=*)
        DARWIN_SDKROOT="${1#*=}"
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

# --desktoptargets alone implies a desktop build when no explicit product mode
# is selected.
if [ -n "$DESKTOP_TARGETS" ] && [ "$BUILD_DESKTOP" != "1" ] && [ "$BUILD_ANDROID" != "1" ]; then
    BUILD_DESKTOP=1
fi

if [ "$BUILD_ANDROID" == "1" ]; then
    # gomobile runs the gobind on PATH: install both at the versions go.mod pins.
    go install tool
fi

box_version="$(go list -m -f '{{.Version}}' github.com/sagernet/sing-box)"
if [ -z "$box_version" ]; then
    echo "Unable to determine sing-box version from go.mod" >&2
    exit 1
fi
husi_version="$(read_husi_version)"
export CGO_ENABLED=1
export GO386=softfloat

# `badlinkname` and `tfogo_checklinkname0` pull unexported symbols, which the linker rejects
# without -checklinkname=0: https://github.com/golang/go/issues/70508
common_ldflags="-X github.com/sagernet/sing-box/constant.Version=${box_version} -s -w -buildid= -checklinkname=0"
# husi-core reports the husi release it belongs to.
desktop_ldflags="$common_ldflags -X main.version=${husi_version}"

GOMOBILE_ANDROID_ARGS=(
    bind
    -target=android
    -androidapi
    "$ANDROID_MIN_API"
    -v
    -trimpath
    -buildvcs=false
    -javapkg="fr.husi"
    -ldflags="$common_ldflags"
    -tags="$BUILD_TAGS"
)

if [ "$BUILD_ANDROID" == "1" ]; then
    if [ -f libcore.aar ]; then
        rm -f libcore.aar
    fi
    if [ -f libcore-sources.jar ]; then
        rm -f libcore-sources.jar
    fi
    # -buildvcs require: https://github.com/SagerNet/gomobile/commit/6bc27c2027e816ac1779bf80058b1a7710dad260
    gomobile_android_home="$(make_gomobile_android_home)" || exit 1
    trap 'rm -rf "${gomobile_android_home:?}"' EXIT
    ANDROID_HOME="$gomobile_android_home" gomobile "${GOMOBILE_ANDROID_ARGS[@]}" . || exit 1
fi

if [ "$BUILD_DESKTOP" == "1" ]; then
    if [ -z "$DESKTOP_TARGETS" ]; then
        DESKTOP_TARGETS="host"
    fi
    IFS="," read -r -a desktop_target_list <<< "$DESKTOP_TARGETS"
    for desktop_target in "${desktop_target_list[@]}"; do
        local_build_tags="$BUILD_TAGS"
        desktop_target="${desktop_target//[[:space:]]/}"
        if [ -z "$desktop_target" ]; then
            continue
        fi
        if [ "$desktop_target" == "host" ]; then
            desktop_target="$(resolve_host_desktop_target)"
        fi
        desktop_platform="${desktop_target%%/*}"
        desktop_arch="${desktop_target#*/}"
        unset CC CXX SDKROOT MACOSX_DEPLOYMENT_TARGET CGO_CFLAGS CGO_CXXFLAGS CGO_LDFLAGS
        desktop_cgo_enabled=0
        if [ "$desktop_platform" == "darwin" ]; then
            # Besides Cronet, sing-box reads the system certificate store and DNS
            # configuration on Darwin only through cgo.
            desktop_cgo_enabled=1
            apply_darwin_toolchain_env "$desktop_target"
        elif cronet_uses_purego "$desktop_platform" "$local_build_tags"; then
            local_build_tags="$(add_build_tag "$local_build_tags" "with_purego")"
        fi
        binary_name="husi-core"
        if [ "$desktop_platform" == "windows" ]; then
            binary_name="husi-core.exe"
        fi
        output="$(desktop_build_dir "$desktop_target")/$binary_name"
        mkdir -p "$(dirname "$output")"
        CGO_ENABLED="$desktop_cgo_enabled" GOOS="$desktop_platform" GOARCH="$desktop_arch" go build -v -trimpath -buildvcs=false \
            -ldflags="$desktop_ldflags" \
            -tags="$local_build_tags" \
            -o "$output" ./cmd/husi-core || exit 1
        echo ">> Built $(realpath "$output")"
        print_sha256 "$output"
        if cronet_uses_purego "$desktop_platform" "$local_build_tags"; then
            install_cronet_library "$desktop_target" "$(dirname "$output")"
        fi
    done
fi

if [ "$BUILD_ANDROID" == "1" ]; then
    proj=../composeApp/libs
    mkdir -p $proj
    cp -f libcore.aar $proj
    echo ">> Installed $(realpath $proj)/libcore.aar"
    print_sha256 libcore.aar
fi
