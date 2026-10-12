package protect

import (
	"net"

	"golang.org/x/sys/unix"
)

func peerProcessIDOf(conn *net.UnixConn) (int, error) {
	rawConn, err := conn.SyscallConn()
	if err != nil {
		return 0, err
	}
	var processID int
	var processIDErr error
	err = rawConn.Control(func(fd uintptr) {
		processID, processIDErr = unix.GetsockoptInt(int(fd), unix.SOL_LOCAL, unix.LOCAL_PEERPID)
	})
	if err != nil {
		return 0, err
	}
	return processID, processIDErr
}

func parentProcessIDOf(processID int) (int, error) {
	info, err := unix.SysctlKinfoProc("kern.proc.pid", processID)
	if err != nil {
		return 0, err
	}
	return int(info.Eproc.Ppid), nil
}
