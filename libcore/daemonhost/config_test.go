package daemonhost

import (
	"net/netip"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDefaultConfigPath(t *testing.T) {
	assert.Equal(t, filepath.Join("abc", "daemon.json"), DefaultConfigPath("abc"))
}

func TestLoadConfigMissingFile(t *testing.T) {
	config, err := LoadConfig(t.Context(), filepath.Join(t.TempDir(), "daemon.json"))
	require.NoError(t, err)
	assert.Nil(t, config)
}

func TestLoadConfigValidFile(t *testing.T) {
	path := filepath.Join(t.TempDir(), "daemon.json")
	content := `{
  // extra endpoint
  "api": {
    "listen": "127.0.0.1",
    "listen_port": 9090,
    "secret": "s3cret",
    "access_control_allow_origin": ["https://example.com"],
    "access_control_allow_private_network": true
  }
}`
	require.NoError(t, os.WriteFile(path, []byte(content), 0o600))

	cfg, err := LoadConfig(t.Context(), path)
	require.NoError(t, err)
	require.NotNil(t, cfg)
	require.NotNil(t, cfg.API)
	require.NotNil(t, cfg.API.Listen)
	assert.Equal(t, "127.0.0.1", netip.Addr(*cfg.API.Listen).String())
	assert.Equal(t, uint16(9090), cfg.API.ListenPort)
	assert.Equal(t, "s3cret", cfg.API.Secret)
	assert.Equal(t, []string{"https://example.com"}, []string(cfg.API.AccessControlAllowOrigin))
	assert.True(t, cfg.API.AccessControlAllowPrivateNetwork)
}

func TestLoadConfigUnknownField(t *testing.T) {
	path := filepath.Join(t.TempDir(), "daemon.json")
	require.NoError(t, os.WriteFile(path, []byte(`{"api":{"dashboard":true}}`), 0o600))

	cfg, err := LoadConfig(t.Context(), path)
	require.Error(t, err)
	assert.Nil(t, cfg)
	assert.Contains(t, err.Error(), "unknown field")
}

func TestLoadConfigMalformed(t *testing.T) {
	path := filepath.Join(t.TempDir(), "daemon.json")
	require.NoError(t, os.WriteFile(path, []byte(`{not json`), 0o600))

	_, err := LoadConfig(t.Context(), path)
	require.Error(t, err)
}
