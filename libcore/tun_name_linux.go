package libcore

import (
	"unsafe"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/unix"
)

const ifReqSize = unix.IFNAMSIZ + 64

func tunnelName(fd int32) (string, error) {
	var ifr [ifReqSize]byte
	_, _, errno := unix.Syscall(
		unix.SYS_IOCTL,
		uintptr(fd),
		uintptr(unix.TUNGETIFF),
		uintptr(unsafe.Pointer(&ifr[0])),
	)
	if errno != 0 {
		return "", E.Cause(errno, "get name of TUN device")
	}
	return unix.ByteSliceToString(ifr[:]), nil
}
