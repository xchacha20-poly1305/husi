package daemonhost

import (
	"crypto/subtle"
	"crypto/x509"
	"time"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

// The Authenticode signer certificate rules below are ported from sing-box's
// experimental/boxdd. They live in a platform-neutral file so they stay
// testable on non-Windows hosts; the wintrust glue that feeds them is in
// authenticode_windows.go.

func validateCodeSigningCertificate(encodedCertificate []byte) (*x509.Certificate, error) {
	certificate, err := x509.ParseCertificate(encodedCertificate)
	if err != nil {
		return nil, E.Cause(err, "parse Authenticode signer certificate")
	}
	if common.Any(certificate.ExtKeyUsage, func(it x509.ExtKeyUsage) bool {
		return it == x509.ExtKeyUsageCodeSigning || it == x509.ExtKeyUsageAny
	}) {
		return certificate, nil
	}
	return nil, E.New("Authenticode signer certificate is not valid for code signing")
}

func validateUntrustedSelfSignedCertificate(certificate *x509.Certificate, currentTime time.Time) error {
	if subtle.ConstantTimeCompare(certificate.RawSubject, certificate.RawIssuer) != 1 {
		return E.New("untrusted Authenticode signer certificate is not self-signed")
	}
	err := certificate.CheckSignature(certificate.SignatureAlgorithm, certificate.RawTBSCertificate, certificate.Signature)
	if err != nil {
		return E.Cause(err, "verify untrusted Authenticode signer self-signature")
	}
	if currentTime.Before(certificate.NotBefore) || currentTime.After(certificate.NotAfter) {
		return E.New("untrusted Authenticode signer certificate is not currently valid")
	}
	return nil
}
