package daemonhost

import (
	"io"
	"os"
	"path/filepath"
	"runtime"

	E "github.com/sagernet/sing/common/exceptions"
)

func CoreLibraryFileName() string {
	switch runtime.GOOS {
	case "windows":
		return "husicore.dll"
	case "darwin":
		return "libhusicore.dylib"
	case "linux":
		return "libhusicore.so"
	default:
		panic("unreachable")
	}
}

func SiblingCoreLibrary(shimPath string) string {
	return filepath.Join(filepath.Dir(shimPath), CoreLibraryFileName())
}

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

func resolvePairSources(executablePath string) (shimPath, libraryPath string, err error) {
	shimPath, err = resolveExecutablePath(executablePath)
	if err != nil {
		return "", "", err
	}
	libraryPath = SiblingCoreLibrary(shimPath)
	if _, err := os.Stat(libraryPath); err != nil {
		return "", "", E.Cause(err, "core library not found next to shim (", libraryPath, ")")
	}
	return shimPath, libraryPath, nil
}

func installPair(srcShim, srcLib, destShim string, stopFn func() error) error {
	if srcShim == "" || srcLib == "" || destShim == "" {
		return E.New("install pair: missing source or destination")
	}
	destDir := filepath.Dir(destShim)
	if err := os.MkdirAll(destDir, 0o755); err != nil {
		return E.Cause(err, "create install directory")
	}
	destLib := SiblingCoreLibrary(destShim)

	if stopFn != nil {
		_ = stopFn()
	}

	// Library first: a torn pair with a new shim and old/missing library fails
	// to load loud; the reverse (new library, old shim still running) is worse.
	if err := copyFileAtomic(srcLib, destLib, 0o755); err != nil {
		return E.Cause(err, "install core library")
	}
	if err := copyFileAtomic(srcShim, destShim, 0o755); err != nil {
		return E.Cause(err, "install core shim")
	}
	return nil
}

func removePair(shimPath string) error {
	if shimPath == "" {
		return nil
	}
	libPath := SiblingCoreLibrary(shimPath)
	var first error
	if err := os.Remove(shimPath); err != nil && !os.IsNotExist(err) {
		first = E.Cause(err, "remove core shim")
	}
	if err := os.Remove(libPath); err != nil && !os.IsNotExist(err) {
		if first == nil {
			first = E.Cause(err, "remove core library")
		}
	}
	return first
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

func ensurePairPresent(shimPath string) error {
	if _, err := os.Stat(shimPath); err != nil {
		return E.Cause(err, "core shim missing at ", shimPath)
	}
	libPath := SiblingCoreLibrary(shimPath)
	if _, err := os.Stat(libPath); err != nil {
		return E.Cause(err, "core library missing at ", libPath)
	}
	return nil
}
