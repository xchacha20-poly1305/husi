package libcore

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	F "github.com/sagernet/sing/common/format"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

const (
	pinnedTestHost     = "husi.fr"
	attackerHost       = "attacker.example"
	pinnedTestIPv4     = "127.0.0.1"
	attackerIPv4       = "127.0.0.2"
	pinnedTestIPv6     = "::1"
	pinnedTestResponse = "husi"
)

func TestHTTPClientPinnedSHA256(t *testing.T) {
	unmatchedSum := strings.Repeat("00", sha256.Size)

	tests := []struct {
		name            string
		certificateHost string
		requestHost     string
		trustServer     bool
		pinLeaf         bool
		wantErr         bool
	}{
		{
			name:            "pinned self signed certificate",
			certificateHost: pinnedTestHost,
			requestHost:     pinnedTestHost,
			pinLeaf:         true,
		},
		{
			name:            "unmatched pin on self signed certificate",
			certificateHost: pinnedTestHost,
			requestHost:     pinnedTestHost,
			wantErr:         true,
		},
		{
			name:            "trusted root with wrong host",
			certificateHost: attackerHost,
			requestHost:     pinnedTestHost,
			trustServer:     true,
			wantErr:         true,
		},
		{
			name:            "trusted root with requested host",
			certificateHost: pinnedTestHost,
			requestHost:     pinnedTestHost,
			trustServer:     true,
		},
		{
			name:            "trusted root with requested address",
			certificateHost: pinnedTestIPv4,
			requestHost:     pinnedTestIPv4,
			trustServer:     true,
		},
		{
			name:            "trusted root with wrong address",
			certificateHost: attackerIPv4,
			requestHost:     pinnedTestIPv4,
			trustServer:     true,
			wantErr:         true,
		},
		{
			name:            "trusted root with requested IPv6 address",
			certificateHost: pinnedTestIPv6,
			requestHost:     pinnedTestIPv6,
			trustServer:     true,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			keyPair := generateTestCertificate(t, test.certificateHost)
			address := startPinnedTestServer(t, keyPair)
			sumHex := unmatchedSum
			if test.pinLeaf {
				sumHex = certificateSHA256(keyPair)
			}

			client := NewHttpClient().(*httpClient)
			t.Cleanup(client.Close)
			client.transport.DialContext = func(ctx context.Context, network, _ string) (net.Conn, error) {
				return N.SystemDialer.DialContext(ctx, network, address)
			}
			if test.trustServer {
				roots := x509.NewCertPool()
				roots.AddCert(keyPair.Leaf)
				client.tls.RootCAs = roots
			}
			client.PinnedSHA256(sumHex)

			request := client.NewRequest()
			connectURL := &url.URL{
				Scheme: "https",
				Host:   net.JoinHostPort(test.requestHost, F.ToString(address.Port)),
			}
			require.NoError(t, request.SetURL(connectURL.String()))

			response, err := request.Execute()
			if test.wantErr {
				assert.Error(t, err)
				return
			}
			require.NoError(t, err)
			t.Cleanup(func() { _ = response.Close() })
			content, err := response.GetContentString()
			require.NoError(t, err)
			assert.Equal(t, pinnedTestResponse, content)
		})
	}
}

// aTLS.GenerateKeyPair can't build cert for IP.
func generateTestCertificate(t *testing.T, host string) tls.Certificate {
	t.Helper()
	privateKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	serialNumber, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	require.NoError(t, err)

	template := &x509.Certificate{
		SerialNumber:          serialNumber,
		Subject:               pkix.Name{CommonName: host},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(time.Hour),
		KeyUsage:              x509.KeyUsageKeyEncipherment | x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
	}
	if address := net.ParseIP(host); address != nil {
		template.IPAddresses = []net.IP{address}
	} else {
		template.DNSNames = []string{host}
	}

	certificateDER, err := x509.CreateCertificate(rand.Reader, template, template, privateKey.Public(), privateKey)
	require.NoError(t, err)
	leaf, err := x509.ParseCertificate(certificateDER)
	require.NoError(t, err)
	return tls.Certificate{
		Certificate: [][]byte{certificateDER},
		PrivateKey:  privateKey,
		Leaf:        leaf,
	}
}

func certificateSHA256(keyPair tls.Certificate) string {
	sum := sha256.Sum256(keyPair.Certificate[0])
	return hex.EncodeToString(sum[:])
}

func startPinnedTestServer(t *testing.T, keyPair tls.Certificate) M.Socksaddr {
	t.Helper()
	server := httptest.NewUnstartedServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		_, _ = writer.Write([]byte(pinnedTestResponse))
	}))
	server.TLS = &tls.Config{Certificates: []tls.Certificate{keyPair}}
	server.StartTLS()
	t.Cleanup(server.Close)
	return M.SocksaddrFromNet(server.Listener.Addr())
}
