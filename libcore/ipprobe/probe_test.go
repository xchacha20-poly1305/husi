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

	mDNS "github.com/miekg/dns"
	"github.com/stretchr/testify/require"
)

// This is not for testing RFC7064 or RFC7065, but is a test for our reject logic.
func TestParseStunServer(t *testing.T) {
	tests := []struct {
		name    string
		uri     string
		wantErr string
		want    stunServer
	}{
		{
			name: "stun default",
			uri:  "stun:example.org",
			want: stunServer{
				scheme:    "stun",
				address:   M.ParseSocksaddrHostPort("example.org", defaultPort),
				transport: N.NetworkUDP,
			},
		},
		{
			name: "trim space",
			uri:  "  stun:example.org  ",
			want: stunServer{
				scheme:    "stun",
				address:   M.ParseSocksaddrHostPort("example.org", defaultPort),
				transport: N.NetworkUDP,
			},
		},
		{
			name: "stuns default",
			uri:  "stuns:example.org",
			want: stunServer{
				scheme:    "stuns",
				address:   M.ParseSocksaddrHostPort("example.org", defaultTLSPort),
				transport: N.NetworkTCP,
				tls:       true,
			},
		},
		{
			name: "turn default",
			uri:  "turn:example.org",
			want: stunServer{
				scheme:    "turn",
				address:   M.ParseSocksaddrHostPort("example.org", defaultPort),
				transport: N.NetworkUDP,
			},
		},
		{
			name: "turns default",
			uri:  "turns:example.org",
			want: stunServer{
				scheme:    "turns",
				address:   M.ParseSocksaddrHostPort("example.org", defaultTLSPort),
				transport: N.NetworkTCP,
				tls:       true,
			},
		},
		{
			name: "explicit port",
			uri:  "stun:example.org:8000",
			want: stunServer{
				scheme:    "stun",
				address:   M.ParseSocksaddrHostPort("example.org", 8000),
				transport: N.NetworkUDP,
			},
		},
		{
			name: "explicit tls port",
			uri:  "turns:example.org:443",
			want: stunServer{
				scheme:    "turns",
				address:   M.ParseSocksaddrHostPort("example.org", 443),
				transport: N.NetworkTCP,
				tls:       true,
			},
		},
		{
			name: "zero port",
			uri:  "stun:example.org:0",
			want: stunServer{
				scheme:    "stun",
				address:   M.ParseSocksaddrHostPort("example.org", defaultPort),
				transport: N.NetworkUDP,
			},
		},
		{
			name: "empty port",
			uri:  "stuns:example.org:",
			want: stunServer{
				scheme:    "stuns",
				address:   M.ParseSocksaddrHostPort("example.org", defaultTLSPort),
				transport: N.NetworkTCP,
				tls:       true,
			},
		},
		{
			name: "turn tcp",
			uri:  "turn:example.org?transport=tcp",
			want: stunServer{
				scheme:    "turn",
				address:   M.ParseSocksaddrHostPort("example.org", defaultPort),
				transport: N.NetworkTCP,
			},
		},
		{
			name: "turn udp",
			uri:  "turn:example.org:8000?transport=udp",
			want: stunServer{
				scheme:    "turn",
				address:   M.ParseSocksaddrHostPort("example.org", 8000),
				transport: N.NetworkUDP,
			},
		},
		{
			name: "transport case",
			uri:  "turns:example.org?transport=TCP",
			want: stunServer{
				scheme:    "turns",
				address:   M.ParseSocksaddrHostPort("example.org", defaultTLSPort),
				transport: N.NetworkTCP,
				tls:       true,
			},
		},
		{
			name: "ipv4",
			uri:  "stun:192.0.2.1:3478",
			want: stunServer{
				scheme:    "stun",
				address:   M.ParseSocksaddrHostPort("192.0.2.1", 3478),
				transport: N.NetworkUDP,
			},
		},
		{
			name: "ipv6",
			uri:  "stun:[2001:db8::1]",
			want: stunServer{
				scheme:    "stun",
				address:   M.ParseSocksaddrHostPort("[2001:db8::1]", defaultPort),
				transport: N.NetworkUDP,
			},
		},
		{
			name:    "unsupported transport",
			uri:     "turn:example.org?transport=sctp",
			wantErr: "unsupported transport",
		},
		{
			name:    "udp over tls",
			uri:     "turns:example.org?transport=udp",
			wantErr: "unsupported UDP over TLS",
		},
		{
			name:    "udp over tls before ip",
			uri:     "  turns:192.0.2.1?transport=UDP  ",
			wantErr: "unsupported UDP over TLS",
		},
		{
			name:    "tls ipv4",
			uri:     "stuns:192.0.2.1",
			wantErr: "reject TLS with IP address",
		},
		{
			name:    "tls ipv6",
			uri:     "stuns:[2001:db8::1]:5349",
			wantErr: "reject TLS with IP address",
		},
		{
			name:    "tls ipv4-mapped",
			uri:     "turns:[::ffff:192.0.2.1]?transport=tcp",
			wantErr: "reject TLS with IP address",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseStunServer(tt.uri)
			if tt.wantErr != "" {
				require.ErrorContains(t, err, tt.wantErr)
				return
			}
			require.NoError(t, err)
			require.Equal(t, tt.want, got)
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
	for _, server := range []struct {
		scheme, query string
	}{
		{"stun", ""},
		{"turn", ""},
		{"turn", "?transport=udp"},
	} {
		for _, ip := range []string{"203.0.113.7", "2001:db8::7"} {
			t.Run(server.scheme+server.query+"/"+ip, func(t *testing.T) {
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
				result, err := Probe(t.Context(), testDialer{}, nil, server.scheme+":"+listener.LocalAddr().String()+server.query)
				require.NoError(t, err)
				require.Equal(t, ip, result.String())
				require.NoError(t, <-completed)
				require.NoError(t, listener.SetReadDeadline(time.Now().Add(20*time.Millisecond)))
				_, _, err = listener.ReadFrom(make([]byte, 1024))
				require.Error(t, err, "probe must send only one binding request")
			})
		}
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
		query  string
	}{
		{"TCP", false, "?transport=tcp"},
		{"TLS", true, "?transport=tcp"},
		{"TLS default transport", true, ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			testProbeTCP(t, tt.secure, tt.query)
		})
	}
}

func testProbeTCP(t *testing.T, secure bool, query string) {
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
		scheme = "turns"
	} else {
		scheme = "turn"
	}
	serverURI := scheme + ":" + listener.Addr().String() + query
	ctx := service.ContextWith[adapter.CertificateStore](t.Context(), testCertificateStore{pool: pool})
	resolver := testResolver{
		exchange: func(context.Context, *mDNS.Msg) (*mDNS.Msg, error) {
			return &mDNS.Msg{Answer: []mDNS.RR{&mDNS.SRV{Target: "srv.example.net.", Port: uint16(listener.Addr().(*net.TCPAddr).Port)}}}, nil
		},
		lookup: func(_ context.Context, host string) ([]netip.Addr, error) {
			require.Equal(t, "srv.example.net", host)
			return []netip.Addr{netip.MustParseAddr("127.0.0.2"), netip.MustParseAddr("127.0.0.1")}, nil
		},
	}
	if secure {
		serverURI = scheme + ":example.com" + query
	}
	result, err := Probe(ctx, testDialer{}, resolver, serverURI)
	require.NoError(t, err)
	require.Equal(t, "2001:db8::8", result.String())
	require.NoError(t, <-completed)
}

func TestProbeCancellation(t *testing.T) {
	listener, err := net.ListenPacket(N.NetworkUDP, "127.0.0.1:0")
	require.NoError(t, err)
	defer listener.Close()
	serverURI := "stun:" + listener.LocalAddr().String()
	ctx, cancel := context.WithTimeout(t.Context(), 30*time.Millisecond)
	defer cancel()
	started := time.Now()
	_, err = Probe(ctx, testDialer{}, nil, serverURI)
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
