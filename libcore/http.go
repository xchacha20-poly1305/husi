package libcore

import (
	"context"
	"io"
	"net/http"
	"os"
	"time"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/httpfetch"
)

// HTTPClient is the Android binding of [httpfetch]. Desktop reaches the same
// code through husi.v1.ApplicationService/HTTPFetch.
type HTTPClient interface {
	// RestrictedTLS forces to use TLS 1.3.
	RestrictedTLS()

	// PinnedSHA256 accepts the server certificate whose sha256 matches, even
	// when it is self-signed. See [httpfetch.Request.PinnedSHA256].
	PinnedSHA256(sumHex string)

	// UseSocks5 connects to server by socks5.
	UseSocks5(port int32, username, password string)

	// SetAgeKey decrypts response bodies with age identities.
	SetAgeKey(identities string) error

	// NewRequest creates a new HTTPRequest base settings.
	NewRequest() HTTPRequest
}

// HTTPRequest is a GET request.
type HTTPRequest interface {
	// SetURL sets target by link.
	SetURL(link string)

	// SetHeader sets HTTP header.
	SetHeader(key string, value string)

	// SetUserAgent sets HTTP user agent.
	SetUserAgent(userAgent string)

	// SetTimeout sets the timeout in milliseconds. See [httpfetch.Request.Timeout]
	// for what zero or less means.
	SetTimeout(timeout int32)

	// Execute do HTTP query.
	Execute() (HTTPResponse, error)
}

// HTTPResponse is the HTTP server response.
type HTTPResponse interface {
	// GetHeader returns the header corresponding to the key.
	GetHeader(key string) string

	// GetContentString reads the whole body and closes it.
	GetContentString() (string, error)

	// WriteTo writes content to the file of `path` and closes the body.
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
	template httpfetch.Request
}

// NewHttpClient returns the basic HTTPClient.
func NewHttpClient() HTTPClient {
	return new(httpClient)
}

func (c *httpClient) RestrictedTLS() {
	c.template.RestrictedTLS = true
}

func (c *httpClient) PinnedSHA256(sumHex string) {
	c.template.PinnedSHA256 = sumHex
}

func (c *httpClient) UseSocks5(port int32, username, password string) {
	c.template.Socks5 = &httpfetch.Socks5{
		Port:     uint16(port),
		Username: username,
		Password: password,
	}
}

func (c *httpClient) SetAgeKey(identities string) (err error) {
	c.template.AgeIdentities, err = parseAgeIdentities(identities)
	return
}

func (c *httpClient) NewRequest() HTTPRequest {
	request := c.template
	request.Header = http.Header{}
	request.Timeout = C.TCPTimeout
	return &httpRequest{request: request}
}

type httpRequest struct {
	request httpfetch.Request
}

func (r *httpRequest) SetURL(link string) {
	r.request.URL = link
}

func (r *httpRequest) SetHeader(key string, value string) {
	r.request.Header.Set(key, value)
}

func (r *httpRequest) SetUserAgent(userAgent string) {
	r.request.Header.Set("User-Agent", userAgent)
}

func (r *httpRequest) SetTimeout(timeout int32) {
	r.request.Timeout = time.Duration(timeout) * time.Millisecond
}

func (r *httpRequest) Execute() (HTTPResponse, error) {
	response, err := httpfetch.Get(context.Background(), r.request)
	if err != nil {
		return nil, err
	}
	return &httpResponse{response}, nil
}

type httpResponse struct {
	*httpfetch.Response
}

func (h *httpResponse) GetHeader(key string) string {
	return h.Header.Get(key)
}

func (h *httpResponse) GetContentString() (string, error) {
	defer h.Body.Close()
	content, err := io.ReadAll(h.Body)
	if err != nil {
		return "", err
	}
	return string(content), nil
}

func (h *httpResponse) WriteTo(path string, callback CopyCallback) error {
	defer h.Body.Close()
	file, err := os.Create(path)
	if err != nil {
		return err
	}
	defer file.Close()
	var reader io.Reader = h.Body
	if callback != nil {
		callback.SetLength(h.ContentLength)
		reader = callbackReader{reader, callback.Update}
	}
	_, err = bufio.Copy(file, reader)
	if err != nil {
		return E.Cause(err, "download to ", path)
	}
	return nil
}

func (h *httpResponse) Close() error {
	return common.Close(h.Body)
}
