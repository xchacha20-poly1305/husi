package daemonhost

import (
	"io"
	"os"
	"path/filepath"

	E "github.com/sagernet/sing/common/exceptions"
)

func resolveExecutablePath(executablePath string) (string, error) {
	resolvedPath, err := filepath.EvalSymlinks(executablePath)
	if err != nil {
		return "", E.Cause(err, "resolve executable path")
	}
	absolutePath, err := filepath.Abs(resolvedPath)
	if err != nil {
		return "", E.Cause(err, "resolve executable path")
	}
	return absolutePath, nil
}

// installBinary copies the running husi-core to destination, calling stop first
// so the old binary is not in use while it is replaced.
func installBinary(source, destination string, stop func() error) error {
	if source == "" || destination == "" {
		return E.New("install core binary: missing source or destination")
	}
	if stop != nil {
		_ = stop()
	}
	err := copyFileAtomic(source, destination, 0o755)
	if err != nil {
		return E.Cause(err, "install core binary")
	}
	return nil
}

func removeBinary(path string) error {
	err := os.Remove(path)
	if err != nil && !os.IsNotExist(err) {
		return E.Cause(err, "remove core binary")
	}
	return nil
}

func copyFileAtomic(src, dest string, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return E.Cause(err, "create destination directory")
	}
	in, err := os.Open(src)
	if err != nil {
		return E.Cause(err, "open source")
	}
	defer in.Close()

	tmp := dest + ".tmp"
	out, err := os.OpenFile(tmp, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return E.Cause(err, "create destination temp")
	}
	if _, err := io.Copy(out, in); err != nil {
		_ = out.Close()
		_ = os.Remove(tmp)
		return E.Cause(err, "copy file")
	}
	if err := out.Close(); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	if err := os.Rename(tmp, dest); err != nil {
		// Windows may refuse rename over a locked file; try remove+rename.
		_ = os.Remove(dest)
		if err2 := os.Rename(tmp, dest); err2 != nil {
			_ = os.Remove(tmp)
			return E.Cause(err2, "install file")
		}
	}
	return nil
}
