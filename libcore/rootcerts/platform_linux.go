//go:build linux && !android

package rootcerts

import (
	"os"
	"path/filepath"
	"strings"

	"github.com/sagernet/sing-box/log"
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

// Linux trust policy is machine-wide.
func appendPlatformRootCAs(roots *Bundle, withUserTrust bool) error {
	err := readLinuxDistrust(roots)
	if err != nil {
		log.Warn(E.Cause(err, "read Linux distrust"))
	}
	return appendOnDiskRootCAs(roots, "", "")
}

func readLinuxDistrust(roots *Bundle) error {
	for _, directory := range []string{
		"/etc/ca-certificates/trust-source/blocklist",
		"/usr/share/ca-certificates/trust-source/blocklist",
		"/etc/pki/ca-trust/source/blocklist",
		"/usr/share/pki/ca-trust-source/blocklist",
		"/etc/pki/trust/blacklist",
		"/usr/share/pki/trust/blacklist",
	} {
		if err := distrustDirectory(roots, directory); err != nil {
			return E.Cause(err, "read blocklist ", directory)
		}
	}
	return readDisabledCertificates(roots, "/etc/ca-certificates.conf", "/usr/share/ca-certificates")
}

func readDisabledCertificates(roots *Bundle, configPath, certificateDirectory string) error {
	content, err := os.ReadFile(configPath)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	for line := range strings.SplitSeq(string(content), "\n") {
		line = strings.TrimSpace(line)
		name, disabled := strings.CutPrefix(line, "!")
		if !disabled {
			continue
		}
		if !filepath.IsLocal(name) {
			return E.New("invalid disabled certificate path: ", name)
		}
		content, err := os.ReadFile(filepath.Join(certificateDirectory, name))
		if os.IsNotExist(err) {
			continue
		}
		if err != nil {
			return err
		}
		if err := roots.distrust(content); err != nil {
			return err
		}
	}
	return nil
}
