//go:build with_quic

package urltest_test

import (
	"crypto/x509"
	"net"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"github.com/sagernet/quic-go/http3"
	"github.com/sagernet/sing-box/adapter"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/urltest"
)

type testCertificateStore struct {
	pool *x509.CertPool
}

func (s *testCertificateStore) Name() string                   { return "test" }
func (s *testCertificateStore) Start(adapter.StartStage) error { return nil }
func (s *testCertificateStore) Close() error                   { return nil }
func (s *testCertificateStore) Pool() *x509.CertPool           { return s.pool }
func (s *testCertificateStore) ExclusiveAnchors() bool         { return true }

func TestMeasureHTTP3(t *testing.T) {
	var requests atomic.Int32
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		w.WriteHeader(http.StatusNoContent)
	})
	tlsServer := httptest.NewTLSServer(handler)
	t.Cleanup(tlsServer.Close)
	rootCAs := tlsServer.Client().Transport.(*http.Transport).TLSClientConfig.RootCAs

	packetConn, err := net.ListenPacket(N.NetworkUDP, "127.0.0.1:0")
	require.NoError(t, err)
	tlsConfig := tlsServer.TLS.Clone()
	tlsConfig.NextProtos = []string{http3.NextProtoH3}
	server := &http3.Server{Handler: handler, TLSConfig: tlsConfig}
	go server.Serve(packetConn)
	t.Cleanup(func() {
		_ = server.Close()
		_ = packetConn.Close()
	})

	ctx := service.ContextWith[adapter.CertificateStore](t.Context(), &testCertificateStore{pool: rootCAs})
	address := packetConn.LocalAddr().String()
	for _, scheme := range []string{"http3", "quic"} {
		requests.Store(0)
		_, err = urltest.Measure(ctx, scheme+"://"+address+"/", N.SystemDialer, urltest.UnifiedDelay)
		require.NoError(t, err, scheme)
		assert.Equal(t, int32(2), requests.Load(), scheme)
	}
}
