//go:build windows

package daemonhost

import (
	"os"
	"path/filepath"
	"strings"

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
	lower := strings.ToLower(currentPath)
	// Reject obvious user-writable roots.
	for _, marker := range []string{`\users\`, `\appdata\`, `\temp\`, `\tmp\`} {
		if strings.Contains(lower, marker) {
			return E.New("protected path must not be under a user-writable location: ", currentPath)
		}
	}
	for {
		info, err := os.Lstat(currentPath)
		if err != nil {
			return err
		}
		if !info.IsDir() {
			return E.New("protected path is not a directory: ", currentPath)
		}
		parentPath := filepath.Dir(currentPath)
		if parentPath == currentPath {
			return nil
		}
		currentPath = parentPath
	}
}
