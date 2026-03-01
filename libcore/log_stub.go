//go:build !unix

package libcore

func dup(oldFd, newFd, flags int) error {
	return nil
}

func flock(fd int) (unlock func() error) {
	return nil
}
