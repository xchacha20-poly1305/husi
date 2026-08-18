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

# Room needs a libsqliteJni for the target, and androidx sqlite-bundled has none for osx_x64.
DARWIN_AMD64_SQLITE_ISSUE="https://issuetracker.google.com/issues/495864182"

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

# anja -libname=husicore emits these names (see anja desktopLibraryFilename).
desktop_native_library_name() {
    local platform="$1"
    case "$platform" in
        windows)
            echo "husicore.dll"
            ;;
        darwin)
            echo "libhusicore.dylib"
            ;;
        *)
            echo "libhusicore.so"
            ;;
    esac
}

desktop_native_build_dir() {
    local desktop_target="$1"
    local platform="${desktop_target%%/*}"
    local arch="${desktop_target#*/}"
    echo "build/${platform}_${arch}"
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
        # Same reason as the Linux naive toolchain: keep zig's UBSan runtime, and
        # its 256 KiB thread-local signal stack, out of the shared library.
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
    local zig_target
    local script_dir
    if [ "$platform" == "darwin" ]; then
        apply_darwin_toolchain_env "$desktop_target"
        return
    fi
    case "$desktop_target" in
    linux/amd64)
        zig_target="x86_64-linux-gnu.2.31"
        ;;
    linux/arm64)
        zig_target="aarch64-linux-gnu.2.31"
        ;;
    *)
        echo "Unsupported naive desktop target without cronet-go toolchain: $desktop_target"
        exit 1
        ;;
    esac
    if ! command -v zig >/dev/null 2>&1; then
        echo "Missing zig compiler in PATH for naive desktop target $desktop_target"
        exit 1
    fi
    export HUSI_ZIG_TARGET="$zig_target"
    script_dir="$(cd "$(dirname "$0")" && pwd)"
    export CC="$script_dir/zig-cc.sh"
    export CXX="zig c++ -target $zig_target"
    # Zig links its own UBSan runtime by default, and that runtime keeps a 256 KiB
    # thread-local signal stack. Go marks a c-shared library DF_STATIC_TLS, so the
    # whole thread-local block has to fit in glibc's static TLS surplus (~1.6 KiB)
    # when the library is dlopen'd, and the load fails with
    # "cannot allocate memory in static TLS block".
    export CGO_CFLAGS="-O2 -fno-sanitize=undefined -fno-sanitize=integer"
    export CGO_CXXFLAGS="$CGO_CFLAGS"
    export CGO_LDFLAGS="-fuse-ld=lld"
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
        # Targets apply to --desktop (JNI jar + sidecar library).
        PLATFORM_SPECIFIED=1
        DESKTOP_TARGETS="$2"
        shift 2
        ;;
    --desktoptargets=*)
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

# --desktoptargets alone used to imply a desktop JNI jar build. Keep that when no
# explicit product mode is selected so older invocations still work.
if [ -n "$DESKTOP_TARGETS" ] && [ "$BUILD_DESKTOP" != "1" ] && [ "$BUILD_ANDROID" != "1" ]; then
    BUILD_DESKTOP=1
fi

# Just install anja & anjb if not have or version not same
go install tool

box_version=$(go run ./cmd/boxversion/)
husi_version="$(read_husi_version)"
export CGO_ENABLED=1
export GO386=softfloat

# Stamp sing-box version + husi Version (used by coreentry / HusiCoreMain).
anja_ldflags="-X github.com/sagernet/sing-box/constant.Version=${box_version} -X libcore.Version=${husi_version} -s -w -buildid="

ANJA_COMMON_ARGS=(
    -v
    -trimpath
    -buildvcs=false
    -javapkg="fr.husi"
)

ANJA_ANDROID_ARGS=(
    bind
    -target=android
    -androidapi
    23
    "${ANJA_COMMON_ARGS[@]}"
    -ldflags="$anja_ldflags -checklinkname=0" # https://github.com/golang/go/issues/70508
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
            # Cronet/naive toolchain for Linux and Darwin on the bind.
            apply_naive_toolchain_env "$desktop_target"
        fi
        desktop_args=("${ANJA_COMMON_ARGS[@]}" "-ldflags=$anja_ldflags" "-tags=$local_build_tags")
        if [ -n "$JNI_INCLUDE" ]; then
            desktop_args+=("-jniinclude=$JNI_INCLUDE")
        fi
        # One anja invocation produces the fat jar and a bare library for
        # packaging / session colocation (N7). -libname renames gojni → husicore.
        # -linkonly blank-imports coreentry in the generated main so HusiCoreMain
        # is linked without bindings and without an import cycle through
        # daemonhost → libcore.
        natives_staging="build/natives-out"
        natives_subdir="${desktop_platform}-${desktop_arch}"
        rm -rf "${natives_staging}/${natives_subdir}"
        mkdir -p "$natives_staging"
        anja bind -target=jvm \
            -desktoptargets "$desktop_target" \
            -libname=husicore \
            -linkonly=libcore/coreentry \
            -nativesout "$natives_staging" \
            "${desktop_args[@]}" \
            -o "$desktop_output" . || exit 1
        native_lib_name="$(desktop_native_library_name "$desktop_platform")"
        native_src="${natives_staging}/${natives_subdir}/${native_lib_name}"
        if [ ! -f "$native_src" ]; then
            echo "anja did not emit desktop native library at $native_src" >&2
            exit 1
        fi
        native_dst_dir="$(desktop_native_build_dir "$desktop_target")"
        mkdir -p "$native_dst_dir"
        cp -f "$native_src" "${native_dst_dir}/${native_lib_name}"
        echo ">> Sidecar $(realpath "${native_dst_dir}/${native_lib_name}")"
        sha256sum "${native_dst_dir}/${native_lib_name}"
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
