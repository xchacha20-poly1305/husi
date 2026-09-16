//go:build with_quic

package urltest

import (
	"context"
	"crypto/tls"
	"net"
	"net/http"

	"github.com/sagernet/quic-go"
	"github.com/sagernet/quic-go/http3"
)

func newHTTP3Transport(conn net.Conn, tlsConfig *tls.Config) (http.RoundTripper, error) {
	return &http3.Transport{
		TLSClientConfig: tlsConfig,
		Dial: func(ctx context.Context, _ string, tlsConfig *tls.Config, quicConfig *quic.Config) (*quic.Conn, error) {
			return quic.DialEarlyConn(ctx, conn, tlsConfig, quicConfig)
		},
	}, nil
}
