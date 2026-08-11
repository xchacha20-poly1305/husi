//go:build darwin

package daemonhost

import (
	"os"
	"path/filepath"
	"syscall"

	E "github.com/sagernet/sing/common/exceptions"
)

func validateProtectedDirectory(directory string) error {
	if directory == "" {
		return E.New("empty protected directory")
	}
	currentPath, err := filepath.Abs(directory)
	if err != nil {
		return E.Cause(err, "resolve protected directory")
	}
	for {
		info, err := os.Lstat(currentPath)
		if err != nil {
			return err
		}
		if info.Mode()&os.ModeSymlink != 0 {
			return E.New("protected path is a symlink: ", currentPath)
		}
		if !info.IsDir() {
			return E.New("protected path is not a directory: ", currentPath)
		}
		fileStatus, ok := info.Sys().(*syscall.Stat_t)
		if !ok || fileStatus.Uid != 0 {
			return E.New("protected path is not owned by root: ", currentPath)
		}
		// Group/other write is forbidden; wheel-owned admin paths are ok.
		if info.Mode().Perm()&0o022 != 0 {
			return E.New("protected path is writable by non-root users: ", currentPath)
		}
		parentPath := filepath.Dir(currentPath)
		if parentPath == currentPath {
			return nil
		}
		currentPath = parentPath
	}
}
