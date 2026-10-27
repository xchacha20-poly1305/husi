//go:build linux && !android

package rootcerts

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	aTLS "github.com/sagernet/sing-box/common/tls"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestReadDisabledCertificates(t *testing.T) {
	_, certificate, err := aTLS.GenerateCertificate(nil, nil, time.Now, "example.com", time.Now().Add(time.Hour))
	require.NoError(t, err)
	directory := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(directory, "disabled.crt"), certificate, 0o600))
	config := filepath.Join(directory, "ca-certificates.conf")
	require.NoError(t, os.WriteFile(config, []byte("# comment\nactive.crt\n!missing.crt\n!disabled.crt\n"), 0o600))
	roots := NewBundle()
	require.NoError(t, readDisabledCertificates(roots, config, directory))
	require.NoError(t, roots.Append(certificate))
	assert.Empty(t, roots.PEM())
	require.NoError(t, os.WriteFile(config, []byte("!../outside.crt\n"), 0o600))
	assert.Error(t, readDisabledCertificates(NewBundle(), config, directory))
}
