package ipprobe

import (
	"context"
	"crypto/tls"
	"encoding/binary"
	"io"
	"net/netip"
	"net/url"
	"strings"
	"time"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing/common/buf"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/ntp"
)

const (
	maxMessageSize  = 65532 // The biggest multiple of 4 under max uint16
	maxResponseSize = headerLen + maxMessageSize
)

const (
	defaultPort    = 3478
	defaultTLSPort = 5379
)

func parseStunServer(rawURL string) (serverAddr M.Socksaddr, transport string, isTLS bool, err error) {
	rawURL = strings.TrimSpace(rawURL)
	if !strings.Contains(rawURL, "://") {
		rawURL = "stun://" + rawURL
	}
	parsed, err := url.Parse(rawURL)
	if err != nil {
		err = E.Cause(err, "invalid server")
		return
	}
	if parsed.Scheme != "stun" && parsed.Scheme != "stuns" {
		err = E.New("invalid scheme: ", parsed.Scheme)
		return
	}
	query, err := url.ParseQuery(parsed.RawQuery)
	if err != nil {
		err = E.Cause(err, "parse query")
		return
	}
	transport = strings.ToLower(query.Get("transport"))
	isTLS = parsed.Scheme == "stuns"
	switch transport {
	case "":
		if isTLS {
			transport = N.NetworkTCP
		} else {
			transport = N.NetworkUDP
		}
	case N.NetworkTCP:
	case N.NetworkUDP:
		if isTLS {
			err = E.New("unsupported UDP over TLS")
			return
		}
	default:
		err = E.New("unsupported transport")
		return
	}
	serverAddr = M.ParseSocksaddr(parsed.Host)
	if !serverAddr.IsValid() {
		err = E.New("invalid host")
		return
	}
	if serverAddr.Port == 0 {
		if isTLS {
			serverAddr.Port = defaultTLSPort
		} else {
			serverAddr.Port = defaultPort
		}
	}
	return
}

func Probe(ctx context.Context, dialer N.Dialer, value string) (netip.Addr, error) {
	serverAddr, transport, isTLS, err := parseStunServer(value)
	if err != nil {
		return netip.Addr{}, E.Cause(err, "parse stun server")
	}
	var timeout time.Duration
	if transport == N.NetworkTCP {
		timeout = C.TCPTimeout
	} else {
		timeout = C.UDPTimeout
	}

	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	sendID := newSTUNTxID()
	request := stunRequest(sendID)
	response := buf.NewSize(maxResponseSize)
	defer response.Release()
	if transport == N.NetworkUDP {
		err = probeUDP(ctx, dialer, serverAddr, request, response)
	} else {
		err = probeTCP(ctx, dialer, serverAddr, isTLS, request, response)
	}
	if err != nil {
		return netip.Addr{}, err
	}
	receivedID, address, err := parseSTUNResponse(response.Bytes())
	if err != nil {
		return netip.Addr{}, E.Cause(err, "parse stun response")
	}
	if receivedID != sendID {
		return netip.Addr{}, E.New("STUN transaction ID mismatch")
	}
	return address.Addr().Unmap(), nil
}

func probeUDP(ctx context.Context, dialer N.Dialer, serverAddr M.Socksaddr, request []byte, response *buf.Buffer) error {
	conn, err := dialer.ListenPacket(ctx, serverAddr)
	if err != nil {
		return err
	}
	defer conn.Close()
	stop := context.AfterFunc(ctx, func() { _ = conn.Close() })
	defer stop()
	// A sing packet conn can read data to *buf.Buffer easily.
	packetConn := bufio.NewPacketConn(conn)
	err = packetConn.WritePacket(buf.As(request), serverAddr)
	if err != nil {
		return E.Cause(err, "write stun request")
	}
	_, err = packetConn.ReadPacket(response)
	if err != nil {
		return E.Cause(err, "read stun response")
	}
	if response.Len() < headerLen {
		return E.New("response too short")
	}
	return nil
}

func probeTCP(ctx context.Context, dialer N.Dialer, serverAddr M.Socksaddr, isTLS bool, request []byte, response *buf.Buffer) error {
	conn, err := dialer.DialContext(ctx, N.NetworkTCP, serverAddr)
	if err != nil {
		return err
	}
	defer conn.Close()
	underlyingConn := conn
	stop := context.AfterFunc(ctx, func() { _ = underlyingConn.Close() })
	defer stop()
	if isTLS {
		tlsConn := tls.Client(conn, &tls.Config{
			ServerName: serverAddr.AddrString(),
			RootCAs:    adapter.RootPoolFromContext(ctx),
			Time:       ntp.TimeFuncFromContext(ctx),
			MinVersion: tls.VersionTLS12,
		})
		err := tlsConn.HandshakeContext(ctx)
		if err != nil {
			return err
		}
		defer tlsConn.Close()
		conn = tlsConn
	}

	_, err = conn.Write(request)
	if err != nil {
		return E.Cause(err, "write stun request")
	}
	err = readTCPResponse(conn, response)
	if err != nil {
		return E.Cause(err, "read stun response")
	}
	return nil
}

func readTCPResponse(reader io.Reader, response *buf.Buffer) error {
	_, err := response.ReadFullFrom(reader, headerLen)
	if err != nil {
		return err
	}
	messageSize := int(binary.BigEndian.Uint16(response.Bytes()[2:4]))
	if messageSize > maxMessageSize || messageSize%4 != 0 {
		return E.New("invalid STUN message length: ", messageSize)
	}
	_, err = response.ReadFullFrom(reader, messageSize)
	return err
}
