//go:build !linux && !darwin && !windows

package protect

import (
	"errors"
	"net"
)

func peerProcessIDOf(*net.UnixConn) (int, error) {
	return 0, errors.ErrUnsupported
}

func parentProcessIDOf(int) (int, error) {
	return 0, errors.ErrUnsupported
}
