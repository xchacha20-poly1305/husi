//go:build unix

package libcore

import (
	"golang.org/x/sys/unix"
)

func dup(oldFd, newFd, flags int) error {
	return unix.Dup3(oldFd, newFd, flags)
}

func flock(fd int) (unlock func() error) {
	_ = unix.Flock(fd, unix.LOCK_EX)
	return func() error {
		return unix.Flock(fd, unix.LOCK_UN)
	}
}
