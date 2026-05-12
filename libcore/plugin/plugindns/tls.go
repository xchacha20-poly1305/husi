package plugindns

import (
	"context"

	"libcore/plugin/pluginoption"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/dialer"
	"github.com/sagernet/sing-box/common/tls"
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

var _ adapter.DNSTransport = (*TLSTransport)(nil)

const tlsDNSMaxInflight = 8

func RegisterTLS(registry *dns.TransportRegistry) {
	dns.RegisterTransport[pluginoption.RemoteTLSDNSServerOptions](registry, C.DNSTypeTLS, NewTLS)
}

type TLSTransport struct {
	dns.TransportAdapter
	logger logger.ContextLogger

	dialer      tls.Dialer
	serverAddr  M.Socksaddr
	tlsConfig   tls.Config
	connections *transport.ConnPool[*reusableDNSConn]
	pipeline    bool
}

func NewTLS(ctx context.Context, logger log.ContextLogger, tag string, options pluginoption.RemoteTLSDNSServerOptions) (adapter.DNSTransport, error) {
	transportDialer, err := dns.NewRemoteDialer(ctx, options.RemoteDNSServerOptions)
	if err != nil {
		return nil, err
	}
	tlsOptions := common.PtrValueOrDefault(options.TLS)
	tlsOptions.Enabled = true
	tlsConfig, err := tls.NewClient(ctx, logger, options.Server, tlsOptions)
	if err != nil {
		return nil, err
	}
	serverAddr := options.DNSServerAddressOptions.Build()
	if serverAddr.Port == 0 {
		serverAddr.Port = 853
	}
	if !serverAddr.IsValid() {
		return nil, E.New("invalid server address: ", serverAddr)
	}
	return newTLSRaw(logger, dns.NewTransportAdapterWithRemoteOptions(C.DNSTypeTLS, tag, options.RemoteDNSServerOptions), transportDialer, serverAddr, tlsConfig, options.Pipeline), nil
}

func newTLSRaw(logger logger.ContextLogger, adapter dns.TransportAdapter, dialer N.Dialer, serverAddr M.Socksaddr, tlsConfig tls.Config, pipeline bool) *TLSTransport {
	return &TLSTransport{
		TransportAdapter: adapter,
		logger:           logger,
		dialer:           tls.NewDialer(dialer, tlsConfig),
		serverAddr:       serverAddr,
		tlsConfig:        tlsConfig,
		connections:      newTLSConnPool(pipeline),
		pipeline:         pipeline,
	}
}

func newTLSConnPool(pipeline bool) *transport.ConnPool[*reusableDNSConn] {
	mode := transport.ConnPoolOrdered
	maxInflight := tlsDNSMaxInflight
	if pipeline {
		mode = transport.ConnPoolSingle
		maxInflight = 0
	}
	return transport.NewConnPool(transport.ConnPoolOptions[*reusableDNSConn]{
		Mode:        mode,
		MaxInflight: maxInflight,
		IsAlive: func(conn *reusableDNSConn) bool {
			return conn != nil
		},
		Close: func(conn *reusableDNSConn, _ error) {
			_ = conn.Close()
		},
	})
}

func (t *TLSTransport) Start(stage adapter.StartStage) error {
	if stage != adapter.StartStateStart {
		return nil
	}
	return dialer.InitializeDetour(t.dialer)
}

func (t *TLSTransport) Close() error {
	return t.connections.Close()
}

func (t *TLSTransport) Reset() {
	t.connections.Reset()
}

func (t *TLSTransport) Exchange(ctx context.Context, message *mDNS.Msg) (*mDNS.Msg, error) {
	if t.pipeline {
		conn, connCtx, _, err := t.connections.AcquireShared(ctx, func(ctx context.Context) (*reusableDNSConn, error) {
			tlsConn, err := t.dialer.DialTLSContext(ctx, t.serverAddr)
			if err != nil {
				return nil, E.Cause(err, "dial TLS connection")
			}
			return newReusableDNSConn(tlsConn, t.logger, tlsConn.NetConn()), nil
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
			tlsConn, err := t.dialer.DialTLSContext(ctx, t.serverAddr)
			if err != nil {
				return nil, E.Cause(err, "dial TLS connection")
			}
			return newReusableDNSConn(tlsConn, t.logger, tlsConn.NetConn()), nil
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
