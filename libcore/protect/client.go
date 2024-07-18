package protect

import (
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/unix"
)

func ClientProtect(protectFd int, protectPath string) error {
	socketFd, err := unix.Socket(unix.AF_UNIX, unix.SOCK_STREAM, 0)
	if err != nil {
		return err
	}
	defer unix.Close(socketFd)

	timeval := unix.Timeval{Sec: 3}
	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &timeval)
	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_SNDTIMEO, &timeval)

	err = unix.Connect(socketFd, &unix.SockaddrUnix{Name: protectPath})
	if err != nil {
		return err
	}

	err = unix.Sendmsg(socketFd, nil, unix.UnixRights(protectFd), nil, 0)
	if err != nil {
		return err
	}

	dummy := []byte{1}
	n, err := unix.Read(socketFd, dummy)
	if err != nil {
		return err
	}
	if n != 1 {
		return E.New("protect failed")
	}
	if dummy[0] != ProtectSuccess {
		return E.New("protect failed for failed flag")
	}

	return nil
}
