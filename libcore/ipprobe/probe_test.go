package ipprobe

import (
	"bytes"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/binary"
	"io"
	"net"
	"net/http/httptest"
	"net/netip"
	"net/url"
	"strconv"
	"testing"
	"testing/iotest"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/common/buf"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"

	"github.com/stretchr/testify/require"
)

func TestProbeServerURL(t *testing.T) {
	for _, test := range []struct {
		input, address string
		transport      string
	}{
		{"example.com", "example.com:3478", "udp"},
		{"stun://example.com:1234", "example.com:1234", "udp"},
		{"stuns://example.com", "example.com:5379", "tcp"},
		{" stuns://[2001:db8::1]:443 ", "[2001:db8::1]:443", "tcp"},
		{"stun://example.com?transport=udp", "example.com:3478", "udp"},
		{"stun://example.com?transport=tcp", "example.com:3478", "tcp"},
		{"example.com:1234?transport=TCP", "example.com:1234", "tcp"},
		{"stuns://example.com?transport=tcp", "example.com:5379", "tcp"},
	} {
		t.Run(test.input, func(t *testing.T) {
			dialer := &recordingDialer{err: E.New("stop before connecting")}
			_, err := Probe(t.Context(), dialer, test.input)
			require.ErrorIs(t, err, dialer.err)
			require.Equal(t, test.address, dialer.address.String())
			require.Equal(t, test.transport, dialer.transport)
		})
	}

	for _, input := range []string{
		"",
		"https://example.com",
		"stun://example.com?transport=quic",
		"stun://example.com?transport=%zz",
	} {
		t.Run(input, func(t *testing.T) {
			_, err := Probe(t.Context(), nil, input)
			require.Error(t, err)
		})
	}
}

type recordingDialer struct {
	address   M.Socksaddr
	transport string
	err       error
}

func (d *recordingDialer) DialContext(_ context.Context, network string, address M.Socksaddr) (net.Conn, error) {
	d.address, d.transport = address, network
	return nil, d.err
}

func (d *recordingDialer) ListenPacket(_ context.Context, address M.Socksaddr) (net.PacketConn, error) {
	d.address, d.transport = address, N.NetworkUDP
	return nil, d.err
}

type testDialer struct{}

func (testDialer) DialContext(ctx context.Context, network string, address M.Socksaddr) (net.Conn, error) {
	if network != N.NetworkTCP {
		return nil, E.New("test dialer only supports TCP through DialContext")
	}
	return (&net.Dialer{}).DialContext(ctx, network, address.String())
}

func (testDialer) ListenPacket(ctx context.Context, address M.Socksaddr) (net.PacketConn, error) {
	conn, err := net.ListenPacket(N.NetworkUDP, "127.0.0.1:0")
	if err != nil {
		return nil, err
	}
	return testPacketConn{conn}, nil
}

type testPacketConn struct{ net.PacketConn }

func (c testPacketConn) WriteTo(b []byte, address net.Addr) (int, error) {
	resolved, err := net.ResolveUDPAddr(N.NetworkUDP, address.String())
	if err != nil {
		return 0, err
	}
	return c.PacketConn.WriteTo(b, resolved)
}

func TestProbeUDP(t *testing.T) {
	for _, ip := range []string{"203.0.113.7", "2001:db8::7"} {
		t.Run(ip, func(t *testing.T) {
			listener, err := net.ListenPacket(N.NetworkUDP, "127.0.0.1:0")
			require.NoError(t, err)
			defer listener.Close()
			completed := make(chan error, 1)
			go func() {
				packet := make([]byte, 1024)
				count, source, err := listener.ReadFrom(packet)
				if err == nil {
					var transaction stunTxID
					transaction, err = parseBindingRequest(packet[:count])
					if err == nil {
						_, err = listener.WriteTo(stunResponse(transaction, netip.AddrPortFrom(netip.MustParseAddr(ip), 1234)), source)
					}
				}
				completed <- err
			}()
			result, err := Probe(t.Context(), testDialer{}, listener.LocalAddr().String())
			require.NoError(t, err)
			require.Equal(t, ip, result.String())
			require.NoError(t, <-completed)
			require.NoError(t, listener.SetReadDeadline(time.Now().Add(20*time.Millisecond)))
			_, _, err = listener.ReadFrom(make([]byte, 1024))
			require.Error(t, err, "probe must send only one binding request")
		})
	}
}

type testCertificateStore struct {
	adapter.CertificateStore
	pool *x509.CertPool
}

