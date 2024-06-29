package libcore

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"sync"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
	"github.com/sagernet/sing/protocol/socks/socks5"
)

// HTTPClient is an adapt client of http.
type HTTPClient interface {
	// RestrictedTLS forces to use TLS 1.3.
	RestrictedTLS()

	// ModernTLS allows use common TLS with TLS 1.2.
	ModernTLS()

	// PinnedTLS12 forces to use TLS 1.2.
	PinnedTLS12()

	// PinnedSHA256 verifies server TLS certificate's sha256 whether same as sumHex.
	// If not, it will reject this handshake.
	PinnedSHA256(sumHex string)

	// TrySocks5 tries to connect to server by socks5 from socksInfo.
	TrySocks5(socksInfo *SocksInfo)

	// KeepAlive force use HTTP/2 and enable keep alive.
	KeepAlive()

	// SetInsecure disables TLS secure verifying.
	SetInsecure(allow bool)

	// NewRequest creates a new HTTPRequest base settings.
	NewRequest() HTTPRequest

	// Close closes all connections.
	Close()
}

// HTTPRequest is an custom HTTP request.
type HTTPRequest interface {
	// SetURL sets target by link.
	SetURL(link string) error

	// SetMethod sets HTTP mod.
	SetMethod(method string)

	// SetHeader sets HTTP header.
	SetHeader(key string, value string)

	// SetContent sets the content you want to send to server.
	SetContent(content []byte)
	SetContentString(content string)

	// SetUserAgent sets HTTP user agent.
	SetUserAgent(userAgent string)

	// Execute do HTTP query.
	Execute() (HTTPResponse, error)
}

// HTTPResponse is the HTTP server response.
type HTTPResponse interface {
	// GetHeader returns the header corresponding to the key.
	GetHeader(key string) string

	// GetContent returns server content in response.
	GetContent() ([]byte, error)
	GetContentString() (string, error)

	// WriteTo writes content to the file of `path`.
	WriteTo(path string) error
}

var (
	_ HTTPClient   = (*httpClient)(nil)
	_ HTTPRequest  = (*httpRequest)(nil)
	_ HTTPResponse = (*httpResponse)(nil)
)

type httpClient struct {
	tls       tls.Config
	client    http.Client
	transport http.Transport
}

// NewHttpClient returns the basic HTTPClient.
func NewHttpClient() HTTPClient {
	client := new(httpClient)
	client.client.Transport = &client.transport
	client.transport.TLSClientConfig = &client.tls
	client.transport.DisableKeepAlives = true
	return client
}

func (c *httpClient) ModernTLS() {
	c.tls.MinVersion = tls.VersionTLS12
	c.tls.CipherSuites = common.Map(tls.CipherSuites(), func(it *tls.CipherSuite) uint16 { return it.ID })
}

func (c *httpClient) RestrictedTLS() {
	c.tls.MinVersion = tls.VersionTLS13
	c.tls.CipherSuites = common.Map(common.Filter(tls.CipherSuites(), func(it *tls.CipherSuite) bool {
		return common.Contains(it.SupportedVersions, uint16(tls.VersionTLS13))
	}), func(it *tls.CipherSuite) uint16 {
		return it.ID
	})
}

func (c *httpClient) PinnedTLS12() {
	c.tls.MinVersion = tls.VersionTLS12
	c.tls.MaxVersion = tls.VersionTLS12
	c.tls.CipherSuites = common.Map(common.Filter(tls.CipherSuites(), func(it *tls.CipherSuite) bool {
		return common.Contains(it.SupportedVersions, uint16(tls.VersionTLS12))
	}), func(it *tls.CipherSuite) uint16 {
		return it.ID
	})
}

func (c *httpClient) PinnedSHA256(sumHex string) {
	c.tls.VerifyPeerCertificate = func(rawCerts [][]byte, verifiedChains [][]*x509.Certificate) error {
		for _, rawCert := range rawCerts {
			certSum := sha256.Sum256(rawCert)
			if sumHex == hex.EncodeToString(certSum[:]) {
				return nil
			}
		}
		return E.New("pinned sha256 sum mismatch")
	}
}

