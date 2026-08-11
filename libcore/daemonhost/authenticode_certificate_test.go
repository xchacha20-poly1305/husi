package daemonhost

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"math/big"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

type testCertificate struct {
	certificate *x509.Certificate
	encoded     []byte
	privateKey  *ecdsa.PrivateKey
}

func newTestCertificate(t *testing.T, template *x509.Certificate, parent *testCertificate) *testCertificate {
	t.Helper()
	privateKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	issuer := template
	signingKey := privateKey
	if parent != nil {
		issuer = parent.certificate
		signingKey = parent.privateKey
	}
	encoded, err := x509.CreateCertificate(rand.Reader, template, issuer, &privateKey.PublicKey, signingKey)
	require.NoError(t, err)
	certificate, err := x509.ParseCertificate(encoded)
	require.NoError(t, err)
	return &testCertificate{
		certificate: certificate,
		encoded:     encoded,
		privateKey:  privateKey,
	}
}

func codeSigningTemplate(commonName string, notBefore, notAfter time.Time) *x509.Certificate {
	return &x509.Certificate{
		SerialNumber:          big.NewInt(1),
		Subject:               pkix.Name{CommonName: commonName},
		NotBefore:             notBefore,
		NotAfter:              notAfter,
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageCodeSigning},
		BasicConstraintsValid: true,
		IsCA:                  true,
	}
}

func TestValidateCodeSigningCertificate(t *testing.T) {
	t.Parallel()
	now := time.Now()

	t.Run("code signing usage", func(t *testing.T) {
		signer := newTestCertificate(t, codeSigningTemplate("husi", now.Add(-time.Hour), now.Add(time.Hour)), nil)
		certificate, err := validateCodeSigningCertificate(signer.encoded)
		require.NoError(t, err)
		require.Equal(t, "husi", certificate.Subject.CommonName)
	})

	t.Run("any usage", func(t *testing.T) {
		template := codeSigningTemplate("husi", now.Add(-time.Hour), now.Add(time.Hour))
		template.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageAny}
		signer := newTestCertificate(t, template, nil)
		_, err := validateCodeSigningCertificate(signer.encoded)
		require.NoError(t, err)
	})

	t.Run("wrong usage", func(t *testing.T) {
		template := codeSigningTemplate("husi", now.Add(-time.Hour), now.Add(time.Hour))
		template.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}
		signer := newTestCertificate(t, template, nil)
		_, err := validateCodeSigningCertificate(signer.encoded)
		require.ErrorContains(t, err, "not valid for code signing")
	})

	t.Run("no usage", func(t *testing.T) {
		template := codeSigningTemplate("husi", now.Add(-time.Hour), now.Add(time.Hour))
		template.ExtKeyUsage = nil
		signer := newTestCertificate(t, template, nil)
		_, err := validateCodeSigningCertificate(signer.encoded)
		require.ErrorContains(t, err, "not valid for code signing")
	})

	t.Run("malformed", func(t *testing.T) {
		_, err := validateCodeSigningCertificate([]byte("not a certificate"))
		require.ErrorContains(t, err, "parse Authenticode signer certificate")
	})
}

func TestValidateUntrustedSelfSignedCertificate(t *testing.T) {
	t.Parallel()
	now := time.Now()

	t.Run("self signed", func(t *testing.T) {
		signer := newTestCertificate(t, codeSigningTemplate("husi", now.Add(-time.Hour), now.Add(time.Hour)), nil)
		require.NoError(t, validateUntrustedSelfSignedCertificate(signer.certificate, now))
	})

	t.Run("issued by another certificate", func(t *testing.T) {
		root := newTestCertificate(t, codeSigningTemplate("husi root", now.Add(-time.Hour), now.Add(time.Hour)), nil)
		leaf := newTestCertificate(t, codeSigningTemplate("husi leaf", now.Add(-time.Hour), now.Add(time.Hour)), root)
		err := validateUntrustedSelfSignedCertificate(leaf.certificate, now)
		require.ErrorContains(t, err, "not self-signed")
	})

	t.Run("self issued but not self signed", func(t *testing.T) {
		// Same subject and issuer names, signed by a different key: the name
		// check alone would pass, only the signature check catches it.
		impostorKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		require.NoError(t, err)
		template := codeSigningTemplate("husi", now.Add(-time.Hour), now.Add(time.Hour))
		subjectKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		require.NoError(t, err)
		encoded, err := x509.CreateCertificate(rand.Reader, template, template, &subjectKey.PublicKey, impostorKey)
		require.NoError(t, err)
		certificate, err := x509.ParseCertificate(encoded)
		require.NoError(t, err)
		err = validateUntrustedSelfSignedCertificate(certificate, now)
		require.ErrorContains(t, err, "verify untrusted Authenticode signer self-signature")
	})

	t.Run("expired", func(t *testing.T) {
		signer := newTestCertificate(t, codeSigningTemplate("husi", now.Add(-2*time.Hour), now.Add(-time.Hour)), nil)
		err := validateUntrustedSelfSignedCertificate(signer.certificate, now)
		require.ErrorContains(t, err, "not currently valid")
	})

	t.Run("not yet valid", func(t *testing.T) {
		signer := newTestCertificate(t, codeSigningTemplate("husi", now.Add(time.Hour), now.Add(2*time.Hour)), nil)
		err := validateUntrustedSelfSignedCertificate(signer.certificate, now)
		require.ErrorContains(t, err, "not currently valid")
	})
}
