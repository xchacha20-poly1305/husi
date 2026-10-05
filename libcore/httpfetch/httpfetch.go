// Package httpfetch performs the HTTP GETs husi makes on its own behalf:
// subscription, rule set and app update downloads.
//
// It is shared by the Android binding (libcore.HTTPClient) and the desktop
// core host (husi.v1.ApplicationService/HTTPFetch), so both platforms verify
// TLS, reach the local socks inbound and decrypt age payloads the same way.
package httpfetch

import (
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"errors"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing/common/canceler"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
	"github.com/sagernet/sing/protocol/socks/socks5"

	"filippo.io/age"
	"filippo.io/age/armor"
)

const StallTimeout = C.TCPTimeout

const errorBodyPreviewLength = 100

type Socks5 struct {
	Port     uint16
	Username string
	Password string
}

type Request struct {
	URL    string
	Header http.Header
	// Timeout bounds the whole exchange. Zero or less leaves it without an
	// overall deadline, which large downloads on slow links need; connection
	// setup still times out, and a body that stops making progress for
	// [StallTimeout] fails.
	Timeout time.Duration
	// RestrictedTLS forces TLS 1.3.
	RestrictedTLS bool
	// PinnedSHA256 is the hex SHA-256 of a leaf certificate that is accepted
	// even when it does not chain to RootCAs. This is designed for OOCv1:
	// https://github.com/Shadowsocks-NET/OpenOnlineConfig/blob/0db1f2452f8ad579967ca4c5092f5e11053c813c/docs/0001-open-online-config-v1.md?plain=1#L69
	PinnedSHA256  string
	RootCAs       *x509.CertPool
	Socks5        *Socks5
	AgeIdentities []age.Identity
}

type Response struct {
	Header http.Header

	// ContentLength is the length of Body, or -1 when unknown.
	ContentLength int64

	Body io.ReadCloser
}

func Get(ctx context.Context, request Request) (*Response, error) {
	link, err := url.Parse(request.URL)
	if err != nil {
		return nil, err
	}
	return newClient(request, link.Hostname()).do(ctx, request, link)
}

// client serves a single [Request]: its TLS verification is bound to the
// requested host, and it keeps no connection alive afterwards.
type client struct {
	tls          tls.Config
	transport    http.Transport
	client       http.Client
	pinnedSHA256 string
}

func newClient(request Request, hostname string) *client {
	c := &client{
		pinnedSHA256: strings.ToLower(strings.TrimSpace(request.PinnedSHA256)),
	}
	c.tls.RootCAs = request.RootCAs
	if request.RestrictedTLS {
		c.tls.MinVersion = tls.VersionTLS13
	}
	if c.pinnedSHA256 != "" {
		// Go leaves ConnectionState.ServerName empty for an IP address, so
		// the fallback verification takes the host from the request instead.
		c.tls.ServerName = hostname
		c.tls.InsecureSkipVerify = true
		c.tls.VerifyConnection = c.verifyConnection
	}
	c.transport.TLSClientConfig = &c.tls
	c.transport.TLSHandshakeTimeout = C.TCPTimeout
	c.transport.ResponseHeaderTimeout = C.TCPTimeout
	c.transport.DisableKeepAlives = true
	c.transport.ForceAttemptHTTP2 = true
	if request.Socks5 != nil {
		c.transport.DialContext = socks5DialContext(*request.Socks5)
	}
	c.client.Transport = &c.transport
	return c
}

// verifyConnection accepts the pinned leaf, and otherwise falls back to the
// normal chain and host name verification that InsecureSkipVerify turned off.
func (c *client) verifyConnection(state tls.ConnectionState) error {
	if len(state.PeerCertificates) == 0 {
		return E.New("missing peer certificate")
	}
	certificate := state.PeerCertificates[0]
	certSum := sha256.Sum256(certificate.Raw)
	if c.pinnedSHA256 == hex.EncodeToString(certSum[:]) {
		return nil
	}

	options := x509.VerifyOptions{
		DNSName:       c.tls.ServerName,
		Roots:         c.tls.RootCAs,
		Intermediates: x509.NewCertPool(),
	}
	for _, intermediate := range state.PeerCertificates[1:] {
		options.Intermediates.AddCert(intermediate)
	}
	_, err := certificate.Verify(options)
	if err != nil {
		return E.Errors(err, E.New("cert sha256 not matched"))
	}
	return nil
}

