package oscall

import (
	"os"

	"golang.org/x/sys/windows"
)

const (
	lockOffsetLow   = 0
	lockOffsetHigh  = 0
	lockLengthLow   = 1
	lockLengthHigh  = 0
	noReservedFlags = 0
)

func Flock(file *os.File) error {
	var overlapped windows.Overlapped
	overlapped.Offset = lockOffsetLow
	overlapped.OffsetHigh = lockOffsetHigh
	return windows.LockFileEx(
		windows.Handle(file.Fd()),
		windows.LOCKFILE_EXCLUSIVE_LOCK,
		noReservedFlags,
		lockLengthLow,
		lockLengthHigh,
		&overlapped,
	)
}

func FUnlock(file *os.File) error {
	var overlapped windows.Overlapped
	overlapped.Offset = lockOffsetLow
	overlapped.OffsetHigh = lockOffsetHigh
	return windows.UnlockFileEx(
		windows.Handle(file.Fd()),
		noReservedFlags,
		lockLengthLow,
		lockLengthHigh,
		&overlapped,
	)
}
