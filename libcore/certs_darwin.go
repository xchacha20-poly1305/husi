//go:build darwin

package libcore

import (
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/shell"
)

// macOS has no default SSL_CERT_{FILE,DIR} paths.
var certFiles, certDirectories []string

func appendPlatformRootCAs(roots *rootCABundle, withUserTrust bool) error {
	keychains := []string{"/System/Library/Keychains/SystemRootCertificates.keychain"}
	if withUserTrust {
		keychains = append(keychains, "/Library/Keychains/System.keychain")
	}

	for _, keychain := range keychains {
		content, err := shell.Exec("security", "find-certificate", "-a", "-p", keychain).Output()
		if err != nil {
			return E.Cause(err, "export certificates from ", keychain)
		}
		if err := roots.Append(content); err != nil {
			return E.Cause(err, "load certificates from ", keychain)
		}
	}
	if roots.pem.Len() == 0 {
		return E.New("no certificate found")
	}
	return nil
}
