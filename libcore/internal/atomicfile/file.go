// Package atomicfile publishes files through a same-directory rename.
package atomicfile

import (
	"os"
	"path/filepath"
)

// Write writes data to path atomically by writing a temporary file in the same
// directory and renaming it over path.
func Write(path string, data []byte, perm os.FileMode) error {
	dir := filepath.Dir(path)
	tempFile, err := os.CreateTemp(dir, ".tmp-*")
	if err != nil {
		return err
	}
	defer tempFile.Close()
	tempPath := tempFile.Name()
	cleanup := true
	defer func() {
		if cleanup {
			_ = os.Remove(tempPath)
		}
	}()
	_, err = tempFile.Write(data)
	if err != nil {
		return err
	}
	err = tempFile.Chmod(perm)
	if err != nil {
		return err
	}
	err = os.Rename(tempPath, path)
	if err != nil {
		return err
	}
	cleanup = false
	return nil
}
