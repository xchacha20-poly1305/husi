package coresvc_test

import (
	"context"
	"encoding/binary"
	"io"
	"net"
	"net/netip"
	"testing"
	"time"

	"github.com/sagernet/sing-box/adapter"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"

	mDNS "github.com/miekg/dns"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/ipprobe"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

func TestDetectIPWithoutBox(t *testing.T) {
	hostCtx := testBaseContext(t)
	service.MustRegister[ipprobe.Resolver](hostCtx, probeSystemResolver{})
	_, socketPath := startTestHost(t, coresvc.HostOptions{Context: hostCtx})
	client := husiv1.NewCoreServiceClient(dialGRPC(t, socketPath))
	listener, err := net.Listen(N.NetworkTCP, "127.0.0.1:0")
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
		_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
		request := make([]byte, 20)
		_, err = io.ReadFull(conn, request)
		if err != nil {
			completed <- err
			return
		}
		response := make([]byte, 32)
		copy(response, request)
		copy(response[20:], []byte{0, 1, 0, 8, 0, 1, 0x04, 0xd2, 203, 0, 113, 7})
		binary.BigEndian.PutUint16(response[:2], 0x0101)
		binary.BigEndian.PutUint16(response[2:4], 12)
		_, err = conn.Write(response)
		completed <- err
	}()
	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()
	address := net.JoinHostPort("probe.invalid", F.ToString(listener.Addr().(*net.TCPAddr).Port))
	response, err := client.DetectIP(ctx, &husiv1.DetectIPRequest{Server: "turn:" + address + "?transport=tcp"})
	require.NoError(t, err)
	require.Equal(t, "203.0.113.7", response.GetIp())
	require.NoError(t, <-completed)
}

type probeSystemResolver struct{}

func (probeSystemResolver) Exchange(_ context.Context, message *mDNS.Msg, _ adapter.DNSQueryOptions) (*mDNS.Msg, error) {
	return new(mDNS.Msg).SetReply(message), nil
}

func (probeSystemResolver) Lookup(_ context.Context, domain string, _ adapter.DNSQueryOptions) ([]netip.Addr, error) {
	if domain != "probe.invalid" {
		return nil, E.New("unexpected domain: ", domain)
	}
	return []netip.Addr{netip.MustParseAddr("127.0.0.1")}, nil
}
