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
	var username, password string
	if user := u.User; user != nil {
		username = user.Username()
		password, _ = user.Password()
	}
	scheme := strings.TrimRight(u.Scheme, "h") // curl private "h" suffix for socks remote resolve
	switch scheme {
	case "http":
		return http.NewClient(http.Options{
			Dialer:   N.SystemDialer,
			Server:   M.ParseSocksaddr(u.Host),
			Username: username,
			Password: password,
			Path:     u.Path,
		}), nil
	case "https":
		tlsConfig := common.Must1(tls.NewSTDClient(ctx, logger.NOP(), u.Host, option.OutboundTLSOptions{
			Enabled: true,
		}))
		dialer := tls.NewDialer(N.SystemDialer, tlsConfig)
		return http.NewClient(http.Options{
			Dialer:   dialer,
			Server:   M.ParseSocksaddr(u.Host),
			Username: username,
			Password: password,
			Path:     u.Path,
		}), nil
	case "socks", "socks4", "socks4a", "socks5":
		var version socks.Version
		switch scheme {
		case "socks", "socks4":
			version = socks.Version4
		case "socks4a":
			version = socks.Version4A
		case "socks5":
			version = socks.Version5
		}
		return socks.NewClient(N.SystemDialer, M.ParseSocksaddr(u.Host), version, username, password), nil
	default:
		return nil, E.New("unsupported scheme")
	}
}
