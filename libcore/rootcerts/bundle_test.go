package rootcerts

import (
	"crypto/sha256"
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"
	"slices"
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

func TestRootCABundleAppendSkipsUnparsableCertificates(t *testing.T) {
	_, certificatePEM, err := aTLS.GenerateCertificate(nil, nil, time.Now, "example.com", time.Now().Add(5*time.Minute))
	require.NoError(t, err)
	block, _ := pem.Decode(certificatePEM)
	require.NotNil(t, block)
	// A well-formed ASN.1 SEQUENCE that is not a certificate.
	invalidDER := []byte{0x30, 0x03, 0x02, 0x01, 0x01}
	invalidPEM := pem.EncodeToMemory(&pem.Block{Type: typeCert, Bytes: invalidDER})

	tests := []struct {
		name string
		raw  []byte
	}{
		{name: "PEM", raw: slices.Concat(invalidPEM, certificatePEM, invalidPEM)},
		{name: "DER", raw: slices.Concat(invalidDER, block.Bytes, invalidDER)},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			roots := NewBundle()
			assert.Error(t, roots.Append(test.raw))
			assert.Equal(t, certificatePEM, roots.PEM())
			assertPoolTrustsOnly(t, roots.pool, certificatePEM)
		})
	}
}

func TestRootCABundleAppendLeavesOutDistrustedRoots(t *testing.T) {
	distrustedPEM, err := os.ReadFile(filepath.Join("testdata", "distrusted-roots.pem"))
	require.NoError(t, err)
	_, certificatePEM, err := aTLS.GenerateCertificate(nil, nil, time.Now, "example.com", time.Now().Add(5*time.Minute))
	require.NoError(t, err)

	roots := NewBundle()
	require.NoError(t, roots.Append(slices.Concat(distrustedPEM, certificatePEM)))
	assert.Equal(t, certificatePEM, roots.PEM())
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
		name     string
		certFile string
		certDir  string
	}{
		{name: "file", certFile: certificateFile, certDir: emptyDirectory},
		{name: "directory", certFile: missingCertificateFile, certDir: certificateDirectory},
		{name: "both", certFile: certificateFile, certDir: certificateDirectory},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Setenv(certFileEnv, test.certFile)
			t.Setenv(certDirEnv, test.certDir)

			roots := NewBundle()
			require.NoError(t, appendSystemRootCAs(roots, false))
			// The platform store must not be consulted, so the test certificate
			// is the only one loaded, once even when several sources list it.
			assert.Equal(t, 1, strings.Count(string(roots.PEM()), string(certificatePEM)))
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

func TestLoadEmbeddedStoresLeaveOutDistrustedRoots(t *testing.T) {
	embeddedPEMs := map[Store]string{
		StoreMozilla: mozillaIncludedPEM(),
		StoreChrome:  chromeIncludedPEM(),
	}
	for store, embeddedPEM := range embeddedPEMs {
		embedded, err := splitCertificates([]byte(embeddedPEM))
		require.NoError(t, err)
		loaded, err := splitCertificates(Load(store).PEM())
		require.NoError(t, err)

		distrustedCount := 0
		for _, der := range embedded {
			if _, isDistrusted := distrustedRoots[sha256.Sum256(der)]; isDistrusted {
				distrustedCount++
			}
		}
		assert.Equal(t, len(embedded)-distrustedCount, len(loaded), "store %d", store)
		for _, der := range loaded {
			assert.NotContains(t, distrustedRoots, sha256.Sum256(der), "store %d", store)
		}
	}
}
