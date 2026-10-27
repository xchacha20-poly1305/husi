package rootcerts

import (
	"encoding/pem"
	"os"
	"path/filepath"
	"slices"
	"testing"
	"time"

	aTLS "github.com/sagernet/sing-box/common/tls"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestPlatformDistrustFiltersPoolAndPEM(t *testing.T) {
	_, denied, err := aTLS.GenerateCertificate(nil, nil, time.Now, "denied.example", time.Now().Add(time.Hour))
	require.NoError(t, err)
	_, allowed, err := aTLS.GenerateCertificate(nil, nil, time.Now, "allowed.example", time.Now().Add(time.Hour))
	require.NoError(t, err)
	block, _ := pem.Decode(denied)
	for name, raw := range map[string][]byte{"PEM": denied, "DER": block.Bytes} {
		t.Run(name, func(t *testing.T) {
			roots := NewBundle()
			require.NoError(t, roots.distrust(raw))
			require.NoError(t, roots.Append(slices.Concat(denied, allowed, denied)))
			assert.Equal(t, allowed, roots.PEM())
			assertPoolTrustsOnly(t, roots.pool, allowed)
			// A separately loaded store must not inherit platform distrust.
			independent := NewBundle()
			require.NoError(t, independent.Append(denied))
			assertPoolTrustsOnly(t, independent.pool, denied)
		})
	}
}

func TestDistrustDirectoryUsesCertificateContents(t *testing.T) {
	_, certificate, err := aTLS.GenerateCertificate(nil, nil, time.Now, "example.com", time.Now().Add(time.Hour))
	require.NoError(t, err)
	directory := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(directory, "01234567.0"), certificate, 0o600))
	roots := NewBundle()
	require.NoError(t, distrustDirectory(roots, directory))
	require.NoError(t, roots.Append(certificate))
	assert.Empty(t, roots.PEM())
	require.NoError(t, distrustDirectory(roots, filepath.Join(directory, "absent")))
}
