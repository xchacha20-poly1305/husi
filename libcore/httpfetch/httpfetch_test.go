package httpfetch

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"testing"
	"time"

	F "github.com/sagernet/sing/common/format"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	"filippo.io/age"
	"filippo.io/age/armor"
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

func TestClientPinnedSHA256(t *testing.T) {
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

			connectURL := &url.URL{
				Scheme: "https",
				Host:   net.JoinHostPort(test.requestHost, F.ToString(address.Port)),
			}
			request := Request{
				URL:          connectURL.String(),
				Timeout:      time.Minute,
				PinnedSHA256: sumHex,
			}
			if test.trustServer {
				request.RootCAs = x509.NewCertPool()
				request.RootCAs.AddCert(keyPair.Leaf)
			}
			client := newClient(request, test.requestHost)
			client.transport.DialContext = func(ctx context.Context, network, _ string) (net.Conn, error) {
				return N.SystemDialer.DialContext(ctx, network, address)
			}

			response, err := client.do(t.Context(), request, connectURL)
			if test.wantErr {
				assert.Error(t, err)
				return
			}
			require.NoError(t, err)
			assert.Equal(t, pinnedTestResponse, readBody(t, response))
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

func readBody(t *testing.T, response *Response) string {
	t.Helper()
	defer response.Body.Close()
	content, err := io.ReadAll(response.Body)
	require.NoError(t, err)
	return string(content)
}

func TestDoSlowBody(t *testing.T) {
	t.Parallel()

	const (
		chunkCount = 6
		chunkDelay = 100 * time.Millisecond
		bodyChunk  = "husi"
	)
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		flusher := writer.(http.Flusher)
		for range chunkCount {
			_, _ = writer.Write([]byte(bodyChunk))
			flusher.Flush()
			time.Sleep(chunkDelay)
		}
	}))
	defer server.Close()

	t.Run("an overall deadline cuts a slow body", func(t *testing.T) {
		response, err := Get(t.Context(), Request{URL: server.URL, Timeout: chunkDelay})
		require.NoError(t, err)
		defer response.Body.Close()
		_, err = io.ReadAll(response.Body)
		assert.Error(t, err)
	})

	t.Run("no overall deadline lets a slow body finish", func(t *testing.T) {
		response, err := Get(t.Context(), Request{URL: server.URL})
		require.NoError(t, err)
		assert.Equal(t, strings.Repeat(bodyChunk, chunkCount), readBody(t, response))
	})
}

func TestDoRejectsNon200WithBodyPreview(t *testing.T) {
	t.Parallel()

	longBody := strings.Repeat("x", errorBodyPreviewLength*2)
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusNotFound)
		_, _ = writer.Write([]byte(longBody))
	}))
	defer server.Close()

	_, err := Get(t.Context(), Request{URL: server.URL, Timeout: time.Minute})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "404")
	assert.Contains(t, err.Error(), longBody[:errorBodyPreviewLength]+" ...")
	assert.NotContains(t, err.Error(), longBody)
}

func TestDoSendsHeadersAndURLCredentials(t *testing.T) {
	t.Parallel()

	const (
		userAgent = "husi-test"
		username  = "user"
		password  = "pass"
	)
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		requestUsername, requestPassword, _ := request.BasicAuth()
		_, _ = writer.Write([]byte(request.UserAgent() + " " + requestUsername + ":" + requestPassword))
	}))
	defer server.Close()

	link, err := url.Parse(server.URL)
	require.NoError(t, err)
	link.User = url.UserPassword(username, password)
	response, err := Get(t.Context(), Request{
		URL:     link.String(),
		Header:  http.Header{"User-Agent": {userAgent}},
		Timeout: time.Minute,
	})
	require.NoError(t, err)
	assert.Equal(t, userAgent+" "+username+":"+password, readBody(t, response))
}

func TestDoDecryptsAgeBody(t *testing.T) {
	t.Parallel()

	const plaintext = "vless://husi"
	identity, err := age.GenerateX25519Identity()
	require.NoError(t, err)
	var armored bytes.Buffer
	armorWriter := armor.NewWriter(&armored)
	encryptWriter, err := age.Encrypt(armorWriter, identity.Recipient())
	require.NoError(t, err)
	_, err = io.WriteString(encryptWriter, plaintext)
	require.NoError(t, err)
	require.NoError(t, encryptWriter.Close())
	require.NoError(t, armorWriter.Close())

	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		_, _ = writer.Write(armored.Bytes())
	}))
	defer server.Close()

	response, err := Get(t.Context(), Request{
		URL:           server.URL,
		Timeout:       time.Minute,
		AgeIdentities: []age.Identity{identity},
	})
	require.NoError(t, err)
	assert.EqualValues(t, -1, response.ContentLength)
	assert.Equal(t, plaintext, readBody(t, response))
}

func TestStallReader(t *testing.T) {
	t.Parallel()

	const stallTimeout = 100 * time.Millisecond

	t.Run("a stalled transfer fails", func(t *testing.T) {
		t.Parallel()

		ctx, cancel := context.WithCancelCause(t.Context())
		reader := newStallReader(ctx, cancel, contextReader{ctx}, stallTimeout)
		_, err := reader.Read(make([]byte, 1))
		assert.ErrorContains(t, err, "stalled")
		assert.ErrorIs(t, err, os.ErrDeadlineExceeded)
	})

	t.Run("progress keeps the transfer alive", func(t *testing.T) {
		t.Parallel()

		const payload = "husi"
		ctx, cancel := context.WithCancelCause(t.Context())
		reader := newStallReader(ctx, cancel, io.NopCloser(strings.NewReader(payload)), stallTimeout)
		content, err := io.ReadAll(reader)
		require.NoError(t, err)
		assert.Equal(t, payload, string(content))
		require.NoError(t, reader.Close())
	})
}

// contextReader blocks like an HTTP response body does: it gives up only once
// the request context is done.
type contextReader struct {
	ctx context.Context
}

func (c contextReader) Read([]byte) (int, error) {
	<-c.ctx.Done()
	return 0, c.ctx.Err()
}

func (c contextReader) Close() error {
	return nil
}
