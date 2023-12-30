package libcore

import (
	"archive/zip"
	"io"
	"os"
	"path/filepath"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	//"github.com/ulikunitz/xz"
)

func Unzip(archive string, path string) error {
	r, err := zip.OpenReader(archive)
	if err != nil {
		return err
	}
	defer r.Close()

	os.MkdirAll(path, os.ModePerm)

	for _, file := range r.File {
		filePath := filepath.Join(path, file.Name)

		if file.FileInfo().IsDir() {
			os.MkdirAll(filePath, os.ModePerm)
			continue
		}

		// Don't forget to close it.
		newFile, err := os.Create(filePath)
		if err != nil {
			return err
		}

		zipFile, err := file.Open()
		if err != nil {
			newFile.Close()
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

// UnzipWithoutDir
// Ignore the directory in the zip file.
func UnzipWithoutDir(archive, path string) error {
	r, err := zip.OpenReader(archive)
	if err != nil {
		return err
	}
	defer r.Close()

	os.MkdirAll(path, os.ModePerm)

	for _, file := range r.File {
		if file.FileInfo().IsDir() {
			continue
		}

		// 获取文件基本名称而非全路径
		fileName := filepath.Base(file.Name)

		filePath := filepath.Join(path, fileName)

		os.Remove(filePath)
		// Don't forget to close it.
		newFile, err := os.Create(filePath)
		if err != nil {
			return err
		}

		zipFile, err := file.Open()
		if err != nil {
			newFile.Close()
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
