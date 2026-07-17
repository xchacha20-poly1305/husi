#!/usr/bin/env bash

set -e
# set -x

TAGS=(
    "with_gvisor"
    "with_quic"
    "with_wireguard"
    "with_openconnect"
    "with_openvpn"
    "with_utls"
    "with_naive_outbound"
)

IFS="," BUILD_TAGS="${TAGS[*]}"

BUILD_DESKTOP=0
BUILD_ANDROID=0
PLATFORM_SPECIFIED=0
DESKTOP_TARGETS=""
DESKTOP_OUTPUTS=()
JNI_INCLUDE=""
EXTERNAL_DARWIN_SDKROOT="${DARWIN_SDKROOT:-${SDKROOT:-}}"
EXTERNAL_MACOSX_DEPLOYMENT_TARGET="${DARWIN_MACOSX_DEPLOYMENT_TARGET:-${MACOSX_DEPLOYMENT_TARGET:-}}"
DARWIN_SDKROOT="$EXTERNAL_DARWIN_SDKROOT"

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

remove_build_tag() {
    local build_tags="$1"
    local remove_tag="$2"
    local tag
    local kept_tags=()
    IFS="," read -r -a input_tags <<< "$build_tags"
    for tag in "${input_tags[@]}"; do
        if [ "$tag" == "$remove_tag" ]; then
            continue
        fi
        kept_tags+=("$tag")
    done
    local IFS=","
    echo "${kept_tags[*]}"
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

read_gn_string_var() {
    local file="$1"
    local key="$2"
    sed -n "s/^[[:space:]]*$key = \"\\([^\"]*\\)\".*/\\1/p" "$file" | head -n1
}

resolve_cronet_go_root() {
    if [ -n "$CRONET_GO_ROOT" ]; then
        echo "$CRONET_GO_ROOT"
        return
    fi

    local repo_default="../../cronet-go"
    local home_default="$HOME/cronet-go"

    if [ -d "$repo_default" ]; then
        echo "$repo_default"
        return
    fi

    echo "$home_default"
}

apply_darwin_toolchain_env() {
    local desktop_target="$1"
    local host_platform
    local arch="${desktop_target#*/}"
    local sdk_version
    local deployment_target
    local sdk_root
    local mac_bin_path
    local clang_arch
    local zig_target
    local cronet_go_root
    local src_root
    local clang_bin
    local xcode_root
    local developer_root
    local mac_sdk_gni

    host_platform="$(go env GOOS)"

    case "$arch" in
    arm64)
        clang_arch="arm64"
        zig_target="aarch64-macos"
        ;;
    amd64)
        clang_arch="x86_64"
        zig_target="x86_64-macos"
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
        export CGO_CFLAGS="-isysroot $SDKROOT -isystem $sdk_include_root -F$framework_root -Wno-deprecated-declarations"
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

    cronet_go_root="$(resolve_cronet_go_root)"
    if [ ! -d "$cronet_go_root" ]; then
        echo "Missing cronet-go root: $cronet_go_root"
        echo "Set CRONET_GO_ROOT or place cronet-go at ../../cronet-go or ~/cronet-go for desktop target $desktop_target."
        exit 1
    fi

    src_root="$cronet_go_root/naiveproxy/src"
    clang_bin="$src_root/third_party/llvm-build/Release+Asserts/bin"
    xcode_root="$src_root/build/mac_files/xcode_binaries"
    developer_root="$xcode_root/Contents/Developer"
    mac_sdk_gni="$src_root/build/config/mac/mac_sdk.gni"

    if [ ! -x "$clang_bin/clang" ] || [ ! -x "$clang_bin/clang++" ]; then
        echo "Missing Chromium clang toolchain in $clang_bin"
        echo "Prepare the cronet-go toolchain checkout before building desktop target $desktop_target."
        exit 1
    fi
    if [ ! -d "$xcode_root" ]; then
        echo "Missing hermetic Xcode toolchain in $xcode_root"
        echo "Prepare the Darwin SDK/linker toolchain in the cronet-go checkout before building $desktop_target."
        exit 1
    fi

    sdk_version="$(read_gn_string_var "$mac_sdk_gni" "mac_sdk_official_version")"
    deployment_target="$(read_gn_string_var "$mac_sdk_gni" "mac_deployment_target")"
    if [ -z "$sdk_version" ] || [ -z "$deployment_target" ]; then
        echo "Failed to read Darwin SDK configuration from $mac_sdk_gni"
        exit 1
    fi

    sdk_root="$developer_root/Platforms/MacOSX.platform/Developer/SDKs/MacOSX${sdk_version}.sdk"
    if [ ! -d "$sdk_root" ]; then
        sdk_root="$developer_root/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
    fi
    if [ ! -d "$sdk_root" ]; then
        echo "Missing macOS SDK under $developer_root/Platforms/MacOSX.platform/Developer/SDKs"
        exit 1
    fi

    mac_bin_path="$developer_root/Toolchains/XcodeDefault.xctoolchain/usr/bin"
    if [ ! -d "$mac_bin_path" ]; then
        echo "Missing Darwin linker toolchain path: $mac_bin_path"
        exit 1
    fi

    export SDKROOT="$sdk_root"
    export MACOSX_DEPLOYMENT_TARGET="$deployment_target"
    export CC="$clang_bin/clang --target=${clang_arch}-apple-macos -B$mac_bin_path"
    export CXX="$clang_bin/clang++ --target=${clang_arch}-apple-macos -B$mac_bin_path"
    export CGO_CFLAGS="-isysroot $SDKROOT -mmacos-version-min=$MACOSX_DEPLOYMENT_TARGET -Wno-deprecated-declarations"
    export CGO_CXXFLAGS="$CGO_CFLAGS"
    export CGO_LDFLAGS="-isysroot $SDKROOT -mmacos-version-min=$MACOSX_DEPLOYMENT_TARGET $dead_strip_dylibs"
}

