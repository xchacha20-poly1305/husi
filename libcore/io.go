package libcore

import (
	"archive/tar"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/klauspost/compress/gzip"
	"github.com/klauspost/compress/zip"
	"github.com/klauspost/compress/zstd"
)

func TryUnpack(archive, path string) error {
	var errors []error
	type unpackFunc func(archive, path string) error
	for _, unpack := range []unpackFunc{UnTarZstdWithoutDir, UntargzWithoutDir, UnzipWithoutDir} {
		err := unpack(archive, path)
		if err == nil {
			return nil
		}
		errors = append(errors, err)
	}
	return E.Errors(errors...)
}

// UntargzWithoutDir untargz the archive to path,
// but ignore the directory in tar.
func UntargzWithoutDir(archive, path string) (err error) {
	file, err := os.Open(archive)
	if err != nil {
		return
	}
	defer file.Close()

	_ = os.MkdirAll(path, os.ModePerm)
	root, err := os.OpenRoot(path)
	if err != nil {
		return err
	}
	defer root.Close()

	gReader, err := gzip.NewReader(file)
	if err != nil {
		return
	}
	defer gReader.Close()
	tReader := tar.NewReader(gReader)

	for {
		header, err := tReader.Next()
		if err != nil {
			if err == io.EOF {
				break
			}
			return err
		}
		if header.Typeflag != tar.TypeReg {
			continue
		}

		fileInfo := header.FileInfo()
		if fileInfo.IsDir() {
			continue
		}

		err = copyToFile(root, fileInfo.Name(), tReader)
		if err != nil {
			return err
		}
	}

	return nil
}

// UnzipWithoutDir unzip the archive to path,
// but ignore the directory in tar.
func UnzipWithoutDir(archive, path string) error {
	reader, err := zip.OpenReader(archive)
	if err != nil {
		return err
	}
	defer reader.Close()

	_ = os.MkdirAll(path, os.ModePerm)
	root, err := os.OpenRoot(path)
	if err != nil {
		return err
	}
	defer root.Close()

	for _, file := range reader.File {
		fileInfo := file.FileInfo()
		if fileInfo.IsDir() {
			continue
		}

		zipFile, err := file.Open()
		if err != nil {
			return err
		}

		err = copyToFile(root, fileInfo.Name(), zipFile)
		_ = zipFile.Close()
		if err != nil {
			return err
		}
	}

	return nil
}

func UnTarZstdWithoutDir(archive, path string) error {
	file, err := os.Open(archive)
	if err != nil {
		return err
	}
	defer file.Close()
	zReader, err := zstd.NewReader(file, zstd.WithDecoderLowmem(common.LowMemory))
	if err != nil {
		return err
	}
	defer zReader.Close()

	_ = os.MkdirAll(path, os.ModePerm)
	root, err := os.OpenRoot(path)
	if err != nil {
		return err
	}
	defer root.Close()

	tReader := tar.NewReader(zReader)
	for {
		header, err := tReader.Next()
		if err != nil {
			if err == io.EOF {
				break
			}
			return err
		}
		if header.Typeflag != tar.TypeReg {
			continue
		}

		fileInfo := header.FileInfo()
		if fileInfo.IsDir() {
			continue
		}

		err = copyToFile(root, fileInfo.Name(), tReader)
		if err != nil {
			return err
		}
	}

	return nil
}

// copyToFile will try to open path in root, then use io.Copy to copy reader into it.
func copyToFile(root *os.Root, name string, reader io.Reader) error {
	newFile, err := root.Create(name)
	if err != nil {
		return err
	}
	defer newFile.Close()

	return common.Error(io.Copy(newFile, reader))
}

// removeIfHasPrefix removes all files which starts with prefix in dir. But it will ignore any error.
func removeIfHasPrefix(dir, prefix string) error {
	return filepath.WalkDir(dir, func(path string, entry fs.DirEntry, err error) (_ error) {
		if err != nil || entry.IsDir() {
			return
		}
		if strings.HasPrefix(entry.Name(), prefix) {
			_ = os.Remove(path)
		}
		return
	})
}

const DevNull = os.DevNull

// CopyCallback callbacks when copying.
type CopyCallback interface {
	SetLength(length int64)
	Update(n int64)
}

// stallReader wraps an io.ReadCloser and closes it, reporting an error, if no
// bytes are read within timeout. Each successful read resets the countdown.
type stallReader struct {
	reader  io.ReadCloser
	timeout time.Duration
	timer   *time.Timer
	stalled atomic.Bool
}

// newStallReader creates a stallReader that closes reader once timeout passes
// without any progress.
func newStallReader(reader io.ReadCloser, timeout time.Duration) *stallReader {
	guard := &stallReader{reader: reader, timeout: timeout}
	guard.timer = time.AfterFunc(timeout, func() {
		guard.stalled.Store(true)
		_ = reader.Close()
	})
	return guard
}

func (s *stallReader) Read(p []byte) (int, error) {
	n, err := s.reader.Read(p)
	if n > 0 {
		s.timer.Reset(s.timeout)
	}
	if err != nil && s.stalled.Load() {
		return n, E.New("transfer stalled for ", s.timeout)
	}
	return n, err
}

func (s *stallReader) Close() error {
	s.timer.Stop()
	return s.reader.Close()
}

// callbackReader use callback when reading.
// It is worth noting that it will never check nil for callback.
type callbackReader struct {
	reader   io.Reader
	callback func(n int64)
}

func (c callbackReader) Read(p []byte) (n int, err error) {
	n, err = c.reader.Read(p)
	if n > 0 {
		c.callback(int64(n))
	}
	return
}

func (c callbackReader) Close() error {
	return common.Close(c.reader)
}

// zeroReader is a reader that always fill the input p with zero like /dev/zero.
type zeroReader struct{}

func (z zeroReader) Read(p []byte) (int, error) {
	clear(p)
	return len(p), nil
}
