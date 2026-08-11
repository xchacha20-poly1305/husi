//go:build !unix && !windows

package daemonhost

import (
	"syscall"

	E "github.com/sagernet/sing/common/exceptions"
)

// ProcessCredentialForOwner is unavailable on this platform.
func ProcessCredentialForOwner(identity PeerIdentity) (*syscall.SysProcAttr, error) {
	return nil, E.New("process credentials are not supported on this platform")
}
