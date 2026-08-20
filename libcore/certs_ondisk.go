package libcore

import (
	"os"
	"path/filepath"
	"strings"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

const (
	certFileEnv = "SSL_CERT_FILE"
	certDirEnv  = "SSL_CERT_DIR"
)

func appendSystemRootCAs(roots *rootCABundle, withUserTrust bool) error {
	certFilePath, certDirPath := os.Getenv(certFileEnv), os.Getenv(certDirEnv)
	if certFilePath != "" || certDirPath != "" {
		return appendOnDiskRootCAs(roots, certFilePath, certDirPath)
	}
	return appendPlatformRootCAs(roots, withUserTrust)
}

func appendOnDiskRootCAs(roots *rootCABundle, certFilePath, certDirPath string) error {
	files := certFiles
	if certFilePath != "" {
		files = []string{certFilePath}
	}
	for _, file := range files {
		if err := appendRootCAFile(roots, file); err == nil {
			break
		}
	}

	directories := certDirectories
	if certDirPath != "" {
		directories = filepath.SplitList(certDirPath)
	}
	for _, directory := range directories {
		_ = filepath.WalkDir(directory, func(path string, entry os.DirEntry, err error) error {
			if err != nil || entry.IsDir() || !isCertificateFileName(path) {
				return nil
			}
			_ = appendRootCAFile(roots, path)
			return nil
		})
	}

	if roots.pem.Len() == 0 {
		return E.New("no certificate found")
	}
	return nil
}

func appendRootCAFile(roots *rootCABundle, path string) error {
	content, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	return roots.Append(content)
}

func isCertificateFileName(path string) bool {
	certFileExtensions := []string{".crt", ".pem"}
	return common.Any(certFileExtensions, func(it string) bool {
		return strings.HasSuffix(path, it)
	})
}
