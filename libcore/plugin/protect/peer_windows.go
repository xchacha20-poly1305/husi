package protect

import (
	"net"
	"unsafe"

	"golang.org/x/sys/windows"
)

// sioAFUnixGetPeerPID is SIO_AF_UNIX_GETPEERPID from afunix.h.
const sioAFUnixGetPeerPID = windows.IOC_OUT | windows.IOC_VENDOR | 256

func peerProcessIDOf(conn *net.UnixConn) (int, error) {
	rawConn, err := conn.SyscallConn()
	if err != nil {
		return 0, err
	}
	var processID uint32
	var ioctlErr error
	err = rawConn.Control(func(fd uintptr) {
		var returned uint32
		ioctlErr = windows.WSAIoctl(windows.Handle(fd), sioAFUnixGetPeerPID, nil, 0,
			(*byte)(unsafe.Pointer(&processID)), uint32(unsafe.Sizeof(processID)), &returned, nil, 0)
	})
	if err != nil {
		return 0, err
	}
	if ioctlErr != nil {
		return 0, ioctlErr
	}
	return int(processID), nil
}

// parentProcessIDOf returns the process that created processID, or 0 once
// that process is gone. Windows does not clear the parent identifier when the
// parent exits, and may reuse it for a later process, so a recorded parent
// counts only if it was created before processID.
func parentProcessIDOf(processID int) (int, error) {
	process, err := openForQuery(processID)
	if err != nil {
		return 0, err
	}
	defer windows.CloseHandle(process)
	var information windows.PROCESS_BASIC_INFORMATION
	err = windows.NtQueryInformationProcess(process, windows.ProcessBasicInformation,
		unsafe.Pointer(&information), uint32(unsafe.Sizeof(information)), nil)
	if err != nil {
		return 0, err
	}
	parentProcessID := int(information.InheritedFromUniqueProcessId)
	childCreation, err := creationTime(process)
	if err != nil {
		return 0, err
	}
	parent, err := openForQuery(parentProcessID)
	if err != nil {
		// The parent has exited; its identifier names nothing or a stranger.
		return 0, nil
	}
	defer windows.CloseHandle(parent)
	parentCreation, err := creationTime(parent)
	if err != nil {
		return 0, err
	}
	if parentCreation >= childCreation {
		return 0, nil
	}
	return parentProcessID, nil
}

func openForQuery(processID int) (windows.Handle, error) {
	return windows.OpenProcess(windows.PROCESS_QUERY_LIMITED_INFORMATION, false, uint32(processID))
}

func creationTime(process windows.Handle) (int64, error) {
	var creation, exit, kernel, user windows.Filetime
	err := windows.GetProcessTimes(process, &creation, &exit, &kernel, &user)
	if err != nil {
		return 0, err
	}
	return creation.Nanoseconds(), nil
}
