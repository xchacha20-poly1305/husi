//go:build unix

package daemonhost

import (
	"os"
	"syscall"
)

func NeedsProcessCredential() bool {
	return os.Geteuid() == 0
}

func ProcessCredentialForOwner(identity PeerIdentity) (*syscall.SysProcAttr, error) {
	return &syscall.SysProcAttr{
		Credential: &syscall.Credential{
			Uid: identity.UID,
			Gid: identity.GID,
		},
	}, nil
}
