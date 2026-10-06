package coresvc

import "path/filepath"

const Socket = "api.sock"

// DaemonPipePath is the fixed Windows named-pipe endpoint for the privileged
// daemon (must match daemonhost.DefaultDaemonPipePath).
const DaemonPipePath = `\\.\pipe\ProtectedPrefix\Administrators\husi`

func SocketPath(basePath string) string {
	return filepath.Join(basePath, Socket)
}
