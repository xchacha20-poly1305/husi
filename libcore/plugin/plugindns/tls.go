package plugindns

import (
	"context"
	"sync"
	"sync/atomic"
	"time"

	"libcore/expiringpool"
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

func RegisterTLS(registry *dns.TransportRegistry) {
	dns.RegisterTransport[pluginoption.RemoteTLSDNSServerOptions](registry, C.DNSTypeTLS, NewTLS)
}

type TLSTransport struct {
	*transport.BaseTransport
	logger                logger.ContextLogger
	dialer                tls.Dialer
	serverAddr            M.Socksaddr
	tlsConfig             tls.Config
	connections           *expiringpool.ExpiringPool[*reuseableDNSConn]
	enablePipeline        bool
	idleTimeout           time.Duration
	disableKeepAlive      bool
	maxQueries            int
	activeConns           []*reuseableDNSConn
	activeAccess          sync.Mutex
	pipelineDetected      int32
	consecutiveOutOfOrder int32
	outOfOrderCount       int32
	totalResponses        int32
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
	var poolIdleTimeout time.Duration
	if options.DisableTCPKeepAlive {
		poolIdleTimeout = 2 * time.Minute
	} else {
		var keepAliveIdle, keepAliveInterval time.Duration
		if options.TCPKeepAlive != 0 {
			keepAliveIdle = time.Duration(options.TCPKeepAlive)
		} else {
			keepAliveIdle = C.TCPKeepAliveInitial
		}
		if options.TCPKeepAliveInterval != 0 {
			keepAliveInterval = time.Duration(options.TCPKeepAliveInterval)
		} else {
			keepAliveInterval = C.TCPKeepAliveInterval
		}
		poolIdleTimeout = keepAliveIdle + keepAliveInterval
	}
	maxQueries := options.MaxQueries
	if maxQueries <= 0 {
		maxQueries = 0
	}
	if !options.Pipeline && maxQueries > 0 {
		maxQueries = 0
	}
	return NewTLSRaw(ctx, logger, dns.NewTransportAdapterWithRemoteOptions(C.DNSTypeTLS, tag, options.RemoteDNSServerOptions), transportDialer, serverAddr, tlsConfig, options.Pipeline, poolIdleTimeout, options.DisableTCPKeepAlive, maxQueries), nil
}

func NewTLSRaw(ctx context.Context, logger logger.ContextLogger, adapter dns.TransportAdapter, dialer N.Dialer, serverAddr M.Socksaddr, tlsConfig tls.Config, enablePipeline bool, idleTimeout time.Duration, disableKeepAlive bool, maxQueries int) *TLSTransport {
	transport := &TLSTransport{
		BaseTransport:    transport.NewBaseTransport(adapter, logger),
		logger:           logger,
		dialer:           tls.NewDialer(dialer, tlsConfig),
		serverAddr:       serverAddr,
		tlsConfig:        tlsConfig,
		enablePipeline:   enablePipeline,
		idleTimeout:      idleTimeout,
		disableKeepAlive: disableKeepAlive,
		maxQueries:       maxQueries,
	}
	transport.connections = expiringpool.New(ctx, idleTimeout, func(conn *reuseableDNSConn) {
		conn.Close()
	})
	return transport
}

func (t *TLSTransport) Start(stage adapter.StartStage) error {
	if stage != adapter.StartStateStart {
		return nil
	}
	err := t.SetStarted()
	if err != nil {
		return err
	}
	if t.connections != nil {
		t.connections.Start()
	}
	return dialer.InitializeDetour(t.dialer)
}

func (t *TLSTransport) Close() error {
	if t.connections != nil {
		t.connections.Close()
	}
	return t.BaseTransport.Close()
}

func (t *TLSTransport) Reset() {
}

func (t *TLSTransport) getValidConnFromPool() *reuseableDNSConn {
	conn := t.connections.Get()
	if conn == nil {
		return nil
	}

	select {
	case <-conn.done:
		return nil
	default:
		return conn
	}
}

func (t *TLSTransport) findAndReserveActiveConn() *reuseableDNSConn {
	t.activeAccess.Lock()
	defer t.activeAccess.Unlock()

	var bestConn *reuseableDNSConn
	var minQueries int32 = -1
	var closedCount int

	for _, conn := range t.activeConns {
		select {
		case <-conn.done:
			closedCount++
		default:
			if conn.maxQueries <= 0 || atomic.LoadInt32(&conn.activeQueries) < int32(conn.maxQueries) {
				current := atomic.LoadInt32(&conn.activeQueries)
				if minQueries == -1 || current < minQueries {
					minQueries = current
					bestConn = conn
				}
			}
		}
	}

	if bestConn != nil && minQueries == 0 && closedCount == 0 {
		atomic.AddInt32(&bestConn.activeQueries, 1)
		return bestConn
	}

	if closedCount > 0 {
		validConns := make([]*reuseableDNSConn, 0, len(t.activeConns)-closedCount)
		for _, conn := range t.activeConns {
			select {
			case <-conn.done:
			default:
				validConns = append(validConns, conn)
			}
		}
		t.activeConns = validConns
	}

	if bestConn != nil {
		atomic.AddInt32(&bestConn.activeQueries, 1)
	}

	return bestConn
}

func (t *TLSTransport) addActiveConn(conn *reuseableDNSConn) {
	t.activeAccess.Lock()
	defer t.activeAccess.Unlock()

	for _, c := range t.activeConns {
		if c == conn {
			return
		}
	}

	t.activeConns = append(t.activeConns, conn)
}

func (t *TLSTransport) removeActiveConn(conn *reuseableDNSConn) {
	t.activeAccess.Lock()
	defer t.activeAccess.Unlock()

	for i, c := range t.activeConns {
		if c == conn {
			last := len(t.activeConns) - 1
			t.activeConns[i] = t.activeConns[last]
			t.activeConns = t.activeConns[:last]
			return
		}
	}
}

func (t *TLSTransport) markPipelineDetected() bool {
	return atomic.CompareAndSwapInt32(&t.pipelineDetected, 0, 1)
}

func (t *TLSTransport) isPipelineDetected() bool {
	return atomic.LoadInt32(&t.pipelineDetected) != 0
}

func (t *TLSTransport) getDetectionCounters() (*int32, *int32, *int32) {
	return &t.consecutiveOutOfOrder, &t.outOfOrderCount, &t.totalResponses
}

func (t *TLSTransport) Exchange(ctx context.Context, message *mDNS.Msg) (*mDNS.Msg, error) {
	if !t.BeginQuery() {
		return nil, transport.ErrTransportClosed
	}
	defer t.EndQuery()

	if t.connections == nil {
		return t.createNewConnection(ctx, message)
	}

	if t.enablePipeline {
		if t.maxQueries == 0 {
			conn := t.getValidConnFromPool()
			if conn != nil {
				return conn.Exchange(ctx, message)
			}
			return t.createNewConnection(ctx, message)
		} else {
			conn := t.findAndReserveActiveConn()
			if conn != nil {
				return conn.exchangeWithoutIncrement(ctx, message)
			}

			conn = t.getValidConnFromPool()
			if conn != nil {
				t.addActiveConn(conn)
				return conn.Exchange(ctx, message)
			}

			return t.createNewConnection(ctx, message)
		}
	} else {
		conn := t.getValidConnFromPool()
		if conn != nil {
			response, err := conn.Exchange(ctx, message)
			if err == nil {
				return response, nil
			}
		}
		return t.createNewConnection(ctx, message)
	}
}

func (t *TLSTransport) createNewConnection(ctx context.Context, message *mDNS.Msg) (*mDNS.Msg, error) {
	tlsConn, err := t.dialer.DialTLSContext(ctx, t.serverAddr)
	if err != nil {
		return nil, E.Cause(err, "dial TLS connection")
	}
	var connIdleTimeout time.Duration
	if t.connections != nil && t.disableKeepAlive {
		connIdleTimeout = t.idleTimeout
	}
	conn := newReuseableDNSConn(tlsConn, t.logger, t.enablePipeline, connIdleTimeout, t.maxQueries, t.connections, t)

	if t.connections == nil {
		defer conn.Close()
	} else if t.enablePipeline && t.maxQueries > 0 {
		t.addActiveConn(conn)
	}

	return conn.Exchange(ctx, message)
}