func (c *httpClient) TrySocks5(socksInfo *SocksInfo) {
	dialer := new(net.Dialer)
	c.transport.DialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
		for {
			if socksInfo == nil || socksInfo.Port == "" {
				break
			}
			socksConn, err := dialer.DialContext(
				ctx,
				N.NetworkTCP,
				net.JoinHostPort("127.0.0.1", socksInfo.Port),
			)
			if err != nil {
				break
			}
			_, err = socks.ClientHandshake5(
				socksConn,
				socks5.CommandConnect,
				metadata.ParseSocksaddr(addr),
				socksInfo.Username,
				socksInfo.Password,
			)
			if err != nil {
				break
			}
			//nolint:staticcheck
			return socksConn, err
		}
		return dialer.DialContext(ctx, network, addr)
	}
}

func (c *httpClient) SetInsecure(allow bool) {
	c.tls.InsecureSkipVerify = allow
}

func (c *httpClient) KeepAlive() {
	c.transport.ForceAttemptHTTP2 = true
	c.transport.DisableKeepAlives = false
}

func (c *httpClient) NewRequest() HTTPRequest {
	req := &httpRequest{httpClient: c}
	req.request = http.Request{
		Method: "GET",
		Header: http.Header{},
	}
	return req
}

func (c *httpClient) Close() {
	c.transport.CloseIdleConnections()
}

type httpRequest struct {
	*httpClient
	request http.Request
}

func (r *httpRequest) SetURL(link string) (err error) {
	r.request.URL, err = url.Parse(link)
	if err != nil {
		return
	}
	if r.request.URL.User != nil {
		user := r.request.URL.User.Username()
		password, _ := r.request.URL.User.Password()
		r.request.SetBasicAuth(user, password)
	}
	return
}

func (r *httpRequest) SetMethod(method string) {
	r.request.Method = method
}

func (r *httpRequest) SetHeader(key string, value string) {
	r.request.Header.Set(key, value)
}

func (r *httpRequest) SetUserAgent(userAgent string) {
	r.request.Header.Set("User-Agent", userAgent)
}

func (r *httpRequest) SetContent(content []byte) {
	buffer := bytes.Buffer{}
	buffer.Write(content)
	r.request.Body = io.NopCloser(bytes.NewReader(buffer.Bytes()))
	r.request.ContentLength = int64(len(content))
}

func (r *httpRequest) SetContentString(content string) {
	r.SetContent([]byte(content))
}

func (r *httpRequest) Execute() (HTTPResponse, error) {
	response, err := r.client.Do(&r.request)
	if err != nil {
		return nil, err
	}
	httpResp := &httpResponse{Response: response}
	if response.StatusCode != http.StatusOK {
		return nil, errors.New(httpResp.errorString())
	}
	return httpResp, nil
}

type httpResponse struct {
	*http.Response

	getContentOnce sync.Once
	content        []byte
	contentError   error
}

func (h *httpResponse) errorString() string {
	content, err := h.GetContentString()
	if err != nil {
		return fmt.Sprint("HTTP ", h.Response.Status)
	}
	if len(content) > 100 {
		content = content[:100] + " ..."
	}
	return fmt.Sprint("HTTP ", h.Response.Status, ": ", content)
}

func (h *httpResponse) GetHeader(key string) string {
	return h.Response.Header.Get(key)
}

func (h *httpResponse) GetContent() ([]byte, error) {
	h.getContentOnce.Do(func() {
		defer h.Response.Body.Close()
		h.content, h.contentError = io.ReadAll(h.Response.Body)
	})
	return h.content, h.contentError
}

func (h *httpResponse) GetContentString() (string, error) {
	content, err := h.GetContent()
	if err != nil {
		return "", err
	}
	return string(content), nil
}

func (h *httpResponse) WriteTo(path string) error {
	defer h.Response.Body.Close()
	file, err := os.Create(path)
	if err != nil {
		return err
	}
	defer file.Close()
	_, err = io.Copy(file, h.Response.Body)
	return err
}
