package libcore

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"sync"
	"time"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
	"github.com/sagernet/sing/protocol/socks/socks5"
)

// HTTPClient is an adapt client of http.
type HTTPClient interface {
	// RestrictedTLS forces to use TLS 1.3.
	RestrictedTLS()

	// PinnedSHA256 verifies server TLS certificate's sha256 when using self-signed certificates.
	// This is designed for OOCv1
	//
	// https://github.com/Shadowsocks-NET/OpenOnlineConfig/blob/0db1f2452f8ad579967ca4c5092f5e11053c813c/docs/0001-open-online-config-v1.md?plain=1#L69
	PinnedSHA256(sumHex string)

	// UseSocks5 connects to server by socks5.
	UseSocks5(port int32, username, password string)

	// KeepAlive force use HTTP/2 and enable keep alive.
	KeepAlive()

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
	// SetContentZero writes zero to server.
	SetContentZero(n int64, callback CopyCallback)

	// SetUserAgent sets HTTP user agent.
	SetUserAgent(userAgent string)

	// SetTimeout sets timeout millisecond.
	SetTimeout(timeout int32)

	// Execute do HTTP query.
	Execute() (HTTPResponse, error)
}

// HTTPResponse is the HTTP server response.
type HTTPResponse interface {
	// GetHeader returns the header corresponding to the key.
	GetHeader(key string) string

	// GetContentString returns server content string in response.
	GetContentString() (*StringWrapper, error)

	// WriteTo writes content to the file of `path`.
	// callback could be nil
	WriteTo(path string, callback CopyCallback) error

	// Close force to close response even the current action not finished.
	Close() error
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
	client.transport.TLSHandshakeTimeout = C.TCPTimeout
	client.transport.TLSClientConfig = &client.tls
	client.transport.DisableKeepAlives = true
	return client
}

func (c *httpClient) RestrictedTLS() {
	c.tls.MinVersion = tls.VersionTLS13
}

func (c *httpClient) PinnedSHA256(sumHex string) {
	c.tls.InsecureSkipVerify = true
	c.tls.VerifyPeerCertificate = func(rawCerts [][]byte, _ [][]*x509.Certificate) error {
		opts := x509.VerifyOptions{
			DNSName:       c.tls.ServerName,
			Roots:         c.tls.RootCAs,
			Intermediates: x509.NewCertPool(),
		}
		if c.tls.Time != nil {
			opts.CurrentTime = c.tls.Time()
		}
		for _, rawCert := range rawCerts[1:] {
			cert, _ := x509.ParseCertificate(rawCert)
			opts.Intermediates.AddCert(cert)
		}
		cert, _ := x509.ParseCertificate(rawCerts[0])
		_, err := cert.Verify(opts)
		if err == nil {
			return nil
		}
		certSum := sha256.Sum256(rawCerts[0])
		if sumHex == hex.EncodeToString(certSum[:]) {
			return nil
		}
		return E.Errors(err, E.New("cert sha256 not matched"))
	}
}

func (c *httpClient) UseSocks5(port int32, username, password string) {
	dialer := new(net.Dialer)
	c.transport.DialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
		if port <= 0 {
			return nil, E.New("invalid port")
		}
		socksConn, err := dialer.DialContext(
			ctx,
			N.NetworkTCP,
			net.JoinHostPort("127.0.0.1", strconv.Itoa(int(port))),
		)
		if err != nil {
			return nil, err
		}
		_, err = socks.ClientHandshake5(
			socksConn,
			socks5.CommandConnect,
			metadata.ParseSocksaddr(addr),
			username,
			password,
		)
		if err != nil {
			socksConn.Close()
			return nil, err
		}
		return socksConn, nil
	}
}

func (c *httpClient) KeepAlive() {
	c.transport.ForceAttemptHTTP2 = true
	c.transport.DisableKeepAlives = false
}

func (c *httpClient) NewRequest() HTTPRequest {
	req := &httpRequest{httpClient: c}
	req.request = http.Request{
		Method: http.MethodGet,
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

func (r *httpRequest) SetContentZero(n int64, callback CopyCallback) {
	reader := io.NopCloser(io.LimitReader(zeroReader{}, n))
	if callback != nil {
		callback.SetLength(n)
		reader = callbackReader{reader, callback.Update}
	}
	r.request.Body = reader
	r.request.ContentLength = n
}

func (r *httpRequest) SetTimeout(timeout int32) {
	r.client.Timeout = time.Duration(timeout) * time.Millisecond
}

func (r *httpRequest) Execute() (HTTPResponse, error) {
	response, err := r.client.Do(&r.request)
	if err != nil {
		return nil, err
	}
	httpResp := &httpResponse{Response: response}
	if response.StatusCode != http.StatusOK {
		return nil, E.New(httpResp.errorString())
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
		return F.ToString("HTTP ", h.Response.Status)
	}
	httpValue := content.Value
	if len(httpValue) > 100 {
		httpValue = httpValue[:100] + " ..."
	}
	return F.ToString("HTTP ", h.Response.Status, ": ", httpValue)
}

func (h *httpResponse) GetHeader(key string) string {
	return h.Response.Header.Get(key)
}

func (h *httpResponse) GetContentString() (*StringWrapper, error) {
	h.getContentOnce.Do(func() {
		defer h.Body.Close()
		h.content, h.contentError = io.ReadAll(h.Body)
	})
	if h.contentError != nil {
		return nil, h.contentError
	}
	return wrapString(string(h.content)), nil
}

func (h *httpResponse) WriteTo(path string, callback CopyCallback) error {
	defer h.Response.Body.Close()
	var writer io.Writer
	if path == DevNull {
		// Android not support /dev/null
		writer = io.Discard
	} else {
		file, err := os.Create(path)
		if err != nil {
			return err
		}
		defer file.Close()
		writer = file
	}
	reader := h.Response.Body
	if callback != nil {
		callback.SetLength(h.Response.ContentLength)
		reader = &callbackReader{reader, callback.Update}
	}
	return common.Error(bufio.Copy(writer, reader))
}

func (h *httpResponse) Close() error {
	return h.Response.Body.Close()
}
