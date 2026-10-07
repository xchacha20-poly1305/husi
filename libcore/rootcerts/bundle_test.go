package rootcerts

import (
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	aTLS "github.com/sagernet/sing-box/common/tls"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRootCABundleAppendBuildsPoolAndPEM(t *testing.T) {
	_, certificatePEM, err := aTLS.GenerateCertificate(nil, nil, time.Now, "example.com", time.Now().Add(5*time.Minute))
	require.NoError(t, err)

	roots := NewBundle()
	require.NoError(t, roots.Append(certificatePEM))

	block, remaining := pem.Decode(roots.PEM())
	require.NotNil(t, block)
	assert.Empty(t, remaining)
	_, err = x509.ParseCertificate(block.Bytes)
	require.NoError(t, err)

	assertPoolTrustsOnly(t, roots.pool, certificatePEM)
}

func TestAppendSystemRootCAsHonorsCertificateEnvironment(t *testing.T) {
	_, certificatePEM, err := aTLS.GenerateCertificate(nil, nil, time.Now, "example.com", time.Now().Add(5*time.Minute))
	require.NoError(t, err)

	certificateFile := filepath.Join(t.TempDir(), "ca.pem")
	require.NoError(t, os.WriteFile(certificateFile, certificatePEM, os.ModePerm))
	missingCertificateFile := filepath.Join(t.TempDir(), "missing.pem")

	certificateDirectory := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(certificateDirectory, "ca.crt"), certificatePEM, os.ModePerm))
	require.NoError(t, os.WriteFile(filepath.Join(certificateDirectory, "README"), []byte("not a certificate"), os.ModePerm))
	emptyDirectory := t.TempDir()

	tests := []struct {
		name          string
		certFile      string
		certDir       string
		wantAppearing int
	}{
		{name: "file", certFile: certificateFile, certDir: emptyDirectory, wantAppearing: 1},
		{name: "directory", certFile: missingCertificateFile, certDir: certificateDirectory, wantAppearing: 1},
		{name: "both", certFile: certificateFile, certDir: certificateDirectory, wantAppearing: 2},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Setenv(certFileEnv, test.certFile)
			t.Setenv(certDirEnv, test.certDir)

			roots := NewBundle()
			require.NoError(t, appendSystemRootCAs(roots, false))
			// The platform store must not be consulted, so the test certificate
			// is the only one loaded, once per source it was found in.
			assert.Equal(t, test.wantAppearing, strings.Count(string(roots.PEM()), string(certificatePEM)))
			assertPoolTrustsOnly(t, roots.pool, certificatePEM)
		})
	}
}

// assertPoolTrustsOnly prevents to use `(*x509.CertPool).Subjects`
func assertPoolTrustsOnly(t *testing.T, pool *x509.CertPool, certificatePEM []byte) {
	t.Helper()
	expectedPool := x509.NewCertPool()
	require.True(t, expectedPool.AppendCertsFromPEM(certificatePEM))
	assert.True(t, pool.Equal(expectedPool))
}

func TestLoadEmbeddedStores(t *testing.T) {
	for _, store := range []Store{StoreMozilla, StoreChrome} {
		roots := Load(store)
		assert.Contains(t, string(roots.PEM()), "-----BEGIN CERTIFICATE-----")
	}
}
