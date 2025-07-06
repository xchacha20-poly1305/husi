package plugindns

import (
	"context"
	"net"
	"sync"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/dialer"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing-box/dns/transport"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/x/list"

	"libcore/plugin/pluginoption"

	mDNS "github.com/miekg/dns"
)

var _ adapter.DNSTransport = (*TCPTransport)(nil)

func RegisterTCP(registry *dns.TransportRegistry) {
	dns.RegisterTransport[pluginoption.RemoteTCPDNSServerOptions](registry, C.DNSTypeTCP, NewTCP)
}

type TCPTransport struct {
	dns.TransportAdapter
	dialer      N.Dialer
	serverAddr  M.Socksaddr
	access      *sync.Mutex
	connections *list.List[*reusableConn]
}

type reusableConn struct {
	net.Conn
	queryID uint16
}

func NewTCP(ctx context.Context, logger log.ContextLogger, tag string, options pluginoption.RemoteTCPDNSServerOptions) (adapter.DNSTransport, error) {
	transportDialer, err := dns.NewRemoteDialer(ctx, options.RemoteDNSServerOptions)
	if err != nil {
		return nil, err
	}
	serverAddr := options.DNSServerAddressOptions.Build()
	if serverAddr.Port == 0 {
		serverAddr.Port = 53
	}
	if !serverAddr.IsValid() {
		return nil, E.New("invalid server address: ", serverAddr)
	}
	transport := &TCPTransport{
		TransportAdapter: dns.NewTransportAdapterWithRemoteOptions(C.DNSTypeTCP, tag, options.RemoteDNSServerOptions),
		dialer:           transportDialer,
		serverAddr:       serverAddr,
	}
	if options.Reuse {
		transport.access = new(sync.Mutex)
		transport.connections = new(list.List[*reusableConn])
	}
	return transport, nil
}

func (t *TCPTransport) Start(stage adapter.StartStage) error {
	if stage != adapter.StartStateStart {
		return nil
	}
	return dialer.InitializeDetour(t.dialer)
}

func (t *TCPTransport) Close() error {
	if t.access == nil {
		return nil
	}
	t.access.Lock()
	defer t.access.Unlock()
	for connection := t.connections.Front(); connection != nil; connection = connection.Next() {
		_ = connection.Value.Close()
	}
	t.connections.Init()
	return nil
}

func (t *TCPTransport) Exchange(ctx context.Context, message *mDNS.Msg) (*mDNS.Msg, error) {
	if t.access != nil {
		t.access.Lock()
		conn := t.connections.PopFront()
		t.access.Unlock()
		if conn != nil {
			response, err := t.exchange(message, conn)
			if err == nil {
				return response, nil
			}
		}
	}
	conn, err := t.dialer.DialContext(ctx, N.NetworkTCP, t.serverAddr)
	if err != nil {
		return nil, err
	}
	return t.exchange(message, &reusableConn{Conn: conn})
}

func (t *TCPTransport) exchange(message *mDNS.Msg, conn *reusableConn) (*mDNS.Msg, error) {
	defer func() {
		conn.queryID++
	}()
	err := transport.WriteMessage(conn, conn.queryID, message)
	if err != nil {
		_ = conn.Close()
		return nil, E.Cause(err, "write request")
	}
	response, err := transport.ReadMessage(conn)
	if err != nil {
		_ = conn.Close()
		return nil, E.Cause(err, "read response")
	}
	if t.access != nil {
		t.access.Lock()
		t.connections.PushBack(conn)
		t.access.Unlock()
	}
	return response, nil
}
