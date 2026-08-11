//go:build unix

package daemonhost

import "syscall"

func ProcessCredentialForOwner(identity PeerIdentity) (*syscall.SysProcAttr, error) {
	return &syscall.SysProcAttr{
		Credential: &syscall.Credential{
			Uid: identity.UID,
			Gid: identity.GID,
		},
	}, nil
}
