package protect

import (
	"bytes"
	"net"
	"os"
	"path/filepath"
	"strconv"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/unix"
)

func peerProcessIDOf(conn *net.UnixConn) (int, error) {
	rawConn, err := conn.SyscallConn()
	if err != nil {
		return 0, err
	}
	var credentials *unix.Ucred
	var credentialsErr error
	err = rawConn.Control(func(fd uintptr) {
		credentials, credentialsErr = unix.GetsockoptUcred(int(fd), unix.SOL_SOCKET, unix.SO_PEERCRED)
	})
	if err != nil {
		return 0, err
	}
	if credentialsErr != nil {
		return 0, credentialsErr
	}
	return int(credentials.Pid), nil
}

func parentProcessIDOf(processID int) (int, error) {
	// Fields after the parenthesized command name: state, ppid, ...
	const parentProcessIDField = 1
	content, err := os.ReadFile(filepath.Join("/proc", strconv.Itoa(processID), "stat"))
	if err != nil {
		return 0, err
	}
	_, afterCommand, cut := bytes.CutLast(content, []byte{')'})
	if !cut {
		return 0, E.New("invalid process stat")
	}
	fields := bytes.Fields(afterCommand)
	if len(fields) <= parentProcessIDField {
		return 0, E.New("incomplete process stat")
	}
	return strconv.Atoi(string(fields[parentProcessIDField]))
}
