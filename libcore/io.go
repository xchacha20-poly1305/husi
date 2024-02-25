package libcore

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"io"
	"os"
	"path/filepath"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	//"github.com/ulikunitz/xz"
)

func Untar(archive, path string) (err error) {
	file, err := os.Open(archive)
	if err != nil {
		return
	}
	defer file.Close()

	_ = os.MkdirAll(path, os.ModePerm)

	gReader, err := gzip.NewReader(file)
	if err != nil {
		return err
	}
	tReader := tar.NewReader(gReader)

	for {
		header, err := tReader.Next()
		if err != nil {
			if err == io.EOF {
				break
			}
		}

		fileInfo := header.FileInfo()
		if fileInfo.IsDir() {
			_ = os.MkdirAll(filepath.Join(path, fileInfo.Name()), os.ModePerm)
			continue
		}

		err = copyToFile(filepath.Join(path, fileInfo.Name()), tReader)
		if err != nil {
			return err
		}
	}

	return nil
}

func UntargzWihoutDir(archive, path string) (err error) {
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
		if header.FileInfo().IsDir() {
			continue
		}

		err = copyToFile(filepath.Join(path, filepath.Base(header.FileInfo().Name())), tReader)
		if err != nil {
			return err
		}
	}

	return nil
}

func copyToFile(path string, reader io.Reader) error {
	newFile, err := os.Create(path)
	if err != nil {
		return err
	}
	defer newFile.Close()

	_, err = io.Copy(newFile, reader)
	return err
}

// UnzipWithoutDir
// Ignore the directory in the zip file.
func UnzipWithoutDir(archive, path string) error {
	r, err := zip.OpenReader(archive)
	if err != nil {
		return err
	}
	defer r.Close()

	_ = os.MkdirAll(path, os.ModePerm)

	for _, file := range r.File {
		if file.FileInfo().IsDir() {
			continue
		}

		// 获取文件基本名称而非全路径
		fileName := filepath.Base(file.Name)

		filePath := filepath.Join(path, fileName)

		_ = os.Remove(filePath)
		// Don't forget to close it.
		newFile, err := os.Create(filePath)
		if err != nil {
			return err
		}

		zipFile, err := file.Open()
		if err != nil {
			_ = newFile.Close()
			return err
		}

		_, err = io.Copy(newFile, zipFile)
		errs := E.Errors(err, common.Close(zipFile, newFile))
		if errs != nil {
			return errs
		}
	}

	return nil
}
