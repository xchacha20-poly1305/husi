package shared

import (
	"cmp"
	"context"
	"net/url"
	"os"
	"strings"

	"github.com/sagernet/sing-box/common/tls"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/http"
	"github.com/sagernet/sing/protocol/socks"
)

var proxyEnvironments = []string{
	"ALL_PROXY", "all_proxy",
	"SOCKS_PROXY", "socks_proxy",
	"HTTPS_PROXY", "https_proxy",
	"HTTP_PROXY", "http_proxy",
}

// DialerFromEnv loads proxy dialer from environment.
// If no proxy URL be set, it uses fallback dialer. If dialer is empty, it uses N.SystemDialer.
// However, if it encounters any error when parsing proxy from URL, it returns all errors.
func DialerFromEnv(ctx context.Context, fallback N.Dialer) (N.Dialer, error) {
	var errs []error
	for _, env := range proxyEnvironments {
		raw := os.Getenv(env)
		if raw == "" {
			continue
		}
		dialer, err := proxyFromURL(ctx, raw)
		if err != nil {
			errs = append(errs, E.Cause(err, "parse proxy from env: ", env))
			continue
		}
		return dialer, nil
	}
	if len(errs) == 0 {
		return cmp.Or[N.Dialer](fallback, N.SystemDialer), nil
	}
	return nil, E.Cause(E.Errors(errs...), "all env failed")
}

func proxyFromURL(ctx context.Context, raw string) (N.Dialer, error) {
	u, err := url.Parse(raw)
	if err != nil {
		return nil, E.Cause(err, "parse URL")
	}
	if u.Host == "" {
		return nil, E.New("missing host")
	}
	scheme := strings.TrimRight(u.Scheme, "h") // curl private "h" suffix for socks remote resolve
	switch scheme {
	case "http":
		return newHTTPProxyClient(ctx, u, false), nil
	case "https":
		return newHTTPProxyClient(ctx, u, true), nil
	case "socks", "socks4":
		return newSocksClient(socks.Version4, u.Host, u.User), nil
	case "socks4a":
		return newSocksClient(socks.Version4A, u.Host, u.User), nil
	case "socks5":
		return newSocksClient(socks.Version5, u.Host, u.User), nil
	default:
		return nil, E.New("unsupported scheme")
	}
}

func destructUserInfo(user *url.Userinfo) (username, password string) {
	if user == nil {
		return
	}
	username = user.Username()
	password, _ = user.Password()
	return
}

func newHTTPProxyClient(ctx context.Context, u *url.URL, enableTLS bool) *http.Client {
	var dialer N.Dialer = N.SystemDialer
	if enableTLS {
		tlsConfig := common.Must1(tls.NewClientWithOptions(tls.ClientOptions{
			Context:       ctx,
			Logger:        logger.NOP(),
			ServerAddress: u.Host,
			Options: option.OutboundTLSOptions{
				Enabled: true,
			},
			AllowEmptyServerName: true,
		}))
		dialer = tls.NewDialer(N.SystemDialer, tlsConfig)
	}
	username, password := destructUserInfo(u.User)
	return http.NewClient(http.Options{
		Dialer:   dialer,
		Server:   M.ParseSocksaddr(u.Host),
		Username: username,
		Password: password,
		Path:     u.Path,
	})
}

func newSocksClient(version socks.Version, host string, user *url.Userinfo) *socks.Client {
	username, password := destructUserInfo(user)
	return socks.NewClient(N.SystemDialer, M.ParseSocksaddr(host), version, username, password)
}