func (s testCertificateStore) Pool() *x509.CertPool { return s.pool }

func TestProbeTCP(t *testing.T) {
	tests := []struct {
		name   string
		secure bool
	}{
		{"TCP", false},
		{"TLS", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			testProbeTCP(t, tt.secure)
		})
	}
}

func testProbeTCP(t *testing.T, secure bool) {
	t.Helper()
	certificateServer := httptest.NewTLSServer(nil)
	certificate := certificateServer.TLS.Certificates[0]
	pool := x509.NewCertPool()
	pool.AddCert(certificateServer.Certificate())
	certificateServer.Close()
	listener, err := net.Listen(N.NetworkTCP, "127.0.0.1:0")
	if secure && err == nil {
		listener = tls.NewListener(listener, &tls.Config{Certificates: []tls.Certificate{certificate}, MinVersion: tls.VersionTLS12})
	}
	require.NoError(t, err)
	defer listener.Close()
	completed := make(chan error, 1)
	go func() {
		conn, err := listener.Accept()
		if err != nil {
			completed <- err
			return
		}
		defer conn.Close()
		header := make([]byte, headerLen)
		_, err = io.ReadFull(conn, header)
		if err != nil {
			completed <- err
			return
		}
		body := make([]byte, int(binary.BigEndian.Uint16(header[2:4])))
		_, err = io.ReadFull(conn, body)
		if err != nil {
			completed <- err
			return
		}
		request := make([]byte, headerLen+len(body))
		copy(request, header)
		copy(request[headerLen:], body)
		transaction, err := parseBindingRequest(request)
		if err == nil {
			response := stunResponse(transaction, netip.MustParseAddrPort("[2001:db8::8]:1234"))
			_, err = conn.Write(response[:10])
			if err == nil {
				_, err = conn.Write(response[10:])
			}
		}
		completed <- err
	}()
	var scheme string
	if secure {
		scheme = "stuns"
	} else {
		scheme = "stun"
	}
	serverURL := &url.URL{
		Scheme: scheme,
		Host:   listener.Addr().String(),
		RawQuery: url.Values{
			"transport": {"tcp"},
		}.Encode(),
	}
	ctx := service.ContextWith[adapter.CertificateStore](t.Context(), testCertificateStore{pool: pool})
	result, err := Probe(ctx, testDialer{}, serverURL.String())
	require.NoError(t, err)
	require.Equal(t, "2001:db8::8", result.String())
	require.NoError(t, <-completed)
}

func TestProbeCancellation(t *testing.T) {
	listener, err := net.ListenPacket(N.NetworkUDP, "127.0.0.1:0")
	require.NoError(t, err)
	defer listener.Close()
	serverURL := listener.LocalAddr().String()
	ctx, cancel := context.WithTimeout(t.Context(), 30*time.Millisecond)
	defer cancel()
	started := time.Now()
	_, err = Probe(ctx, testDialer{}, serverURL)
	require.Error(t, err)
	require.Less(t, time.Since(started), time.Second)
}

func TestReadTCPResponse(t *testing.T) {
	response := stunResponse(newSTUNTxID(), netip.MustParseAddrPort("[2001:db8::8]:1234"))
	maximumResponse := make([]byte, maxResponseSize)
	binary.BigEndian.PutUint16(maximumResponse[2:4], maxMessageSize)
	for _, test := range []struct {
		name string
		data []byte
		err  error
	}{
		{"fragmented", response, nil},
		{"truncated header", response[:10], io.ErrUnexpectedEOF},
		{"truncated body", response[:len(response)-1], io.ErrUnexpectedEOF},
		{"maximum body", maximumResponse, nil},
	} {
		t.Run(test.name, func(t *testing.T) {
			buffer := buf.NewSize(maxResponseSize)
			defer buffer.Release()
			err := readTCPResponse(iotest.OneByteReader(bytes.NewReader(test.data)), buffer)
			if test.err != nil {
				require.ErrorIs(t, err, test.err)
			} else {
				require.NoError(t, err)
				require.Equal(t, test.data, buffer.Bytes())
			}
		})
	}
	for _, size := range []uint16{1, 65535} {
		t.Run("invalid length "+strconv.Itoa(int(size)), func(t *testing.T) {
			header := make([]byte, headerLen)
			binary.BigEndian.PutUint16(header[2:4], size)
			buffer := buf.NewSize(maxResponseSize)
			defer buffer.Release()
			require.ErrorContains(t, readTCPResponse(bytes.NewReader(header), buffer), "invalid STUN message length")
		})
	}
}
