//go:build linux && !android

package libcore

import (
	"os"
	"path/filepath"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"
)

// Possible certificate files; stop after finding one.
var certFiles = []string{
	"/etc/ssl/certs/ca-certificates.crt",                // Debian/Ubuntu/Gentoo etc.
	"/etc/pki/tls/certs/ca-bundle.crt",                  // Fedora/RHEL 6
	"/etc/ssl/ca-bundle.pem",                            // OpenSUSE
	"/etc/pki/tls/cacert.pem",                           // OpenELEC
	"/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem", // CentOS/RHEL 7
	"/etc/ssl/cert.pem",                                 // Alpine Linux
}

// Possible directories with certificate files; all will be read.
var certDirectories = []string{
	"/etc/ssl/certs",     // SLES10/SLES11, https://golang.org/issue/12139
	"/etc/pki/tls/certs", // Fedora/RHEL
}

func appendSystemRootCAs(roots *rootCABundle, withUserTrust bool) error {
	files := certFiles
	if sslCertFile := os.Getenv("SSL_CERT_FILE"); sslCertFile != "" {
		files = []string{sslCertFile}
	}
	for _, file := range files {
		if err := appendRootCAFile(roots, file); err == nil {
			break
		}
	}

	dirs := certDirectories
	if sslCertDir := os.Getenv("SSL_CERT_DIR"); sslCertDir != "" {
		dirs = filepath.SplitList(sslCertDir)
	}
	for _, dir := range dirs {
		_ = filepath.WalkDir(dir, func(path string, entry os.DirEntry, err error) error {
			if err != nil || entry.IsDir() {
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
	if !strings.HasSuffix(path, ".crt") && !strings.HasSuffix(path, ".pem") {
		return nil
	}
	content, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	return roots.Append(content)
}
