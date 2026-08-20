package simpleproxyurl

import (
	"fmt"
	"strings"
	"testing"

	N "github.com/sagernet/sing/common/network"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func dialerTypeName(t *testing.T, d N.Dialer) string {
	t.Helper()
	return fmt.Sprintf("%T", d)
}

func TestProxyFromURL(t *testing.T) {
	tests := []struct {
		name        string
		raw         string
		wantErr     bool
		errContains string
		typeContain string
	}{
		{
			name:        "http with auth",
			raw:         "http://user:pass@example.com:8080/proxy",
			typeContain: "http",
		},
		{
			name: "http without auth",
			raw:  "http://example.com:8080",
		},
		{
			name:        "https with auth",
			raw:         "https://user:pass@example.com:443",
			typeContain: "http",
		},
		{
			name:        "socks5 with auth",
			raw:         "socks5://user:pass@example.com:1080",
			typeContain: "socks",
		},
		{
			name: "socks4",
			raw:  "socks4://example.com:1080",
		},
		{
			name: "socks4a",
			raw:  "socks4a://example.com:1080",
		},
		{
			name: "socks bare",
			raw:  "socks://example.com:1080",
		},
		{
			name:        "socks5h trims curl h suffix",
			raw:         "socks5h://example.com:1080",
			typeContain: "socks",
		},
		{
			name:        "unsupported scheme",
			raw:         "ftp://user:pass@example.com:21",
			wantErr:     true,
			errContains: "unsupported scheme",
		},
		{
			name:        "missing host",
			raw:         "http:///no-host-here",
			wantErr:     true,
			errContains: "missing host",
		},
		{
			name:        "invalid url",
			raw:         "http://%zz",
			wantErr:     true,
			errContains: "parse URL",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			d, err := ProxyFromURL(t.Context(), tt.raw)

			if tt.wantErr {
				assert.Error(t, err)
				assert.Nil(t, d)
				if tt.errContains != "" {
					assert.Contains(t, err.Error(), tt.errContains)
				}
				return
			}

			require.NoError(t, err)
			require.NotNil(t, d)
			if tt.typeContain != "" {
				assert.Contains(t, strings.ToLower(dialerTypeName(t, d)), tt.typeContain)
			}
		})
	}
}

func TestDialerFromEnv(t *testing.T) {
	tests := []struct {
		name        string
		envs        map[string]string
		fallback    N.Dialer
		wantErr     bool
		errContains string
		typeContain string
		wantSame    N.Dialer
	}{
		{
			name:     "no env set returns fallback",
			envs:     map[string]string{},
			fallback: N.SystemDialer,
			wantSame: N.SystemDialer,
		},
		{
			name:     "no env set no fallback returns system dialer",
			envs:     map[string]string{},
			fallback: nil,
			wantSame: N.SystemDialer,
		},
		{
			name: "valid http proxy",
			envs: map[string]string{
				"HTTP_PROXY": "http://example.com:8080",
			},
		},
		{
			name: "priority all_proxy over http_proxy",
			envs: map[string]string{
				"ALL_PROXY":  "socks5://example.com:1080",
				"HTTP_PROXY": "http://example.com:8080",
			},
			typeContain: "socks",
		},
		{
			name: "skips invalid then uses valid",
			envs: map[string]string{
				"ALL_PROXY":  "ftp://example.com:21",
				"HTTP_PROXY": "http://example.com:8080",
			},
		},
		{
			name: "all invalid returns error",
			envs: map[string]string{
				"ALL_PROXY":  "ftp://example.com:21",
				"HTTP_PROXY": "ftp://example.com:21",
			},
			wantErr:     true,
			errContains: "all env failed",
		},
		{
			name: "lowercase env var works",
			envs: map[string]string{
				"http_proxy": "http://example.com:8080",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			for _, env := range proxyEnvironments {
				t.Setenv(env, "")
			}
			for k, v := range tt.envs {
				t.Setenv(k, v)
			}

			d, err := DialerFromEnv(t.Context(), tt.fallback)

			if tt.wantErr {
				assert.Error(t, err)
				assert.Nil(t, d)
				if tt.errContains != "" {
					assert.Contains(t, err.Error(), tt.errContains)
				}
				return
			}

			require.NoError(t, err)
			require.NotNil(t, d)
			if tt.wantSame != nil {
				assert.Same(t, tt.wantSame, d)
			}
			if tt.typeContain != "" {
				assert.Contains(t, strings.ToLower(dialerTypeName(t, d)), tt.typeContain)
			}
		})
	}
}
