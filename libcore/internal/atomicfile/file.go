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
	tempPath := tempFile.Name()
	cleanup := true
	defer func() {
		if cleanup {
			_ = os.Remove(tempPath)
		}
	}()
	closed := false
	defer func() {
		if !closed {
			_ = tempFile.Close()
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
	// Close before renaming: Windows rejects renaming an open file handle.
	closed = true
	err = tempFile.Close()
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
