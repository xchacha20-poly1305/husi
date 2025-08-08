package libcore

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

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

// callbackWriter use callback when writing.
// It is worth noting that it will never check nil for callback.
type callbackWriter struct {
	writer   io.Writer
	callback func(n int64)
}

func (c *callbackWriter) Write(p []byte) (n int, err error) {
	n, err = c.writer.Write(p)
	if n > 0 {
		c.callback(int64(n))
	}
	return
}
