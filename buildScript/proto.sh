#!/usr/bin/env bash

set -euo pipefail

if ! command -v protoc >/dev/null 2>&1; then
  echo "protoc not found in PATH. Install it with your package manager (e.g. pacman -S protobuf)." >&2
  exit 1
fi

# The protoc plugins are pinned by libcore/go.mod rather than by whatever the
# developer happens to have installed, so that generated code stays stable.
PLUGIN_DIR="$PWD/build/protoc-plugins"
mkdir -p "$PLUGIN_DIR"
(
  cd libcore
  go build -o "$PLUGIN_DIR" \
    google.golang.org/protobuf/cmd/protoc-gen-go \
    google.golang.org/grpc/cmd/protoc-gen-go-grpc
)

SING_BOX_MODULE="github.com/sagernet/sing-box"
# The schema is read from the module's source, so it has to be in the module
# cache first: `go list -m` reports an empty Dir for a module never downloaded
# rather than failing, which on a cold cache would leave the vendored copy to be
# built from a path with no root.
(cd libcore && go mod download "$SING_BOX_MODULE")
SING_BOX_DIR="$(cd libcore && go list -m -f '{{.Dir}}' "$SING_BOX_MODULE")"
SING_BOX_VERSION="$(cd libcore && go list -m -f '{{.Version}}' "$SING_BOX_MODULE")"

if [ -z "$SING_BOX_DIR" ] || [ ! -d "$SING_BOX_DIR" ]; then
  echo "Cannot locate $SING_BOX_MODULE sources: '$SING_BOX_DIR'" >&2
  exit 1
fi

# The rpcs husi calls on daemon.StartedService, which is what the vendored copy
# is trimmed down to. This is the same list as the method constants in
# composeApp .../fr/husi/core/CoreClient.kt; using a new upstream rpc means
# adding it here and re-running `make proto`.
KEEP_STARTED_SERVICE_RPCS=(
  GetVersion GetStartedAt URLTest
  SubscribeServiceStatus SubscribeStatus
  SubscribeLog GetDefaultLogLevel ClearLogs
  SubscribeGroups SubscribeOutbounds SelectOutbound SetGroupExpand
  GetClashModeStatus SubscribeClashMode SetClashMode
  SubscribeConnections CloseConnection CloseAllConnections
  SubscribeOpenConnectStatus SubmitOpenConnectAuthResponse CancelOpenConnectAuthChallenge
  SubscribeOpenVPNStatus SubmitOpenVPNChallengeResponse CancelOpenVPNChallenge
)

# Husi speaks sing-box's own contract on the wire, so the core-scoped schema is
# copied from the pinned sing-box rather than rewritten. libcore/cmd/prototrim
# keeps only the rpcs above and the types they reach, because the whole tree is
# compiled into JVM classes shipped in the app. Only Java options are added:
# they name those classes and never reach the wire, which is what keeps the copy
# interchangeable with the original.
vendor_sing_box_proto() {
  local relative_path="$1" outer_classname="$2"
  local destination="proto/$relative_path"
  shift 2  # What remains is the rpc allowlist.

  mkdir -p "$(dirname "$destination")"
  {
    echo "// Vendored from $SING_BOX_MODULE $SING_BOX_VERSION by \`make proto\`."
    echo "// Trimmed to the RPCs husi actually calls; the rest of the upstream"
    echo "// schema is not copied."
    echo "// Do not edit: husi is wire compatible with sing-box here, so every"
    echo "// package, message name, field number and type must stay identical."
    echo
    (cd libcore && go run ./cmd/prototrim "$SING_BOX_DIR/$relative_path" "$outer_classname" "$@")
  } >"$destination"
}

vendor_sing_box_proto daemon/started_service.proto StartedServiceProto \
  "${KEEP_STARTED_SERVICE_RPCS[@]}"

export PATH="$PLUGIN_DIR:$PATH"

GO_OUT="$PWD/libcore/pb"

# Clean previously generated files so removed messages don't linger.
find "$GO_OUT/husi" -name '*.go' -delete 2>/dev/null || true

# Go stubs are generated for husi's own schema only. The vendored schema is
# already compiled into the sing-box module, and registering a second copy of
# the same file descriptor would panic the protobuf registry at startup.
protoc \
  -I proto \
  --go_out="$GO_OUT" --go_opt=paths=source_relative \
  --go-grpc_out="$GO_OUT" --go-grpc_opt=paths=source_relative \
  proto/husi/v1/*.proto
