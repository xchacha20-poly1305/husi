package plugindns

import (
	"context"

	"libcore/plugin/pluginoption"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/dialer"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing-box/dns/transport"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	mDNS "github.com/miekg/dns"
)

var _ adapter.DNSTransport = (*TCPTransport)(nil)

func RegisterTCP(registry *dns.TransportRegistry) {
	dns.RegisterTransport[pluginoption.RemoteTCPDNSServerOptions](registry, C.DNSTypeTCP, NewTCP)
}

type TCPTransport struct {
	dns.TransportAdapter
	logger     logger.ContextLogger
	dialer     N.Dialer
	serverAddr M.Socksaddr

	connections *transport.ConnPool[*reusableDNSConn]
	pipeline    bool
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
	var connPool *transport.ConnPool[*reusableDNSConn]
	usePool := options.Reuse || options.Pipeline
	if usePool {
		mode := transport.ConnPoolOrdered
		if options.Pipeline {
			mode = transport.ConnPoolSingle
		}
		connPool = transport.NewConnPool(transport.ConnPoolOptions[*reusableDNSConn]{
			Mode: mode,
			IsAlive: func(conn *reusableDNSConn) bool {
				return conn != nil
			},
			Close: func(conn *reusableDNSConn, err error) {
				_ = conn.Close()
			},
		})
	}
	return &TCPTransport{
		TransportAdapter: dns.NewTransportAdapterWithRemoteOptions(C.DNSTypeTCP, tag, options.RemoteDNSServerOptions),
		logger:           logger,
		dialer:           transportDialer,
		serverAddr:       serverAddr,
		connections:      connPool,
		pipeline:         options.Pipeline,
	}, nil
}

func (t *TCPTransport) Start(stage adapter.StartStage) error {
	if stage != adapter.StartStateStart {
		return nil
	}
	return dialer.InitializeDetour(t.dialer)
}

func (t *TCPTransport) Close() error {
	return common.Close(common.PtrOrNil(t.connections))
}

func (t *TCPTransport) Reset() {
	if t.connections != nil {
		t.connections.Reset()
	}
}

func (t *TCPTransport) Exchange(ctx context.Context, message *mDNS.Msg) (*mDNS.Msg, error) {
	if t.connections == nil {
		conn, err := t.dialer.DialContext(ctx, N.NetworkTCP, t.serverAddr)
		if err != nil {
			return nil, E.Cause(err, "dial TCP connection")
		}
		defer common.Close(conn)
		return newReusableDNSConn(conn, t.logger).exchange(ctx, message)
	}

	if t.pipeline {
		conn, connCtx, _, err := t.connections.AcquireShared(ctx, func(ctx context.Context) (*reusableDNSConn, error) {
			rawConn, err := t.dialer.DialContext(ctx, N.NetworkTCP, t.serverAddr)
			if err != nil {
				return nil, E.Cause(err, "dial TCP connection")
			}
			return newReusableDNSConn(rawConn, t.logger), nil
		})
		if err != nil {
			return nil, err
		}
		defer t.connections.Release(conn, true)
		return conn.exchangePipeline(ctx, connCtx, message, func(cause error) {
			t.connections.Invalidate(conn, cause)
		})
	}

	var lastErr error
	for attempt := 0; attempt < 2; attempt++ {
		conn, created, err := t.connections.Acquire(ctx, func(ctx context.Context) (*reusableDNSConn, error) {
			rawConn, err := t.dialer.DialContext(ctx, N.NetworkTCP, t.serverAddr)
			if err != nil {
				return nil, E.Cause(err, "dial")
			}
			return newReusableDNSConn(rawConn, t.logger), nil
		})
		if err != nil {
			return nil, err
		}
		response, err := conn.exchange(ctx, message)
		if err == nil {
			t.connections.Release(conn, true)
			return response, nil
		}
		lastErr = err
		t.logger.DebugContext(ctx, "discarded pooled connection: ", err)
		t.connections.Release(conn, false)
		if created {
			return nil, err
		}
	}
	return nil, lastErr
}
