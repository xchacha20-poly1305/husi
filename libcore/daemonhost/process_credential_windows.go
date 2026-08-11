//go:build windows

package daemonhost

import (
	"syscall"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows"
)

func ProcessCredentialForOwner(identity PeerIdentity) (*syscall.SysProcAttr, error) {
	if identity.SessionID == 0 {
		return nil, E.New("missing Windows session id for process credentials")
	}
	var token windows.Token
	if err := windows.WTSQueryUserToken(identity.SessionID, &token); err != nil {
		return nil, E.Cause(err, "query user token for session ", identity.SessionID)
	}
	// SysProcAttr.Token takes ownership of the handle for process creation;
	// callers must not Close the token while children may still be spawning.
	// We keep the token open for the lifetime of the SysProcAttr use in Start.
	return &syscall.SysProcAttr{
		Token: syscall.Token(token),
		// Hide console windows for plugin children.
		HideWindow: true,
	}, nil
}

func closeProcessCredential(attr *syscall.SysProcAttr) {
	if attr == nil || attr.Token == 0 {
		return
	}
	_ = windows.CloseHandle(windows.Handle(attr.Token))
	attr.Token = 0
}
