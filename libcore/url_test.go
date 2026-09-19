package libcore

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func Test_ParseUrl(t *testing.T) {
	type args struct {
		rawURL string
	}
	tests := []struct {
		name    string
		args    args
		isWant  func(u URL) bool
		wantErr bool
	}{
		{
			name: "no port",
			args: args{
				rawURL: "hysteria2://ganggang@icecreamsogood/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "ganggang" {
					return false
				}
				if u.GetHost() != "icecreamsogood" {
					return false
				}
				return true
			},
		},
		{
			name: "single port",
			args: args{
				rawURL: "hysteria2://yesyes@icecreamsogood:8888/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "yesyes" {
					return false
				}
				if u.GetHost() != "icecreamsogood" {
					return false
				}
				if u.GetPorts() != "8888" {
					return false
				}
				return true
			},
		},
		{
			name: "multi port",
			args: args{
				rawURL: "hysteria2://darkness@laplus.org:8888,9999,11111/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "darkness" {
					return false
				}
				if u.GetHost() != "laplus.org" {
					return false
				}
				if u.GetPorts() != "8888,9999,11111" {
					return false
				}
				return true
			},
		},
		{
			name: "range port",
			args: args{
				rawURL: "hysteria2://darkness@laplus.org:8888-9999/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "darkness" {
					return false
				}
				if u.GetHost() != "laplus.org" {
					return false
				}
				if u.GetPorts() != "8888-9999" {
					return false
				}
				return true
			},
		},
		{
			name: "both",
			args: args{
				rawURL: "hysteria2://gawr:gura@atlantis.moe:443,7788-8899,10010/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "gawr" {
					return false
				}
				if u.GetPassword() != "gura" {
					return false
				}
				if u.GetHost() != "atlantis.moe" {
					return false
				}
				if u.GetPorts() != "443,7788-8899,10010" {
					return false
				}
				return true
			},
		},
		{
			name: "relative path with query, no scheme or host",
			args: args{
				rawURL: "/ws?ed=2560",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetPath() != "/ws" {
					return false
				}
				if u.QueryParameter("ed") != "2560" {
					return false
				}
				return true
			},
		},
		{
			name: "relative path with many query parameters",
			args: args{
				rawURL: "/ws?foo=1&ed=2560&bar=2&baz=hello%20world",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetPath() != "/ws" {
					return false
				}
				if u.QueryParameter("ed") != "2560" {
					return false
				}
				if u.QueryParameter("foo") != "1" {
					return false
				}
				if u.QueryParameter("bar") != "2" {
					return false
				}
				if u.QueryParameter("baz") != "hello world" {
					return false
				}
				return true
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParseURL(tt.args.rawURL)
			if tt.wantErr {
				assert.Error(t, err)
				return
			}
			require.NoError(t, err)
			require.NotNil(t, got)
			assert.True(t, tt.isWant(got), got.GetString())
		})
	}
}

func Test_URL_RemoveQueryParameter(t *testing.T) {
	u, err := ParseURL("/ws?foo=1&ed=2560&bar=2")
	require.NoError(t, err)

	u.RemoveQueryParameter("ed")

	assert.Equal(t, "/ws", u.GetPath())
	assert.Empty(t, u.QueryParameter("ed"))
	assert.Equal(t, "1", u.QueryParameter("foo"))
	assert.Equal(t, "2", u.QueryParameter("bar"))

	rebuilt := u.GetString()
	assert.NotContains(t, rebuilt, "ed=")
	assert.Contains(t, rebuilt, "foo=1")
	assert.Contains(t, rebuilt, "bar=2")
}

func Test_URL_IPv6Host(t *testing.T) {
	tests := []struct {
		name     string
		rawURL   string
		wantHost string
		wantPort string
	}{
		{
			name:     "bracketed without port",
			rawURL:   "http://[2001:db8::1]/path",
			wantHost: "2001:db8::1",
		},
		{
			name:     "bracketed with port",
			rawURL:   "http://[2001:db8::1]:8080/path",
			wantHost: "2001:db8::1",
			wantPort: "8080",
		},
		{
			name:     "loopback with credentials",
			rawURL:   "hysteria2://gawr:gura@[::1]:443/",
			wantHost: "::1",
			wantPort: "443",
		},
		{
			name:     "hysteria port hop",
			rawURL:   "hysteria2://darkness@[2001:db8::1]:443,7788-8899,10010/",
			wantHost: "2001:db8::1",
			wantPort: "443,7788-8899,10010",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			u, err := ParseURL(tt.rawURL)
			require.NoError(t, err)
			assert.Equal(t, tt.wantHost, u.GetHost())
			assert.Equal(t, tt.wantPort, u.GetPorts())
			assert.Equal(t, tt.rawURL, u.GetString())
		})
	}
}

func Test_URL_IPv6RoundTrip(t *testing.T) {
	parsed, err := ParseURL("http://[2001:db8::1]/path")
	require.NoError(t, err)

	rebuilt := NewURL("http")
	rebuilt.SetHost(parsed.GetHost())
	rebuilt.SetPath(parsed.GetPath())

	assert.Equal(t, "[2001:db8::1]", rebuilt.GetFullHost())
	assert.Equal(t, "http://[2001:db8::1]/path", rebuilt.GetString())
}

func Test_URL_SetIPv6HostAndPorts(t *testing.T) {
	t.Run("host then port", func(t *testing.T) {
		u := NewURL("socks5")
		u.SetHost("::1")
		assert.Equal(t, "socks5://[::1]", u.GetString())

		u.SetPorts("1080")
		assert.Equal(t, "::1", u.GetHost())
		assert.Equal(t, "1080", u.GetPorts())
		assert.Equal(t, "socks5://[::1]:1080", u.GetString())
	})

	t.Run("port then host", func(t *testing.T) {
		u := NewURL("socks5")
		u.SetPorts("1080")
		u.SetHost("::1")
		assert.Equal(t, "::1", u.GetHost())
		assert.Equal(t, "1080", u.GetPorts())
		assert.Equal(t, "socks5://[::1]:1080", u.GetString())
	})

	t.Run("replace domain with ipv6", func(t *testing.T) {
		u, err := ParseURL("https://laplus.org:8888/path")
		require.NoError(t, err)

		u.SetHost("2001:db8::1")
		assert.Equal(t, "https://[2001:db8::1]:8888/path", u.GetString())
	})

	t.Run("replace ipv6 with domain", func(t *testing.T) {
		u, err := ParseURL("https://[2001:db8::1]/path")
		require.NoError(t, err)

		u.SetHost("laplus.org")
		assert.Equal(t, "https://laplus.org/path", u.GetString())
	})
}
