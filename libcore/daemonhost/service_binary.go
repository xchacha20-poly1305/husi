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

// installCore copies the running husi-core to destination, with the shared
// libraries it loads from its own directory beside it. stop is called first so
// the old copy is not in use while it is replaced. The libraries land before the
// binary, so the binary never sits there without them.
func installCore(source, destination string, stop func() error) error {
	if source == "" || destination == "" {
		return E.New("install core binary: missing source or destination")
	}
	if stop != nil {
		_ = stop()
	}
	sourceDir := filepath.Dir(source)
	destinationDir := filepath.Dir(destination)
	for _, library := range coreLibraries {
		err := copyFileAtomic(filepath.Join(sourceDir, library), filepath.Join(destinationDir, library), 0o644)
		if err != nil {
			return E.Cause(err, "install ", library)
		}
	}
	err := copyFileAtomic(source, destination, 0o755)
	if err != nil {
		return E.Cause(err, "install core binary")
	}
	return nil
}

func removeCore(path string) error {
	err := removeFile(path)
	if err != nil {
		return E.Cause(err, "remove core binary")
	}
	directory := filepath.Dir(path)
	for _, library := range coreLibraries {
		err = removeFile(filepath.Join(directory, library))
		if err != nil {
			return E.Cause(err, "remove ", library)
		}
	}
	return nil
}

func removeFile(path string) error {
	err := os.Remove(path)
	if err != nil && !os.IsNotExist(err) {
		return err
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