func socks5DialContext(proxy Socks5) func(ctx context.Context, network, address string) (net.Conn, error) {
	proxyAddress := M.ParseSocksaddrHostPort("127.0.0.1", proxy.Port)
	return func(ctx context.Context, _, address string) (net.Conn, error) {
		if proxy.Port == 0 {
			return nil, E.New("invalid socks port")
		}
		conn, err := N.SystemDialer.DialContext(ctx, N.NetworkTCP, proxyAddress)
		if err != nil {
			return nil, err
		}
		_, err = socks.ClientHandshake5(
			conn,
			socks5.CommandConnect,
			M.ParseSocksaddr(address),
			proxy.Username,
			proxy.Password,
		)
		if err != nil {
			_ = conn.Close()
			return nil, err
		}
		return conn, nil
	}
}

func (c *client) do(ctx context.Context, request Request, link *url.URL) (*Response, error) {
	stopDeadline := context.CancelFunc(func() {})
	if request.Timeout > 0 {
		ctx, stopDeadline = context.WithTimeout(ctx, request.Timeout)
	}
	ctx, cancel := context.WithCancelCause(ctx)
	release := func(cause error) {
		cancel(cause)
		stopDeadline()
	}

	httpRequest, err := http.NewRequestWithContext(ctx, http.MethodGet, link.String(), nil)
	if err != nil {
		release(err)
		return nil, err
	}
	if request.Header != nil {
		httpRequest.Header = request.Header.Clone()
	}
	if link.User != nil {
		password, _ := link.User.Password()
		httpRequest.SetBasicAuth(link.User.Username(), password)
	}

	httpResponse, err := c.client.Do(httpRequest)
	if err != nil {
		release(err)
		return nil, err
	}
	if httpResponse.StatusCode != http.StatusOK {
		err = statusError(httpResponse)
		release(err)
		return nil, err
	}

	var body io.ReadCloser = &releaseOnClose{ReadCloser: httpResponse.Body, release: release}
	if request.Timeout <= 0 {
		body = newStallReader(ctx, cancel, body, StallTimeout)
	}
	response := &Response{
		Header:        httpResponse.Header,
		ContentLength: httpResponse.ContentLength,
		Body:          body,
	}
	if len(request.AgeIdentities) > 0 {
		decrypted, err := age.Decrypt(armor.NewReader(body), request.AgeIdentities...)
		if err != nil {
			_ = body.Close()
			return nil, E.Cause(err, "decrypt age response")
		}
		response.ContentLength = -1
		response.Body = readCloser{Reader: decrypted, Closer: body}
	}
	return response, nil
}

func statusError(response *http.Response) error {
	defer response.Body.Close()
	preview, err := io.ReadAll(io.LimitReader(response.Body, errorBodyPreviewLength+1))
	if err != nil || len(preview) == 0 {
		retErr := E.New("HTTP ", response.Status)
		if err != nil {
			retErr = E.Cause1(retErr, err)
		}
		return retErr
	}
	var content string
	if len(preview) > errorBodyPreviewLength {
		content = string(preview[:errorBodyPreviewLength]) + " ..."
	} else {
		content = string(preview)
	}
	return E.New("HTTP ", response.Status, ": ", content)
}

// releaseOnClose releases the request context once the body is done with.
type releaseOnClose struct {
	io.ReadCloser
	release func(cause error)
}

func (r *releaseOnClose) Close() error {
	err := r.ReadCloser.Close()
	r.release(context.Canceled)
	return err
}

type readCloser struct {
	io.Reader
	io.Closer
}

// stallReader fails a transfer that stops delivering bytes. It is only meant
// for requests that have no overall deadline of their own.
type stallReader struct {
	reader   io.ReadCloser
	ctx      context.Context
	canceler *canceler.Instance
}

// newStallReader starts a timer that cancels ctx through cancel once timeout
// passes without a read; each read that returns data pushes the deadline back.
func newStallReader(
	ctx context.Context,
	cancel context.CancelCauseFunc,
	reader io.ReadCloser,
	timeout time.Duration,
) *stallReader {
	return &stallReader{
		reader:   reader,
		ctx:      ctx,
		canceler: canceler.New(ctx, cancel, timeout),
	}
}

func (s *stallReader) Read(p []byte) (int, error) {
	n, err := s.reader.Read(p)
	if n > 0 {
		s.canceler.Update()
	}
	if err != nil && errors.Is(context.Cause(s.ctx), os.ErrDeadlineExceeded) {
		return n, E.Cause(os.ErrDeadlineExceeded, "transfer stalled")
	}
	return n, err
}

func (s *stallReader) Close() error {
	s.canceler.Close()
	return s.reader.Close()
}
