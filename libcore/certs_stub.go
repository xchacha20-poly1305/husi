//go:build !android

package libcore

import (
	"crypto/x509"
)

func loadSystemCertWithUserTrust(sysRoot *x509.CertPool, withUserTrust bool) (*x509.CertPool, error) {
	return sysRoot, nil
}
