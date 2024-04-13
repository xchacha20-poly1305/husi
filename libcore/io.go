package libcore

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

func Untargz(archive, path string) (err error) {
	file, err := os.Open(archive)
	if err != nil {
		return
	}
	defer file.Close()

	_ = os.MkdirAll(path, os.ModePerm)

	// gReader is a gzip.Reader
	gReader, err := gzip.NewReader(file)
	if err != nil {
		return err
	}
	// tReader is a tar.Reader
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
			_ = os.MkdirAll(filepath.Join(path, header.Name), os.ModePerm)
			continue
		}

		// Zip flip
		if strings.Contains(header.Name, "..") {
			log.Warn("Found zip flip when untargz: ", header.Name)
			continue
		}

		err = copyToFile(filepath.Join(path, header.Name), tReader)
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

	_, err = io.Copy(newFile, reader)
	return err
}

// UnzipWithoutDir unzip zipfile buy ignore the directory in the zip file.
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

		_, err = io.Copy(newFile, zipFile)
		errs := E.Errors(err, common.Close(zipFile, newFile))
		if errs != nil {
			return errs
		}
	}

	return nil
}
