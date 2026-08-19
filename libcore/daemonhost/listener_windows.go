//go:build windows

package daemonhost

import (
	"net"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"

	"github.com/tailscale/go-winio"
	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
)

const DefaultDaemonPipePath = coresvc.DaemonPipePath

const (
	pipeSecurityDescriptor = `D:P(A;;GA;;;BA)(A;;GA;;;SY)(A;;GRGW;;;WD)`
	pipeBufferSize         = 65536
)

func ListenDaemonEndpoint(socketPath string) (net.Listener, error) {
	path := DefaultDaemonPipePath
	if socketPath != "" && !strings.EqualFold(socketPath, DefaultDaemonPipePath) {
		return nil, E.New("custom Windows daemon pipe paths are not supported")
	}
	return winio.ListenPipe(path, &winio.PipeConfig{
		SecurityDescriptor: pipeSecurityDescriptor,
		InputBufferSize:    pipeBufferSize,
		OutputBufferSize:   pipeBufferSize,
	})
}
