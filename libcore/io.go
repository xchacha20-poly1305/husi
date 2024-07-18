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
)

// UntargzWithoutDir untargz the archive to path,
// but ignore the directory in tar.
func UntargzWithoutDir(archive, path string) (err error) {
	file, err := os.Open(archive)
	if err != nil {
		return
	}
	defer file.Close()

	_ = os.MkdirAll(path, os.ModePerm)

	gReader, err := gzip.NewReader(file)
	if err != nil {
		return
	}
	tReader := tar.NewReader(gReader)

	for {
		header, err := tReader.Next()
		if err != nil {
			if err == io.EOF {
				break
			}
			return err
		}

		fileInfo := header.FileInfo()

		if fileInfo.IsDir() {
			continue
		}

		err = copyToFile(filepath.Join(path, fileInfo.Name()), tReader)
		if err != nil {
			return err
		}
	}

	return nil
}

// copyToFile will try to open path as an *os.File, then use io.Copy to copy reader into it.
func copyToFile(path string, reader io.Reader) error {
	newFile, err := os.Create(path)
	if err != nil {
		return err
	}
	defer newFile.Close()

	return common.Error(io.Copy(newFile, reader))
}

// UnzipWithoutDir unzip the archive to path,
// but ignore the directory in tar.
func UnzipWithoutDir(archive, path string) error {
	r, err := zip.OpenReader(archive)
	if err != nil {
		return err
	}
	defer r.Close()

	_ = os.MkdirAll(path, os.ModePerm)

	for _, file := range r.File {
		fileInfo := file.FileInfo()

		if fileInfo.IsDir() {
			continue
		}

		filePath := filepath.Join(path, fileInfo.Name())

		_ = os.Remove(filePath)
		newFile, err := os.Create(filePath)
		if err != nil {
			return err
		}

		zipFile, err := file.Open()
		if err != nil {
			_ = newFile.Close()
			return err
		}

		err = E.Errors(
			common.Error(io.Copy(newFile, zipFile)),
			common.Close(zipFile, newFile),
		)
		if err != nil {
			return err
		}
	}

	return nil
}

// removeIfHasPrefix removes all files which starts with prefix in dir. But it will ignore any error.
func removeIfHasPrefix(dir, prefix string) error {
	return filepath.Walk(dir, func(path string, info fs.FileInfo, err error) error {
		if err != nil || info.IsDir() {
			return nil
		}
		if strings.HasPrefix(info.Name(), prefix) {
			_ = os.Remove(path)
		}
		return nil
	})
}

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
	c.callback(int64(n))
	return
}
