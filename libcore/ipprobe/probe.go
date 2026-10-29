package ipprobe

import (
	"context"
	"crypto/tls"
	"encoding/binary"
	"io"
	"net/netip"
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

	"github.com/xchacha20-poly1305/stunuri-go"
)

const (
	maxMessageSize  = 65532 // The biggest multiple of 4 under max uint16
	maxResponseSize = headerLen + maxMessageSize
)

const (
	defaultPort    = 3478
	defaultTLSPort = 5349
)

type stunServer struct {
	scheme    string
	address   M.Socksaddr
	transport string
	tls       bool
}

func parseStunServer(rawURI string) (stunServer, error) {
	rawURI = strings.TrimSpace(rawURI)
	parsed, err := stunuri.Parse(rawURI)
	if err != nil {
		return stunServer{}, err
	}
	server := stunServer{
		scheme:    parsed.Scheme,
		transport: strings.ToLower(parsed.Transport),
		tls:       parsed.Secure(),
	}
	switch server.transport {
	case "":
		if server.tls {
			server.transport = N.NetworkTCP
		} else {
			server.transport = N.NetworkUDP
		}
	case N.NetworkTCP, N.NetworkUDP:
	default:
		return stunServer{}, E.New("unsupported transport: ", parsed.Transport)
	}
	if server.tls && server.transport == N.NetworkUDP {
		return stunServer{}, E.New("unsupported UDP over TLS")
	}
	server.address = M.ParseSocksaddrHostPortStr(parsed.Host, parsed.Port)
	if !server.address.IsValid() {
		return stunServer{}, E.New("invalid server host")
	}
	if server.tls && server.address.IsIP() {
		// https://www.rfc-editor.org/info/rfc8489/#section-8.1
		return stunServer{}, E.New("reject TLS with IP address")
	}
	if server.address.Port == 0 {
		if server.tls {
			server.address.Port = defaultTLSPort
		} else {
			server.address.Port = defaultPort
		}
	}
	return server, nil
}

func Probe(ctx context.Context, dialer N.Dialer, resolver Resolver, value string) (netip.Addr, error) {
	server, err := parseStunServer(value)
	if err != nil {
		return netip.Addr{}, E.Cause(err, "parse stun server")
	}
	addresses, err := discoverServers(ctx, resolver, server)
	if err != nil {
		return netip.Addr{}, err
	}
	var failures []error
	for _, address := range addresses {
		result, err := probeServer(ctx, dialer, server, address)
		if err == nil {
			return result, nil
		}
		if ctx.Err() != nil {
			return netip.Addr{}, ctx.Err()
		}
		failures = append(failures, err)
	}
	return netip.Addr{}, E.Errors(failures...)
}

func probeServer(ctx context.Context, dialer N.Dialer, server stunServer, address M.Socksaddr) (netip.Addr, error) {
	var err error
	var timeout time.Duration
	if server.transport == N.NetworkTCP {
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
	if server.transport == N.NetworkUDP {
		err = probeUDP(ctx, dialer, address, request, response)
	} else {
		err = probeTCP(ctx, dialer, address, server.tls, server.address.Fqdn, request, response)
	}
	if err != nil {
		return netip.Addr{}, err
	}
	receivedID, mappedAddress, err := parseSTUNResponse(response.Bytes())
	if err != nil {
		return netip.Addr{}, E.Cause(err, "parse stun response")
	}
	if receivedID != sendID {
		return netip.Addr{}, E.New("STUN transaction ID mismatch")
	}
	return mappedAddress.Addr().Unmap(), nil
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

func probeTCP(ctx context.Context, dialer N.Dialer, serverAddr M.Socksaddr, isTLS bool, serverName string, request []byte, response *buf.Buffer) error {
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
			ServerName: serverName,
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