apply_windows_toolchain_env() {
    local desktop_target="$1"
    local host_platform
    local arch="${desktop_target#*/}"
    local zig_target

    host_platform="$(go env GOOS)"

    case "$arch" in
    arm64)
        zig_target="aarch64-windows-gnu"
        ;;
    amd64)
        zig_target="x86_64-windows-gnu"
        ;;
    *)
        echo "Unsupported Windows desktop target: $desktop_target"
        exit 1
        ;;
    esac

    if [ "$host_platform" == "windows" ]; then
        return
    fi

    if ! command -v zig >/dev/null 2>&1; then
        echo "Missing zig compiler in PATH for Windows desktop target $desktop_target"
        exit 1
    fi

    export CC="zig cc -target $zig_target"
    export CXX="zig c++ -target $zig_target"
    export CGO_CFLAGS="-O2 -fno-sanitize=undefined -fno-sanitize=integer"
    export CGO_CXXFLAGS="$CGO_CFLAGS"
}

apply_naive_toolchain_env() {
    local desktop_target="$1"
    local platform="${desktop_target%%/*}"
    if [ "$platform" == "darwin" ]; then
        apply_darwin_toolchain_env "$desktop_target"
        return
    fi
    local cronet_go_root
    cronet_go_root="$(resolve_cronet_go_root)"
    if [ ! -d "$cronet_go_root" ]; then
        echo "Missing cronet-go root: $cronet_go_root"
        echo "Set CRONET_GO_ROOT or place cronet-go at ../../cronet-go or ~/cronet-go for desktop target $desktop_target."
        exit 1
    fi
    local exported_env
    if ! exported_env="$(
        cd "$cronet_go_root" &&
            go run ./cmd/build-naive --target="$desktop_target" env --export
    )"; then
        echo "Failed to load cronet-go toolchain environment for desktop target $desktop_target from $cronet_go_root"
        exit 1
    fi
    if [ -n "$exported_env" ]; then
        eval "$exported_env"
    fi
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
    --jniinclude)
        if [ -z "$2" ]; then
            echo "Missing value for --jniinclude"
            exit 1
        fi
        JNI_INCLUDE="$2"
        shift 2
        ;;
    --jniinclude=*)
        JNI_INCLUDE="${1#*=}"
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

# Just install anja & anjb if not have or version not same
go install tool

box_version=$(go run ./cmd/boxversion/)
export CGO_ENABLED=1
export GO386=softfloat

ANJA_COMMON_ARGS=(
    -v
    -trimpath
    -buildvcs=false
    -ldflags="-X github.com/sagernet/sing-box/constant.Version=${box_version} -s -w -buildid="
    -javapkg="fr.husi"
)

ANJA_ANDROID_ARGS=(
    bind
    -target=android
    -androidapi
    23
    "${ANJA_COMMON_ARGS[@]}"
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
    anja "${ANJA_ANDROID_ARGS[@]}" . || exit 1
fi

if [ "$BUILD_DESKTOP" == "1" ]; then
    if [ -z "$DESKTOP_TARGETS" ]; then
        DESKTOP_TARGETS="host"
    fi
    host_platform="$(go env GOOS)"
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
        if [ "$desktop_platform" == "windows" ] && [[ ",$local_build_tags," == *",with_naive_outbound,"* ]]; then
            local_build_tags="$(add_build_tag "$local_build_tags" "with_purego")"
        fi
        desktop_output="$(desktop_jar_name "$desktop_target")"
        if [ -f "$desktop_output" ]; then
            rm -f "$desktop_output"
        fi
        unset CC CXX SDKROOT MACOSX_DEPLOYMENT_TARGET CGO_CFLAGS CGO_CXXFLAGS CGO_LDFLAGS QEMU_LD_PREFIX
        if [ "$desktop_platform" == "windows" ]; then
            apply_windows_toolchain_env "$desktop_target"
        elif [[ ",$local_build_tags," == *",with_naive_outbound,"* ]]; then
            apply_naive_toolchain_env "$desktop_target"
        elif [ "$host_platform" != "darwin" ] && [ "$desktop_platform" == "darwin" ]; then
            apply_darwin_toolchain_env "$desktop_target"
        fi
        desktop_args=("${ANJA_COMMON_ARGS[@]}" "-tags=$local_build_tags")
        if [ -n "$JNI_INCLUDE" ]; then
            desktop_args+=("-jniinclude=$JNI_INCLUDE")
        fi
        anja bind -target=jvm \
            -desktoptargets "$desktop_target" \
            "${desktop_args[@]}" \
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
