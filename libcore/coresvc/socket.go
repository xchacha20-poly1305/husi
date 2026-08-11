package coresvc

import (
	"path/filepath"
	"strings"
)

const Socket = "api.sock"

// DaemonPipePath is the fixed Windows named-pipe endpoint for the privileged
// daemon (must match daemonhost.DefaultDaemonPipePath).
const DaemonPipePath = `\\.\pipe\ProtectedPrefix\Administrators\husi`

func SocketPath(basePath string) string {
	return filepath.Join(basePath, Socket)
}

func IsWindowsPipePath(path string) bool {
	const prefix = `\\.\pipe\`
	if len(path) < len(prefix) {
		return false
	}
	return strings.EqualFold(path[:len(prefix)], prefix)
}

func ClientEndpoint(basePath string) string {
	if IsWindowsPipePath(basePath) {
		return basePath
	}
	return SocketPath(basePath)
}
