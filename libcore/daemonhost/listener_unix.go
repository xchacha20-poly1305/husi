//go:build unix

package daemonhost

import (
	"net"
	"os"
	"path/filepath"

	E "github.com/sagernet/sing/common/exceptions"
)

const DefaultDaemonSocketPath = "/var/run/husi/api.sock"

func ListenDaemonEndpoint(socketPath string) (net.Listener, error) {
	if socketPath == "" {
		socketPath = DefaultDaemonSocketPath
	}
	if err := os.MkdirAll(filepath.Dir(socketPath), 0o755); err != nil {
		return nil, E.Cause(err, "create socket directory")
	}
	if err := os.Remove(socketPath); err != nil && !os.IsNotExist(err) {
		return nil, E.Cause(err, "remove stale socket")
	}
	listener, err := net.Listen("unix", socketPath)
	if err != nil {
		return nil, E.Cause(err, "listen unix socket")
	}
	if err := os.Chmod(socketPath, 0o666); err != nil {
		_ = listener.Close()
		return nil, E.Cause(err, "chmod socket")
	}
	return listener, nil
}
